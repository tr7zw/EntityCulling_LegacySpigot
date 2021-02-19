package it.feargames.tileculling.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.feargames.tileculling.ChunkSeeder;
import it.feargames.tileculling.CullingPlugin;
import it.feargames.tileculling.PlayerChunkTracker;
import it.feargames.tileculling.adapter.IAdapter;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MapChunkPacketListener extends PacketAdapter {

	private final IAdapter adapter;
	private final PlayerChunkTracker playerChunkTracker;
	private final ChunkSeeder chunkSeeder;

	public MapChunkPacketListener(CullingPlugin plugin, IAdapter adapter, PlayerChunkTracker playerChunkTracker, ChunkSeeder chunkSeeder) {
		super(plugin, ListenerPriority.HIGHEST, Collections.singletonList(PacketType.Play.Server.MAP_CHUNK), ListenerOptions.ASYNC);
		this.plugin = plugin;
		this.adapter = adapter;
		this.playerChunkTracker = playerChunkTracker;
		this.chunkSeeder = chunkSeeder;
	}

	@Override
	public void onPacketSending(PacketEvent event) {
		Player player = event.getPlayer();
		PacketContainer packet = event.getPacket();

		int chunkX = packet.getIntegers().read(0);
		int chunkZ = packet.getIntegers().read(1);
		long chunkKey = Chunk.getChunkKey(chunkX, chunkZ);

		if (!transformPacket(packet)) {
			// No tiles in the chunk, no need to seed it
			playerChunkTracker.trackChunk(player, chunkKey);
			return;
		}

		chunkSeeder.seedChunk(player, chunkKey);
	}

	public boolean transformPacket(PacketContainer packet) {
		//long start = System.nanoTime();
		//int chunkX = packet.getIntegers().read(0);
		//int chunkZ = packet.getIntegers().read(1);

		List<NbtBase<?>> tileEntities = packet.getListNbtModifier().read(0);
		if (tileEntities.isEmpty()) {
			return false;
		}

		int bitMask = packet.getIntegers().read(2);
		byte[] chunkData = packet.getByteArrays().read(0);

		IntSet removedBlocks = null;
		for (Iterator<NbtBase<?>> iterator = tileEntities.iterator(); iterator.hasNext(); ) {
			NbtBase<?> tileEntity = iterator.next();
			NbtCompound compound = (NbtCompound) tileEntity;

			String type = compound.getString("id");
			if (!CullingPlugin.shouldHide(type)) {
				continue;
			}

			iterator.remove();

			if (removedBlocks == null) {
				removedBlocks = new IntOpenHashSet();
			}

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
			return false;
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
		return true;
	}
}
