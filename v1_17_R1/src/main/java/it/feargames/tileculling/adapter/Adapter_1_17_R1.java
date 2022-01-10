package it.feargames.tileculling.adapter;

import com.comphenix.protocol.events.PacketContainer;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.v1_17_R1.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

@SuppressWarnings({"JavaReflectionMemberAccess", "unused"})
public class Adapter_1_17_R1 implements IAdapter {

    private static final Field CHUNK_SECTION_BUFFER_FIELD;

    static {
        try {
            CHUNK_SECTION_BUFFER_FIELD = ClientboundLevelChunkPacket.class.getDeclaredField("g"); // buffer
            CHUNK_SECTION_BUFFER_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] getChunkSectionBuffer(ClientboundLevelChunkPacket packet) {
        try {
            return (byte[]) CHUNK_SECTION_BUFFER_FIELD.get(packet);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeChunkSectionBuffer(ClientboundLevelChunkPacket packet, byte[] buffer) {
        try {
            CHUNK_SECTION_BUFFER_FIELD.set(packet, buffer);
        } catch (IllegalAccessException e) {
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
        net.minecraft.world.level.block.state.BlockState handleData = blockData == null ? Blocks.AIR.defaultBlockState() : ((CraftBlockData) blockData).getState();
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
        } else if (block instanceof Bed) { // FIXME
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
    public void transformPacket(Player player, PacketContainer container, Function<String, Boolean> tileEntityTypeFilter) {
        CraftWorld craftWorld = (CraftWorld) player.getWorld();
        ServerLevel vanillaWorld = craftWorld.getHandle();

        ClientboundLevelChunkPacket packet = (ClientboundLevelChunkPacket) container.getHandle();

        List<CompoundTag> blockTileEntities = packet.getBlockEntitiesTags();

        IntList removedBlocks = null;
        for (Iterator<CompoundTag> iterator = blockTileEntities.iterator(); iterator.hasNext(); ) {
            CompoundTag tileEntity = iterator.next();

            String type = tileEntity.getString("id");
            if (!tileEntityTypeFilter.apply(type)) {
                continue;
            }

            iterator.remove();

            if (removedBlocks == null) {
                removedBlocks = new IntArrayList();
            }

            short y = (short) tileEntity.getInt("y");

            int packedXZ = (tileEntity.getInt("x") & 0xF) << 4 | (tileEntity.getInt("z") & 0xF);
            byte x = (byte) (packedXZ & 0xF);
            byte z = (byte) ((packedXZ >> 4) & 0xF);

            // Y, X, Z
            int key = (y & 0xFFFF) | ((x & 0xF) << 16) | ((z & 0xF) << 20);
            removedBlocks.add(key);
        }

        if (removedBlocks == null) {
            return;
        }

        byte[] readerBuffer = getChunkSectionBuffer(packet);
        FriendlyByteBuf reader = new FriendlyByteBuf(Unpooled.wrappedBuffer(readerBuffer));

        int bufferSize = 0;
        LevelChunkSection[] sections = new LevelChunkSection[vanillaWorld.getSectionsCount()];
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            int yOffset = vanillaWorld.getSectionYFromSectionIndex(sectionIndex);
            LevelChunkSection section = new LevelChunkSection(yOffset);
            section.read(reader);

            for (byte y = 0; y < 16; y++) {
                for (byte z = 0; z < 16; z++) {
                    for (byte x = 0; x < 16; x++) {
                        int key = ((yOffset + y) & 0xFFFF) | ((x & 0xF) << 16) | ((z & 0xF) << 20);
                        if (!removedBlocks.contains(key)) {
                            continue;
                        }
                        section.setBlockState(x, y, z, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }

            sections[sectionIndex] = section;
            bufferSize += section.getSerializedSize();
        }

        byte[] writerBuffer = new byte[bufferSize];
        FriendlyByteBuf writer = new FriendlyByteBuf(Unpooled.wrappedBuffer(writerBuffer));
        writer.writerIndex(0);

        for (LevelChunkSection section : sections) {
            section.write(writer);
        }

        writeChunkSectionBuffer(packet, writerBuffer);
    }

}
