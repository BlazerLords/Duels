package com.meteordevelopments.duels;

public final class Permissions {

    public static final String DUEL = "duels.duel";
    public static final String STATS = "duels.stats";
    public static final String STATS_OTHERS = STATS + ".others";
    public static final String TOGGLE = "duels.toggle";
    public static final String TOP = "duels.top";
    public static final String SPECTATE = "duels.spectate";
    public static final String SPEC_ANON = SPECTATE + ".anonymously";
    public static final String ADMIN = "duels.admin";
    public static final String TP_BYPASS = "duels.teleport.bypass";
    public static final String KIT = "duels.kits.%s";
    public static final String KIT_ALL = "duels.kits.*";
    public static final String KIT_EDIT = "duels.kiteditor";
    public static final String KIT_SELECTING = "duels.use.kit-select";
    public static final String OWN_INVENTORY = "duels.use.own-inventory";
    public static final String ARENA_SELECTING = "duels.use.arena-select";
    public static final String ITEM_BETTING = "duels.use.item-betting";
    public static final String MONEY_BETTING = "duels.use.money-betting";
    public static final String SETTING_ALL = "duels.use.*";
    public static final String QUEUE = "duels.queue";
    public static final String PARTY = "duels.party";
    public static final String PARTY_LIST_OTHERS = PARTY + ".list.others";
    public static final String PARTY_TOGGLE = PARTY + ".toggle";
    public static final String TOURNAMENT_ADMIN = "tournament.admin";
    public static final String TOURNAMENT_CREATE = "tournament.create";
    public static final String TOURNAMENT_DELETE = "tournament.delete";
    public static final String TOURNAMENT_ADD = "tournament.add";
    public static final String TOURNAMENT_REMOVE = "tournament.remove";
    public static final String TOURNAMENT_START = "tournament.start";
    public static final String TOURNAMENT_CANCEL = "tournament.cancel";
    public static final String TOURNAMENT_FINISH = "tournament.finish";
    public static final String TOURNAMENT_WIN = "tournament.win";
    public static final String TOURNAMENT_REPLAY = "tournament.replay";
    public static final String TOURNAMENT_HOLO = "tournament.holo";
    public static final String TOURNAMENT_JOIN = "tournament.join";
    public static final String TOURNAMENT_SPECTATE = "tournament.spectate";
    public static final String TOURNAMENT_SPECTATE_ADMIN = "tournament.spectate.admin";
    public static final String TOURNAMENT_SET_LOBBY = "tournament.setlobby";

    private Permissions() {
    }
}
