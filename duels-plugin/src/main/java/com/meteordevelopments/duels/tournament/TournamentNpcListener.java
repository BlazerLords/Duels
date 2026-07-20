package com.meteordevelopments.duels.tournament;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.Permissions;
import com.meteordevelopments.duels.util.StringUtil;
import com.github.thesilentpro.headdb.api.HeadAPI;
import com.github.thesilentpro.headdb.api.model.Head;
import com.github.thesilentpro.headdb.core.HeadDB;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TournamentNpcListener implements Listener {

    private static final int TROPHY_HEAD_ID = 42035;
    private static final int UNREGISTER_HEAD_ID = 30154;
    private static final int RULES_HEAD_ID = 281;
    private static final int SPECTATE_HEAD_ID = 108522;
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final Map<Integer, String> HEAD_TEXTURES = Map.of(
            TROPHY_HEAD_ID, "f97e2c8b8276276e38deca8850872d59ecc9c1f38f2adce40858edb9e634d7ba",
            UNREGISTER_HEAD_ID, "47e50591f4118b9ae44755f7b485699b4b917f00d65f5ea8553ee48826d234c7",
            RULES_HEAD_ID, "9c2e9d8395cacd9922869c15373cf7cb16da0a5ce5f3c632b19ceb3929c9a11",
            SPECTATE_HEAD_ID, "ab9db809147fc527b47d6b78bfcff3895ba279480ae32927489211e15b239fc5"
    );

    private final DuelsPlugin plugin;
    private final Map<Integer, ItemStack> headDbItems = new ConcurrentHashMap<>();

    public TournamentNpcListener(final DuelsPlugin plugin) {
        this.plugin = plugin;
        preloadHeadDbItems();
    }

    public boolean registerFancyNpcHook() {
        try {
            final Class<? extends Event> eventClass = Class
                    .forName("de.oliver.fancynpcs.api.events.NpcInteractEvent")
                    .asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL,
                    (listener, event) -> onNpcInteract(event), plugin, true);
            return true;
        } catch (ClassNotFoundException | ClassCastException exception) {
            plugin.getLogger().warning("FancyNpcs найден, но его API несовместим: " + exception.getMessage());
            return false;
        }
    }

    private void onNpcInteract(final Event event) {
        final TournamentManager.TournamentSettings settings = plugin.getTournamentManager().getSettings();
        if (settings == null || !settings.fancyNpcsEnabled || settings.fancyNpcTournaments.isEmpty()) {
            return;
        }

        try {
            final Object npc = event.getClass().getMethod("getNpc").invoke(event);
            final Object npcData = npc.getClass().getMethod("getData").invoke(npc);
            final String npcName = (String) npcData.getClass().getMethod("getName").invoke(npcData);
            final String npcId = (String) npcData.getClass().getMethod("getId").invoke(npcData);
            final String tournament = findTournament(settings.fancyNpcTournaments, npcName, npcId);
            if (tournament == null) {
                return;
            }

            if (event instanceof Cancellable cancellable) {
                cancellable.setCancelled(true);
            }
            final Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            openMenu(player, tournament);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            plugin.getLogger().warning("Не удалось обработать нажатие на FancyNPC: " + exception.getMessage());
        }
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof TournamentNpcMenu menu)) {
            return;
        }

        event.setCancelled(true);
        final Tournament tournament = plugin.getTournamentManager().getTournament(menu.tournament);
        if (tournament == null) {
            player.closeInventory();
            message(player, "&cТурнир не найден.");
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> register(player, tournament.getName());
            case 12 -> unregister(player, tournament.getName());
            case 14 -> describe(player, tournament);
            case 16 -> spectate(player, tournament.getName());
            default -> {
                return;
            }
        }
    }

    private void openMenu(final Player player, final String tournamentName) {
        final Tournament tournament = plugin.getTournamentManager().getTournament(tournamentName);
        if (tournament == null) {
            message(player, "&cТурнир не найден.");
            return;
        }

        final TournamentManager.TournamentSettings settings = plugin.getTournamentManager().getSettings();
        final int size = settings == null ? 27 : settings.fancyNpcMenuSize;
        final Inventory inventory = Bukkit.createInventory(new TournamentNpcMenu(tournament.getName()), size,
                StringUtil.color("&6Турнир: &e" + tournament.getName()));

        if (settings != null) {
            for (TournamentManager.MenuDecorItem decor : settings.fancyNpcMenuDecor) {
                final ItemStack item = namedItem(decor.material(), decor.name(), decor.lore(), decor.skullOwner(), decor.headDbId());
                for (int slot : decor.slots()) {
                    if (slot >= 0 && slot < inventory.getSize()) {
                        inventory.setItem(slot, item);
                    }
                }
            }
        }

        final Tournament activeTournament = plugin.getTournamentManager().getPlayerOpenTournament(player.getName());
        final boolean blockedByOtherTournament = activeTournament != null
                && !activeTournament.getName().equalsIgnoreCase(tournament.getName())
                && !tournament.hasParticipant(player.getName());
        final boolean registrationOpen = tournament.getStatus() == TournamentStatus.CREATED && !blockedByOtherTournament;
        inventory.setItem(4, headDbItem(TROPHY_HEAD_ID, Material.NETHER_STAR, "&6&l" + tournament.getName(), statusLore(player, tournament)));
        inventory.setItem(10, namedItem(registrationOpen ? Material.WRITABLE_BOOK : Material.BARRIER,
                registrationOpen ? "&aРегистрация" : registrationTitle(player, tournament),
                registrationLore(player, tournament)));
        inventory.setItem(12, headDbItem(UNREGISTER_HEAD_ID, Material.RED_DYE, "&cОтменить регистрацию",
                List.of(
                        "&7Нажмите, если передумали участвовать.",
                        "&7Ваше имя будет удалено из списка участников.",
                        "&7После запуска турнира отменить регистрацию",
                        "&7через это меню уже нельзя.")));
        inventory.setItem(14, headDbItem(RULES_HEAD_ID, Material.CLOCK, "&eКак проходит турнир",
                List.of(
                        "&7Турнир проходит по олимпийской системе:",
                        "&7победитель матча идет дальше,",
                        "&7проигравший выбывает из турнира.",
                        "&7Перед каждым боем игроки получают пару,",
                        "&7раунд и номер матча.",
                        "&7Свой текущий матч можно проверить командой:",
                        "&f/tour mymatch")));
        inventory.setItem(16, headDbItem(SPECTATE_HEAD_ID, Material.ENDER_EYE, "&bСледить за турниром",
                List.of(
                        "&7Нажмите, чтобы наблюдать активный матч.",
                        "&7Если бой уже идет, вас переведет",
                        "&7в режим наблюдения за участниками.",
                        "&7Активные участники турнира не могут",
                        "&7смотреть чужие матчи, пока не выбыли.")));

        player.openInventory(inventory);
    }

    private void register(final Player player, final String tournament) {
        if (!player.hasPermission(Permissions.TOURNAMENT_JOIN)) {
            message(player, "&cНедостаточно прав: " + Permissions.TOURNAMENT_JOIN);
            return;
        }
        final Tournament target = plugin.getTournamentManager().getTournament(tournament);
        switch (plugin.getTournamentManager().joinPlayer(tournament, player)) {
            case SUCCESS -> message(player, "&aВы зарегистрированы на турнир &e" + tournament + "&a.");
            case NOT_FOUND -> message(player, "&cТурнир не найден.");
            case LOCKED -> message(player, lockedRegistrationMessage(target));
            case ALREADY_ADDED -> message(player, "&eВы уже зарегистрированы на этот турнир.");
            case NOT_ENOUGH_PLAYTIME -> {
                final int required = target == null ? 0 : target.getRequiredPlaytimeHours();
                final int current = plugin.getTournamentManager().getPlaytimeHours(player);
                message(player, "&cДля регистрации нужно минимум &f" + required + " ч &cонлайна. У вас: &f" + current + " ч&c.");
            }
            case ECONOMY_UNAVAILABLE -> message(player, "&cЭкономика сервера недоступна, регистрация со взносом временно невозможна.");
            case NOT_ENOUGH_MONEY -> {
                final String amount = target == null ? "0" : formatMoney(target.getEntryFeeAmount());
                message(player, "&cДля регистрации нужен взнос &f" + amount + "&c. Недостаточно средств.");
            }
            case ECONOMY_WITHDRAW_FAILED -> message(player, "&cНе удалось списать взнос. Сообщите администрации.");
            case ALREADY_IN_OTHER_TOURNAMENT -> {
                final Tournament activeTournament = plugin.getTournamentManager().getPlayerOpenTournament(player.getName());
                message(player, activeTournament == null
                        ? "&cВы уже участвуете в другом незавершённом турнире."
                        : "&cВы уже участвуете в турнире &e" + activeTournament.getName() + "&c.");
            }
        }
    }

    private void unregister(final Player player, final String tournament) {
        if (!player.hasPermission(Permissions.TOURNAMENT_JOIN)) {
            message(player, "&cНедостаточно прав: " + Permissions.TOURNAMENT_JOIN);
            return;
        }
        switch (plugin.getTournamentManager().leavePlayer(tournament, player)) {
            case SUCCESS -> message(player, "&aРегистрация отменена.");
            case NOT_FOUND -> message(player, "&cТурнир не найден.");
            case LOCKED -> message(player, "&cТурнир уже начался.");
            case NOT_REGISTERED -> message(player, "&eВы не зарегистрированы на этот турнир.");
        }
    }

    private void describe(final Player player, final Tournament tournament) {
        player.closeInventory();
        message(player, "&8&m                                                ");
        message(player, tournament.getStatus() == TournamentStatus.FINISHED ? "&6&lТурнир завершён" : "&6&lКак проходит турнир");
        message(player, "&7Турнир: &e" + tournament.getName() + " &8| &7Кит: &f" + tournament.getKit());
        if (tournament.getStatus() == TournamentStatus.FINISHED
                || (tournament.hasParticipant(player.getName()) && tournament.isEliminated(player.getName()))) {
            plugin.getTournamentManager().describePlayerResults(tournament, player.getName()).forEach(line -> message(player, line));
        } else {
            message(player, "&7Формат: &fолимпийская сетка на выбывание.");
            message(player, "&7Победитель матча проходит дальше, проигравший выбывает.");
            message(player, "&7Организатор запускает пары по очереди, когда игроки готовы.");
            message(player, "&7Вашу пару, раунд и номер матча можно проверить командой:");
            message(player, "&f/tour mymatch");
        }
        message(player, "&8&m                                                ");
    }

    private List<String> statusLore(final Player player, final Tournament tournament) {
        final List<String> lore = new ArrayList<>();
        lore.add("&7Статус: " + plugin.getTournamentManager().formatTournamentStatus(tournament.getStatus()));
        lore.add("&7Участников: &f" + tournament.getPlayers().size());
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            lore.add("&6Турнир завершён.");
            lore.add("&7Нажмите на часы, чтобы посмотреть");
            lore.add("&7результат своих игр.");
        } else if (tournament.hasParticipant(player.getName()) && tournament.isEliminated(player.getName())) {
            lore.add("&cВы выбыли из этого турнира.");
            lore.add("&7Результаты своих игр доступны");
            lore.add("&7через кнопку с правилами.");
        } else if (tournament.hasParticipant(player.getName())) {
            lore.add("&aВы участвуете в турнире.");
            final TournamentMatch match = findVisibleMatch(tournament, player.getName());
            if (match != null && match.getStatus() == TournamentMatchStatus.WAITING_PLAYER) {
                lore.add("&6Матч ожидает игрока.");
                lore.add("&7Ожидание: &f" + plugin.getTournamentManager().formatWaitingElapsed(match));
                lore.add("&7Осталось: &f" + plugin.getTournamentManager().formatWaitingRemaining(match));
            }
        } else if (tournament.getStatus() == TournamentStatus.CREATED) {
            final Tournament activeTournament = plugin.getTournamentManager().getPlayerOpenTournament(player.getName());
            if (activeTournament != null && !activeTournament.getName().equalsIgnoreCase(tournament.getName())) {
                lore.add("&cВы уже участвуете в другом турнире:");
                lore.add("&e" + activeTournament.getName());
            } else {
                lore.add("&eРегистрация открыта.");
            }
        } else {
            lore.add("&cРегистрация закрыта.");
        }
        return lore;
    }

    private List<String> registrationLore(final Player player, final Tournament tournament) {
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            return List.of("&6Турнир уже завершён.", "&7Регистрация недоступна.", "&7Результаты доступны через кнопку с часами.");
        }
        if (tournament.hasParticipant(player.getName()) && tournament.isEliminated(player.getName())) {
            return List.of("&cВы выбыли из турнира.", "&7Повторная регистрация невозможна.");
        }
        if (tournament.getStatus() != TournamentStatus.CREATED) {
            return List.of("&cРегистрация закрыта.", "&7Турнир уже запущен или отменён.");
        }
        final Tournament activeTournament = plugin.getTournamentManager().getPlayerOpenTournament(player.getName());
        if (activeTournament != null && !activeTournament.getName().equalsIgnoreCase(tournament.getName())) {
            return List.of(
                    "&cРегистрация недоступна.",
                    "&7Вы уже участвуете в турнире:",
                    "&e" + activeTournament.getName(),
                    "&7Дождитесь окончания или выбытия.");
        }
        return List.of(
                "&7Нажмите, чтобы занять место в турнире.",
                "&7После регистрации вы попадете в список участников.",
                "&7Когда организатор запустит турнир,",
                "&7плагин составит пары и покажет ваш матч.",
                "&eВажно: &7зарегистрироваться можно только до старта.");
    }

    private String registrationTitle(final Player player, final Tournament tournament) {
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            return "&6Турнир завершён";
        }
        if (tournament.hasParticipant(player.getName()) && tournament.isEliminated(player.getName())) {
            return "&cВы выбыли";
        }
        final Tournament activeTournament = plugin.getTournamentManager().getPlayerOpenTournament(player.getName());
        if (activeTournament != null && !activeTournament.getName().equalsIgnoreCase(tournament.getName())) {
            return "&cУже есть турнир";
        }
        return "&cРегистрация закрыта";
    }

    private String lockedRegistrationMessage(final Tournament tournament) {
        if (tournament == null) {
            return "&cРегистрация уже закрыта.";
        }
        return switch (tournament.getStatus()) {
            case FINISHED -> "&6Турнир уже завершён. Результаты можно посмотреть в меню NPC.";
            case CANCELLED -> "&cТурнир отменён.";
            case IN_PROGRESS -> "&cТурнир уже начался, регистрация закрыта.";
            case CREATED -> "&cРегистрация уже закрыта.";
        };
    }

    private String formatMoney(final double amount) {
        synchronized (MONEY_FORMAT) {
            return MONEY_FORMAT.format(amount);
        }
    }

    private TournamentMatch findVisibleMatch(final Tournament tournament, final String player) {
        return tournament.getMatches().values().stream()
                .flatMap(round -> round.values().stream())
                .filter(match -> match.hasPlayer(player))
                .filter(match -> match.getStatus() == TournamentMatchStatus.READY
                        || match.getStatus() == TournamentMatchStatus.STARTING
                        || match.getStatus() == TournamentMatchStatus.IN_PROGRESS
                        || match.getStatus() == TournamentMatchStatus.WAITING_PLAYER)
                .findFirst()
                .orElse(null);
    }

    private void spectate(final Player player, final String tournament) {
        if (!player.hasPermission(Permissions.TOURNAMENT_SPECTATE)) {
            message(player, "&cНедостаточно прав: " + Permissions.TOURNAMENT_SPECTATE);
            return;
        }
        final boolean admin = player.hasPermission(Permissions.TOURNAMENT_SPECTATE_ADMIN) || player.hasPermission(Permissions.TOURNAMENT_ADMIN);
        switch (plugin.getTournamentManager().spectate(player, tournament, admin)) {
            case SUCCESS -> message(player, "&aВы наблюдаете за матчем турнира.");
            case NOT_FOUND -> message(player, "&cТурнир не найден.");
            case NOT_STARTED -> message(player, "&eСейчас нет активного матча.");
            case ACTIVE_PARTICIPANT -> message(player, "&cАктивный участник не может наблюдать другие матчи.");
            case NO_TARGET -> message(player, "&cИгрок матча не онлайн.");
            default -> message(player, "&cНе удалось включить наблюдение.");
        }
    }

    private ItemStack namedItem(final Material material, final String name, final List<String> lore) {
        return namedItem(material, name, lore, null);
    }

    private ItemStack namedItem(final Material material, final String name, final List<String> lore, final String skullOwner) {
        return namedItem(new ItemStack(material), name, lore, skullOwner);
    }

    private ItemStack namedItem(final Material material, final String name, final List<String> lore, final String skullOwner, final int headDbId) {
        if (headDbId > 0) {
            return headDbItem(headDbId, material, name, lore, skullOwner);
        }
        return namedItem(material, name, lore, skullOwner);
    }

    private ItemStack headDbItem(final int headId, final Material fallback, final String name, final List<String> lore) {
        return headDbItem(headId, fallback, name, lore, null);
    }

    private ItemStack headDbItem(final int headId, final Material fallback, final String name, final List<String> lore, final String skullOwner) {
        return namedItem(loadHeadDbItem(headId)
                .or(() -> textureHeadItem(headId))
                .orElseGet(() -> new ItemStack(fallback)), name, lore, skullOwner);
    }

    private ItemStack namedItem(final ItemStack source, final String name, final List<String> lore, final String skullOwner) {
        final ItemStack item = source.clone();
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(StringUtil.color(name));
            if (meta instanceof SkullMeta skullMeta && skullOwner != null && !skullOwner.isBlank()) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(skullOwner));
            }
            final List<String> coloredLore = new ArrayList<>();
            lore.forEach(line -> coloredLore.add(StringUtil.color(line)));
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Optional<ItemStack> loadHeadDbItem(final int headId) {
        return Optional.ofNullable(headDbItems.get(headId)).map(ItemStack::clone);
    }

    private Optional<ItemStack> textureHeadItem(final int headId) {
        final String texture = HEAD_TEXTURES.get(headId);
        if (texture == null || texture.isBlank()) {
            return Optional.empty();
        }
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (!(item.getItemMeta() instanceof SkullMeta skullMeta)) {
            return Optional.empty();
        }
        try {
            final PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));
            profile.getTextures().setSkin(new URL("https://textures.minecraft.net/texture/" + texture));
            skullMeta.setOwnerProfile(profile);
            item.setItemMeta(skullMeta);
            return Optional.of(item);
        } catch (MalformedURLException ignored) {
            return Optional.empty();
        }
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
        final Set<Integer> targetIds = new HashSet<>(Arrays.asList(TROPHY_HEAD_ID, UNREGISTER_HEAD_ID, RULES_HEAD_ID, SPECTATE_HEAD_ID));
        final TournamentManager.TournamentSettings settings = plugin.getTournamentManager().getSettings();
        if (settings != null) {
            settings.fancyNpcMenuDecor.stream()
                    .map(TournamentManager.MenuDecorItem::headDbId)
                    .filter(id -> id > 0)
                    .forEach(targetIds::add);
        }
        for (Head head : heads) {
            if (!targetIds.contains(head.getId())) {
                continue;
            }
            try {
                final ItemStack item = head.getItem();
                if (item != null) {
                    headDbItems.put(head.getId(), item.clone());
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to cache HeadDB head " + head.getId() + ": " + exception.getMessage());
            }
        }
    }

    private String findTournament(final Map<String, String> tournaments, final String... npcKeys) {
        for (String npcKey : npcKeys) {
            if (npcKey == null) {
                continue;
            }
            final String tournament = tournaments.get(npcKey.toLowerCase(Locale.ROOT));
            if (tournament != null) {
                return tournament;
            }
        }
        return null;
    }

    private void message(final Player player, final String message) {
        player.sendMessage(StringUtil.color(message));
    }

    private record TournamentNpcMenu(String tournament) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
