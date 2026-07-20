package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DestructibleArenaConfigTest {
    @Test
    void newKitConfigurationIsDisabled() {
        assertFalse(new DestructibleArenaConfig().isEnabled());
    }

    @Test
    void disabledMasterSwitchOverridesEveryDestructiveSubFeature() {
        DestructibleArenaConfig config = new DestructibleArenaConfig();
        config.setAllowBlockPlace(true);
        config.setAllowBlockBreak(true);
        config.setAllowExplosions(true);
        config.setAllowEndCrystals(true);
        config.setAllowRespawnAnchors(true);
        config.setAllowTntMinecarts(true);
        config.setAllowTnt(true);
        config.setAllowBedExplosions(true);
        config.setAllowCobwebs(true);
        config.setAllowRails(true);
        config.setAllowLiquids(true);
        config.setAllowPistons(true);
        config.setAllowFire(true);
        config.setSuppressBlockDrops(false);
        config.setSuppressEntityDrops(false);

        assertAll(
                () -> assertFalse(config.isAllowBlockPlace()),
                () -> assertFalse(config.isAllowBlockBreak()),
                () -> assertFalse(config.isAllowExplosions()),
                () -> assertFalse(config.isAllowEndCrystals()),
                () -> assertFalse(config.isAllowRespawnAnchors()),
                () -> assertFalse(config.isAllowTntMinecarts()),
                () -> assertFalse(config.isAllowTnt()),
                () -> assertFalse(config.isAllowBedExplosions()),
                () -> assertFalse(config.isAllowCobwebs()),
                () -> assertFalse(config.isAllowRails()),
                () -> assertFalse(config.isAllowLiquids()),
                () -> assertFalse(config.isAllowPistons()),
                () -> assertFalse(config.isAllowFire()),
                () -> assertTrue(config.isSuppressBlockDrops()),
                () -> assertTrue(config.isSuppressEntityDrops()));
    }

    @Test
    void copyDoesNotShareMaterialSets() {
        DestructibleArenaConfig source = new DestructibleArenaConfig();
        DestructibleArenaConfig copy = source.copy();
        copy.getAllowedPlaceMaterials().add(Material.STONE);
        assertFalse(source.getAllowedPlaceMaterials().contains(Material.STONE));
    }

    @Test
    void sessionCachesConfiguration() {
        DestructibleArenaConfig source = new DestructibleArenaConfig();
        source.setEnabled(true);
        DestructibleArenaSession session = new DestructibleArenaSession("m", "a", "k", MatchType.TOURNAMENT,
                new ArenaBounds(java.util.UUID.randomUUID(), 0, 0, 0, 1, 1, 1), source);
        source.setAllowExplosions(false);
        assertTrue(session.getConfig().isAllowExplosions());
        assertEquals(MatchType.TOURNAMENT, session.getMatchType());
    }

    @Test
    void recoveredStateReturnsDefensiveCopiesOfMutableData() {
        org.bukkit.inventory.ItemStack[] inventory = {new org.bukkit.inventory.ItemStack(Material.STONE)};
        String[] lines = {"original"};
        StoredBlockState state = StoredBlockState.recovered(Material.CHEST, "minecraft:chest", inventory, lines);

        inventory[0].setAmount(32);
        lines[0] = "changed";

        assertEquals(1, state.inventory()[0].getAmount());
        assertEquals("original", state.signLines()[0]);
        state.inventory()[0].setAmount(16);
        state.signLines()[0] = "again";
        assertEquals(1, state.inventory()[0].getAmount());
        assertEquals("original", state.signLines()[0]);
    }
}
