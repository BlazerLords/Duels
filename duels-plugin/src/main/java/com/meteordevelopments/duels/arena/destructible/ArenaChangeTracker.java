package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.Map;

public final class ArenaChangeTracker {
    private final Map<BlockPosition, StoredBlockState> originalBlocks;

    public ArenaChangeTracker(Map<BlockPosition, StoredBlockState> originalBlocks) {
        this.originalBlocks = originalBlocks;
    }

    public boolean capture(Block block) {
        BlockPosition position = BlockPosition.of(block);
        if (originalBlocks.containsKey(position)) {
            return false;
        }
        originalBlocks.put(position, StoredBlockState.capture(block));
        return true;
    }

    public boolean capture(BlockState state) {
        BlockPosition position = BlockPosition.of(state.getBlock());
        if (originalBlocks.containsKey(position)) return false;
        originalBlocks.put(position, StoredBlockState.capture(state));
        return true;
    }

    public boolean wasChanged(Block block) {
        return originalBlocks.containsKey(BlockPosition.of(block));
    }
}
