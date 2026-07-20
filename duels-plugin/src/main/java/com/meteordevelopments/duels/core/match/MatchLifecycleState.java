package com.meteordevelopments.duels.core.match;

/** Internal lifecycle of a duel instance. */
public enum MatchLifecycleState {
    CREATED,
    PREPARING,
    ACTIVE,
    FINISHING,
    RESTORING,
    COMPLETED,
    CANCELLED,
    ERROR
}
