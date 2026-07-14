package com.meteordevelopments.duels.command.commands;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.Permissions;
import com.meteordevelopments.duels.command.BaseCommand;
import com.meteordevelopments.duels.config.CommandsConfig.CommandSettings;
import com.meteordevelopments.duels.tournament.Tournament;
import com.meteordevelopments.duels.tournament.TournamentKitMode;
import com.meteordevelopments.duels.tournament.TournamentManager;
import com.meteordevelopments.duels.tournament.TournamentStatus;
import com.meteordevelopments.duels.util.StringUtil;
import com.github.thesilentpro.headdb.api.HeadAPI;
import com.github.thesilentpro.headdb.api.model.Head;
import com.github.thesilentpro.headdb.core.HeadDB;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TournamentCommand extends BaseCommand implements Listener {

    private final TournamentManager tournamentManager;
    private final Map<Integer, ItemStack> headDbItems = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingNpcBindings = new ConcurrentHashMap<>();

    private static final int TROPHY_HEAD_ID = 42035;
    private static final int CANCEL_HEAD_ID = 30154;
    private static final int RULES_HEAD_ID = 281;
    private static final int SPECTATE_HEAD_ID = 108522;

    public TournamentCommand(final DuelsPlugin plugin, final CommandSettings settings) {
        super(plugin, Objects.requireNonNull(settings, "settings").getName(), null, false, settings.getAliasArray());
        this.tournamentManager = plugin.getTournamentManager();
        plugin.registerListener(this);
        preloadHeadDbItems();
    }

    @Override
    protected void execute(final CommandSender sender, final String label, final String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "help":
            case "?":
                help(sender, label, args);
                break;
            case "create":
                create(sender, args);
                break;
            case "kitmode":
            case "mode":
                kitMode(sender, args);
                break;
            case "kits":
            case "kitlimit":
                tournamentKits(sender, args);
                break;
            case "arenas":
            case "arenalimit":
                tournamentArenas(sender, args);
                break;
            case "delete":
                delete(sender, args);
                break;
            case "add":
                add(sender, args);
                break;
            case "join":
                join(sender, args);
                break;
            case "leave":
                leave(sender, args);
                break;
            case "remove":
                remove(sender, args);
                break;
            case "start":
                start(sender, args);
                break;
            case "cancel":
                cancel(sender, args);
                break;
            case "reset":
                reset(sender, args);
                break;
            case "finish":
                finish(sender, args);
                break;
            case "info":
                info(sender, args);
                break;
            case "mymatch":
                myMatch(sender);
                break;
            case "selectkit":
                selectKit(sender);
                break;
            case "admin":
            case "gui":
            case "panel":
                openAdminMenu(sender);
                break;
            case "menu":
                openSmartMenu(sender, args);
                break;
            case "player":
            case "playermenu":
                openPlayerMenuCommand(sender, args);
                break;
            case "confirm":
                confirm(sender, args);
                break;
            case "match":
                match(sender, args);
                break;
            case "next":
            case "nextmatch":
                nextMatch(sender, args);
                break;
            case "win":
                win(sender, args);
                break;
            case "replay":
                replay(sender, args);
                break;
            case "holo":
                holo(sender, args);
                break;
            case "npc":
                npc(sender, args);
                break;
            case "setlobby":
                setLobby(sender);
                break;
            case "spec":
            case "spectate":
                spectate(sender, args);
                break;
            default:
                sendHelp(sender, label);
                break;
        }
    }

    private void help(final CommandSender sender, final String label, final String[] args) {
        if (args.length < 2) {
            sendHelp(sender, label);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "player", "игрок" -> sendPlayerHelp(sender);
            case "admin", "organizer", "организатор" -> sendAdminHelp(sender);
            case "match", "matches", "матчи" -> sendMatchHelp(sender);
            case "holo", "hologram", "holograms", "голограммы" -> sendHoloHelp(sender);
            case "npc", "нпс" -> sendNpcHelp(sender);
            case "spec", "spectate", "наблюдение" -> sendSpectateHelp(sender);
            default -> sendHelp(sender, label);
        }
    }

    private void create(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_CREATE)) return;
        if (args.length < 3) {
            message(sender, "&cИспользование: /tour create <турнир> <fixed|choice> &7или &c/tour create <турнир> <kit> [fixed|choice]");
            return;
        }
        final TournamentKitMode directMode = parseKitMode(args[2]);
        final boolean modeOnlyCreate = directMode != null && args.length == 3;
        final TournamentKitMode kitMode = modeOnlyCreate ? directMode : args.length >= 4 ? parseKitMode(args[3]) : TournamentKitMode.FIXED;
        if (kitMode == null) {
            message(sender, "&cРежим кита: fixed или choice.");
            return;
        }

        switch (modeOnlyCreate ? tournamentManager.create(args[1], kitMode) : tournamentManager.create(args[1], args[2], kitMode)) {
            case SUCCESS -> successBlock(sender, "Турнир создан", List.of(
                    "&7Название: &e" + args[1],
                    modeOnlyCreate ? "&7Тип турнира: " + formatKitMode(kitMode) : "&7Кит: &f" + args[2],
                    modeOnlyCreate ? "&7Киты настраиваются в &f/tour menu &7-> &fРазрешённые киты" : "&7Режим кита: " + formatKitMode(kitMode),
                    "&7Дальше: &f/tour add " + args[1] + " <ник> &7или &f/tour menu"));
            case ALREADY_EXISTS -> errorBlock(sender, "Турнир уже существует", List.of(
                    "&7Название &e" + args[1] + " &7уже занято.",
                    "&7Начать заново без удаления: &f/tour reset " + args[1],
                    "&7Удалить полностью: &f/tour delete " + args[1]));
            case NO_KIT -> errorBlock(sender, "Кит не найден", List.of(
                    "&7Кит &f" + args[2] + " &7не найден в Duels.",
                    "&7Проверьте название кита и повторите команду."));
        }
    }

    private void kitMode(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_CREATE)) return;
        if (args.length < 3) {
            message(sender, "&cИспользование: /tour kitmode <турнир> <fixed|choice>");
            return;
        }

        final TournamentKitMode kitMode = parseKitMode(args[2]);
        if (kitMode == null) {
            message(sender, "&cРежим кита: fixed или choice.");
            return;
        }

        switch (tournamentManager.setKitMode(args[1], kitMode)) {
            case SUCCESS -> successBlock(sender, "Режим кита изменён", List.of(
                    "&7Турнир: &e" + args[1],
                    "&7Режим: " + formatKitMode(kitMode)));
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case LOCKED -> message(sender, "&cРежим можно менять только до запуска турнира.");
        }
    }

    private void tournamentKits(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_CREATE)) return;
        if (args.length < 3) {
            message(sender, "&cИспользование: /tour kits <name> <list|toggle|clear> [kit]");
            return;
        }

        final Tournament tournament = tournamentManager.getTournament(args[1]);
        if (tournament == null) {
            message(sender, "&cТурнир не найден.");
            return;
        }

        switch (args[2].toLowerCase()) {
            case "list" -> {
                message(sender, "&6Киты турнира &e" + tournament.getName() + "&6:");
                message(sender, tournament.getAllowedKits().isEmpty()
                        ? "&7Ограничений нет, используется общий список китов."
                        : "&f" + String.join("&7, &f", tournament.getAllowedKits()));
            }
            case "toggle", "add", "remove" -> {
                if (args.length < 4) {
                    message(sender, "&cИспользование: /tour kits " + tournament.getName() + " toggle <kit>");
                    return;
                }
                handleListChange(sender, tournamentManager.toggleAllowedKit(tournament.getName(), args[3]),
                        "&aСписок китов турнира обновлён.", "&cКит не найден.");
            }
            case "clear", "all" -> handleListChange(sender, tournamentManager.clearAllowedKits(tournament.getName()),
                    "&aОграничения китов очищены.", "&cКит не найден.");
            default -> message(sender, "&cИспользование: /tour kits <name> <list|toggle|clear> [kit]");
        }
    }

    private void tournamentArenas(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_CREATE)) return;
        if (args.length < 3) {
            message(sender, "&cИспользование: /tour arenas <name> <list|toggle|clear> [arena]");
            return;
        }

        final Tournament tournament = tournamentManager.getTournament(args[1]);
        if (tournament == null) {
            message(sender, "&cТурнир не найден.");
            return;
        }

        switch (args[2].toLowerCase()) {
            case "list" -> {
                message(sender, "&6Арены турнира &e" + tournament.getName() + "&6:");
                message(sender, tournament.getAllowedArenas().isEmpty()
                        ? "&7Ограничений нет, Duels выберет подходящую арену сам."
                        : "&f" + String.join("&7, &f", tournament.getAllowedArenas()));
            }
            case "toggle", "add", "remove" -> {
                if (args.length < 4) {
                    message(sender, "&cИспользование: /tour arenas " + tournament.getName() + " toggle <arena>");
                    return;
                }
                handleListChange(sender, tournamentManager.toggleAllowedArena(tournament.getName(), args[3]),
                        "&aСписок арен турнира обновлён.", "&cАрена не найдена.");
            }
            case "clear", "all" -> handleListChange(sender, tournamentManager.clearAllowedArenas(tournament.getName()),
                    "&aОграничения арен очищены.", "&cАрена не найдена.");
            default -> message(sender, "&cИспользование: /tour arenas <name> <list|toggle|clear> [arena]");
        }
    }

    private void handleListChange(final CommandSender sender, final TournamentManager.TournamentListChangeResult result,
                                  final String success, final String invalid) {
        switch (result) {
            case SUCCESS -> message(sender, success);
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case LOCKED -> message(sender, "&cОграничения можно менять только до запуска турнира.");
            case INVALID_VALUE -> message(sender, invalid);
        }
    }

    private void delete(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_DELETE)) return;
        if (args.length < 2) {
            message(sender, "&cИспользование: /tour delete <турнир>");
            return;
        }
        message(sender, tournamentManager.delete(args[1]) ? "&aТурнир удалён." : "&cТурнир не найден.");
    }

    private void add(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_ADD)) return;
        if (args.length < 3) {
            message(sender, "&cИспользование: /tour add <турнир> <ник>");
            return;
        }
        switch (tournamentManager.addPlayer(args[1], args[2])) {
            case SUCCESS -> message(sender, "&aИгрок добавлен.");
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case LOCKED -> message(sender, "&cРегистрация уже закрыта.");
            case ALREADY_ADDED -> message(sender, "&cИгрок уже добавлен.");
        }
    }

    private void join(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cЭта команда доступна только игроку.");
            return;
        }
        if (!has(player, Permissions.TOURNAMENT_JOIN)) return;
        if (args.length < 2) {
            message(sender, "&cИспользование: /tour join <турнир>");
            return;
        }
        switch (tournamentManager.joinPlayer(args[1], player)) {
            case SUCCESS -> message(sender, "&aВы зарегистрированы на турнир &e" + args[1] + "&a.");
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case LOCKED -> message(sender, "&cРегистрация уже закрыта.");
            case ALREADY_ADDED -> message(sender, "&eВы уже зарегистрированы на этот турнир.");
            case ALREADY_IN_OTHER_TOURNAMENT -> {
                final Tournament activeTournament = tournamentManager.getPlayerOpenTournament(player.getName());
                message(sender, activeTournament == null
                        ? "&cВы уже участвуете в другом незавершённом турнире."
                        : "&cВы уже участвуете в турнире &e" + activeTournament.getName() + "&c.");
            }
        }
    }

    private void leave(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cЭта команда доступна только игроку.");
            return;
        }
        if (!has(player, Permissions.TOURNAMENT_JOIN)) return;
        if (args.length < 2) {
            message(sender, "&cИспользование: /tour leave <турнир>");
            return;
        }
        switch (tournamentManager.leavePlayer(args[1], player)) {
            case SUCCESS -> message(sender, "&aВы отменили регистрацию на турнир &e" + args[1] + "&a.");
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case LOCKED -> message(sender, "&cТурнир уже начался, регистрацию отменить нельзя.");
            case NOT_REGISTERED -> message(sender, "&eВы не зарегистрированы на этот турнир.");
        }
    }

    private void remove(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_REMOVE)) return;
        if (args.length < 3) {
            message(sender, "&cИспользование: /tour remove <турнир> <ник>");
            return;
        }
        message(sender, tournamentManager.removePlayer(args[1], args[2]) ? "&aИгрок удалён." : "&cНе удалось удалить игрока.");
    }

    private void start(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_START)) return;
        if (args.length < 2) {
            message(sender, "&cИспользование: /tour start <турнир>");
            return;
        }
        switch (tournamentManager.startTournament(args[1])) {
            case SUCCESS -> successBlock(sender, "Турнир запущен", List.of(
                    "&7Сетка сформирована, пары готовы.",
                    "&7Панель управления: &f/tour menu"), "/tour nextmatch " + args[1]);
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case ALREADY_STARTED -> errorBlock(sender, "Турнир уже идёт", List.of(
                    "&7Нельзя повторно запустить активный турнир.",
                    "&7Информация: &f/tour info " + args[1]), "/tour nextmatch " + args[1]);
            case NOT_ENOUGH_PLAYERS -> message(sender, "&cНужно минимум 2 участника.");
            case CANCELLED -> errorBlock(sender, "Турнир отменён", List.of(
                    "&7Сначала верните его в регистрацию: &f/tour reset " + args[1],
                    "&7После этого можно снова запустить: &f/tour start " + args[1]));
            case FINISHED -> errorBlock(sender, "Турнир уже завершён", List.of(
                    "&7Чтобы провести заново с теми же участниками: &f/tour reset " + args[1],
                    "&7Или создайте новый турнир с другим названием."));
        }
    }

    private void cancel(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_CANCEL)) return;
        if (args.length < 2) {
            message(sender, "&cИспользование: /tour cancel <турнир>");
            return;
        }
        if (tournamentManager.cancel(args[1])) {
            successBlock(sender, "Турнир отменён", List.of(
                    "&7Турнир сохранён в списке, но его матчи остановлены.",
                    "&7Вернуть в регистрацию: &f/tour reset " + args[1],
                    "&7Удалить полностью: &f/tour delete " + args[1]));
        } else {
            message(sender, "&cНе удалось отменить турнир.");
        }
    }

    private void reset(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_CANCEL)) return;
        if (args.length < 2) {
            message(sender, "&cИспользование: /tour reset <турнир>");
            return;
        }
        if (tournamentManager.reset(args[1])) {
            successBlock(sender, "Турнир возвращён в регистрацию", List.of(
                    "&7Название: &e" + args[1],
                    "&7Участники сохранены, сетка очищена.",
                    "&7Запуск: &f/tour start " + args[1]));
        } else {
            message(sender, "&cТурнир не найден.");
        }
    }

    private void finish(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_FINISH)) return;
        if (args.length < 2) {
            message(sender, "&cИспользование: /tour finish <турнир>");
            return;
        }
        message(sender, tournamentManager.finishTournament(args[1]) ? "&aТурнир завершён." : "&cНе удалось завершить турнир.");
    }

    private void info(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_ADMIN)) return;
        if (args.length < 2) {
            message(sender, "&cИспользование: /tour info <турнир>");
            return;
        }
        final Tournament tournament = tournamentManager.getTournament(args[1]);
        if (tournament == null) {
            message(sender, "&cТурнир не найден.");
            return;
        }
        tournamentManager.describe(tournament).forEach(line -> message(sender, line));
    }

    private void match(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_START)) return;
        if (args.length < 5 || !"start".equalsIgnoreCase(args[1])) {
            message(sender, "&cИспользование: /tour match start <турнир> <раунд> <матч>");
            return;
        }
        final int round = parsePositive(args[3]);
        final int match = parsePositive(args[4]);
        if (round <= 0 || match <= 0) {
            message(sender, "&cРаунд и матч должны быть числами.");
            return;
        }
        switch (tournamentManager.startMatch(args[2], round, match)) {
            case SUCCESS -> message(sender, "&aМатч запускается через 30 секунд.");
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case NO_MATCH -> message(sender, "&cМатч не найден.");
            case NOT_READY -> message(sender, "&cМатч ещё не готов.");
            case PLAYER_OFFLINE -> message(sender, "&eОдин игрок оффлайн. Матч ожидает игрока.");
            case BOTH_OFFLINE -> message(sender, "&eОба игрока оффлайн. Матч ожидает игроков.");
            case NO_KIT -> message(sender, "&cКит турнира не найден.");
            case NO_ARENA -> message(sender, "&cНет свободной разрешённой арены для этого турнира и кита.");
            case DUEL_REJECTED -> message(sender, "&cDuels не смог запустить матч. Проверь арену/валидаторы.");
            case STARTING -> message(sender, "&eМатч уже запускается.");
            case ALREADY_RUNNING -> message(sender, "&cМатч уже идёт.");
            case FINISHED -> message(sender, "&cМатч уже завершён.");
        }
    }

    private void nextMatch(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_START)) return;
        if (args.length < 2) {
            message(sender, "&cИспользование: /tour next <турнир>");
            return;
        }

        final TournamentManager.NextMatchStart next = tournamentManager.startNextMatch(args[1]);
        switch (next.result()) {
            case SUCCESS -> {
                message(sender, "&8&m                                                ");
                message(sender, "&6&lСледующая пара турнира запущена");
                message(sender, "&7Турнир: &e" + next.tournament());
                message(sender, "&7Раунд: &f" + next.round() + " &8| &7Матч: &f#" + next.match());
                message(sender, "&7Игроки: &a" + next.player1() + " &7vs &c" + next.player2());
                final Tournament tournament = tournamentManager.getTournament(next.tournament());
                message(sender, (tournament == null ? "&7Кит: &f" + next.kit() : effectiveKitLine(tournament)) + " &8| &7Арена: &f" + next.arena());
                message(sender, "&eМатч начнется через 30 секунд.");
                message(sender, "&8&m                                                ");
            }
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case NO_MATCH -> message(sender, "&eНет готовых пар для запуска. Дождитесь результата текущего матча или проверьте сетку.");
            case NOT_READY -> {
                final Tournament tournament = tournamentManager.getTournament(args[1]);
                if (tournament == null) {
                    message(sender, "&cТурнир не найден.");
                } else {
                    errorBlock(sender, "Следующая пара недоступна", List.of(
                            "&7Статус турнира: " + tournamentManager.formatTournamentStatus(tournament.getStatus()),
                            tournament.getStatus() == TournamentStatus.CANCELLED
                                    ? "&7Верните турнир в регистрацию: &f/tour reset " + tournament.getName()
                                    : "&7Запустите турнир или дождитесь формирования пары.",
                            "&7Информация: &f/tour info " + tournament.getName()));
                }
            }
            case PLAYER_OFFLINE -> {
                sendNextMatchHeader(sender, next);
                message(sender, "&eОдин из игроков оффлайн. Матч переведен в ожидание игрока.");
            }
            case BOTH_OFFLINE -> {
                sendNextMatchHeader(sender, next);
                message(sender, "&eОба игрока оффлайн. Матч переведен в ожидание игроков.");
            }
            case NO_KIT -> message(sender, "&cКит турнира не найден: &f" + next.kit());
            case NO_ARENA -> message(sender, "&cНет свободной разрешённой арены для этого турнира и кита.");
            case DUEL_REJECTED -> message(sender, "&cDuels не смог запустить матч. Проверь арену/валидаторы.");
            case STARTING -> {
                sendNextMatchHeader(sender, next);
                message(sender, "&eЭта пара уже запускается.");
            }
            case ALREADY_RUNNING -> {
                sendNextMatchHeader(sender, next);
                message(sender, "&cЭта пара уже играет.");
            }
            case FINISHED -> {
                sendNextMatchHeader(sender, next);
                message(sender, "&cЭта пара уже завершена.");
            }
        }
    }

    private void myMatch(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cЭта команда доступна только игроку.");
            return;
        }
        tournamentManager.describePlayerMatch(player).forEach(line -> message(sender, line));
    }

    private void openAdminMenu(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cЭта команда доступна только игроку.");
            return;
        }
        if (!has(player, Permissions.TOURNAMENT_ADMIN)) return;
        openTournamentList(player);
    }

    private void openSmartMenu(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cЭта команда доступна только игроку.");
            return;
        }
        if (player.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            openTournamentList(player);
            return;
        }
        openPlayerMenu(player, args.length >= 2 ? args[1] : null);
    }

    private void openPlayerMenuCommand(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cЭта команда доступна только игроку.");
            return;
        }
        openPlayerMenu(player, args.length >= 2 ? args[1] : null);
    }

    private void confirm(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cПодтверждение доступно только игроку.");
            return;
        }
        if (args.length < 2) {
            message(sender, "&cНечего подтверждать.");
            return;
        }
        final String command = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        final String pending = pendingConfirmations.remove(player.getUniqueId());
        if (!command.equalsIgnoreCase(pending)) {
            message(player, "&cПодтверждение устарело. Нажмите команду ещё раз.");
            return;
        }
        Bukkit.dispatchCommand(player, command);
    }

    private void win(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_WIN)) return;
        if (args.length < 3) {
            message(sender, "&cИспользование: /tour win <турнир> <ник>");
            return;
        }
        message(sender, tournamentManager.setWinner(args[1], args[2]) ? "&aПобедитель записан." : "&cНе удалось записать победителя.");
    }

    private void replay(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_REPLAY)) return;
        if (args.length < 4) {
            message(sender, "&cИспользование: /tour replay <турнир> <раунд> <матч>");
            return;
        }
        final int round = parsePositive(args[2]);
        final int match = parsePositive(args[3]);
        if (round <= 0 || match <= 0) {
            message(sender, "&cРаунд и матч должны быть числами.");
            return;
        }
        switch (tournamentManager.replayMatch(args[1], round, match)) {
            case SUCCESS -> message(sender, "&aМатч возвращён на переигровку. Следующие раунды пересобраны.");
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case NO_MATCH -> message(sender, "&cМатч не найден.");
            case NOT_READY -> message(sender, "&cЭтот матч нельзя переиграть.");
        }
    }

    private void selectKit(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cЭта команда доступна только игроку.");
            return;
        }
        tournamentManager.reopenKitSelection(player);
    }

    private void holo(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_HOLO)) return;
        if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            final List<String> holograms = tournamentManager.describeHolograms();
            if (holograms.isEmpty()) {
                message(sender, "&eТурнирные голограммы не созданы.");
            } else {
                holograms.forEach(line -> message(sender, line));
            }
            return;
        }
        if (args.length < 3) {
            sendHoloHelp(sender);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "create", "set" -> {
                if (!(sender instanceof Player player)) {
                    message(sender, "&cЭта команда должна выполняться игроком.");
                    return;
                }
                message(sender, tournamentManager.createOrSetHologram(player, args[2])
                        ? "&aГолограмма создана/перенесена и обновлена."
                        : "&cТурнир не найден или CMI-голограммы выключены.");
            }
            case "remove" -> message(sender, tournamentManager.removeHologram(args[2])
                    ? "&aГолограмма удалена."
                    : "&cГолограмма или турнир не найден.");
            case "update" -> {
                tournamentManager.updateHologram(args[2]);
                message(sender, "&aГолограмма обновлена.");
            }
            case "page" -> {
                if (args.length < 4) {
                    message(sender, "&cИспользование: /tour holo page <турнир> <страница>");
                    return;
                }
                message(sender, tournamentManager.setHologramPage(args[2], parsePositive(args[3])) ? "&aСтраница обновлена." : "&cНе удалось изменить страницу.");
            }
            case "round" -> {
                if (args.length < 4) {
                    message(sender, "&cИспользование: /tour holo round <турнир> <раунд>");
                    return;
                }
                message(sender, tournamentManager.setHologramRound(args[2], parsePositive(args[3])) ? "&aРаунд голограммы обновлён." : "&cНе удалось изменить раунд.");
            }
            case "direction" -> {
                if (args.length < 4) {
                    message(sender, "&cИспользование: /tour holo direction <турнир> <auto|north|east|south|west>");
                    return;
                }
                switch (tournamentManager.setHologramDirection(args[2], args[3])) {
                    case SUCCESS -> message(sender, "&aНаправление голограммы " + args[2] + " изменено на " + args[3].toLowerCase() + ".");
                    case NOT_FOUND -> message(sender, "&cТурнир не найден.");
                    case INVALID_DIRECTION -> message(sender, "&cНаправление должно быть: auto, north, east, south или west.");
                }
            }
            case "next", "prev" -> {
                final Tournament tournament = tournamentManager.getTournament(args[2]);
                if (tournament == null) {
                    message(sender, "&cТурнир не найден.");
                    return;
                }
                final int page = Math.max(1, tournament.getHologramPage() + ("next".equalsIgnoreCase(args[1]) ? 1 : -1));
                tournamentManager.setHologramPage(args[2], page);
                message(sender, "&aСтраница обновлена.");
            }
            default -> sendHoloHelp(sender);
        }
    }

    private void npc(final CommandSender sender, final String[] args) {
        if (!has(sender, Permissions.TOURNAMENT_ADMIN)) return;
        if (args.length < 2) {
            sendNpcHelp(sender);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "name" -> {
                if (args.length < 3) {
                    message(sender, "&cИспользование: /tour npc name <турнир>");
                    return;
                }
                final Tournament tournament = tournamentManager.getTournament(args[2]);
                if (tournament == null) {
                    message(sender, "&cТурнир не найден.");
                    return;
                }
                successBlock(sender, "Имя NPC для турнира", List.of(
                        "&7Турнир: &e" + tournament.getName(),
                        "&7Рекомендуемое имя NPC: &f" + tournamentManager.suggestFancyNpcName(tournament.getName()),
                        "&7Создайте FancyNPC с этим именем или привяжите существующий:",
                        "&f/tour npc bind " + tournament.getName() + " <npcNameOrId>"));
            }
            case "bind" -> {
                if (args.length < 4) {
                    message(sender, "&cИспользование: /tour npc bind <турнир> <npcNameOrId>");
                    return;
                }
                switch (tournamentManager.bindFancyNpc(args[2], args[3])) {
                    case SUCCESS -> successBlock(sender, "NPC привязан", List.of(
                            "&7Турнир: &e" + args[2],
                            "&7NPC: &f" + args[3],
                            "&7Игроки смогут открыть меню турнира кликом по этому FancyNPC."));
                    case NOT_FOUND -> message(sender, "&cТурнир не найден.");
                    case INVALID_NPC -> message(sender, "&cУкажите имя или id NPC.");
                }
            }
            case "unbind" -> {
                if (args.length < 3) {
                    message(sender, "&cИспользование: /tour npc unbind <npcNameOrId>");
                    return;
                }
                message(sender, tournamentManager.unbindFancyNpc(args[2])
                        ? "&aNPC отвязан от турнира."
                        : "&cТакая привязка NPC не найдена.");
            }
            case "list" -> tournamentManager.describeFancyNpcBindings().forEach(line -> message(sender, line));
            default -> sendNpcHelp(sender);
        }
    }

    private void spectate(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "&cЭта команда доступна только игроку.");
            return;
        }

        if (args.length >= 2 && "current".equalsIgnoreCase(args[1])) {
            message(sender, plugin.getSpectateManager().isSpectating(player) ? "&aВы сейчас наблюдаете матч." : "&eВы сейчас не наблюдаете матч.");
            return;
        }

        if (args.length >= 3 && "list".equalsIgnoreCase(args[1])) {
            final Tournament tournament = tournamentManager.getTournament(args[2]);
            if (tournament == null) {
                message(sender, "&cТурнир не найден.");
                return;
            }
            final List<String> matches = tournamentManager.activeSpecList(tournament);
            if (matches.isEmpty()) {
                message(sender, "&eСейчас нет активных матчей.");
            } else {
                matches.forEach(line -> message(sender, "&7" + line));
            }
            return;
        }

        if (!has(player, Permissions.TOURNAMENT_SPECTATE)) return;
        if (args.length < 2) {
            sendSpectateHelp(sender);
            return;
        }

        final boolean admin = player.hasPermission(Permissions.TOURNAMENT_SPECTATE_ADMIN) || player.hasPermission(Permissions.TOURNAMENT_ADMIN);
        final TournamentManager.SpectateResult result;
        if (args.length >= 4) {
            final int round = parsePositive(args[2]);
            final int match = parsePositive(args[3]);
            result = tournamentManager.spectate(player, args[1], round, match, admin);
        } else {
            result = tournamentManager.spectate(player, args[1], admin);
        }

        switch (result) {
            case SUCCESS -> message(sender, "&aВы наблюдаете за матчем турнира.");
            case NOT_FOUND -> message(sender, "&cТурнир не найден.");
            case NO_MATCH -> message(sender, "&cМатч не найден.");
            case NOT_STARTED -> message(sender, "&eМатч ещё не начался.");
            case FINISHED -> message(sender, "&eМатч уже завершён.");
            case ACTIVE_PARTICIPANT -> message(sender, "&cУчастник, который ещё не вылетел, не может наблюдать другие матчи.");
            case NO_TARGET -> message(sender, "&cИгрок матча не онлайн.");
            case REJECTED -> message(sender, "&cDuels не разрешил наблюдение.");
        }
    }

    private void setLobby(final CommandSender sender) {
        if (!has(sender, Permissions.TOURNAMENT_SET_LOBBY)) return;
        if (!(sender instanceof Player player)) {
            message(sender, "&cЭта команда доступна только игроку.");
            return;
        }
        message(sender, tournamentManager.setLobby(player) ? "&aЛобби турниров установлено." : "&cНе удалось сохранить лобби турниров.");
    }

    private boolean has(final CommandSender sender, final String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            return true;
        }
        message(sender, "&cНедостаточно прав: " + permission);
        return false;
    }

    private int parsePositive(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private TournamentKitMode parseKitMode(final String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase()) {
            case "fixed", "single", "one", "kit", "фикс", "фиксированный" -> TournamentKitMode.FIXED;
            case "choice", "choose", "select", "players", "player", "выбор" -> TournamentKitMode.PLAYER_CHOICE;
            default -> null;
        };
    }

    private String formatKitMode(final TournamentKitMode kitMode) {
        return kitMode == TournamentKitMode.PLAYER_CHOICE
                ? StringUtil.color("&aВыбор игроками")
                : StringUtil.color("&fФиксированный кит");
    }

    private String effectiveKitLine(final Tournament tournament) {
        if (tournament.getAllowedKits().size() == 1) {
            return "&7Кит: &f" + tournament.getAllowedKits().get(0);
        }
        if (tournament.getAllowedKits().size() > 1 || tournament.getKitMode() == TournamentKitMode.PLAYER_CHOICE) {
            return "&7Тип турнира: &aВыбор китов";
        }
        if (tournament.getKit() == null || tournament.getKit().isBlank()) {
            return "&7Кит: &cне выбран";
        }
        return "&7Кит: &f" + tournament.getKit();
    }

    private String effectiveTypeLine(final Tournament tournament) {
        if (tournament.getAllowedKits().size() == 1) {
            return "&7Режим кита: " + formatKitMode(TournamentKitMode.FIXED);
        }
        if (tournament.getAllowedKits().size() > 1) {
            return "&7Китов на выбор: &f" + tournament.getAllowedKits().size();
        }
        if (tournament.getKit() == null || tournament.getKit().isBlank()) {
            return "&7Выберите кит в &fРазрешённые киты";
        }
        return "&7Режим кита: " + formatKitMode(tournament.getKitMode());
    }

    private String effectiveKitIconName(final Tournament tournament) {
        if (tournament.getAllowedKits().size() == 1) {
            return tournament.getAllowedKits().get(0);
        }
        return tournament.getKit();
    }

    private void sendHelp(final CommandSender sender, final String label) {
        message(sender, "&8&m                                                ");
        message(sender, "&6&lТурниры");
        message(sender, "&7Основное меню: &e/tour menu");
        message(sender, "&7Помощь по разделам:");
        message(sender, "&e/tour help player &7- команды игрока");
        if (canUseTournamentAdminCommands(sender)) {
            message(sender, "&e/tour help admin &7- создание и управление турнирами");
            message(sender, "&e/tour help match &7- запуск, победы и переигровки матчей");
            message(sender, "&e/tour help npc &7- привязка FancyNPC к турнирам");
            message(sender, "&e/tour help holo &7- CMI-голограммы турниров");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_SPECTATE)
                || sender.hasPermission(Permissions.TOURNAMENT_SPECTATE_ADMIN)
                || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            message(sender, "&e/tour help spec &7- наблюдение за матчами");
        }
        message(sender, "&7Коротко: &f/tour join <турнир>&7, &f/tour mymatch&7, &f/tour info <турнир>");
        message(sender, "&7Внутренние команды меню скрыты и вручную обычно не нужны.");
        message(sender, "&8&m                                                ");
    }

    private void sendPlayerHelp(final CommandSender sender) {
        message(sender, "&8&m                                                ");
        message(sender, "&6&lТурниры: игрок");
        message(sender, "&e/tour menu &7- открыть меню турниров и NPC-ориентированные действия");
        message(sender, "&e/tour join <турнир> &7- зарегистрироваться до старта");
        message(sender, "&e/tour leave <турнир> &7- отменить регистрацию до старта");
        message(sender, "&e/tour mymatch &7- показать ваш текущий матч, ожидание или результат");
        message(sender, "&e/tour info <турнир> &7- состав, статус, сетка и активные матчи");
        message(sender, "&e/tour spec <турнир> &7- наблюдать активный матч турнира");
        message(sender, "&7Ограничение: &fигрок может быть только в одном незавершённом турнире.");
        message(sender, "&8&m                                                ");
    }

    private void sendAdminHelp(final CommandSender sender) {
        message(sender, "&8&m                                                ");
        message(sender, "&6&lТурниры: организатор");
        message(sender, "&e/tour menu &7- админ-панель турниров через инвентарь");
        message(sender, "&e/tour create <турнир> <fixed|choice> &7- создать турнир без фиксированного кита");
        message(sender, "&e/tour create <турнир> <kit> [fixed|choice] &7- создать турнир с китом");
        message(sender, "&e/tour kitmode <турнир> <fixed|choice> &7- сменить режим кита до старта");
        message(sender, "&e/tour kits <турнир> list|toggle|clear [kit] &7- разрешённые киты");
        message(sender, "&e/tour arenas <турнир> list|toggle|clear [arena] &7- разрешённые арены");
        message(sender, "&e/tour add <турнир> <ник> &7- вручную добавить участника");
        message(sender, "&e/tour remove <турнир> <ник> &7- вручную убрать участника до старта");
        message(sender, "&e/tour start <турнир> &7- сформировать сетку и запустить турнир");
        message(sender, "&e/tour cancel <турнир> &7- отменить турнир без удаления");
        message(sender, "&e/tour reset <турнир> &7- вернуть турнир в регистрацию, сохранив участников");
        message(sender, "&e/tour finish <турнир> &7- принудительно завершить турнир");
        message(sender, "&e/tour delete <турнир> &7- удалить турнир полностью");
        message(sender, "&e/tour setlobby &7- поставить лобби турниров на вашу позицию");
        message(sender, "&7Дополнительно: &f/tour help match&7, &f/tour help npc&7, &f/tour help holo");
        message(sender, "&8&m                                                ");
    }

    private void sendMatchHelp(final CommandSender sender) {
        message(sender, "&8&m                                                ");
        message(sender, "&6&lТурниры: матчи");
        message(sender, "&e/tour nextmatch <турнир> &7- запустить ближайшую готовую пару");
        message(sender, "&e/tour next <турнир> &7- то же самое, короткий алиас");
        message(sender, "&e/tour match start <турнир> <раунд> <матч> &7- запустить конкретный матч");
        message(sender, "&e/tour win <турнир> <ник> &7- вручную засчитать победу игроку");
        message(sender, "&e/tour replay <турнир> <раунд> <матч> &7- вернуть матч на переигровку");
        message(sender, "&e/tour info <турнир> &7- посмотреть номера раундов и матчей");
        message(sender, "&7Если игрок не онлайн, матч уходит в ожидание; по таймеру победа даётся онлайн-игроку.");
        message(sender, "&8&m                                                ");
    }

    private void sendHoloHelp(final CommandSender sender) {
        message(sender, "&8&m                                                ");
        message(sender, "&6&lТурниры: голограммы");
        message(sender, "&e/tour holo list &7- список турнирных CMI-голограмм");
        message(sender, "&e/tour holo create <турнир> &7- создать голограммы у вашей позиции");
        message(sender, "&e/tour holo set <турнир> &7- перенести существующие голограммы к вам");
        message(sender, "&e/tour holo update <турнир> &7- вручную обновить текст");
        message(sender, "&e/tour holo remove <турнир> &7- удалить голограммы турнира");
        message(sender, "&e/tour holo page <турнир> <страница> &7- показать страницу сетки");
        message(sender, "&e/tour holo next <турнир> &7- следующая страница сетки");
        message(sender, "&e/tour holo prev <турнир> &7- предыдущая страница сетки");
        message(sender, "&e/tour holo round <турнир> <раунд> &7- закрепить отображаемый раунд");
        message(sender, "&e/tour holo direction <турнир> <auto|north|east|south|west> &7- направление панелей");
        message(sender, "&8&m                                                ");
    }

    private void sendNpcHelp(final CommandSender sender) {
        message(sender, "&8&m                                                ");
        message(sender, "&6&lТурниры: NPC");
        message(sender, "&e/tour menu &7- выбрать турнир -> &fNPC турнира &7и привязать через меню");
        message(sender, "&e/tour npc bind <турнир> <npcNameOrId> &7- привязать FancyNPC к турниру");
        message(sender, "&e/tour npc unbind <npcNameOrId> &7- отвязать NPC");
        message(sender, "&e/tour npc list &7- показать все привязки NPC -> турнир");
        message(sender, "&e/tour npc name <турнир> &7- подсказать техническое имя, если нужно");
        message(sender, "&7NPC может называться как угодно. Один NPC ведёт только в один турнир.");
        message(sender, "&8&m                                                ");
    }

    private void sendSpectateHelp(final CommandSender sender) {
        message(sender, "&8&m                                                ");
        message(sender, "&6&lТурниры: наблюдение");
        message(sender, "&e/tour spec <турнир> &7- наблюдать первый активный матч турнира");
        message(sender, "&e/tour spec <турнир> <раунд> <матч> &7- наблюдать конкретный матч");
        message(sender, "&e/tour spec list <турнир> &7- список активных матчей для наблюдения");
        message(sender, "&e/tour spec current &7- проверить, наблюдаете ли вы сейчас матч");
        message(sender, "&7Активный участник турнира не может смотреть чужие матчи, пока не выбыл.");
        message(sender, "&8&m                                                ");
    }

    private boolean canUseTournamentAdminCommands(final CommandSender sender) {
        return sender.hasPermission(Permissions.TOURNAMENT_ADMIN)
                || sender.hasPermission(Permissions.TOURNAMENT_CREATE)
                || sender.hasPermission(Permissions.TOURNAMENT_START)
                || sender.hasPermission(Permissions.TOURNAMENT_ADD)
                || sender.hasPermission(Permissions.TOURNAMENT_REMOVE)
                || sender.hasPermission(Permissions.TOURNAMENT_CANCEL)
                || sender.hasPermission(Permissions.TOURNAMENT_FINISH)
                || sender.hasPermission(Permissions.TOURNAMENT_DELETE)
                || sender.hasPermission(Permissions.TOURNAMENT_WIN)
                || sender.hasPermission(Permissions.TOURNAMENT_REPLAY)
                || sender.hasPermission(Permissions.TOURNAMENT_HOLO)
                || sender.hasPermission(Permissions.TOURNAMENT_SET_LOBBY);
    }

    private void sendNextMatchHeader(final CommandSender sender, final TournamentManager.NextMatchStart next) {
        message(sender, "&8&m                                                ");
        message(sender, "&6&lСледующая пара турнира");
        message(sender, "&7Турнир: &e" + next.tournament());
        if (next.round() > 0 && next.match() > 0) {
            message(sender, "&7Раунд: &f" + next.round() + " &8| &7Матч: &f#" + next.match());
            message(sender, "&7Игроки: &a" + next.player1() + " &7vs &c" + next.player2());
        }
        message(sender, "&8&m                                                ");
    }

    private void message(final CommandSender sender, final String message) {
        sender.sendMessage(StringUtil.color(message));
    }

    private void successBlock(final CommandSender sender, final String title, final List<String> lines) {
        message(sender, "&8&m                                                ");
        message(sender, "&a&l" + title);
        lines.forEach(line -> message(sender, line));
        message(sender, "&8&m                                                ");
    }

    private void successBlock(final CommandSender sender, final String title, final List<String> lines, final String command) {
        message(sender, "&8&m                                                ");
        message(sender, "&a&l" + title);
        lines.forEach(line -> message(sender, line));
        sendConfirmableCommand(sender, "&7Следующая пара: ", command);
        message(sender, "&8&m                                                ");
    }

    private void errorBlock(final CommandSender sender, final String title, final List<String> lines) {
        message(sender, "&8&m                                                ");
        message(sender, "&c&l" + title);
        lines.forEach(line -> message(sender, line));
        message(sender, "&8&m                                                ");
    }

    private void errorBlock(final CommandSender sender, final String title, final List<String> lines, final String command) {
        message(sender, "&8&m                                                ");
        message(sender, "&c&l" + title);
        lines.forEach(line -> message(sender, line));
        sendConfirmableCommand(sender, "&7Следующая пара: ", command);
        message(sender, "&8&m                                                ");
    }

    private void sendConfirmableCommand(final CommandSender sender, final String prefix, final String command) {
        if (!(sender instanceof Player player)) {
            message(sender, prefix + "&f" + command);
            return;
        }
        final String cleanCommand = command.startsWith("/") ? command.substring(1) : command;
        pendingConfirmations.put(player.getUniqueId(), cleanCommand);
        final TextComponent line = new TextComponent(StringUtil.color(prefix));
        final TextComponent clickable = new TextComponent(StringUtil.color("&e&n" + command));
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tour confirm " + cleanCommand));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(StringUtil.color("&eНажмите, чтобы подтвердить запуск следующей пары.\n&7Команда выполнится только после этого клика.")).create()));
        line.addExtra(clickable);
        player.spigot().sendMessage(line);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof TournamentListMenu) && !(holder instanceof TournamentActionMenu)
                && !(holder instanceof TournamentHologramMenu) && !(holder instanceof TournamentNpcMenu)
                && !(holder instanceof TournamentPlayerMenu)
                && !(holder instanceof TournamentKitLimitMenu) && !(holder instanceof TournamentArenaLimitMenu)) {
            return;
        }

        event.setCancelled(true);
        if (holder instanceof TournamentListMenu) {
            handleTournamentListClick(player, event.getRawSlot());
        } else if (holder instanceof TournamentActionMenu menu) {
            handleTournamentActionClick(player, menu.tournament(), event.getRawSlot());
        } else if (holder instanceof TournamentHologramMenu menu) {
            handleTournamentHologramClick(player, menu.tournament(), event.getRawSlot());
        } else if (holder instanceof TournamentNpcMenu menu) {
            handleTournamentNpcClick(player, menu.tournament(), event.getRawSlot());
        } else if (holder instanceof TournamentPlayerMenu menu) {
            handlePlayerMenuClick(player, menu.tournament(), event.getRawSlot());
        } else if (holder instanceof TournamentKitLimitMenu menu) {
            handleTournamentKitLimitClick(player, menu.tournament(), event.getRawSlot());
        } else if (holder instanceof TournamentArenaLimitMenu menu) {
            handleTournamentArenaLimitClick(player, menu.tournament(), event.getRawSlot());
        }
    }

    @EventHandler
    public void onNpcBindingChat(final AsyncPlayerChatEvent event) {
        final String tournamentName = pendingNpcBindings.remove(event.getPlayer().getUniqueId());
        if (tournamentName == null) {
            return;
        }

        event.setCancelled(true);
        final String npcKey = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> finishNpcBindingInput(event.getPlayer(), tournamentName, npcKey));
    }

    private void finishNpcBindingInput(final Player player, final String tournamentName, final String npcKey) {
        final Tournament tournament = tournamentManager.getTournament(tournamentName);
        if ("cancel".equalsIgnoreCase(npcKey) || "отмена".equalsIgnoreCase(npcKey)) {
            message(player, "&eПривязка NPC отменена.");
            if (tournament != null) {
                openTournamentNpcSettings(player, tournament);
            }
            return;
        }
        if (npcKey.isBlank()) {
            message(player, "&cИмя NPC не может быть пустым.");
            if (tournament != null) {
                openTournamentNpcSettings(player, tournament);
            }
            return;
        }

        switch (tournamentManager.bindFancyNpc(tournamentName, npcKey)) {
            case SUCCESS -> message(player, "&aNPC &f" + npcKey + " &aпривязан к турниру &e" + tournamentName + "&a.");
            case NOT_FOUND -> message(player, "&cТурнир не найден.");
            case INVALID_NPC -> message(player, "&cУкажите имя или id NPC.");
        }
        if (tournament != null) {
            openTournamentNpcSettings(player, tournament);
        }
    }

    private void openPlayerMenu(final Player player, final String requestedTournament) {
        final Tournament tournament = requestedTournament == null || requestedTournament.isBlank()
                ? tournamentManager.getTournaments().stream().findFirst().orElse(null)
                : tournamentManager.getTournament(requestedTournament);
        if (tournament == null) {
            message(player, "&cТурнир не найден.");
            return;
        }

        final Inventory inventory = Bukkit.createInventory(new TournamentPlayerMenu(tournament.getName()), 27,
                StringUtil.color("&6Турнир: &e" + tournament.getName()));
        fill(inventory);
        inventory.setItem(4, headDbItem(TROPHY_HEAD_ID, Material.NETHER_STAR, "&6&l" + tournament.getName(),
                effectiveKitLine(tournament),
                effectiveTypeLine(tournament),
                "&7Статус: " + tournamentManager.formatTournamentStatus(tournament.getStatus()),
                "&7Участников: &f" + tournament.getPlayers().size()));
        inventory.setItem(10, item(Material.WRITABLE_BOOK, "&aРегистрация",
                "&7Нажмите, чтобы занять место",
                "&7в турнире до его запуска."));
        inventory.setItem(12, headDbItem(CANCEL_HEAD_ID, Material.RED_DYE, "&cОтменить регистрацию",
                "&7Нажмите, если передумали",
                "&7участвовать до старта турнира."));
        inventory.setItem(14, headDbItem(RULES_HEAD_ID, Material.CLOCK, "&eМой матч и правила",
                "&7Покажет ваш текущий матч",
                "&7и краткое объяснение формата."));
        inventory.setItem(16, headDbItem(SPECTATE_HEAD_ID, Material.ENDER_EYE, "&bСледить за турниром",
                "&7Попытаться наблюдать активный матч",
                "&7этого турнира."));
        inventory.setItem(22, item(Material.ARROW, "&7Закрыть", "&7Закрыть меню."));
        player.openInventory(inventory);
    }

    private void openTournamentList(final Player player) {
        final Inventory inventory = Bukkit.createInventory(new TournamentListMenu(), 54, StringUtil.color("&6Турниры: управление"));
        fill(inventory);
        inventory.setItem(4, item(Material.WRITABLE_BOOK, "&6&lПанель организатора",
                "&7Выберите турнир ниже, чтобы открыть",
                "&7быстрые действия без длинных команд.",
                " ",
                "&eСоздание нового турнира:",
                "&f/tour create <name> <fixed|choice>",
                "&7Кит выберите ниже в настройках турнира."));

        final List<Tournament> tournaments = tournamentManager.getTournaments();
        final int[] slots = tournamentSlots();
        if (tournaments.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, "&cТурниров нет",
                    "&7Создайте первый турнир:",
                    "&f/tour create Nether choice"));
        } else {
            for (int i = 0; i < tournaments.size() && i < slots.length; i++) {
                final Tournament tournament = tournaments.get(i);
                inventory.setItem(slots[i], tournamentKitItem(tournament, "&e" + tournament.getName(),
                        effectiveKitLine(tournament),
                        effectiveTypeLine(tournament),
                        "&7Китов разрешено: &f" + listState(tournament.getAllowedKits()),
                        "&7Арен разрешено: &f" + listState(tournament.getAllowedArenas()),
                        "&7Статус: " + tournamentManager.formatTournamentStatus(tournament.getStatus()),
                        "&7Участников: &f" + tournament.getPlayers().size(),
                        " ",
                        "&aЛКМ: &7открыть управление"));
            }
        }

        inventory.setItem(49, item(Material.ARROW, "&7Закрыть", "&7Закрыть меню."));
        player.openInventory(inventory);
    }

    private void openTournamentActions(final Player player, final Tournament tournament) {
        final Inventory inventory = Bukkit.createInventory(new TournamentActionMenu(tournament.getName()), 54,
                StringUtil.color("&6Турнир: &e" + tournament.getName()));
        fill(inventory);
        inventory.setItem(4, tournamentKitItem(tournament, "&6&l" + tournament.getName(),
                effectiveKitLine(tournament),
                effectiveTypeLine(tournament),
                "&7Китов разрешено: &f" + listState(tournament.getAllowedKits()),
                "&7Арен разрешено: &f" + listState(tournament.getAllowedArenas()),
                "&7Статус: " + tournamentManager.formatTournamentStatus(tournament.getStatus()),
                "&7Участников: &f" + tournament.getPlayers().size(),
                "&7Раунд: &f" + tournament.getCurrentRound()));

        inventory.setItem(10, headDbItem(RULES_HEAD_ID, Material.BOOK, "&eИнформация",
                "&7Участники, сетка и состояние", "&7матчей этого турнира."));
        inventory.setItem(12, item(Material.LIME_CONCRETE, "&aЗапустить турнир",
                "&7Формирует сетку и открывает", "&7первый раунд."));
        inventory.setItem(14, headDbItem(SPECTATE_HEAD_ID, Material.ENDER_EYE, "&bЗапустить следующую пару",
                "&7Запускает ближайший готовый", "&7матч турнира."));
        inventory.setItem(16, item(Material.WRITABLE_BOOK, "&6Сбросить сетку",
                "&7Возвращает турнир к регистрации.", "&eУчастники сохраняются."));

        inventory.setItem(20, item(tournament.getKitMode() == TournamentKitMode.PLAYER_CHOICE ? Material.ENDER_EYE : Material.ANVIL,
                "&eРежим кита",
                "&7Сейчас: " + formatKitMode(tournament.getKitMode()),
                " ",
                "&fФиксированный кит&7: один кит турнира.",
                "&aВыбор игроками&7: выбор перед матчем.",
                " ",
                tournament.getStatus() == TournamentStatus.CREATED ? "&eНажмите, чтобы переключить." : "&cМожно менять только до запуска."));
        inventory.setItem(21, item(Material.CHEST, "&aРазрешённые киты",
                "&7Настройка доступных китов.",
                "&7Сейчас: &f" + listState(tournament.getAllowedKits()),
                " ",
                tournament.getStatus() == TournamentStatus.CREATED ? "&eНажмите, чтобы настроить." : "&cМожно менять только до запуска."));
        inventory.setItem(22, item(Material.FILLED_MAP, "&bРазрешённые арены",
                "&7Настройка карт турнира.",
                "&7Сейчас: &f" + listState(tournament.getAllowedArenas()),
                " ",
                tournament.getStatus() == TournamentStatus.CREATED ? "&eНажмите, чтобы настроить." : "&cМожно менять только до запуска."));
        inventory.setItem(24, item(Material.VILLAGER_SPAWN_EGG, "&aNPC турнира",
                "&7Привязка FancyNPC к турниру.",
                "&7Привязано: &f" + tournamentManager.getFancyNpcBindings(tournament.getName()).size(),
                " ",
                "&eНажмите, чтобы настроить."));
        inventory.setItem(25, item(Material.COMPARATOR, "&dНастройка голограммы",
                "&7Создание, перенос, обновление",
                "&7и удаление панелей."));

        inventory.setItem(30, headDbItem(CANCEL_HEAD_ID, Material.RED_DYE, "&cОтменить турнир",
                "&7Останавливает турнир,", "&7но не удаляет его."));
        inventory.setItem(32, item(Material.REDSTONE_BLOCK, "&cЗавершить турнир",
                "&7Принудительно завершает турнир", "&7и освобождает арену."));
        inventory.setItem(34, item(Material.BARRIER, "&4Удалить турнир",
                "&7Полностью удаляет турнир.", "&cДействие необратимо."));
        inventory.setItem(49, item(Material.ARROW, "&7Назад", "&7Вернуться к списку турниров."));
        player.openInventory(inventory);
    }

    private void openTournamentNpcSettings(final Player player, final Tournament tournament) {
        final Inventory inventory = Bukkit.createInventory(new TournamentNpcMenu(tournament.getName()), 36,
                StringUtil.color("&aNPC турнира: &e" + tournament.getName()));
        fill(inventory);
        final String suggestedName = tournamentManager.suggestFancyNpcName(tournament.getName());
        final List<String> bindings = tournamentManager.getFancyNpcBindings(tournament.getName());
        inventory.setItem(4, item(Material.VILLAGER_SPAWN_EGG, "&a&lNPC турнира",
                "&7Турнир: &e" + tournament.getName(),
                "&7NPC можно назвать как угодно.",
                "&7Привязано NPC: &f" + bindings.size(),
                " ",
                bindings.isEmpty() ? "&7Текущие привязки: &cнет" : "&7Текущие привязки: &f" + String.join("&7, &f", bindings)));
        inventory.setItem(10, item(Material.NAME_TAG, "&aПривязать свой NPC",
                "&7Нажмите и введите в чат любое",
                "&7имя или id уже созданного FancyNPC.",
                " ",
                "&7Например: &fTournament",
                "&7или: &fМойТурнирныйNPC",
                " ",
                "&eЛКМ: начать ввод."));
        inventory.setItem(12, item(Material.PAPER, "&eБыстрая подсказка",
                "&7Можно использовать любое имя NPC.",
                "&7Рекомендуемое имя, если нужно:",
                "&f" + suggestedName,
                " ",
                "&7Ручная команда остаётся:",
                "&f/tour npc bind " + tournament.getName() + " <npcNameOrId>"));
        inventory.setItem(14, item(Material.BOOK, "&bПоказать привязки в чат",
                "&7Выведет все NPC, которые сейчас",
                "&7ведут в этот турнир."));
        inventory.setItem(16, item(Material.RED_DYE, "&cОтвязать NPC турнира",
                "&7Удаляет все NPC-привязки",
                "&7только для этого турнира."));
        inventory.setItem(31, item(Material.ARROW, "&7Назад", "&7Вернуться к управлению турниром."));
        player.openInventory(inventory);
    }

    private void openTournamentHologramSettings(final Player player, final Tournament tournament) {
        final Inventory inventory = Bukkit.createInventory(new TournamentHologramMenu(tournament.getName()), 36,
                StringUtil.color("&dГолограмма: &e" + tournament.getName()));
        fill(inventory);
        inventory.setItem(4, item(Material.COMPARATOR, "&d&lНастройки голограммы",
                "&7Расстояние между панелями: &f5 блоков",
                "&7Панели стоят на одной плоскости."));
        inventory.setItem(10, item(Material.NAME_TAG, "&aСоздать/перенести сюда",
                "&7Создаёт три CMI-голограммы",
                "&7или переносит их к вашему взгляду."));
        inventory.setItem(12, item(Material.CLOCK, "&bОбновить",
                "&7Перерисовывает строки, позицию",
                "&7и расстояние CMI-голограмм."));
        inventory.setItem(14, item(Material.RED_DYE, "&cУдалить голограмму",
                "&7Удаляет все панели выбранного турнира."));
        inventory.setItem(31, item(Material.ARROW, "&7Назад", "&7Вернуться к управлению турниром."));
        player.openInventory(inventory);
    }

    private void openTournamentKitLimits(final Player player, final Tournament tournament) {
        final Inventory inventory = Bukkit.createInventory(new TournamentKitLimitMenu(tournament.getName()), 54,
                StringUtil.color("&aКиты турнира: &e" + tournament.getName()));
        fill(inventory);
        inventory.setItem(4, item(Material.CHEST, "&a&lРазрешённые киты",
                "&7Пустой список: игроки видят",
                "&7общий пул китов Duels.",
                "&7Сейчас: &f" + listState(tournament.getAllowedKits())));

        final int[] slots = tournamentSlots();
        final List<String> kits = plugin.getKitManager().getNames(false);
        for (int i = 0; i < kits.size() && i < slots.length; i++) {
            final String kitName = kits.get(i);
            final boolean enabled = containsIgnoreCase(tournament.getAllowedKits(), kitName);
            final var kit = plugin.getKitManager().get(kitName);
            final ItemStack icon = kit == null ? new ItemStack(Material.CHEST) : kit.getDisplayed();
            inventory.setItem(slots[i], namedItem(icon, (enabled ? "&a" : "&7") + kitName,
                    List.of(enabled ? "&aРазрешён для этого турнира." : "&7Выключен в списке турнира.",
                            "&eЛКМ: переключить",
                            tournament.getStatus() == TournamentStatus.CREATED ? "&7Можно менять до запуска." : "&cТурнир уже запущен.")));
        }

        inventory.setItem(45, item(Material.ARROW, "&7Назад", "&7Вернуться к управлению турниром."));
        inventory.setItem(49, item(Material.REDSTONE_BLOCK, "&cОчистить ограничения",
                "&7После очистки будет использоваться",
                "&7общий пул китов Duels."));
        player.openInventory(inventory);
    }

    private void openTournamentArenaLimits(final Player player, final Tournament tournament) {
        final Inventory inventory = Bukkit.createInventory(new TournamentArenaLimitMenu(tournament.getName()), 54,
                StringUtil.color("&bАрены турнира: &e" + tournament.getName()));
        fill(inventory);
        inventory.setItem(4, item(Material.FILLED_MAP, "&b&lРазрешённые арены",
                "&7Пустой список: Duels сам выберет",
                "&7подходящую арену по киту.",
                "&7Сейчас: &f" + listState(tournament.getAllowedArenas())));

        final int[] slots = tournamentSlots();
        final List<String> arenas = plugin.getArenaManager().getNames();
        for (int i = 0; i < arenas.size() && i < slots.length; i++) {
            final String arenaName = arenas.get(i);
            final boolean enabled = containsIgnoreCase(tournament.getAllowedArenas(), arenaName);
            final var arena = plugin.getArenaManager().get(arenaName);
            final ItemStack icon = arena == null ? new ItemStack(Material.FILLED_MAP) : arena.getDisplayedOriginal();
            inventory.setItem(slots[i], namedItem(icon, (enabled ? "&a" : "&7") + arenaName,
                    List.of(enabled ? "&aРазрешена для этого турнира." : "&7Выключена в списке турнира.",
                            "&eЛКМ: переключить",
                            tournament.getStatus() == TournamentStatus.CREATED ? "&7Можно менять до запуска." : "&cТурнир уже запущен.")));
        }

        inventory.setItem(45, item(Material.ARROW, "&7Назад", "&7Вернуться к управлению турниром."));
        inventory.setItem(49, item(Material.REDSTONE_BLOCK, "&cОчистить ограничения",
                "&7После очистки Duels будет выбирать",
                "&7подходящую арену сам."));
        player.openInventory(inventory);
    }

    private void handleTournamentListClick(final Player player, final int slot) {
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        final int[] slots = tournamentSlots();
        final List<Tournament> tournaments = tournamentManager.getTournaments();
        for (int i = 0; i < slots.length && i < tournaments.size(); i++) {
            if (slot == slots[i]) {
                openTournamentActions(player, tournaments.get(i));
                return;
            }
        }
    }

    private void handleTournamentActionClick(final Player player, final String tournamentName, final int slot) {
        final Tournament tournament = tournamentManager.getTournament(tournamentName);
        if (slot == 49) {
            openTournamentList(player);
            return;
        }
        if (tournament == null) {
            player.closeInventory();
            message(player, "&cТурнир не найден.");
            return;
        }

        switch (slot) {
            case 10 -> {
                player.closeInventory();
                tournamentManager.describe(tournament).forEach(line -> message(player, line));
            }
            case 12 -> {
                start(player, new String[]{"start", tournamentName});
                openTournamentActions(player, tournament);
            }
            case 14 -> {
                player.closeInventory();
                message(player, "&8&m                                                ");
                message(player, "&6&lПодтверждение запуска пары");
                message(player, "&7Команда не выполнена сразу, чтобы избежать случайного клика.");
                sendConfirmableCommand(player, "&7Подтвердить: ", "/tour nextmatch " + tournamentName);
                message(player, "&8&m                                                ");
            }
            case 16 -> {
                reset(player, new String[]{"reset", tournamentName});
                openTournamentActions(player, tournament);
            }
            case 30 -> {
                cancel(player, new String[]{"cancel", tournamentName});
                openTournamentActions(player, tournament);
            }
            case 32 -> {
                finish(player, new String[]{"finish", tournamentName});
                openTournamentActions(player, tournament);
            }
            case 25 -> {
                openTournamentHologramSettings(player, tournament);
            }
            case 24 -> openTournamentNpcSettings(player, tournament);
            case 20 -> {
                final TournamentKitMode nextMode = tournament.getKitMode() == TournamentKitMode.PLAYER_CHOICE
                        ? TournamentKitMode.FIXED
                        : TournamentKitMode.PLAYER_CHOICE;
                switch (tournamentManager.setKitMode(tournamentName, nextMode)) {
                    case SUCCESS -> message(player, "&aРежим кита: " + formatKitMode(nextMode));
                    case NOT_FOUND -> message(player, "&cТурнир не найден.");
                    case LOCKED -> message(player, "&cРежим можно менять только до запуска турнира.");
                }
                openTournamentActions(player, tournament);
            }
            case 21 -> openTournamentKitLimits(player, tournament);
            case 22 -> openTournamentArenaLimits(player, tournament);
            case 34 -> {
                delete(player, new String[]{"delete", tournamentName});
                openTournamentList(player);
            }
            default -> {
            }
        }
    }

    private void handleTournamentNpcClick(final Player player, final String tournamentName, final int slot) {
        final Tournament tournament = tournamentManager.getTournament(tournamentName);
        if (slot == 31) {
            if (tournament == null) {
                openTournamentList(player);
            } else {
                openTournamentActions(player, tournament);
            }
            return;
        }
        if (tournament == null) {
            player.closeInventory();
            message(player, "&cТурнир не найден.");
            return;
        }

        switch (slot) {
            case 10 -> {
                pendingNpcBindings.put(player.getUniqueId(), tournament.getName());
                player.closeInventory();
                message(player, "&8&m                                                ");
                message(player, "&6&lПривязка NPC к турниру");
                message(player, "&7Турнир: &e" + tournament.getName());
                message(player, "&7Введите в чат &fимя или id FancyNPC&7, который должен открывать этот турнир.");
                message(player, "&7Например: &fTournament &7или &fPvP Arena");
                message(player, "&7Отмена: &ccancel");
                message(player, "&8&m                                                ");
            }
            case 14 -> {
                player.closeInventory();
                final List<String> bindings = tournamentManager.getFancyNpcBindings(tournament.getName());
                message(player, "&8&m                                                ");
                message(player, "&6&lNPC турнира &e" + tournament.getName());
                if (bindings.isEmpty()) {
                    message(player, "&eNPC ещё не привязаны.");
                    message(player, "&7Рекомендуемое имя: &f" + tournamentManager.suggestFancyNpcName(tournament.getName()));
                } else {
                    bindings.forEach(npc -> message(player, "&7- &f" + npc));
                }
                message(player, "&8&m                                                ");
            }
            case 16 -> {
                final int removed = tournamentManager.unbindFancyNpcs(tournament.getName());
                message(player, removed > 0
                        ? "&aОтвязано NPC: &f" + removed
                        : "&eУ этого турнира не было NPC-привязок.");
                openTournamentNpcSettings(player, tournament);
            }
            default -> {
            }
        }
    }

    private void handleTournamentHologramClick(final Player player, final String tournamentName, final int slot) {
        final Tournament tournament = tournamentManager.getTournament(tournamentName);
        if (slot == 31) {
            if (tournament == null) {
                openTournamentList(player);
            } else {
                openTournamentActions(player, tournament);
            }
            return;
        }
        if (tournament == null) {
            player.closeInventory();
            message(player, "&cТурнир не найден.");
            return;
        }

        switch (slot) {
            case 10 -> {
                message(player, tournamentManager.createOrSetHologram(player, tournamentName)
                        ? "&aГолограмма создана/перенесена и обновлена."
                        : "&cТурнир не найден или CMI-голограммы выключены.");
                openTournamentHologramSettings(player, tournament);
            }
            case 12 -> {
                tournamentManager.updateHologram(tournamentName);
                successBlock(player, "Голограмма обновлена", List.of("&7Турнир: &e" + tournamentName));
                openTournamentHologramSettings(player, tournament);
            }
            case 14 -> {
                message(player, tournamentManager.removeHologram(tournamentName)
                        ? "&aГолограмма удалена."
                        : "&cГолограмма или турнир не найден.");
                openTournamentHologramSettings(player, tournament);
            }
            default -> {
            }
        }
    }

    private void handleTournamentKitLimitClick(final Player player, final String tournamentName, final int slot) {
        final Tournament tournament = tournamentManager.getTournament(tournamentName);
        if (slot == 45) {
            if (tournament == null) {
                openTournamentList(player);
            } else {
                openTournamentActions(player, tournament);
            }
            return;
        }
        if (tournament == null) {
            player.closeInventory();
            message(player, "&cТурнир не найден.");
            return;
        }
        if (slot == 49) {
            handleListChange(player, tournamentManager.clearAllowedKits(tournamentName),
                    "&aОграничения китов очищены.", "&cКит не найден.");
            openTournamentKitLimits(player, tournament);
            return;
        }

        final String kitName = valueBySlot(slot, plugin.getKitManager().getNames(false));
        if (kitName == null) {
            return;
        }
        handleListChange(player, tournamentManager.toggleAllowedKit(tournamentName, kitName),
                containsIgnoreCase(tournament.getAllowedKits(), kitName)
                        ? "&aКит &f" + kitName + " &aразрешён для турнира."
                        : "&eКит &f" + kitName + " &eвыключен для турнира.",
                "&cКит не найден.");
        openTournamentKitLimits(player, tournament);
    }

    private void handleTournamentArenaLimitClick(final Player player, final String tournamentName, final int slot) {
        final Tournament tournament = tournamentManager.getTournament(tournamentName);
        if (slot == 45) {
            if (tournament == null) {
                openTournamentList(player);
            } else {
                openTournamentActions(player, tournament);
            }
            return;
        }
        if (tournament == null) {
            player.closeInventory();
            message(player, "&cТурнир не найден.");
            return;
        }
        if (slot == 49) {
            handleListChange(player, tournamentManager.clearAllowedArenas(tournamentName),
                    "&aОграничения арен очищены.", "&cАрена не найдена.");
            openTournamentArenaLimits(player, tournament);
            return;
        }

        final String arenaName = valueBySlot(slot, plugin.getArenaManager().getNames());
        if (arenaName == null) {
            return;
        }
        handleListChange(player, tournamentManager.toggleAllowedArena(tournamentName, arenaName),
                containsIgnoreCase(tournament.getAllowedArenas(), arenaName)
                        ? "&aАрена &f" + arenaName + " &aразрешена для турнира."
                        : "&eАрена &f" + arenaName + " &eвыключена для турнира.",
                "&cАрена не найдена.");
        openTournamentArenaLimits(player, tournament);
    }

    private void handlePlayerMenuClick(final Player player, final String tournamentName, final int slot) {
        switch (slot) {
            case 10 -> {
                join(player, new String[]{"join", tournamentName});
                openPlayerMenu(player, tournamentName);
            }
            case 12 -> {
                leave(player, new String[]{"leave", tournamentName});
                openPlayerMenu(player, tournamentName);
            }
            case 14 -> {
                player.closeInventory();
                tournamentManager.describePlayerMatch(player).forEach(line -> message(player, line));
                message(player, "&8&m                                                ");
                message(player, "&6&lКак проходит турнир");
                message(player, "&7Формат: &fолимпийская сетка на выбывание.");
                message(player, "&7Победитель проходит дальше, проигравший выбывает.");
                message(player, "&7Организатор запускает пары по очереди, когда игроки готовы.");
                message(player, "&8&m                                                ");
            }
            case 16 -> {
                spectate(player, new String[]{"spec", tournamentName});
                openPlayerMenu(player, tournamentName);
            }
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    private int[] tournamentSlots() {
        return new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    }

    private String valueBySlot(final int slot, final List<String> values) {
        final int[] slots = tournamentSlots();
        for (int i = 0; i < slots.length && i < values.size(); i++) {
            if (slot == slots[i]) {
                return values.get(i);
            }
        }
        return null;
    }

    private boolean containsIgnoreCase(final List<String> values, final String value) {
        return values.stream().anyMatch(existing -> existing.equalsIgnoreCase(value));
    }

    private String listState(final List<String> values) {
        return values.isEmpty() ? "без ограничений" : String.valueOf(values.size());
    }

    private ItemStack item(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(StringUtil.color(name));
            meta.setLore(StringUtil.color(Arrays.asList(lore)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack headDbItem(final int headId, final Material fallback, final String name, final String... lore) {
        return namedItem(headDbItems.getOrDefault(headId, new ItemStack(fallback)), name, Arrays.asList(lore));
    }

    private ItemStack tournamentKitItem(final Tournament tournament, final String name, final String... lore) {
        final var kit = plugin.getKitManager().get(effectiveKitIconName(tournament));
        return namedItem(kit == null ? new ItemStack(Material.NETHER_STAR) : kit.getDisplayed(), name, Arrays.asList(lore));
    }

    private ItemStack namedItem(final ItemStack source, final String name, final List<String> lore) {
        final ItemStack item = source.clone();
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(StringUtil.color(name));
            meta.setLore(StringUtil.color(new ArrayList<>(lore)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void preloadHeadDbItems() {
        if (!(Bukkit.getPluginManager().getPlugin("HeadDB") instanceof HeadDB headDb)) {
            return;
        }
        final HeadAPI api = headDb.getHeadApi();
        if (api == null) {
            return;
        }
        api.onReady().thenAccept(heads -> Bukkit.getScheduler().runTask(plugin, () -> cacheHeadDbItems(heads)));
    }

    private void cacheHeadDbItems(final List<Head> heads) {
        final Set<Integer> targetIds = new HashSet<>(Arrays.asList(TROPHY_HEAD_ID, CANCEL_HEAD_ID, RULES_HEAD_ID, SPECTATE_HEAD_ID));
        for (Head head : heads) {
            if (!targetIds.contains(head.getId())) {
                continue;
            }
            try {
                final ItemStack item = head.getItem();
                if (item != null) {
                    headDbItems.put(head.getId(), item.clone());
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load HeadDB head " + head.getId() + " for tournament menu: " + ex.getMessage());
            }
        }
    }

    private void fill(final Inventory inventory) {
        final ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private record TournamentListMenu() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TournamentActionMenu(String tournament) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TournamentHologramMenu(String tournament) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TournamentNpcMenu(String tournament) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TournamentKitLimitMenu(String tournament) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TournamentArenaLimitMenu(String tournament) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TournamentPlayerMenu(String tournament) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) {
            return filter(args[0], rootCompletions(sender));
        }
        if (args.length == 2 && Arrays.asList("kitmode", "mode", "kits", "kitlimit", "arenas", "arenalimit", "delete", "add", "join", "leave", "remove", "start", "next", "nextmatch", "cancel", "reset", "finish", "info", "win", "replay", "menu", "player", "playermenu").contains(args[0].toLowerCase())) {
            return filter(args[1], tournamentNames());
        }
        if (args.length == 3 && ("kits".equalsIgnoreCase(args[0]) || "kitlimit".equalsIgnoreCase(args[0])
                || "arenas".equalsIgnoreCase(args[0]) || "arenalimit".equalsIgnoreCase(args[0]))) {
            return filter(args[2], "list", "toggle", "clear");
        }
        if (args.length == 4 && ("kits".equalsIgnoreCase(args[0]) || "kitlimit".equalsIgnoreCase(args[0]))
                && "toggle".equalsIgnoreCase(args[2])) {
            return filter(args[3], kitManager.getNames(false));
        }
        if (args.length == 4 && ("arenas".equalsIgnoreCase(args[0]) || "arenalimit".equalsIgnoreCase(args[0]))
                && "toggle".equalsIgnoreCase(args[2])) {
            return filter(args[3], plugin.getArenaManager().getNames());
        }
        if (args.length == 3 && "replay".equalsIgnoreCase(args[0])) {
            return filter(args[2], "1", "2", "3", "4", "5");
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            return List.of("<name>");
        }
        if (args.length == 3 && "create".equalsIgnoreCase(args[0])) {
            final List<String> values = new ArrayList<>(List.of("choice", "fixed"));
            values.addAll(kitManager.getNames(false));
            return filter(args[2], values);
        }
        if (args.length == 4 && "create".equalsIgnoreCase(args[0])) {
            return filter(args[3], "fixed", "choice");
        }
        if (args.length == 3 && ("kitmode".equalsIgnoreCase(args[0]) || "mode".equalsIgnoreCase(args[0]))) {
            return filter(args[2], "fixed", "choice");
        }
        if (args.length == 2 && "holo".equalsIgnoreCase(args[0])) {
            return filter(args[1], "list", "create", "set", "remove", "update", "page", "round", "direction", "next", "prev");
        }
        if (args.length == 3 && "holo".equalsIgnoreCase(args[0]) && !"list".equalsIgnoreCase(args[1])) {
            return filter(args[2], tournamentNames());
        }
        if (args.length == 4 && "holo".equalsIgnoreCase(args[0]) && "direction".equalsIgnoreCase(args[1])) {
            return filter(args[3], "auto", "north", "east", "south", "west");
        }
        if (args.length == 2 && "npc".equalsIgnoreCase(args[0])) {
            return filter(args[1], "name", "bind", "unbind", "list");
        }
        if (args.length == 3 && "npc".equalsIgnoreCase(args[0]) && ("name".equalsIgnoreCase(args[1]) || "bind".equalsIgnoreCase(args[1]))) {
            return filter(args[2], tournamentNames());
        }
        if (args.length == 2 && ("spec".equalsIgnoreCase(args[0]) || "spectate".equalsIgnoreCase(args[0]))) {
            return filter(args[1], tournamentNamesWithSpec());
        }
        if (args.length == 3 && ("spec".equalsIgnoreCase(args[0]) || "spectate".equalsIgnoreCase(args[0]))
                && "list".equalsIgnoreCase(args[1])) {
            return filter(args[2], tournamentNames());
        }
        if (args.length == 2 && "help".equalsIgnoreCase(args[0])) {
            return filter(args[1], "player", "admin", "match", "npc", "holo", "spec");
        }
        if (args.length == 2 && "match".equalsIgnoreCase(args[0])) {
            return filter(args[1], "start");
        }
        if (args.length == 3 && "match".equalsIgnoreCase(args[0]) && "start".equalsIgnoreCase(args[1])) {
            return filter(args[2], tournamentNames());
        }
        return null;
    }

    private List<String> rootCompletions(final CommandSender sender) {
        final List<String> commands = new ArrayList<>(List.of("help", "menu", "join", "leave", "info", "mymatch"));
        if (sender.hasPermission(Permissions.TOURNAMENT_SPECTATE)
                || sender.hasPermission(Permissions.TOURNAMENT_SPECTATE_ADMIN)
                || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("spec");
            commands.add("spectate");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_CREATE) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.addAll(List.of("create", "kitmode", "mode", "kits", "kitlimit", "arenas", "arenalimit", "reset"));
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_DELETE) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("delete");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_ADD) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("add");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_REMOVE) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("remove");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_START) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.addAll(List.of("start", "next", "nextmatch", "match"));
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_CANCEL) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("cancel");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_FINISH) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("finish");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_WIN) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("win");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_REPLAY) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("replay");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_HOLO) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("holo");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_SET_LOBBY) || sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("setlobby");
        }
        if (sender.hasPermission(Permissions.TOURNAMENT_ADMIN)) {
            commands.add("npc");
        }
        return commands;
    }

    private List<String> tournamentNames() {
        return tournamentManager.getTournaments().stream().map(Tournament::getName).collect(Collectors.toList());
    }

    private List<String> tournamentNamesWithSpec() {
        final List<String> names = new ArrayList<>(tournamentNames());
        names.add("list");
        names.add("current");
        return names;
    }

    private List<String> filter(final String input, final String... values) {
        return filter(input, Arrays.asList(values));
    }

    private List<String> filter(final String input, final List<String> values) {
        return values.stream()
                .filter(value -> value.toLowerCase().startsWith(input.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }
}
