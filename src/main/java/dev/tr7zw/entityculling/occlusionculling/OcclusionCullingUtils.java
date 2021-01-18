package dev.tr7zw.entityculling.occlusionculling;

import java.util.Arrays;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import dev.tr7zw.entityculling.AxisAlignedBB;
import dev.tr7zw.entityculling.CullingPlugin;
import dev.tr7zw.entityculling.occlusionculling.BlockChangeListener.ChunkCoords;

public class OcclusionCullingUtils {

	public static boolean isAABBVisible(Location aabbBlock, AxisAlignedBB aabb, Location playerLoc, boolean entity) {
		try {
			double width = aabb.getWidth();
			double height = aabb.getHeight();
			double depth = aabb.getDepth();
			Location center = entity ? aabbBlock.clone().add(0, height/2, 0) : aabb.getAABBMiddle(aabbBlock).toLocation(aabbBlock.getWorld());
			Location centerXMin = center.clone().add(-width / 2, 0, 0);
			Location centerXMax = center.clone().add(width / 2, 0, 0);
			Location centerYMin = center.clone().add(0, -height / 2, 0);
			Location centerYMax = center.clone().add(0, height / 2, 0);
			Location centerZMin = center.clone().add(0, 0, -depth / 2);
			Location centerZMax = center.clone().add(0, 0, depth / 2);
			Vector[] targets = new Vector[4];
			targets[0] = centerYMin.subtract(playerLoc).toVector();
			targets[1] = centerYMax.subtract(playerLoc).toVector();
		
			if(centerXMin.distanceSquared(playerLoc) > centerXMax.distanceSquared(playerLoc)) {
				targets[2] = centerXMin.subtract(playerLoc).toVector();
			}else {
				targets[2] = centerXMax.subtract(playerLoc).toVector();
			}
			if(centerZMin.distanceSquared(playerLoc) > centerZMax.distanceSquared(playerLoc)) {
				targets[3] = centerZMin.subtract(playerLoc).toVector();
			}else {
				targets[3] = centerZMax.subtract(playerLoc).toVector();
			}
			if(isVisible(playerLoc, targets))return true;

			return false;

		} catch (Exception exception) {
			exception.printStackTrace();
		}
		return true;
	}
	
	private static byte[] cache = new byte[40000];
	
	/**
	 * returns the grid cells that intersect with this vector<br>
	 * <a href=
	 * "http://playtechs.blogspot.de/2007/03/raytracing-on-grid.html">http://playtechs.blogspot.de/2007/03/raytracing-on-grid.html</a>
	 */
	private static boolean isVisible(Location start, Vector[] targets) {
		int maxX = 0;
		int maxY = 0;
		int maxZ = 0;
		for(int i = 0; i < targets.length; i++) {
			maxX = Math.max(maxX, Math.abs(targets[i].getBlockX()));
			maxY = Math.max(maxY, Math.abs(targets[i].getBlockY()));
			maxZ = Math.max(maxZ, Math.abs(targets[i].getBlockZ()));
		}
		int size = (maxX+1) * (maxY+1) * (maxZ+1);
		if(size > 40000)return false;
		Arrays.fill(cache, (byte)0);
		
		for(int v = 0; v < targets.length; v++) {
			Vector target = targets[v];
			// coordinates of start and target point
			double x0 = start.getX();
			double y0 = start.getY();
			double z0 = start.getZ();
			double x1 = x0 + target.getX();
			double y1 = y0 + target.getY();
			double z1 = z0 + target.getZ();
	
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
	
			boolean finished =stepRay(start, cache, maxX, maxY, maxZ, x0, y0, z0, x, y, z, dt_dx, dt_dy, dt_dz, n, x_inc, y_inc, z_inc, t_next_y, t_next_x,
					t_next_z);
			if(finished) {
				return true;
			}
		}
		return false;
	}

	private static boolean stepRay(Location start, byte[] cache, int maxX, int maxY, int maxZ, double x0, double y0, double z0, int x, int y,
			int z, double dt_dx, double dt_dy, double dt_dz, int n, int x_inc, int y_inc, int z_inc, double t_next_y,
			double t_next_x, double t_next_z) {
		int chunkX = (int) Math.floor(x / 16d);
		int chunkZ = (int) Math.floor(z / 16d);
		String worldName = start.getWorld().getName();
		ChunkCoords cc = new ChunkCoords(worldName, chunkX, chunkZ);
		ChunkSnapshot snapshot = CullingPlugin.instance.blockChangeListener.cachedChunkSnapshots.get(cc);
		
		if(snapshot == null)return false;
		
		// iterate through all intersecting cells (n times)
		for (; n > 0; n--) {
			int cx = (int) Math.abs(x0 - x);
			int cy = (int) Math.abs(y0 - y);
			int cz = (int) Math.abs(z0 - z);
			int key = cx + cy*maxX + cz*maxX*maxY;
			byte cVal = cache[key];
			if(cVal == 2) {
				return false;
			}
			if(cVal == 0) {
				// save current cell
				Vector cp = new Vector(x, y, z);
				chunkX = (int) Math.floor(x / 16d);
				chunkZ = (int) Math.floor(z / 16d);
				if(cc.chunkX != chunkX || cc.chunkZ != chunkZ) {
					cc = new ChunkCoords(worldName, chunkX, chunkZ);
					snapshot = CullingPlugin.instance.blockChangeListener.cachedChunkSnapshots.get(cc);
					if(snapshot == null) {
						//cache[cx][cy][cz] = 2;
						return false;
					}
				}
				
				int relativeX = x % 16;
				if (relativeX < 0) {
					relativeX = 16 + relativeX;
				}
				int relativeZ = z % 16;
				if (relativeZ < 0) {
					relativeZ = 16 + relativeZ;
				}
				if (relativeX < 0 || relativeX > 15) {
					cache[key] = 2;
					return false;
				}
				if (relativeZ < 0 || relativeZ > 15) {
					cache[key] = 2;
					return false;
				}
				if (y < 0 || y > 255) {
					cache[key] = 2;
					return false;
				}
				Material material = snapshot.getBlockType(relativeX, cp.getBlockY(), relativeZ);
				if(material.isOccluding() && material != Material.SPAWNER) {
					//System.out.println(cx + " " + cy + " " + cz);
					cache[key] = 2;
					return false;
				}
				cache[key] = 1;
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
