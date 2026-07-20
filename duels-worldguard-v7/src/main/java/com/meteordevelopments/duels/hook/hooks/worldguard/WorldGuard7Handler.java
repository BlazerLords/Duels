package com.meteordevelopments.duels.hook.hooks.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.SessionManager;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

public class WorldGuard7Handler implements WorldGuardHandler {

    @Override
    public String findRegion(final Player player, final Collection<String> regions) {
        final Location location = player.getLocation();
        final BlockVector3 vector = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());

        for (final ProtectedRegion region : Objects.requireNonNull(WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(player.getWorld())))
                .getApplicableRegions(vector)) {
            if (regions.contains(region.getId())) {
                return region.getId();
            }
        }

        return null;
    }

    @Override
    public BypassState enableBypass(final Player player, final World world) {
        if (player == null || world == null) {
            return BypassState.unsupported();
        }

        final SessionManager sessionManager = WorldGuard.getInstance().getPlatform().getSessionManager();
        final LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        final Session session = sessionManager.get(localPlayer);
        final String permission = "worldguard.region.bypass." + world.getName();

        // Do not call SessionManager#hasBypass before granting the permission. WorldGuard caches
        // negative bypass checks, which can make it deny the first hits (and send its PvP warning)
        // even though Duels has already started the match.
        final boolean previousBypassDisabled = session.hasBypassDisabled();
        final boolean previousPermission = player.hasPermission(permission);
        final boolean previous = previousPermission && !previousBypassDisabled;
        final PermissionAttachment attachment = previousPermission
                ? null
                : player.addAttachment(WorldGuardPlugin.inst(), permission, true);
        if (previousBypassDisabled) {
            session.setBypassDisabled(false);
        }
        player.recalculatePermissions();
        sessionManager.resetState(localPlayer);
        return new BypassState(true, previous, new BypassHandle(attachment, previousBypassDisabled));
    }

    @Override
    public void restoreBypass(final UUID playerId, final UUID worldId, final BypassState state) {
        if (playerId == null || worldId == null || state == null || !state.supported() || state.previousBypass()) {
            return;
        }

        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        if (state.handle() instanceof BypassHandle handle) {
            if (handle.attachment() != null) {
                player.removeAttachment(handle.attachment());
            }
            player.recalculatePermissions();
            final SessionManager sessionManager = WorldGuard.getInstance().getPlatform().getSessionManager();
            final LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            if (handle.previousBypassDisabled()) {
                sessionManager.get(localPlayer).setBypassDisabled(true);
            }
            sessionManager.resetState(localPlayer);
        }
    }

    private record BypassHandle(PermissionAttachment attachment, boolean previousBypassDisabled) {
    }
}
