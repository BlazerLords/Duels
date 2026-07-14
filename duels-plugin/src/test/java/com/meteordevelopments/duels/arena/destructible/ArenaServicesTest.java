package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArenaServicesTest {
    private UUID worldId;
    private World world;
    private Player player;
    private DestructibleArenaSession session;
    private DestructibleArenaSessionRegistry registry;
    private ArenaProtectionService protection;

    @BeforeEach
    void setUp() {
        worldId = UUID.randomUUID();
        world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        DestructibleArenaConfig config = new DestructibleArenaConfig();
        config.setEnabled(true);
        session = new DestructibleArenaSession("match", "arena", "kit", MatchType.DUEL,
                new ArenaBounds(worldId, 0, 0, 0, 10, 10, 10), config);
        session.getPlayers().add(player.getUniqueId());
        session.setState(SessionState.ACTIVE);
        registry = new DestructibleArenaSessionRegistry();
        assertTrue(registry.register(session));
        protection = new ArenaProtectionService(registry);
    }

    @Test
    void placementInsideBoundsIsAllowed() {
        assertTrue(protection.canPlace(player, block(5, 5, 5, Material.OBSIDIAN), Material.OBSIDIAN).allowed());
    }

    @Test
    void placementOutsideBoundsIsDenied() {
        assertFalse(protection.canPlace(player, block(11, 5, 5, Material.OBSIDIAN), Material.OBSIDIAN).allowed());
    }

    @Test
    void placementOfUnconfiguredMaterialIsDenied() {
        assertFalse(protection.canPlace(player, block(5, 5, 5, Material.STONE), Material.STONE).allowed());
    }

    @Test
    void everyArenaBlockInsideBoundsCanBeBroken() {
        assertTrue(protection.canBreak(player, block(5, 5, 5, Material.STONE)).allowed());
    }

    @Test
    void breakOutsideBoundsIsDenied() {
        assertFalse(protection.canBreak(player, block(-1, 5, 5, Material.OBSIDIAN)).allowed());
    }

    @Test
    void changedBlockCanBeBrokenRegardlessOfOriginalMaterial() {
        Block stone = block(5, 5, 5, Material.STONE);
        session.getOriginalBlocks().put(BlockPosition.of(stone), StoredBlockState.recovered(Material.AIR, "minecraft:air"));
        assertTrue(protection.canBreak(player, stone).allowed());
    }

    @Test
    void explosionFiltersOutsideAndKeepsAllowedInside() {
        Block inside = capturableBlock(5, 5, 5, Material.OBSIDIAN);
        Block outside = block(20, 5, 5, Material.OBSIDIAN);
        List<Block> blocks = new ArrayList<>(List.of(inside, outside));
        new ArenaExplosionService(registry).filterAndCapture(session, blocks);
        assertEquals(List.of(inside), blocks);
        assertEquals(1, session.getOriginalBlocks().size());
    }

    @Test
    void explosionKeepsAndCapturesEveryBlockInsideBounds() {
        Block stone = capturableBlock(5, 5, 5, Material.STONE);
        List<Block> blocks = new ArrayList<>(List.of(stone));
        new ArenaExplosionService(registry).filterAndCapture(session, blocks);
        assertEquals(List.of(stone), blocks);
        assertEquals(1, session.getOriginalBlocks().size());
    }

    @Test
    void firstBlockStateIsCapturedOnlyOnce() {
        Block block = capturableBlock(5, 5, 5, Material.OBSIDIAN);
        ArenaChangeTracker tracker = new ArenaChangeTracker(session.getOriginalBlocks());
        assertTrue(tracker.capture(block));
        assertFalse(tracker.capture(block));
        assertEquals(1, session.getOriginalBlocks().size());
    }

    @Test
    void secondSessionCannotUseSameArena() {
        DestructibleArenaSession other = new DestructibleArenaSession("other", "arena", "kit", MatchType.TOURNAMENT,
                session.getBounds(), session.getConfig());
        other.setState(SessionState.ACTIVE);
        assertFalse(registry.register(other));
    }

    @Test
    void restoringArenaRejectsNewSession() {
        session.setState(SessionState.RESTORING);
        DestructibleArenaSession other = new DestructibleArenaSession("other", "arena", "kit", MatchType.DUEL,
                session.getBounds(), session.getConfig());
        assertFalse(registry.register(other));
    }

    @Test
    void registryIndexesPlayerMatchWorldAndArena() {
        assertSame(session, registry.byPlayer(player));
        assertSame(session, registry.byMatch("match"));
        assertSame(session, registry.byArena("ARENA"));
        assertSame(session, registry.at(new Location(world, 3, 3, 3)));
    }

    @Test
    void removingSessionClearsPlayerEntityMatchAndWorldIndexes() {
        Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        registry.indexEntity(session, entity.getUniqueId());

        registry.remove(session);

        assertNull(registry.byPlayer(player));
        assertNull(registry.byEntity(entity));
        assertNull(registry.byMatch("match"));
        assertNull(registry.at(new Location(world, 3, 3, 3)));
        assertSame(session, registry.byArena("arena"));
    }

    @Test
    void inactiveSessionCannotModifyBlocks() {
        session.setState(SessionState.FINISHING);

        assertFalse(protection.canPlace(player, block(5, 5, 5, Material.OBSIDIAN), Material.OBSIDIAN).allowed());
        assertFalse(protection.canBreak(player, block(5, 5, 5, Material.STONE)).allowed());
    }

    private Block block(int x, int y, int z, Material material) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getType()).thenReturn(material);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        return block;
    }

    private Block capturableBlock(int x, int y, int z, Material material) {
        Block block = block(x, y, z, material);
        BlockState state = mock(BlockState.class);
        BlockData data = mock(BlockData.class);
        when(data.getAsString()).thenReturn("minecraft:" + material.name().toLowerCase());
        when(state.getType()).thenReturn(material);
        when(state.getBlockData()).thenReturn(data);
        when(block.getState()).thenReturn(state);
        return block;
    }
}
