package it.feargames.tileculling.occlusionculling;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

public class AxisAlignedBB {

	public double minx;
	public double miny;
	public double minz;
	public double maxx;
	public double maxy;
	public double maxz;

	public AxisAlignedBB(double minx, double miny, double minz, double maxx, double maxy, double maxz) {
		this.minx = minx;
		this.miny = miny;
		this.minz = minz;
		this.maxx = maxx;
		this.maxy = maxy;
		this.maxz = maxz;
	}

	public Vector getAABBMiddle(Location blockLoc) {
		return new Vector(minx + (maxx - minx) / 2d, miny + (maxy - miny) / 2d, minz + (maxz - minz) / 2d).add(blockLoc.toVector());
	}

	public Location getMinLocation(World world) {
		return new Location(world, minx, miny, minz);
	}

	public Location getMaxLocation(World world) {
		return new Location(world, maxx, maxy, maxz);
	}

	public double getWidth() {
		return maxx - minx;
	}

	public double getHeight() {
		return maxy - miny;
	}

	public double getDepth() {
		return maxz - minz;
	}

	@Override
	public String toString() {
		return minx + ":" + miny + ":" + minz + "_" + maxx + ":" + maxy + ":" + maxz;
	}

}
