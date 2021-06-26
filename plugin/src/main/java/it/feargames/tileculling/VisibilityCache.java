package it.feargames.tileculling;

import it.feargames.tileculling.util.LocationUtilities;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.LongPredicate;

public class VisibilityCache implements Listener {

    private final Map<Player, Long2BooleanMap> hiddenBlocks = new HashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final WriteLock writeLock = lock.writeLock();
    private final ReadLock readLock = lock.readLock();

    public void setHidden(Player player, long blockKey, boolean hidden) {
        try {
            writeLock.lock();
            Long2BooleanMap blocks = hiddenBlocks.computeIfAbsent(player, p -> new Long2BooleanOpenHashMap());
            blocks.put(blockKey, hidden);
        } finally {
            writeLock.unlock();
        }
    }

    public void setHidden(Player player, Location blockLocation, boolean hidden) {
        setHidden(player, LocationUtilities.getBlockKey(blockLocation), hidden);
    }

    public boolean isHidden(Player player, long blockKey) {
        try {
            readLock.lock();
            Long2BooleanMap blocks = hiddenBlocks.get(player);
            boolean result;
            if (blocks == null) {
                result = true;
            } else {
                result = blocks.getOrDefault(blockKey, true);
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    public boolean isHidden(Player player, Location blockLocation) {
        return isHidden(player, LocationUtilities.getBlockKey(blockLocation));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invalidateCache(event.getPlayer());
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        try {
            writeLock.lock();
            for (Long2BooleanMap blocks : hiddenBlocks.values()) {
                blocks.keySet().removeIf((LongPredicate) block -> {
                    int chunkX = LocationUtilities.getBlockKeyX(block) >> 4;
                    if (event.getChunk().getX() != chunkX) {
                        return false;
                    }
                    int chunkZ = LocationUtilities.getBlockKeyZ(block) >> 4;
                    return event.getChunk().getZ() == chunkZ;
                });
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void invalidateCache(Player player) {
        try {
            writeLock.lock();
            hiddenBlocks.remove(player);
        } finally {
            writeLock.unlock();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        invalidateCache(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        invalidateCache(event.getPlayer());
    }
}
