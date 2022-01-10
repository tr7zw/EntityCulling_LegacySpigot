package it.feargames.tileculling.adapter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.server.v1_16_R3.Block;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.v1_16_R3.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

@SuppressWarnings("unused")
public class Adapter_1_16_R3 implements IAdapter {

    private static final Field BLOCK_ENTITIES_DATA_FIELD;
    private static final Field CHUNK_SECTION_BUFFER_FIELD;

    static {
        try {
            BLOCK_ENTITIES_DATA_FIELD = PacketPlayOutMapChunk.class.getDeclaredField("d"); // blockEntitiesData
            BLOCK_ENTITIES_DATA_FIELD.setAccessible(true);

            CHUNK_SECTION_BUFFER_FIELD = PacketPlayOutMapChunk.class.getDeclaredField("f"); // buffer
            CHUNK_SECTION_BUFFER_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int readVarInt(PacketDataSerializer serializer) {
        return serializer.i();
    }

    private static void writeVarInt(PacketDataSerializer serializer, int value) {
        serializer.d(value);
    }

    @SuppressWarnings("unchecked")
    private static List<NBTTagCompound> getChunkTileEntities(PacketPlayOutMapChunk packet) {
        try {
            return (List<NBTTagCompound>) BLOCK_ENTITIES_DATA_FIELD.get(packet);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] getChunkSectionBuffer(PacketPlayOutMapChunk packet) {
        try {
            return (byte[]) CHUNK_SECTION_BUFFER_FIELD.get(packet);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeChunkSectionBuffer(PacketPlayOutMapChunk packet, byte[] buffer) {
        try {
            CHUNK_SECTION_BUFFER_FIELD.set(packet, buffer);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateBlockState(Player player, Location location, BlockData blockData) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        EntityPlayer handlePlayer = craftPlayer.getHandle();
        PlayerConnection connection = handlePlayer.playerConnection;
        if (connection == null) {
            return;
        }

        BlockPosition blockPosition = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        IBlockData handleData = blockData == null ? Blocks.AIR.getBlockData() : ((CraftBlockData) blockData).getState();
        PacketPlayOutBlockChange blockChange = new PacketPlayOutBlockChange(blockPosition, handleData);
        connection.sendPacket(blockChange);
    }

    @Override
    public void updateBlockData(Player player, Location location, BlockState block) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        EntityPlayer handlePlayer = craftPlayer.getHandle();
        PlayerConnection connection = handlePlayer.playerConnection;
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
        NBTTagCompound nbt = craftBlockEntityState.getSnapshotNBT();
        BlockPosition blockPosition = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        PacketPlayOutTileEntityData packet = new PacketPlayOutTileEntityData(blockPosition, action, nbt);
        connection.sendPacket(packet);
    }

    @Override
    public void transformPacket(Player player, PacketContainer container, Function<String, Boolean> tileEntityTypeFilter) {
        List<NbtBase<?>> tileEntities = container.getListNbtModifier().read(0);
        if (tileEntities.isEmpty()) {
            return;
        }

        int bitMask = container.getIntegers().read(2);
        byte[] chunkBuffer = container.getByteArrays().read(0);

        IntSet removedBlocks = null;
        for (Iterator<NbtBase<?>> iterator = tileEntities.iterator(); iterator.hasNext(); ) {
            NbtCompound tileEntity = (NbtCompound) iterator.next();

            String type = tileEntity.getString("id");
            if (!tileEntityTypeFilter.apply(type)) {
                continue;
            }

            iterator.remove();

            if (removedBlocks == null) {
                removedBlocks = new IntOpenHashSet();
            }

            int rawY = tileEntity.getInteger("y");
            byte section = (byte) (rawY >> 4);
            byte x = (byte) (tileEntity.getInteger("x") & 0xF);
            byte y = (byte) (rawY & 0xF);
            byte z = (byte) (tileEntity.getInteger("z") & 0xF);

            int key = section + (x << 8) + (y << 16) + (z << 24);
            removedBlocks.add(key);
        }

        if (removedBlocks == null) {
            return;
        }

        container.getListNbtModifier().write(0, tileEntities); // TODO: not sure this is required

        PacketDataSerializer reader = new PacketDataSerializer(Unpooled.wrappedBuffer(chunkBuffer));
        PacketDataSerializer writer = new PacketDataSerializer(Unpooled.buffer(chunkBuffer.length));

        for (int sectionIndex = 0; sectionIndex < 16; sectionIndex++) {
            if (bitMask != -1 && (bitMask & (1 << sectionIndex)) == 0) {
                continue;
            }

            short nonAirBlockCount = reader.readShort(); // TODO: Should decrease as we hide blocks? (shouldn't cause issues anyway)
            writer.writeShort(nonAirBlockCount);

            // Block data
            int bitsPerBlock = reader.readByte();
            writer.writeByte(bitsPerBlock);

            int paletteAirIndex;
            if (bitsPerBlock == 0) {
                // Single valued palette
                paletteAirIndex = 0; // Unused in this case, but we have to initialize it
                writeVarInt(writer, readVarInt(reader));
            } else if (bitsPerBlock < 9) {
                if (bitsPerBlock <= 4) {
                    // Array/linear palette
                    bitsPerBlock = 4;
                } else {
                    // BiMap palette
                }

                paletteAirIndex = -1;

                int paletteLength = readVarInt(reader);
                int[] palette = new int[paletteLength];
                for (int i = 0; i < paletteLength; i++) {
                    int globalId = readVarInt(reader);
                    palette[i] = globalId;

                    if (globalId == 0) {
                        paletteAirIndex = i;
                    }
                }

                if (paletteAirIndex == -1) {
                    paletteLength++;
                    int[] newPalette = new int[paletteLength];
                    System.arraycopy(palette, 0, newPalette, 0, palette.length);
                    newPalette[newPalette.length - 1] = 0;
                    palette = newPalette;
                }

                writeVarInt(writer, paletteLength);
                for (int entry : palette) {
                    writeVarInt(writer, entry);
                }
            } else {
                // ID List/Global palette
                bitsPerBlock = MathHelper.e(Block.REGISTRY_ID.a());
                paletteAirIndex = 0; // Global index
            }

            int dataArrayLength = readVarInt(reader);
            if (dataArrayLength != 0 && bitsPerBlock == 0) {
                throw new RuntimeException("Data array length != 0 when bits per block is 0!");
            }

            writeVarInt(writer, dataArrayLength);

            long[] dataArray = new long[dataArrayLength];
            for (int i = 0; i < dataArrayLength; i++) {
                dataArray[i] = reader.readLong();
            }

            if (bitsPerBlock != 0) { // Ignore single palette chunks (empty chunks)
                byte blocksPerLong = (byte) (64 / bitsPerBlock);

                for (byte y = 0; y < 16; y++) {
                    for (byte z = 0; z < 16; z++) {
                        for (byte x = 0; x < 16; x++) {
                            int key = sectionIndex + (x << 8) + (y << 16) + (z << 24);
                            if (!removedBlocks.contains(key)) {
                                continue;
                            }

                            short blockNumber = (short) ((((y * 16) + z) * 16) + x);
                            short longIndex = (short) (blockNumber / blocksPerLong);

                            long previous = dataArray[longIndex];
                            byte startOffset = (byte) ((blockNumber % blocksPerLong) * bitsPerBlock);

                            long data = previous;
                            for (int i = 0; i < bitsPerBlock; i++) {
                                if ((paletteAirIndex & (1 << i)) == 1) {
                                    data |= (1L << i + startOffset);
                                } else {
                                    data &= ~(1L << i + startOffset);
                                }
                            }
                            dataArray[longIndex] = data;
                        }
                    }
                }
            }

            for (int i = 0; i < dataArrayLength; i++) {
                writer.writeLong(dataArray[i]);
            }
        }

        // Replace data
        chunkBuffer = writer.array();
        container.getByteArrays().write(0, chunkBuffer);
    }

}
