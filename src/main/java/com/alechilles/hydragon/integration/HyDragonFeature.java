package com.alechilles.hydragon.integration;

import java.util.Set;

/** Runtime features independently gated by their complete Tamework capability contracts. */
public enum HyDragonFeature {
    CAPTURE_AND_ROSTER(Set.of(
            "BONDED_COMPANIONS", "CAPTURE_POLICY",
            "CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION",
            "INTERACTION_EXTENSIONS", "EVENTS")),
    DRAGON_HORN(Set.of("BONDED_COMPANIONS")),
    TIMED_SUMMONING(Set.of("BONDED_COMPANIONS")),
    PAID_REVIVAL(Set.of("BONDED_COMPANIONS")),
    SOUL_BOND_CLAIM(Set.of("BONDED_COMPANIONS")),
    MINIWYVERN_ATTUNEMENT(Set.of("BONDED_COMPANIONS")),
    MINIWYVERN_ABILITIES(Set.of("BONDED_COMPANIONS")),
    DYNAMIC_ENCOUNTERS(Set.of(
            "BONDED_COMPANIONS", "CAPTURE_POLICY",
            "CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION",
            "INTERACTION_EXTENSIONS", "EVENTS")),
    TAMEWORK_DIAGNOSTICS(Set.of("DIAGNOSTICS"));

    private final Set<String> requiredCapabilities;

    HyDragonFeature(Set<String> requiredCapabilities) {
        this.requiredCapabilities = Set.copyOf(requiredCapabilities);
    }

    /** Stable public capability names used by both runtime adapters and diagnostic gates. */
    public Set<String> requiredCapabilities() {
        return requiredCapabilities;
    }
}
