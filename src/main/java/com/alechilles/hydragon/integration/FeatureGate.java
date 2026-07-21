package com.alechilles.hydragon.integration;

import java.util.List;
import java.util.Set;

/** Immutable explanation of why one runtime feature is enabled or fail-closed. */
public record FeatureGate(
        HyDragonFeature feature,
        boolean available,
        Set<String> requiredCapabilities,
        Set<String> missingCapabilities,
        List<String> contractBlockers) {
    public FeatureGate {
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        missingCapabilities = Set.copyOf(missingCapabilities);
        contractBlockers = List.copyOf(contractBlockers);
    }

    public String reason() {
        if (available) return "ready";
        if (!missingCapabilities.isEmpty()) return "missing capabilities " + missingCapabilities;
        if (!contractBlockers.isEmpty()) return String.join("; ", contractBlockers);
        return "Tamework API unavailable";
    }
}
