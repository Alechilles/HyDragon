package com.alechilles.hydragon.persistence;

/** Durable HyDragon-side entitlement state for a player's one-time Soul Bond grant. */
public enum SoulBondState {
    UNCLAIMED,
    PENDING,
    CLAIMED,
    NEEDS_RECONCILIATION
}
