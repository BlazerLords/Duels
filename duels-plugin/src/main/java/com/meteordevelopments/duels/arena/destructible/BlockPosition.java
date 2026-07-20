package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

public record BlockPosition(UUID worldId, int x, int y, int z) {
    public static BlockPosition of(Block block) {
        return new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public Block block() {
        World world = Bukkit.getWorld(worldId);
        return world == null ? null : world.getBlockAt(x, y, z);
    }
}
