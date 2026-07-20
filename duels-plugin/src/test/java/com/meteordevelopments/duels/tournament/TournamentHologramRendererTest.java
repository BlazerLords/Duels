package com.meteordevelopments.duels.tournament;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.math.BigDecimal;

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
        assertEquals("&6&lИТОГИ ТУРНИРА", next.getFirst());
        assertTrue(next.contains("&f&lAlpha"));
        assertTrue(next.contains("&fBravo"));
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
    void prizeFundIsShownOnlyForEnabledMoneyRewards() {
        Tournament tournament = tournamentWithEightPlayers();
        tournament.setRewardsEnabled(true);
        tournament.setRewardType(TournamentRewardType.FIXED_MONEY);
        tournament.setFixedFirstReward(new BigDecimal("50000"));
        tournament.setFixedSecondReward(new BigDecimal("30000"));
        tournament.setFixedThirdReward(new BigDecimal("20000"));
        assertTrue(renderer.buildMain(tournament, 30).contains("&6Призовой фонд: &f100 000 ₽"));

        tournament.setRewardType(TournamentRewardType.ITEMS);
        assertFalse(renderer.buildMain(tournament, 30).stream().anyMatch(line -> line.contains("Призовой фонд")));
        tournament.setRewardsEnabled(false);
        assertFalse(renderer.buildMain(tournament, 30).stream().anyMatch(line -> line.contains("Призовой фонд")));
    }

    @Test
    void finishedMoneyTournamentShowsFrozenActualPayouts() {
        Tournament tournament = finishedTournament();
        tournament.setRewardsEnabled(true);
        tournament.setRewardType(TournamentRewardType.ENTRY_FEE_POOL);
        TournamentRewardDelivery first = new TournamentRewardDelivery(1);
        first.setWinnerName("Alpha");
        first.setAmount(new BigDecimal("60"));
        TournamentRewardDelivery second = new TournamentRewardDelivery(2);
        second.setWinnerName("Bravo");
        second.setAmount(new BigDecimal("40"));
        tournament.getRewardDeliveries().put(1, first);
        tournament.getRewardDeliveries().put(2, second);

        List<String> lines = renderer.buildMain(tournament, 30);
        assertTrue(lines.stream().anyMatch(line -> line.contains("Alpha") && line.contains("60 ₽")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Bravo") && line.contains("40 ₽")));
    }

    @Test
    void resultCalculationUsesFinalWinnerAndOpponent() {
        TournamentHologramRenderer.TournamentResults results = renderer.results(finishedTournament());

        assertEquals("Alpha", results.first());
        assertEquals("Bravo", results.second());
    }

    @Test
    void activePanelContainsOnlyActuallyRunningMatchesAndAtMostThreePairs() {
        Tournament tournament = tournamentWithEightPlayers();
        tournament.getMatch(1, 1).setStatus(TournamentMatchStatus.IN_PROGRESS);
        tournament.getMatch(1, 2).setStatus(TournamentMatchStatus.STARTING);
        tournament.getMatch(1, 3).setStatus(TournamentMatchStatus.READY);
        tournament.getMatch(1, 4).setStatus(TournamentMatchStatus.IN_PROGRESS);

        List<String> lines = renderer.buildActive(tournament, 20);

        assertTrue(lines.stream().anyMatch(line -> line.contains("Alpha") && line.contains("Bravo")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Golf") && line.contains("Hotel")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("Charlie") || line.contains("Echo")));
    }

    @Test
    void nextPanelShowsThreeKnownPairsFromEarliestPendingRound() {
        Tournament tournament = tournamentWithEightPlayers();
        tournament.getMatch(1, 1).setStatus(TournamentMatchStatus.IN_PROGRESS);

        List<String> lines = renderer.buildNext(tournament, 20, false);

        assertEquals("&6&lСледующие раунды", lines.getFirst());
        assertEquals(3, lines.stream().filter(line -> line.contains(" &7vs ")).count());
        assertFalse(lines.stream().anyMatch(line -> line.contains("Alpha") || line.contains("Ожидание игрока")));
    }

    private Tournament tournamentWithEightPlayers() {
        Tournament tournament = new Tournament("Cup", "crystal");
        tournament.getPlayers().addAll(List.of("Alpha", "Bravo", "Charlie", "Delta",
                "Echo", "Foxtrot", "Golf", "Hotel"));
        bracketService.buildBracket(tournament, false);
        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        return tournament;
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
