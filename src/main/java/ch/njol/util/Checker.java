package ch.njol.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Predicate;

@Deprecated
@FunctionalInterface
@ApiStatus.ScheduledForRemoval
public interface Checker<T> extends Predicate<T> {

	boolean check(T o);

	@Override
	default boolean test(T t) {
		return this.check(t);
	}

}
