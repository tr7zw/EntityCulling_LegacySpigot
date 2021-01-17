package dev.tr7zw.entityculling;

import java.lang.reflect.InvocationTargetException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;

import dev.tr7zw.entityculling.occlusionculling.OcclusionCullingUtils;
import net.minecraft.server.v1_16_R3.EntityLiving;
import net.minecraft.server.v1_16_R3.EntityPlayer;
import net.minecraft.server.v1_16_R3.Packet;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_16_R3.PacketPlayOutSpawnEntity;
import net.minecraft.server.v1_16_R3.PacketPlayOutSpawnEntityLiving;
import net.minecraft.server.v1_16_R3.PlayerChunkMap.EntityTracker;
import net.minecraft.server.v1_16_R3.WorldServer;

public class CullTask implements Runnable {

	private CullingPlugin instance;
	private AxisAlignedBB blockAABB = new AxisAlignedBB(0d, 0d, 0d, 1d, 1d, 1d);
	private AxisAlignedBB entityAABB = new AxisAlignedBB(0d, 0d, 0d, 1d, 2d, 1d);

	public CullTask(CullingPlugin pl) {
		instance = pl;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void run() {
		long start = System.currentTimeMillis();
		for (Player player : Bukkit.getOnlinePlayers()) {
			for (int x = -3; x <= 3; x++) {
				for (int y = -3; y <= 3; y++) {
					Location loc = player.getLocation().add(x * 16, 0, y * 16);
					if (instance.blockChangeListener.isInLoadedChunk(loc)) {
						// ChunkSnapshot chunkSnapshot = instance.blockChangeListener.getChunk(loc);
						BlockState[] tiles = instance.blockChangeListener.getChunkTiles(loc);
						for (BlockState block : tiles) {
							//if (block.getType() == Material.CHEST) {
								boolean canSee = OcclusionCullingUtils.isAABBVisible(block.getLocation(), blockAABB,
										player.getEyeLocation(), false);
								boolean hidden = instance.cache.isHidden(player, block.getLocation());
								if (hidden && canSee) {
									instance.cache.setHidden(player, block.getLocation(), false);
									player.sendBlockChange(block.getLocation(), block.getBlockData());
								} else if (!hidden && !canSee) {
									instance.cache.setHidden(player, block.getLocation(), true);
									player.sendBlockChange(block.getLocation(), Material.BARRIER, (byte) 0);
								}
							//}
						}
						Entity[] entities = instance.blockChangeListener.getChunkEntities(loc);
						Int2ObjectMap<EntityTracker> trackers = ((WorldServer) ((CraftEntity) player).getHandle().world).getChunkProvider().playerChunkMap.trackedEntities;
						EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
						for (Entity entity : entities) {
							EntityTracker tracker = trackers.get(entity.getEntityId());
							if(tracker == null){
								continue;
							}
							if(!tracker.trackedPlayers.contains(nmsPlayer)){
								continue;
							}
							boolean canSee = OcclusionCullingUtils.isAABBVisible(entity.getLocation(), entityAABB,
									player.getEyeLocation(), true);
							boolean hidden = instance.cache.isHidden(player, entity);
							if (hidden && canSee) {
								instance.cache.setHidden(player, entity, false);
								if(entity instanceof Player) {
									// Do nothing!
								}else if(entity instanceof LivingEntity) {
									PacketPlayOutSpawnEntityLiving packet = new PacketPlayOutSpawnEntityLiving(
											(EntityLiving) ((CraftEntity) entity).getHandle());
									sendPacket(player, PacketType.Play.Server.SPAWN_ENTITY_LIVING, packet);
								}else {
									PacketPlayOutSpawnEntity packet = new PacketPlayOutSpawnEntity(
											((CraftEntity) entity).getHandle());
									sendPacket(player, PacketType.Play.Server.SPAWN_ENTITY, packet);
								}
								
								PacketPlayOutEntityMetadata metaPacket = new PacketPlayOutEntityMetadata(
										entity.getEntityId(), ((CraftEntity) entity).getHandle().getDataWatcher(),
										true);
								sendPacket(player, PacketType.Play.Server.ENTITY_METADATA, metaPacket);
							} else if (!hidden && !canSee) { // hide entity
								if(!(entity instanceof Player) && !(entity instanceof ExperienceOrb) && !(entity instanceof Painting)) {
									instance.cache.setHidden(player, entity, true);
									PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(
											entity.getEntityId());
									sendPacket(player, PacketType.Play.Server.ENTITY_DESTROY, packet);
								}
							}
						}
					}
				}
			}
		}
		//System.out.println("Time: " + (System.currentTimeMillis() - start));
	}
	
	private void sendPacket(Player player, PacketType type, Packet<?> packet) {
		try {
			ProtocolLibrary.getProtocolManager().sendServerPacket(player, new PacketContainer(type, packet));
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

}
