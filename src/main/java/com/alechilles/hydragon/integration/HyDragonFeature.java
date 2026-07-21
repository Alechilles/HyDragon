package com.alechilles.hydragon.integration;

import java.util.Set;

/** Runtime features independently gated by their complete Tamework capability contracts. */
public enum HyDragonFeature {
    CAPTURE_AND_BOND(Set.of(
            "CAPTURE_POLICY", "BONDED_VESSELS", "POPULATION_GROUPS",
            "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE", "INTERACTION_EXTENSIONS", "EVENTS")),
    BONDED_STONE_TRANSITIONS(Set.of(
            "BONDED_VESSELS", "POPULATION_GROUPS", "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE")),
    BONDED_STONE_REPAIR(Set.of(
            "BONDED_VESSELS", "POPULATION_GROUPS", "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE")),
    SOUL_BOND_CLAIM(Set.of(
            "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE", "POPULATION_GROUPS",
            "COMPANION_PROVISIONING", "INTERACTION_EXTENSIONS")),
    MINIWYVERN_ATTUNEMENT(Set.of("PROFILE_DATA", "PROFILE_DATA_TRANSACTIONS")),
    MINIWYVERN_ABILITIES(Set.of(
            "EVENTS", "PROFILES", "POLICY", "PROFILE_DATA", "PROFILE_DATA_TRANSACTIONS")),
    DYNAMIC_ENCOUNTERS(Set.of(
            "CAPTURE_POLICY", "POPULATION_GROUPS", "PROFILES", "POLICY",
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
