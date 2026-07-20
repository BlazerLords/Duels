package com.meteordevelopments.duels.command.commands.duels.subcommands;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.arena.destructible.DestructibleArenaConfig;
import com.meteordevelopments.duels.command.BaseCommand;
import com.meteordevelopments.duels.core.kit.KitImpl;
import com.meteordevelopments.duels.util.StringUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class KitdebugCommand extends BaseCommand {
    public KitdebugCommand(DuelsPlugin plugin) {
        super(plugin, "kitdebug", "kitdebug <kit>", "Показывает настройки разрушаемой арены кита.", 2, false);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String name = StringUtil.join(args, " ", 1, args.length).replace("-", " ");
        KitImpl kit = kitManager.get(name);
        if (kit == null) {
            lang.sendMessage(sender, "ERROR.kit.not-found", "name", name);
            return;
        }
        DestructibleArenaConfig c = kit.getDestructibleArena();
        sender.sendMessage(StringUtil.color("&8&m                                                "));
        sender.sendMessage(StringUtil.color("&6&lРазрушаемая арена: &e" + kit.getName()));
        sender.sendMessage(StringUtil.color("&7Модуль: " + state(c.isEnabled())
                + " &7| Установка: " + state(c.isAllowBlockPlace())
                + " &7| Ломание: " + state(c.isAllowBlockBreak())
                + " &7| Взрывы: " + state(c.isAllowExplosions())));
        sender.sendMessage(StringUtil.color("&7Кристаллы: " + state(c.isAllowEndCrystals())
                + " &7| Якоря: " + state(c.isAllowRespawnAnchors())
                + " &7| TNT-вагонетки: " + state(c.isAllowTntMinecarts())));
        sender.sendMessage(StringUtil.color("&7TNT: " + state(c.isAllowTnt())
                + " &7| Кровати: " + state(c.isAllowBedExplosions())
                + " &7| Жидкости: " + state(c.isAllowLiquids())
                + " &7| Поршни: " + state(c.isAllowPistons())
                + " &7| Огонь: " + state(c.isAllowFire())));
        sender.sendMessage(StringUtil.color("&7Выпадение блоков: " + state(!c.isSuppressBlockDrops())
                + " &7| Выпадение сущностей: " + state(!c.isSuppressEntityDrops())));
        sender.sendMessage(StringUtil.color("&7Очистка сущностей: " + state(c.isCleanupEntities())
                + " &7| Восстановление после матча: " + state(c.isRestoreAfterMatch())));
        sender.sendMessage(StringUtil.color("&7Блоки для установки: &f" + c.getAllowedPlaceMaterials()));
        sender.sendMessage(StringUtil.color("&7Разрушение: &aвсе блоки внутри границ арены"));
        sender.sendMessage(StringUtil.color("&8Исходные блоки не выпадают, поставленные игроком расходники выпадают при ручном ломании."));
        sender.sendMessage(StringUtil.color("&8&m                                                "));
    }

    private String state(boolean value) {
        return value ? "&aвключено" : "&cвыключено";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 2 ? handleTabCompletion(args[1], kitManager.getNames(false)) : null;
    }
}
