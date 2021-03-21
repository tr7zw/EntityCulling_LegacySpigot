package it.feargames.tileculling.occlusionculling;

import com.logisticscraft.occlusionculling.DataProvider;
import com.logisticscraft.occlusionculling.util.Vec3d;
import it.feargames.tileculling.ChunkCache;
import it.feargames.tileculling.CullingPlugin;
import it.feargames.tileculling.util.LocationUtilities;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

public class PaperDataProvider implements DataProvider {

	private final ChunkCache chunkCache;

	private World world;
	private ChunkSnapshot snapshot;

	public PaperDataProvider(ChunkCache chunkCache) {
		this.chunkCache = chunkCache;
	}

	public void setWorld(World world) {
		this.world = world;
	}

	@Override
	public boolean prepareChunk(int chunkX, int chunkZ) {
		if (world == null) {
			throw new IllegalStateException("World not loaded into DataProvider!");
		}
		if (snapshot != null && chunkX == snapshot.getX() && chunkZ == snapshot.getZ()) {
			return true; // Already cached
		}

		long chunkKey = LocationUtilities.getChunkKey(chunkX, chunkZ);
		snapshot = chunkCache.getChunk(world, chunkKey);
		return snapshot != null;
	}

	@Override
	public boolean isOpaqueFullCube(int x, int y, int z) {
		if (snapshot == null) {
			throw new IllegalStateException("Chunk not loaded into DataProvider!");
		}
		int relativeX = x & 0xF;
		int relativeZ = z & 0xF;
		Material material = snapshot.getBlockType(relativeX, y, relativeZ);
		return CullingPlugin.isOccluding(material);
	}

	@Override
	public void cleanup() {
		snapshot = null;
	}

	@Override
	public void checkingPosition(Vec3d[] targetPoints, int size, Vec3d viewerPosition) {
	}
}
