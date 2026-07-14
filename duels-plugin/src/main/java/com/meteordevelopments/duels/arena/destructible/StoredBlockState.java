package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class StoredBlockState {
    private static final ConcurrentMap<String, String> BLOCK_DATA_CACHE = new ConcurrentHashMap<>();
    private final Material material;
    private final String blockData;
    private final BlockState snapshot;
    private final ItemStack[] inventory;
    private final String[] signLines;

    private StoredBlockState(Material material, String blockData, BlockState snapshot, ItemStack[] inventory, String[] signLines) {
        this.material = material;
        this.blockData = blockData;
        this.snapshot = snapshot;
        this.inventory = inventory;
        this.signLines = signLines;
    }

    public static StoredBlockState capture(Block block) {
        return capture(block.getState());
    }

    public static StoredBlockState capture(BlockState state) {
        ItemStack[] inventory = state instanceof Container container ? cloneItems(container.getInventory().getContents()) : null;
        String[] lines = state instanceof Sign sign ? sign.getLines().clone() : null;
        BlockState snapshot = state instanceof TileState ? state : null;
        return new StoredBlockState(state.getType(), canonical(state.getBlockData().getAsString()), snapshot, inventory, lines);
    }

    public static StoredBlockState recovered(Material material, String blockData) {
        return new StoredBlockState(material, canonical(blockData), null, null, null);
    }

    public static StoredBlockState recovered(Material material, String blockData, ItemStack[] inventory, String[] signLines) {
        return new StoredBlockState(material, canonical(blockData), null, cloneItems(inventory), signLines == null ? null : signLines.clone());
    }

    public Material material() {
        return material;
    }

    public String blockData() {
        return blockData;
    }

    public boolean wasAir() {
        return material.isAir();
    }

    public ItemStack[] inventory() { return inventory == null ? null : cloneItems(inventory); }
    public String[] signLines() { return signLines == null ? null : signLines.clone(); }

    public void restore(Block block, boolean applyPhysics) {
        if (snapshot != null) {
            snapshot.update(true, applyPhysics);
            return;
        }
        BlockData data;
        try {
            data = Bukkit.createBlockData(blockData);
        } catch (IllegalArgumentException ex) {
            data = material.createBlockData();
        }
        block.setBlockData(data, applyPhysics);
        BlockState state = block.getState();
        if (inventory != null && state instanceof Container container) {
            container.getInventory().setContents(cloneItems(inventory));
        }
        if (signLines != null && state instanceof Sign sign) {
            for (int i = 0; i < Math.min(signLines.length, sign.getLines().length); i++) {
                sign.setLine(i, signLines[i]);
            }
        }
        state.update(true, applyPhysics);
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) return null;
        return Arrays.stream(source).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }

    private static String canonical(String blockData) {
        return BLOCK_DATA_CACHE.computeIfAbsent(blockData, value -> value);
    }
}
