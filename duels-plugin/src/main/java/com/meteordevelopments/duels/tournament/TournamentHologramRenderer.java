package com.meteordevelopments.duels.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            lines.add("");
            lines.add("&7Активных матчей нет");
            return trim(lines, maxLines);
        }
        lines.add("&c&lАктивные матчи");
        lines.add("");
        List<TournamentMatch> matches = tournament.getMatches().values().stream()
                .flatMap(round -> round.values().stream())
                .filter(this::isVisibleActiveMatch)
                .sorted(Comparator.comparingInt(this::activeWeight)
                        .thenComparingInt(TournamentMatch::getRound)
                        .thenComparingInt(TournamentMatch::getNumber))
                .limit(Math.max(1, maxLines / 2))
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
            lines.add("&6&lИтоги турнира");
            lines.add("");
            lines.addAll(buildPlacement(tournament));
            return trim(lines, maxLines);
        }

        List<String> lines = new ArrayList<>();
        int round = resolvePreviewRound(tournament);
        lines.add(isFinalStage(tournament) ? "&6&lФинальная стадия" : "&6&lСледующий раунд");
        lines.add("");
        if (round <= 0 || tournament.getRoundMatches(round).isEmpty()) {
            lines.add("&7Ожидает игроков");
            return trim(lines, maxLines);
        }
        for (TournamentMatch match : tournament.getRoundMatches(round)) {
            if (!showByeMatches && match.getStatus() == TournamentMatchStatus.BYE) {
                continue;
            }
            lines.add("&e" + roundName(tournament, match.getRound()) + " #" + match.getNumber());
            if (match.isKnown()) {
                lines.add("&f" + participantName(match.getPlayer1()));
                lines.add("&7vs");
                lines.add("&f" + participantName(match.getPlayer2()));
            } else {
                lines.add("&7Ожидает игроков");
            }
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
        lines.add("&e1 место: &f" + displayName(results.first()));
        if (results.second() != null) {
            lines.add("&e2 место: &f" + displayName(results.second()));
        }
        lines.add(results.third() == null ? "&e3 место: &7не определено" : "&e3 место: &f" + displayName(results.third()));
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
        if (tournament.getRoundMatches(tournament.getCurrentRound()).stream().anyMatch(this::isVisibleActiveMatch)) {
            return tournament.getCurrentRound();
        }
        int nextRound = tournament.getCurrentRound() + 1;
        if (tournament.getMatches().containsKey(nextRound)) {
            return nextRound;
        }
        return tournament.getMatches().containsKey(tournament.getCurrentRound()) ? tournament.getCurrentRound() : -1;
    }

    private boolean isVisibleActiveMatch(TournamentMatch match) {
        return match.getStatus().isPending();
    }

    private int activeWeight(TournamentMatch match) {
        return switch (match.getStatus()) {
            case READY, STARTING, WAITING_PLAYER -> 0;
            case IN_PROGRESS -> 1;
            default -> 2;
        };
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

    private List<String> trim(List<String> lines, int maxLines) {
        return lines.size() > maxLines ? lines.subList(0, maxLines) : lines;
    }

    public record TournamentResults(String first, String second, String third) {
    }
}
