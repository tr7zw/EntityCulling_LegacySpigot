package it.feargames.tileculling.adapter;

import com.comphenix.protocol.events.AbstractStructure;
import com.comphenix.protocol.events.PacketContainer;
import io.netty.buffer.ByteBuf;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.List;

public interface IAdapter {

	void updateBlockState(Player player, Location location, BlockData blockData);

	void updateBlockData(Player player, Location location, BlockState block);

	ByteBuf packetDataSerializer(ByteBuf byteBuf);

	int readVarInt(ByteBuf byteBuf);

	void writeVarInt(ByteBuf byteBuf, int value);

	default int getChunkPacketBitmask(AbstractStructure packet) {
		return -1; // All bits at 1
	}

	default AbstractStructure getChunkData(PacketContainer packet) {
		return packet;
	}

	List<?> getChunkTileEntities(AbstractStructure chunkData);

	void writeChunkTileEntities(AbstractStructure chunkData, List<?> tileEntities);

	String getChunkTileEntityType(Object tileEntity);

	int getChunkTileEntityXZ(Object tileEntity);

	int getChunkTileEntityY(Object tileEntity);

	int ceilLog2(int value);

	int getBlockStateRegistrySize();

	default boolean hasVerticalBiomes() {
		return false;
	}
}
