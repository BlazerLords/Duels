package com.meteordevelopments.duels.tournament;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TournamentBracketServiceTest {
    private final TournamentBracketService service = new TournamentBracketService();

    @Test
    void byeAdvancesAndFinalWinFinishesTournament() {
        Tournament tournament = tournament("Alpha", "Bravo", "Charlie");
        service.buildBracket(tournament, false);
        tournament.setStatus(TournamentStatus.IN_PROGRESS);

        service.processReadyAndByeMatches(tournament);

        TournamentMatch first = tournament.getMatch(1, 1);
        TournamentMatch bye = tournament.getMatch(1, 2);
        TournamentMatch finalMatch = tournament.getMatch(2, 1);
        assertEquals(TournamentMatchStatus.READY, first.getStatus());
        assertEquals("Charlie", bye.getWinner());
        assertEquals("Charlie", finalMatch.getPlayer2());

        first.setWinner("Alpha");
        first.setStatus(TournamentMatchStatus.FINISHED);
        service.advanceWinner(tournament, first, "Alpha");
        assertEquals(TournamentMatchStatus.READY, finalMatch.getStatus());
        assertEquals("Alpha", finalMatch.getPlayer1());

        finalMatch.setWinner("Alpha");
        finalMatch.setStatus(TournamentMatchStatus.FINISHED);
        service.advanceWinner(tournament, finalMatch, "Alpha");

        assertEquals(TournamentStatus.FINISHED, tournament.getStatus());
        assertEquals(2, tournament.getCurrentRound());
    }

    @Test
    void rebuildingRoundClearsStaleWinnerAndRestoresKnownFinal() {
        Tournament tournament = tournament("Alpha", "Bravo", "Charlie", "Delta");
        service.buildBracket(tournament, false);
        TournamentMatch first = tournament.getMatch(1, 1);
        TournamentMatch second = tournament.getMatch(1, 2);
        first.setWinner("Alpha");
        first.setStatus(TournamentMatchStatus.FINISHED);
        second.setWinner("Charlie");
        second.setStatus(TournamentMatchStatus.FINISHED);
        service.advanceWinner(tournament, first, "Alpha");
        service.advanceWinner(tournament, second, "Charlie");
        TournamentMatch finalMatch = tournament.getMatch(2, 1);
        finalMatch.setWinner("Alpha");
        finalMatch.setStatus(TournamentMatchStatus.FINISHED);

        service.rebuildFutureRounds(tournament, 1);

        assertNull(finalMatch.getWinner());
        assertEquals("Alpha", finalMatch.getPlayer1());
        assertEquals("Charlie", finalMatch.getPlayer2());
        assertEquals(TournamentMatchStatus.READY, finalMatch.getStatus());
    }

    @Test
    void currentRoundSelectsEarliestPendingRound() {
        Tournament tournament = tournament("Alpha", "Bravo", "Charlie", "Delta");
        service.buildBracket(tournament, false);
        tournament.setCurrentRound(2);

        assertEquals(1, service.resolveCurrentRound(tournament));
        assertEquals(8, service.nextPowerOfTwo(5));
    }

    private Tournament tournament(String... players) {
        Tournament tournament = new Tournament("Cup", "sword");
        tournament.getPlayers().addAll(java.util.List.of(players));
        return tournament;
    }
}
