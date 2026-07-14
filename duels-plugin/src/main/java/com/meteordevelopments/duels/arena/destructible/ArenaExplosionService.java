package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.List;

public final class ArenaExplosionService {
    private final DestructibleArenaSessionRegistry registry;

    public ArenaExplosionService(DestructibleArenaSessionRegistry registry) {
        this.registry = registry;
    }

    public DestructibleArenaSession sessionFor(Entity entity, Block source, List<Block> blocks) {
        DestructibleArenaSession session = entity == null ? null : registry.at(entity.getLocation());
        if (session == null && source != null) session = registry.at(source.getLocation());
        if (session == null) {
            for (Block block : blocks) {
                session = registry.at(block.getLocation());
                if (session != null) break;
            }
        }
        return session;
    }

    public boolean allow(DestructibleArenaSession session, Entity entity, Block source) {
        if (session == null || !session.isActive() || !session.getConfig().isAllowExplosions()) return false;
        DestructibleArenaConfig config = session.getConfig();
        if (entity != null) {
            EntityType type = entity.getType();
            if (type == EntityType.ENDER_CRYSTAL) return config.isAllowEndCrystals();
            if (type == EntityType.MINECART_TNT) return config.isAllowTntMinecarts();
            if (type == EntityType.PRIMED_TNT) return config.isAllowTnt();
        }
        if (source != null) {
            Material type = source.getType();
            if (type == Material.RESPAWN_ANCHOR) return config.isAllowRespawnAnchors();
            if (type.name().endsWith("_BED")) return config.isAllowBedExplosions();
        }
        return true;
    }

    public void filterAndCapture(DestructibleArenaSession session, List<Block> blocks) {
        ArenaChangeTracker tracker = new ArenaChangeTracker(session.getOriginalBlocks());
        blocks.removeIf(block -> {
            if (!session.getBounds().contains(block)) return true;
            tracker.capture(block);
            return false;
        });
    }
}
