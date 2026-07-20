package com.meteordevelopments.duels.tournament;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class TournamentRewardCalculator {
    private TournamentRewardCalculator() {
    }

    public static List<BigDecimal> distribute(final BigDecimal source, final int firstPercent,
                                              final int secondPercent, final int thirdPercent) {
        if (firstPercent < 0 || secondPercent < 0 || thirdPercent < 0
                || firstPercent + secondPercent + thirdPercent != 100) {
            throw new IllegalArgumentException("Reward distribution must equal 100%");
        }
        final BigDecimal fund = nonNegative(source).setScale(2, RoundingMode.DOWN);
        final BigDecimal second = share(fund, secondPercent);
        final BigDecimal third = share(fund, thirdPercent);
        final BigDecimal firstBase = share(fund, firstPercent);
        final BigDecimal remainder = fund.subtract(firstBase).subtract(second).subtract(third);
        return List.of(firstBase.add(remainder), second, third);
    }

    public static BigDecimal total(final List<BigDecimal> amounts) {
        return amounts.stream().map(TournamentRewardCalculator::nonNegative)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal nonNegative(final BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private static BigDecimal share(final BigDecimal fund, final int percent) {
        return fund.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
    }
}
