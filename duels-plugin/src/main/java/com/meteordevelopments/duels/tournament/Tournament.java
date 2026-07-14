package com.meteordevelopments.duels.tournament;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class Tournament {

    private final String name;
    private final String kit;
    private TournamentKitMode kitMode = TournamentKitMode.FIXED;
    private TournamentStatus status = TournamentStatus.CREATED;
    private int currentRound = 1;
    private int hologramPage = 1;
    private int hologramRound = 1;
    private String hologramMode = "CURRENT_ROUND";
    private String hologramName;
    private String hologramWorld;
    private double hologramX;
    private double hologramY;
    private double hologramZ;
    private float hologramYaw;
    private float hologramPitch;
    private String hologramDirection;
    private String reservedArena;
    private boolean reservedArenaWasDisabled;
    private final List<String> players = new ArrayList<>();
    private final List<String> eliminatedPlayers = new ArrayList<>();
    private final List<String> allowedKits = new ArrayList<>();
    private final List<String> allowedArenas = new ArrayList<>();
    private final Map<Integer, Map<Integer, TournamentMatch>> matches = new LinkedHashMap<>();

    public Tournament(final String name, final String kit) {
        this.name = name;
        this.kit = kit;
    }

    public TournamentMatch getMatch(final int round, final int match) {
        final Map<Integer, TournamentMatch> roundMatches = matches.get(round);
        return roundMatches == null ? null : roundMatches.get(match);
    }

    public List<TournamentMatch> getRoundMatches(final int round) {
        final Map<Integer, TournamentMatch> roundMatches = matches.get(round);
        return roundMatches == null ? List.of() : new ArrayList<>(roundMatches.values());
    }

    public boolean hasParticipant(final String player) {
        return players.stream().anyMatch(name -> name.equalsIgnoreCase(player));
    }

    public boolean isEliminated(final String player) {
        if (!hasParticipant(player)) {
            return true;
        }
        if (eliminatedPlayers.stream().anyMatch(name -> name.equalsIgnoreCase(player))) {
            return true;
        }

        for (Map<Integer, TournamentMatch> round : matches.values()) {
            for (TournamentMatch match : round.values()) {
                if (match.hasPlayer(player)
                        && match.getStatus() == TournamentMatchStatus.FINISHED
                        && match.getWinner() != null
                        && !match.getWinner().equalsIgnoreCase(player)) {
                    return true;
                }
            }
        }

        return status == TournamentStatus.FINISHED
                && matches.values().stream()
                .flatMap(round -> round.values().stream())
                .noneMatch(match -> player.equalsIgnoreCase(match.getWinner()));
    }
}
