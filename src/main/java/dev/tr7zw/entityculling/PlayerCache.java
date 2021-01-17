package dev.tr7zw.entityculling;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class PlayerCache implements Listener {

	private Map<Player, Set<Location>> hiddenBlocks = new HashMap<>();
	private Map<Player, Set<Entity>> hiddenEntities = new HashMap<>();
	private Map<Player, Set<Integer>> hiddenEntitiesID = new HashMap<>();

	public void setHidden(Player player, Location loc, boolean hidden) {
		Set<Location> blocks = hiddenBlocks.computeIfAbsent(player, p -> new HashSet<>());
		if (!hidden) {
			blocks.remove(loc);
		} else {
			blocks.add(loc);
		}
	}
	
	public void setHidden(Player player, Entity entity, boolean hidden) {
		Set<Entity> entities = hiddenEntities.computeIfAbsent(player, p -> new HashSet<>());
		Set<Integer> ids = hiddenEntitiesID.computeIfAbsent(player, p -> new HashSet<>());
		if (!hidden) {
			entities.remove(entity);
			ids.remove(entity.getEntityId());
		} else {
			entities.add(entity);
			ids.add(entity.getEntityId());
		}
	}

	public boolean isHidden(Player player, Location loc) {
		Set<Location> blocks = hiddenBlocks.get(player);
		if (blocks == null)
			return false;
		return blocks.contains(loc);
	}
	
	public boolean isHidden(Player player, Entity entity) {
		Set<Entity> entities = hiddenEntities.get(player);
		if (entities == null)
			return false;
		return entities.contains(entity);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		hiddenBlocks.remove(event.getPlayer());
		hiddenEntities.remove(event.getPlayer());
		hiddenEntitiesID.remove(event.getPlayer());
	}
	
	@EventHandler
	public void onDeath(EntityDeathEvent event) {
		for(Set<Entity> hidden : hiddenEntities.values()) {
			hidden.remove(event.getEntity());
		}
		for(Set<Integer> hidden : hiddenEntitiesID.values()) {
			hidden.remove(event.getEntity().getEntityId());
		}
	}
	
	@EventHandler
	public void onUnload(ChunkUnloadEvent event) {
		for(Set<Entity> hidden : hiddenEntities.values()) {
			hidden.removeAll(Arrays.asList(event.getChunk().getEntities()));
		}
		//leak ids
	}

	public boolean isEntityHidden(Player player, int entityyId) {
		Set<Integer> ids = hiddenEntitiesID.get(player);
		if (ids == null)
			return false;
		return ids.contains(entityyId);
	}

}
