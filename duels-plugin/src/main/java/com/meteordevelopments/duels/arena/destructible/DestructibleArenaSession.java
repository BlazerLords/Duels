package com.meteordevelopments.duels.arena.destructible;

import com.meteordevelopments.duels.hook.hooks.worldguard.WorldGuardHandler;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DestructibleArenaSession {
    private final UUID sessionId = UUID.randomUUID();
    private final String matchId;
    private final String arenaId;
    private final String kitId;
    private final MatchType matchType;
    private final ArenaBounds bounds;
    private final DestructibleArenaConfig config;
    private SessionState state = SessionState.PREPARING;
    private final Map<BlockPosition, StoredBlockState> originalBlocks = new LinkedHashMap<>();
    private final Set<UUID> trackedEntities = new LinkedHashSet<>();
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Map<WorldGuardBypassKey, WorldGuardHandler.BypassState> worldGuardBypasses = new LinkedHashMap<>();
    private final long startedAt = System.currentTimeMillis();
    private BukkitTask restoreTask;
    private int restoredBlocks;

    public DestructibleArenaSession(String matchId, String arenaId, String kitId, MatchType matchType,
                                    ArenaBounds bounds, DestructibleArenaConfig config) {
        this.matchId = matchId;
        this.arenaId = arenaId;
        this.kitId = kitId;
        this.matchType = matchType;
        this.bounds = bounds;
        this.config = config.copy();
    }

    public UUID getSessionId() { return sessionId; }
    public String getMatchId() { return matchId; }
    public String getArenaId() { return arenaId; }
    public String getKitId() { return kitId; }
    public MatchType getMatchType() { return matchType; }
    public ArenaBounds getBounds() { return bounds; }
    public DestructibleArenaConfig getConfig() { return config; }
    public SessionState getState() { return state; }
    public void setState(SessionState state) { this.state = state; }
    public Map<BlockPosition, StoredBlockState> getOriginalBlocks() { return originalBlocks; }
    public Set<UUID> getTrackedEntities() { return trackedEntities; }
    public Set<UUID> getPlayers() { return players; }
    public Map<WorldGuardBypassKey, WorldGuardHandler.BypassState> getWorldGuardBypasses() { return worldGuardBypasses; }
    public long getStartedAt() { return startedAt; }
    public BukkitTask getRestoreTask() { return restoreTask; }
    public void setRestoreTask(BukkitTask restoreTask) { this.restoreTask = restoreTask; }
    public int getRestoredBlocks() { return restoredBlocks; }
    public void incrementRestoredBlocks() { restoredBlocks++; }
    public boolean isActive() { return state == SessionState.ACTIVE; }

    public record WorldGuardBypassKey(UUID playerId, UUID worldId) {
    }
}
