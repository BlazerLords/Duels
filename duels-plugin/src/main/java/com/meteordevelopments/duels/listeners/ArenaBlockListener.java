package com.meteordevelopments.duels.listeners;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.arena.destructible.ArenaChangeTracker;
import com.meteordevelopments.duels.arena.destructible.BlockPosition;
import com.meteordevelopments.duels.arena.destructible.ArenaProtectionService;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaSession;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaService;
import com.meteordevelopments.duels.util.StringUtil;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public final class ArenaBlockListener implements Listener {
    private final DestructibleArenaService service;

    public ArenaBlockListener(DuelsPlugin plugin) {
        service = plugin.getDestructibleArenaService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent) return;
        if (denyWhenMasterDisabled(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (!applies(event.getPlayer(), event.getBlockPlaced())) return;
        ArenaProtectionService.Decision decision = service.getProtection().canPlace(event.getPlayer(), event.getBlockPlaced(), event.getBlockPlaced().getType());
        if (!decision.allowed()) {
            event.setCancelled(true);
            message(event.getPlayer(), decision.message());
            return;
        }
        new ArenaChangeTracker(decision.session().getOriginalBlocks()).capture(event.getBlockReplacedState());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        if (denyWhenMasterDisabled(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (event.getReplacedBlockStates().stream().noneMatch(state -> applies(event.getPlayer(), state.getBlock()))) return;
        DestructibleArenaSession session = null;
        for (BlockState state : event.getReplacedBlockStates()) {
            ArenaProtectionService.Decision decision = service.getProtection().canPlace(event.getPlayer(), state.getBlock(), state.getBlock().getType());
            if (!decision.allowed()) {
                event.setCancelled(true);
                message(event.getPlayer(), decision.message());
                return;
            }
            session = decision.session();
        }
        if (session != null) {
            ArenaChangeTracker tracker = new ArenaChangeTracker(session.getOriginalBlocks());
            event.getReplacedBlockStates().forEach(tracker::capture);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denyWhenMasterDisabled(event.getPlayer())) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }
        if (!applies(event.getPlayer(), event.getBlock())) return;
        ArenaProtectionService.Decision decision = service.getProtection().canBreak(event.getPlayer(), event.getBlock());
        if (!decision.allowed()) {
            event.setCancelled(true);
            message(event.getPlayer(), decision.message());
            return;
        }
        DestructibleArenaSession session = decision.session();
        BlockPosition position = BlockPosition.of(event.getBlock());
        boolean playerPlaced = session.getOriginalBlocks().containsKey(position)
                && session.getOriginalBlocks().get(position).wasAir();
        new ArenaChangeTracker(session.getOriginalBlocks()).capture(event.getBlock());
        if (!playerPlaced && session.getConfig().isSuppressBlockDrops()) {
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (denyWhenMasterDisabled(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        org.bukkit.block.Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!applies(event.getPlayer(), target)) return;
        DestructibleArenaSession session = service.getRegistry().byPlayer(event.getPlayer());
        if (session == null || !session.isActive() || !session.getBounds().contains(target)
                || !session.getConfig().isAllowLiquids()) {
            event.setCancelled(true);
            message(event.getPlayer(), "&cЖидкости выключены или находятся за границей арены.");
            return;
        }
        new ArenaChangeTracker(session.getOriginalBlocks()).capture(target);
    }

    private void message(org.bukkit.entity.Player player, String message) {
        if (message != null) player.sendMessage(StringUtil.color(message));
    }

    private boolean applies(org.bukkit.entity.Player player, org.bukkit.block.Block block) {
        return service.getRegistry().byPlayer(player) != null || service.getRegistry().at(block.getLocation()) != null;
    }

    private boolean denyWhenMasterDisabled(final org.bukkit.entity.Player player) {
        if (!service.isDestructionDisabled(player)) return false;
        message(player, "&cИзменение арены выключено для выбранного кита.");
        return true;
    }
}
