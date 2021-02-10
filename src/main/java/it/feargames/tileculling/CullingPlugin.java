package it.feargames.tileculling;

import it.feargames.tileculling.occlusionculling.BlockChangeListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public class CullingPlugin extends JavaPlugin {

	public static final int TASK_INTERVAL = 50;

	public static CullingPlugin instance;

	public BlockChangeListener blockChangeListener;
	public PlayerCache cache;

	private Thread thread;

	@Override
	public void onEnable() {
		instance = this;

		blockChangeListener = new BlockChangeListener();
		cache = new PlayerCache();

		getServer().getPluginManager().registerEvents(blockChangeListener, this);
		getServer().getPluginManager().registerEvents(cache, this);

		Runnable task = new CullTask(this);
		thread = new Thread(() -> {
			while (true) {
				long start = System.currentTimeMillis();
				task.run();
				long took = System.currentTimeMillis() - start;
				long sleep = Math.max(0, TASK_INTERVAL - took);
				if (sleep > 0) {
					try {
						Thread.sleep(sleep);
					} catch (InterruptedException e) {
					}
				}
			}
		});
		thread.start();
	}

	@Override
	public void onDisable() {
		if (thread != null) {
			thread.interrupt();
		}
	}

	public static void runTask(Runnable task) {
		if (!CullingPlugin.instance.isEnabled()) {
			return;
		}
		Bukkit.getScheduler().runTask(CullingPlugin.instance, task);
	}

	public static boolean shouldHide(BlockState state) {
		return state instanceof BlockInventoryHolder
				|| state instanceof EnderChest
				|| state instanceof CreatureSpawner
				|| state instanceof EnchantingTable
				|| state instanceof Banner
				|| state instanceof Skull
				//|| state instanceof Sign
				;
	}

	// TODO: improve
	public static boolean isOccluding(Material material) {
		if (material.isAir()) {
			return false;
		}
		switch (material) {
			case CHEST:
			case TRAPPED_CHEST:
			case ENDER_CHEST:
			case WATER:
			case BARRIER:
			case SPAWNER:
			case BEACON:
				return false;
		}
		// TODO: are we sure we want to use this?
		return material.isOccluding();
	}

}
