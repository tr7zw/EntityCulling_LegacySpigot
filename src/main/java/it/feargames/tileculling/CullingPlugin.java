package it.feargames.tileculling;

import com.comphenix.protocol.ProtocolLibrary;
import it.feargames.tileculling.adapter.Adapter_1_16_R3;
import it.feargames.tileculling.adapter.IAdapter;
import it.feargames.tileculling.protocol.ChunkPacketListener;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CullingPlugin extends JavaPlugin {

	private SettingsHolder settings;
	private HiddenTileRegistry hiddenTileRegistry;

	private IAdapter adapter;
	private ChunkTileVisibilityManager chunkTileVisibilityManager;
	private PlayerChunkTracker playerChunkTracker;
	private ChunkCache chunkCache;
	private VisibilityCache visibilityCache;
	private ChunkPacketListener chunkPacketListener;

	private VisibilityUpdateThread visibilityUpdateThread;

	@Override
	public void onEnable() {
		saveDefaultConfig();

		settings = new SettingsHolder();
		settings.load(getConfig().getConfigurationSection("settings"));
		hiddenTileRegistry = new HiddenTileRegistry(getLogger());
		hiddenTileRegistry.load(getConfig().getConfigurationSection("hiddenTiles"));

		adapter = new Adapter_1_16_R3();
		playerChunkTracker = new PlayerChunkTracker(this);
		visibilityCache = new VisibilityCache();
		chunkCache = new ChunkCache(this, hiddenTileRegistry);
		chunkTileVisibilityManager = new ChunkTileVisibilityManager(settings, adapter, playerChunkTracker, visibilityCache, chunkCache);

		getServer().getPluginManager().registerEvents(playerChunkTracker, this);
		getServer().getPluginManager().registerEvents(chunkCache, this);
		getServer().getPluginManager().registerEvents(visibilityCache, this);

		chunkPacketListener = new ChunkPacketListener(this, hiddenTileRegistry, adapter, playerChunkTracker);
		ProtocolLibrary.getProtocolManager().addPacketListener(chunkPacketListener);

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

	// TODO: registry
	public static boolean isOccluding(Material material) {
		switch (material) {
			case BARRIER:
			case SPAWNER:
				return false;
		}
		return material.isOccluding();
	}
}
