package com.meteordevelopments.duels.tournament;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.api.event.match.MatchEndEvent;
import com.meteordevelopments.duels.api.folialib.task.WrappedTask;
import com.meteordevelopments.duels.api.event.match.MatchEndEvent.Reason;
import com.meteordevelopments.duels.api.event.match.MatchStartEvent;
import com.meteordevelopments.duels.api.match.Match;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.core.kit.KitImpl;
import com.meteordevelopments.duels.core.match.DuelMatch;
import com.meteordevelopments.duels.core.player.PlayerInfo;
import com.meteordevelopments.duels.data.LocationData;
import com.meteordevelopments.duels.setting.Settings;
import com.meteordevelopments.duels.util.Loadable;
import com.meteordevelopments.duels.util.StringUtil;
import com.meteordevelopments.duels.util.TextBuilder;
import com.meteordevelopments.duels.util.io.AtomicFileWriter;
import com.meteordevelopments.duels.util.io.LatestSnapshotWriter;
import com.meteordevelopments.duels.util.json.JsonUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TournamentManager implements Loadable, Listener {

    private final DuelsPlugin plugin;
    private final Map<String, Tournament> tournaments = new LinkedHashMap<>();
    private final Map<Match, ActiveTournamentMatch> activeMatches = new HashMap<>();
    private final Map<String, PendingKitSelection> pendingKitSelections = new HashMap<>();
    private final Map<String, Deque<String>> playerKitHistory = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final TournamentBracketService bracketService = new TournamentBracketService();
    private final TournamentHologramRenderer hologramRenderer = new TournamentHologramRenderer();
    private final File dataFile;
    private final File configFile;
    private final File lobbyFile;
    private TournamentSettings settings;
    private Location lobby;
    private WrappedTask autoForfeitTask;
    private LatestSnapshotWriter<String> dataWriter;

    public TournamentManager(final DuelsPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tournaments.yml");
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.lobbyFile = new File(plugin.getDataFolder(), "tournament-lobby.json");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void handleLoad() {
        dataWriter = new LatestSnapshotWriter<>(plugin::doAsync, this::writeData,
                ex -> plugin.getLogger().warning("Could not save tournaments.yml: " + ex.getMessage()));
        settings = TournamentSettings.load(YamlConfiguration.loadConfiguration(configFile));
        loadLobby();
        loadData();
        tournaments.values().forEach(this::refreshHologramAfterLoad);
        autoForfeitTask = plugin.doSyncRepeat(this::checkAutoForfeits, 20L, 20L * 30L);
    }

    @Override
    public void handleUnload() {
        if (autoForfeitTask != null && !autoForfeitTask.isCancelled()) {
            plugin.cancelTask(autoForfeitTask);
        }
        autoForfeitTask = null;
        new ArrayList<>(pendingKitSelections.values()).forEach(this::cleanupKitSelection);
        saveDataNow();
        dataWriter = null;
        tournaments.clear();
        activeMatches.clear();
        pendingKitSelections.clear();
    }

    public TournamentSettings getSettings() {
        return settings;
    }

    public List<Tournament> getTournaments() {
        return new ArrayList<>(tournaments.values());
    }

    public Tournament getTournament(final String name) {
        return tournaments.get(normalize(name));
    }

    public NpcBindResult bindFancyNpc(final String tournamentName, final String npcKey) {
        final Tournament tournament = getTournament(tournamentName);
        if (tournament == null) {
            return NpcBindResult.NOT_FOUND;
        }
        if (npcKey == null || npcKey.isBlank()) {
            return NpcBindResult.INVALID_NPC;
        }
        settings.fancyNpcTournaments.put(npcKey.toLowerCase(Locale.ROOT), tournament.getName());
        saveFancyNpcBindings();
        return NpcBindResult.SUCCESS;
    }

    public boolean unbindFancyNpc(final String npcKey) {
        if (npcKey == null || npcKey.isBlank()) {
            return false;
        }
        final String removed = settings.fancyNpcTournaments.remove(npcKey.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        saveFancyNpcBindings();
        return true;
    }

    public List<String> describeFancyNpcBindings() {
        if (settings.fancyNpcTournaments.isEmpty()) {
            return List.of("&eNPC турниров не привязаны.");
        }
        final List<String> lines = new ArrayList<>();
        lines.add("&6NPC турниров:");
        settings.fancyNpcTournaments.forEach((npc, tournament) -> lines.add("&7- &f" + npc + " &8-> &e" + tournament));
        return lines;
    }

    public List<String> getFancyNpcBindings(final String tournamentName) {
        final Tournament tournament = getTournament(tournamentName);
        if (tournament == null) {
            return List.of();
        }
        return settings.fancyNpcTournaments.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(tournament.getName()))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    public Tournament getPlayerOpenTournament(final String player) {
        return findPlayerOpenTournament(player);
    }

    public int unbindFancyNpcs(final String tournamentName) {
        final Tournament tournament = getTournament(tournamentName);
        if (tournament == null) {
            return 0;
        }
        final int before = settings.fancyNpcTournaments.size();
        settings.fancyNpcTournaments.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(tournament.getName()));
        final int removed = before - settings.fancyNpcTournaments.size();
        if (removed > 0) {
            saveFancyNpcBindings();
        }
        return removed;
    }

    public String suggestFancyNpcName(final String tournamentName) {
        return "tour_" + tournamentName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private void saveFancyNpcBindings() {
        final FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        final String path = "tournament.fancy-npcs.npc-tournaments";
        config.set(path, null);
        settings.fancyNpcTournaments.forEach((npc, tournament) -> config.set(path + "." + npc, tournament));
        try {
            config.save(configFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save tournament NPC bindings: " + ex.getMessage());
        }
    }

    public CreateResult create(final String name, final String kit) {
        return create(name, kit, TournamentKitMode.FIXED);
    }

    public CreateResult create(final String name, final TournamentKitMode kitMode) {
        if (getTournament(name) != null) {
            return CreateResult.ALREADY_EXISTS;
        }

        final Tournament tournament = new Tournament(name, "");
        tournament.setKitMode(kitMode == null ? TournamentKitMode.FIXED : kitMode);
        tournaments.put(normalize(name), tournament);
        changed(name);
        return CreateResult.SUCCESS;
    }

    public CreateResult create(final String name, final String kit, final TournamentKitMode kitMode) {
        if (getTournament(name) != null) {
            return CreateResult.ALREADY_EXISTS;
        }
        if (plugin.getKitManager().get(kit) == null) {
            return CreateResult.NO_KIT;
        }

        final Tournament tournament = new Tournament(name, kit);
        tournament.setKitMode(kitMode == null ? TournamentKitMode.FIXED : kitMode);
        tournaments.put(normalize(name), tournament);
        changed(name);
        return CreateResult.SUCCESS;
    }

    public SetKitModeResult setKitMode(final String name, final TournamentKitMode kitMode) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return SetKitModeResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return SetKitModeResult.LOCKED;
        }

        tournament.setKitMode(kitMode == null ? TournamentKitMode.FIXED : kitMode);
        syncKitModeWithAllowedKits(tournament);
        changed(tournament);
        return SetKitModeResult.SUCCESS;
    }

    public TournamentListChangeResult toggleAllowedKit(final String name, final String kitName) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return TournamentListChangeResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return TournamentListChangeResult.LOCKED;
        }
        if (plugin.getKitManager().get(kitName) == null) {
            return TournamentListChangeResult.INVALID_VALUE;
        }

        toggleValue(tournament.getAllowedKits(), kitName);
        syncKitModeWithAllowedKits(tournament);
        changed(tournament);
        return TournamentListChangeResult.SUCCESS;
    }

    public TournamentListChangeResult clearAllowedKits(final String name) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return TournamentListChangeResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return TournamentListChangeResult.LOCKED;
        }

        tournament.getAllowedKits().clear();
        changed(tournament);
        return TournamentListChangeResult.SUCCESS;
    }

    public TournamentListChangeResult toggleAllowedArena(final String name, final String arenaName) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return TournamentListChangeResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return TournamentListChangeResult.LOCKED;
        }
        if (plugin.getArenaManager().get(arenaName) == null) {
            return TournamentListChangeResult.INVALID_VALUE;
        }

        toggleValue(tournament.getAllowedArenas(), arenaName);
        changed(tournament);
        return TournamentListChangeResult.SUCCESS;
    }

    public TournamentListChangeResult clearAllowedArenas(final String name) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return TournamentListChangeResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return TournamentListChangeResult.LOCKED;
        }

        tournament.getAllowedArenas().clear();
        changed(tournament);
        return TournamentListChangeResult.SUCCESS;
    }

    public boolean delete(final String name) {
        final Tournament removed = getTournament(name);
        if (removed == null) {
            return false;
        }

        cleanupPendingSelections(removed, ignored -> true);
        terminateActiveMatches(removed, ignored -> true);
        releaseTournamentArena(removed);
        tournaments.remove(normalize(name));
        removeTournamentHologram(removed, false);
        saveData();
        return true;
    }

    public AddResult addPlayer(final String name, final String player) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return AddResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return AddResult.LOCKED;
        }
        if (tournament.hasParticipant(player)) {
            return AddResult.ALREADY_ADDED;
        }

        tournament.getPlayers().add(player);
        changed(tournament);
        return AddResult.SUCCESS;
    }

    public boolean removePlayer(final String name, final String player) {
        final Tournament tournament = getTournament(name);
        if (tournament == null || tournament.getStatus() != TournamentStatus.CREATED) {
            return false;
        }

        final boolean removed = tournament.getPlayers().removeIf(value -> value.equalsIgnoreCase(player));
        if (removed) {
            changed(tournament);
        }
        return removed;
    }

    public AddResult joinPlayer(final String name, final Player player) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return AddResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return AddResult.LOCKED;
        }
        if (tournament.hasParticipant(player.getName())) {
            return AddResult.ALREADY_ADDED;
        }

        final Tournament activeTournament = findPlayerOpenTournament(player.getName());
        if (activeTournament != null && !activeTournament.getName().equalsIgnoreCase(tournament.getName())) {
            return AddResult.ALREADY_IN_OTHER_TOURNAMENT;
        }

        return addPlayer(name, player.getName());
    }

    public RemoveResult leavePlayer(final String name, final Player player) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return RemoveResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return RemoveResult.LOCKED;
        }
        if (!tournament.hasParticipant(player.getName())) {
            return RemoveResult.NOT_REGISTERED;
        }
        removePlayer(name, player.getName());
        return RemoveResult.SUCCESS;
    }

    public StartResult startTournament(final String name) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return StartResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return switch (tournament.getStatus()) {
                case IN_PROGRESS -> StartResult.ALREADY_STARTED;
                case CANCELLED -> StartResult.CANCELLED;
                case FINISHED -> StartResult.FINISHED;
                case CREATED -> StartResult.ALREADY_STARTED;
            };
        }
        if (tournament.getPlayers().size() < 2) {
            return StartResult.NOT_ENOUGH_PLAYERS;
        }

        buildBracket(tournament);
        tournament.getEliminatedPlayers().clear();
        reserveTournamentArena(tournament);
        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournament.setCurrentRound(1);
        tournament.setHologramRound(1);
        processReadyAndByeMatches(tournament);
        announceNextReadyMatch(tournament);
        changed(tournament);
        return StartResult.SUCCESS;
    }

    public boolean cancel(final String name) {
        final Tournament tournament = getTournament(name);
        if (tournament == null || tournament.getStatus() == TournamentStatus.FINISHED) {
            return false;
        }

        tournament.setStatus(TournamentStatus.CANCELLED);
        tournament.getMatches().values().forEach(round -> round.values().stream()
                .filter(match -> match.getStatus() != TournamentMatchStatus.FINISHED)
                .forEach(match -> match.setStatus(TournamentMatchStatus.CANCELLED)));
        cleanupPendingSelections(tournament, ignored -> true);
        terminateActiveMatches(tournament, ignored -> true);
        changed(tournament);
        releaseTournamentArena(tournament);
        return true;
    }

    public boolean reset(final String name) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return false;
        }

        releaseTournamentArena(tournament);
        cleanupPendingSelections(tournament, ignored -> true);
        terminateActiveMatches(tournament, ignored -> true);
        tournament.setStatus(TournamentStatus.CREATED);
        tournament.setCurrentRound(1);
        tournament.setHologramRound(1);
        tournament.setHologramPage(1);
        tournament.getEliminatedPlayers().clear();
        tournament.getMatches().clear();
        changed(tournament);
        return true;
    }

    public boolean finishTournament(final String name) {
        final Tournament tournament = getTournament(name);
        if (tournament == null || tournament.getStatus() == TournamentStatus.FINISHED) {
            return false;
        }

        tournament.setStatus(TournamentStatus.FINISHED);
        tournament.getMatches().values().forEach(round -> round.values().stream()
                .filter(match -> match.getStatus() == TournamentMatchStatus.STARTING
                        || match.getStatus() == TournamentMatchStatus.IN_PROGRESS
                        || match.getStatus() == TournamentMatchStatus.READY
                        || match.getStatus() == TournamentMatchStatus.WAITING_PLAYER)
                .forEach(match -> match.setStatus(TournamentMatchStatus.CANCELLED)));
        cleanupPendingSelections(tournament, ignored -> true);
        terminateActiveMatches(tournament, ignored -> true);
        changed(tournament);
        releaseTournamentArena(tournament);
        sendFinalResults(tournament);
        return true;
    }

    public ReplayResult replayMatch(final String name, final int round, final int matchNumber) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return ReplayResult.NOT_FOUND;
        }
        final TournamentMatch target = tournament.getMatch(round, matchNumber);
        if (target == null) {
            return ReplayResult.NO_MATCH;
        }
        if (!target.isKnown() || target.isBye()) {
            return ReplayResult.NOT_READY;
        }

        cleanupPendingSelections(tournament, selection -> selection.round >= round);
        terminateActiveMatches(tournament, active -> active.round >= round);
        target.setWinner(null);
        target.setStatus(TournamentMatchStatus.READY);
        target.setWaitingSince(0L);
        tournament.getEliminatedPlayers().removeIf(player -> target.hasPlayer(player));
        rebuildFutureRounds(tournament, round);
        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournament.setCurrentRound(resolveCurrentRound(tournament));
        changed(tournament);
        return ReplayResult.SUCCESS;
    }

    public MatchStartResult startMatch(final String name, final int round, final int matchNumber) {
        return startMatch(name, round, matchNumber, false);
    }

    private MatchStartResult startMatch(final String name, final int round, final int matchNumber,
                                        final boolean resumeImmediately) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return MatchStartResult.NOT_FOUND;
        }
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return tournament.getStatus() == TournamentStatus.FINISHED || tournament.getStatus() == TournamentStatus.CANCELLED
                    ? MatchStartResult.FINISHED
                    : MatchStartResult.NOT_READY;
        }

        final TournamentMatch match = tournament.getMatch(round, matchNumber);
        if (match == null) {
            return MatchStartResult.NO_MATCH;
        }
        if (match.getStatus() == TournamentMatchStatus.IN_PROGRESS) {
            return MatchStartResult.ALREADY_RUNNING;
        }
        if (match.getStatus() == TournamentMatchStatus.STARTING) {
            return MatchStartResult.STARTING;
        }
        if (match.getStatus() == TournamentMatchStatus.FINISHED || match.getStatus() == TournamentMatchStatus.BYE) {
            return MatchStartResult.FINISHED;
        }
        if (!match.isKnown() || match.isBye()) {
            return MatchStartResult.NOT_READY;
        }

        final Player player1 = Bukkit.getPlayerExact(match.getPlayer1());
        final Player player2 = Bukkit.getPlayerExact(match.getPlayer2());

        if (player1 == null || player2 == null) {
            match.setStatus(TournamentMatchStatus.WAITING_PLAYER);
            match.setWaitingSince(System.currentTimeMillis());
            changed(tournament);
            announceWaitingPlayer(tournament, match, player1 == null, player2 == null);
            return player1 == null && player2 == null ? MatchStartResult.BOTH_OFFLINE : MatchStartResult.PLAYER_OFFLINE;
        }

        final List<String> kitPool = getTournamentKitPool(tournament);
        if (kitPool.isEmpty()) {
            return MatchStartResult.NO_KIT;
        }

        final String directKit = resolveDirectMatchKit(tournament, kitPool);
        final ArenaImpl arena = getArenaForTournament(tournament, plugin.getKitManager().get(directKit));
        if (hasTournamentArenaLimit(tournament) && arena == null) {
            return MatchStartResult.NO_ARENA;
        }
        match.setStatus(TournamentMatchStatus.STARTING);
        match.setWaitingSince(0L);
        changed(tournament);
        if (tournament.getKitMode() == TournamentKitMode.PLAYER_CHOICE && kitPool.size() > 1) {
            beginKitSelection(tournament, match, player1, player2, arena);
        } else if (resumeImmediately) {
            launchStartingMatch(tournament.getName(), round, matchNumber, directKit);
        } else {
            notifyMatchStarting(tournament, match, arena, 30);
            plugin.doSyncAfter(() -> notifyMatchStarting(tournament, match, arena, 10), 20L * 20L);
            plugin.doSyncAfter(() -> launchStartingMatch(tournament.getName(), round, matchNumber, directKit), 20L * 30L);
        }
        return MatchStartResult.SUCCESS;
    }

    public NextMatchStart startNextMatch(final String name) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return new NextMatchStart(MatchStartResult.NOT_FOUND, name, 0, 0, null, null, null, settings.defaultArena);
        }
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return new NextMatchStart(MatchStartResult.NOT_READY, tournament.getName(), 0, 0, null, null, tournament.getKit(), currentArenaName());
        }

        recoverStaleKitSelections(tournament);
        final TournamentMatch next = findNextStartableMatch(tournament);
        if (next == null) {
            return new NextMatchStart(MatchStartResult.NO_MATCH, tournament.getName(), 0, 0, null, null, tournament.getKit(), currentArenaName());
        }

        final MatchStartResult result = startMatch(tournament.getName(), next.getRound(), next.getNumber());
        return new NextMatchStart(result, tournament.getName(), next.getRound(), next.getNumber(),
                next.getPlayer1(), next.getPlayer2(), tournament.getKit(), currentArenaName());
    }

    private void launchStartingMatch(final String tournamentName, final int round, final int matchNumber) {
        launchStartingMatch(tournamentName, round, matchNumber, null);
    }

    private void launchStartingMatch(final String tournamentName, final int round, final int matchNumber, final String selectedKitName) {
        final Tournament tournament = getTournament(tournamentName);
        if (tournament == null || tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return;
        }

        final TournamentMatch match = tournament.getMatch(round, matchNumber);
        if (match == null || match.getStatus() != TournamentMatchStatus.STARTING || !match.isKnown()) {
            return;
        }

        final Player player1 = Bukkit.getPlayerExact(match.getPlayer1());
        final Player player2 = Bukkit.getPlayerExact(match.getPlayer2());
        if (player1 == null || player2 == null) {
            match.setStatus(TournamentMatchStatus.WAITING_PLAYER);
            match.setWaitingSince(System.currentTimeMillis());
            changed(tournament);
            return;
        }

        final KitImpl kit = plugin.getKitManager().get(selectedKitName == null || selectedKitName.isBlank() ? tournament.getKit() : selectedKitName);
        if (kit == null) {
            match.setStatus(TournamentMatchStatus.READY);
            changed(tournament);
            return;
        }

        if (preparePlayersForTournamentMatch(player1, player2)) {
            plugin.doSyncAfter(() -> launchStartingMatch(tournament.getName(), round, matchNumber, selectedKitName), 5L);
            return;
        }

        final Settings duelSettings = new Settings(plugin);
        duelSettings.setKit(kit);

        final ArenaImpl arena = getArenaForTournament(tournament, kit);
        if (hasTournamentArenaLimit(tournament) && arena == null) {
            match.setStatus(TournamentMatchStatus.READY);
            changed(tournament);
            return;
        }
        if (arena != null) {
            duelSettings.setArena(arena);
        }

        final boolean started = startTournamentDuel(tournament, player1, player2, duelSettings);
        if (!started) {
            match.setStatus(TournamentMatchStatus.READY);
            changed(tournament);
            return;
        }

        final Match active = findStartedMatch(player1, player2);
        if (active != null) {
            activeMatches.put(active, new ActiveTournamentMatch(tournament.getName(), round, matchNumber));
        }
        final ArenaImpl startedArena = plugin.getArenaManager().get(player1);

        match.setStatus(TournamentMatchStatus.IN_PROGRESS);
        match.setWaitingSince(0L);
        changed(tournament);
        announceMatchStart(tournament, match, startedArena == null ? arena : startedArena);
    }

    @EventHandler
    public void onKitSelectionClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof KitSelectionMenu menu)) {
            return;
        }

        event.setCancelled(true);
        final PendingKitSelection selection = pendingKitSelections.get(menu.key());
        if (selection == null || selection.isConfirmed(player.getName())) {
            return;
        }

        final int slot = event.getRawSlot();
        final String kitName = selection.kitBySlot.get(slot);
        if (kitName == null) {
            return;
        }
        if (isKitBlocked(player.getName(), kitName)) {
            player.sendMessage(StringUtil.color("&cЭтот кит использовался в двух ваших последних матчах подряд. Выберите другой кит."));
            return;
        }

        selection.choose(player.getName(), kitName);
        openKitSelectionMenu(player, selection);
        if (selection.isComplete()) {
            finishKitSelection(selection);
        }
    }

    @EventHandler
    public void onKitSelectionClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof KitSelectionMenu menu)) {
            return;
        }

        final PendingKitSelection selection = pendingKitSelections.get(menu.key());
        if (selection == null || selection.isConfirmed(player.getName()) || selection.isComplete()) {
            return;
        }

        plugin.doSyncAfter(() -> {
            final PendingKitSelection current = pendingKitSelections.get(menu.key());
            if (current == null || current.isConfirmed(player.getName()) || current.isComplete()) {
                return;
            }
            sendReopenKitSelectionMessage(player);
        }, 2L);
    }

    public void reopenKitSelection(final Player player) {
        final PendingKitSelection selection = findPendingKitSelection(player.getName());
        if (selection == null) {
            player.sendMessage(StringUtil.color("&cСейчас для вас нет активного выбора кита."));
            return;
        }
        if (selection.isConfirmed(player.getName())) {
            player.sendMessage(StringUtil.color("&eВаш выбор кита уже подтверждён."));
            return;
        }

        openKitSelectionMenu(player, selection);
    }

    public boolean setWinner(final String name, final String player) {
        final Tournament tournament = getTournament(name);
        if (tournament == null || tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return false;
        }

        for (Map<Integer, TournamentMatch> round : tournament.getMatches().values()) {
            for (TournamentMatch match : round.values()) {
                if (match.hasPlayer(player) && match.getStatus() != TournamentMatchStatus.FINISHED && match.getStatus() != TournamentMatchStatus.BYE) {
                    finishMatch(tournament, match, player);
                    return true;
                }
            }
        }
        return false;
    }

    public SpectateResult spectate(final Player spectator, final String name, final int round, final int matchNumber, final boolean admin) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return SpectateResult.NOT_FOUND;
        }

        final TournamentMatch match = tournament.getMatch(round, matchNumber);
        if (match == null) {
            return SpectateResult.NO_MATCH;
        }
        if (!admin && match.getStatus() != TournamentMatchStatus.IN_PROGRESS) {
            return match.getStatus() == TournamentMatchStatus.FINISHED || match.getStatus() == TournamentMatchStatus.BYE
                    ? SpectateResult.FINISHED
                    : SpectateResult.NOT_STARTED;
        }
        if (!admin && !settings.allowActiveParticipantsToSpectate && tournament.hasParticipant(spectator.getName()) && !tournament.isEliminated(spectator.getName())) {
            return SpectateResult.ACTIVE_PARTICIPANT;
        }

        final Player target = Bukkit.getPlayerExact(match.getPlayer1());
        if (target == null) {
            return SpectateResult.NO_TARGET;
        }

        return switch (plugin.getSpectateManager().startSpectating(spectator, target)) {
            case SUCCESS -> {
                setSpectatorReturnLobby(spectator);
                yield SpectateResult.SUCCESS;
            }
            case TARGET_NOT_IN_MATCH -> SpectateResult.NOT_STARTED;
            default -> SpectateResult.REJECTED;
        };
    }

    public SpectateResult spectate(final Player spectator, final String name, final boolean admin) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return SpectateResult.NOT_FOUND;
        }
        if (!admin && !settings.allowActiveParticipantsToSpectate && tournament.hasParticipant(spectator.getName()) && !tournament.isEliminated(spectator.getName())) {
            return SpectateResult.ACTIVE_PARTICIPANT;
        }

        final TournamentMatch match = activeMatches(tournament).stream()
                .filter(active -> active.getStatus() == TournamentMatchStatus.IN_PROGRESS)
                .findFirst()
                .orElse(null);
        if (match == null) {
            return SpectateResult.NOT_STARTED;
        }

        final Player target = Bukkit.getPlayerExact(match.getPlayer1());
        if (target == null) {
            return SpectateResult.NO_TARGET;
        }

        return switch (plugin.getSpectateManager().startSpectating(spectator, target)) {
            case SUCCESS -> {
                setSpectatorReturnLobby(spectator);
                yield SpectateResult.SUCCESS;
            }
            case TARGET_NOT_IN_MATCH -> SpectateResult.NOT_STARTED;
            default -> SpectateResult.REJECTED;
        };
    }

    public boolean setLobby(final Player player) {
        final Location location = player.getLocation().clone();
        try (final Writer writer = new OutputStreamWriter(Files.newOutputStream(lobbyFile.toPath()), StandardCharsets.UTF_8)) {
            JsonUtil.getObjectWriter().writeValue(writer, LocationData.fromLocation(location));
            writer.flush();
            this.lobby = location;
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save tournament lobby: " + ex.getMessage());
            return false;
        }
    }

    public Location getLobby() {
        return lobby == null ? plugin.getPlayerManager().getLobby() : lobby;
    }

    public boolean createOrSetHologram(final Player player, final String name) {
        final Tournament tournament = getTournament(name);
        if (tournament == null || !settings.cmiEnabled) {
            return false;
        }

        if (tournament.getHologramName() != null && !tournament.getHologramName().isBlank()) {
            removeTournamentHologram(tournament, false);
        }

        final Location location = player.getEyeLocation().add(0D, 0.6D, 0D);
        location.setYaw((float) snapYawToCardinal(location.getYaw()));
        location.setPitch(0F);
        tournament.setHologramName(settings.hologramNameFormat.replace("%name%", tournament.getName()));
        tournament.setHologramWorld(location.getWorld() == null ? "" : location.getWorld().getName());
        tournament.setHologramX(location.getX());
        tournament.setHologramY(location.getY());
        tournament.setHologramZ(location.getZ());
        tournament.setHologramYaw(location.getYaw());
        tournament.setHologramPitch(location.getPitch());
        tournament.setHologramDirection(settings.hologramDirection);
        tournament.setHologramMode(settings.showOnlyCurrentRound ? "CURRENT_ROUND" : "ALL_ROUNDS");
        tournament.setHologramPage(1);
        tournament.setHologramRound(tournament.getCurrentRound());

        for (String hologram : getTournamentHologramNames(tournament)) {
            dispatch(player, settings.cmiCreateCommand.replace("%hologram%", hologram));
        }
        changed(tournament);
        return true;
    }

    public boolean removeHologram(final String name) {
        final Tournament tournament = getTournament(name);
        return tournament != null && removeTournamentHologram(tournament, true);
    }

    public List<String> describeHolograms() {
        final List<String> lines = new ArrayList<>();
        for (Tournament tournament : tournaments.values()) {
            if (getBaseHologramName(tournament).isBlank()) {
                continue;
            }

            lines.add("&6" + tournament.getName() + " &7- управлять: &f/tournament holo set|remove|update " + tournament.getName());
            lines.add("&7CMI: &f" + String.join("&7, &f", getTournamentHologramNames(tournament)));
        }
        return lines;
    }

    public void updateHologram(final String name) {
        final Tournament tournament = getTournament(name);
        if (tournament != null) {
            updateHologram(tournament);
        }
    }

    public boolean setHologramPage(final String name, final int page) {
        final Tournament tournament = getTournament(name);
        if (tournament == null || page < 1) {
            return false;
        }
        tournament.setHologramPage(Math.min(page, getHologramMaxPage(tournament)));
        changed(tournament);
        return true;
    }

    public boolean setHologramRound(final String name, final int round) {
        final Tournament tournament = getTournament(name);
        if (tournament == null || !tournament.getMatches().containsKey(round)) {
            return false;
        }
        tournament.setHologramRound(round);
        tournament.setHologramMode("ROUND");
        tournament.setHologramPage(1);
        changed(tournament);
        return true;
    }

    public HologramDirectionResult setHologramDirection(final String name, final String direction) {
        final Tournament tournament = getTournament(name);
        if (tournament == null) {
            return HologramDirectionResult.NOT_FOUND;
        }
        final String normalized = direction.toUpperCase(Locale.ROOT);
        if (!List.of("AUTO", "NORTH", "EAST", "SOUTH", "WEST").contains(normalized)) {
            return HologramDirectionResult.INVALID_DIRECTION;
        }
        tournament.setHologramDirection(normalized);
        changed(tournament);
        return HologramDirectionResult.SUCCESS;
    }

    public List<String> describe(final Tournament tournament) {
        final List<String> lines = new ArrayList<>();
        lines.add("&8&m                                                ");
        lines.add("&6&lТурнир &e&l" + tournament.getName());
        lines.add("&7" + effectiveKitDescription(tournament) + " &8| &7Арена: &f" + currentArenaName());
        lines.add("&7Статус: " + formatTournamentStatus(tournament.getStatus()) + " &8| &7Участников: &f" + tournament.getPlayers().size());
        lines.add("&7Текущий раунд: &f" + tournament.getCurrentRound() + " &8| &7Осталось: &f" + countRemainingPlayers(tournament));
        lines.add("&8&m                                                ");

        if (tournament.getPlayers().isEmpty()) {
            lines.add("&eУчастников пока нет. Добавьте игроков через &f/tour add " + tournament.getName() + " <ник>&e.");
        } else {
            lines.add("&6Участники:");
            lines.add("&f" + String.join("&7, &f", tournament.getPlayers()));
        }

        final List<TournamentMatch> currentMatches = tournament.getRoundMatches(tournament.getCurrentRound());
        if (currentMatches.isEmpty()) {
            lines.add("&8&m                                                ");
            lines.add("&eСетка ещё не сформирована.");
            lines.add("&7Запуск: &f/tour start " + tournament.getName());
        } else {
            lines.add("&8&m                                                ");
            lines.add("&6Матчи раунда " + tournament.getCurrentRound() + ":");
            for (TournamentMatch match : currentMatches) {
                lines.add("&f#" + match.getNumber() + " &7| &e" + participantName(match.getPlayer1())
                        + " &8vs &e" + participantName(match.getPlayer2())
                        + " &8- " + formatMatchStatus(match.getStatus())
                        + (match.getWinner() == null ? "" : " &8| &aПобедитель: &f" + participantName(match.getWinner())));
            }
        }
        lines.add("&8&m                                                ");
        lines.add("&7Управление: &f/tour menu &7или &f/tour nextmatch " + tournament.getName());
        return lines;
    }

    public List<String> activeSpecList(final Tournament tournament) {
        return tournament.getMatches().values().stream()
                .flatMap(round -> round.values().stream())
                .filter(match -> match.getStatus() == TournamentMatchStatus.IN_PROGRESS)
                .map(match -> "R" + match.getRound() + " M" + match.getNumber() + ": " + match.getPlayer1() + " vs " + match.getPlayer2())
                .collect(Collectors.toList());
    }

    public List<String> describePlayerMatch(final Player player) {
        final Tournament tournament = findPlayerTournament(player.getName());
        if (tournament == null) {
            return List.of(
                    "&8&m                                                ",
                    "&c&lТурнирный матч",
                    "&7Вы сейчас не участвуете в активном турнире.",
                    "&7Если регистрация открыта, подойдите к NPC турнира и нажмите &aРегистрация&7.",
                    "&8&m                                                ");
        }
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            final List<String> lines = new ArrayList<>();
            lines.add("&8&m                                                ");
            lines.add("&6&lТурнир завершён: &e" + tournament.getName());
            lines.add("&7Ваш результат:");
            lines.addAll(describePlayerResults(tournament, player.getName()));
            lines.add("&8&m                                                ");
            return lines;
        }
        if (tournament.isEliminated(player.getName())) {
            final List<String> lines = new ArrayList<>();
            lines.add("&8&m                                                ");
            lines.add("&c&lВы выбыли из турнира");
            lines.add("&7Турнир: &e" + tournament.getName());
            lines.addAll(describePlayerResults(tournament, player.getName()));
            lines.add("&8&m                                                ");
            return lines;
        }
        if (tournament.getStatus() == TournamentStatus.CREATED || tournament.getMatches().isEmpty()) {
            return List.of(
                    "&8&m                                                ",
                    "&6&lТурнир &e&l" + tournament.getName(),
                    "&7Статус: " + formatTournamentStatus(tournament.getStatus()),
                    "&eВаш матч ещё не сформирован.",
                    "&7Организатор запустит турнир, когда регистрация завершится.",
                    "&8&m                                                ");
        }

        final TournamentMatch match = findPlayerCurrentMatch(tournament, player.getName());
        if (match == null) {
            return List.of(
                    "&8&m                                                ",
                    "&6&lТурнир &e&l" + tournament.getName(),
                    "&eДля вас пока нет готовой пары.",
                    "&7Дождитесь завершения текущих матчей раунда.",
                    "&8&m                                                ");
        }

        final String opponent = match.getOpponent(player.getName());
        return List.of(
                "&8&m                                                ",
                "&6&lВаш турнирный матч",
                "&7Турнир: &e" + tournament.getName() + " &8| &7" + effectiveKitDescription(tournament),
                "&7Раунд: &f" + match.getRound() + " &8| &7Матч: &f#" + match.getNumber() + " &8| &7Арена: &f" + currentArenaName(),
                "&7Пара: &a" + participantName(match.getPlayer1()) + " &8vs &c" + participantName(match.getPlayer2()),
                "&7Ваш соперник: &f" + participantName(opponent),
                "&7Статус матча: " + formatMatchStatus(match.getStatus()),
                match.getStatus() == TournamentMatchStatus.WAITING_PLAYER
                        ? "&7Ожидание: &f" + formatWaitingElapsed(match) + " &8| &7осталось: &f" + formatWaitingRemaining(match)
                        : "",
                match.getStatus() == TournamentMatchStatus.READY || match.getStatus() == TournamentMatchStatus.WAITING_PLAYER
                        ? "&eОжидайте запуска пары организатором."
                        : "&7Следите за сообщениями в чате.",
                "&8&m                                                ");
    }

    public List<String> describePlayerResults(final Tournament tournament, final String playerName) {
        final List<String> lines = new ArrayList<>();
        boolean found = false;
        for (Map<Integer, TournamentMatch> round : tournament.getMatches().values()) {
            for (TournamentMatch match : round.values()) {
                if (!match.hasPlayer(playerName) || match.getStatus() == TournamentMatchStatus.BYE) {
                    continue;
                }
                found = true;
                final String opponent = match.getOpponent(playerName);
                if (match.getStatus() == TournamentMatchStatus.FINISHED && match.getWinner() == null) {
                    lines.add("&7Раунд " + match.getRound() + ", матч #" + match.getNumber()
                            + ": &cоба игрока выбыли &8| &7соперник: &f" + participantName(opponent));
                } else if (playerName.equalsIgnoreCase(match.getWinner())) {
                    lines.add("&7Раунд " + match.getRound() + ", матч #" + match.getNumber()
                            + ": &aпобеда &8| &7соперник: &f" + participantName(opponent));
                } else if (match.getWinner() != null) {
                    lines.add("&7Раунд " + match.getRound() + ", матч #" + match.getNumber()
                            + ": &cпоражение &8| &7победитель: &f" + participantName(match.getWinner()));
                } else {
                    lines.add("&7Раунд " + match.getRound() + ", матч #" + match.getNumber()
                            + ": " + formatMatchStatus(match.getStatus()) + " &8| &7соперник: &f" + participantName(opponent));
                }
            }
        }
        if (!found) {
            lines.add(tournament.hasParticipant(playerName)
                    ? "&7Матчи для вас ещё не были сформированы."
                    : "&7Вы не были участником этого турнира.");
        }
        return lines;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchStart(final MatchStartEvent event) {
        final Player[] players = event.getPlayers();
        if (players.length < 2) {
            return;
        }

        for (Tournament tournament : tournaments.values()) {
            if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
                continue;
            }

            final TournamentMatch match = findTournamentMatch(tournament, players[0].getName(), players[1].getName());
            // MatchStartEvent is fired synchronously while launchStartingMatch still has STARTING status.
            if (match == null || !match.getStatus().isRunning()) {
                continue;
            }

            activeMatches.put(event.getMatch(), new ActiveTournamentMatch(tournament.getName(), match.getRound(), match.getNumber()));
            setTournamentReturnLobby(players);
            updateHologram(tournament);
            return;
        }
    }

    public boolean isTournamentMatch(final Match match) {
        return match != null && activeMatches.containsKey(match);
    }

    public void sendTournamentDeathMessage(final Match match, final Player loser, final Player winner, final double health) {
        final ActiveTournamentMatch active = activeMatches.get(match);
        if (active == null) {
            return;
        }
        final Tournament tournament = getTournament(active.tournament);
        if (tournament == null || tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return;
        }
        sendToTournamentPlayers(tournament, List.of(
                "&8&m                                                ",
                "&6&lТурнир: &e" + tournament.getName(),
                "&a" + winner.getName() + " &7победил игрока &c" + loser.getName(),
                "&7Осталось здоровья: &c" + String.format(Locale.US, "%.1f", health) + "❤",
                "&7Раунд: &f" + active.round + " &8| &7Матч: &f#" + active.match,
                "&8&m                                                "));
    }

    public boolean sendTournamentInventories(final DuelMatch match) {
        final ActiveTournamentMatch active = activeMatches.get(match);
        if (active == null) {
            return false;
        }
        final Tournament tournament = getTournament(active.tournament);
        if (tournament == null) {
            return false;
        }

        final List<Player> recipients = onlineTournamentPlayers(tournament);
        if (recipients.isEmpty()) {
            return true;
        }

        final TextBuilder builder = TextBuilder.of(StringUtil.color("&8&m                                                \n"
                + "&6&lИнвентари турнирного матча\n"
                + "&7Нажмите на ник, чтобы открыть инвентарь после боя:\n"));
        final Iterator<Player> iterator = match.getAllPlayers().iterator();
        while (iterator.hasNext()) {
            final Player player = iterator.next();
            builder.add(StringUtil.color("&e&n" + player.getName()), ClickEvent.Action.RUN_COMMAND, "/duel _ " + player.getUniqueId());
            if (iterator.hasNext()) {
                builder.add(StringUtil.color("&7, "));
            }
        }
        builder.add(StringUtil.color("\n&8&m                                                "));
        builder.send(new HashSet<>(recipients));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(final MatchEndEvent event) {
        final ActiveTournamentMatch active = activeMatches.remove(event.getMatch());
        if (active == null || event.getWinner() == null) {
            return;
        }

        final Tournament tournament = getTournament(active.tournament);
        if (tournament == null) {
            return;
        }

        final TournamentMatch match = tournament.getMatch(active.round, active.match);
        if (match == null) {
            return;
        }

        final String winner = resolveStoredPlayerName(match, event.getWinner());
        if (winner != null) {
            finishMatch(tournament, match, winner);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        // Active tournament duels are paused by DuelManager before its normal disconnect-loss handling.
    }

    public boolean pauseForDisconnect(final Player player) {
        if (settings.disconnectLoss) {
            return false;
        }
        final ArenaImpl arena = plugin.getArenaManager().get(player);
        final DuelMatch duelMatch = arena == null ? null : arena.getMatch();
        final ActiveTournamentMatch active = duelMatch == null ? null : activeMatches.remove(duelMatch);
        if (active == null) {
            return false;
        }

        final Tournament tournament = getTournament(active.tournament);
        final TournamentMatch match = tournament == null ? null : tournament.getMatch(active.round, active.match);
        if (tournament == null || match == null || match.getStatus() != TournamentMatchStatus.IN_PROGRESS) {
            return false;
        }

        match.setStatus(TournamentMatchStatus.WAITING_PLAYER);
        match.setWaitingSince(System.currentTimeMillis());
        changed(tournament);
        announceWaitingPlayer(tournament, match,
                match.getPlayer1().equalsIgnoreCase(player.getName()),
                match.getPlayer2().equalsIgnoreCase(player.getName()));
        plugin.getDuelManager().pauseTournamentMatch(player);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        plugin.doSyncAfter(() -> resumeWaitingMatch(event.getPlayer()), 5L);
    }

    private void resumeWaitingMatch(final Player joined) {
        if (!joined.isOnline()) {
            return;
        }

        final WaitingTournamentMatch waiting = tournaments.values().stream()
                .filter(tournament -> tournament.getStatus() == TournamentStatus.IN_PROGRESS)
                .flatMap(tournament -> tournament.getMatches().values().stream()
                        .flatMap(round -> round.values().stream())
                        .filter(match -> match.getStatus() == TournamentMatchStatus.WAITING_PLAYER)
                        .filter(match -> match.hasPlayer(joined.getName()))
                        .map(match -> new WaitingTournamentMatch(tournament, match)))
                .min(Comparator.comparingLong(value -> value.match().getWaitingSince()))
                .orElse(null);

        if (waiting == null) {
            return;
        }

        final TournamentMatch match = waiting.match();
        final String opponentName = match.getOpponent(joined.getName());
        final Player opponent = opponentName == null ? null : Bukkit.getPlayerExact(opponentName);
        if (opponent == null) {
            joined.sendMessage(StringUtil.color("&6&lТурнир: &e" + waiting.tournament().getName()));
            joined.sendMessage(StringUtil.color("&eВы вернулись, но соперник ещё не в сети."));
            joined.sendMessage(StringUtil.color("&7До автоматического решения: &f" + formatWaitingRemaining(match) + "&7."));
            return;
        }

        joined.sendMessage(StringUtil.color("&6&lТурнир: &e" + waiting.tournament().getName()));
        joined.sendMessage(StringUtil.color("&aВы вернулись. Турнирный матч запускается автоматически."));
        opponent.sendMessage(StringUtil.color("&aСоперник вернулся. Турнирный матч запускается автоматически."));
        startMatch(waiting.tournament().getName(), match.getRound(), match.getNumber(), true);
    }

    private void buildBracket(final Tournament tournament) {
        bracketService.buildBracket(tournament, settings.shufflePlayers);
    }

    private void processReadyAndByeMatches(final Tournament tournament) {
        bracketService.processReadyAndByeMatches(tournament);
    }

    private void finishMatch(final Tournament tournament, final TournamentMatch match, final String winner) {
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS
                || match.getStatus().isTerminal()) {
            return;
        }
        final String loser = match.getOpponent(winner);

        if (!match.hasPlayer(winner)) {
            return;
        }

        match.setWinner(winner);
        match.setStatus(TournamentMatchStatus.FINISHED);
        match.setWaitingSince(0L);
        advanceWinner(tournament, match, winner);
        processReadyAndByeMatches(tournament);
        tournament.setCurrentRound(resolveCurrentRound(tournament));
        announceMatchResult(tournament, match, winner, loser);
        notifyMatchFinished(tournament, winner, loser);
        announceNextReadyMatch(tournament);
        final boolean finished = tournament.getStatus() == TournamentStatus.FINISHED;
        changed(tournament);

        if (finished) {
            releaseTournamentArena(tournament);
            sendFinalResults(tournament);
        }
    }

    private void rebuildFutureRounds(final Tournament tournament, final int fromRound) {
        bracketService.rebuildFutureRounds(tournament, fromRound);
    }

    private void advanceWinner(final Tournament tournament, final TournamentMatch match, final String winner) {
        bracketService.advanceWinner(tournament, match, winner);
    }

    private int resolveCurrentRound(final Tournament tournament) {
        return bracketService.resolveCurrentRound(tournament);
    }

    private Match findStartedMatch(final Player player1, final Player player2) {
        for (ArenaImpl arena : plugin.getArenaManager().getArenasImpl()) {
            if (arena.getMatch() == null) {
                continue;
            }
            final List<UUID> ids = arena.getMatch().getStartingPlayers().stream().map(Player::getUniqueId).collect(Collectors.toList());
            if (ids.contains(player1.getUniqueId()) && ids.contains(player2.getUniqueId())) {
                return arena.getMatch();
            }
        }
        return null;
    }

    private String resolveStoredPlayerName(final TournamentMatch match, final UUID winnerId) {
        if (matchesPlayerId(match.getPlayer1(), winnerId)) {
            return match.getPlayer1();
        }
        if (matchesPlayerId(match.getPlayer2(), winnerId)) {
            return match.getPlayer2();
        }

        final OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerId);
        return winner.getName();
    }

    private boolean matchesPlayerId(final String name, final UUID id) {
        return name != null && Bukkit.getOfflinePlayer(name).getUniqueId().equals(id);
    }

    private ArenaImpl getArenaForTournament(final Tournament tournament, final KitImpl kit) {
        if (hasTournamentArenaLimit(tournament)) {
            final List<ArenaImpl> available = tournament.getAllowedArenas().stream()
                    .map(plugin.getArenaManager()::get)
                    .filter(Objects::nonNull)
                    .filter(arena -> plugin.getArenaManager().isSelectable(kit, arena))
                    .filter(ArenaImpl::isAvailable)
                    .collect(Collectors.toList());
            return available.isEmpty() ? null : available.get(random.nextInt(available.size()));
        }

        final String arenaName = settings.defaultArena;
        return arenaName == null || arenaName.isBlank() ? null : plugin.getArenaManager().get(arenaName);
    }

    private boolean hasTournamentArenaLimit(final Tournament tournament) {
        return tournament != null && !tournament.getAllowedArenas().isEmpty();
    }

    private void checkAutoForfeits() {
        if (settings.autoForfeitMinutes <= 0) {
            return;
        }

        final long timeout = settings.autoForfeitMinutes * 60_000L;
        final long now = System.currentTimeMillis();
        for (Tournament tournament : tournaments.values()) {
            if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
                continue;
            }
            for (TournamentMatch match : tournament.getRoundMatches(tournament.getCurrentRound())) {
                if (match.getStatus() != TournamentMatchStatus.WAITING_PLAYER || match.getWaitingSince() <= 0L || now - match.getWaitingSince() < timeout) {
                    continue;
                }
                final Player player1 = Bukkit.getPlayerExact(match.getPlayer1());
                final Player player2 = Bukkit.getPlayerExact(match.getPlayer2());
                if (player1 == null && player2 == null) {
                    eliminateBothMissing(tournament, match);
                } else if (player1 == null) {
                    finishMatch(tournament, match, match.getPlayer2());
                } else if (player2 == null) {
                    finishMatch(tournament, match, match.getPlayer1());
                }
            }
        }
    }

    private void eliminateBothMissing(final Tournament tournament, final TournamentMatch match) {
        addEliminated(tournament, match.getPlayer1());
        addEliminated(tournament, match.getPlayer2());
        match.setWinner(null);
        match.setStatus(TournamentMatchStatus.FINISHED);
        match.setWaitingSince(0L);
        advanceEmptySlot(tournament, match);
        processReadyAndByeMatches(tournament);
        tournament.setCurrentRound(resolveCurrentRound(tournament));
        sendToTournamentPlayers(tournament, List.of(
                "&8&m                                                ",
                "&6&lТурнир: &e" + tournament.getName(),
                "&cОба игрока не вернулись вовремя и выбыли.",
                "&7Раунд: &f" + match.getRound() + " &8| &7Матч: &f#" + match.getNumber(),
                "&7Игроки: &f" + participantName(match.getPlayer1()) + " &8vs &f" + participantName(match.getPlayer2()),
                "&8&m                                                "));
        final boolean finished = tournament.getStatus() == TournamentStatus.FINISHED;
        changed(tournament);
        if (finished) {
            releaseTournamentArena(tournament);
            sendFinalResults(tournament);
        }
    }

    private void addEliminated(final Tournament tournament, final String player) {
        if (player == null || "BYE".equalsIgnoreCase(player)) {
            return;
        }
        if (tournament.getEliminatedPlayers().stream().noneMatch(name -> name.equalsIgnoreCase(player))) {
            tournament.getEliminatedPlayers().add(player);
        }
    }

    private void advanceEmptySlot(final Tournament tournament, final TournamentMatch match) {
        final int nextRound = match.getRound() + 1;
        final Map<Integer, TournamentMatch> nextRoundMatches = tournament.getMatches().get(nextRound);
        if (nextRoundMatches == null) {
            tournament.setStatus(TournamentStatus.FINISHED);
            tournament.setCurrentRound(match.getRound());
            return;
        }

        final TournamentMatch nextMatch = nextRoundMatches.get((match.getNumber() + 1) / 2);
        if (nextMatch == null) {
            return;
        }
        if (match.getNumber() % 2 == 1) {
            nextMatch.setPlayer1("BYE");
        } else {
            nextMatch.setPlayer2("BYE");
        }
        if (nextMatch.isKnown()) {
            nextMatch.setStatus(nextMatch.isBye() ? TournamentMatchStatus.BYE : TournamentMatchStatus.READY);
        }
    }

    private void beginKitSelection(final Tournament tournament, final TournamentMatch match, final Player player1, final Player player2, final ArenaImpl arena) {
        final String key = selectionKey(tournament.getName(), match.getRound(), match.getNumber());
        final PendingKitSelection selection = new PendingKitSelection(key, tournament.getName(), match.getRound(), match.getNumber(),
                player1.getName(), player2.getName(), getTournamentKitPool(tournament), System.currentTimeMillis() + settings.kitSelectionSeconds * 1000L);
        pendingKitSelections.put(key, selection);

        player1.sendMessage(StringUtil.color("&eВыберите кит для турнирного матча против &f" + player2.getName() + "&e."));
        player2.sendMessage(StringUtil.color("&eВыберите кит для турнирного матча против &f" + player1.getName() + "&e."));
        try {
            openKitSelectionMenu(player1, selection);
            openKitSelectionMenu(player2, selection);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Could not open tournament kit selection menu: " + ex.getMessage());
            failKitSelection(selection, "&cНе удалось открыть меню выбора кита. Попробуйте запустить пару ещё раз.");
            return;
        }
        runKitSelectionTimer(selection);
        notifyMatchStarting(tournament, match, arena, settings.kitSelectionSeconds);
    }

    private PendingKitSelection findPendingKitSelection(final String playerName) {
        return pendingKitSelections.values().stream()
                .filter(selection -> selection.hasPlayer(playerName))
                .findFirst()
                .orElse(null);
    }

    private void sendReopenKitSelectionMessage(final Player player) {
        player.sendMessage(StringUtil.color("&eВы закрыли меню выбора кита. Нажмите, чтобы открыть его снова:"));
        final TextComponent component = new TextComponent(StringUtil.color("&a&l[Выбрать кит]"));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tour selectkit"));
        player.spigot().sendMessage(component);
    }

    private void runKitSelectionTimer(final PendingKitSelection selection) {
        if (!pendingKitSelections.containsKey(selection.key) || selection.isComplete()) {
            return;
        }

        final long remaining = Math.max(0L, (selection.deadline - System.currentTimeMillis() + 999L) / 1000L);
        for (String playerName : List.of(selection.player1, selection.player2)) {
            final Player player = Bukkit.getPlayerExact(playerName);
            if (player == null || selection.isConfirmed(playerName)) {
                continue;
            }
            final String text = StringUtil.color("&eВыбор кита: &f" + remaining + " сек.");
            if ("BOSSBAR".equalsIgnoreCase(settings.kitSelectionTimerDisplay)) {
                updateSelectionBossBar(selection, player, remaining);
            } else {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
            }
        }

        if (remaining <= 0L) {
            autoConfirmMissingSelections(selection);
            return;
        }
        plugin.doSyncAfter(() -> runKitSelectionTimer(selection), 20L);
    }

    private void updateSelectionBossBar(final PendingKitSelection selection, final Player player, final long remaining) {
        BossBar bossBar = selection.bossBars.get(player.getName().toLowerCase(Locale.ROOT));
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(StringUtil.color("&eВыбор кита"), BarColor.YELLOW, BarStyle.SOLID);
            bossBar.addPlayer(player);
            selection.bossBars.put(player.getName().toLowerCase(Locale.ROOT), bossBar);
        }
        bossBar.setTitle(StringUtil.color("&eВыбор кита: &f" + remaining + " сек."));
        bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, remaining / (double) Math.max(1, settings.kitSelectionSeconds))));
    }

    private void autoConfirmMissingSelections(final PendingKitSelection selection) {
        for (String playerName : List.of(selection.player1, selection.player2)) {
            if (selection.isConfirmed(playerName)) {
                continue;
            }
            final Player player = Bukkit.getPlayerExact(playerName);
            final List<String> available = selection.kits.stream()
                    .filter(kit -> !isKitBlocked(playerName, kit))
                    .filter(kit -> plugin.getKitManager().get(kit) != null)
                    .collect(Collectors.toList());
            if (available.isEmpty()) {
                failKitSelection(selection, "&cНет доступных китов для игрока &f" + playerName + "&c. Матч не запущен.");
                return;
            }
            final String selected = available.size() == 1 ? available.get(0) : available.get(random.nextInt(available.size()));
            selection.choose(playerName, selected);
            if (player != null) {
                player.sendMessage(StringUtil.color("&eВремя выбора вышло. Система выбрала за вас кит &f" + selected + "&e."));
            }
        }
        finishKitSelection(selection);
    }

    private void finishKitSelection(final PendingKitSelection selection) {
        if (!pendingKitSelections.containsKey(selection.key)) {
            return;
        }

        final Tournament tournament = getTournament(selection.tournament);
        final TournamentMatch match = tournament == null ? null : tournament.getMatch(selection.round, selection.match);
        if (tournament == null || match == null || match.getStatus() != TournamentMatchStatus.STARTING) {
            cleanupKitSelection(selection);
            return;
        }

        final String first = selection.selectionFor(selection.player1);
        final String second = selection.selectionFor(selection.player2);
        if (first == null || second == null) {
            return;
        }

        final String finalKit = first.equalsIgnoreCase(second) ? first : (random.nextBoolean() ? first : second);
        for (String playerName : List.of(selection.player1, selection.player2)) {
            final Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                player.closeInventory();
            }
        }

        cleanupKitSelection(selection);
        playKitSelectionAnimation(selection, finalKit);
        plugin.doSyncAfter(() -> {
            rememberPlayedKit(selection.player1, finalKit);
            rememberPlayedKit(selection.player2, finalKit);
            launchStartingMatch(selection.tournament, selection.round, selection.match, finalKit);
        }, 20L * Math.max(0, settings.kitSelectionAnimationSeconds));
    }

    private void failKitSelection(final PendingKitSelection selection, final String message) {
        final Tournament tournament = getTournament(selection.tournament);
        final TournamentMatch match = tournament == null ? null : tournament.getMatch(selection.round, selection.match);
        cleanupKitSelection(selection);
        if (match != null) {
            match.setStatus(TournamentMatchStatus.READY);
            match.setWaitingSince(0L);
            changed(tournament);
        }
        Bukkit.getConsoleSender().sendMessage(StringUtil.color(message));
        for (String playerName : List.of(selection.player1, selection.player2)) {
            final Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                player.closeInventory();
                player.sendMessage(StringUtil.color(message));
                teleportToTournamentLobby(player);
            }
        }
    }

    private void announceSelectedKit(final PendingKitSelection selection, final String finalKit) {
        for (String playerName : List.of(selection.player1, selection.player2)) {
            final Player player = Bukkit.getPlayerExact(playerName);
            if (player == null) {
                continue;
            }
            player.sendTitle(StringUtil.color("&6Выбран кит"), StringUtil.color("&f" + finalKit), 10, 60, 10);
            player.sendMessage(StringUtil.color("&6Турнир &7» &eСистема выбрала кит &f" + finalKit + "&e для текущего матча."));
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1.2F);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void playKitSelectionAnimation(final PendingKitSelection selection, final String finalKit) {
        final int seconds = settings.kitSelectionAnimationSeconds;
        if (seconds <= 0 || selection.selectionFor(selection.player1).equalsIgnoreCase(selection.selectionFor(selection.player2))) {
            announceSelectedKit(selection, finalKit);
            return;
        }

        final int frames = Math.max(2, seconds * 2);
        for (int frame = 0; frame < frames; frame++) {
            final int currentFrame = frame;
            plugin.doSyncAfter(() -> {
                final String shown = currentFrame == frames - 1
                        ? finalKit
                        : (currentFrame % 2 == 0 ? selection.selectionFor(selection.player1) : selection.selectionFor(selection.player2));
                for (String playerName : List.of(selection.player1, selection.player2)) {
                    final Player player = Bukkit.getPlayerExact(playerName);
                    if (player != null) {
                        player.sendTitle(StringUtil.color("&eВыбор кита"), StringUtil.color("&f" + shown), 0, 15, 0);
                    }
                }
                if (currentFrame == frames - 1) {
                    announceSelectedKit(selection, finalKit);
                }
            }, frame * 10L);
        }
    }

    private void cleanupKitSelection(final PendingKitSelection selection) {
        pendingKitSelections.remove(selection.key);
        selection.bossBars.values().forEach(BossBar::removeAll);
    }

    private void openKitSelectionMenu(final Player player, final PendingKitSelection selection) {
        final Inventory inventory = Bukkit.createInventory(new KitSelectionMenu(selection.key), 54,
                StringUtil.color("&6Выбор кита: &e" + selection.tournament));
        final ItemStack filler = namedMenuItem(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        selection.kitBySlot.clear();
        final int[] slots = kitSelectionSlots();
        int index = 0;
        final String selected = selection.selectionFor(player.getName());
        for (String kitName : selection.kits) {
            final KitImpl kit = plugin.getKitManager().get(kitName);
            if (kit == null || index >= slots.length) {
                continue;
            }

            final boolean blocked = isKitBlocked(player.getName(), kitName);
            final boolean chosen = selected != null && selected.equalsIgnoreCase(kitName);
            final ItemStack icon = blocked
                    ? namedMenuItem(new ItemStack(Material.BARRIER), "&c" + kitName, List.of(
                    "&cЭтот кит использовался в двух",
                    "&cваших последних матчах подряд.",
                    "&7Выберите другой кит."))
                    : namedMenuItem(kit.getDisplayed(), (chosen ? "&a&l" : "&e") + kitName, List.of(
                    chosen ? "&aВыбор подтверждён." : "&7Нажмите, чтобы выбрать и подтвердить.",
                    "&8Ваш выбор не виден сопернику."));
            inventory.setItem(slots[index], icon);
            selection.kitBySlot.put(slots[index], kitName);
            index++;
        }

        player.openInventory(inventory);
    }

    private int[] kitSelectionSlots() {
        return new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    }

    private ItemStack namedMenuItem(final ItemStack source, final String name, final List<String> lore) {
        final ItemStack item = source == null ? new ItemStack(Material.STONE) : source.clone();
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(StringUtil.color(name));
            meta.setLore(StringUtil.color(new ArrayList<>(lore)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> getTournamentKitPool(final Tournament tournament) {
        final List<String> configured;
        if (!tournament.getAllowedKits().isEmpty()) {
            configured = tournament.getAllowedKits();
        } else if (tournament.getKitMode() == TournamentKitMode.PLAYER_CHOICE) {
            configured = settings.kitSelectionKits.isEmpty()
                    ? plugin.getKitManager().getNames(false)
                    : settings.kitSelectionKits;
        } else {
            configured = tournament.getKit() == null || tournament.getKit().isBlank()
                    ? List.of()
                    : List.of(tournament.getKit());
        }
        final List<String> kits = configured.stream()
                .filter(name -> plugin.getKitManager().get(name) != null)
                .distinct()
                .collect(Collectors.toList());
        if (kits.isEmpty()
                && tournament.getKitMode() == TournamentKitMode.PLAYER_CHOICE
                && plugin.getKitManager().get(tournament.getKit()) != null) {
            kits.add(tournament.getKit());
        }
        return kits;
    }

    private String resolveDirectMatchKit(final Tournament tournament, final List<String> kitPool) {
        if (kitPool.size() == 1) {
            return kitPool.get(0);
        }
        return tournament.getKit();
    }

    private void syncKitModeWithAllowedKits(final Tournament tournament) {
        if (tournament.getAllowedKits().size() == 1) {
            tournament.setKitMode(TournamentKitMode.FIXED);
        } else if (tournament.getAllowedKits().size() > 1) {
            tournament.setKitMode(TournamentKitMode.PLAYER_CHOICE);
        }
    }

    private void toggleValue(final List<String> values, final String value) {
        final Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().equalsIgnoreCase(value)) {
                iterator.remove();
                return;
            }
        }
        values.add(value);
    }

    private boolean isKitBlocked(final String playerName, final String kitName) {
        final Deque<String> history = playerKitHistory.get(playerName.toLowerCase(Locale.ROOT));
        return history != null
                && history.size() >= 2
                && history.stream().limit(2).allMatch(previous -> previous.equalsIgnoreCase(kitName));
    }

    private void rememberPlayedKit(final String playerName, final String kitName) {
        final Deque<String> history = playerKitHistory.computeIfAbsent(playerName.toLowerCase(Locale.ROOT), key -> new ArrayDeque<>());
        history.addFirst(kitName);
        while (history.size() > 2) {
            history.removeLast();
        }
        saveData();
    }

    private String selectionKey(final String tournament, final int round, final int match) {
        return normalize(tournament) + ":" + round + ":" + match;
    }

    private void teleportToTournamentLobby(final Player player) {
        final Location location = getLobby();
        if (location != null) {
            plugin.getTeleport().tryTeleport(player, location);
        }
    }

    private void refreshHologramAfterLoad(final Tournament tournament) {
        if (!settings.cmiEnabled || getBaseHologramName(tournament).isBlank()) {
            return;
        }
        for (String hologram : getTournamentHologramNamesIncludingLegacy(tournament)) {
            removeCmiHologram(hologram);
        }
        updateHologram(tournament);
    }

    private void changed(final String name) {
        changed(Objects.requireNonNull(getTournament(name)));
    }

    private void changed(final Tournament tournament) {
        saveData();
        if (settings.autoUpdateHologram) {
            updateHologram(tournament);
        }
    }

    private Tournament findPlayerTournament(final String player) {
        return tournaments.values().stream()
                .filter(tournament -> tournament.hasParticipant(player))
                .filter(tournament -> tournament.getStatus() == TournamentStatus.CREATED || tournament.getStatus() == TournamentStatus.IN_PROGRESS)
                .findFirst()
                .orElseGet(() -> tournaments.values().stream()
                        .filter(tournament -> tournament.hasParticipant(player))
                        .findFirst()
                        .orElse(null));
    }

    private Tournament findPlayerOpenTournament(final String player) {
        return tournaments.values().stream()
                .filter(tournament -> tournament.hasParticipant(player))
                .filter(tournament -> tournament.getStatus() == TournamentStatus.CREATED
                        || (tournament.getStatus() == TournamentStatus.IN_PROGRESS && !tournament.isEliminated(player)))
                .findFirst()
                .orElse(null);
    }

    private TournamentMatch findPlayerCurrentMatch(final Tournament tournament, final String player) {
        return tournament.getMatches().values().stream()
                .flatMap(round -> round.values().stream())
                .filter(match -> match.hasPlayer(player))
                .filter(match -> match.getStatus() == TournamentMatchStatus.READY
                        || match.getStatus() == TournamentMatchStatus.STARTING
                        || match.getStatus() == TournamentMatchStatus.IN_PROGRESS
                        || match.getStatus() == TournamentMatchStatus.WAITING_PLAYER
                        || match.getStatus() == TournamentMatchStatus.WAITING)
                .findFirst()
                .orElse(null);
    }

    private TournamentMatch findNextStartableMatch(final Tournament tournament) {
        return tournament.getMatches().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> entry.getValue().values().stream()
                        .sorted(Comparator.comparingInt(TournamentMatch::getNumber)))
                .filter(match -> match.isKnown() && !match.isBye())
                .filter(match -> match.getStatus() == TournamentMatchStatus.READY
                        || match.getStatus() == TournamentMatchStatus.WAITING_PLAYER)
                .findFirst()
                .orElse(null);
    }

    private void recoverStaleKitSelections(final Tournament tournament) {
        boolean changed = false;
        for (Map<Integer, TournamentMatch> round : tournament.getMatches().values()) {
            for (TournamentMatch match : round.values()) {
                if (match.getStatus() != TournamentMatchStatus.STARTING
                        || tournament.getKitMode() != TournamentKitMode.PLAYER_CHOICE
                        || pendingKitSelections.containsKey(selectionKey(tournament.getName(), match.getRound(), match.getNumber()))) {
                    continue;
                }
                match.setStatus(TournamentMatchStatus.READY);
                match.setWaitingSince(0L);
                changed = true;
            }
        }
        if (changed) {
            changed(tournament);
        }
    }

    private TournamentMatch findTournamentMatch(final Tournament tournament, final String player1, final String player2) {
        return tournament.getMatches().values().stream()
                .flatMap(round -> round.values().stream())
                .filter(match -> match.hasPlayer(player1) && match.hasPlayer(player2))
                .findFirst()
                .orElse(null);
    }

    private String currentArenaName() {
        return settings.defaultArena == null || settings.defaultArena.isBlank() ? "Ожидает" : settings.defaultArena;
    }

    private boolean startTournamentDuel(final Tournament tournament, final Player player1, final Player player2, final Settings duelSettings) {
        final ArenaImpl arena = duelSettings.getArena();
        final boolean reservedArena = settings.reserveDefaultArena
                && arena != null
                && arena.getName().equalsIgnoreCase(tournament.getReservedArena());

        if (!reservedArena) {
            return plugin.getDuelManager().startTournamentMatch(player1, player2, duelSettings);
        }

        final boolean disabledBeforeStart = arena.isDisabled();
        if (disabledBeforeStart) {
            arena.setDisabled(Bukkit.getConsoleSender(), false);
        }

        final boolean started = plugin.getDuelManager().startTournamentMatch(player1, player2, duelSettings);
        arena.setDisabled(Bukkit.getConsoleSender(), true);
        return started;
    }

    private boolean preparePlayersForTournamentMatch(final Player player1, final Player player2) {
        boolean needsDelay = false;
        needsDelay |= preparePlayerForTournamentMatch(player1);
        needsDelay |= preparePlayerForTournamentMatch(player2);
        return needsDelay;
    }

    private boolean preparePlayerForTournamentMatch(final Player player) {
        boolean needsDelay = false;
        final ArenaImpl arena = plugin.getArenaManager().get(player);
        if (arena != null && arena.getMatch() != null) {
            if (activeMatches.containsKey(arena.getMatch())) {
                return false;
            }
            plugin.getDuelManager().forceEndMatch(player, Reason.TIE);
            player.sendMessage(StringUtil.color("&eВаша обычная дуэль завершена, потому что начинается турнирный матч."));
            needsDelay = true;
        }

        if (player.isDead()) {
            try {
                player.spigot().respawn();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Could not force respawn tournament player " + player.getName() + ": " + ex.getMessage());
            }
            needsDelay = true;
        }
        return needsDelay;
    }

    private void reserveTournamentArena(final Tournament tournament) {
        if (!settings.reserveDefaultArena || hasTournamentArenaLimit(tournament)) {
            return;
        }

        final ArenaImpl arena = getArenaForTournament(tournament, plugin.getKitManager().get(tournament.getKit()));
        if (arena == null) {
            return;
        }

        tournament.setReservedArena(arena.getName());
        tournament.setReservedArenaWasDisabled(arena.isDisabled());
        if (!arena.isDisabled()) {
            arena.setDisabled(Bukkit.getConsoleSender(), true);
        }
    }

    private void releaseTournamentArena(final Tournament tournament) {
        if (!settings.reserveDefaultArena || tournament.getReservedArena() == null) {
            return;
        }

        final ArenaImpl arena = plugin.getArenaManager().get(tournament.getReservedArena());
        if (arena != null && !tournament.isReservedArenaWasDisabled() && arena.isDisabled()) {
            arena.setDisabled(Bukkit.getConsoleSender(), false);
        }

        tournament.setReservedArena(null);
        tournament.setReservedArenaWasDisabled(false);
        saveData();
    }

    private void setSpectatorReturnLobby(final Player spectator) {
        final PlayerInfo info = plugin.getPlayerManager().get(spectator);
        if (info != null && getLobby() != null) {
            info.setLocation(getLobby().clone());
        }
    }

    private void setTournamentReturnLobby(final Player[] players) {
        final Location returnLobby = getLobby();
        if (returnLobby == null) {
            return;
        }
        for (Player player : players) {
            final PlayerInfo info = plugin.getPlayerManager().get(player);
            if (info != null) {
                info.setLocation(returnLobby.clone());
            }
        }
    }

    private void updateHologram(final Tournament tournament) {
        if (!settings.cmiEnabled || tournament.getHologramName() == null || tournament.getHologramName().isBlank()) {
            return;
        }

        updateHologramLines(tournament, "main", buildMainHologramLines(tournament));
        updateHologramLines(tournament, "active", buildActiveMatchesHologramLines(tournament));
        updateHologramLines(tournament, "next", buildNextRoundHologramLines(tournament));
        removeCmiHologram(getHologramName(tournament, "spectate"));
    }

    private boolean removeTournamentHologram(final Tournament tournament, final boolean clearData) {
        if (!settings.cmiEnabled || getBaseHologramName(tournament).isBlank()) {
            return false;
        }

        for (String hologram : getTournamentHologramNamesIncludingLegacy(tournament)) {
            removeCmiHologram(hologram);
        }
        if (clearData) {
            tournament.setHologramName(null);
            tournament.setHologramWorld(null);
            tournament.setHologramX(0D);
            tournament.setHologramY(0D);
            tournament.setHologramZ(0D);
            tournament.setHologramYaw(0F);
            tournament.setHologramPitch(0F);
            tournament.setHologramDirection(null);
            tournament.setHologramPage(1);
            tournament.setHologramMode("CURRENT_ROUND");
            saveData();
        }
        return true;
    }

    private void updateHologramLines(final Tournament tournament, final String type, final List<String> lines) {
        final String hologram = getHologramName(tournament, type);
        if (updateCmiHologramWithApi(tournament, type, hologram, lines)) {
            saveCmiHologramLines(tournament, type, hologram, lines);
            refreshCmiHologram(hologram);
            return;
        }

        for (int i = 0; i < settings.maxLinesPerPage; i++) {
            final String text = i < lines.size() ? lines.get(i) : "";
            dispatch(Bukkit.getConsoleSender(), settings.cmiSetLineCommand
                    .replace("%hologram%", hologram)
                    .replace("%line%", String.valueOf(i + 1))
                    .replace("%text%", text));
        }
        saveCmiHologramLines(tournament, type, hologram, lines);
        refreshCmiHologram(hologram);
    }

    private boolean updateCmiHologramWithApi(final Tournament tournament, final String type, final String hologramName, final List<String> lines) {
        final org.bukkit.plugin.Plugin cmiPlugin = Bukkit.getPluginManager().getPlugin("CMI");
        if (cmiPlugin == null || !cmiPlugin.isEnabled()) {
            return false;
        }

        final Location location = getHologramLocation(tournament, type);
        if (location == null) {
            return false;
        }

        if (updateCmiHologramV2(hologramName, lines, location)) {
            return true;
        }

        return updateLegacyCmiHologram(hologramName, lines, location);
    }

    private boolean updateCmiHologramV2(final String hologramName, final List<String> lines, final Location location) {
        try {
            final Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
            final Object cmi = cmiClass.getMethod("getInstance").invoke(null);
            if (cmi == null) {
                return false;
            }

            final Object manager = cmiClass.getMethod("getHologramManager_v2").invoke(cmi);
            if (manager == null) {
                return false;
            }

            final Class<?> managerClass = manager.getClass();
            Object hologram = managerClass.getMethod("getByName", String.class).invoke(manager, hologramName);
            if (hologram == null) {
                final Class<?> hologramClass = Class.forName("com.Zrips.CMI.Modules.Holograms_v2.CMIHologram_v2");
                hologram = hologramClass.getConstructor(String.class, Location.class).newInstance(hologramName, location);
                managerClass.getMethod("add", hologramClass).invoke(manager, hologram);
            }

            final Class<?> hologramClass = hologram.getClass();
            if (settings.manageHologramLocations) {
                hologramClass.getMethod("setLocation", Location.class).invoke(hologram, location);
            }
            hologramClass.getMethod("setLines", List.class).invoke(hologram, List.copyOf(lines));
            hologramClass.getMethod("makePersistent").invoke(hologram);
            hologramClass.getMethod("saveToFile").invoke(hologram);
            hologramClass.getMethod("update").invoke(hologram);
            hologramClass.getMethod("refresh").invoke(hologram);
            managerClass.getMethod("save").invoke(manager);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Could not update CMI v2 hologram '" + hologramName + "' via API: " + ex.getMessage());
            return false;
        }
    }

    private boolean updateLegacyCmiHologram(final String hologramName, final List<String> lines, final Location location) {
        try {
            final Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
            final Object cmi = cmiClass.getMethod("getInstance").invoke(null);
            if (cmi == null) {
                return false;
            }

            final Object manager = cmiClass.getMethod("getHologramManager").invoke(cmi);
            if (manager == null) {
                return false;
            }

            final Class<?> managerClass = manager.getClass();
            Object hologram = managerClass.getMethod("getByName", String.class).invoke(manager, hologramName);
            if (hologram == null) {
                final Class<?> hologramClass = Class.forName("com.Zrips.CMI.Modules.Holograms.CMIHologram");
                hologram = hologramClass.getConstructor(String.class, Location.class).newInstance(hologramName, location);
                managerClass.getMethod("addHologram", hologramClass).invoke(manager, hologram);
            }

            final Class<?> hologramClass = hologram.getClass();
            if (settings.manageHologramLocations) {
                hologramClass.getMethod("setLoc", Location.class).invoke(hologram, location);
            }
            hologramClass.getMethod("setLines", List.class).invoke(hologram, List.copyOf(lines));
            hologramClass.getMethod("makePersistent").invoke(hologram);
            hologramClass.getMethod("update").invoke(hologram);
            hologramClass.getMethod("refresh").invoke(hologram);
            managerClass.getMethod("save").invoke(manager);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Could not update legacy CMI hologram '" + hologramName + "' via API: " + ex.getMessage());
            return false;
        }
    }

    private void removeCmiHologram(final String hologramName) {
        if (hologramName == null || hologramName.isBlank()) {
            return;
        }

        if (!removeCmiHologramV2(hologramName) && !removeLegacyCmiHologram(hologramName)) {
            dispatch(Bukkit.getConsoleSender(), settings.cmiRemoveCommand.replace("%hologram%", hologramName));
        }
        removeCmiHologramFromFile(hologramName);
    }

    private boolean removeCmiHologramV2(final String hologramName) {
        try {
            final Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
            final Object cmi = cmiClass.getMethod("getInstance").invoke(null);
            if (cmi == null) {
                return false;
            }

            final Object manager = cmiClass.getMethod("getHologramManager_v2").invoke(cmi);
            if (manager == null) {
                return false;
            }

            final Class<?> managerClass = manager.getClass();
            final Object hologram = managerClass.getMethod("getByName", String.class).invoke(manager, hologramName);
            if (hologram == null) {
                return false;
            }

            final boolean removed = invokeFirstAvailable(manager, List.of("remove", "removeHologram", "delete"), hologram)
                    || invokeFirstAvailable(manager, List.of("remove", "removeHologram", "delete"), hologramName)
                    || invokeFirstAvailable(hologram, List.of("remove", "delete"), new Object[0]);
            if (removed) {
                invokeFirstAvailable(manager, List.of("save"), new Object[0]);
            }
            return removed;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Could not remove CMI v2 hologram '" + hologramName + "' via API: " + ex.getMessage());
            return false;
        }
    }

    private boolean removeLegacyCmiHologram(final String hologramName) {
        try {
            final Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
            final Object cmi = cmiClass.getMethod("getInstance").invoke(null);
            if (cmi == null) {
                return false;
            }

            final Object manager = cmiClass.getMethod("getHologramManager").invoke(cmi);
            if (manager == null) {
                return false;
            }

            final Class<?> managerClass = manager.getClass();
            final Object hologram = managerClass.getMethod("getByName", String.class).invoke(manager, hologramName);
            if (hologram == null) {
                return false;
            }

            final boolean removed = invokeFirstAvailable(manager, List.of("remove", "removeHologram", "delete"), hologram)
                    || invokeFirstAvailable(manager, List.of("remove", "removeHologram", "delete"), hologramName)
                    || invokeFirstAvailable(hologram, List.of("remove", "delete"), new Object[0]);
            if (removed) {
                invokeFirstAvailable(manager, List.of("save"), new Object[0]);
            }
            return removed;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Could not remove legacy CMI hologram '" + hologramName + "' via API: " + ex.getMessage());
            return false;
        }
    }

    private boolean invokeFirstAvailable(final Object target, final List<String> methodNames, final Object... args) {
        for (String methodName : methodNames) {
            for (java.lang.reflect.Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                    continue;
                }
                try {
                    method.invoke(target, args);
                    return true;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    break;
                }
            }
        }
        return false;
    }

    private void refreshCmiHologram(final String hologramName) {
        if (!refreshCmiHologramV2(hologramName)) {
            refreshLegacyCmiHologram(hologramName);
        }
    }

    private boolean refreshCmiHologramV2(final String hologramName) {
        try {
            final Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
            final Object cmi = cmiClass.getMethod("getInstance").invoke(null);
            if (cmi == null) {
                return false;
            }
            final Object manager = cmiClass.getMethod("getHologramManager_v2").invoke(cmi);
            if (manager == null) {
                return false;
            }
            final Object hologram = manager.getClass().getMethod("getByName", String.class).invoke(manager, hologramName);
            if (hologram == null) {
                return false;
            }
            invokeFirstAvailable(hologram, List.of("update", "refresh"), new Object[0]);
            invokeFirstAvailable(hologram, List.of("refresh", "update"), new Object[0]);
            invokeFirstAvailable(manager, List.of("save"), new Object[0]);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private boolean refreshLegacyCmiHologram(final String hologramName) {
        try {
            final Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
            final Object cmi = cmiClass.getMethod("getInstance").invoke(null);
            if (cmi == null) {
                return false;
            }
            final Object manager = cmiClass.getMethod("getHologramManager").invoke(cmi);
            if (manager == null) {
                return false;
            }
            final Object hologram = manager.getClass().getMethod("getByName", String.class).invoke(manager, hologramName);
            if (hologram == null) {
                return false;
            }
            invokeFirstAvailable(hologram, List.of("update", "refresh"), new Object[0]);
            invokeFirstAvailable(hologram, List.of("refresh", "update"), new Object[0]);
            invokeFirstAvailable(manager, List.of("save"), new Object[0]);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private void removeCmiHologramFromFile(final String hologramName) {
        final org.bukkit.plugin.Plugin cmiPlugin = Bukkit.getPluginManager().getPlugin("CMI");
        if (cmiPlugin == null) {
            return;
        }

        final File hologramsFile = new File(cmiPlugin.getDataFolder(), "Saves/Holograms.yml");
        if (!hologramsFile.exists()) {
            return;
        }

        final FileConfiguration config = YamlConfiguration.loadConfiguration(hologramsFile);
        if (!config.contains(hologramName)) {
            return;
        }

        config.set(hologramName, null);
        try {
            config.save(hologramsFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not remove CMI hologram '" + hologramName + "' from file: " + ex.getMessage());
        }
    }

    private void saveCmiHologramLines(final Tournament tournament, final String type, final String hologramName, final List<String> lines) {
        final org.bukkit.plugin.Plugin cmiPlugin = Bukkit.getPluginManager().getPlugin("CMI");
        if (cmiPlugin == null) {
            return;
        }

        final Location location = getHologramLocation(tournament, type);
        if (location == null) {
            return;
        }

        final File hologramsFile = new File(cmiPlugin.getDataFolder(), "Saves/Holograms.yml");
        final File parent = hologramsFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create CMI hologram saves directory: " + parent.getPath());
            return;
        }

        final FileConfiguration config = YamlConfiguration.loadConfiguration(hologramsFile);
        if (settings.manageHologramLocations) {
            config.set(hologramName + ".Loc", formatCmiLocation(location));
        }
        if (!config.contains(hologramName + ".Filler")) {
            config.set(hologramName + ".Filler", 245);
        }
        if (!config.contains(hologramName + ".NewDisplay")) {
            config.set(hologramName + ".NewDisplay", true);
        }
        config.set(hologramName + ".ND.Bill", "FIXED");
        config.set(hologramName + ".ND.Yaw", cmiDisplayYaw(location.getYaw()));
        config.set(hologramName + ".Lines", List.copyOf(lines));

        try {
            config.save(hologramsFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save CMI hologram '" + hologramName + "' lines: " + ex.getMessage());
        }
    }

    private String formatCmiLocation(final Location location) {
        return String.format(Locale.US, "%s;%.2f;%.2f;%.2f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ());
    }

    private Location getHologramLocation(final Tournament tournament, final String type) {
        if (tournament.getHologramWorld() == null || tournament.getHologramWorld().isBlank()) {
            return null;
        }

        final org.bukkit.World world = Bukkit.getWorld(tournament.getHologramWorld());
        if (world == null) {
            return null;
        }

        final Location location = new Location(world,
                tournament.getHologramX(),
                tournament.getHologramY(),
                tournament.getHologramZ(),
                tournament.getHologramYaw(),
                tournament.getHologramPitch());
        return applyHologramOffset(tournament, location, type);
    }

    private Location applyHologramOffset(final Tournament tournament, final Location location, final String type) {
        final HologramOffset configuredOffset = settings.hologramUseOffsets
                ? settings.hologramOffsets.getOrDefault(type, HologramOffset.ZERO)
                : HologramOffset.ZERO;
        final int panelIndex = hologramPanelIndex(type);
        final double baseYaw = resolveBaseHologramYaw(tournament, location);
        final HologramOffset panelOffset = resolvePanelOffset(panelIndex);
        final double yaw = Math.toRadians(baseYaw);
        final double rightX = Math.cos(yaw);
        final double rightZ = Math.sin(yaw);
        final double forwardX = -Math.sin(yaw);
        final double forwardZ = Math.cos(yaw);

        final Location shifted = location.clone().add(
                rightX * (panelOffset.x + configuredOffset.x) + forwardX * (panelOffset.z + configuredOffset.z),
                panelOffset.y + configuredOffset.y,
                rightZ * (panelOffset.x + configuredOffset.x) + forwardZ * (panelOffset.z + configuredOffset.z));
        shifted.setYaw((float) baseYaw);
        shifted.setPitch(0F);
        return shifted;
    }

    private double resolveBaseHologramYaw(final Tournament tournament, final Location location) {
        final String direction = locationDirection(tournament);
        return switch (direction) {
            case "NORTH" -> 180D;
            case "EAST" -> -90D;
            case "SOUTH" -> 0D;
            case "WEST" -> 90D;
            default -> snapYawToCardinal(location.getYaw());
        };
    }

    private HologramOffset resolvePanelOffset(final int panelIndex) {
        if (panelIndex == 1) {
            return new HologramOffset(settings.hologramPanelSpacing, 0D, 0D);
        }
        if (panelIndex == 2) {
            return new HologramOffset(-settings.hologramPanelSpacing, 0D, 0D);
        }
        return HologramOffset.ZERO;
    }

    private int hologramPanelIndex(final String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "active" -> 1;
            case "next" -> 2;
            default -> 0;
        };
    }

    private double snapYawToCardinal(final double yaw) {
        final double normalized = ((yaw % 360D) + 360D) % 360D;
        final int quadrant = (int) Math.floor((normalized + 45D) / 90D) % 4;
        return switch (quadrant) {
            case 0 -> 0D;
            case 1 -> 90D;
            case 2 -> 180D;
            default -> -90D;
        };
    }

    private int cmiDisplayYaw(final float yaw) {
        return (int) Math.round(((yaw % 360F) + 360F) % 360F);
    }

    private String locationDirection(final Tournament tournament) {
        final String direction = tournament.getHologramDirection() == null
                ? settings.hologramDirection
                : tournament.getHologramDirection();
        return direction == null ? "AUTO" : direction.toUpperCase(Locale.ROOT);
    }

    private List<String> getTournamentHologramNames(final Tournament tournament) {
        return List.of(
                getHologramName(tournament, "main"),
                getHologramName(tournament, "active"),
                getHologramName(tournament, "next"));
    }

    private List<String> getTournamentHologramNamesIncludingLegacy(final Tournament tournament) {
        final List<String> names = new ArrayList<>(getTournamentHologramNames(tournament));
        names.add(getHologramName(tournament, "spectate"));
        return names;
    }

    private String getHologramName(final Tournament tournament, final String type) {
        final String base = getBaseHologramName(tournament);
        return "main".equals(type) ? base : base + "_" + type;
    }

    private String getBaseHologramName(final Tournament tournament) {
        final String saved = tournament.getHologramName();
        if (saved != null && !saved.isBlank()) {
            return saved;
        }
        return settings.hologramNameFormat.replace("%name%", tournament.getName());
    }

    private List<String> buildMainHologramLines(final Tournament tournament) {
        return hologramRenderer.buildMain(tournament, settings.maxLinesPerPage);
    }

    private List<String> buildActiveMatchesHologramLines(final Tournament tournament) {
        return hologramRenderer.buildActive(tournament, settings.maxLinesPerPage);
    }

    private List<String> buildNextRoundHologramLines(final Tournament tournament) {
        return hologramRenderer.buildNext(tournament, settings.maxLinesPerPage, settings.showByeMatches);
    }

    private List<TournamentMatch> activeMatches(final Tournament tournament) {
        return tournament.getMatches().values().stream()
                .flatMap(round -> round.values().stream())
                .filter(match -> match.getStatus().isRunning())
                .collect(Collectors.toList());
    }

    private void terminateActiveMatches(final Tournament tournament,
                                        final Predicate<ActiveTournamentMatch> filter) {
        final List<Match> matches = activeMatches.entrySet().stream()
                .filter(entry -> entry.getValue().tournament.equalsIgnoreCase(tournament.getName()))
                .filter(entry -> filter.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        for (Match activeMatch : matches) {
            activeMatches.remove(activeMatch);
            if (!(activeMatch instanceof DuelMatch duelMatch)) {
                continue;
            }
            final Player participant = duelMatch.getAlivePlayers().stream().findFirst().orElse(null);
            if (participant != null) {
                plugin.getDuelManager().forceEndMatch(participant, Reason.OTHER);
            } else if (duelMatch.getArena().getMatch() == duelMatch) {
                duelMatch.getArena().endMatch(null, null, Reason.OTHER);
            }
        }
    }

    private void cleanupPendingSelections(final Tournament tournament,
                                          final Predicate<PendingKitSelection> filter) {
        new ArrayList<>(pendingKitSelections.values()).stream()
                .filter(selection -> selection.tournament.equalsIgnoreCase(tournament.getName()))
                .filter(filter)
                .forEach(this::cleanupKitSelection);
    }

    private List<String> buildHologramLines(final Tournament tournament) {
        final List<TournamentMatch> matches = getVisibleHologramMatches(tournament);
        final int maxPage = Math.max(1, getHologramMaxPage(tournament, matches.size()));
        if (tournament.getHologramPage() > maxPage) {
            tournament.setHologramPage(maxPage);
            saveData();
        }

        final List<String> header = applyTournamentPlaceholders(settings.template.header, tournament, maxPage);
        final List<String> footer = applyTournamentPlaceholders(settings.template.footer, tournament, maxPage);
        final int matchCapacity = Math.max(1, settings.maxLinesPerPage - header.size() - footer.size());
        final int from = Math.min((tournament.getHologramPage() - 1) * matchCapacity, matches.size());
        final int to = Math.min(from + matchCapacity, matches.size());

        final List<String> lines = new ArrayList<>(settings.maxLinesPerPage);
        lines.addAll(header);
        if (matches.isEmpty()) {
            lines.add(applyMatchPlaceholders(settings.template.waitingLine, tournament, null, maxPage));
        } else {
            for (TournamentMatch match : matches.subList(from, to)) {
                lines.add(applyMatchPlaceholders(templateFor(match), tournament, match, maxPage));
            }
        }
        lines.addAll(footer);
        return lines.size() > settings.maxLinesPerPage ? lines.subList(0, settings.maxLinesPerPage) : lines;
    }

    private List<TournamentMatch> getVisibleHologramMatches(final Tournament tournament) {
        final List<Integer> rounds;
        if ("ROUND".equalsIgnoreCase(tournament.getHologramMode())) {
            rounds = List.of(tournament.getHologramRound() > 0 ? tournament.getHologramRound() : tournament.getCurrentRound());
        } else if (settings.showOnlyCurrentRound || "CURRENT_ROUND".equalsIgnoreCase(tournament.getHologramMode())) {
            rounds = List.of(tournament.getCurrentRound());
        } else {
            rounds = new ArrayList<>(tournament.getMatches().keySet());
        }

        return rounds.stream()
                .flatMap(round -> tournament.getRoundMatches(round).stream())
                .filter(match -> settings.showByeMatches || match.getStatus() != TournamentMatchStatus.BYE)
                .filter(match -> settings.showFinishedMatches || match.getStatus() != TournamentMatchStatus.FINISHED)
                .collect(Collectors.toList());
    }

    private int getHologramMaxPage(final Tournament tournament) {
        return getHologramMaxPage(tournament, getVisibleHologramMatches(tournament).size());
    }

    private int getHologramMaxPage(final Tournament tournament, final int matchCount) {
        final int headerSize = applyTournamentPlaceholders(settings.template.header, tournament, 1).size();
        final int footerSize = applyTournamentPlaceholders(settings.template.footer, tournament, 1).size();
        final int matchCapacity = Math.max(1, settings.maxLinesPerPage - headerSize - footerSize);
        return Math.max(1, (int) Math.ceil(matchCount / (double) matchCapacity));
    }

    private String templateFor(final TournamentMatch match) {
        if (match.getWinner() != null && match.getStatus() == TournamentMatchStatus.BYE) {
            return settings.template.byeLine;
        }
        if (match.getWinner() != null) {
            return settings.template.winnerLine;
        }
        if (!match.isKnown()) {
            return settings.template.waitingLine;
        }
        return settings.template.matchLine;
    }

    private String roundName(final Tournament tournament, final int round) {
        final int totalRounds = tournament.getMatches().size();
        if (round == totalRounds) {
            return "Финал";
        }
        if (round == totalRounds - 1) {
            return "Полуфинал";
        }
        return "Раунд " + round;
    }


    private List<String> applyTournamentPlaceholders(final List<String> lines, final Tournament tournament, final int maxPage) {
        return lines.stream()
                .map(line -> applyTournamentPlaceholders(line, tournament, maxPage))
                .collect(Collectors.toList());
    }

    private String applyMatchPlaceholders(final String line, final Tournament tournament, final TournamentMatch match, final int maxPage) {
        String result = applyTournamentPlaceholders(line, tournament, maxPage);
        result = result.replace("%round%", match == null ? "" : String.valueOf(match.getRound()));
        result = result.replace("%match%", match == null ? "" : String.valueOf(match.getNumber()));
        result = result.replace("%player1%", match == null ? "Ожидает" : participantName(match.getPlayer1()));
        result = result.replace("%player2%", match == null ? "Ожидает" : participantName(match.getPlayer2()));
        result = result.replace("%winner%", match == null ? "" : participantName(match.getWinner()));
        result = result.replace("%loser%", match == null || match.getWinner() == null ? "" : participantName(match.getOpponent(match.getWinner())));
        result = result.replace("%match_status%", match == null ? "" : formatMatchStatus(match.getStatus()));
        result = result.replace("%waiting_elapsed%", match == null ? "" : formatWaitingElapsed(match));
        result = result.replace("%waiting_remaining%", match == null ? "" : formatWaitingRemaining(match));
        return result;
    }

    private String applyTournamentPlaceholders(final String line, final Tournament tournament, final int maxPage) {
        return line
                .replace("%tournament_name%", tournament.getName())
                .replace("%kit%", tournament.getKit())
                .replace("%arena%", settings.defaultArena == null || settings.defaultArena.isBlank() ? "Ожидает" : settings.defaultArena)
                .replace("%status%", formatTournamentStatus(tournament.getStatus()))
                .replace("%current_round%", String.valueOf(tournament.getCurrentRound()))
                .replace("%page%", String.valueOf(tournament.getHologramPage()))
                .replace("%max_page%", String.valueOf(maxPage))
                .replace("%players_count%", String.valueOf(tournament.getPlayers().size()))
                .replace("%remaining_players%", String.valueOf(countRemainingPlayers(tournament)))
                .replace("%max_players%", String.valueOf(tournament.getMatches().isEmpty() ? tournament.getPlayers().size() : nextPowerOfTwo(tournament.getPlayers().size())))
                .replace("%spectators_count%", String.valueOf(countTournamentSpectators(tournament)));
    }

    private int countRemainingPlayers(final Tournament tournament) {
        return hologramRenderer.countRemainingPlayers(tournament);
    }

    private int countTournamentSpectators(final Tournament tournament) {
        return (int) Bukkit.getOnlinePlayers().stream()
                .filter(player -> plugin.getSpectateManager().isSpectating(player))
                .count();
    }

    public String formatTournamentStatus(final TournamentStatus status) {
        return hologramRenderer.formatTournamentStatus(status);
    }

    public String formatTournamentActivityStatus(final Tournament tournament) {
        return hologramRenderer.formatActivityStatus(tournament);
    }

    public String formatMatchStatus(final TournamentMatchStatus status) {
        return hologramRenderer.formatMatchStatus(status);
    }

    public String formatWaitingElapsed(final TournamentMatch match) {
        if (match == null || match.getWaitingSince() <= 0L) {
            return "0:00";
        }
        return formatDuration(Math.max(0L, System.currentTimeMillis() - match.getWaitingSince()));
    }

    public String formatWaitingRemaining(final TournamentMatch match) {
        if (match == null || match.getWaitingSince() <= 0L || settings.autoForfeitMinutes <= 0) {
            return "выключено";
        }
        final long timeout = settings.autoForfeitMinutes * 60_000L;
        final long remaining = Math.max(0L, timeout - (System.currentTimeMillis() - match.getWaitingSince()));
        return formatDuration(remaining);
    }

    private String formatDuration(final long millis) {
        final long totalSeconds = Math.max(0L, millis / 1000L);
        final long minutes = totalSeconds / 60L;
        final long seconds = totalSeconds % 60L;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private void dispatch(final CommandSender sender, final String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        Bukkit.dispatchCommand(sender, StringUtil.color(command));
    }

    private String displayName(final String name) {
        return name == null || name.isBlank() ? "Ожидание игрока" : name;
    }

    private String effectiveKitDescription(final Tournament tournament) {
        if (tournament.getAllowedKits().size() == 1) {
            return "Кит: &f" + tournament.getAllowedKits().get(0);
        }
        if (tournament.getAllowedKits().size() > 1 || tournament.getKitMode() == TournamentKitMode.PLAYER_CHOICE) {
            return "Тип турнира: &aВыбор китов";
        }
        if (tournament.getKit() == null || tournament.getKit().isBlank()) {
            return "Кит: &cне выбран";
        }
        return "Кит: &f" + tournament.getKit();
    }

    public String participantName(final String name) {
        return "BYE".equalsIgnoreCase(name) ? "Проход без соперника" : displayName(name);
    }

    private void announceWaitingPlayer(final Tournament tournament, final TournamentMatch match, final boolean player1Missing, final boolean player2Missing) {
        final List<String> missing = new ArrayList<>();
        if (player1Missing) {
            missing.add(match.getPlayer1());
        }
        if (player2Missing) {
            missing.add(match.getPlayer2());
        }
        final String missingText = missing.stream().map(this::participantName).collect(Collectors.joining(", "));
        final int minutes = Math.max(1, settings.autoForfeitMinutes);
        final List<String> lines = new ArrayList<>();
        lines.add("&8&m                                                ");
        lines.add("&6&lТурнир: &e" + tournament.getName());
        lines.add("&eМатч ожидает игрока: &f" + missingText);
        lines.add("&7Раунд: &f" + match.getRound() + " &8| &7Матч: &f#" + match.getNumber());
        lines.add("&7Ожидание: &fдо " + minutes + " мин.");
        if (settings.autoForfeitMinutes > 0) {
            lines.add("&7Если игрок не вернётся вовремя, победа будет засчитана сопернику.");
        } else {
            lines.add("&7Автопоражение выключено, организатор решит матч вручную.");
        }
        lines.add("&8&m                                                ");
        sendToTournamentPlayers(tournament, lines);
    }

    private void announceMatchStart(final Tournament tournament, final TournamentMatch match, final ArenaImpl arena) {
        if (!settings.broadcastMatchStart) {
            return;
        }

        sendToTournamentPlayers(tournament, List.of(
                "&8&m                                                ",
                "&6&lТурнир: &e" + tournament.getName(),
                "&aМатч начался: &f" + participantName(match.getPlayer1())
                        + " &7vs &f" + participantName(match.getPlayer2()),
                "&7Раунд: &f" + match.getRound() + " &8| &7Матч: &f#" + match.getNumber()
                        + (arena == null ? "" : " &8| &7Арена: &e" + arena.getName()),
                "&8&m                                                "));
    }

    private void notifyMatchStarting(final Tournament tournament, final TournamentMatch match, final ArenaImpl arena, final int seconds) {
        if (match.getStatus() != TournamentMatchStatus.STARTING) {
            return;
        }
        final Player player1 = Bukkit.getPlayerExact(match.getPlayer1());
        final Player player2 = Bukkit.getPlayerExact(match.getPlayer2());
        notifyMatchStarting(player1, tournament, match, arena, seconds);
        notifyMatchStarting(player2, tournament, match, arena, seconds);
    }

    private void notifyMatchStarting(final Player player, final Tournament tournament, final TournamentMatch match, final ArenaImpl arena, final int seconds) {
        if (player == null) {
            return;
        }
        if (seconds == 30) {
            player.sendMessage(StringUtil.color("&c⚔ &eВаш турнирный матч начинается через &f30 &eсекунд."));
            player.sendMessage(StringUtil.color("&7Соперник: &f" + displayName(match.getOpponent(player.getName()))));
            player.sendMessage(StringUtil.color("&7Арена: &f" + (arena == null ? currentArenaName() : arena.getName())));
            player.sendMessage(StringUtil.color("&7" + effectiveKitDescription(tournament)));
        } else {
            player.sendMessage(StringUtil.color("&c⚔ &eДо начала матча осталось &f" + seconds + " &eсекунд."));
        }
    }

    private void announceNextReadyMatch(final Tournament tournament) {
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return;
        }

        final TournamentMatch match = findNextStartableMatch(tournament);
        if (match == null) {
            return;
        }

        final Player player1 = Bukkit.getPlayerExact(match.getPlayer1());
        final Player player2 = Bukkit.getPlayerExact(match.getPlayer2());
        notifyNextReadyPlayer(player1, tournament, match);
        notifyNextReadyPlayer(player2, tournament, match);
    }

    private void notifyNextReadyPlayer(final Player player, final Tournament tournament, final TournamentMatch match) {
        if (player == null) {
            return;
        }

        final String opponent = displayName(match.getOpponent(player.getName()));
        player.sendTitle(StringUtil.color("&6Вы следующие"), StringUtil.color("&f" + opponent), 10, 60, 10);
        player.sendMessage(StringUtil.color("&8&m                                                "));
        player.sendMessage(StringUtil.color("&6&lТурнир: &e" + tournament.getName()));
        player.sendMessage(StringUtil.color("&eВы следующая пара. Подготовьтесь к матчу."));
        player.sendMessage(StringUtil.color("&7Соперник: &f" + opponent));
        player.sendMessage(StringUtil.color("&7Раунд: &f" + match.getRound() + " &8| &7Матч: &f#" + match.getNumber()));
        player.sendMessage(StringUtil.color(tournament.getAllowedKits().size() > 1 || tournament.getKitMode() == TournamentKitMode.PLAYER_CHOICE
                ? "&7Перед стартом откроется меню выбора кита."
                : "&7" + effectiveKitDescription(tournament)));
        player.sendMessage(StringUtil.color("&8&m                                                "));
    }

    private void announceMatchResult(final Tournament tournament, final TournamentMatch match, final String winner, final String loser) {
        if (!settings.broadcastMatchResult || loser == null) {
            return;
        }

        sendToTournamentPlayers(tournament, List.of(
                "&8&m                                                ",
                "&6&lТурнир: &e" + tournament.getName(),
                "&aПобедитель матча: &f" + participantName(winner),
                "&7Соперник: &c" + participantName(loser),
                "&7Раунд: &f" + match.getRound() + " &8| &7Матч: &f#" + match.getNumber(),
                "&8&m                                                "));
    }

    private void notifyMatchFinished(final Tournament tournament, final String winner, final String loser) {
        final Player winnerPlayer = Bukkit.getPlayerExact(winner);
        if (winnerPlayer != null) {
            winnerPlayer.sendMessage(StringUtil.color("&6🏆 &eПоздравляем!"));
            winnerPlayer.sendMessage(StringUtil.color(tournament.getStatus() == TournamentStatus.FINISHED
                    ? "&aВы победили в турнире &e" + tournament.getName() + "&a!"
                    : "&aВы прошли в следующий раунд."));
        }
        final Player loserPlayer = loser == null ? null : Bukkit.getPlayerExact(loser);
        if (loserPlayer != null) {
            loserPlayer.sendMessage(StringUtil.color(tournament.getStatus() == TournamentStatus.FINISHED
                    ? "&eТурнир завершён. &fВы заняли &e2 место&f."
                    : "&c❌ Вы выбыли из турнира."));
        }
    }

    private void sendFinalResults(final Tournament tournament) {
        if (!settings.sendFinalResultsToParticipants) {
            return;
        }

        final TournamentResults results = getTournamentResults(tournament);
        final List<String> lines = new ArrayList<>();
        lines.add("&8&m                                                ");
        lines.add("&6&lИтоги турнира: &e" + tournament.getName());
        lines.add("&7Финальная таблица");
        if (results.first == null) {
            lines.add("&eПобедитель не определён.");
        } else {
            lines.add("&e1 место: &a" + displayName(results.first));
            lines.add("&e2 место: &f" + displayName(results.second));
        }
        if (results.third != null) {
            lines.add("&e3 место: &f" + displayName(results.third));
        }
        lines.add("&8&m                                                ");
        for (Map<Integer, TournamentMatch> round : tournament.getMatches().values()) {
            for (TournamentMatch match : round.values()) {
                if (match.getWinner() == null || match.getStatus() == TournamentMatchStatus.BYE) {
                    continue;
                }
                lines.add("&7Раунд " + match.getRound() + ", матч #" + match.getNumber() + ": &a" + participantName(match.getWinner())
                        + " &7победил &c" + participantName(match.getOpponent(match.getWinner())));
            }
        }
        lines.add("&8&m                                                ");

        sendToTournamentPlayers(tournament, lines);
    }

    private List<Player> onlineTournamentPlayers(final Tournament tournament) {
        return tournament.getPlayers().stream()
                .map(Bukkit::getPlayerExact)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void sendToTournamentPlayers(final Tournament tournament, final List<String> lines) {
        onlineTournamentPlayers(tournament).forEach(player -> lines.forEach(line -> player.sendMessage(StringUtil.color(line))));
    }

    private TournamentResults getTournamentResults(final Tournament tournament) {
        final TournamentHologramRenderer.TournamentResults results = hologramRenderer.results(tournament);
        return new TournamentResults(results.first(), results.second(), results.third());
    }

    private record TournamentResults(String first, String second, String third) {
    }

    private int nextPowerOfTwo(final int value) {
        return bracketService.nextPowerOfTwo(value);
    }

    private String normalize(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private void loadData() {
        tournaments.clear();
        if (!dataFile.exists()) {
            return;
        }

        final FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        final ConfigurationSection history = data.getConfigurationSection("kit-history");
        if (history != null) {
            playerKitHistory.clear();
            for (String player : history.getKeys(false)) {
                final Deque<String> kits = new ArrayDeque<>();
                data.getStringList("kit-history." + player).stream().limit(2).forEach(kits::addLast);
                if (!kits.isEmpty()) {
                    playerKitHistory.put(player.toLowerCase(Locale.ROOT), kits);
                }
            }
        }

        final ConfigurationSection root = data.getConfigurationSection("tournaments");
        if (root == null) {
            return;
        }

        for (String name : root.getKeys(false)) {
            final String path = "tournaments." + name;
            final Tournament tournament = new Tournament(name, data.getString(path + ".kit", ""));
            tournament.setKitMode(parseKitMode(data.getString(path + ".kit-mode",
                    settings.kitSelectionEnabled ? TournamentKitMode.PLAYER_CHOICE.name() : TournamentKitMode.FIXED.name())));
            tournament.setStatus(TournamentStatus.valueOf(data.getString(path + ".status", TournamentStatus.CREATED.name())));
            tournament.setCurrentRound(data.getInt(path + ".current-round", 1));
            tournament.setHologramPage(data.getInt(path + ".hologram.page", 1));
            tournament.setHologramRound(data.getInt(path + ".hologram.round", tournament.getCurrentRound()));
            tournament.setHologramMode(data.getString(path + ".hologram.mode", "CURRENT_ROUND"));
            tournament.setHologramName(data.getString(path + ".hologram.name"));
            tournament.setHologramWorld(data.getString(path + ".hologram.world"));
            tournament.setHologramX(data.getDouble(path + ".hologram.x"));
            tournament.setHologramY(data.getDouble(path + ".hologram.y"));
            tournament.setHologramZ(data.getDouble(path + ".hologram.z"));
            tournament.setHologramYaw((float) data.getDouble(path + ".hologram.yaw"));
            tournament.setHologramPitch((float) data.getDouble(path + ".hologram.pitch"));
            tournament.setHologramDirection(data.getString(path + ".hologram.direction", settings.hologramDirection));
            tournament.setReservedArena(data.getString(path + ".reserved-arena.name"));
            tournament.setReservedArenaWasDisabled(data.getBoolean(path + ".reserved-arena.was-disabled", false));
            tournament.getPlayers().addAll(data.getStringList(path + ".players"));
            tournament.getEliminatedPlayers().addAll(data.getStringList(path + ".eliminated-players"));
            tournament.getAllowedKits().addAll(data.getStringList(path + ".allowed-kits"));
            tournament.getAllowedArenas().addAll(data.getStringList(path + ".allowed-arenas"));
            syncKitModeWithAllowedKits(tournament);

            final ConfigurationSection rounds = data.getConfigurationSection(path + ".matches");
            if (rounds != null) {
                for (String roundKey : rounds.getKeys(false)) {
                    final int round = Integer.parseInt(roundKey);
                    final Map<Integer, TournamentMatch> matches = new LinkedHashMap<>();
                    final ConfigurationSection matchSection = rounds.getConfigurationSection(roundKey);
                    if (matchSection == null) {
                        continue;
                    }
                    for (String matchKey : matchSection.getKeys(false)) {
                        final int number = Integer.parseInt(matchKey);
                        final String matchPath = path + ".matches." + roundKey + "." + matchKey;
                        final TournamentMatch match = new TournamentMatch(round, number);
                        match.setPlayer1(data.getString(matchPath + ".player1"));
                        match.setPlayer2(data.getString(matchPath + ".player2"));
                        match.setWinner(data.getString(matchPath + ".winner"));
                        match.setStatus(TournamentMatchStatus.valueOf(data.getString(matchPath + ".status", TournamentMatchStatus.WAITING.name())));
                        if (match.getStatus() == TournamentMatchStatus.STARTING || match.getStatus() == TournamentMatchStatus.IN_PROGRESS) {
                            match.setStatus(TournamentMatchStatus.READY);
                        }
                        match.setWaitingSince(data.getLong(matchPath + ".waiting-since", 0L));
                        matches.put(number, match);
                    }
                    tournament.getMatches().put(round, matches);
                }
            }
            tournaments.put(normalize(name), tournament);
        }
    }

    private void loadLobby() {
        if (!lobbyFile.exists() || lobbyFile.length() == 0L) {
            this.lobby = plugin.getPlayerManager().getLobby();
            return;
        }

        try (final Reader reader = new InputStreamReader(new FileInputStream(lobbyFile), StandardCharsets.UTF_8)) {
            this.lobby = JsonUtil.getObjectMapper().readValue(reader, LocationData.class).toLocation();
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not load tournament lobby: " + ex.getMessage());
            this.lobby = plugin.getPlayerManager().getLobby();
        }

        if (this.lobby == null || this.lobby.getWorld() == null) {
            this.lobby = plugin.getPlayerManager().getLobby();
        }
    }

    private void saveData() {
        final String snapshot = createDataSnapshot();
        if (dataWriter == null || !dataWriter.submit(snapshot)) {
            try {
                writeData(snapshot);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save tournaments.yml: " + ex.getMessage());
            }
        }
    }

    private void saveDataNow() {
        final String snapshot = createDataSnapshot();
        if (dataWriter != null) {
            dataWriter.closeAndWrite(snapshot);
            return;
        }
        try {
            writeData(snapshot);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save tournaments.yml: " + ex.getMessage());
        }
    }

    private String createDataSnapshot() {
        final YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<String, Deque<String>> entry : playerKitHistory.entrySet()) {
            data.set("kit-history." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        for (Tournament tournament : tournaments.values()) {
            final String path = "tournaments." + tournament.getName();
            data.set(path + ".kit", tournament.getKit());
            data.set(path + ".kit-mode", tournament.getKitMode().name());
            data.set(path + ".status", tournament.getStatus().name());
            data.set(path + ".current-round", tournament.getCurrentRound());
            data.set(path + ".players", new ArrayList<>(tournament.getPlayers()));
            data.set(path + ".eliminated-players", new ArrayList<>(tournament.getEliminatedPlayers()));
            data.set(path + ".allowed-kits", new ArrayList<>(tournament.getAllowedKits()));
            data.set(path + ".allowed-arenas", new ArrayList<>(tournament.getAllowedArenas()));
            data.set(path + ".hologram.name", tournament.getHologramName());
            data.set(path + ".hologram.world", tournament.getHologramWorld());
            data.set(path + ".hologram.x", tournament.getHologramX());
            data.set(path + ".hologram.y", tournament.getHologramY());
            data.set(path + ".hologram.z", tournament.getHologramZ());
            data.set(path + ".hologram.yaw", tournament.getHologramYaw());
            data.set(path + ".hologram.pitch", tournament.getHologramPitch());
            data.set(path + ".hologram.direction", tournament.getHologramDirection());
            data.set(path + ".hologram.page", tournament.getHologramPage());
            data.set(path + ".hologram.round", tournament.getHologramRound());
            data.set(path + ".hologram.mode", tournament.getHologramMode());
            data.set(path + ".reserved-arena.name", tournament.getReservedArena());
            data.set(path + ".reserved-arena.was-disabled", tournament.isReservedArenaWasDisabled());

            for (Map.Entry<Integer, Map<Integer, TournamentMatch>> round : tournament.getMatches().entrySet()) {
                for (TournamentMatch match : round.getValue().values()) {
                    final String matchPath = path + ".matches." + round.getKey() + "." + match.getNumber();
                    data.set(matchPath + ".player1", match.getPlayer1());
                    data.set(matchPath + ".player2", match.getPlayer2());
                    data.set(matchPath + ".winner", match.getWinner());
                    data.set(matchPath + ".status", match.getStatus().name());
                    data.set(matchPath + ".waiting-since", match.getWaitingSince());
                }
            }
        }

        return data.saveToString();
    }

    private void writeData(final String snapshot) throws IOException {
        AtomicFileWriter.writeUtf8(dataFile, snapshot);
    }

    private TournamentKitMode parseKitMode(final String value) {
        try {
            return TournamentKitMode.valueOf(value == null ? TournamentKitMode.FIXED.name() : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TournamentKitMode.FIXED;
        }
    }

    private record ActiveTournamentMatch(String tournament, int round, int match) {
    }

    private record KitSelectionMenu(String key) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static class PendingKitSelection {
        private final String key;
        private final String tournament;
        private final int round;
        private final int match;
        private final String player1;
        private final String player2;
        private final List<String> kits;
        private final long deadline;
        private final Map<String, String> selectedKits = new HashMap<>();
        private final Map<Integer, String> kitBySlot = new HashMap<>();
        private final Map<String, BossBar> bossBars = new HashMap<>();

        private PendingKitSelection(final String key, final String tournament, final int round, final int match,
                                    final String player1, final String player2, final List<String> kits, final long deadline) {
            this.key = key;
            this.tournament = tournament;
            this.round = round;
            this.match = match;
            this.player1 = player1;
            this.player2 = player2;
            this.kits = List.copyOf(kits);
            this.deadline = deadline;
        }

        private void choose(final String player, final String kit) {
            selectedKits.put(player.toLowerCase(Locale.ROOT), kit);
        }

        private String selectionFor(final String player) {
            return selectedKits.get(player.toLowerCase(Locale.ROOT));
        }

        private boolean hasPlayer(final String player) {
            return player1.equalsIgnoreCase(player) || player2.equalsIgnoreCase(player);
        }

        private boolean isConfirmed(final String player) {
            return selectionFor(player) != null;
        }

        private boolean isComplete() {
            return isConfirmed(player1) && isConfirmed(player2);
        }
    }

    public static class HologramTemplate {
        public List<String> header;
        public String matchLine;
        public String byeLine;
        public String winnerLine;
        public String waitingLine;
        public List<String> footer;

        public static HologramTemplate load(final FileConfiguration config) {
            final HologramTemplate template = new HologramTemplate();
            template.header = config.getStringList("hologram-template.header");
            if (template.header.isEmpty()) {
                template.header = List.of(
                        "&6&lТурнир: &e%tournament_name%",
                        "&7Кит: &f%kit%",
                        "&7Статус: %status%",
                        "&7Текущий раунд: &f%current_round%",
                        "&8&m--------------------");
            }
            template.matchLine = config.getString("hologram-template.match-line", "&f%round%.%match% &7| %player1% &8vs %player2% &7- %match_status%");
            template.byeLine = config.getString("hologram-template.bye-line", "&f%round%.%match% &7| %player1% &aпрошёл дальше без соперника");
            template.winnerLine = config.getString("hologram-template.winner-line", "&f%round%.%match% &7| &a%winner% &7победил &c%loser%");
            template.waitingLine = config.getString("hologram-template.waiting-line", "&f%round%.%match% &7| &eОжидание игроков");
            template.footer = config.getStringList("hologram-template.footer");
            if (template.footer.isEmpty()) {
                template.footer = List.of(
                        "&8&m--------------------",
                        "&7Страница: &f%page%&7/&f%max_page%",
                        "&e/tour player %tournament_name%",
                        "&e/tour spec %tournament_name%");
            }
            return template;
        }
    }

    public enum AddResult {
        SUCCESS,
        NOT_FOUND,
        LOCKED,
        ALREADY_ADDED,
        ALREADY_IN_OTHER_TOURNAMENT
    }

    public enum CreateResult {
        SUCCESS,
        ALREADY_EXISTS,
        NO_KIT
    }

    public enum SetKitModeResult {
        SUCCESS,
        NOT_FOUND,
        LOCKED
    }

    public enum TournamentListChangeResult {
        SUCCESS,
        NOT_FOUND,
        LOCKED,
        INVALID_VALUE
    }

    public enum RemoveResult {
        SUCCESS,
        NOT_FOUND,
        LOCKED,
        NOT_REGISTERED
    }

    public enum StartResult {
        SUCCESS,
        NOT_FOUND,
        ALREADY_STARTED,
        NOT_ENOUGH_PLAYERS,
        CANCELLED,
        FINISHED
    }

    public enum MatchStartResult {
        SUCCESS,
        NOT_FOUND,
        NO_MATCH,
        NOT_READY,
        PLAYER_OFFLINE,
        BOTH_OFFLINE,
        NO_KIT,
        NO_ARENA,
        DUEL_REJECTED,
        STARTING,
        ALREADY_RUNNING,
        FINISHED
    }

    public enum ReplayResult {
        SUCCESS,
        NOT_FOUND,
        NO_MATCH,
        NOT_READY
    }

    public enum SpectateResult {
        SUCCESS,
        NOT_FOUND,
        NO_MATCH,
        NOT_STARTED,
        FINISHED,
        ACTIVE_PARTICIPANT,
        NO_TARGET,
        REJECTED
    }

    public enum HologramDirectionResult {
        SUCCESS,
        NOT_FOUND,
        INVALID_DIRECTION
    }

    public enum NpcBindResult {
        SUCCESS,
        NOT_FOUND,
        INVALID_NPC
    }

    public record NextMatchStart(MatchStartResult result, String tournament, int round, int match,
                                 String player1, String player2, String kit, String arena) {
    }

    private record WaitingTournamentMatch(Tournament tournament, TournamentMatch match) {
    }

    public static class TournamentSettings {
        public int autoForfeitMinutes;
        public boolean shufflePlayers;
        public boolean disconnectLoss;
        public String defaultArena;
        public boolean allowActiveParticipantsToSpectate;
        public boolean cmiEnabled;
        public String hologramNameFormat;
        public String cmiCreateCommand;
        public String cmiRemoveCommand;
        public String cmiSetLineCommand;
        public int maxLinesPerPage;
        public boolean showOnlyCurrentRound;
        public boolean showFinishedMatches;
        public boolean showByeMatches;
        public boolean reserveDefaultArena;
        public boolean broadcastMatchStart;
        public boolean broadcastMatchResult;
        public boolean sendFinalResultsToParticipants;
        public boolean autoUpdateHologram;
        public boolean manageHologramLocations;
        public boolean hologramUseOffsets;
        public String hologramDirection;
        public Map<String, HologramOffset> hologramOffsets;
        public boolean fancyNpcsEnabled;
        public boolean kitSelectionEnabled;
        public int kitSelectionSeconds;
        public int kitSelectionAnimationSeconds;
        public String kitSelectionTimerDisplay;
        public List<String> kitSelectionKits;
        public Map<String, String> fancyNpcTournaments;
        public int fancyNpcMenuSize;
        public List<MenuDecorItem> fancyNpcMenuDecor;
        public double hologramSpacing;
        public double hologramPanelSpacing;
        public HologramTemplate template;

        public static TournamentSettings load(final FileConfiguration config) {
            final TournamentSettings settings = new TournamentSettings();
            settings.autoForfeitMinutes = config.getInt("tournament.auto-forfeit-minutes", 2);
            settings.shufflePlayers = config.getBoolean("tournament.shuffle-players", true);
            settings.disconnectLoss = config.getBoolean("tournament.disconnect-loss", false);
            settings.defaultArena = config.getString("duels.default-arena", "");
            settings.allowActiveParticipantsToSpectate = config.getBoolean("spectate.allow-active-participants-to-spectate", false);
            settings.cmiEnabled = config.getBoolean("cmi-holograms.enabled", true);
            settings.hologramNameFormat = config.getString("cmi-holograms.hologram-name-format", "tournament_%name%");
            settings.cmiCreateCommand = config.getString("cmi-holograms.create-command", "cmi hologram new %hologram%");
            settings.cmiRemoveCommand = config.getString("cmi-holograms.remove-command", "cmi hologram remove %hologram%");
            settings.cmiSetLineCommand = config.getString("cmi-holograms.setline-command", "cmi hologram setline %hologram% %line% %text%");
            settings.maxLinesPerPage = config.getInt("hologram.max-lines-per-page", 20);
            settings.showOnlyCurrentRound = config.getBoolean("hologram.show-only-current-round", true);
            settings.showFinishedMatches = config.getBoolean("hologram.show-finished-matches", true);
            settings.showByeMatches = config.getBoolean("hologram.show-bye-matches", false);
            settings.autoUpdateHologram = config.getBoolean("hologram.auto-update", true);
            settings.hologramSpacing = Math.max(1.5D, config.getDouble("hologram.spacing", 3.0D));
            settings.hologramPanelSpacing = Math.max(5.0D, config.getDouble("hologram.panel-spacing",
                    config.getDouble("holograms.panel-spacing", settings.hologramSpacing)));
            settings.manageHologramLocations = config.getBoolean("hologram.manage-locations", true);
            settings.hologramUseOffsets = config.getBoolean("hologram.use-offsets", false);
            settings.hologramDirection = config.getString("hologram.direction", "AUTO").toUpperCase(Locale.ROOT);
            settings.hologramOffsets = new HashMap<>();
            settings.hologramOffsets.put("main", HologramOffset.load(config, "hologram.offsets.main", 0D, 0D, 0D));
            settings.hologramOffsets.put("active", HologramOffset.load(config, "hologram.offsets.active", 0D, 0D, 0D));
            settings.hologramOffsets.put("next", HologramOffset.load(config, "hologram.offsets.next", 0D, 0D, 0D));
            settings.template = HologramTemplate.load(config);
            settings.reserveDefaultArena = config.getBoolean("tournament.reserve-default-arena", true);
            settings.broadcastMatchStart = config.getBoolean("tournament.broadcast-match-start", true);
            settings.broadcastMatchResult = config.getBoolean("tournament.broadcast-match-result", true);
            settings.sendFinalResultsToParticipants = config.getBoolean("tournament.send-final-results-to-participants", true);
            settings.kitSelectionEnabled = config.getBoolean("tournament.kit-selection.enabled", true);
            settings.kitSelectionSeconds = Math.max(5, config.getInt("tournament.kit-selection.seconds", 30));
            settings.kitSelectionAnimationSeconds = Math.max(0, config.getInt("tournament.kit-selection.animation-seconds", 3));
            settings.kitSelectionTimerDisplay = config.getString("tournament.kit-selection.timer-display", "ACTIONBAR");
            settings.kitSelectionKits = config.getStringList("tournament.kit-selection.kits");
            settings.fancyNpcsEnabled = config.getBoolean("tournament.fancy-npcs.enabled", true);
            settings.fancyNpcTournaments = new HashMap<>();
            final ConfigurationSection npcSection = config.getConfigurationSection("tournament.fancy-npcs.npc-tournaments");
            if (npcSection != null) {
                final Set<String> keys = npcSection.getKeys(false);
                for (String key : keys) {
                    final String tournament = npcSection.getString(key);
                    if (tournament != null && !tournament.isBlank()) {
                        settings.fancyNpcTournaments.put(key.toLowerCase(Locale.ROOT), tournament);
                    }
                }
            }
            settings.fancyNpcMenuSize = Math.min(54, Math.max(9, config.getInt("tournament.fancy-npcs.menu.size", 27)));
            if (settings.fancyNpcMenuSize % 9 != 0) {
                settings.fancyNpcMenuSize = 27;
            }
            settings.fancyNpcMenuDecor = new ArrayList<>();
            final ConfigurationSection decorSection = config.getConfigurationSection("tournament.fancy-npcs.menu.decor");
            if (decorSection != null) {
                for (String key : decorSection.getKeys(false)) {
                    final String path = "tournament.fancy-npcs.menu.decor." + key;
                    final Material material = Material.matchMaterial(config.getString(path + ".material", "BLACK_STAINED_GLASS_PANE"));
                    if (material == null) {
                        continue;
                    }
                    settings.fancyNpcMenuDecor.add(new MenuDecorItem(
                            material,
                            config.getString(path + ".name", " "),
                            config.getStringList(path + ".lore"),
                            config.getString(path + ".skull-owner", ""),
                            config.getInt(path + ".head-db-id", 0),
                            config.getIntegerList(path + ".slots")));
                }
            }
            return settings;
        }
    }

    public static class HologramOffset {
        public static final HologramOffset ZERO = new HologramOffset(0D, 0D, 0D);
        public final double x;
        public final double y;
        public final double z;

        public HologramOffset(final double x, final double y, final double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static HologramOffset load(final FileConfiguration config, final String path, final double x, final double y, final double z) {
            return new HologramOffset(
                    config.getDouble(path + ".x", x),
                    config.getDouble(path + ".y", y),
                    config.getDouble(path + ".z", z));
        }
    }

    public record MenuDecorItem(Material material, String name, List<String> lore, String skullOwner, int headDbId, List<Integer> slots) {
    }
}
