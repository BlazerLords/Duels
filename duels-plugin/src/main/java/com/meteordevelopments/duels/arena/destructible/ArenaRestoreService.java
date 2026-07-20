package com.meteordevelopments.duels.arena.destructible;

import com.meteordevelopments.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;

public final class ArenaRestoreService {
    private final DuelsPlugin plugin;
    private final DestructibleArenaSessionRegistry registry;
    private final int blocksPerTick;
    private final long maxNanosPerTick;
    private final boolean applyPhysics;
    private long budgetTick = Long.MIN_VALUE;
    private int blocksRestoredThisTick;
    private long nanosUsedThisTick;
    private java.util.function.Consumer<DestructibleArenaSession> beforeRestore = session -> { };
    private Runnable completion = () -> { };

    public ArenaRestoreService(DuelsPlugin plugin, DestructibleArenaSessionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.blocksPerTick = Math.max(1, plugin.getConfig().getInt("destructible-arena.restore.blocks-per-tick", 100));
        this.maxNanosPerTick = (long) (Math.max(0.1,
                plugin.getConfig().getDouble("destructible-arena.restore.max-time-per-tick-ms", 2.0)) * 1_000_000L);
        this.applyPhysics = !plugin.getConfig().getBoolean("destructible-arena.restore.disable-physics", true);
    }

    public boolean restore(DestructibleArenaSession session) {
        if (session == null || session.getState() == SessionState.RESTORING || session.getState() == SessionState.READY) return false;
        // Bukkit rejects new tasks during plugin disable. Keep the session active so the
        // final recovery snapshot can restore it after the next server startup.
        if (!plugin.isEnabled()) return true;
        beforeRestore.accept(session);
        session.setState(SessionState.FINISHING);
        if (session.getConfig().isCleanupEntities()) {
            for (java.util.UUID entityId : session.getTrackedEntities()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) entity.remove();
            }
        }
        if (!session.getConfig().isRestoreAfterMatch()) {
            ready(session);
            return true;
        }
        session.setState(SessionState.RESTORING);
        BukkitTaskRunner task = new BukkitTaskRunner(session, session.getOriginalBlocks().entrySet().iterator());
        session.setRestoreTask(task.runTaskTimer(plugin, 1L, 1L));
        return true;
    }

    public void setCompletion(Runnable completion) {
        this.completion = completion == null ? () -> { } : completion;
    }

    public void setBeforeRestore(java.util.function.Consumer<DestructibleArenaSession> beforeRestore) {
        this.beforeRestore = beforeRestore == null ? session -> { } : beforeRestore;
    }

    private final class BukkitTaskRunner extends BukkitRunnable {
        private final DestructibleArenaSession session;
        private final Iterator<Map.Entry<BlockPosition, StoredBlockState>> pending;

        private BukkitTaskRunner(DestructibleArenaSession session, Iterator<Map.Entry<BlockPosition, StoredBlockState>> pending) {
            this.session = session;
            this.pending = pending;
        }

        @Override
        public void run() {
            resetBudgetForCurrentTick();
            while (pending.hasNext() && blocksRestoredThisTick < blocksPerTick && nanosUsedThisTick < maxNanosPerTick) {
                long started = System.nanoTime();
                Map.Entry<BlockPosition, StoredBlockState> entry = pending.next();
                org.bukkit.block.Block block = entry.getKey().block();
                if (block != null) entry.getValue().restore(block, applyPhysics);
                nanosUsedThisTick += System.nanoTime() - started;
                blocksRestoredThisTick++;
                session.incrementRestoredBlocks();
            }
            if (!pending.hasNext()) {
                cancel();
                ready(session);
            }
        }
    }

    private void resetBudgetForCurrentTick() {
        long currentTick = Bukkit.getCurrentTick();
        if (budgetTick == currentTick) return;
        budgetTick = currentTick;
        blocksRestoredThisTick = 0;
        nanosUsedThisTick = 0L;
    }

    private void ready(DestructibleArenaSession session) {
        session.getOriginalBlocks().clear();
        session.setState(SessionState.READY);
        session.setRestoreTask(null);
        registry.remove(session);
        session.getTrackedEntities().clear();
        completion.run();
    }
}
