package com.meteordevelopments.duels.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/** Builds tournament hologram text without performing Bukkit or CMI operations. */
public final class TournamentHologramRenderer {
    public List<String> buildMain(Tournament tournament, int maxLines) {
        List<String> lines = new ArrayList<>();
        List<TournamentMatch> running = runningMatches(tournament);
        TournamentMatch next = nextPlayableMatch(tournament);
        lines.add("&6&lТурнир &e" + tournament.getName());
        lines.add("");
        lines.add("&7Участников: &f" + tournament.getPlayers().size());
        lines.add("&7Осталось: &f" + countRemainingPlayers(tournament));
        if (showPrizeFund(tournament) && tournament.getStatus() != TournamentStatus.FINISHED) {
            lines.add("&6Призовой фонд: &f" + money(prizeFund(tournament)) + " ₽");
        }
        lines.add("");
        lines.add("&7Статус: " + formatActivityStatus(tournament));
        lines.add("&7Текущий раунд: &f" + roundName(tournament, tournament.getCurrentRound()));
        lines.add("");
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            lines.addAll(buildFinalStage(tournament));
        } else if (!running.isEmpty()) {
            lines.add("&eСейчас играют:");
            lines.add(formatShortMatch(running.getFirst()));
            lines.add("");
            lines.add("&eСледующий матч:");
            lines.add(next == null ? "&7Ожидает игроков" : formatShortMatch(next));
        } else if (isFinalStage(tournament)) {
            lines.addAll(buildFinalStage(tournament));
        } else {
            lines.add("&eСейчас играют:");
            lines.add("&7Нет активных матчей");
            lines.add("");
            lines.add("&eСледующий матч:");
            lines.add(next == null ? "&7Ожидает игроков" : formatShortMatch(next));
        }
        lines.add("");
        lines.add("&eКоманды:");
        lines.add("&f/tour mymatch");
        lines.add("&f/tour spec " + tournament.getName());
        return trim(lines, maxLines);
    }

    public List<String> buildActive(Tournament tournament, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            TournamentResults results = results(tournament);
            lines.add("&6&lТурнир завершён");
            lines.add("");
            lines.add(results.first() == null ? "&eПобедитель не определён" : "&eПобедитель: &f" + displayName(results.first()));
            return trim(lines, maxLines);
        }
        lines.add("&c&lАктивные матчи");
        lines.add("");
        List<TournamentMatch> matches = tournament.getMatches().values().stream()
                .flatMap(round -> round.values().stream())
                .filter(match -> match.getStatus() == TournamentMatchStatus.IN_PROGRESS)
                .sorted(Comparator.comparingInt(TournamentMatch::getRound)
                        .thenComparingInt(TournamentMatch::getNumber))
                .limit(3)
                .toList();
        if (matches.isEmpty()) {
            lines.add("&7Нет активных матчей");
        } else {
            for (TournamentMatch match : matches) {
                lines.add("&f#" + match.getNumber() + " &e" + participantName(match.getPlayer1()) + " &7vs &e" + participantName(match.getPlayer2()));
                lines.add("&7Статус: " + formatMatchStatus(match.getStatus()));
                if (match.getStatus() == TournamentMatchStatus.IN_PROGRESS) {
                    lines.add("&f/tour spec " + tournament.getName());
                }
                lines.add("");
            }
        }
        return trim(lines, maxLines);
    }

    public List<String> buildNext(Tournament tournament, int maxLines, boolean showByeMatches) {
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            List<String> lines = new ArrayList<>();
            lines.add("&6&lИТОГИ ТУРНИРА");
            lines.add("&6&l━━━━━━━━━━━━");
            lines.add("");
            lines.addAll(buildLargePlacement(tournament));
            return trim(lines, maxLines);
        }

        List<String> lines = new ArrayList<>();
        int round = resolvePreviewRound(tournament);
        lines.add("&6&lСледующие раунды");
        lines.add("");
        if (round <= 0 || tournament.getRoundMatches(round).isEmpty()) {
            lines.add("&7Ожидает игроков");
            return trim(lines, maxLines);
        }
        List<TournamentMatch> upcoming = tournament.getRoundMatches(round).stream()
                .filter(TournamentMatch::isKnown)
                .filter(match -> match.getStatus() == TournamentMatchStatus.READY
                        || match.getStatus() == TournamentMatchStatus.STARTING
                        || match.getStatus() == TournamentMatchStatus.WAITING_PLAYER)
                .filter(match -> showByeMatches || match.getStatus() != TournamentMatchStatus.BYE)
                .sorted(Comparator.comparingInt(TournamentMatch::getNumber))
                .limit(3)
                .toList();
        if (upcoming.isEmpty()) {
            lines.add("&7Ожидает формирования пар");
            return trim(lines, maxLines);
        }
        for (TournamentMatch match : upcoming) {
            lines.add("&e" + roundName(tournament, match.getRound()) + " #" + match.getNumber());
            lines.add("&f" + participantName(match.getPlayer1()) + " &7vs &f" + participantName(match.getPlayer2()));
            lines.add("");
        }
        return trim(lines, maxLines);
    }

    public TournamentResults results(Tournament tournament) {
        int finalRound = tournament.getMatches().size();
        TournamentMatch finalMatch = tournament.getRoundMatches(finalRound).stream().findFirst().orElse(null);
        String first = finalMatch == null ? null : finalMatch.getWinner();
        String second = finalMatch == null || first == null ? null : finalMatch.getOpponent(first);
        String third = null;
        if (finalRound > 1) {
            for (TournamentMatch semifinal : tournament.getRoundMatches(finalRound - 1)) {
                if (semifinal.getWinner() != null) {
                    String loser = semifinal.getOpponent(semifinal.getWinner());
                    if (loser != null && !"BYE".equalsIgnoreCase(loser)) {
                        third = loser;
                        break;
                    }
                }
            }
        }
        return new TournamentResults(first, second, third);
    }

    public int countRemainingPlayers(Tournament tournament) {
        return (int) tournament.getPlayers().stream().filter(player -> !tournament.isEliminated(player)).count();
    }

    private List<String> buildFinalStage(Tournament tournament) {
        List<String> lines = new ArrayList<>();
        if (tournament.getStatus() == TournamentStatus.FINISHED) {
            lines.add("&6&lИТОГИ");
            lines.addAll(buildPlacement(tournament));
            return lines;
        }
        int totalRounds = tournament.getMatches().size();
        TournamentMatch finalMatch = tournament.getRoundMatches(totalRounds).stream().findFirst().orElse(null);
        if (finalMatch != null && finalMatch.isKnown()) {
            lines.add("&6&lФИНАЛ");
            lines.add("&f" + participantName(finalMatch.getPlayer1()));
            lines.add("&c⚔");
            lines.add("&f" + participantName(finalMatch.getPlayer2()));
            return lines;
        }
        lines.add("&6&lФИНАЛЬНАЯ СТАДИЯ");
        for (TournamentMatch match : tournament.getRoundMatches(Math.max(1, totalRounds - 1))) {
            lines.add("&eПолуфинал " + match.getNumber() + ":");
            lines.add(match.isKnown() ? "&f" + participantName(match.getPlayer1()) + " &7vs &f" + participantName(match.getPlayer2()) : "&7Ожидает игроков");
        }
        return lines;
    }

    private List<String> buildPlacement(Tournament tournament) {
        TournamentResults results = results(tournament);
        List<String> lines = new ArrayList<>();
        if (results.first() == null) {
            lines.add("&eПобедитель не определён");
            return lines;
        }
        lines.add(placementLine(tournament, 1, results.first(), "&e1 место: &f"));
        if (results.second() != null) {
            lines.add(placementLine(tournament, 2, results.second(), "&e2 место: &f"));
        }
        lines.add(results.third() == null ? "&e3 место: &7не определено"
                : placementLine(tournament, 3, results.third(), "&e3 место: &f"));
        return lines;
    }

    private List<String> buildLargePlacement(Tournament tournament) {
        TournamentResults results = results(tournament);
        if (results.first() == null) {
            return List.of("&e&lПобедитель не определён");
        }
        List<String> lines = new ArrayList<>();
        lines.add("&e&l1 МЕСТО");
        lines.add("&f&l" + displayName(results.first()) + rewardSuffix(tournament, 1));
        if (results.second() != null) {
            lines.add("");
            lines.add("&7&l2 МЕСТО");
            lines.add("&f" + displayName(results.second()) + rewardSuffix(tournament, 2));
        }
        if (results.third() != null) {
            lines.add("");
            lines.add("&6&l3 МЕСТО");
            lines.add("&f" + displayName(results.third()) + rewardSuffix(tournament, 3));
        }
        return lines;
    }

    private List<TournamentMatch> runningMatches(Tournament tournament) {
        return tournament.getMatches().values().stream().flatMap(round -> round.values().stream())
                .filter(match -> match.getStatus() == TournamentMatchStatus.IN_PROGRESS).toList();
    }

    private TournamentMatch nextPlayableMatch(Tournament tournament) {
        return tournament.getMatches().values().stream().flatMap(round -> round.values().stream())
                .filter(match -> match.getStatus() == TournamentMatchStatus.READY
                        || match.getStatus() == TournamentMatchStatus.STARTING
                        || match.getStatus() == TournamentMatchStatus.WAITING_PLAYER)
                .findFirst().orElse(null);
    }

    private boolean isFinalStage(Tournament tournament) {
        return tournament.getStatus() == TournamentStatus.FINISHED
                || tournament.getStatus() == TournamentStatus.IN_PROGRESS && countRemainingPlayers(tournament) <= 4 && !tournament.getMatches().isEmpty();
    }

    private int resolvePreviewRound(Tournament tournament) {
        return tournament.getMatches().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue().values().stream().anyMatch(match -> match.isKnown()
                        && (match.getStatus() == TournamentMatchStatus.READY
                        || match.getStatus() == TournamentMatchStatus.STARTING
                        || match.getStatus() == TournamentMatchStatus.WAITING_PLAYER)))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(-1);
    }

    public String formatActivityStatus(Tournament tournament) {
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return formatTournamentStatus(tournament.getStatus());
        }
        return runningMatches(tournament).isEmpty() ? "&eОжидание" : "&aИдёт";
    }

    public String formatTournamentStatus(TournamentStatus status) {
        return switch (status) {
            case CREATED -> "&eОжидает";
            case IN_PROGRESS -> "&aИдёт";
            case FINISHED -> "&6Завершён";
            case CANCELLED -> "&cОтменён";
        };
    }

    public String formatMatchStatus(TournamentMatchStatus status) {
        return switch (status) {
            case WAITING, READY, STARTING -> "&eОжидание";
            case IN_PROGRESS -> "&cИдёт";
            case FINISHED -> "&6Завершён";
            case WAITING_PLAYER -> "&6Ожидает игрока";
            case BYE -> "&aПроход без соперника";
            case CANCELLED -> "&cОтменён";
        };
    }

    private String roundName(Tournament tournament, int round) {
        int totalRounds = tournament.getMatches().size();
        if (round == totalRounds) return "Финал";
        if (round == totalRounds - 1) return "Полуфинал";
        return "Раунд " + round;
    }

    private String formatShortMatch(TournamentMatch match) {
        return "&f" + participantName(match.getPlayer1()) + " &7vs &f" + participantName(match.getPlayer2());
    }

    private String participantName(String name) {
        return "BYE".equalsIgnoreCase(name) ? "Проход без соперника" : displayName(name);
    }

    private String displayName(String name) {
        return name == null || name.isBlank() ? "Ожидание игрока" : name;
    }

    private boolean showPrizeFund(final Tournament tournament) {
        return tournament.isRewardsEnabled() && tournament.getRewardType() != TournamentRewardType.ITEMS;
    }

    private BigDecimal prizeFund(final Tournament tournament) {
        if (tournament.getRewardType() == TournamentRewardType.ENTRY_FEE_POOL) {
            return TournamentRewardCalculator.nonNegative(tournament.getRewardFund());
        }
        return TournamentRewardCalculator.total(List.of(tournament.getFixedFirstReward(),
                tournament.getFixedSecondReward(), tournament.getFixedThirdReward()));
    }

    private String placementLine(final Tournament tournament, final int place, final String player, final String prefix) {
        return prefix + displayName(player) + rewardSuffix(tournament, place);
    }

    private String rewardSuffix(final Tournament tournament, final int place) {
        if (!showPrizeFund(tournament)) return "";
        final TournamentRewardDelivery delivery = tournament.getRewardDeliveries().get(place);
        if (delivery == null) return "";
        return " &7— &a" + money(delivery.getAmount()) + " ₽";
    }

    private String money(final BigDecimal amount) {
        final DecimalFormatSymbols symbols = new DecimalFormatSymbols(java.util.Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        final DecimalFormat format = new DecimalFormat("#,##0.##", symbols);
        return format.format(TournamentRewardCalculator.nonNegative(amount));
    }

    private List<String> trim(List<String> lines, int maxLines) {
        return lines.size() > maxLines ? lines.subList(0, maxLines) : lines;
    }

    public record TournamentResults(String first, String second, String third) {
    }
}
