package it.feargames.tileculling.occlusionculling;

import it.feargames.tileculling.ChunkCache;
import it.feargames.tileculling.CullingPlugin;
import it.feargames.tileculling.util.VectorUtilities;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Arrays;

public class OcclusionCulling {

	private final ChunkCache chunkCache;

	private static final int REACH = 64; // TODO: config
	private final byte[] cache = new byte[((REACH * 2) * (REACH * 2) * (REACH * 2)) / 4];

	public OcclusionCulling(ChunkCache chunkCache) {
		this.chunkCache = chunkCache;
	}

	public boolean isAABBVisible(Player player, Vector playerEyeLocation, Location aabbBlock, AxisAlignedBB aabb) {
		try {
			if (playerEyeLocation.getY() > 255 || playerEyeLocation.getY() < 0) {
				return false; // Ignore if player is outside of world bounds
			}

			Vector center = aabb.getAABBMiddle(aabbBlock);

			if (center.distanceSquared(playerEyeLocation) > (REACH * REACH)) {
				return false; // Out of reach
			}

			double width = aabb.getWidth();
			double height = aabb.getHeight();
			double depth = aabb.getDepth();

			Vector centerXMin = VectorUtilities.cloneAndAdd(center, -width / 2, 0, 0);
			Vector centerXMax = VectorUtilities.cloneAndAdd(center, width / 2, 0, 0);
			Vector centerYMin = VectorUtilities.cloneAndAdd(center, 0, -height / 2, 0);
			Vector centerYMax = VectorUtilities.cloneAndAdd(center, 0, height / 2, 0);
			Vector centerZMin = VectorUtilities.cloneAndAdd(center, 0, 0, -depth / 2);
			Vector centerZMax = VectorUtilities.cloneAndAdd(center, 0, 0, depth / 2);

			//CullingPlugin.particle(player, Particle.SNOWBALL, center);

			Vector[] rays = new Vector[4];
			//CullingPlugin.particle(player, Particle.VILLAGER_HAPPY, centerYMin, centerYMax);
			rays[0] = centerYMin.subtract(playerEyeLocation);
			rays[1] = centerYMax.subtract(playerEyeLocation);

			if (centerXMin.distanceSquared(playerEyeLocation) > centerXMax.distanceSquared(playerEyeLocation)) {
				//CullingPlugin.particle(player, Particle.VILLAGER_HAPPY, centerXMax);
				rays[2] = centerXMax.subtract(playerEyeLocation);
			} else {
				//CullingPlugin.particle(player, Particle.VILLAGER_HAPPY, centerXMin);
				rays[2] = centerXMin.subtract(playerEyeLocation);
			}
			if (centerZMin.distanceSquared(playerEyeLocation) > centerZMax.distanceSquared(playerEyeLocation)) {
				//CullingPlugin.particle(player, Particle.VILLAGER_HAPPY, centerZMax);
				rays[3] = centerZMax.subtract(playerEyeLocation);
			} else {
				//CullingPlugin.particle(player, Particle.VILLAGER_HAPPY, centerZMin);
				rays[3] = centerZMin.subtract(playerEyeLocation);
			}

			return isVisible(player, player.getWorld(), playerEyeLocation, rays);
		} catch (Throwable t) {
			// Failsafe
			t.printStackTrace();
		}
		return true;
	}

	public void resetCache() {
		Arrays.fill(cache, (byte) 0);
	}

	/**
	 * returns the grid cells that intersect with this vector<br>
	 * <a href=
	 * "http://playtechs.blogspot.de/2007/03/raytracing-on-grid.html">http://playtechs.blogspot.de/2007/03/raytracing-on-grid.html</a>
	 */
	private boolean isVisible(Player player, World world, Vector start, Vector[] rays) {
		// start point coords
		double x0 = start.getX();
		double y0 = start.getY();
		double z0 = start.getZ();

		for (Vector ray : rays) {
			// target point coords
			double x1 = x0 + ray.getX();
			double y1 = y0 + ray.getY();
			double z1 = z0 + ray.getZ();

			// horizontal and vertical cell amount spanned
			double dx = Math.abs(x1 - x0);
			double dy = Math.abs(y1 - y0);
			double dz = Math.abs(z1 - z0);

			// start cell coordinate
			int x = (int) Math.floor(x0);
			int y = (int) Math.floor(y0);
			int z = (int) Math.floor(z0);

			// distance between horizontal intersection points with cell border as a
			// fraction of the total vector length
			double dt_dx = 1f / dx;
			// distance between vertical intersection points with cell border as a fraction
			// of the total vector length
			double dt_dy = 1f / dy;
			double dt_dz = 1f / dz;

			// total amount of intersected cells
			int n = 1;

			// 1, 0 or -1
			// determines the direction of the next cell (horizontally / vertically)
			int x_inc, y_inc, z_inc;
			// the distance to the next horizontal / vertical intersection point with a cell
			// border as a fraction of the total vector length
			double t_next_y, t_next_x, t_next_z;

			if (dx == 0f) {
				x_inc = 0;
				t_next_x = dt_dx; // don't increment horizontally because the vector is perfectly vertical
			} else if (x1 > x0) {
				x_inc = 1; // target point is horizontally greater than starting point so increment every
				// step by 1
				n += (int) Math.floor(x1) - x; // increment total amount of intersecting cells
				t_next_x = (float) ((Math.floor(x0) + 1 - x0) * dt_dx); // calculate the next horizontal intersection point based on the position inside
				// the first cell
			} else {
				x_inc = -1; // target point is horizontally smaller than starting point so reduce every step
				// by 1
				n += x - (int) Math.floor(x1); // increment total amount of intersecting cells
				t_next_x = (float) ((x0 - Math.floor(x0)) * dt_dx); // calculate the next horizontal intersection point based on the position inside
				// the first cell
			}

			if (dy == 0f) {
				y_inc = 0;
				t_next_y = dt_dy; // don't increment vertically because the vector is perfectly horizontal
			} else if (y1 > y0) {
				y_inc = 1; // target point is vertically greater than starting point so increment every
				// step by 1
				n += (int) Math.floor(y1) - y; // increment total amount of intersecting cells
				t_next_y = (float) ((Math.floor(y0) + 1 - y0) * dt_dy); // calculate the next vertical intersection point based on the position inside
				// the first cell
			} else {
				y_inc = -1; // target point is vertically smaller than starting point so reduce every step
				// by 1
				n += y - (int) Math.floor(y1); // increment total amount of intersecting cells
				t_next_y = (float) ((y0 - Math.floor(y0)) * dt_dy); // calculate the next vertical intersection point based on the position inside
				// the first cell
			}

			if (dz == 0f) {
				z_inc = 0;
				t_next_z = dt_dz; // don't increment vertically because the vector is perfectly horizontal
			} else if (z1 > z0) {
				z_inc = 1; // target point is vertically greater than starting point so increment every
				// step by 1
				n += (int) Math.floor(z1) - z; // increment total amount of intersecting cells
				t_next_z = (float) ((Math.floor(z0) + 1 - z0) * dt_dz); // calculate the next vertical intersection point based on the position inside
				// the first cell
			} else {
				z_inc = -1; // target point is vertically smaller than starting point so reduce every step
				// by 1
				n += z - (int) Math.floor(z1); // increment total amount of intersecting cells
				t_next_z = (float) ((z0 - Math.floor(z0)) * dt_dz); // calculate the next vertical intersection point based on the position inside
				// the first cell
			}

			boolean finished = stepRay(player, world, x0, y0, z0, x, y, z, dt_dx, dt_dy, dt_dz, n, x_inc, y_inc, z_inc, t_next_y, t_next_x, t_next_z);
			if (finished) {
				return true;
			}
		}
		return false;
	}

	private boolean stepRay(Player player, World world, double x0, double y0, double z0, int x, int y,
							int z, double dt_dx, double dt_dy, double dt_dz, int n, int x_inc, int y_inc, int z_inc, double t_next_y,
							double t_next_x, double t_next_z) {
		int chunkX = 0;
		int chunkZ = 0;

		ChunkSnapshot snapshot = null;

		// iterate through all intersecting cells (n times)
		for (; n > 0; n--) {
			if (y < 0 || y > 255) {
				// Impossible coordinates
				throw new RuntimeException("Invalid Y value! " + y);
			}

			// Calculate cache relative coords
			int cx = (int) Math.floor((x0 - x) + REACH);
			int cy = (int) Math.floor((y0 - y) + REACH);
			int cz = (int) Math.floor((z0 - z) + REACH);
			// Calculate cache key
			int keyPos = cx + cy * (REACH * 2) + cz * (REACH * 2) * (REACH * 2);
			int entry = keyPos / 4;
			if (entry > cache.length - 1) {
				// Something went wrong... we exceeded the cache size!
				throw new RuntimeException("Illegal cache relative coordinates!");
			}
			int offset = (keyPos % 4) * 2;
			// Get cached value, 0 means uncached (default)
			int cVal = cache[entry] >> offset & 3;

			if (cVal == 2) {
				// Block cached as occluding, stop ray
				return false;
			}

			if (cVal == 0) {
				// Block is not cached, analyze chunk snapshot
				int currentChunkX = x >> 4;
				int currentChunkZ = z >> 4;
				if (snapshot == null || chunkX != currentChunkX || chunkZ != currentChunkZ) {
					// Need to fetch chunk snapshot
					chunkX = currentChunkX;
					chunkZ = currentChunkZ;
					long chunkKey = Chunk.getChunkKey(chunkX, chunkZ);
					snapshot = chunkCache.getChunk(world, chunkKey);
				}

				if (snapshot == null) {
					// Looks normal, a ray can just step into an unloaded chunk
					return false;
				}

				int relativeX = x & 0xF;
				int relativeZ = z & 0xF;
				Material material = snapshot.getBlockType(relativeX, y, relativeZ);
				if (CullingPlugin.isOccluding(material)) {
					cache[entry] |= 1 << offset + 1;
					return false;
				}
				cache[entry] |= 1 << offset;
			}

			if (t_next_y < t_next_x && t_next_y < t_next_z) { // next cell is upwards/downwards because the distance to the next vertical
				// intersection point is smaller than to the next horizontal intersection point
				y += y_inc; // move up/down
				t_next_y += dt_dy; // update next vertical intersection point
			} else if (t_next_x < t_next_y && t_next_x < t_next_z) { // next cell is right/left
				x += x_inc; // move right/left
				t_next_x += dt_dx; // update next horizontal intersection point
			} else {
				z += z_inc; // move right/left
				t_next_z += dt_dz; // update next horizontal intersection point
			}

		}
		return true;
	}

}
