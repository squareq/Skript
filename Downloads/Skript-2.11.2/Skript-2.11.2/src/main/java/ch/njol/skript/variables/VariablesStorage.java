package ch.njol.skript.variables;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jetbrains.annotations.Nullable;

import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.log.ParseLogHandler;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.FileUtils;
import ch.njol.skript.util.Task;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.SerializedVariable.Value;
import ch.njol.util.Closeable;

/**
 * A variable storage is holds the means and methods of storing variables.
 * <p>
 * This is usually some sort of database, and could be as simply as a text file.
 *
 * @see FlatFileStorage
 * @see DatabaseStorage
 */
// FIXME ! large databases (>25 MB) cause the server to be unresponsive instead of loading slowly
@SuppressWarnings({"SuspiciousIndentAfterControlStatement", "removal"})
public abstract class VariablesStorage implements Closeable {

	/**
	 * The size of the variable changes queue.
	 */
	private static final int QUEUE_SIZE = 1000;
	/**
	 * The threshold of the size of the variable change
	 * after which a warning will be sent.
	 */
	private static final int FIRST_WARNING = 300;

	final LinkedBlockingQueue<SerializedVariable> changesQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);

	/**
	 * Whether this variable storage has been {@link #close() closed}.
	 */
	protected volatile boolean closed = false;

	/**
	 * The name of the database
	 */
	private String databaseName;

	/**
	 * The type of the database, i.e. CSV.
	 */
	private final String databaseType;

	/**
	 * The file associated with this variable storage.
	 * Can be {@code null} if no file is required.
	 */
	@Nullable
	protected File file;

	/**
	 * The pattern of the variable name this storage accepts.
	 * {@code null} for '{@code .*}' or '{@code .*}'.
	 */
	@Nullable
	private Pattern variableNamePattern;

	/**
	 * The thread used for writing variables to the storage.
	 */
	// created in the constructor, started in load()
	private final Thread writeThread;

	/**
	 * Creates a new variable storage with the given name.
	 * <p>
	 * This will also create the {@link #writeThread}, but it must be started
	 * with {@link #load(SectionNode)}.
	 *
	 * @param type the database type i.e. CSV.
	 */
	protected VariablesStorage(String type) {
		assert type != null;
		databaseType = type;

		writeThread = Skript.newThread(() -> {
			while (!closed) {
				try {
					// Take a variable from the queue and process it
					SerializedVariable variable = changesQueue.take();
					Value value = variable.value;

					// Actually save the variable
					if (value != null)
						save(variable.name, value.type, value.data);
					else
						save(variable.name, null, null);
				} catch (InterruptedException ignored) {
					// Ignored as the `closed` field will indicate whether the thread actually needs to stop
				}
			}
		}, "Skript variable save thread for database '" + type + "'");
	}

	/**
	 * Get the config name of a database
	 * <p>
	 * Note: Returns the user set name for the database, ex:
	 * <pre>{@code
	 * default: <- Config Name
	 *    type: CSV
	 * }</pre>
	 * @return name of database
	 */
	protected final String getUserConfigurationName() {
		return databaseName;
	}

	/**
	 * Get the config type of a database
	 * @return type of databse
	 */
	protected final String getDatabaseType() {
		return databaseType;
	}

	/**
	 * Gets the string value at the given key of the given section node.
	 *
	 * @param sectionNode the section node.
	 * @param key the key.
	 * @return the value, or {@code null} if the value was invalid,
	 * or not found.
	 */
	@Nullable
	protected String getValue(SectionNode sectionNode, String key) {
		return getValue(sectionNode, key, String.class);
	}

	/**
	 * Gets the value at the given key of the given section node,
	 * parsed with the given type.
	 *
	 * @param sectionNode the section node.
	 * @param key the key.
	 * @param type the type.
	 * @return the parsed value, or {@code null} if the value was invalid,
	 * or not found.
	 * @param <T> the type.
	 */
	@Nullable
	protected <T> T getValue(SectionNode sectionNode, String key, Class<T> type) {
		String rawValue = sectionNode.getValue(key);
		// Section node doesn't have this key
		if (rawValue == null) {
			Skript.error("The config is missing the entry for '" + key + "' in the database '" + databaseName + "'");
			return null;
		}

		try (ParseLogHandler log = SkriptLogger.startParseLogHandler()) {
			T parsedValue = Classes.parse(rawValue, type, ParseContext.CONFIG);

			if (parsedValue == null)
				// Parsing failed
				log.printError("The entry for '" + key + "' in the database '" + databaseName + "' must be " +
					Classes.getSuperClassInfo(type).getName().withIndefiniteArticle());
			else
				log.printLog();

			return parsedValue;
		}
	}

	private static final Set<File> registeredFiles = new HashSet<>();

	/**
	 * Loads the configuration for this variable storage
	 * from the given section node.
	 *
	 * @param sectionNode the section node.
	 * @return whether the loading succeeded.
	 */
	public final boolean load(SectionNode sectionNode) {
		databaseName = sectionNode.getKey();

		String pattern = getValue(sectionNode, "pattern");
		if (pattern == null)
			return false;

		try {
			// Set variable name pattern, see field javadoc for explanation of null value
			variableNamePattern = pattern.equals(".*") || pattern.equals(".+") ? null : Pattern.compile(pattern);
		} catch (PatternSyntaxException e) {
			Skript.error("Invalid pattern '" + pattern + "': " + e.getLocalizedMessage());
			return false;
		}

		if (requiresFile()) {
			// Initialize file
			String fileName = getValue(sectionNode, "file");
			if (fileName == null)
				return false;

			this.file = getFile(fileName).getAbsoluteFile();

			if (file.exists() && !file.isFile()) {
				Skript.error("The database file '" + file.getName() + "' must be an actual file, not a directory.");
				return false;
			}

			// Create the file if it does not exist yet
			try {
				//noinspection ResultOfMethodCallIgnored
				file.createNewFile();
			} catch (IOException e) {
				Skript.error("Cannot create the database file '" + file.getName() + "': " + e.getLocalizedMessage());
				return false;
			}

			// Check for read & write permissions to the file
			if (!file.canWrite()) {
				Skript.error("Cannot write to the database file '" + file.getName() + "'!");
				return false;
			}
			if (!file.canRead()) {
				Skript.error("Cannot read from the database file '" + file.getName() + "'!");
				return false;
			}

			if (registeredFiles.contains(file)) {
				Skript.error("Database `" + databaseName + "` failed to load. The file `" + fileName + "` is already registered to another database.");
				return false;
			}
			registeredFiles.add(file);

			// Set the backup interval, if present & enabled
			if (!"0".equals(getValue(sectionNode, "backup interval"))) {
				Timespan backupInterval = getValue(sectionNode, "backup interval", Timespan.class);
				int toKeep = getValue(sectionNode, "backups to keep", Integer.class);
				boolean removeBackups = false;
				boolean startBackup = true;
				if (backupInterval != null)
					if (toKeep == 0) {
						startBackup = false;
					} else if (toKeep >= 1) {
						removeBackups = true;
					}
					if (startBackup) {
						startBackupTask(backupInterval, removeBackups, toKeep);
					} else {
						try {
							FileUtils.backupPurge(file, toKeep);
						} catch (IOException e) {
							Skript.error("Variables backup wipe failed: " + e.getLocalizedMessage());
						}
					}
			}
		}

		// Load the entries custom to the variable storage
		if (!load_i(sectionNode))
			return false;

		writeThread.start();
		Skript.closeOnDisable(this);

		return true;
	}

	/**
	 * Loads variables stored here.
	 *
	 * @return Whether the database could be loaded successfully,
	 * i.e. whether the config is correct and all variables could be loaded.
	 */
	protected abstract boolean load_i(SectionNode n);

	/**
	 * Called after all storages have been loaded, and variables
	 * have been redistributed if settings have changed.
	 * This should commit the first transaction (which is not empty if
	 * variables have been moved from another database to this one or vice versa),
	 * and start repeating transactions if applicable.
	 */
	protected abstract void allLoaded();

	/**
	 * Checks if this storage requires a file for storing its data.
	 *
	 * @return if this storage needs a file.
	 */
	protected abstract boolean requiresFile();

	/**
	 * Gets the file needed for this variable storage from the given file name.
	 * <p>
	 * Will only be called if {@link #requiresFile()} is {@code true}.
	 *
	 * @param fileName the given file name.
	 * @return the {@link File} object.
	 */
	protected abstract File getFile(String fileName);

	/**
	 * Must be locked after {@link Variables#getReadLock()}
	 * (if that lock is used at all).
	 */
	protected final Object connectionLock = new Object();

	/**
	 * (Re)connects to the database.
	 * <p>
	 * Not called on the first connect: do this in {@link #load_i(SectionNode)}.
	 * An error should be printed by this method
	 * prior to returning {@code false}.
	 *
	 * @return whether the connection could be re-established.
	 */
	protected abstract boolean connect();

	/**
	 * Disconnects from the database.
	 */
	protected abstract void disconnect();

	/**
	 * The backup task, or {@code null} if automatic backups are disabled.
	 */
	@Nullable
	protected Task backupTask = null;

	/**
	 * Starts the backup task, with the given backup interval.
	 *
	 * @param backupInterval the backup interval.
	 */
	public void startBackupTask(Timespan backupInterval, boolean removeBackups, int toKeep) {
		// File is null or backup interval is invalid
		if (file == null || backupInterval.getAs(Timespan.TimePeriod.TICK) == 0)
			return;
		backupTask = new Task(Skript.getInstance(), backupInterval.getAs(Timespan.TimePeriod.TICK), backupInterval.getAs(Timespan.TimePeriod.TICK), true) {
			@Override
			public void run() {
				synchronized (connectionLock) {
					// Disconnect,
					disconnect();
					try {
						// ..., then backup
						FileUtils.backup(file);
						if (removeBackups) {
							try {
								FileUtils.backupPurge(file, toKeep);
							} catch (IOException | IllegalArgumentException e) {
								Skript.error("Automatic variables backup purge failed: " + e.getLocalizedMessage());
							}
						}
					} catch (IOException e) {
						Skript.error("Automatic variables backup failed: " + e.getLocalizedMessage());
					} finally {
						// ... and reconnect
						connect();
					}
				}
			}
		};
	}

	/**
	 * Checks if this variable storage accepts the given variable name.
	 *
	 * @param var the variable name.
	 * @return if this storage accepts the variable name.
	 *
	 * @see #variableNamePattern
	 */
	boolean accept(@Nullable String var) {
		if (var == null)
			return false;
		if(var.startsWith("$")){
			return false;
		}
		return variableNamePattern == null || variableNamePattern.matcher(var).matches();
	}

	/**
	 * Returns the name pattern accepted by this variable storage
	 * @return the name pattern, or null if accepting all
	 */
	public @Nullable Pattern getNamePattern() {
		return variableNamePattern;
	}

	/**
	 * The interval between warnings that many variables are being written
	 * at once, in seconds.
	 */
	private static final int WARNING_INTERVAL = 10;
	/**
	 * The interval between errors that too many variables are being written
	 * at once, in seconds.
	 */
	private static final int ERROR_INTERVAL = 10;

	/**
	 * The last time a warning was printed for many variables in the queue.
	 */
	private long lastWarning = Long.MIN_VALUE;
	/**
	 * The last time an error was printed for too many variables in the queue.
	 */
	private long lastError = Long.MIN_VALUE;

	/**
	 * Saves the given serialized variable.
	 * <p>
	 * May be called from a different thread than Bukkit's main thread.
	 *
	 * @param var the serialized variable.
	 */
	final void save(SerializedVariable var) {
		if(!accept(var.name)){
			changesQueue.remove(var);
			return;
		}
		if (changesQueue.size() > FIRST_WARNING && lastWarning < System.currentTimeMillis() - WARNING_INTERVAL * 1000) {
			// Too many variables queued up to save, warn the server
			Skript.warning("Cannot write variables to the database '" + databaseName + "' at sufficient speed; " +
				"server performance may suffer and many variables will be lost if the server crashes. " +
				"(this warning will be repeated at most once every " + WARNING_INTERVAL + " seconds)");

			lastWarning = System.currentTimeMillis();
		}

		if (!changesQueue.offer(var)) {
			// Variable changes queue filled up

			if (lastError < System.currentTimeMillis() - ERROR_INTERVAL * 1000) {
				// Inform console about overload of variable changes
				Skript.error("Skript cannot save any variables to the database '" + databaseName + "'. " +
					"The server will hang and may crash if no more variables can be saved.");

				lastError = System.currentTimeMillis();
			}

			// Halt thread until variables queue starts clearing up
			while (true) {
				try {
					// REMIND add repetitive error and/or stop saving variables altogether?
					changesQueue.put(var);
					break;
				} catch (InterruptedException ignored) {}
			}
		}
	}

	/**
	 * Called when Skript gets disabled.
	 * <p>
	 * The default implementation will wait for all variables to be saved
	 * before setting {@link #closed} to {@code true} and stopping
	 * the {@link #writeThread write thread}.
	 * <p>
	 * Therefore, make sure to call {@code super.close()}
	 * if this method is overridden.
	 */
	@Override
	public void close() {
		// Wait for all variable changes to be processed
		while (changesQueue.size() > 0) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException ignored) {}
		}

		// Now safely close storage and interrupt thread
		closed = true;
		writeThread.interrupt();
	}

	/**
	 * Clears the {@link #changesQueue queue} of unsaved variables.
	 * <p>
	 * Only used if all variables are saved immediately
	 * after calling this method.
	 */
	protected void clearChangesQueue() {
		changesQueue.clear();
	}

	/**
	 * Saves a variable.
	 * <p>
	 * This is called from the main thread
	 * while variables are transferred between databases,
	 * and from the {@link #writeThread} afterwards.
	 * <p>
	 * {@code type} and {@code value} are <i>both</i> {@code null}
	 * iff this call is to delete the variable.
	 *
	 * @param name the name of the variable.
	 * @param type the type of the variable.
	 * @param value the serialized value of the variable.
	 * @return Whether the variable was saved.
	 */
	protected abstract boolean save(String name, @Nullable String type, @Nullable byte[] value);

}
