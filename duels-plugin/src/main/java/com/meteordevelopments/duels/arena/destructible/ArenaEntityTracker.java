package com.meteordevelopments.duels.arena.destructible;

import com.meteordevelopments.duels.DuelsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

public final class ArenaEntityTracker {
    private final NamespacedKey matchKey;
    private final NamespacedKey arenaKey;
    private final NamespacedKey sessionKey;
    private final DestructibleArenaSessionRegistry registry;

    public ArenaEntityTracker(DuelsPlugin plugin, DestructibleArenaSessionRegistry registry) {
        this.registry = registry;
        matchKey = new NamespacedKey(plugin, "match_id");
        arenaKey = new NamespacedKey(plugin, "arena_id");
        sessionKey = new NamespacedKey(plugin, "arena_session_id");
    }

    public void track(DestructibleArenaSession session, Entity entity) {
        entity.getPersistentDataContainer().set(matchKey, PersistentDataType.STRING, session.getMatchId());
        entity.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, session.getArenaId());
        entity.getPersistentDataContainer().set(sessionKey, PersistentDataType.STRING, session.getSessionId().toString());
        registry.indexEntity(session, entity.getUniqueId());
    }

    public boolean belongsTo(DestructibleArenaSession session, Entity entity) {
        String value = entity.getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
        return session.getSessionId().toString().equals(value);
    }
}
