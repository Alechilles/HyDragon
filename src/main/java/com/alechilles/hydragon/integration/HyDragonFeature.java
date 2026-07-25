package com.alechilles.hydragon.integration;

import java.util.Set;

/** Runtime features independently gated by their complete Tamework capability contracts. */
public enum HyDragonFeature {
    CAPTURE_AND_ROSTER(Set.of(
            "CAPTURE_POLICY", "COMMAND_FAMILY_ROSTERS", "CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION",
            "CAPTURE_TAME_AND_LINK", "COMMAND_TIMED_SUMMONING", "POPULATION_GROUPS",
            "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE", "INTERACTION_EXTENSIONS", "EVENTS")),
    DRAGON_HORN(Set.of(
            "COMMAND_FAMILY_ROSTERS", "POPULATION_GROUPS", "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE")),
    TIMED_SUMMONING(Set.of(
            "COMMAND_FAMILY_ROSTERS", "COMMAND_TIMED_SUMMONING", "POPULATION_GROUPS",
            "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE")),
    PAID_REVIVAL(Set.of(
            "COMMAND_FAMILY_ROSTERS", "PAID_COMMAND_REVIVAL", "POPULATION_GROUPS",
            "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE")),
    SOUL_BOND_CLAIM(Set.of(
            "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE", "POPULATION_GROUPS",
            "COMPANION_PROVISIONING", "COMMAND_FAMILY_ROSTERS", "COMMAND_TIMED_SUMMONING",
            "INTERACTION_EXTENSIONS")),
    MINIWYVERN_ATTUNEMENT(Set.of("PROFILE_DATA", "PROFILE_DATA_TRANSACTIONS")),
    MINIWYVERN_ABILITIES(Set.of(
            "EVENTS", "PROFILES", "POLICY", "PROFILE_DATA", "PROFILE_DATA_TRANSACTIONS",
            "COMPANION_PROVISIONING")),
    DYNAMIC_ENCOUNTERS(Set.of(
            "CAPTURE_POLICY", "POPULATION_GROUPS", "PROFILES", "POLICY",
            "INTERACTION_EXTENSIONS", "EVENTS", "CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION")),
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
