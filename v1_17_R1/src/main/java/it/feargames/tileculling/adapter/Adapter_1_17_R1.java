package it.feargames.tileculling.adapter;

import com.comphenix.protocol.events.AbstractStructure;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_17_R1.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.v1_17_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;

public class Adapter_1_17_R1 implements IAdapter {

	@Override
	public void updateBlockState(Player player, Location location, BlockData blockData) {
		CraftPlayer craftPlayer = (CraftPlayer) player;
		ServerPlayer handlePlayer = craftPlayer.getHandle();
		ServerGamePacketListenerImpl connection = handlePlayer.connection;
		if (connection == null) {
			return;
		}

		BlockPos blockPosition = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		net.minecraft.world.level.block.state.BlockState handleData = blockData == null ? Blocks.AIR.defaultBlockState() : ((CraftBlockData) blockData).getState();
		ClientboundBlockUpdatePacket blockChange = new ClientboundBlockUpdatePacket(blockPosition, handleData);
		connection.send(blockChange);
	}

	@Override
	public void updateBlockData(Player player, Location location, BlockState block) {
		CraftPlayer craftPlayer = (CraftPlayer) player;
		ServerPlayer handlePlayer = craftPlayer.getHandle();
		ServerGamePacketListenerImpl connection = handlePlayer.connection;
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
		CompoundTag nbt = craftBlockEntityState.getSnapshotNBT();
		BlockPos blockPosition = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		ClientboundBlockEntityDataPacket packet = new ClientboundBlockEntityDataPacket(blockPosition, action, nbt);
		connection.send(packet);
	}

	@Override
	public ByteBuf packetDataSerializer(ByteBuf byteBuf) {
		return new FriendlyByteBuf(byteBuf);
	}

	@Override
	public int readVarInt(ByteBuf byteBuf) {
		FriendlyByteBuf serializer = (FriendlyByteBuf) byteBuf;
		return serializer.readVarInt();
	}

	@Override
	public void writeVarInt(ByteBuf byteBuf, int value) {
		FriendlyByteBuf serializer = (FriendlyByteBuf) byteBuf;
		serializer.writeVarInt(value);
	}

	@Override
	public int getChunkPacketBitmask(AbstractStructure container) {
		ClientboundLevelChunkPacket packet = (ClientboundLevelChunkPacket) container.getHandle();
		return (int) packet.getAvailableSections().toLongArray()[0];
	}

	@Override
	public List<?> getChunkTileEntities(AbstractStructure chunkData) {
		return chunkData.getListNbtModifier().read(0);
	}

	@Override
	public void writeChunkTileEntities(AbstractStructure chunkData, List<?> tileEntities) {
		//noinspection unchecked
		chunkData.getListNbtModifier().write(0, (List<NbtBase<?>>) tileEntities);
	}

	@Override
	public String getChunkTileEntityType(Object tileEntity) {
		return ((NbtCompound) tileEntity).getString("id");
	}

	@Override
	public int getChunkTileEntityXZ(Object tileEntity) {
		return (((NbtCompound) tileEntity).getInteger("x") & 0xF) << 4 | (((NbtCompound) tileEntity).getInteger("z") & 0xF);
	}

	@Override
	public int getChunkTileEntityY(Object tileEntity) {
		return ((NbtCompound) tileEntity).getInteger("y");
	}

	@Override
	public int ceilLog2(int value) {
		return Mth.ceillog2(value);
	}

	@Override
	public int getBlockStateRegistrySize() {
		return net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.size();
	}

}
