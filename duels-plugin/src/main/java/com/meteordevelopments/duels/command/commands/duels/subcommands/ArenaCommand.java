package com.meteordevelopments.duels.command.commands.duels.subcommands;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.arena.destructible.ArenaBounds;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaSession;
import com.meteordevelopments.duels.command.BaseCommand;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.util.StringUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

public final class ArenaCommand extends BaseCommand {
    public ArenaCommand(DuelsPlugin plugin) {
        super(plugin, "arena", "arena <restore|restorestatus|debug> <arena>", "Управление разрушаемой ареной.", 3, false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String action = args[1].toLowerCase(Locale.ROOT);
        String arenaName = StringUtil.join(args, " ", 2, args.length).replace("-", " ");
        ArenaImpl arena = arenaManager.get(arenaName);
        if (arena == null) {
            lang.sendMessage(sender, "ERROR.arena.not-found", "name", arenaName);
            return;
        }
        DestructibleArenaSession session = plugin.getDestructibleArenaService().getRegistry().byArena(arena.getName());
        switch (action) {
            case "restore" -> {
                if (session == null || !plugin.getDestructibleArenaService().getRestore().restore(session)) {
                    sender.sendMessage(StringUtil.color("&eУ арены нет сессии, которую можно восстановить."));
                } else {
                    sender.sendMessage(StringUtil.color("&aВосстановление арены &e" + arena.getName() + " &aзапущено."));
                }
            }
            case "restorestatus" -> sendStatus(sender, arena, session);
            case "debug" -> sendDebug(sender, arena, session);
            default -> sender.sendMessage(StringUtil.color("&cИспользование: /" + label + " arena <restore|restorestatus|debug> <арена>"));
        }
    }

    private void sendStatus(CommandSender sender, ArenaImpl arena, DestructibleArenaSession session) {
        sender.sendMessage(StringUtil.color("&8&m                                                "));
        sender.sendMessage(StringUtil.color("&6&lСтатус арены: &e" + arena.getName()));
        if (session == null) {
            sender.sendMessage(StringUtil.color("&7Состояние: &aготова"));
            sender.sendMessage(StringUtil.color("&8&m                                                "));
            return;
        }
        int total = session.getOriginalBlocks().size();
        sender.sendMessage(StringUtil.color("&7Состояние: &f" + session.getState()));
        sender.sendMessage(StringUtil.color("&7Осталось восстановить: &f" + Math.max(0, total - session.getRestoredBlocks())));
        sender.sendMessage(StringUtil.color("&7Временных сущностей: &f" + session.getTrackedEntities().size()));
        sender.sendMessage(StringUtil.color("&7Восстановлено: &f" + session.getRestoredBlocks() + "/" + total));
        sender.sendMessage(StringUtil.color("&8&m                                                "));
    }

    private void sendDebug(CommandSender sender, ArenaImpl arena, DestructibleArenaSession session) {
        ArenaBounds bounds = arena.getArenaBounds();
        sender.sendMessage(StringUtil.color("&8&m                                                "));
        sender.sendMessage(StringUtil.color("&6&lРазрушаемая арена: &e" + arena.getName()));
        sender.sendMessage(StringUtil.color("&7Границы: &f" + (bounds == null ? "не установлены" :
                bounds.worldId() + " [" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ() + "] -> ["
                        + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ() + "]")));
        sender.sendMessage(StringUtil.color("&7Активный матч: &f" + (session == null ? "нет" : session.getMatchId())));
        sender.sendMessage(StringUtil.color("&7Кит: &f" + (session == null ? "нет" : session.getKitId())));
        sender.sendMessage(StringUtil.color("&7Разрушение: " + (session != null && session.getConfig().isEnabled() ? "&aвключено" : "&cвыключено")));
        if (session != null) {
            sender.sendMessage(StringUtil.color("&7Состояние: &f" + session.getState()));
            sender.sendMessage(StringUtil.color("&7Изменённых блоков: &f" + session.getOriginalBlocks().size()));
            sender.sendMessage(StringUtil.color("&7Временных сущностей: &f" + session.getTrackedEntities().size()));
        }
        sender.sendMessage(StringUtil.color("&8&m                                                "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2) return handleTabCompletion(args[1], List.of("restore", "restorestatus", "debug"));
        if (args.length == 3) return handleTabCompletion(args[2], arenaManager.getNames());
        return null;
    }
}
