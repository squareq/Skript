package ch.njol.skript.classes;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Predicate;

/**
 * @author Peter Güttinger
 */
@Deprecated
@FunctionalInterface
@ApiStatus.ScheduledForRemoval
public interface SerializableChecker<T> extends ch.njol.util.Checker<T>, Predicate<T> {}
