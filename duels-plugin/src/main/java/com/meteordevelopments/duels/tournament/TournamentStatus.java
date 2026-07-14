package com.meteordevelopments.duels.tournament;

public enum TournamentStatus {
    CREATED,
    IN_PROGRESS,
    FINISHED,
    CANCELLED;

    public boolean isTerminal() {
        return this == FINISHED || this == CANCELLED;
    }
}
