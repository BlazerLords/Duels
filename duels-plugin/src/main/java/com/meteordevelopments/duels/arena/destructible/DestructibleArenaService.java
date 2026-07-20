package com.meteordevelopments.duels.arena.destructible;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.core.kit.KitImpl;
import com.meteordevelopments.duels.core.match.DuelMatch;
import com.meteordevelopments.duels.hook.hooks.worldguard.WorldGuardHook;
import com.meteordevelopments.duels.util.Loadable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DestructibleArenaService implements Loadable, Listener {
    private final DuelsPlugin plugin;
    private final DestructibleArenaSessionRegistry registry = new DestructibleArenaSessionRegistry();
    private final ArenaProtectionService protection = new ArenaProtectionService(registry);
    private final ArenaExplosionService explosions = new ArenaExplosionService(registry);
    private final ArenaEntityTracker entities;
    private final ArenaRestoreService restore;
    private final ArenaRecoveryService recovery;

    public DestructibleArenaService(DuelsPlugin plugin) {
        this.plugin = plugin;
        entities = new ArenaEntityTracker(plugin, registry);
        restore = new ArenaRestoreService(plugin, registry);
        recovery = new ArenaRecoveryService(plugin, registry, restore);
        restore.setBeforeRestore(this::releaseWorldGuardBypass);
        restore.setCompletion(recovery::saveSnapshotAsync);
        plugin.registerListener(this);
    }

    @Override
    public void handleLoad() { recovery.start(); }

    @Override
    public void handleUnload() {
        List.copyOf(registry.sessions()).forEach(this::releaseWorldGuardBypass);
        recovery.shutdown();
        for (DestructibleArenaSession session : registry.sessions()) {
            if (session.getRestoreTask() != null) session.getRestoreTask().cancel();
        }
    }

    public StartResult validateStart(ArenaImpl arena, KitImpl kit) {
        if (kit == null || !kit.getDestructibleArena().isEnabled()) return StartResult.DISABLED;
        if (arena.getArenaBounds() == null) return StartResult.MISSING_BOUNDS;
        DestructibleArenaSession current = registry.byArena(arena.getName());
        if (current != null && current.getState() != SessionState.READY && current.getState() != SessionState.CANCELLED) {
            return StartResult.ARENA_BUSY;
        }
        return StartResult.READY;
    }

    public DestructibleArenaSession start(DuelMatch match, MatchType type, Collection<Player> players) {
        ArenaImpl arena = match.getArena();
        KitImpl kit = match.getKit();
        if (validateStart(arena, kit) != StartResult.READY) return null;
        String matchId = arena.getName() + ":" + match.getCreation();
        DestructibleArenaSession session = new DestructibleArenaSession(matchId, arena.getName(), kit.getName(),
                type, arena.getArenaBounds(), kit.getDestructibleArena());
        players.forEach(player -> session.getPlayers().add(player.getUniqueId()));
        if (!registry.register(session)) return null;
        session.setState(SessionState.ACTIVE);
        return session;
    }

    public boolean finish(DuelMatch match) {
        DestructibleArenaSession session = registry.byMatch(match.getArena().getName() + ":" + match.getCreation());
        return session != null && restore.restore(session);
    }

    public DestructibleArenaSessionRegistry getRegistry() { return registry; }
    public ArenaProtectionService getProtection() { return protection; }
    public ArenaExplosionService getExplosions() { return explosions; }
    public ArenaEntityTracker getEntities() { return entities; }
    public ArenaRestoreService getRestore() { return restore; }

    public boolean isDestructionDisabled(final Player player) {
        final ArenaImpl arena = plugin.getArenaManager().get(player);
        if (arena == null || arena.getMatch() == null) return false;
        final KitImpl kit = arena.getMatch().getKit();
        return kit == null || !kit.getDestructibleArena().isEnabled();
    }

    public boolean isDestructionDisabledAt(final Location location) {
        if (location == null || location.getWorld() == null) return false;
        for (ArenaImpl arena : plugin.getArenaManager().getArenasImpl()) {
            if (!arena.isUsed() || arena.getMatch() == null || arena.getArenaBounds() == null
                    || !arena.isInBounds(location)) continue;
            final KitImpl kit = arena.getMatch().getKit();
            if (kit == null || !kit.getDestructibleArena().isEnabled()) return true;
        }
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        DestructibleArenaSession session = registry.byPlayer(event.getPlayer());
        if (session != null) {
            releaseWorldGuardBypass(session, event.getPlayer().getUniqueId());
        }
    }

    private void grantWorldGuardBypass(DestructibleArenaSession session, Collection<Player> players) {
        WorldGuardHook worldGuard = Bukkit.getPluginManager().isPluginEnabled(WorldGuardHook.NAME)
                ? DuelsPlugin.getInstance().getHookManager().getHook(WorldGuardHook.class)
                : null;
        World world = Bukkit.getWorld(session.getBounds().worldId());
        if (worldGuard == null || world == null) {
            return;
        }

        for (Player player : players) {
            DestructibleArenaSession.WorldGuardBypassKey key =
                    new DestructibleArenaSession.WorldGuardBypassKey(player.getUniqueId(), world.getUID());
            session.getWorldGuardBypasses().putIfAbsent(key, worldGuard.enableBypass(player, world));
        }
    }

    private void releaseWorldGuardBypass(DestructibleArenaSession session) {
        for (DestructibleArenaSession.WorldGuardBypassKey key : List.copyOf(session.getWorldGuardBypasses().keySet())) {
            releaseWorldGuardBypass(session, key.playerId());
        }
    }

    private void releaseWorldGuardBypass(DestructibleArenaSession session, UUID playerId) {
        WorldGuardHook worldGuard = DuelsPlugin.getInstance().getHookManager().getHook(WorldGuardHook.class);
        for (Map.Entry<DestructibleArenaSession.WorldGuardBypassKey, com.meteordevelopments.duels.hook.hooks.worldguard.WorldGuardHandler.BypassState> entry
                : List.copyOf(session.getWorldGuardBypasses().entrySet())) {
            DestructibleArenaSession.WorldGuardBypassKey key = entry.getKey();
            if (!key.playerId().equals(playerId)) {
                continue;
            }
            if (worldGuard != null) {
                worldGuard.restoreBypass(key.playerId(), key.worldId(), entry.getValue());
            }
            session.getWorldGuardBypasses().remove(key);
        }
    }

    public enum StartResult { DISABLED, READY, MISSING_BOUNDS, ARENA_BUSY }
}
