package com.meteordevelopments.duels.command.commands.duels.subcommands;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.command.BaseCommand;
import com.meteordevelopments.duels.util.StringUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CommandsCommand extends BaseCommand {

    private static final List<String> ACTIONS = List.of("list", "add", "remove", "lock", "unlock", "status");

    public CommandsCommand(final DuelsPlugin plugin) {
        super(plugin, "commands", "commands <list|add|remove|lock|unlock|status> [command]",
                "Управляет командами во время дуэлей и турниров.", 2, false, "commandblock", "cmdblock");
    }

    @Override
    protected void execute(final CommandSender sender, final String label, final String[] args) {
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                sender.sendMessage(StringUtil.color("&6Запрещённые команды &7(" + config.getBlacklistedCommands().size() + "):"));
                sender.sendMessage(StringUtil.color(config.getBlacklistedCommands().isEmpty()
                        ? "&7Список пуст."
                        : "&c/" + String.join("&7, &c/", config.getBlacklistedCommands())));
            }
            case "status" -> sender.sendMessage(StringUtil.color("&6Блокировка команд: "
                    + (config.isBlockAllCommands() ? "&cвсе, кроме разрешённых" : "&aтолько чёрный список")));
            case "lock" -> setLockdown(sender, true);
            case "unlock" -> setLockdown(sender, false);
            case "add" -> changeRule(sender, true, args);
            case "remove" -> changeRule(sender, false, args);
            default -> sender.sendMessage(StringUtil.color("&cИспользование: /" + label + " " + getUsage()));
        }
    }

    private void setLockdown(final CommandSender sender, final boolean enabled) {
        if (!config.setCommandLockdown(enabled)) {
            sender.sendMessage(StringUtil.color("&cНе удалось сохранить настройку в config.yml."));
            return;
        }
        sender.sendMessage(StringUtil.color(enabled
                ? "&aВсе команды, кроме разрешённых дуэльных, заблокированы."
                : "&aПолная блокировка выключена; действует чёрный список."));
    }

    private void changeRule(final CommandSender sender, final boolean add, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(StringUtil.color("&cУкажите команду, например: /duels commands add ah sell"));
            return;
        }
        final String rule = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        final boolean success = add ? config.addBlacklistedCommand(rule) : config.removeBlacklistedCommand(rule);
        if (!success) {
            sender.sendMessage(StringUtil.color("&cКоманда уже находится в таком состоянии или config.yml не удалось сохранить."));
            return;
        }
        sender.sendMessage(StringUtil.color((add ? "&aДобавлено: &f/" : "&aУдалено: &f/")
                + rule.replaceFirst("^/+", "")));
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 2) {
            return handleTabCompletion(args[1], ACTIONS);
        }
        if (args.length == 3 && "remove".equalsIgnoreCase(args[1])) {
            return handleTabCompletion(args[2], config.getBlacklistedCommands());
        }
        return List.of();
    }
}
