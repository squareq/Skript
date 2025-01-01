package ch.njol.util;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

@Deprecated
@FunctionalInterface
@ApiStatus.ScheduledForRemoval
public interface Callback<R, A> extends Function<A, R> {

	@Nullable
	public R run(A arg);

	@Override
	default R apply(A a) {
		return run(a);
	}

}
