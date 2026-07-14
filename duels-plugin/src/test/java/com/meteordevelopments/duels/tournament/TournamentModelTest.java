package com.meteordevelopments.duels.tournament;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class TournamentModelTest {
    @Test
    void participantAndOpponentLookupIgnoreNameCase() {
        Tournament tournament = new Tournament("Cup", "sword");
        tournament.getPlayers().add("PlayerOne");
        TournamentMatch match = match(1, 1, "PlayerOne", "PlayerTwo");
        tournament.getMatches().put(1, new LinkedHashMap<>(java.util.Map.of(1, match)));

        assertTrue(tournament.hasParticipant("playerone"));
        assertTrue(match.hasPlayer("PLAYERONE"));
        assertEquals("PlayerTwo", match.getOpponent("playerone"));
        assertNull(match.getOpponent("unknown"));
    }

    @Test
    void finishedLossEliminatesParticipant() {
        Tournament tournament = tournamentWithPlayers("Winner", "Loser");
        TournamentMatch match = match(1, 1, "Winner", "Loser");
        match.setStatus(TournamentMatchStatus.FINISHED);
        match.setWinner("Winner");
        tournament.getMatches().put(1, new LinkedHashMap<>(java.util.Map.of(1, match)));

        assertFalse(tournament.isEliminated("Winner"));
        assertTrue(tournament.isEliminated("loser"));
    }

    @Test
    void finalTournamentKeepsOnlyRecordedChampionActive() {
        Tournament tournament = tournamentWithPlayers("Winner", "Other");
        TournamentMatch match = match(1, 1, "Winner", "Other");
        match.setStatus(TournamentMatchStatus.FINISHED);
        match.setWinner("Winner");
        tournament.getMatches().put(1, new LinkedHashMap<>(java.util.Map.of(1, match)));
        tournament.setStatus(TournamentStatus.FINISHED);

        assertFalse(tournament.isEliminated("winner"));
        assertTrue(tournament.isEliminated("Other"));
        assertTrue(tournament.isEliminated("NotRegistered"));
    }

    @Test
    void byeAndKnownStateFollowStoredPlayerNames() {
        TournamentMatch match = match(2, 3, "Player", "BYE");

        assertTrue(match.isKnown());
        assertTrue(match.isBye());
        assertEquals("BYE", match.getOpponent("Player"));
    }

    @Test
    void lifecycleClassificationsKeepTerminalAndPendingStatesSeparate() {
        assertTrue(TournamentStatus.FINISHED.isTerminal());
        assertTrue(TournamentStatus.CANCELLED.isTerminal());
        assertFalse(TournamentStatus.IN_PROGRESS.isTerminal());
        assertTrue(TournamentMatchStatus.FINISHED.isTerminal());
        assertTrue(TournamentMatchStatus.WAITING_PLAYER.isPending());
        assertTrue(TournamentMatchStatus.STARTING.isRunning());
        assertTrue(TournamentMatchStatus.IN_PROGRESS.isRunning());
        assertFalse(TournamentMatchStatus.READY.isRunning());
        assertFalse(TournamentMatchStatus.CANCELLED.isPending());
    }

    private Tournament tournamentWithPlayers(String... players) {
        Tournament tournament = new Tournament("Cup", "sword");
        tournament.getPlayers().addAll(java.util.List.of(players));
        return tournament;
    }

    private TournamentMatch match(int round, int number, String first, String second) {
        TournamentMatch match = new TournamentMatch(round, number);
        match.setPlayer1(first);
        match.setPlayer2(second);
        return match;
    }
}
