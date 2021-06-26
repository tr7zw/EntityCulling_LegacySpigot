package it.feargames.tileculling.adapter;

import com.comphenix.protocol.events.PacketContainer;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPosition;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketDataSerializer;
import net.minecraft.network.protocol.game.PacketPlayOutBlockChange;
import net.minecraft.network.protocol.game.PacketPlayOutMapChunk;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_17_R1.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.v1_17_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class Adapter_1_17_R1 implements IAdapter {

	@Override
	public void updateBlockState(Player player, Location location, BlockData blockData) {
		CraftPlayer craftPlayer = (CraftPlayer) player;
		EntityPlayer handlePlayer = craftPlayer.getHandle();
		PlayerConnection connection = handlePlayer.b; // playerConnection
		if (connection == null) {
			return;
		}

		BlockPosition blockPosition = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		IBlockData handleData = blockData == null ? Blocks.a.getBlockData() /* Blocks.AIR */ : ((CraftBlockData) blockData).getState();
		PacketPlayOutBlockChange blockChange = new PacketPlayOutBlockChange(blockPosition, handleData);
		connection.sendPacket(blockChange);
	}

	@Override
	public void updateBlockData(Player player, Location location, BlockState block) {
		CraftPlayer craftPlayer = (CraftPlayer) player;
		EntityPlayer handlePlayer = craftPlayer.getHandle();
		PlayerConnection connection = handlePlayer.b; // playerConnection
		if (connection == null) {
			return;
		}

		byte action;
		if (block instanceof CreatureSpawner) {
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
		} else if (block instanceof Bed) {
			action = 11;
		} else if (block instanceof Jigsaw) {
			action = 12;
		} else if (block instanceof Campfire) {
			action = 13;
		} else if (block instanceof Beehive) {
			action = 14;
		} else {
			return;
		}

		CraftBlockEntityState<?> craftBlockEntityState = (CraftBlockEntityState<?>) block;
		NBTTagCompound nbt = craftBlockEntityState.getSnapshotNBT();
		BlockPosition blockPosition = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		PacketPlayOutTileEntityData packet = new PacketPlayOutTileEntityData(blockPosition, action, nbt);
		connection.sendPacket(packet);
	}

	@Override
	public ByteBuf packetDataSerializer(ByteBuf byteBuf) {
		return new PacketDataSerializer(byteBuf);
	}

	@Override
	public int readVarInt(ByteBuf byteBuf) {
		PacketDataSerializer serializer = (PacketDataSerializer) byteBuf;
		return serializer.j();
	}

	@Override
	public void writeVarInt(ByteBuf byteBuf, int value) {
		PacketDataSerializer serializer = (PacketDataSerializer) byteBuf;
		serializer.d(value);
	}

	@Override
	public int getChunkPacketBitmask(PacketContainer container) {
		PacketPlayOutMapChunk packet = (PacketPlayOutMapChunk) container.getHandle();
		return (int) packet.e().toLongArray()[0];
	}
}
