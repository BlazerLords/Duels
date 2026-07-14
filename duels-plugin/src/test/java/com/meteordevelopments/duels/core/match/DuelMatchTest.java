package com.meteordevelopments.duels.core.match;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.core.arena.ArenaImpl;
import com.meteordevelopments.duels.party.PartyManagerImpl;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DuelMatchTest {
    private DuelMatch match;
    private Player first;
    private Player second;

    @BeforeEach
    void setUp() {
        DuelsPlugin plugin = mock(DuelsPlugin.class);
        when(plugin.getPartyManager()).thenReturn(mock(PartyManagerImpl.class));
        match = new DuelMatch(plugin, mock(ArenaImpl.class), null, new HashMap<>(), 0, null);
        first = player();
        second = player();
        match.addPlayer(first);
        match.addPlayer(second);
    }

    @Test
    void newlyAddedPlayersAreAliveAndPartOfStartingRoster() {
        assertEquals(2, match.size());
        assertEquals(2, match.getStartingPlayers().size());
        assertFalse(match.isDead(first));
        assertFalse(match.isFinished());
        assertFalse(match.isFromQueue());
        assertTrue(match.isOwnInventory());
        assertEquals(MatchLifecycleState.CREATED, match.getLifecycleState());
    }

    @Test
    void lifecycleAllowsOnlyOrderedStartTransitions() {
        assertFalse(match.markActive());
        assertTrue(match.markPreparing());
        assertFalse(match.markPreparing());
        assertTrue(match.markActive());
        assertFalse(match.markActive());
        assertEquals(MatchLifecycleState.ACTIVE, match.getLifecycleState());
    }

    @Test
    void finishingAndCleanupCanBeClaimedOnlyOnce() {
        assertTrue(match.markPreparing());
        assertTrue(match.markActive());

        assertTrue(match.tryBeginFinishing());
        assertFalse(match.tryBeginFinishing());
        assertFalse(match.isFinished());
        assertTrue(match.tryBeginRestoring());
        assertFalse(match.tryBeginRestoring());
        match.setFinished();
        assertTrue(match.markCompleted());

        assertTrue(match.isFinished());
        assertEquals(MatchLifecycleState.COMPLETED, match.getLifecycleState());
        assertFalse(match.markCancelled());
        assertFalse(match.markError());
    }

    @Test
    void cancellationIsTerminalAndIdempotent() {
        assertTrue(match.markPreparing());
        assertTrue(match.markCancelled());
        assertFalse(match.markCancelled());
        assertFalse(match.markActive());
        assertFalse(match.tryBeginFinishing());
        assertEquals(MatchLifecycleState.CANCELLED, match.getLifecycleState());
    }

    @Test
    void concurrentFinishRequestsHaveSingleWinner() {
        assertTrue(match.markPreparing());
        assertTrue(match.markActive());

        long accepted = java.util.stream.IntStream.range(0, 100)
                .parallel()
                .filter(ignored -> match.tryBeginFinishing())
                .count();

        assertEquals(1, accepted);
        assertEquals(MatchLifecycleState.FINISHING, match.getLifecycleState());
    }

    @Test
    void deathRemovesPlayerOnlyFromAliveRoster() {
        match.markAsDead(second);

        assertEquals(1, match.size());
        assertEquals(java.util.Set.of(first), match.getAlivePlayers());
        assertTrue(match.getStartingPlayers().contains(second));
        assertTrue(match.isDead(second));
    }

    @Test
    void nextRoundRevivesPlayersAndPreservesRoundWins() {
        match.markAsDead(second);
        match.addRoundWin(first);
        match.nextRound();

        assertEquals(1, match.getCurrentRound());
        assertEquals(2, match.size());
        assertEquals(1, match.getRoundWins(first));
        assertFalse(match.hasWonMatch(first));
        match.addRoundWin(first);
        assertTrue(match.hasWonMatch(first));
    }

    @Test
    void spawnPointIsClonedWhenStored() {
        World world = mock(World.class);
        Location source = new Location(world, 1, 2, 3);
        match.setSpawnPoint(first, source);
        source.setX(99);

        assertEquals(1, match.getSpawnPoint(first).getX());
    }

    @Test
    void handleMatchEndMarksLoserAndResetsWinnerCombatState() {
        when(first.getMaxHealth()).thenReturn(20.0);

        match.handleMatchEnd(first, second);
        match.handleMatchEnd(first, second);

        assertTrue(match.isFinished());
        assertEquals(MatchLifecycleState.COMPLETED, match.getLifecycleState());
        assertTrue(match.isDead(second));
        verify(first, times(1)).setHealth(20.0);
        verify(first, times(1)).setFireTicks(0);
        verify(first, times(1)).setFallDistance(0);
        verify(first, times(1)).setVelocity(argThat(vector -> vector.lengthSquared() == 0));
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
