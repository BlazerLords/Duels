package com.meteordevelopments.duels.arena.destructible;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.api.folialib.task.WrappedTask;
import com.meteordevelopments.duels.util.io.AtomicFileWriter;
import com.meteordevelopments.duels.util.io.LatestSnapshotWriter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class ArenaRecoveryService {
    private final DuelsPlugin plugin;
    private final DestructibleArenaSessionRegistry registry;
    private final ArenaRestoreService restoreService;
    private final File file;
    private WrappedTask snapshotTask;
    private LatestSnapshotWriter<String> writer;

    public ArenaRecoveryService(DuelsPlugin plugin, DestructibleArenaSessionRegistry registry, ArenaRestoreService restoreService) {
        this.plugin = plugin;
        this.registry = registry;
        this.restoreService = restoreService;
        this.file = new File(plugin.getDataFolder(), "arena-recovery.yml");
    }

    public void start() {
        writer = new LatestSnapshotWriter<>(plugin::doAsync,
                snapshot -> AtomicFileWriter.writeUtf8(file, snapshot),
                ex -> plugin.getLogger().warning("Could not save destructible arena recovery: " + ex.getMessage()));
        recover();
        long interval = Math.max(200L,
                plugin.getConfig().getLong("destructible-arena.recovery.snapshot-interval-ticks", 1200L));
        snapshotTask = plugin.doSyncRepeat(this::saveSnapshotAsync, interval, interval);
    }

    public void shutdown() {
        if (snapshotTask != null && !snapshotTask.isCancelled()) {
            plugin.cancelTask(snapshotTask);
        }
        snapshotTask = null;
        if (writer != null) {
            writer.closeAndWrite(serialize(snapshot()));
            writer = null;
        }
    }

    public void saveSnapshotAsync() {
        if (writer != null) {
            writer.submit(serialize(snapshot()));
        }
    }

    private List<RecoverySnapshot> snapshot() {
        List<RecoverySnapshot> snapshots = new ArrayList<>();
        for (DestructibleArenaSession session : registry.sessions()) {
            if (session.getState() == SessionState.READY || session.getState() == SessionState.CANCELLED) continue;
            List<RecoveryBlock> blocks = session.getOriginalBlocks().entrySet().stream()
                    .map(entry -> new RecoveryBlock(entry.getKey(), entry.getValue().material().name(), entry.getValue().blockData(),
                            entry.getValue().inventory(), entry.getValue().signLines()))
                    .toList();
            snapshots.add(new RecoverySnapshot(session, blocks, List.copyOf(session.getTrackedEntities())));
        }
        return snapshots;
    }

    private String serialize(List<RecoverySnapshot> snapshots) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (RecoverySnapshot snapshot : snapshots) snapshot.write(yaml);
        return yaml.saveToString();
    }

    private void recover() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sessions = yaml.getConfigurationSection("sessions");
        if (sessions == null) return;
        for (String id : sessions.getKeys(false)) {
            String path = "sessions." + id;
            try {
                ArenaBounds bounds = new ArenaBounds(UUID.fromString(yaml.getString(path + ".world")),
                        yaml.getInt(path + ".min-x"), yaml.getInt(path + ".min-y"), yaml.getInt(path + ".min-z"),
                        yaml.getInt(path + ".max-x"), yaml.getInt(path + ".max-y"), yaml.getInt(path + ".max-z"));
                DestructibleArenaConfig config = new DestructibleArenaConfig();
                config.setEnabled(true);
                DestructibleArenaSession session = new DestructibleArenaSession(yaml.getString(path + ".match-id", id),
                        yaml.getString(path + ".arena-id"), yaml.getString(path + ".kit-id", "unknown"),
                        MatchType.valueOf(yaml.getString(path + ".match-type", MatchType.DUEL.name())), bounds, config);
                ConfigurationSection blocks = yaml.getConfigurationSection(path + ".blocks");
                if (blocks != null) {
                    for (String key : blocks.getKeys(false)) {
                        String blockPath = path + ".blocks." + key;
                        BlockPosition position = new BlockPosition(bounds.worldId(), yaml.getInt(blockPath + ".x"),
                                yaml.getInt(blockPath + ".y"), yaml.getInt(blockPath + ".z"));
                        Material material = Material.matchMaterial(yaml.getString(blockPath + ".material", "AIR"));
                        if (material != null) {
                            List<?> rawItems = yaml.getList(blockPath + ".inventory");
                            org.bukkit.inventory.ItemStack[] inventory = rawItems == null ? null : rawItems.stream()
                                    .map(value -> value instanceof org.bukkit.inventory.ItemStack item ? item : null)
                                    .toArray(org.bukkit.inventory.ItemStack[]::new);
                            List<String> lines = yaml.getStringList(blockPath + ".sign-lines");
                            session.getOriginalBlocks().put(position, StoredBlockState.recovered(material,
                                    yaml.getString(blockPath + ".data", material.createBlockData().getAsString()),
                                    inventory, lines.isEmpty() ? null : lines.toArray(String[]::new)));
                        }
                    }
                }
                yaml.getStringList(path + ".entities").stream().map(UUID::fromString).forEach(session.getTrackedEntities()::add);
                registry.register(session);
                restoreService.restore(session);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Could not recover arena session " + id + ": " + ex.getMessage());
            }
        }
    }

    private record RecoveryBlock(BlockPosition position, String material, String data,
                                 org.bukkit.inventory.ItemStack[] inventory, String[] signLines) {}

    private record RecoverySnapshot(String sessionId, String matchId, String arenaId, String kitId, MatchType matchType,
                                    ArenaBounds bounds, List<RecoveryBlock> blocks, List<UUID> entities) {
        RecoverySnapshot(DestructibleArenaSession session, List<RecoveryBlock> blocks, List<UUID> entities) {
            this(session.getSessionId().toString(), session.getMatchId(), session.getArenaId(), session.getKitId(),
                    session.getMatchType(), session.getBounds(), blocks, entities);
        }

        void write(YamlConfiguration yaml) {
            String path = "sessions." + sessionId;
            yaml.set(path + ".match-id", matchId);
            yaml.set(path + ".arena-id", arenaId);
            yaml.set(path + ".kit-id", kitId);
            yaml.set(path + ".match-type", matchType.name());
            yaml.set(path + ".world", bounds.worldId().toString());
            yaml.set(path + ".min-x", bounds.minX()); yaml.set(path + ".min-y", bounds.minY()); yaml.set(path + ".min-z", bounds.minZ());
            yaml.set(path + ".max-x", bounds.maxX()); yaml.set(path + ".max-y", bounds.maxY()); yaml.set(path + ".max-z", bounds.maxZ());
            for (int i = 0; i < blocks.size(); i++) {
                RecoveryBlock block = blocks.get(i);
                String blockPath = path + ".blocks." + i;
                yaml.set(blockPath + ".x", block.position.x()); yaml.set(blockPath + ".y", block.position.y()); yaml.set(blockPath + ".z", block.position.z());
                yaml.set(blockPath + ".material", block.material); yaml.set(blockPath + ".data", block.data);
                if (block.inventory != null) yaml.set(blockPath + ".inventory", java.util.Arrays.asList(block.inventory));
                if (block.signLines != null) yaml.set(blockPath + ".sign-lines", java.util.Arrays.asList(block.signLines));
            }
            yaml.set(path + ".entities", entities.stream().map(UUID::toString).toList());
        }
    }
}
