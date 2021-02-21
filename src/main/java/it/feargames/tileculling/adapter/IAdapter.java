package it.feargames.tileculling.adapter;

import com.comphenix.protocol.events.PacketContainer;
import io.netty.buffer.ByteBuf;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

public interface IAdapter {

	void updateBlockState(Player player, Location location, BlockData blockData);

	void updateBlockData(Player player, Location location, BlockState block);

	boolean isCustomPacket(PacketContainer container);

	ByteBuf packetDataSerializer(ByteBuf byteBuf);

	int readVarInt(ByteBuf byteBuf);

	void writeVarInt(ByteBuf byteBuf, int value);
}
