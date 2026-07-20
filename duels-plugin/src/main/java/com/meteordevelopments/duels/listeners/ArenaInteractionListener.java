package com.meteordevelopments.duels.listeners;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.arena.destructible.ArenaChangeTracker;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaSession;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaService;
import com.meteordevelopments.duels.util.StringUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ArenaInteractionListener implements Listener {
    private final DestructibleArenaService service;

    public ArenaInteractionListener(DuelsPlugin plugin) { service = plugin.getDestructibleArenaService(); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        Block target = block.getRelative(event.getBlockFace());
        Material item = event.getItem() == null ? Material.AIR : event.getItem().getType();
        if (service.isDestructionDisabled(event.getPlayer()) && isDestructiveInteraction(item, block.getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(StringUtil.color("&cРазрушаемые механики выключены для выбранного кита."));
            return;
        }
        if (item == Material.END_CRYSTAL || item == Material.TNT_MINECART || item == Material.MINECART) {
            DestructibleArenaSession playerSession = service.getRegistry().byPlayer(event.getPlayer());
            DestructibleArenaSession locationSession = service.getRegistry().at(target.getLocation());
            if (playerSession == null && locationSession == null) return;
            boolean allowed = playerSession != null && playerSession.isActive() && playerSession.getBounds().contains(target)
                    && (item == Material.END_CRYSTAL ? playerSession.getConfig().isAllowEndCrystals()
                    : item == Material.TNT_MINECART ? playerSession.getConfig().isAllowTntMinecarts()
                    : playerSession.getConfig().isAllowRails());
            if (!allowed) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(StringUtil.color("&cЭту сущность нельзя установить здесь для выбранного кита."));
            }
            return;
        }
        Material type = block.getType();
        if (type != Material.RESPAWN_ANCHOR && !type.name().endsWith("_BED")) return;

        DestructibleArenaSession session = service.getRegistry().byPlayer(event.getPlayer());
        DestructibleArenaSession locationSession = service.getRegistry().at(block.getLocation());
        if (session == null && locationSession == null) return;
        boolean inSession = session != null && session.isActive() && session.getBounds().contains(block);
        boolean allowed = inSession && (type == Material.RESPAWN_ANCHOR
                ? session.getConfig().isAllowRespawnAnchors() : session.getConfig().isAllowBedExplosions());
        if (!allowed) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(StringUtil.color("&cЭта механика выключена или находится за границей арены."));
            return;
        }
        new ArenaChangeTracker(session.getOriginalBlocks()).capture(block);
    }

    private boolean isDestructiveInteraction(final Material item, final Material block) {
        return item == Material.END_CRYSTAL || item == Material.TNT_MINECART || item == Material.MINECART
                || item == Material.FLINT_AND_STEEL || item.name().endsWith("_BUCKET")
                || block == Material.RESPAWN_ANCHOR || block.name().endsWith("_BED");
    }
}
