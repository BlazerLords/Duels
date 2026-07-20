package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArenaBoundsTest {
    private final UUID worldId = UUID.randomUUID();

    @Test
    void normalizesCorners() {
        ArenaBounds bounds = new ArenaBounds(worldId, 10, 20, 30, -10, 5, -30);
        assertEquals(-10, bounds.minX());
        assertEquals(20, bounds.maxY());
        assertEquals(-30, bounds.minZ());
    }

    @Test
    void containsInclusiveBlockCoordinates() {
        ArenaBounds bounds = new ArenaBounds(worldId, 0, 0, 0, 10, 10, 10);
        assertTrue(bounds.contains(0, 0, 0));
        assertTrue(bounds.contains(10, 10, 10));
        assertFalse(bounds.contains(11, 10, 10));
    }

    @Test
    void rejectsOtherWorld() {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        assertFalse(new ArenaBounds(worldId, 0, 0, 0, 1, 1, 1).contains(new Location(world, 0, 0, 0)));
    }

    @Test
    void detectsBoundingBoxIntersection() {
        ArenaBounds bounds = new ArenaBounds(worldId, 0, 0, 0, 10, 10, 10);
        assertTrue(bounds.intersects(new BoundingBox(9, 9, 9, 12, 12, 12)));
        assertFalse(bounds.intersects(new BoundingBox(11.1, 0, 0, 12, 1, 1)));
    }
}
