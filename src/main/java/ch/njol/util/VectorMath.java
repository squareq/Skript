package ch.njol.util;

import ch.njol.skript.effects.EffVectorRotateAroundAnother;
import ch.njol.skript.effects.EffVectorRotateXYZ;
import ch.njol.skript.expressions.ExprVectorCylindrical;
import ch.njol.skript.expressions.ExprVectorFromYawAndPitch;
import ch.njol.skript.expressions.ExprVectorSpherical;
import ch.njol.skript.expressions.ExprYawPitch;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;

@Deprecated
@ApiStatus.ScheduledForRemoval
public final class VectorMath {

	public static final double PI = Math.PI;
	public static final double HALF_PI = PI / 2;
	public static final double DEG_TO_RAD = Math.PI / 180;
	public static final double RAD_TO_DEG =  180 / Math.PI;

	private VectorMath() {}

	public static Vector fromSphericalCoordinates(double radius, double theta, double phi) {
		return ExprVectorSpherical.fromSphericalCoordinates(radius, theta, phi);
	}

	public static Vector fromCylindricalCoordinates(double radius, double phi, double height) {
		return ExprVectorCylindrical.fromCylindricalCoordinates(radius, phi, height);
	}

	public static Vector fromYawAndPitch(float yaw, float pitch) {
		return ExprYawPitch.fromYawAndPitch(yaw, pitch);
	}

	public static float getYaw(Vector vector) {
		return ExprYawPitch.getYaw(vector);
	}

	public static float getPitch(Vector vector) {
		return ExprYawPitch.getPitch(vector);
	}

	public static Vector rotX(Vector vector, double angle) {
		return EffVectorRotateXYZ.rotX(vector, angle);
	}

	public static Vector rotY(Vector vector, double angle) {
		return EffVectorRotateXYZ.rotY(vector, angle);
	}

	public static Vector rotZ(Vector vector, double angle) {
		return EffVectorRotateXYZ.rotZ(vector, angle);
	}

	public static Vector rot(Vector vector, Vector axis, double angle) {
		return EffVectorRotateAroundAnother.rot(vector, axis, angle);
	}

	public static float skriptYaw(float yaw) {
		return ExprYawPitch.skriptYaw(yaw);
	}

	public static float skriptPitch(float pitch) {
		return ExprYawPitch.skriptPitch(pitch);
	}

	public static float fromSkriptYaw(float yaw) {
		return ExprVectorFromYawAndPitch.fromSkriptYaw(yaw);
	}

	public static float fromSkriptPitch(float pitch) {
		return ExprVectorFromYawAndPitch.fromSkriptPitch(pitch);
	}

	public static float wrapAngleDeg(float angle) {
		return ExprVectorFromYawAndPitch.wrapAngleDeg(angle);
	}

	public static void copyVector(Vector vector1, Vector vector2) {
		vector1.copy(vector2);
	}

	/**
	 * Check whether or not each component of this vector is equal to 0.
	 * <br>Replaces {@code Vector#isZero()} since that method was added in spigot 1.19.3
	 * @return true if equal to zero, false if at least one component is non-zero
	 */
	public static boolean isZero(Vector vector) {
		return (vector.getX() == 0 && vector.getY() == 0 && vector.getZ() == 0);
	}

}
