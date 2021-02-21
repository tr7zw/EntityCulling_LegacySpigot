package it.feargames.tileculling;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ChunkCache implements Listener {

	private final CullingPlugin plugin;

	private final Map<World, Long2ObjectMap<ChunkEntry>> cachedChunks = new HashMap<>();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
	private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();

	public ChunkCache(CullingPlugin plugin) {
		this.plugin = plugin;
		for (World world : plugin.getServer().getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				updateCachedChunkSync(chunk.getWorld(), chunk.getChunkKey(), chunk);
			}
		}
	}

	static class ChunkEntry {
		ChunkSnapshot snapshot;
		List<BlockState> tiles;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWorldUnload(WorldUnloadEvent event) {
		cachedChunks.remove(event.getWorld());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent e) {
		Chunk chunk = e.getBlock().getChunk();
		// We have to delay as tile entities are updated after the block has changed
		new BukkitRunnable() {
			@Override
			public void run() {
				updateCachedChunkSync(chunk.getWorld(), chunk.getChunkKey(), chunk);
			}
		}.runTask(plugin);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent e) {
		Chunk chunk = e.getBlock().getChunk();
		// We have to delay as tile entities are updated after the block has changed
		new BukkitRunnable() {
			@Override
			public void run() {
				updateCachedChunkSync(chunk.getWorld(), chunk.getChunkKey(), chunk);
			}
		}.runTask(plugin);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent e) {
		Chunk chunk = e.getChunk();
		updateCachedChunkSync(chunk.getWorld(), chunk.getChunkKey(), chunk);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkUnload(ChunkUnloadEvent e) {
		Chunk chunk = e.getChunk();
		updateCachedChunkSync(chunk.getWorld(), chunk.getChunkKey(), null);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent e) {
		handleExplosionSync(e.blockList());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent e) {
		handleExplosionSync(e.blockList());
	}

	private void handleExplosionSync(List<Block> blockList) {
		Set<Chunk> chunks = new HashSet<>();
		for (Block block : blockList) {
			chunks.add(block.getChunk());
		}
		for (Chunk chunk : chunks) {
			updateCachedChunkSync(chunk.getWorld(), chunk.getChunkKey(), chunk);
		}
	}

	private void updateCachedChunkSync(World world, long chunkKey, final Chunk chunk) {
		if (chunk == null) {
			try {
				writeLock.lock();
				Long2ObjectMap<ChunkEntry> entries = cachedChunks.get(world);
				if (entries != null) {
					entries.remove(chunkKey);
					if (entries.isEmpty()) {
						cachedChunks.remove(world);
					}
				}
			} finally {
				writeLock.unlock();
			}
			return;
		}

		try {
			writeLock.lock();
			ChunkEntry entry = cachedChunks.computeIfAbsent(world, k -> new Long2ObjectOpenHashMap<>()).computeIfAbsent(chunkKey, k -> new ChunkEntry());
			entry.snapshot = chunk.getChunkSnapshot(false, false, false);
			entry.tiles = filterTiles(chunk.getTileEntities());
		} finally {
			writeLock.unlock();
		}
	}

	private List<BlockState> filterTiles(BlockState[] tiles) {
		if (tiles.length == 0) {
			return Collections.emptyList();
		}

		List<BlockState> result = new LinkedList<>();
		for (BlockState state : tiles) {
			if (CullingPlugin.shouldHide(state)) {
				result.add(state);
			}
		}
		return result;
	}

	public boolean isInLoadedChunk(World world, long chunkKey) {
		try {
			readLock.lock();
			Long2ObjectMap<ChunkEntry> entries = cachedChunks.get(world);
			return entries != null && entries.containsKey(chunkKey);
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			readLock.unlock();
		}
		return false;
	}

	public ChunkSnapshot getChunk(World world, long chunkKey) {
		try {
			readLock.lock();
			Long2ObjectMap<ChunkEntry> entries = cachedChunks.get(world);
			if (entries == null) {
				return null;
			}
			ChunkEntry entry = entries.get(chunkKey);
			if (entry == null) {
				return null;
			}
			return entry.snapshot;
		} finally {
			readLock.unlock();
		}
	}

	public List<BlockState> getChunkTiles(World world, long chunkKey) {
		try {
			readLock.lock();
			Long2ObjectMap<ChunkEntry> entries = cachedChunks.get(world);
			if (entries == null) {
				return null;
			}
			ChunkEntry entry = entries.get(chunkKey);
			if (entry == null) {
				return null;
			}
			return entry.tiles;
		} finally {
			readLock.unlock();
		}
	}

}
