package it.feargames.tileculling.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;

public final class LocationUtilities {

	private LocationUtilities() {
	}

	public static long getChunkKey(Chunk chunk) {
		return getChunkKey(chunk.getX(), chunk.getZ());
	}

	public static long getChunkKey(int x, int z) {
		return (long) x & 0xffffffffL | ((long) z & 0xffffffffL) << 32;
	}

	public static long getBlockKey(Block block) {
		return getBlockKey(block.getX(), block.getY(), block.getZ());
	}

	public static long getBlockKey(Location location) {
		return getBlockKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	public static long getBlockKey(int x, int y, int z) {
		return ((long) x & 0x7FFFFFF) | (((long) z & 0x7FFFFFF) << 27) | ((long) y << 54);
	}

	public static int getBlockKeyX(long packed) {
		return (int) ((packed << 37) >> 37);
	}

	public static int getBlockKeyY(long packed) {
		return (int) (packed >>> 54);
	}

	public static int getBlockKeyZ(long packed) {
		return (int) ((packed << 10) >> 37);
	}
}
