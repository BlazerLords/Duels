package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class DestructibleArenaSessionRegistry {
    private final Map<String, DestructibleArenaSession> byArena = new HashMap<>();
    private final Map<String, DestructibleArenaSession> byMatch = new HashMap<>();
    private final Map<UUID, DestructibleArenaSession> byPlayer = new HashMap<>();
    private final Map<UUID, DestructibleArenaSession> byEntity = new HashMap<>();
    private final Map<UUID, Set<DestructibleArenaSession>> byWorld = new HashMap<>();

    public synchronized boolean register(DestructibleArenaSession session) {
        DestructibleArenaSession current = byArena.get(key(session.getArenaId()));
        if (current != null && current.getState() != SessionState.READY && current.getState() != SessionState.CANCELLED) {
            return false;
        }
        byArena.put(key(session.getArenaId()), session);
        byMatch.put(session.getMatchId(), session);
        session.getPlayers().forEach(player -> byPlayer.put(player, session));
        byWorld.computeIfAbsent(session.getBounds().worldId(), ignored -> new LinkedHashSet<>()).add(session);
        return true;
    }

    public synchronized void indexPlayer(DestructibleArenaSession session, UUID player) {
        session.getPlayers().add(player);
        byPlayer.put(player, session);
    }

    public DestructibleArenaSession byArena(String arena) { return byArena.get(key(arena)); }
    public DestructibleArenaSession byMatch(String match) { return byMatch.get(match); }
    public DestructibleArenaSession byPlayer(Player player) { return player == null ? null : byPlayer.get(player.getUniqueId()); }
    public DestructibleArenaSession byEntity(org.bukkit.entity.Entity entity) { return entity == null ? null : byEntity.get(entity.getUniqueId()); }

    public synchronized void indexEntity(DestructibleArenaSession session, UUID entity) {
        session.getTrackedEntities().add(entity);
        byEntity.put(entity, session);
    }

    public DestructibleArenaSession at(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Set<DestructibleArenaSession> sessions = byWorld.get(location.getWorld().getUID());
        if (sessions == null) return null;
        for (DestructibleArenaSession session : sessions) {
            if (session.getState() != SessionState.READY && session.getState() != SessionState.CANCELLED
                    && session.getBounds().contains(location)) return session;
        }
        return null;
    }

    public Collection<DestructibleArenaSession> sessions() { return List.copyOf(byArena.values()); }

    public synchronized void remove(DestructibleArenaSession session) {
        byMatch.remove(session.getMatchId(), session);
        session.getPlayers().forEach(player -> byPlayer.remove(player, session));
        session.getTrackedEntities().forEach(entity -> byEntity.remove(entity, session));
        Set<DestructibleArenaSession> world = byWorld.get(session.getBounds().worldId());
        if (world != null) {
            world.remove(session);
            if (world.isEmpty()) byWorld.remove(session.getBounds().worldId());
        }
        // Keep READY in the arena index for diagnostics and restore status.
        byArena.put(key(session.getArenaId()), session);
    }

    private static String key(String value) { return value.toLowerCase(Locale.ROOT); }
}
