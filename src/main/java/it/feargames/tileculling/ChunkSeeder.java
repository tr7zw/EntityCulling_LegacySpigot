package it.feargames.tileculling;

import org.bukkit.entity.Player;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChunkSeeder {

	private final PlayerChunkTracker playerTracker;
	private final ChunkTileVisibilityManager chunkTileVisibilityManager;

	private final ExecutorService seederExecutor = Executors.newFixedThreadPool(10);

	public ChunkSeeder(PlayerChunkTracker playerTracker, ChunkTileVisibilityManager chunkTileVisibilityManager) {
		this.playerTracker = playerTracker;
		this.chunkTileVisibilityManager = chunkTileVisibilityManager;
	}

	public void shutdown() {
		seederExecutor.shutdownNow();
		try {
			seederExecutor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException ignored) {
		}
	}

	public void seedChunk(Player player, long chunkKey) {
		seederExecutor.submit(() -> {
			chunkTileVisibilityManager.seedChunk(player, chunkKey);
			playerTracker.trackChunk(player, chunkKey);
		});
	}
}
