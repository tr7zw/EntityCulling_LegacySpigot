package it.feargames.tileculling.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class PaperUtilities {

	private static Method GET_NO_TICK_VIEW_DISTANCE_METHOD;

	static {
		try {
			GET_NO_TICK_VIEW_DISTANCE_METHOD = World.class.getDeclaredMethod("getNoTickViewDistance");
		} catch (NoSuchMethodException ignored) {
		}
	}

	private PaperUtilities() {
	}

	public static int getViewDistance(World world) {
		if (GET_NO_TICK_VIEW_DISTANCE_METHOD != null) {
			try {
				return (int) GET_NO_TICK_VIEW_DISTANCE_METHOD.invoke(world);
			} catch (IllegalAccessException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		return world.getViewDistance();
	}

}
