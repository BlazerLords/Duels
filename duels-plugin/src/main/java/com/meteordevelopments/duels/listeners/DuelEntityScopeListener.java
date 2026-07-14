package com.meteordevelopments.duels.listeners;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.core.arena.ArenaManagerImpl;
import com.meteordevelopments.duels.core.spectate.SpectateManagerImpl;
import com.meteordevelopments.duels.util.compat.DuelEntities;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;

public class DuelEntityScopeListener implements Listener {

    private final ArenaManagerImpl arenaManager;
    private final SpectateManagerImpl spectateManager;

    public DuelEntityScopeListener(DuelsPlugin plugin) {
        this.arenaManager = plugin.getArenaManager();
        this.spectateManager = plugin.getSpectateManager();
        plugin.registerListener(this);
    }

    private ArenaImpl arenaOf(Player p) {
        return arenaManager.get(p);
    }

    private boolean sameArena(ArenaImpl a, ArenaImpl b) {
        return a != null && b != null && a.getName().equals(b.getName());
    }

    private String resolveProjectileArena(Projectile proj) {
        String marker = DuelEntities.getArenaName(proj);
        if (marker != null)
            return marker;
        if (!(proj.getShooter() instanceof Player shooter))
            return null;
        ArenaImpl arena = arenaOf(shooter);
        return arena != null ? arena.getName() : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ProjectileLaunchEvent event) { // mark every duelist's projectile at birth
        Projectile proj = event.getEntity();
        if (!(proj.getShooter() instanceof Player shooter))
            return;
        ArenaImpl arena = arenaOf(shooter);
        if (arena == null)
            return;
        DuelEntities.mark(proj, arena);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntitySpawnEvent event) { // mark AEC + experience orbs born inside arena bounds
        Entity entity = event.getEntity();

        if (entity instanceof AreaEffectCloud cloud) {
            if (!(cloud.getSource() instanceof Player source))
                return;
            ArenaImpl arena = arenaOf(source);
            if (arena == null)
                return;
            DuelEntities.mark(cloud, arena);
            return;
        }

        if (entity instanceof ExperienceOrb orb) {
            for (ArenaImpl arena: arenaManager.getArenasImpl()) {
                if (!arena.isUsed() || !arena.hasBounds())
                    continue;
                if (arena.isInBounds(orb.getLocation())) {
                    DuelEntities.mark(orb, arena);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent event) { // disable damage to foreign players
        if (!(event.getEntity() instanceof Player victim))
            return;
        if (!(event.getDamager() instanceof Projectile proj))
            return;

        String arenaName = resolveProjectileArena(proj);
        if (arenaName == null)
            return;

        ArenaImpl victimArena = arenaOf(victim);
        if (victimArena != null && arenaName.equals(victimArena.getName()))
            return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void on(PotionSplashEvent event) { // disable splash potions for non-arena entities
        String arenaName = resolveProjectileArena(event.getPotion());
        if (arenaName == null)
            return;

        for (LivingEntity entity: event.getAffectedEntities()) {
            if (!(entity instanceof Player p))
                continue;
            if (spectateManager.isSpectating(p)) {
                event.setIntensity(p, 0.0);
                continue;
            }
            ArenaImpl pArena = arenaOf(p);
            if (pArena == null || !arenaName.equals(pArena.getName()))
                event.setIntensity(p, 0.0);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void on(AreaEffectCloudApplyEvent event) { // apply cloud effects only to arena participants
        String arenaName = DuelEntities.getArenaName(event.getEntity());

        if (arenaName == null) {
            if (!(event.getEntity().getSource() instanceof Player source))
                return;
            ArenaImpl arena = arenaOf(source);
            if (arena == null)
                return;
            arenaName = arena.getName();
        }

        String finalArena = arenaName;
        event.getAffectedEntities().removeIf(entity -> {
            if (!(entity instanceof Player p))
                return false;
            if (spectateManager.isSpectating(p))
                return true;
            ArenaImpl pArena = arenaOf(p);
            return pArena == null || !finalArena.equals(pArena.getName());
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void on(PlayerPickupArrowEvent event) { // disable foreign pickup of duel arrows
        AbstractArrow arrow = event.getArrow();
        String arenaName = DuelEntities.getArenaName(arrow);
        if (arenaName == null) {
            if (!(arrow.getShooter() instanceof Player shooter))
                return;
            ArenaImpl shooterArena = arenaOf(shooter);
            if (shooterArena == null)
                return;
            arenaName = shooterArena.getName();
        }

        ArenaImpl pickerArena = arenaOf(event.getPlayer());
        if (pickerArena != null && arenaName.equals(pickerArena.getName()))
            return;

        event.setCancelled(true);
        arrow.remove();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void on(PlayerFishEvent event) { // block any cross-arena fishing rod pull
        if (!(event.getCaught() instanceof Player target))
            return;

        ArenaImpl fisherArena = arenaOf(event.getPlayer());
        ArenaImpl targetArena = arenaOf(target);

        if (fisherArena == null && targetArena == null)
            return;
        if (sameArena(fisherArena, targetArena))
            return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void on(PlayerPickupExperienceEvent event) { // foreign pickup → cancel + despawn orb
        ExperienceOrb orb = event.getExperienceOrb();
        if (!DuelEntities.isDuelEntity(orb))
            return;
        String arenaName = DuelEntities.getArenaName(orb);
        ArenaImpl playerArena = arenaOf(event.getPlayer());
        if (playerArena != null && playerArena.getName().equals(arenaName))
            return;
        event.setCancelled(true);
        orb.remove();
    }
}
