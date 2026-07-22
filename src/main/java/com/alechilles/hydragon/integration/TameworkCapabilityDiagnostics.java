package com.alechilles.hydragon.integration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Builds deterministic, actionable startup diagnostics for every capability HyDragon consumes. */
public final class TameworkCapabilityDiagnostics {
    private TameworkCapabilityDiagnostics() {
    }

    public static List<Entry> evaluate(TameworkBridge.Snapshot snapshot) {
        Set<String> required = new TreeSet<>();
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            required.addAll(feature.requiredCapabilities());
        }

        List<Entry> entries = new ArrayList<>(required.size());
        for (String capability : required) {
            EnumSet<HyDragonFeature> affected = EnumSet.noneOf(HyDragonFeature.class);
            for (HyDragonFeature feature : HyDragonFeature.values()) {
                if (feature.requiredCapabilities().contains(capability)) {
                    affected.add(feature);
                }
            }
            boolean present = snapshot.capabilities().contains(capability);
            String action = present
                    ? "none"
                    : "Install Tamework " + TameworkBridge.REQUIRED_TAMEWORK_RANGE
                            + " with Public API capability " + capability;
            entries.add(new Entry(capability, present, Set.copyOf(affected), action));
        }
        return List.copyOf(entries);
    }

    public record Entry(
            String capability,
            boolean present,
            Set<HyDragonFeature> affectedFeatures,
            String operatorAction) {
        public String format() {
            return "Tamework capability " + capability + ": " + (present ? "READY" : "MISSING")
                    + "; affectedFeatures=" + new TreeSet<>(affectedFeatures)
                    + "; requiredTamework=" + TameworkBridge.REQUIRED_TAMEWORK_RANGE
                    + "; action=" + operatorAction;
        }
    }
}
