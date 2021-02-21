package it.feargames.tileculling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.LongPredicate;

public class VisibilityCache implements Listener {

	private final Map<Player, LongSet> hiddenBlocks = new HashMap<>();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final WriteLock writeLock = lock.writeLock();
	private final ReadLock readLock = lock.readLock();

	public void setHidden(Player player, long blockKey, boolean hidden) {
		try {
			writeLock.lock();
			LongSet blocks = hiddenBlocks.computeIfAbsent(player, p -> new LongOpenHashSet());
			if (!hidden) {
				blocks.remove(blockKey);
			} else {
				blocks.add(blockKey);
			}
		} finally {
			writeLock.unlock();
		}
	}

	public void setHidden(Player player, Location blockLocation, boolean hidden) {
		setHidden(player, blockLocation.toBlockKey(), hidden);
	}

	public boolean isHidden(Player player, long blockKey) {
		try {
			readLock.lock();
			LongSet blocks = hiddenBlocks.get(player);
			if (blocks == null) {
				return true; // We remove tiles in chunks sent to the client
			}
			return blocks.contains(blockKey);
		} finally {
			readLock.unlock();
		}
	}

	public boolean isHidden(Player player, Location blockLocation) {
		return isHidden(player, blockLocation.toBlockKey());
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
			for (LongSet blocks : hiddenBlocks.values()) {
				blocks.removeIf((LongPredicate) block -> {
					int chunkX = Block.getBlockKeyX(block) >> 4;
					if (event.getChunk().getX() != chunkX) {
						return false;
					}
					int chunkZ = Block.getBlockKeyZ(block) >> 4;
					return event.getChunk().getZ() == chunkZ;
				});
			}
		} finally {
			writeLock.unlock();
		}
	}

}
