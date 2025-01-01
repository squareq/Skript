package ch.njol.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

@Deprecated
@FunctionalInterface
@ApiStatus.ScheduledForRemoval
public interface Setter<T> extends Consumer<T> {

	void set(T t);

	@Override
	default void accept(T t) {
		this.set(t);
	}
}
