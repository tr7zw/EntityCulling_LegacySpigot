package it.feargames.tileculling.occlusionculling;

import it.feargames.tileculling.ChunkCache;
import it.feargames.tileculling.CullingPlugin;
import it.feargames.tileculling.util.LocationUtilities;
import it.feargames.tileculling.util.MathUtilities;
import it.feargames.tileculling.util.vector.Vec3d;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class OcclusionCulling {

	private final ChunkCache chunkCache;
	private final int tileRange;

	private final Vec3d[] targets = new Vec3d[8];
	private final OcclusionCache cache;

	public OcclusionCulling(ChunkCache chunkCache, int tileRange) {
		this.chunkCache = chunkCache;
		this.tileRange = tileRange;

		cache = new ArrayOcclusionCache(tileRange);
	}

	public boolean isAABBVisible(Vec3d aabbBlock, AxisAlignedBB aabb, Vec3d playerPosition) {
		try {
			if (playerPosition.getY() > 255 || playerPosition.getY() < 0) { // TODO fix for 1.17
				return false; // Ignore if player is outside of world bounds
			}

			aabbBlock = aabbBlock.subtract((int) playerPosition.x, (int) playerPosition.y, (int) playerPosition.z);
			int maxX = MathUtilities.ceil(aabbBlock.x + aabb.maxX + 0.25);
			int maxY = MathUtilities.ceil(aabbBlock.y + aabb.maxY + 0.25);
			int maxZ = MathUtilities.ceil(aabbBlock.z + aabb.maxZ + 0.25);
			int minX = MathUtilities.fastFloor(aabbBlock.x + aabb.minX - 0.25);
			int minY = MathUtilities.fastFloor(aabbBlock.y + aabb.minY - 0.25);
			int minZ = MathUtilities.fastFloor(aabbBlock.z + aabb.minZ - 0.25);

			if (minX <= 0 && maxX > 0 && minY <= 0 && maxY >= 0 && minZ < 0 && maxZ >= 0) {
				return true; // We are inside of the AABB, don't cull
			}

			Relative relX = Relative.from(minX, maxX);
			Relative relY = Relative.from(minY, maxY);
			Relative relZ = Relative.from(minZ + 1, maxZ + 1);

			int blockCount = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
			Vec3d[] blocks = new Vec3d[blockCount];
			boolean[][] faceEdgeData = new boolean[blockCount][];
			int slot = 0;

			boolean[] onFaceEdge = new boolean[6];
			for (int x = minX; x < maxX; x++) {
				onFaceEdge[0] = x == minX;
				onFaceEdge[1] = x == maxX - 1;
				for (int y = minY; y < maxY; y++) {
					onFaceEdge[2] = y == minY;
					onFaceEdge[3] = y == maxY - 1;
					for (int z = minZ; z < maxZ; z++) {
						int cVal = getCacheValue(x, y, z);
						if (cVal == 1) {
							return true;
						}
						if (cVal == 0) {
							onFaceEdge[4] = z == minZ;
							onFaceEdge[5] = z == maxZ - 1;
							if ((onFaceEdge[0] && relX == Relative.POSITIVE)
									|| (onFaceEdge[1] && relX == Relative.NEGATIVE)
									|| (onFaceEdge[2] && relY == Relative.POSITIVE)
									|| (onFaceEdge[3] && relY == Relative.NEGATIVE)
									|| (onFaceEdge[4] && relZ == Relative.POSITIVE)
									|| (onFaceEdge[5] && relZ == Relative.NEGATIVE)) {
								blocks[slot] = new Vec3d(x, y, z);
								faceEdgeData[slot] = Arrays.copyOf(onFaceEdge, 6);
								slot++;
							}
						}
					}
				}
			}

			for (int i = 0; i < slot; i++) {
				if (isVoxelVisible(playerPosition, blocks[i], faceEdgeData[i])) {
					return true;
				}
			}

			return false;
		} catch (Throwable t) {
			// Failsafe
			t.printStackTrace();
		}
		return true;
	}

	// -1 = invalid location, 0 = not checked yet, 1 = visible, 2 = blocked
	private int getCacheValue(int x, int y, int z) {
		if (Math.abs(x) > tileRange - 2 || Math.abs(y) > tileRange - 2 || Math.abs(z) > tileRange - 2) {
			return -1;
		}

		// check if target is already known
		int cx = MathUtilities.fastFloor(x + tileRange);
		int cy = MathUtilities.fastFloor(y + tileRange);
		int cz = MathUtilities.fastFloor(z + tileRange);
		return cache.getState(cx, cy, cz);
	}

	private boolean isVoxelVisible(Vec3d playerPosition, Vec3d position, boolean[] faceEdgeData) {
		int targetSize = 0;

		// boolean onMinX = faceEdgeData[0];
		// boolean onMaxX = faceEdgeData[1];
		// boolean onMinY = faceEdgeData[2];
		// boolean onMaxY = faceEdgeData[3];
		// boolean onMinZ = faceEdgeData[4];
		// boolean onMaxZ = faceEdgeData[5];

		// main points for all faces
		position = position.add(0.05, 0.05, 0.05);
		if (faceEdgeData[0] || faceEdgeData[4] || faceEdgeData[2]) {
			targets[targetSize++] = position;
		}
		if (faceEdgeData[1]) {
			targets[targetSize++] = position.add(0.90, 0, 0);
		}
		if (faceEdgeData[3]) {
			targets[targetSize++] = position.add(0, 0.90, 0);
		}
		if (faceEdgeData[5]) {
			targets[targetSize++] = position.add(0, 0, 0.90);
		}
		// Extra corner points
		if ((faceEdgeData[4] && faceEdgeData[1] && faceEdgeData[3]) || (faceEdgeData[1] && faceEdgeData[3])) {
			targets[targetSize++] = position.add(0.90, 0.90, 0);
		}
		if ((faceEdgeData[0] && faceEdgeData[5] && faceEdgeData[3]) || (faceEdgeData[5] && faceEdgeData[3])) {
			targets[targetSize++] = position.add(0, 0.90, 0.90);
		}
		if (faceEdgeData[5] && faceEdgeData[1]) {
			targets[targetSize++] = position.add(0.90, 0, 0.90);
		}
		if (faceEdgeData[1] && faceEdgeData[3] && faceEdgeData[5]) {
			targets[targetSize++] = position.add(0.90, 0.90, 0.90);
		}

		/*
		if (showDebug) {
			for (int i = 0; i < targetSize; i++) {
				Vec3d target = targets[i];
				client.world.addImportantParticle(ParticleTypes.HAPPY_VILLAGER, true, ((int) playerPosition.x) + target.x,
						((int) playerPosition.y) + target.y, ((int) playerPosition.z) + target.z, 0, 0, 0);
			}
		}
		*/
		boolean result = isVisible(playerPosition, targets, targetSize);
		cacheResult(targets[0], result);
		return result;
	}

	/**
	 * returns the grid cells that intersect with this Vec3d<br>
	 * <a href=
	 * "http://playtechs.blogspot.de/2007/03/raytracing-on-grid.html">http://playtechs.blogspot.de/2007/03/raytracing-on-grid.html</a>
	 * <p>
	 * Caching assumes that all Vec3d's are inside the same block
	 */
	private boolean isVisible(Vec3d start, Vec3d[] targets, int size) {
		// start point coords
		double startX = start.getX();
		double startY = start.getY();
		double startZ = start.getZ();

		// start cell coordinate
		int x = MathUtilities.floor(startX);
		int y = MathUtilities.floor(startY);
		int z = MathUtilities.floor(startZ);

		for (int v = 0; v < size; v++) {
			// ray-casting target
			Vec3d target = targets[v];

			// coordinates of end target point
			double relativeX = startX + target.getX();
			double relativeY = startY + target.getY();
			double relativeZ = startZ + target.getZ();

			// horizontal and vertical cell amount spanned
			double dimensionX = Math.abs(relativeX - startX);
			double dimensionY = Math.abs(relativeY - startY);
			double dimensionZ = Math.abs(relativeZ - startZ);

			// distance between horizontal intersection points with cell border as a
			// fraction of the total Vec3d length
			double dimFracX = 1f / dimensionX ;
			// distance between vertical intersection points with cell border as a fraction
			// of the total Vec3d length
			double dimFracY = 1f / dimensionY;
			double dimFracZ = 1f / dimensionZ;

			// total amount of intersected cells
			int intersectCount = 1;

			// 1, 0 or -1
			// determines the direction of the next cell (horizontally / vertically)
			int x_inc, y_inc, z_inc;

			// the distance to the next horizontal / vertical intersection point with a cell
			// border as a fraction of the total Vec3d length
			double t_next_y, t_next_x, t_next_z;

			if (dimensionX  == 0f) {
				x_inc = 0;
				t_next_x = dimFracX; // don't increment horizontally because the Vec3d is perfectly vertical
			} else if (relativeX > startX) {
				x_inc = 1; // target point is horizontally greater than starting point so increment every
				// step by 1
				intersectCount += MathUtilities.floor(relativeX) - x; // increment total amount of intersecting cells
				t_next_x = (float) ((MathUtilities.floor(startX) + 1 - startX) * dimFracX); // calculate the next horizontal
				// intersection
				// point based on the position inside
				// the first cell
			} else {
				x_inc = -1; // target point is horizontally smaller than starting point so reduce every step
				// by 1
				intersectCount += x - MathUtilities.floor(relativeX); // increment total amount of intersecting cells
				t_next_x = (float) ((startX - MathUtilities.floor(startX)) * dimFracX); // calculate the next horizontal
				// intersection point
				// based on the position inside
				// the first cell
			}

			if (dimensionY == 0f) {
				y_inc = 0;
				t_next_y = dimFracY; // don't increment vertically because the Vec3d is perfectly horizontal
			} else if (relativeY > startY) {
				y_inc = 1; // target point is vertically greater than starting point so increment every
				// step by 1
				intersectCount += MathUtilities.floor(relativeY) - y; // increment total amount of intersecting cells
				t_next_y = (float) ((MathUtilities.floor(startY) + 1 - startY) * dimFracY); // calculate the next vertical
				// intersection
				// point based on the position inside
				// the first cell
			} else {
				y_inc = -1; // target point is vertically smaller than starting point so reduce every step
				// by 1
				intersectCount += y - MathUtilities.floor(relativeY); // increment total amount of intersecting cells
				t_next_y = (float) ((startY - MathUtilities.floor(startY)) * dimFracY); // calculate the next vertical intersection
				// point
				// based on the position inside
				// the first cell
			}

			if (dimensionZ == 0f) {
				z_inc = 0;
				t_next_z = dimFracZ; // don't increment vertically because the Vec3d is perfectly horizontal
			} else if (relativeZ > startZ) {
				z_inc = 1; // target point is vertically greater than starting point so increment every
				// step by 1
				intersectCount += MathUtilities.floor(relativeZ) - z; // increment total amount of intersecting cells
				t_next_z = (float) ((MathUtilities.floor(startZ) + 1 - startZ) * dimFracZ); // calculate the next vertical
				// intersection
				// point based on the position inside
				// the first cell
			} else {
				z_inc = -1; // target point is vertically smaller than starting point so reduce every step
				// by 1
				intersectCount += z - MathUtilities.floor(relativeZ); // increment total amount of intersecting cells
				t_next_z = (float) ((startZ - MathUtilities.floor(startZ)) * dimFracZ); // calculate the next vertical intersection
				// point
				// based on the position inside
				// the first cell
			}

			boolean finished = stepRay(start, startX, startY, startZ, x, y, z, dimFracX, dimFracY, dimFracZ,
					intersectCount, x_inc, y_inc, z_inc, t_next_y, t_next_x, t_next_z);
			if (finished) {
				return true;
			}
		}
		return false;
	}

	private boolean stepRay(Vec3d start, double startX, double startY, double startZ, int currentX, int currentY,
							int currentZ, double distInX, double distInY, double distInZ, int n, int x_inc, int y_inc,
							int z_inc, double t_next_y, double t_next_x, double t_next_z) {
		int chunkX = 0;
		int chunkZ = 0;

		ChunkSnapshot snapshot = null;

		// iterate through all intersecting cells (n times)
		for (; n > 1; n--) { // n-1 times because we don't want to check the last block
			// Calculate cache relative coords
			int cx = MathUtilities.fastFloor((startX - currentX) + tileRange);
			int cy = MathUtilities.fastFloor((startY - currentY) + tileRange);
			int cz = MathUtilities.fastFloor((startZ - currentZ) + tileRange);

			// Get cached value, 0 means uncached (default)
			int cVal = cache.getState(cx, cy, cz);

			if (cVal == 2) {
				// Block cached as occluding, stop ray
				return false;
			}

			if (cVal == 0) {
				// Block is not cached, analyze chunk snapshot

				int currentChunkX = currentX >> 4;
				int currentChunkZ = currentZ >> 4;
				if (snapshot == null || chunkX != currentChunkX || chunkZ != currentChunkZ) {
					// Need to fetch chunk snapshot
					chunkX = currentChunkX;
					chunkZ = currentChunkZ;
					Refere
					long chunkKey = LocationUtilities.getChunkKey(chunkX, chunkZ);
					snapshot = chunkCache.getChunk(world, chunkKey);


					snapshot = world.getChunk(chunkX, chunkZ);
					if (snapshot == null) {
						return false;
					}
				}

				int relativeX = currentX % 16;
				if (relativeX < 0) {
					relativeX = 16 + relativeX;
				}
				int relativeZ = currentZ % 16;
				if (relativeZ < 0) {
					relativeZ = 16 + relativeZ;
				}
				if (relativeX < 0) {
					cache.setLastHidden();
					return false;
				}
				if (relativeZ < 0) {
					cache.setLastHidden();
					return false;
				}
				if (currentY < 0 || currentY > 255) { // out of world // TODO fix for 1.17
					return true;
				}
				BlockPos pos = new BlockPos(currentX, currentY, currentZ);
				BlockState state = snapshot.getBlockState(pos);
				if (state.isOpaqueFullCube(world, pos)) {
					cache.setLastHidden();
					return false;
				}
				cache.setLastVisible();
			}

			if (t_next_y < t_next_x && t_next_y < t_next_z) { // next cell is upwards/downwards because the distance to
				// the next vertical
				// intersection point is smaller than to the next horizontal intersection point
				currentY += y_inc; // move up/down
				t_next_y += distInY; // update next vertical intersection point
			} else if (t_next_x < t_next_y && t_next_x < t_next_z) { // next cell is right/left
				currentX += x_inc; // move right/left
				t_next_x += distInX; // update next horizontal intersection point
			} else {
				currentZ += z_inc; // move right/left
				t_next_z += distInZ; // update next horizontal intersection point
			}

		}
		return true;
	}

	private boolean stepRay0(Player player, World world, double x0, double y0, double z0, int x, int y,
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
			int cx = (int) Math.floor((x0 - x) + tileRange);
			int cy = (int) Math.floor((y0 - y) + tileRange);
			int cz = (int) Math.floor((z0 - z) + tileRange);
			// Calculate cache key
			int keyPos = cx + cy * (tileRange * 2) + cz * (tileRange * 2) * (tileRange * 2);
			int entry = keyPos / 4;
			if (entry > cache.length - 1) {
				// FIXME: sometimes this happens... maybe we should doublecheck the range logic
				// Something went wrong... we exceeded the cache size!
				throw new RuntimeException(String.format("Illegal cache relative coordinates! xyz(%d;%d;%d) x0y0z0(%f;%f;%f) c0c0c0(%d;%d;%d) K:%d E:%d", x, y, z, x0, y0, z0, cx, cy, cz, keyPos, entry));
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
					long chunkKey = LocationUtilities.getChunkKey(chunkX, chunkZ);
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

	private void cacheResult(Vec3d vector, boolean result) {
		int cx = MathUtilities.fastFloor(vector.x + tileRange);
		int cy = MathUtilities.fastFloor(vector.y + tileRange);
		int cz = MathUtilities.fastFloor(vector.z + tileRange);
		if (result) {
			cache.setVisible(cx, cy, cz);
		} else {
			cache.setHidden(cx, cy, cz);
		}
	}

	public void resetCache() {
		this.cache.resetCache();
	}

	private enum Relative {
		INSIDE, POSITIVE, NEGATIVE;

		public static Relative from(int min, int max) {
			if (max > 0 && min > 0) {
				return POSITIVE;
			} else if (min < 0 && max <= 0) {
				return NEGATIVE;
			}
			return INSIDE;
		}
	}

}
