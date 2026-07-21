package com.alechilles.hydragon.diagnostics;

import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.integration.TameworkRuntimeDiagnostics;
import java.util.ArrayList;
import java.util.List;

/** Stable, compact operator-facing readiness report. */
public final class HyDragonStatusFormatter {
    private HyDragonStatusFormatter() {
    }

    public static List<String> format(
            HyDragonConfigRepository.Snapshot config,
            TameworkBridge.Snapshot tamework,
            TameworkRuntimeDiagnostics.Snapshot diagnostics) {
        List<String> lines = new ArrayList<>();
        lines.add("HyDragon status");
        lines.add("Config: " + (config.isValid() ? "READY" : "INVALID")
                + "; species=" + config.species().size()
                + ", archetypes=" + config.archetypes().size()
                + ", encounters=" + config.encounters().size()
                + ", issues=" + config.issues().size());
        for (String issue : config.issues().stream().limit(5).toList()) {
            lines.add("  config: " + issue);
        }
        if (config.issues().size() > 5) {
            lines.add("  config: ... and " + (config.issues().size() - 5) + " more");
        }

        lines.add("Tamework Public API: " + tamework.apiVersion()
                + "; capabilities=" + tamework.capabilities().size());
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            FeatureGate gate = tamework.feature(feature);
            lines.add("  " + feature + ": " + (gate.available() ? "READY" : "DISABLED — " + gate.reason()));
        }
        lines.add("Tamework persistence: " + diagnostics.persistenceStatus()
                + "; queue=" + diagnostics.queueDepth()
                + "; population=" + diagnostics.populationReadiness()
                + "; resilience=" + diagnostics.resilienceState());
        if (!diagnostics.available() && diagnostics.persistenceReason() != null) {
            lines.add("  diagnostics: " + diagnostics.persistenceReason());
        }
        return List.copyOf(lines);
    }
}
