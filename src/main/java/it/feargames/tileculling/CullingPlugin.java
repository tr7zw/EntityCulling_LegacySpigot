package it.feargames.tileculling;

import com.comphenix.protocol.ProtocolLibrary;
import it.feargames.tileculling.adapter.Adapter_1_16_R3;
import it.feargames.tileculling.adapter.IAdapter;
import it.feargames.tileculling.protocol.MapChunkPacketListener;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

// FIXME: fill player chunk tracker with chunks near player if the plugin is reloaded

public class CullingPlugin extends JavaPlugin {

	private IAdapter adapter;

	private ChunkTileVisibilityManager chunkTileVisibilityManager;
	private PlayerChunkTracker playerChunkTracker;
	private ChunkCache chunkCache;
	private VisibilityCache visibilityCache;
	private MapChunkPacketListener mapChunkPacketListener;
	private ChunkSeeder chunkSeeder;

	private VisibilityUpdateThread visibilityUpdateThread;

	@Override
	public void onEnable() {
		adapter = new Adapter_1_16_R3();
		playerChunkTracker = new PlayerChunkTracker();
		visibilityCache = new VisibilityCache();
		chunkCache = new ChunkCache(this);
		chunkTileVisibilityManager = new ChunkTileVisibilityManager(adapter, playerChunkTracker, visibilityCache, chunkCache);
		chunkSeeder = new ChunkSeeder(playerChunkTracker, chunkTileVisibilityManager);

		getServer().getPluginManager().registerEvents(playerChunkTracker, this);
		getServer().getPluginManager().registerEvents(chunkCache, this);
		getServer().getPluginManager().registerEvents(visibilityCache, this);

		mapChunkPacketListener = new MapChunkPacketListener(this, adapter, playerChunkTracker, chunkSeeder);
		ProtocolLibrary.getProtocolManager().addPacketListener(mapChunkPacketListener);

		visibilityUpdateThread = new VisibilityUpdateThread(chunkTileVisibilityManager);
		visibilityUpdateThread.start();
	}

	@Override
	public void onDisable() {
		// Remove packet listeners
		ProtocolLibrary.getProtocolManager().removePacketListeners(this);

		// Stop update task
		if (visibilityUpdateThread != null) {
			visibilityUpdateThread.shutdown();
		}

		// Stop seeder
		if (chunkSeeder != null) {
			chunkSeeder.shutdown();
		}
	}

	// TODO: create a registry
	// TODO: SIGNS

	public static boolean shouldHide(BlockState state) {
		return state instanceof BlockInventoryHolder
				|| state instanceof EnderChest
				|| state instanceof CreatureSpawner
				|| state instanceof EnchantingTable
				|| state instanceof Banner
				|| state instanceof Skull;
	}

	public static boolean shouldHide(String namespacedKey) {
		return namespacedKey.equals("minecraft:chest")
				|| namespacedKey.equals("minecraft:trapped_chest")
				|| namespacedKey.equals("minecraft:furnace")
				|| namespacedKey.equals("minecraft:dispenser")
				|| namespacedKey.equals("minecraft:dropper")
				|| namespacedKey.equals("minecraft:hopper")
				|| namespacedKey.equals("minecraft:brewing_stand")
				|| namespacedKey.endsWith("shulker_box")
				|| namespacedKey.equals("minecraft:barrel")
				|| namespacedKey.equals("minecraft:ender_chest")
				// Misc
				|| namespacedKey.equals("minecraft:spawner")
				|| namespacedKey.equals("minecraft:enchanting_table")
				// Heads/Skulls
				|| namespacedKey.equals("minecraft:player_head")
				|| namespacedKey.equals("minecraft:dragon_head")
				|| namespacedKey.equals("minecraft:creeper_head")
				|| namespacedKey.equals("minecraft:skeleton_skull")
				|| namespacedKey.equals("minecraft:wither_skeleton_skull")
				|| namespacedKey.equals("minecraft:zombie_head");
	}

	public static boolean isOccluding(Material material) {
		switch (material) {
			case BARRIER:
			case SPAWNER:
				return false;
		}
		// TODO: are we sure we want to use this?
		return material.isOccluding();
	}
}
