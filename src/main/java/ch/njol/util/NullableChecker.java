package ch.njol.util;

import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface NullableChecker<T> extends ch.njol.util.Checker<T>, Predicate<T> {

	@Override
	boolean check(@Nullable T o);

	public static final NullableChecker<Object> nullChecker = new NullableChecker<Object>() {
		@Override
		public boolean check(final @Nullable Object o) {
			return o != null;
		}
	};

}
