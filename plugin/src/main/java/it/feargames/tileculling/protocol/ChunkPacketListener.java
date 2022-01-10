package it.feargames.tileculling.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.*;
import it.feargames.tileculling.CullingPlugin;
import it.feargames.tileculling.HiddenTileRegistry;
import it.feargames.tileculling.PlayerChunkTracker;
import it.feargames.tileculling.adapter.IAdapter;
import it.feargames.tileculling.util.LocationUtilities;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class ChunkPacketListener extends PacketAdapter {

	private final HiddenTileRegistry hiddenTileRegistry;
	private final IAdapter adapter;
	private final PlayerChunkTracker playerChunkTracker;

	public ChunkPacketListener(CullingPlugin plugin, HiddenTileRegistry hiddenTileRegistry, IAdapter adapter, PlayerChunkTracker playerChunkTracker) {
		super(plugin, ListenerPriority.HIGHEST, Arrays.asList(PacketType.Play.Server.MAP_CHUNK, PacketType.Play.Server.UNLOAD_CHUNK), ListenerOptions.ASYNC);
		this.hiddenTileRegistry = hiddenTileRegistry;
		this.plugin = plugin;
		this.adapter = adapter;
		this.playerChunkTracker = playerChunkTracker;
	}

	@Override
	public void onPacketSending(PacketEvent event) {
		Player player = event.getPlayer();
		PacketContainer packet = event.getPacket();

		int chunkX = packet.getIntegers().read(0);
		int chunkZ = packet.getIntegers().read(1);
		long chunkKey = LocationUtilities.getChunkKey(chunkX, chunkZ);
		if (packet.getType() == PacketType.Play.Server.MAP_CHUNK) {
			adapter.transformPacket(player, packet, hiddenTileRegistry::shouldHide);
			playerChunkTracker.trackChunk(player, chunkKey);
		} else if (packet.getType() == PacketType.Play.Server.UNLOAD_CHUNK) {
			playerChunkTracker.untrackChunk(player, chunkKey);
		}
	}

}
