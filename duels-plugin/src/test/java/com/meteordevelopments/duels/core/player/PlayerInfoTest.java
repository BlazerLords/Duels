package com.meteordevelopments.duels.core.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.meteordevelopments.duels.util.PlayerUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class PlayerInfoTest {
    @Test
    void restoreReappliesHealthExperienceHungerAndInventoryUpdate() {
        Location location = mock(Location.class);
        PlayerInfo info = new PlayerInfo(List.of(), 18.0, 0.75f, 12, 16, location, true);
        Player player = player();

        try (MockedStatic<PlayerUtil> playerUtil = mockStatic(PlayerUtil.class)) {
            playerUtil.when(() -> PlayerUtil.getMaxHealth(player)).thenReturn(10.0);
            info.restore(player);
        }

        verify(player).setHealth(10.0);
        verify(player).setExp(0.75f);
        verify(player).setLevel(12);
        verify(player).setFoodLevel(16);
        verify(player).updateInventory();
        assertSame(location, info.getLocation());
    }

    @Test
    void restoreWithoutExperienceLeavesCurrentExperienceUntouched() {
        PlayerInfo info = new PlayerInfo(List.of(), 18.0, 0.75f, 12, 16, mock(Location.class), true);
        Player player = player();

        try (MockedStatic<PlayerUtil> playerUtil = mockStatic(PlayerUtil.class)) {
            playerUtil.when(() -> PlayerUtil.getMaxHealth(player)).thenReturn(20.0);
            info.restoreWithoutExperience(player);
        }

        verify(player, never()).setExp(anyFloat());
        verify(player, never()).setLevel(anyInt());
        verify(player).setHealth(18.0);
        verify(player).setFoodLevel(16);
    }

    @Test
    void snapshotClonesInventoryItemsBeforeRestoringThem() {
        Player player = player();
        PlayerInventory inventory = player.getInventory();
        ItemStack original = mock(ItemStack.class);
        ItemStack snapshot = mock(ItemStack.class);
        ItemStack restored = mock(ItemStack.class);
        Location location = mock(Location.class);

        when(player.getLocation()).thenReturn(location);
        when(location.clone()).thenReturn(location);
        when(inventory.getSize()).thenReturn(1);
        when(inventory.getItem(0)).thenReturn(original);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
        when(original.getType()).thenReturn(Material.DIAMOND);
        when(original.clone()).thenReturn(snapshot);
        when(snapshot.clone()).thenReturn(restored);

        PlayerInfo info = new PlayerInfo(player, false);
        reset(inventory);

        try (MockedStatic<PlayerUtil> playerUtil = mockStatic(PlayerUtil.class)) {
            playerUtil.when(() -> PlayerUtil.getMaxHealth(player)).thenReturn(20.0);
            info.restore(player);
        }

        verify(inventory).setItem(0, restored);
        verify(inventory, never()).setItem(0, original);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        return player;
    }
}
