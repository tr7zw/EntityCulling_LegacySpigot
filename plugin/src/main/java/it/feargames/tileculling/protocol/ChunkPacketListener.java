package it.feargames.tileculling.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.feargames.tileculling.CullingPlugin;
import it.feargames.tileculling.HiddenTileRegistry;
import it.feargames.tileculling.PlayerChunkTracker;
import it.feargames.tileculling.adapter.IAdapter;
import it.feargames.tileculling.util.LocationUtilities;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ChunkPacketListener extends PacketAdapter {

	private final HiddenTileRegistry hiddenTileRegistry;
	private final IAdapter adapter;
	private final PlayerChunkTracker playerChunkTracker;

	public ChunkPacketListener(CullingPlugin plugin, HiddenTileRegistry hiddenTileRegistry, IAdapter adapter, PlayerChunkTracker playerChunkTracker) {
		super(plugin, ListenerPriority.HIGHEST, Arrays.asList(PacketType.Play.Server.MAP_CHUNK, PacketType.Play.Server.UNLOAD_CHUNK), ListenerOptions.ASYNC);
		this.hiddenTileRegistry = hiddenTileRegistry;
		this.plugin = plugin;
		this.adapter = adapter;
		this.playerChunkTracker = playerChunkTracker;
	}

	@Override
	public void onPacketSending(PacketEvent event) {
		Player player = event.getPlayer();
		PacketContainer packet = event.getPacket();

		World world = player.getWorld();
		int sectionsCount = (int) Math.ceil((world.getMaxHeight() - world.getMinHeight()) / 16F);

		int chunkX = packet.getIntegers().read(0);
		int chunkZ = packet.getIntegers().read(1);
		long chunkKey = LocationUtilities.getChunkKey(chunkX, chunkZ);
		if (packet.getType() == PacketType.Play.Server.MAP_CHUNK) {
			transformPacket(packet, sectionsCount);
			playerChunkTracker.trackChunk(player, chunkKey);
		} else if (packet.getType() == PacketType.Play.Server.UNLOAD_CHUNK) {
			playerChunkTracker.untrackChunk(player, chunkKey);
		}
	}

	public void transformPacket(PacketContainer packet, int sectionsCount) {
		//long start = System.nanoTime();
		//int chunkX = packet.getIntegers().read(0);
		//int chunkZ = packet.getIntegers().read(1);

		AbstractStructure chunkData = adapter.getChunkData(packet);

		List<?> tileEntities = adapter.getChunkTileEntities(chunkData);
		if (tileEntities.isEmpty()) {
			return;
		}

		int bitMask = adapter.getChunkPacketBitmask(chunkData);
		byte[] chunkBuffer = chunkData.getByteArrays().read(0);

		IntSet removedBlocks = null;
		for (Iterator<?> iterator = tileEntities.iterator(); iterator.hasNext(); ) {
			Object tileEntity = iterator.next();

			String type = adapter.getChunkTileEntityType(tileEntity);
			if (!hiddenTileRegistry.shouldHide(type)) {
				continue;
			}

			iterator.remove();

			if (removedBlocks == null) {
				removedBlocks = new IntOpenHashSet();
			}

			/*
			System.err.println("===========================================");
			System.err.println("POS " + compound.getInteger("x") + " " + compound.getInteger("y") + " " + compound.getInteger("z"));
			System.err.println("CHUNK " + (compound.getInteger("x") >> 4) + (compound.getInteger("z") >> 4));
			System.err.println("SECTION " + (compound.getInteger("y") >> 4));
			System.err.println("REL " + (compound.getInteger("x") & 0xF) + " " + (compound.getInteger("y") & 0xF) + " " + (compound.getInteger("z") & 0xF));
			System.err.println("===========================================");
			*/

			int rawY = adapter.getChunkTileEntityY(tileEntity);
			byte section = (byte) (rawY >> 4);
			int packedXY = adapter.getChunkTileEntityXZ(tileEntity);
			byte x = (byte) (packedXY & 0xF);
			byte y = (byte) (rawY & 0xF);
			byte z = (byte) ((packedXY >> 4) & 0xF);

			int key = section + (x << 8) + (y << 16) + (z << 24);
			removedBlocks.add(key);

			//plugin.getLogger().warning(chunkX + "," + chunkZ + ": Found " + type + " in section " + section + " at " + x + "," + y + "," + z + " key: " + key);
		}
		if (removedBlocks == null) {
			//plugin.getLogger().warning(chunkX + "," + chunkZ + ": No removed blocks. Took " + (System.nanoTime() - start));
			return;
		}

		adapter.writeChunkTileEntities(chunkData, tileEntities); // TODO: not sure this is required

		ByteBuf reader = adapter.packetDataSerializer(Unpooled.wrappedBuffer(chunkBuffer));
		ByteBuf writer = adapter.packetDataSerializer(Unpooled.buffer(chunkBuffer.length));

		for (int sectionY = 0; sectionY < sectionsCount; sectionY++) {
			if (bitMask != -1 && (bitMask & (1 << sectionY)) == 0) {
				continue;
			}

			short nonAirBlockCount = reader.readShort(); // TODO: Should decrease as we hide blocks? (shouldn't cause issues anyway)
			writer.writeShort(nonAirBlockCount);

			// Block data
			int bitsPerBlock = reader.readByte();
			//System.err.println("BPB: " + bitsPerBlock);
			writer.writeByte(bitsPerBlock);

			int paletteAirIndex;
			if (bitsPerBlock == 0) {
				// Single valued palette
				paletteAirIndex = 0; // Global index
				adapter.writeVarInt(writer, adapter.readVarInt(reader));
			} else if (bitsPerBlock >= 9) {
				// Global palette
				bitsPerBlock = adapter.ceilLog2(adapter.getBlockStateRegistrySize());
				paletteAirIndex = 0; // Global index
			} else {
				if (bitsPerBlock <= 4) {
					// Linear palette
					bitsPerBlock = 4;
				} else {
					// HashMap palette
				}

				paletteAirIndex = -1;

				int paletteLength = adapter.readVarInt(reader);
				int[] palette = new int[paletteLength];
				for (int i = 0; i < paletteLength; i++) {
					int globalId = adapter.readVarInt(reader);
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

				adapter.writeVarInt(writer, paletteLength);
				for (int entry : palette) {
					adapter.writeVarInt(writer, entry);
				}
			}

			int dataArrayLength = adapter.readVarInt(reader);
			adapter.writeVarInt(writer, dataArrayLength);

			long[] dataArray = new long[dataArrayLength];
			for (int i = 0; i < dataArrayLength; i++) {
				dataArray[i] = reader.readLong();
			}

			if (bitsPerBlock != 0) { // Ignore single palette chunks (empty chunks)
				byte blocksPerLong = (byte) (64 / bitsPerBlock);

				for (byte y = 0; y < 16; y++) {
					for (byte z = 0; z < 16; z++) {
						for (byte x = 0; x < 16; x++) {
							int key = sectionY + (x << 8) + (y << 16) + (z << 24);
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

			if (adapter.hasVerticalBiomes()) {
				// Biome data
				copyPalettedContainer(reader, writer);
			}
		}

		// Replace data
		chunkBuffer = writer.array();
		chunkData.getByteArrays().write(0, chunkBuffer);

		//plugin.getLogger().warning(chunkX + "," + chunkZ + ": Processed. Took " + (System.nanoTime() - start));
	}

	private void copyPalettedContainer(ByteBuf source, ByteBuf target) {
		// Bits per block
		int bitsPerBlock = source.readByte();
		//System.err.println("BIOME BPB: " + bitsPerBlock);
		target.writeByte(bitsPerBlock);

		if (bitsPerBlock == 0) {
			// Single valued
			adapter.writeVarInt(target, adapter.readVarInt(source));
		} else if (bitsPerBlock < 9) {
			// With linear/hashmap palette
			int paletteLength = adapter.readVarInt(source);
			for (int i = 0; i < paletteLength; i++) {
				adapter.writeVarInt(target, adapter.readVarInt(source));
			}
		} else {
			// Global palette
		}

		int dataArrayLength = adapter.readVarInt(source);
		adapter.writeVarInt(target, dataArrayLength);
		for (int i = 0; i < dataArrayLength; i++) {
			target.writeLong(source.readLong());
		}
	}
}
