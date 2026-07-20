package com.meteordevelopments.duels.tournament;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public final class TournamentPaymentRecord {
    private final UUID paymentId;
    private final UUID playerId;
    private final String playerName;
    private final UUID tournamentId;
    private final BigDecimal amount;
    private final long paidAt;
    private TournamentPaymentStatus status;
    private boolean addedToFund;

    public TournamentPaymentRecord(final UUID paymentId, final UUID playerId, final String playerName,
                                   final UUID tournamentId, final BigDecimal amount, final long paidAt,
                                   final TournamentPaymentStatus status, final boolean addedToFund) {
        this.paymentId = paymentId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.tournamentId = tournamentId;
        this.amount = amount;
        this.paidAt = paidAt;
        this.status = status;
        this.addedToFund = addedToFund;
    }
}
