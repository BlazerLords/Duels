package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

import java.util.Objects;
import java.util.UUID;

public record ArenaBounds(UUID worldId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public ArenaBounds {
        Objects.requireNonNull(worldId, "worldId");
        int lowX = Math.min(minX, maxX);
        int lowY = Math.min(minY, maxY);
        int lowZ = Math.min(minZ, maxZ);
        int highX = Math.max(minX, maxX);
        int highY = Math.max(minY, maxY);
        int highZ = Math.max(minZ, maxZ);
        minX = lowX;
        minY = lowY;
        minZ = lowZ;
        maxX = highX;
        maxY = highY;
        maxZ = highZ;
    }

    public static ArenaBounds of(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().getUID().equals(second.getWorld().getUID())) {
            return null;
        }
        return new ArenaBounds(first.getWorld().getUID(), first.getBlockX(), first.getBlockY(), first.getBlockZ(),
                second.getBlockX(), second.getBlockY(), second.getBlockZ());
    }

    public boolean contains(Location location) {
        return location != null && location.getWorld() != null && worldId.equals(location.getWorld().getUID())
                && contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(Block block) {
        return block != null && worldId.equals(block.getWorld().getUID())
                && contains(block.getX(), block.getY(), block.getZ());
    }

    public boolean contains(Entity entity) {
        return entity != null && contains(entity.getLocation());
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(BoundingBox box) {
        return box != null && box.overlaps(new BoundingBox(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0));
    }
}
