package it.feargames.tileculling.adapter;

import io.netty.buffer.ByteBuf;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

public interface IAdapter {

	void updateBlockState(Player player, Location location, BlockData blockData);

	void updateBlockState(Player player, Location location, Material material, byte data);

	ByteBuf packetDataSerializer(ByteBuf byteBuf);

	int readVarInt(ByteBuf byteBuf);

	void writeVarInt(ByteBuf byteBuf, int value);
}
