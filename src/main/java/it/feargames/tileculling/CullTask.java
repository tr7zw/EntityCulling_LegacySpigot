package it.feargames.tileculling;

import it.feargames.tileculling.occlusionculling.AxisAlignedBB;
import it.feargames.tileculling.occlusionculling.OcclusionCulling;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class CullTask implements Runnable {

	public static final AxisAlignedBB BLOCK_AABB = new AxisAlignedBB(0d, 0d, 0d, 1d, 1d, 1d);

	private final CullingPlugin plugin;
	private final OcclusionCulling culling = new OcclusionCulling();

	public CullTask(CullingPlugin plugin) {
		this.plugin = plugin;
	}

	/*
	public void blockParticles(Player player, Particle particle, Location location) {
		int x = location.getBlockX();
		int y = location.getBlockY();
		int z = location.getBlockZ();
		player.spawnParticle(particle, x, y, z, 1);
		player.spawnParticle(particle, x + 1, y, z, 1);
		player.spawnParticle(particle, x, y, z + 1, 1);
		player.spawnParticle(particle, x + 1, y, z + 1, 1);
		player.spawnParticle(particle, x, y + 1, z, 1);
		player.spawnParticle(particle, x + 1, y + 1, z, 1);
		player.spawnParticle(particle, x, y + 1, z + 1, 1);
		player.spawnParticle(particle, x + 1, y + 1, z + 1, 1);
	}
	*/

	public static final int CHUNK_RANGE = 6; // FIXME: makes no sense to have a chunk range, not this way at least

	public static final int PARTICLE_INTERVAL = 40;
	public static int particleTick = 0;

	@Override
	public void run() {
		//long start = System.currentTimeMillis();
		particleTick++;
		for (Player player : Bukkit.getOnlinePlayers()) {
			World world = player.getWorld();
			Location playerLocation = player.getLocation();

			Vector playerEyeLocation = player.getEyeLocation().toVector();

			int playerChunkX = playerLocation.getBlockX() >> 4;
			int playerChunkZ = playerLocation.getBlockZ() >> 4;

			culling.resetCache();

			for (int x = -CHUNK_RANGE; x <= CHUNK_RANGE; x++) {
				for (int z = -CHUNK_RANGE; z <= CHUNK_RANGE; z++) {
					int chunkX = playerChunkX + x;
					int chunkZ = playerChunkZ + z;
					long chunkKey = Chunk.getChunkKey(chunkX, chunkZ);
					List<BlockState> tiles = plugin.blockChangeListener.getChunkTiles(world, chunkKey);
					if (tiles == null) {
						continue;
					}
					for (BlockState block : tiles) {
						Location bloc = block.getLocation();
						/*
						if (false && particleTick > PARTICLE_INTERVAL && player.getName().equals("sgdc3")) {
							blockParticles(player, Particle.VILLAGER_ANGRY, bloc);
						}
						*/
						boolean canSee = culling.isAABBVisible(player, playerEyeLocation, bloc, BLOCK_AABB);
						/*
						if (false && canSee && particleTick > PARTICLE_INTERVAL && player.getName().equals("sgdc3")) {
							blockParticles(player, Particle.VILLAGER_HAPPY, bloc);
						}
						*/
						boolean hidden = plugin.cache.isHidden(player, block.getLocation());
						if (hidden && canSee) {
							plugin.cache.setHidden(player, block.getLocation(), false);
							player.sendBlockChange(block.getLocation(), block.getBlockData());
							/*
							if (player.getName().equals("sgdc3") && bloc.getBlockX() == 11 && bloc.getBlockY() == 4 && bloc.getBlockZ() == 232) {
								plugin.getLogger().warning("SHOWN > canSee: " + canSee + " hidden: " + hidden);
							}
							*/
						} else if (!hidden && !canSee) {
							plugin.cache.setHidden(player, block.getLocation(), true);
							player.sendBlockChange(block.getLocation(), Material.AIR, (byte) 0);
							/*
							if (player.getName().equals("sgdc3") && bloc.getBlockX() == 11 && bloc.getBlockY() == 4 && bloc.getBlockZ() == 232) {
								plugin.getLogger().warning("HIDDEN > canSee: " + canSee + " hidden: " + hidden);
							}
							*/
						}
						/*
						else {
							if (false && player.getName().equals("sgdc3") && bloc.getBlockX() == 11 && bloc.getBlockY() == 4 && bloc.getBlockZ() == 232) {
								plugin.getLogger().warning("NOOP > canSee: " + canSee + " hidden: " + hidden);
							}
						}
						*/
					}
				}
			}
		}
		if (particleTick > PARTICLE_INTERVAL) {
			particleTick = 0;
		}
		//System.err.println("Time: " + (System.currentTimeMillis() - start));
	}

}
