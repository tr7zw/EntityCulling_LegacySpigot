package dev.tr7zw.entityculling;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import dev.tr7zw.entityculling.occlusionculling.BlockChangeListener;
import dev.tr7zw.entityculling.occlusionculling.OcclusionCullingUtils;
import net.minecraft.server.v1_16_R3.Entity;

public class CullingPlugin extends JavaPlugin {

	private static AxisAlignedBB entityAABB = new AxisAlignedBB(0d, 0d, 0d, 1d, 2d,1d);

	public static CullingPlugin instance;

	public BlockChangeListener blockChangeListener;
	public PlayerCache cache;

	@Override
	public void onEnable() {
		instance = this;
		blockChangeListener = new BlockChangeListener();
		cache = new PlayerCache();
		Bukkit.getPluginManager().registerEvents(blockChangeListener, this);
		Bukkit.getPluginManager().registerEvents(cache, this);
		Bukkit.getScheduler().runTaskTimerAsynchronously(instance, new CullTask(this), 3, 3);
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL,
				PacketType.Play.Server.SPAWN_ENTITY_LIVING, PacketType.Play.Server.SPAWN_ENTITY) {
			@Override
			public void onPacketSending(PacketEvent event) {
				int entityyId = event.getPacket().getIntegers().read(0);
				Entity entity = ((CraftWorld) event.getPlayer().getWorld()).getHandle().getEntity(entityyId);
				if (entity != null && entity.getBukkitEntity().getType() != EntityType.PLAYER) {
					boolean canSee = OcclusionCullingUtils.isAABBVisible(entity.getBukkitEntity().getLocation(),
							entityAABB, event.getPlayer().getEyeLocation(), true);
					if (!canSee) {
						event.setCancelled(true);
						cache.setHidden(event.getPlayer(), entity.getBukkitEntity(), true);
					}
				}
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener(
				new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA,
						PacketType.Play.Server.ENTITY_HEAD_ROTATION, PacketType.Play.Server.ENTITY_VELOCITY,
						PacketType.Play.Server.ENTITY_LOOK, PacketType.Play.Server.ENTITY_MOVE_LOOK,
						PacketType.Play.Server.REL_ENTITY_MOVE, PacketType.Play.Server.REL_ENTITY_MOVE_LOOK) {
					@Override
					public void onPacketSending(PacketEvent event) {
						int entityyId = event.getPacket().getIntegers().read(0);
						if (cache.isEntityHidden(event.getPlayer(), entityyId)) {
							event.setCancelled(true);
						}
					}
				});
	}

	public static void runTask(Runnable task) {
		if (CullingPlugin.instance.isEnabled()) {
			Bukkit.getScheduler().runTask(CullingPlugin.instance, task);
		}
	}

	public static void runTaskLater(Runnable task, long delay) {
		if (CullingPlugin.instance.isEnabled()) {
			Bukkit.getScheduler().runTaskLater(CullingPlugin.instance, task, delay);
		}
	}

	public static void runTaskAsynchronously(Runnable task) {
		if (CullingPlugin.instance.isEnabled()) {
			Bukkit.getScheduler().runTaskAsynchronously(CullingPlugin.instance, task);
		}
	}

}
