package com.meteordevelopments.duels.core.player;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.config.Config;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerInfoManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void failedRestoreRetainsSnapshotForRetry() throws Exception {
        PlayerInfoManager manager = manager();
        Player player = player();
        PlayerInfo info = mock(PlayerInfo.class);
        putSnapshot(manager, player, info);
        doThrow(new IllegalStateException("restore failed")).doNothing().when(info).restore(player);

        assertFalse(manager.restore(player, false, false, false));
        assertSame(info, manager.get(player));

        assertTrue(manager.restore(player, false, false, false));
        assertNull(manager.get(player));
        verify(info, times(2)).restore(player);
    }

    @Test
    void successfulSnapshotCanOnlyBeRestoredOnce() throws Exception {
        PlayerInfoManager manager = manager();
        Player player = player();
        PlayerInfo info = mock(PlayerInfo.class);
        putSnapshot(manager, player, info);

        assertTrue(manager.restore(player, false, false, false));
        assertFalse(manager.restore(player, false, false, false));

        verify(info, times(1)).restore(player);
    }

    @Test
    void existingRecoverySnapshotCannotBeOverwrittenByMatchPreparation() throws Exception {
        PlayerInfoManager manager = manager();
        Player player = player();
        PlayerInfo original = mock(PlayerInfo.class);
        putSnapshot(manager, player, original);

        assertFalse(manager.createIfAbsent(player, false, true));
        assertSame(original, manager.get(player));
        verify(player, never()).getInventory();
    }

    @Test
    void concurrentRestoreAttemptIsRejectedWhileFirstOwnsSnapshot() throws Exception {
        PlayerInfoManager manager = manager();
        Player player = player();
        PlayerInfo info = mock(PlayerInfo.class);
        putSnapshot(manager, player, info);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(info).restore(player);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> first = executor.submit(() -> manager.restore(player, false, false, false));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(manager.restore(player, false, false, false));
            release.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        verify(info, times(1)).restore(player);
    }

    private PlayerInfoManager manager() {
        DuelsPlugin plugin = mock(DuelsPlugin.class);
        when(plugin.getConfiguration()).thenReturn(mock(Config.class));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        return new PlayerInfoManager(plugin);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("TestPlayer");
        return player;
    }

    @SuppressWarnings("unchecked")
    private void putSnapshot(PlayerInfoManager manager, Player player, PlayerInfo info) throws Exception {
        Field cacheField = PlayerInfoManager.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        ((Map<UUID, PlayerInfo>) cacheField.get(manager)).put(player.getUniqueId(), info);
    }
}
