package com.meteordevelopments.duels.hook.hooks.worldguard;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.config.Config;
import com.meteordevelopments.duels.util.hook.PluginHook;
import com.meteordevelopments.duels.util.reflect.ReflectionUtil;
import org.bukkit.entity.Player;
import org.bukkit.World;

import java.util.Collection;
import java.util.UUID;

public class WorldGuardHook extends PluginHook<DuelsPlugin> {

    public static final String NAME = "WorldGuard";

    private final Config config;
    private final WorldGuardHandler handler;

    public WorldGuardHook(final DuelsPlugin plugin) {
        super(plugin, NAME);
        this.config = plugin.getConfiguration();
        this.handler = new WorldGuard7Handler();
    }

    public String findDuelZone(final Player player) {
        if (!config.isDuelzoneEnabled()) {
            return null;
        }

        final Collection<String> allowedRegions = config.getDuelzones();

        if (allowedRegions.isEmpty()) {
            return null;
        }

        return handler.findRegion(player, allowedRegions);
    }

    public WorldGuardHandler.BypassState enableBypass(final Player player, final World world) {
        return handler.enableBypass(player, world);
    }

    public void restoreBypass(final UUID playerId, final UUID worldId, final WorldGuardHandler.BypassState state) {
        handler.restoreBypass(playerId, worldId, state);
    }
}
