package it.feargames.tileculling;

import it.feargames.tileculling.adapter.IAdapter;
import it.feargames.tileculling.occlusionculling.AxisAlignedBB;
import it.feargames.tileculling.occlusionculling.OcclusionCulling;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class ChunkTileVisibilityManager {

	private static final AxisAlignedBB BLOCK_AABB = new AxisAlignedBB(0d, 0d, 0d, 1d, 1d, 1d);

	private final IAdapter adapter;
	private final PlayerChunkTracker playerTracker;
	private final VisibilityCache visibilityCache;
	private final ChunkCache chunkCache;

	private final OcclusionCulling culling;

	public ChunkTileVisibilityManager(SettingsHolder settings, IAdapter adapter, PlayerChunkTracker playerTracker, VisibilityCache visibilityCache, ChunkCache chunkCache) {
		this.adapter = adapter;
		this.playerTracker = playerTracker;
		this.visibilityCache = visibilityCache;
		this.chunkCache = chunkCache;

		this.culling = new OcclusionCulling(chunkCache, settings.getTileRange());
	}

	public void updateVisibility(Player player) {
		World world = player.getWorld();
		Vector playerEyeLocation = player.getEyeLocation().toVector();

		culling.resetCache();

		long[] trackedChunks = playerTracker.getTrackedChunks(player);
		if (trackedChunks == null) {
			return;
		}
		for (long chunkKey : trackedChunks) {
			List<BlockState> tiles = chunkCache.getChunkTiles(world, chunkKey);
			if (tiles == null) {
				continue;
			}
			for (BlockState block : tiles) {
				Location bloc = block.getLocation();
				boolean canSee = culling.isAABBVisible(player, playerEyeLocation, bloc, BLOCK_AABB);
				boolean hidden = visibilityCache.isHidden(player, bloc);
				if (hidden && canSee) {
					visibilityCache.setHidden(player, bloc, false);
					adapter.updateBlockState(player, bloc, block.getBlockData());
					if (block instanceof TileState) {
						adapter.updateBlockData(player, bloc, block);

					}
				} else if (!hidden && !canSee) {
					visibilityCache.setHidden(player, bloc, true);
					adapter.updateBlockState(player, bloc, null);
				}
			}
		}
	}

	public void restoreVisibility(Player player) {
		World world = player.getWorld();
		long[] trackedChunks = playerTracker.getTrackedChunks(player);
		if (trackedChunks == null) {
			return;
		}
		for (long chunkKey : trackedChunks) {
			List<BlockState> tiles = chunkCache.getChunkTiles(world, chunkKey);
			if (tiles == null) {
				continue;
			}
			for (BlockState block : tiles) {
				Location bloc = block.getLocation();
				adapter.updateBlockState(player, bloc, block.getBlockData());
				adapter.updateBlockData(player, bloc, block);
			}
		}
	}
}
