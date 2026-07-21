package com.alechilles.hydragon.runtime;

import java.util.Objects;

/** Bounded outcome returned by a HyDragon gameplay saga. */
public record GameplayResult(Status status, String reason) {
    public GameplayResult {
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason").trim();
        if (reason.isEmpty()) throw new IllegalArgumentException("reason is required");
    }

    public boolean succeeded() {
        return status == Status.APPLIED || status == Status.ALREADY_APPLIED;
    }

    public static GameplayResult applied(String reason) {
        return new GameplayResult(Status.APPLIED, reason);
    }

    public static GameplayResult denied(String reason) {
        return new GameplayResult(Status.DENIED, reason);
    }

    public static GameplayResult retryable(String reason) {
        return new GameplayResult(Status.RETRYABLE, reason);
    }

    public static GameplayResult reconciliation(String reason) {
        return new GameplayResult(Status.RECONCILIATION_REQUIRED, reason);
    }

    public static GameplayResult unavailable(String reason) {
        return new GameplayResult(Status.UNAVAILABLE, reason);
    }

    public enum Status {
        APPLIED,
        ALREADY_APPLIED,
        DENIED,
        RETRYABLE,
        RECONCILIATION_REQUIRED,
        UNAVAILABLE,
        QUARANTINED
    }
}
