package it.feargames.tileculling;

import org.bukkit.util.Vector;

public final class VectorUtilities {

	private VectorUtilities() {
	}

	public static Vector cloneAndAdd(Vector vector, double x, double y, double z) {
		vector = vector.clone();
		vector.setX(vector.getX() + x);
		vector.setY(vector.getY() + y);
		vector.setZ(vector.getZ() + z);
		return vector;
	}
}
