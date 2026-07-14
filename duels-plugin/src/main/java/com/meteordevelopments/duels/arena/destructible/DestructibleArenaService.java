package com.meteordevelopments.duels.arena.destructible;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.core.kit.KitImpl;
import com.meteordevelopments.duels.core.match.DuelMatch;
import com.meteordevelopments.duels.util.Loadable;
import org.bukkit.entity.Player;

import java.util.Collection;

public final class DestructibleArenaService implements Loadable {
    private final DestructibleArenaSessionRegistry registry = new DestructibleArenaSessionRegistry();
    private final ArenaProtectionService protection = new ArenaProtectionService(registry);
    private final ArenaExplosionService explosions = new ArenaExplosionService(registry);
    private final ArenaEntityTracker entities;
    private final ArenaRestoreService restore;
    private final ArenaRecoveryService recovery;

    public DestructibleArenaService(DuelsPlugin plugin) {
        entities = new ArenaEntityTracker(plugin, registry);
        restore = new ArenaRestoreService(plugin, registry);
        recovery = new ArenaRecoveryService(plugin, registry, restore);
        restore.setCompletion(recovery::saveSnapshotAsync);
    }

    @Override
    public void handleLoad() { recovery.start(); }

    @Override
    public void handleUnload() {
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

    public enum StartResult { DISABLED, READY, MISSING_BOUNDS, ARENA_BUSY }
}
