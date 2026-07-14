package com.meteordevelopments.duels.listeners;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.arena.destructible.ArenaChangeTracker;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaSession;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.StructureGrowEvent;

public final class ArenaBoundaryListener implements Listener {
    private final DestructibleArenaService service;

    public ArenaBoundaryListener(DuelsPlugin plugin) { service = plugin.getDestructibleArenaService(); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        DestructibleArenaSession session = resolve(event.getBlock(), event.getToBlock());
        if (session == null) return;
        if (!session.getConfig().isAllowLiquids() || !session.getBounds().contains(event.getBlock())
                || !session.getBounds().contains(event.getToBlock())) {
            event.setCancelled(true);
            return;
        }
        ArenaChangeTracker tracker = new ArenaChangeTracker(session.getOriginalBlocks());
        tracker.capture(event.getBlock());
        tracker.capture(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        DestructibleArenaSession session = pistonSession(event.getBlock(), event.getBlocks(), event.getDirection());
        if (session == null) return;
        if (!session.getConfig().isAllowPistons() || event.getBlocks().stream()
                .anyMatch(block -> !session.getBounds().contains(block)
                        || !session.getBounds().contains(block.getRelative(event.getDirection())))) {
            event.setCancelled(true);
            return;
        }
        ArenaChangeTracker tracker = new ArenaChangeTracker(session.getOriginalBlocks());
        tracker.capture(event.getBlock());
        tracker.capture(event.getBlock().getRelative(event.getDirection()));
        event.getBlocks().forEach(block -> {
            tracker.capture(block);
            tracker.capture(block.getRelative(event.getDirection()));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        org.bukkit.block.BlockFace movement = event.getDirection().getOppositeFace();
        DestructibleArenaSession session = pistonSession(event.getBlock(), event.getBlocks(), movement);
        if (session == null) return;
        if (!session.getConfig().isAllowPistons() || event.getBlocks().stream()
                .anyMatch(block -> !session.getBounds().contains(block)
                        || !session.getBounds().contains(block.getRelative(movement)))) {
            event.setCancelled(true);
            return;
        }
        ArenaChangeTracker tracker = new ArenaChangeTracker(session.getOriginalBlocks());
        tracker.capture(event.getBlock());
        tracker.capture(event.getBlock().getRelative(event.getDirection()));
        event.getBlocks().forEach(block -> {
            tracker.capture(block);
            tracker.capture(block.getRelative(movement));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { protectFire(event.getBlock(), event); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) { protectFire(event.getBlock(), event); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        DestructibleArenaSession session = resolve(event.getSource(), event.getBlock());
        if (session == null) return;
        if (!session.getConfig().isAllowFire() || !session.getBounds().contains(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        new ArenaChangeTracker(session.getOriginalBlocks()).capture(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        DestructibleArenaSession session = service.getRegistry().at(event.getBlock().getLocation());
        if (session == null) session = service.getRegistry().byEntity(event.getEntity());
        if (session == null) return;
        if (!session.isActive() || !session.getBounds().contains(event.getEntity())
                || !session.getBounds().contains(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        new ArenaChangeTracker(session.getOriginalBlocks()).capture(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        DestructibleArenaSession session = service.getRegistry().at(event.getLocation());
        if (session != null && event.getBlocks().stream().anyMatch(state -> !session.getBounds().contains(state.getBlock()))) {
            event.setCancelled(true);
        } else if (session != null) {
            ArenaChangeTracker tracker = new ArenaChangeTracker(session.getOriginalBlocks());
            event.getBlocks().forEach(tracker::capture);
        }
    }

    private void protectFire(Block block, org.bukkit.event.Cancellable event) {
        DestructibleArenaSession session = service.getRegistry().at(block.getLocation());
        if (session == null) return;
        if (!session.getConfig().isAllowFire() || !session.getBounds().contains(block)) {
            event.setCancelled(true);
        } else {
            new ArenaChangeTracker(session.getOriginalBlocks()).capture(block);
        }
    }

    private DestructibleArenaSession resolve(Block first, Block second) {
        DestructibleArenaSession session = service.getRegistry().at(first.getLocation());
        return session != null ? session : service.getRegistry().at(second.getLocation());
    }

    private DestructibleArenaSession pistonSession(Block piston, java.util.List<Block> blocks, org.bukkit.block.BlockFace movement) {
        DestructibleArenaSession session = service.getRegistry().at(piston.getLocation());
        if (session != null) return session;
        for (Block block : blocks) {
            session = resolve(block, block.getRelative(movement));
            if (session != null) return session;
        }
        return service.getRegistry().at(piston.getRelative(movement).getLocation());
    }
}
