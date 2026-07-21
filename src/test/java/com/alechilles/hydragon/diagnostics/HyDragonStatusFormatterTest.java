package com.alechilles.hydragon.diagnostics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.integration.TameworkRuntimeDiagnostics;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HyDragonStatusFormatterTest {
    @Test
    void reportsEachFeatureAndItsStableDisableReason() {
        HyDragonConfigRepository.Snapshot config = new HyDragonConfigRepository.Snapshot(
                Map.of(), Map.of(), Map.of(), Map.of(), List.of("missing bundled assets"));
        Map<HyDragonFeature, FeatureGate> gates = new EnumMap<>(HyDragonFeature.class);
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            boolean diagnostics = feature == HyDragonFeature.TAMEWORK_DIAGNOSTICS;
            gates.put(feature, new FeatureGate(
                    feature,
                    diagnostics,
                    diagnostics ? Set.of("DIAGNOSTICS") : Set.of("BONDED_VESSELS"),
                    diagnostics ? Set.of() : Set.of("BONDED_VESSELS"),
                    List.of()));
        }
        TameworkBridge.Snapshot bridge = new TameworkBridge.Snapshot("0.8.0", Set.of("DIAGNOSTICS"), gates, null);
        TameworkRuntimeDiagnostics.Snapshot diagnostics = new TameworkRuntimeDiagnostics.Snapshot(
                true, "HEALTHY", null, 0, "READY", "COMPLETE", "READ_WRITE", null);

        HyDragonPersistenceStatus localPersistence = new HyDragonPersistenceStatus(
                true, true, 1, 1, 0, 3, 0, 5, null);
        List<String> lines = HyDragonStatusFormatter.format(config, bridge, diagnostics, localPersistence);

        assertTrue(lines.stream().anyMatch(line -> line.contains("Config: INVALID")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("missing bundled assets")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("CAPTURE_AND_BOND: DISABLED")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("TAMEWORK_DIAGNOSTICS: READY")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Tamework persistence: HEALTHY")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("HyDragon persistence: READ_WRITE")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("pendingProfileProjections=3")));
    }
}
