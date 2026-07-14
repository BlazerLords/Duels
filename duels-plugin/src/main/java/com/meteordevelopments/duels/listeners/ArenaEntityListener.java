package com.meteordevelopments.duels.listeners;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaSession;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaService;
import com.meteordevelopments.duels.arena.destructible.BlockPosition;
import com.meteordevelopments.duels.arena.destructible.StoredBlockState;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

public final class ArenaEntityListener implements Listener {
    private final DestructibleArenaService service;

    public ArenaEntityListener(DuelsPlugin plugin) { service = plugin.getDestructibleArenaService(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        DestructibleArenaSession session = service.getRegistry().at(entity.getLocation());
        if (session == null || !session.isActive()) return;
        if (entity instanceof EnderCrystal || entity instanceof Minecart || entity instanceof TNTPrimed || entity instanceof FallingBlock) {
            service.getEntities().track(session, entity);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        DestructibleArenaSession session = service.getRegistry().at(event.getBlock().getLocation());
        if (session != null && session.isActive() && session.getConfig().isSuppressBlockDrops()
                && !wasPlayerPlaced(session, event.getBlock())) {
            event.getItems().forEach(Entity::remove);
            event.getItems().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        DestructibleArenaSession session = service.getRegistry().at(event.getLocation());
        if (session == null || !session.isActive() || !session.getConfig().isSuppressBlockDrops()
                || event.getEntity().getThrower() != null) return;
        org.bukkit.block.Block block = event.getLocation().getBlock();
        boolean arenaBlockDrop = wasOriginalArenaBlock(session, block)
                || wasOriginalArenaBlock(session, block.getRelative(org.bukkit.block.BlockFace.DOWN));
        if (arenaBlockDrop) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDrop(EntityDropItemEvent event) {
        DestructibleArenaSession session = service.getRegistry().at(event.getEntity().getLocation());
        if (session != null && session.isActive() && session.getConfig().isSuppressEntityDrops()
                && service.getEntities().belongsTo(session, event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        DestructibleArenaSession session = service.getRegistry().at(event.getVehicle().getLocation());
        if (session != null && session.isActive() && session.getConfig().isSuppressEntityDrops()
                && service.getEntities().belongsTo(session, event.getVehicle())) {
            event.setCancelled(true);
            event.getVehicle().remove();
        }
    }

    private boolean wasPlayerPlaced(DestructibleArenaSession session, org.bukkit.block.Block block) {
        StoredBlockState original = session.getOriginalBlocks().get(BlockPosition.of(block));
        return original != null && original.wasAir();
    }

    private boolean wasOriginalArenaBlock(DestructibleArenaSession session, org.bukkit.block.Block block) {
        StoredBlockState original = session.getOriginalBlocks().get(BlockPosition.of(block));
        return original != null && !original.wasAir();
    }
}
