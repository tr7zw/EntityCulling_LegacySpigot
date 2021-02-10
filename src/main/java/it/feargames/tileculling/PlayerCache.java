package it.feargames.tileculling;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

public class PlayerCache implements Listener {

	private final Map<Player, Set<Location>> hiddenBlocks = new HashMap<>();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final WriteLock writeLock = lock.writeLock();
	private final ReadLock readLock = lock.readLock();

	public void setHidden(Player player, Location loc, boolean hidden) {
		try {
			writeLock.lock();
			Set<Location> blocks = hiddenBlocks.computeIfAbsent(player, p -> new LinkedHashSet<>());
			if (!hidden) {
				blocks.remove(loc);
			} else {
				blocks.add(loc);
			}
		} finally {
			writeLock.unlock();
		}
	}

	public boolean isHidden(Player player, Location loc) {
		try {
			readLock.lock();
			Set<Location> blocks = hiddenBlocks.get(player);
			if (blocks == null) {
				return false;
			}
			return blocks.contains(loc);
		} finally {
			readLock.unlock();
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		try {
			writeLock.lock();
			hiddenBlocks.remove(event.getPlayer());
		} finally {
			writeLock.unlock();
		}
	}

	@EventHandler
	public void onUnload(ChunkUnloadEvent event) {
		try {
			writeLock.lock();
			for (Set<Location> locations : hiddenBlocks.values()) {
				locations.removeIf(location -> {
					int chunkX = location.getBlockX() >> 4;
					if (event.getChunk().getX() != chunkX) {
						return false;
					}
					int chunkZ = location.getBlockZ() >> 4;
					return event.getChunk().getZ() == chunkZ;
				});
			}
		} finally {
			writeLock.unlock();
		}
	}

}
