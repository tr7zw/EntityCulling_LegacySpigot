package it.feargames.tileculling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PlayerChunkTracker implements Listener {

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
	private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
	private final Map<Player, LongSet> trackedPlayers;

	public PlayerChunkTracker(CullingPlugin plugin) {
		trackedPlayers = new HashMap<>();

		// Track chunks around online players
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			Chunk playerChunk = player.getChunk();
			World world = player.getWorld();
			int viewDistanceSquared = world.getViewDistance() * world.getViewDistance(); // TODO: use notickviewdistance on paper?
			for (Chunk chunk : world.getLoadedChunks()) {
				double distanceSquared = Math.pow(chunk.getX() - playerChunk.getX(), 2) + Math.pow(chunk.getZ() - playerChunk.getZ(), 2);
				distanceSquared = Math.abs(distanceSquared);
				if (distanceSquared > viewDistanceSquared) {
					continue;
				}
				trackChunk(player, chunk.getChunkKey());
			}
		}
	}

	public void trackChunk(Player player, long chunkKey) {
		try {
			writeLock.lock();
			trackedPlayers.computeIfAbsent(player, k -> new LongOpenHashSet()).add(chunkKey);
		} finally {
			writeLock.unlock();
		}
	}

	public void untrackChunk(Player player, long chunkKey) {
		try {
			writeLock.lock();
			LongSet chunkKeys = trackedPlayers.get(player);
			if (chunkKeys == null) {
				return;
			}
			chunkKeys.remove(chunkKey);
		} finally {
			writeLock.unlock();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
		try {
			writeLock.lock();
			trackedPlayers.remove(event.getPlayer());
		} finally {
			writeLock.unlock();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		try {
			writeLock.lock();
			trackedPlayers.remove(event.getPlayer());
		} finally {
			writeLock.unlock();
		}
	}

	public boolean isChunkTracked(Player player, long chunkKey) {
		try {
			readLock.lock();
			LongSet trackedChunks = trackedPlayers.get(player);
			if (trackedChunks == null) {
				return false;
			}
			return trackedChunks.contains(chunkKey);
		} finally {
			readLock.unlock();
		}
	}
}
