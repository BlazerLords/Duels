package com.meteordevelopments.duels.hook.hooks.worldguard;

import java.util.Collection;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.entity.Player;

public interface WorldGuardHandler {

    String findRegion(final Player player, final Collection<String> regions);

    default BypassState enableBypass(final Player player, final World world) {
        return BypassState.unsupported();
    }

    default void restoreBypass(final UUID playerId, final UUID worldId, final BypassState state) {
    }

    record BypassState(boolean supported, boolean previousBypass, Object handle) {
        public static BypassState unsupported() {
            return new BypassState(false, false, null);
        }
    }
}
