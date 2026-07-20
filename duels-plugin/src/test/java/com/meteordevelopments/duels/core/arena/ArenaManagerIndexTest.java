package com.meteordevelopments.duels.core.arena;

import com.meteordevelopments.duels.DuelsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class ArenaManagerIndexTest {
    @Test
    void indexedLookupIsConstantTimeAndDropsStaleEntry() {
        DuelsPlugin plugin = mock(DuelsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(new File("build/test-data"));
        PluginManager pluginManager = mock(PluginManager.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        ArenaImpl arena = mock(ArenaImpl.class);
        when(arena.has(player)).thenReturn(true, false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            ArenaManagerImpl manager = new ArenaManagerImpl(plugin);
            manager.indexPlayer(player, arena);

            assertSame(arena, manager.get(player));
            assertNull(manager.get(player));
        }

        verify(arena, times(2)).has(player);
    }
}
