package com.meteordevelopments.duels.arena.destructible;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ArenaProtectionService {
    private final DestructibleArenaSessionRegistry registry;

    public ArenaProtectionService(DestructibleArenaSessionRegistry registry) {
        this.registry = registry;
    }

    public Decision canPlace(Player player, Block block, Material material) {
        DestructibleArenaSession session = registry.byPlayer(player);
        if (!activeAt(session, block)) return Decision.deny(null, "&cВы не можете изменять эту арену.");
        DestructibleArenaConfig config = session.getConfig();
        if (!config.isAllowBlockPlace()) return Decision.deny(session, "&cУстановка блоков для этого кита выключена.");
        if (!config.getAllowedPlaceMaterials().contains(material)) return Decision.deny(session, "&cЭтот блок нельзя устанавливать на арене.");
        if (material == Material.COBWEB && !config.isAllowCobwebs()) return Decision.deny(session, "&cПаутина для этого кита выключена.");
        if (isRail(material) && !config.isAllowRails()) return Decision.deny(session, "&cРельсы для этого кита выключены.");
        if (material == Material.RESPAWN_ANCHOR && !config.isAllowRespawnAnchors()) {
            return Decision.deny(session, "&cЯкоря для этого кита выключены.");
        }
        return Decision.allow(session);
    }

    public Decision canBreak(Player player, Block block) {
        DestructibleArenaSession session = registry.byPlayer(player);
        if (!activeAt(session, block)) return Decision.deny(null, "&cВы не можете изменять эту арену.");
        DestructibleArenaConfig config = session.getConfig();
        if (!config.isAllowBlockBreak()) return Decision.deny(session, "&cЛомание блоков для этого кита выключено.");
        return Decision.allow(session);
    }

    private boolean activeAt(DestructibleArenaSession session, Block block) {
        return session != null && session.isActive() && session.getBounds().contains(block);
    }

    public static boolean isRail(Material material) {
        return material == Material.RAIL || material == Material.POWERED_RAIL
                || material == Material.ACTIVATOR_RAIL || material == Material.DETECTOR_RAIL;
    }

    public record Decision(boolean allowed, DestructibleArenaSession session, String message) {
        public static Decision allow(DestructibleArenaSession session) { return new Decision(true, session, null); }
        public static Decision deny(DestructibleArenaSession session, String message) { return new Decision(false, session, message); }
    }
}
