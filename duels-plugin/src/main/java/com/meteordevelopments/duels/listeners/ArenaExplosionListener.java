package com.meteordevelopments.duels.listeners;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.arena.destructible.ArenaExplosionService;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaSession;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public final class ArenaExplosionListener implements Listener {
    private final DestructibleArenaService service;

    public ArenaExplosionListener(DuelsPlugin plugin) { service = plugin.getDestructibleArenaService(); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent event) {
        ArenaExplosionService explosions = service.getExplosions();
        DestructibleArenaSession session = explosions.sessionFor(event.getEntity(), null, event.blockList());
        if (session == null) return;
        if (!explosions.allow(session, event.getEntity(), null)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        explosions.filterAndCapture(session, event.blockList());
        if (session.getConfig().isSuppressBlockDrops()) event.setYield(0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockExplode(BlockExplodeEvent event) {
        ArenaExplosionService explosions = service.getExplosions();
        DestructibleArenaSession session = explosions.sessionFor(null, event.getBlock(), event.blockList());
        if (session == null) return;
        if (!explosions.allow(session, null, event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        explosions.filterAndCapture(session, event.blockList());
        if (session.getConfig().isSuppressBlockDrops()) event.setYield(0F);
    }
}
