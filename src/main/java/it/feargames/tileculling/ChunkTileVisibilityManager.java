package it.feargames.tileculling;

import it.feargames.tileculling.adapter.IAdapter;
import it.feargames.tileculling.occlusionculling.AxisAlignedBB;
import it.feargames.tileculling.occlusionculling.OcclusionCulling;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class ChunkTileVisibilityManager {

	public static final int CHUNK_RANGE = 6; // TODO: makes no sense to have a chunk range, not this way at least
	public static final AxisAlignedBB BLOCK_AABB = new AxisAlignedBB(0d, 0d, 0d, 1d, 1d, 1d);

	private final IAdapter adapter;
	private final PlayerChunkTracker playerTracker;
	private final VisibilityCache visibilityCache;
	private final ChunkCache chunkCache;

	private final OcclusionCulling culling;

	public ChunkTileVisibilityManager(IAdapter adapter, PlayerChunkTracker playerTracker, VisibilityCache visibilityCache, ChunkCache chunkCache) {
		this.adapter = adapter;
		this.playerTracker = playerTracker;
		this.visibilityCache = visibilityCache;
		this.chunkCache = chunkCache;

		this.culling = new OcclusionCulling(chunkCache);
	}

	public void updateVisibility(Player player) {
		World world = player.getWorld();
		Location playerLocation = player.getLocation();

		Vector playerEyeLocation = player.getEyeLocation().toVector();

		int playerChunkX = playerLocation.getBlockX() >> 4;
		int playerChunkZ = playerLocation.getBlockZ() >> 4;

		culling.resetCache();

		for (int x = -CHUNK_RANGE; x <= CHUNK_RANGE; x++) {
			for (int z = -CHUNK_RANGE; z <= CHUNK_RANGE; z++) {
				int chunkX = playerChunkX + x;
				int chunkZ = playerChunkZ + z;
				long chunkKey = Chunk.getChunkKey(chunkX, chunkZ);

				if (!playerTracker.isChunkTracked(player, chunkKey)) {
					continue;
				}

				List<BlockState> tiles = chunkCache.getChunkTiles(world, chunkKey);
				if (tiles == null) {
					continue;
				}
				for (BlockState block : tiles) {
					Location bloc = block.getLocation();
					boolean canSee = culling.isAABBVisible(player, playerEyeLocation, bloc, BLOCK_AABB);
					boolean hidden = visibilityCache.isHidden(player, block.getLocation());
					if (hidden && canSee) {
						visibilityCache.setHidden(player, block.getLocation(), false);
						adapter.updateBlockState(player, block.getLocation(), block.getBlockData());
					} else if (!hidden && !canSee) {
						visibilityCache.setHidden(player, block.getLocation(), true);
						adapter.updateBlockState(player, block.getLocation(), Material.AIR, (byte) 0);
					}
				}
			}
		}
	}
}
