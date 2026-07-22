package com.alechilles.hydragon.persistence;

/** Durable phases of a HyDragon-owned consumable saga. */
public enum ConsumableTransactionStatus {
    PREPARED,
    CANCELED,
    MATERIAL_CONSUMED,
    COMMITTED,
    REFUND_DUE,
    REFUNDED,
    QUARANTINED;

    public boolean terminal() {
        return this == CANCELED || this == COMMITTED || this == REFUNDED || this == QUARANTINED;
    }

    /** Only transitions that preserve the one-success-or-one-refund invariant are legal. */
    public boolean mayTransitionTo(ConsumableTransactionStatus next) {
        if (next == null || next == this || terminal()) {
            return false;
        }
        return switch (this) {
            case PREPARED -> next == CANCELED || next == MATERIAL_CONSUMED || next == QUARANTINED;
            case MATERIAL_CONSUMED -> next == COMMITTED || next == REFUND_DUE || next == QUARANTINED;
            case REFUND_DUE -> next == REFUNDED || next == QUARANTINED;
            case CANCELED, COMMITTED, REFUNDED, QUARANTINED -> false;
        };
    }
}
