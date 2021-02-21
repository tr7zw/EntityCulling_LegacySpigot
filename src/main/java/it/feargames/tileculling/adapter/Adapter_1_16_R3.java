package it.feargames.tileculling.adapter;

import io.netty.buffer.ByteBuf;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlockEntityState;
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
	public void updateBlockData(Player player, Location location, BlockState block) {
		byte action;
		if (block instanceof org.bukkit.block.CreatureSpawner) {
			action = 1;
		} else if (block instanceof CommandBlock) {
			action = 2;
		} else if (block instanceof Beacon) {
			action = 3;
		} else if (block instanceof Skull) {
			action = 4;
		} else if (block instanceof Conduit) {
			action = 5;
		} else if (block instanceof Banner) {
			action = 6;
		} else if (block instanceof Structure) {
			action = 7;
		} else if (block instanceof EndGateway) {
			action = 8;
		} else if (block instanceof Sign) {
			action = 9;
		} else if (block instanceof Jigsaw) {
			action = 12;
		} else if (block instanceof Campfire) {
			action = 13;
		} else if (block instanceof Beehive) {
			action = 14;
		} else {
			// FIXME: bed (11) ? (probably existed only in pre 1.13?)
			return;
		}
		CraftBlockEntityState<?> craftBlockEntityState = (CraftBlockEntityState<?>) block;
		NBTTagCompound nbt = craftBlockEntityState.getSnapshotNBT();
		BlockPosition blockPosition = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		PacketPlayOutTileEntityData packet = new PacketPlayOutTileEntityData(blockPosition, action, nbt);
		CraftPlayer craftPlayer = (CraftPlayer) player;
		craftPlayer.getHandle().networkManager.sendPacket(packet);
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
