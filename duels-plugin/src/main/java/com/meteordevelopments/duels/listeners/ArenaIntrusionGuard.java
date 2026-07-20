package com.meteordevelopments.duels.listeners;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.Permissions;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.core.arena.ArenaManagerImpl;
import com.meteordevelopments.duels.core.spectate.SpectateManagerImpl;
import com.meteordevelopments.duels.util.Log;
import com.meteordevelopments.duels.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

// AI generated
public class ArenaIntrusionGuard implements Runnable {

    private static final long INTERVAL_TICKS = 60L;
    private static final String MSG_PLAYER = "&c[Duels] Вы были перемещены за пределы активной арены.";

    private final ArenaManagerImpl arenaManager;
    private final SpectateManagerImpl spectateManager;

    public ArenaIntrusionGuard(DuelsPlugin plugin) {
        this.arenaManager = plugin.getArenaManager();
        this.spectateManager = plugin.getSpectateManager();
        plugin.doSyncRepeat(this, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    @Override
    public void run() {
        List<ArenaImpl> active = buildActiveList();
        if (active.isEmpty())
            return;

        for (Player player: Bukkit.getOnlinePlayers()) {
            if (isExempt(player, active))
                continue;
            Location loc = player.getLocation();
            for (ArenaImpl arena: active) {
                if (arena.isInBounds(loc)) {
                    eject(player, arena, loc);
                    break;
                }
            }
        }
    }

    private List<ArenaImpl> buildActiveList() {
        List<ArenaImpl> result = new ArrayList<>();
        for (ArenaImpl arena: arenaManager.getArenasImpl()) {
            if (arena.isUsed() && arena.hasBounds())
                result.add(arena);
        }
        return result;
    }

    private boolean isExempt(Player player, List<ArenaImpl> active) {
        return player.hasPermission(Permissions.ADMIN)
                || player.isOp()
                || active.stream().anyMatch(arena -> arena.hasParticipant(player))
                || spectateManager.isSpectating(player);
    }

    private void eject(Player player, ArenaImpl arena, Location from) {
        player.teleport(player.getWorld().getSpawnLocation());
        player.sendMessage(StringUtil.color(MSG_PLAYER));
        Log.warn(String.format("[ArenaGuard] Ejected %s from arena '%s' at [%d,%d,%d]",
                player.getName(), arena.getName(),
                from.getBlockX(), from.getBlockY(), from.getBlockZ()));
    }
}
