package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

public class DestructibleArenaConfig {
    private boolean enabled;
    private boolean allowBlockPlace = true;
    private boolean allowBlockBreak = true;
    private boolean allowExplosions = true;
    private boolean allowEndCrystals = true;
    private boolean allowRespawnAnchors = true;
    private boolean allowTntMinecarts = true;
    private boolean allowTnt;
    private boolean allowBedExplosions;
    private boolean allowCobwebs = true;
    private boolean allowRails = true;
    private boolean allowLiquids;
    private boolean allowPistons;
    private boolean allowFire;
    private boolean suppressBlockDrops = true;
    private boolean suppressEntityDrops = true;
    private boolean cleanupEntities = true;
    private boolean restoreAfterMatch = true;
    private Set<Material> allowedPlaceMaterials = defaults();
    private Set<Material> allowedBreakMaterials = defaults();

    public DestructibleArenaConfig copy() {
        DestructibleArenaConfig copy = new DestructibleArenaConfig();
        copy.enabled = enabled;
        copy.allowBlockPlace = allowBlockPlace;
        copy.allowBlockBreak = allowBlockBreak;
        copy.allowExplosions = allowExplosions;
        copy.allowEndCrystals = allowEndCrystals;
        copy.allowRespawnAnchors = allowRespawnAnchors;
        copy.allowTntMinecarts = allowTntMinecarts;
        copy.allowTnt = allowTnt;
        copy.allowBedExplosions = allowBedExplosions;
        copy.allowCobwebs = allowCobwebs;
        copy.allowRails = allowRails;
        copy.allowLiquids = allowLiquids;
        copy.allowPistons = allowPistons;
        copy.allowFire = allowFire;
        copy.suppressBlockDrops = suppressBlockDrops;
        copy.suppressEntityDrops = suppressEntityDrops;
        copy.cleanupEntities = cleanupEntities;
        copy.restoreAfterMatch = restoreAfterMatch;
        copy.allowedPlaceMaterials = copyOf(allowedPlaceMaterials);
        copy.allowedBreakMaterials = copyOf(allowedBreakMaterials);
        return copy;
    }

    private static Set<Material> defaults() {
        return EnumSet.of(Material.OBSIDIAN, Material.COBWEB, Material.RAIL, Material.POWERED_RAIL,
                Material.ACTIVATOR_RAIL, Material.DETECTOR_RAIL, Material.RESPAWN_ANCHOR);
    }

    private static Set<Material> copyOf(Set<Material> materials) {
        return materials == null || materials.isEmpty() ? EnumSet.noneOf(Material.class) : EnumSet.copyOf(materials);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isAllowBlockPlace() { return allowBlockPlace; }
    public void setAllowBlockPlace(boolean value) { allowBlockPlace = value; }
    public boolean isAllowBlockBreak() { return allowBlockBreak; }
    public void setAllowBlockBreak(boolean value) { allowBlockBreak = value; }
    public boolean isAllowExplosions() { return allowExplosions; }
    public void setAllowExplosions(boolean value) { allowExplosions = value; }
    public boolean isAllowEndCrystals() { return allowEndCrystals; }
    public void setAllowEndCrystals(boolean value) { allowEndCrystals = value; }
    public boolean isAllowRespawnAnchors() { return allowRespawnAnchors; }
    public void setAllowRespawnAnchors(boolean value) { allowRespawnAnchors = value; }
    public boolean isAllowTntMinecarts() { return allowTntMinecarts; }
    public void setAllowTntMinecarts(boolean value) { allowTntMinecarts = value; }
    public boolean isAllowTnt() { return allowTnt; }
    public void setAllowTnt(boolean value) { allowTnt = value; }
    public boolean isAllowBedExplosions() { return allowBedExplosions; }
    public void setAllowBedExplosions(boolean value) { allowBedExplosions = value; }
    public boolean isAllowCobwebs() { return allowCobwebs; }
    public void setAllowCobwebs(boolean value) { allowCobwebs = value; }
    public boolean isAllowRails() { return allowRails; }
    public void setAllowRails(boolean value) { allowRails = value; }
    public boolean isAllowLiquids() { return allowLiquids; }
    public void setAllowLiquids(boolean value) { allowLiquids = value; }
    public boolean isAllowPistons() { return allowPistons; }
    public void setAllowPistons(boolean value) { allowPistons = value; }
    public boolean isAllowFire() { return allowFire; }
    public void setAllowFire(boolean value) { allowFire = value; }
    public boolean isSuppressBlockDrops() { return suppressBlockDrops; }
    public void setSuppressBlockDrops(boolean value) { suppressBlockDrops = value; }
    public boolean isSuppressEntityDrops() { return suppressEntityDrops; }
    public void setSuppressEntityDrops(boolean value) { suppressEntityDrops = value; }
    public boolean isCleanupEntities() { return cleanupEntities; }
    public void setCleanupEntities(boolean value) { cleanupEntities = value; }
    public boolean isRestoreAfterMatch() { return restoreAfterMatch; }
    public void setRestoreAfterMatch(boolean value) { restoreAfterMatch = value; }
    public Set<Material> getAllowedPlaceMaterials() { return allowedPlaceMaterials; }
    public void setAllowedPlaceMaterials(Set<Material> value) { allowedPlaceMaterials = copyOf(value); }
    public Set<Material> getAllowedBreakMaterials() { return allowedBreakMaterials; }
    public void setAllowedBreakMaterials(Set<Material> value) { allowedBreakMaterials = copyOf(value); }
}
