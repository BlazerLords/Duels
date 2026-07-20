package com.meteordevelopments.duels.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns deterministic bracket mutations independently from Bukkit match execution. */
public final class TournamentBracketService {
    public void buildBracket(Tournament tournament, boolean shufflePlayers) {
        List<String> entrants = new ArrayList<>(tournament.getPlayers());
        if (shufflePlayers) {
            Collections.shuffle(entrants);
        }

        int slots = nextPowerOfTwo(entrants.size());
        while (entrants.size() < slots) {
            entrants.add("BYE");
        }

        tournament.getMatches().clear();
        int roundSize = slots;
        int round = 1;
        while (roundSize >= 2) {
            Map<Integer, TournamentMatch> matches = new LinkedHashMap<>();
            for (int number = 1; number <= roundSize / 2; number++) {
                TournamentMatch match = new TournamentMatch(round, number);
                if (round == 1) {
                    match.setPlayer1(entrants.get((number - 1) * 2));
                    match.setPlayer2(entrants.get((number - 1) * 2 + 1));
                    match.setStatus(match.isBye() ? TournamentMatchStatus.BYE : TournamentMatchStatus.READY);
                }
                matches.put(number, match);
            }
            tournament.getMatches().put(round, matches);
            roundSize /= 2;
            round++;
        }
    }

    public void processReadyAndByeMatches(Tournament tournament) {
        boolean changed;
        do {
            changed = false;
            for (Map<Integer, TournamentMatch> round : tournament.getMatches().values()) {
                for (TournamentMatch match : round.values()) {
                    if (match.getStatus() == TournamentMatchStatus.BYE && match.getWinner() == null) {
                        String winner = "BYE".equalsIgnoreCase(match.getPlayer1()) ? match.getPlayer2() : match.getPlayer1();
                        match.setWinner(winner);
                        advanceWinner(tournament, match, winner);
                        changed = true;
                    } else if (match.getStatus() == TournamentMatchStatus.WAITING && match.isKnown()) {
                        match.setStatus(match.isBye() ? TournamentMatchStatus.BYE : TournamentMatchStatus.READY);
                        changed = true;
                    }
                }
            }
        } while (changed);
    }

    public void rebuildFutureRounds(Tournament tournament, int fromRound) {
        for (Map.Entry<Integer, Map<Integer, TournamentMatch>> entry : tournament.getMatches().entrySet()) {
            if (entry.getKey() <= fromRound) {
                continue;
            }
            for (TournamentMatch match : entry.getValue().values()) {
                match.setPlayer1(null);
                match.setPlayer2(null);
                match.setWinner(null);
                match.setSelectedKit(null);
                match.setCompletedAt(0L);
                match.setWaitingSince(0L);
                match.setStatus(TournamentMatchStatus.WAITING);
            }
        }

        for (int round = 1; round < tournament.getMatches().size(); round++) {
            Map<Integer, TournamentMatch> matches = tournament.getMatches().get(round);
            if (matches == null) {
                continue;
            }
            for (TournamentMatch match : matches.values()) {
                if (match.getWinner() != null) {
                    advanceWinner(tournament, match, match.getWinner());
                }
            }
        }
        processReadyAndByeMatches(tournament);
    }

    public void advanceWinner(Tournament tournament, TournamentMatch match, String winner) {
        int nextRound = match.getRound() + 1;
        Map<Integer, TournamentMatch> nextRoundMatches = tournament.getMatches().get(nextRound);
        if (nextRoundMatches == null) {
            tournament.setStatus(TournamentStatus.FINISHED);
            tournament.setCurrentRound(match.getRound());
            return;
        }

        TournamentMatch nextMatch = nextRoundMatches.get((match.getNumber() + 1) / 2);
        if (nextMatch == null) {
            return;
        }
        if (match.getNumber() % 2 == 1) {
            nextMatch.setPlayer1(winner);
        } else {
            nextMatch.setPlayer2(winner);
        }
        if (nextMatch.isKnown()) {
            nextMatch.setStatus(nextMatch.isBye() ? TournamentMatchStatus.BYE : TournamentMatchStatus.READY);
        }
    }

    public int resolveCurrentRound(Tournament tournament) {
        return tournament.getMatches().entrySet().stream()
                .filter(entry -> entry.getValue().values().stream().anyMatch(this::isPendingMatch))
                .map(Map.Entry::getKey)
                .min(Comparator.naturalOrder())
                .orElse(tournament.getCurrentRound());
    }

    public int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result *= 2;
        }
        return result;
    }

    private boolean isPendingMatch(TournamentMatch match) {
        return match.getStatus().isPending();
    }
}
