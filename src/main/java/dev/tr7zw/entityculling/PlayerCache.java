package dev.tr7zw.entityculling;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class PlayerCache implements Listener {

	private final Map<Player, Set<Location>> hiddenBlocks = new HashMap<>();
	private final Map<Player, Set<Entity>> hiddenEntities = new HashMap<>();
	private final Map<Player, Set<Integer>> hiddenEntitiesID = new HashMap<>();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final WriteLock writeLock = lock.writeLock();
	private final ReadLock readLock = lock.readLock();

	public void setHidden(Player player, Location loc, boolean hidden) {
		try {
			writeLock.lock();
			Set<Location> blocks = hiddenBlocks.computeIfAbsent(player, p -> new HashSet<>());
			if (!hidden) {
				blocks.remove(loc);
			} else {
				blocks.add(loc);
			}
		} finally {
			writeLock.unlock();
		}
	}

	public void setHidden(Player player, Entity entity, boolean hidden) {
		try {
			writeLock.lock();
			Set<Entity> entities = hiddenEntities.computeIfAbsent(player, p -> new HashSet<>());
			Set<Integer> ids = hiddenEntitiesID.computeIfAbsent(player, p -> new HashSet<>());
			if (!hidden) {
				entities.remove(entity);
				ids.remove(entity.getEntityId());
			} else {
				entities.add(entity);
				ids.add(entity.getEntityId());
			}
		} finally {
			writeLock.unlock();
		}
	}

	public boolean isHidden(Player player, Location loc) {
		try {
			readLock.lock();
			Set<Location> blocks = hiddenBlocks.get(player);
			if (blocks == null)
				return false;
			return blocks.contains(loc);
		} finally {
			readLock.unlock();
		}
	}

	public boolean isHidden(Player player, Entity entity) {
		try {
			readLock.lock();
			Set<Entity> entities = hiddenEntities.get(player);
			if (entities == null)
				return false;
			return entities.contains(entity);
		} finally {
			readLock.unlock();
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		try {
			writeLock.lock();
			hiddenBlocks.remove(event.getPlayer());
			hiddenEntities.remove(event.getPlayer());
			hiddenEntitiesID.remove(event.getPlayer());
		} finally {
			writeLock.unlock();
		}
	}

	@EventHandler
	public void onDeath(EntityDeathEvent event) {
		try {
			writeLock.lock();
			for (Set<Entity> hidden : hiddenEntities.values()) {
				hidden.remove(event.getEntity());
			}
			for (Set<Integer> hidden : hiddenEntitiesID.values()) {
				hidden.remove(event.getEntity().getEntityId());
			}
		} finally {
			writeLock.unlock();
		}
	}

	@EventHandler
	public void onUnload(ChunkUnloadEvent event) {
		try {
			writeLock.lock();
			for (Set<Entity> hidden : hiddenEntities.values()) {
				hidden.removeAll(Arrays.asList(event.getChunk().getEntities()));
			}
		} finally {
			writeLock.unlock();
		}
		// leak ids
	}

	public boolean isEntityHidden(Player player, int entityyId) {
		try {
			readLock.lock();
			Set<Integer> ids = hiddenEntitiesID.get(player);
			if (ids == null)
				return false;
			return ids.contains(entityyId);
		} finally {
			readLock.unlock();
		}
	}

}
