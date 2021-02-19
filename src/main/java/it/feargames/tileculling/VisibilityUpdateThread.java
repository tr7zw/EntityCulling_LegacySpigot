package it.feargames.tileculling;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

public class VisibilityUpdateThread extends Thread {

	public static final int TASK_INTERVAL = 50;

	private final ChunkTileVisibilityManager chunkTileVisibilityManager;

	private final AtomicBoolean running = new AtomicBoolean(false);

	public VisibilityUpdateThread(ChunkTileVisibilityManager chunkTileVisibilityManager) {
		this.chunkTileVisibilityManager = chunkTileVisibilityManager;
	}

	@Override
	public synchronized void start() {
		running.set(true);
		super.start();
	}

	public void shutdown() {
		running.set(false);
		interrupt();
	}

	@Override
	public void run() {
		while (running.get()) {
			long start = System.currentTimeMillis();
			for (Player player : Bukkit.getOnlinePlayers()) {
				chunkTileVisibilityManager.updateVisibility(player);
			}
			long took = System.currentTimeMillis() - start;
			long sleep = Math.max(0, TASK_INTERVAL - took);
			if (sleep > 0) {
				try {
					//noinspection BusyWait
					Thread.sleep(sleep);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

}
