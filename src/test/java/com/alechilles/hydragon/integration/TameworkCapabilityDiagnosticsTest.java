package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TameworkCapabilityDiagnosticsTest {
    @Test
    void emitsOneDeterministicActionableEntryPerRequiredCapability() {
        Map<HyDragonFeature, FeatureGate> gates = new EnumMap<>(HyDragonFeature.class);
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            gates.put(feature, new FeatureGate(
                    feature, false, feature.requiredCapabilities(), feature.requiredCapabilities(), List.of()));
        }
        TameworkBridge.Snapshot snapshot = new TameworkBridge.Snapshot(
                "0.9.0", Set.of("DIAGNOSTICS"), gates, null);

        List<TameworkCapabilityDiagnostics.Entry> entries = TameworkCapabilityDiagnostics.evaluate(snapshot);

        long expected = java.util.Arrays.stream(HyDragonFeature.values())
                .flatMap(feature -> feature.requiredCapabilities().stream())
                .distinct()
                .count();
        assertEquals(expected, entries.size());
        assertEquals(entries.stream().map(TameworkCapabilityDiagnostics.Entry::capability).sorted().toList(),
                entries.stream().map(TameworkCapabilityDiagnostics.Entry::capability).toList());

        TameworkCapabilityDiagnostics.Entry missing = entries.stream()
                .filter(entry -> entry.capability().equals("BONDED_VESSELS"))
                .findFirst()
                .orElseThrow();
        assertFalse(missing.present());
        assertTrue(missing.affectedFeatures().contains(HyDragonFeature.CAPTURE_AND_BOND));
        assertTrue(missing.operatorAction().contains(">=3.0.0 <4.0.0"));
        assertTrue(missing.operatorAction().contains("BONDED_VESSELS"));
        assertTrue(missing.format().contains("MISSING"));

        TameworkCapabilityDiagnostics.Entry present = entries.stream()
                .filter(entry -> entry.capability().equals("DIAGNOSTICS"))
                .findFirst()
                .orElseThrow();
        assertTrue(present.present());
        assertEquals("none", present.operatorAction());
        assertTrue(present.format().contains("READY"));
    }
}
