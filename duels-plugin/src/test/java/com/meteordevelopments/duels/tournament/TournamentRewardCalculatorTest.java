package com.meteordevelopments.duels.tournament;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TournamentRewardCalculatorTest {
    @Test
    void defaultDistributionPreservesTheWholeFund() {
        final List<BigDecimal> result = TournamentRewardCalculator.distribute(new BigDecimal("100000"), 60, 30, 10);
        assertEquals(new BigDecimal("60000.00"), result.get(0));
        assertEquals(new BigDecimal("30000.00"), result.get(1));
        assertEquals(new BigDecimal("10000.00"), result.get(2));
        assertEquals(new BigDecimal("100000.00"), result.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void roundingRemainderAlwaysGoesToFirstPlace() {
        final List<BigDecimal> result = TournamentRewardCalculator.distribute(new BigDecimal("0.05"), 34, 33, 33);
        assertEquals(List.of(new BigDecimal("0.03"), new BigDecimal("0.01"), new BigDecimal("0.01")), result);
        assertEquals(new BigDecimal("0.05"), result.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void invalidDistributionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TournamentRewardCalculator.distribute(BigDecimal.TEN, 50, 30, 10));
        assertThrows(IllegalArgumentException.class,
                () -> TournamentRewardCalculator.distribute(BigDecimal.TEN, 110, -10, 0));
    }

    @Test
    void fundCanNeverBecomeNegative() {
        assertEquals(BigDecimal.ZERO, TournamentRewardCalculator.nonNegative(new BigDecimal("-1")));
        assertEquals(List.of(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")),
                TournamentRewardCalculator.distribute(new BigDecimal("-500"), 60, 30, 10));
    }
}
