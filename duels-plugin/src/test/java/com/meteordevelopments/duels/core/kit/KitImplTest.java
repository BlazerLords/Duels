package com.meteordevelopments.duels.core.kit;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.api.event.kit.KitEquipEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class KitImplTest {
    @Test
    void cancelledEquipEventDoesNotChangeInventory() {
        DuelsPlugin plugin = mock(DuelsPlugin.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(player.getInventory()).thenReturn(inventory);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            doAnswer(invocation -> {
                ((KitEquipEvent) invocation.getArgument(0)).setCancelled(true);
                return null;
            }).when(pluginManager).callEvent(any(KitEquipEvent.class));

            KitImpl kit = new KitImpl(plugin, "test", mock(ItemStack.class), false, false, new HashSet<>());

            assertFalse(kit.equip(player));
        }

        verify(inventory, never()).setItem(anyInt(), any());
        verify(player, never()).updateInventory();
    }
}
