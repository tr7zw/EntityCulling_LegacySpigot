package it.feargames.tileculling.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.feargames.tileculling.CullingPlugin;
import it.feargames.tileculling.HiddenTileRegistry;
import it.feargames.tileculling.PlayerChunkTracker;
import it.feargames.tileculling.adapter.IAdapter;
import it.feargames.tileculling.util.LocationUtilities;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
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

		int chunkX = packet.getIntegers().read(0);
		int chunkZ = packet.getIntegers().read(1);
		long chunkKey = LocationUtilities.getChunkKey(chunkX, chunkZ);
		if (packet.getType() == PacketType.Play.Server.MAP_CHUNK) {
			transformPacket(packet);
			playerChunkTracker.trackChunk(player, chunkKey);
		} else if (packet.getType() == PacketType.Play.Server.UNLOAD_CHUNK) {
			playerChunkTracker.untrackChunk(player, chunkKey);
		}
	}

	public void transformPacket(PacketContainer packet) {
		//long start = System.nanoTime();
		//int chunkX = packet.getIntegers().read(0);
		//int chunkZ = packet.getIntegers().read(1);

		List<NbtBase<?>> tileEntities = packet.getListNbtModifier().read(0);
		if (tileEntities.isEmpty()) {
			return;
		}


		int bitMask = adapter.getChunkPacketBitmask(packet);
		byte[] chunkData = packet.getByteArrays().read(0);

		IntSet removedBlocks = null;
		for (Iterator<NbtBase<?>> iterator = tileEntities.iterator(); iterator.hasNext(); ) {
			NbtBase<?> tileEntity = iterator.next();
			NbtCompound compound = (NbtCompound) tileEntity;

			String type = compound.getString("id");
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

			int rawY = compound.getInteger("y");
			byte section = (byte) (rawY >> 4);
			byte x = (byte) (compound.getInteger("x") & 0xF);
			byte y = (byte) (rawY & 0xF);
			byte z = (byte) (compound.getInteger("z") & 0xF);

			int key = section + (x << 8) + (y << 16) + (z << 24);
			removedBlocks.add(key);

			//plugin.getLogger().warning(chunkX + "," + chunkZ + ": Found " + type + " in section " + section + " at " + x + "," + y + "," + z + " key: " + key);
		}
		if (removedBlocks == null) {
			//plugin.getLogger().warning(chunkX + "," + chunkZ + ": No removed blocks. Took " + (System.nanoTime() - start));
			return;
		}

		packet.getListNbtModifier().write(0, tileEntities);

		ByteBuf reader = adapter.packetDataSerializer(Unpooled.wrappedBuffer(chunkData));
		ByteBuf writer = adapter.packetDataSerializer(Unpooled.buffer(chunkData.length));

		for (int sectionY = 0; sectionY < 16; sectionY++) {
			if ((bitMask & (1 << sectionY)) == 0) {
				continue;
			}

			short nonAirBlockCount = reader.readShort();
			writer.writeShort(nonAirBlockCount);

			byte bitsPerBlock = reader.readByte();
			if (bitsPerBlock < 4 || bitsPerBlock > 64) {
				throw new RuntimeException("Invalid bits per block! (" + bitsPerBlock + ")");
			}
			writer.writeByte(bitsPerBlock);

			int paletteAirIndex;
			if (bitsPerBlock > 8) {
				// Direct
				paletteAirIndex = 0; // Global index
			} else {
				// Indirect
				paletteAirIndex = -1;

				int paletteLength = adapter.readVarInt(reader);
				adapter.writeVarInt(writer, paletteLength);

				for (int i = 0; i < paletteLength; i++) {
					int globalId = adapter.readVarInt(reader);
					adapter.writeVarInt(writer, globalId);

					if (globalId == 0) {
						paletteAirIndex = i;
					}
				}

				if (paletteAirIndex == -1) {
					throw new RuntimeException("No air found in palette!");
				}
			}

			int dataArrayLength = adapter.readVarInt(reader);
			adapter.writeVarInt(writer, dataArrayLength);

			long[] dataArray = new long[dataArrayLength];
			for (int i = 0; i < dataArrayLength; i++) {
				dataArray[i] = reader.readLong();
			}

			//int individualValueMask = ((1 << bitsPerBlock) - 1);
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

			for (int i = 0; i < dataArrayLength; i++) {
				writer.writeLong(dataArray[i]);
			}
		}

		// Replace data
		chunkData = writer.array();
		packet.getByteArrays().write(0, chunkData);

		//plugin.getLogger().warning(chunkX + "," + chunkZ + ": Processed. Took " + (System.nanoTime() - start));
	}
}
