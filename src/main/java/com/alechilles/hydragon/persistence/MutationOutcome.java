package com.alechilles.hydragon.persistence;

/** Result of an idempotent local persistence mutation. */
public enum MutationOutcome {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT,
    QUARANTINED,
    STORE_READ_ONLY
}
