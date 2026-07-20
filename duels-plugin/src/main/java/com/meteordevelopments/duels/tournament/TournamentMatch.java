package com.meteordevelopments.duels.tournament;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TournamentMatch {

    private final int round;
    private final int number;
    private String player1;
    private String player2;
    private String winner;
    private String selectedKit;
    private TournamentMatchStatus status = TournamentMatchStatus.WAITING;
    private long waitingSince;
    private long completedAt;

    public TournamentMatch(final int round, final int number) {
        this.round = round;
        this.number = number;
    }

    public boolean hasPlayer(final String name) {
        return equalsName(player1, name) || equalsName(player2, name);
    }

    public String getOpponent(final String name) {
        if (equalsName(player1, name)) {
            return player2;
        }
        if (equalsName(player2, name)) {
            return player1;
        }
        return null;
    }

    public boolean isKnown() {
        return player1 != null && player2 != null;
    }

    public boolean isBye() {
        return "BYE".equalsIgnoreCase(player1) || "BYE".equalsIgnoreCase(player2);
    }

    private boolean equalsName(final String left, final String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
