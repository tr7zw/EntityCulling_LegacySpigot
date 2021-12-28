package it.feargames.tileculling.adapter;

import com.comphenix.protocol.events.AbstractStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_18_R1.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.v1_18_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;

public class Adapter_1_18_R1 implements IAdapter {

	private static final Constructor<ClientboundBlockEntityDataPacket> BLOCK_ENTITY_DATA_PACKET_CONSTRUCTOR;
	private static final Field BLOCK_ENTITIES_DATA_FIELD;

	private static final Class<?> BLOCK_ENTITY_INFO_CLASS;
	private static final Field BLOCK_ENTITY_INFO_TYPE_FIELD;
	private static final Field BLOCK_ENTITY_INFO_XZ_FIELD;
	private static final Field BLOCK_ENTITY_INFO_Y_FIELD;

	static {
		try {
			BLOCK_ENTITY_DATA_PACKET_CONSTRUCTOR = ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(
					BlockPos.class, BlockEntityType.class, CompoundTag.class);
			BLOCK_ENTITY_DATA_PACKET_CONSTRUCTOR.setAccessible(true);
			BLOCK_ENTITIES_DATA_FIELD = ClientboundLevelChunkPacketData.class.getDeclaredField("d"); // blockEntitiesData
			BLOCK_ENTITIES_DATA_FIELD.setAccessible(true);

			BLOCK_ENTITY_INFO_CLASS = ClientboundLevelChunkPacketData.class.getDeclaredClasses()[0];
			BLOCK_ENTITY_INFO_TYPE_FIELD = BLOCK_ENTITY_INFO_CLASS.getDeclaredField("c"); // type
			BLOCK_ENTITY_INFO_TYPE_FIELD.setAccessible(true);
			BLOCK_ENTITY_INFO_XZ_FIELD = BLOCK_ENTITY_INFO_CLASS.getDeclaredField("a"); // packedXZ
			BLOCK_ENTITY_INFO_XZ_FIELD.setAccessible(true);
			BLOCK_ENTITY_INFO_Y_FIELD = BLOCK_ENTITY_INFO_CLASS.getDeclaredField("b"); // y
			BLOCK_ENTITY_INFO_Y_FIELD.setAccessible(true);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void updateBlockState(Player player, Location location, BlockData blockData) {
		CraftPlayer craftPlayer = (CraftPlayer) player;
		ServerPlayer handlePlayer = craftPlayer.getHandle();
		ServerPlayerConnection connection = handlePlayer.connection;
		if (connection == null) {
			return;
		}

		BlockPos blockPosition = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		BlockState handleData = blockData == null ? Blocks.AIR.defaultBlockState() : ((CraftBlockData) blockData).getState();
		ClientboundBlockUpdatePacket blockChange = new ClientboundBlockUpdatePacket(blockPosition, handleData);
		connection.send(blockChange);
	}

	@Override
	public void updateBlockData(Player player, Location location, org.bukkit.block.BlockState block) {
		CraftPlayer craftPlayer = (CraftPlayer) player;
		ServerPlayer handlePlayer = craftPlayer.getHandle();
		ServerPlayerConnection connection = handlePlayer.connection;
		if (connection == null) {
			return;
		}

		BlockEntityType<?> type;
		if (block instanceof Smoker) {
			type = BlockEntityType.SMOKER;
		} else if (block instanceof BlastFurnace) {
			type = BlockEntityType.BLAST_FURNACE;
		} else if (block instanceof Furnace) {
			type = BlockEntityType.FURNACE;
		} else if (block instanceof Chest) {
			type = block.getType() == Material.TRAPPED_CHEST ? BlockEntityType.TRAPPED_CHEST : BlockEntityType.CHEST; // FIXME
		} else if (block instanceof EnderChest) {
			type = BlockEntityType.ENDER_CHEST;
		} else if (block instanceof Jukebox) {
			type = BlockEntityType.JUKEBOX;
		} else if (block instanceof Dispenser) {
			type = BlockEntityType.DISPENSER;
		} else if (block instanceof Dropper) {
			type = BlockEntityType.DROPPER;
		} else if (block instanceof Sign) {
			type = BlockEntityType.SIGN;
		} else if (block instanceof CreatureSpawner) {
			type = BlockEntityType.MOB_SPAWNER;
		} else if (block.getType() == Material.PISTON_HEAD) { // FIXME
			type = BlockEntityType.PISTON;
		} else if (block instanceof BrewingStand) {
			type = BlockEntityType.BREWING_STAND;
		} else if (block instanceof EnchantingTable) {
			type = BlockEntityType.ENCHANTING_TABLE;
		} else if (block.getType() == Material.END_PORTAL) { // FIXME
			type = BlockEntityType.END_PORTAL;
		} else if (block instanceof Beacon) {
			type = BlockEntityType.BEACON;
		} else if (block instanceof Skull) {
			type = BlockEntityType.SKULL;
		} else if (block instanceof DaylightDetector) {
			type = BlockEntityType.DAYLIGHT_DETECTOR;
		} else if (block instanceof Hopper) {
			type = BlockEntityType.HOPPER;
		} else if (block instanceof Comparator) {
			type = BlockEntityType.COMPARATOR;
		} else if (block instanceof Banner) {
			type = BlockEntityType.BANNER;
		} else if (block instanceof Structure) {
			type = BlockEntityType.STRUCTURE_BLOCK;
		} else if (block instanceof EndGateway) {
			type = BlockEntityType.END_GATEWAY;
		} else if (block instanceof CommandBlock) {
			type = BlockEntityType.COMMAND_BLOCK;
		} else if (block instanceof ShulkerBox) {
			type = BlockEntityType.SHULKER_BOX;
		} else if (block instanceof Bed) { // FIXME
			type = BlockEntityType.BED;
		} else if (block instanceof Conduit) {
			type = BlockEntityType.CONDUIT;
		} else if (block instanceof Barrel) {
			type = BlockEntityType.BARREL;
		} else if (block instanceof Lectern) {
			type = BlockEntityType.LECTERN;
		} else if (block instanceof Bell) {
			type = BlockEntityType.BELL;
		} else if (block instanceof Jigsaw) {
			type = BlockEntityType.JIGSAW;
		} else if (block instanceof Campfire) {
			type = BlockEntityType.CAMPFIRE;
		} else if (block instanceof Beehive) {
			type = BlockEntityType.BEEHIVE;
		} else if (block instanceof SculkSensor) {
			type = BlockEntityType.BEEHIVE;
		} else {
			return;
		}

		CraftBlockEntityState<?> craftBlockEntityState = (CraftBlockEntityState<?>) block;
		CompoundTag nbt = craftBlockEntityState.getSnapshotNBT();
		BlockPos blockPosition = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		ClientboundBlockEntityDataPacket packet;
		try {
			packet = BLOCK_ENTITY_DATA_PACKET_CONSTRUCTOR.newInstance(blockPosition, type, nbt);
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
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
	public AbstractStructure getChunkData(PacketContainer packet) {
		return packet.getStructures().read(0);
	}

	@Override
	public List<?> getChunkTileEntities(AbstractStructure chunkData) {
		try {
			return (List<?>) BLOCK_ENTITIES_DATA_FIELD.get(chunkData.getHandle());
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void writeChunkTileEntities(AbstractStructure chunkData, List<?> tileEntities) {
		try {
			BLOCK_ENTITIES_DATA_FIELD.set(chunkData.getHandle(), tileEntities);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getChunkTileEntityType(Object tileEntity) {
		try {
			BlockEntityType<?> type = (BlockEntityType<?>) BLOCK_ENTITY_INFO_TYPE_FIELD.get(tileEntity);
			return Objects.requireNonNull(Registry.BLOCK_ENTITY_TYPE.getKey(type)).getPath();
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int getChunkTileEntityXZ(Object tileEntity) {
		try {
			return (int) BLOCK_ENTITY_INFO_XZ_FIELD.get(tileEntity);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int getChunkTileEntityY(Object tileEntity) {
		try {
			return (int) BLOCK_ENTITY_INFO_Y_FIELD.get(tileEntity);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
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
