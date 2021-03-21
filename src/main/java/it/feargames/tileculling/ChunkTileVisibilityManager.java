package it.feargames.tileculling;

import com.logisticscraft.occlusionculling.OcclusionCullingInstance;
import com.logisticscraft.occlusionculling.util.AxisAlignedBB;
import com.logisticscraft.occlusionculling.util.Vec3d;
import it.feargames.tileculling.adapter.IAdapter;
import it.feargames.tileculling.occlusionculling.PaperDataProvider;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;

import java.util.List;

public class ChunkTileVisibilityManager {

	private final IAdapter adapter;
	private final PlayerChunkTracker playerTracker;
	private final VisibilityCache visibilityCache;
	private final ChunkCache chunkCache;

	private final PaperDataProvider dataProvider;
	private final OcclusionCullingInstance culling;

	public ChunkTileVisibilityManager(SettingsHolder settings, IAdapter adapter, PlayerChunkTracker playerTracker, VisibilityCache visibilityCache, ChunkCache chunkCache) {
		this.adapter = adapter;
		this.playerTracker = playerTracker;
		this.visibilityCache = visibilityCache;
		this.chunkCache = chunkCache;

		dataProvider = new PaperDataProvider(chunkCache);
		culling = new OcclusionCullingInstance(settings.getTileRange(), dataProvider);
	}

	public void updateVisibility(Player player) {
		World world = player.getWorld();
		Location playerEyeLocation = player.getEyeLocation();
		if (playerEyeLocation.getY() > 255 || playerEyeLocation.getY() < 0) { // TODO: 1.17
			return;
		}

		Vec3d viewerPosition = new Vec3d(playerEyeLocation.getX(), playerEyeLocation.getY(), playerEyeLocation.getZ());

		culling.resetCache();
		dataProvider.setWorld(world);

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
				AxisAlignedBB blockAABB = new AxisAlignedBB(block.getX(), block.getY(), block.getZ(),
						block.getX() + 1, block.getY() + 1, block.getZ() + 1);
				Location bloc = block.getLocation();
				boolean canSee = culling.isAABBVisible(blockAABB, viewerPosition);
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

		// Prevent memory leak
		dataProvider.setWorld(null);
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
