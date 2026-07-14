package com.meteordevelopments.duels.tournament;

public enum TournamentMatchStatus {
    WAITING,
    READY,
    STARTING,
    IN_PROGRESS,
    FINISHED,
    WAITING_PLAYER,
    BYE,
    CANCELLED;

    public boolean isTerminal() {
        return this == FINISHED || this == BYE || this == CANCELLED;
    }

    public boolean isPending() {
        return this == READY || this == STARTING || this == IN_PROGRESS || this == WAITING_PLAYER;
    }

    public boolean isRunning() {
        return this == STARTING || this == IN_PROGRESS;
    }
}
