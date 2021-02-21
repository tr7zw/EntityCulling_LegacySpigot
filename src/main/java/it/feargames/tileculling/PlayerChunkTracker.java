package it.feargames.tileculling;

import it.feargames.tileculling.util.LocationUtilities;
import it.feargames.tileculling.util.PaperUtilities;
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
			int playerChunkX = player.getLocation().getBlockX() >> 4;
			int playerChunkZ = player.getLocation().getBlockZ() >> 4;
			World world = player.getWorld();
			int viewDistance = PaperUtilities.getViewDistance(world);
			int viewDistanceSquared = viewDistance * viewDistance;
			for (Chunk chunk : world.getLoadedChunks()) {
				double distanceSquared = Math.pow(chunk.getX() - playerChunkX, 2) + Math.pow(chunk.getZ() - playerChunkZ, 2);
				distanceSquared = Math.abs(distanceSquared);
				if (distanceSquared > viewDistanceSquared) {
					continue;
				}
				trackChunk(player, LocationUtilities.getChunkKey(chunk));
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

	public long[] getTrackedChunks(Player player) {
		try {
			readLock.lock();
			LongSet trackedChunks = trackedPlayers.get(player);
			if (trackedChunks == null) {
				return null;
			}
			return trackedChunks.toArray(new long[0]);
		} finally {
			readLock.unlock();
		}
	}
}
