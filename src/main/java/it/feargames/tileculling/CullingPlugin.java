package it.feargames.tileculling;

import com.comphenix.protocol.ProtocolLibrary;
import com.destroystokyo.paper.MaterialTags;
import it.feargames.tileculling.adapter.Adapter_1_16_R3;
import it.feargames.tileculling.adapter.IAdapter;
import it.feargames.tileculling.protocol.MapChunkPacketListener;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CullingPlugin extends JavaPlugin {

	private IAdapter adapter;

	private ChunkTileVisibilityManager chunkTileVisibilityManager;
	private PlayerChunkTracker playerChunkTracker;
	private ChunkCache chunkCache;
	private VisibilityCache visibilityCache;
	private MapChunkPacketListener mapChunkPacketListener;

	private VisibilityUpdateThread visibilityUpdateThread;

	@Override
	public void onEnable() {
		adapter = new Adapter_1_16_R3();
		playerChunkTracker = new PlayerChunkTracker(this);
		visibilityCache = new VisibilityCache();
		chunkCache = new ChunkCache(this);
		chunkTileVisibilityManager = new ChunkTileVisibilityManager(adapter, playerChunkTracker, visibilityCache, chunkCache);

		getServer().getPluginManager().registerEvents(playerChunkTracker, this);
		getServer().getPluginManager().registerEvents(chunkCache, this);
		getServer().getPluginManager().registerEvents(visibilityCache, this);

		mapChunkPacketListener = new MapChunkPacketListener(this, adapter, playerChunkTracker);
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

		// Restore visibility
		if (chunkTileVisibilityManager != null) {
			for (Player player : getServer().getOnlinePlayers()) {
				chunkTileVisibilityManager.restoreVisibility(player);
			}
		}
	}

	// TODO: create a registry

	private static final Material[] hiddenMaterials;
	private static final String[] hiddenNamespaces;

	static {
		List<Material> materials = new ArrayList<>(Arrays.asList(
				Material.CHEST,
				Material.TRAPPED_CHEST,
				Material.ENDER_CHEST,
				Material.FURNACE,
				Material.DISPENSER,
				Material.DROPPER,
				Material.HOPPER,
				Material.BREWING_STAND,
				Material.BARREL,
				Material.SPAWNER,
				Material.ENCHANTING_TABLE

		));
		materials.addAll(MaterialTags.SHULKER_BOXES.getValues());
		materials.addAll(MaterialTags.SKULLS.getValues());
		materials.addAll(MaterialTags.SIGNS.getValues());
		// Cache values
		hiddenMaterials = materials.toArray(new Material[0]);
		hiddenNamespaces = materials.stream().map(material -> material.getKey().toString()).toArray(String[]::new);
	}

	public static boolean shouldHide(String namespacedKey) {
		for (String current : hiddenNamespaces) {
			if (current.equals(namespacedKey)) {
				return true;
			}
		}
		return true;
	}

	public static boolean shouldHide(Material material) {
		for (Material current : hiddenMaterials) {
			if (current == material) {
				return true;
			}
		}
		return true;
	}

	public static boolean shouldHide(BlockState state) {
		return shouldHide(state.getType());
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
