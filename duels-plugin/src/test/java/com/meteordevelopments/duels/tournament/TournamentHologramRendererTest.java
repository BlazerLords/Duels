package com.meteordevelopments.duels.tournament;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TournamentHologramRendererTest {
    private final TournamentBracketService bracketService = new TournamentBracketService();
    private final TournamentHologramRenderer renderer = new TournamentHologramRenderer();

    @Test
    void finishedTournamentShowsResultsInsteadOfNextRound() {
        Tournament tournament = finishedTournament();

        List<String> main = renderer.buildMain(tournament, 20);
        List<String> active = renderer.buildActive(tournament, 20);
        List<String> next = renderer.buildNext(tournament, 20, false);

        assertTrue(main.contains("&6&lИТОГИ"));
        assertTrue(active.contains("&6&lТурнир завершён"));
        assertTrue(active.contains("&eПобедитель: &fAlpha"));
        assertEquals("&6&lИтоги турнира", next.getFirst());
        assertTrue(next.contains("&e1 место: &fAlpha"));
        assertTrue(next.contains("&e2 место: &fBravo"));
        assertFalse(next.stream().anyMatch(line -> line.contains("Следующий раунд")));
        assertFalse(next.stream().anyMatch(line -> line.contains("Alpha &7vs")));
    }

    @Test
    void rendererRespectsConfiguredLineLimit() {
        Tournament tournament = finishedTournament();

        assertEquals(4, renderer.buildMain(tournament, 4).size());
        assertEquals(3, renderer.buildNext(tournament, 3, false).size());
    }

    @Test
    void resultCalculationUsesFinalWinnerAndOpponent() {
        TournamentHologramRenderer.TournamentResults results = renderer.results(finishedTournament());

        assertEquals("Alpha", results.first());
        assertEquals("Bravo", results.second());
    }

    private Tournament finishedTournament() {
        Tournament tournament = new Tournament("Cup", "sword");
        tournament.getPlayers().addAll(List.of("Alpha", "Bravo"));
        bracketService.buildBracket(tournament, false);
        TournamentMatch finalMatch = tournament.getMatch(1, 1);
        finalMatch.setWinner("Alpha");
        finalMatch.setStatus(TournamentMatchStatus.FINISHED);
        tournament.setStatus(TournamentStatus.FINISHED);
        return tournament;
    }
}
