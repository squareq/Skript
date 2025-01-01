package ch.njol.util;

import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;

/**
 * @author Peter G�ttinger
 *
 */
@Deprecated
@FunctionalInterface
@ApiStatus.ScheduledForRemoval
public interface Predicate<T> extends java.util.function.Predicate<T> {
  boolean test(@Nullable T paramT);
}

