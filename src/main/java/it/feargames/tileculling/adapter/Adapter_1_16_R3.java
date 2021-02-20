package it.feargames.tileculling.adapter;

import io.netty.buffer.ByteBuf;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_16_R3.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers;
import org.bukkit.entity.Player;

public class Adapter_1_16_R3 implements IAdapter {

	private void updateBlockState(Player player, Location location, IBlockData blockData) {
		BlockPosition blockPosition = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		PacketPlayOutBlockChange blockChange = new PacketPlayOutBlockChange(blockPosition, blockData);
		CraftPlayer craftPlayer = (CraftPlayer) player;
		craftPlayer.getHandle().networkManager.sendPacket(blockChange);
	}

	@Override
	public void updateBlockState(Player player, Location location, BlockData blockData) {
		CraftBlockData craftBlockData = (CraftBlockData) blockData;
		updateBlockState(player, location, craftBlockData.getState());
	}

	@Override
	public void updateBlockState(Player player, Location location, Material material, byte data) {
		IBlockData blockData = CraftMagicNumbers.getBlock(material, data);
		updateBlockState(player, location, blockData);
	}

	@Override
	public ByteBuf packetDataSerializer(ByteBuf byteBuf) {
		return new PacketDataSerializer(byteBuf);
	}

	@Override
	public int readVarInt(ByteBuf byteBuf) {
		PacketDataSerializer serializer = (PacketDataSerializer) byteBuf;
		return serializer.readVarInt();
	}

	@Override
	public void writeVarInt(ByteBuf byteBuf, int value) {
		PacketDataSerializer serializer = (PacketDataSerializer) byteBuf;
		serializer.d(value);
	}
}
