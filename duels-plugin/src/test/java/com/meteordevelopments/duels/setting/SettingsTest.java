package com.meteordevelopments.duels.setting;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.config.Config;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.core.kit.KitImpl;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettingsTest {
    @Test
    void ownInventoryDefaultsToInverseOfKitSelection() {
        assertFalse(settings(true).isOwnInventory());
        assertTrue(settings(false).isOwnInventory());
    }

    @Test
    void selectingKitAndOwnInventoryAreMutuallyExclusive() {
        Settings settings = settings(true);
        KitImpl kit = mock(KitImpl.class);

        settings.setKit(kit);
        assertSame(kit, settings.getKit());
        assertFalse(settings.isOwnInventory());

        settings.setOwnInventory(true);
        assertNull(settings.getKit());
        assertTrue(settings.isOwnInventory());
    }

    @Test
    void changingTargetResetsPreviousDuelOptions() {
        Settings settings = settings(true);
        Player first = player();
        Player second = player();
        settings.setTarget(first);
        settings.setArena(mock(ArenaImpl.class));
        settings.setBet(50);

        settings.setTarget(second);

        assertEquals(second.getUniqueId(), settings.getTarget());
        assertNull(settings.getArena());
        assertEquals(0, settings.getBet());
    }

    @Test
    void lightCopyHasIndependentCacheMap() {
        Settings settings = settings(true);
        Player player = player();
        Location location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);
        when(location.clone()).thenReturn(location);
        settings.setBaseLoc(player);

        Settings copy = settings.lightCopy();
        copy.clearCache();

        assertNotNull(settings.getBaseLoc(player));
        assertNull(copy.getBaseLoc(player));
    }

    private Settings settings(boolean kitSelectionEnabled) {
        DuelsPlugin plugin = mock(DuelsPlugin.class);
        Config config = mock(Config.class);
        when(plugin.getConfiguration()).thenReturn(config);
        when(config.isKitSelectingEnabled()).thenReturn(kitSelectionEnabled);
        return new Settings(plugin);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
