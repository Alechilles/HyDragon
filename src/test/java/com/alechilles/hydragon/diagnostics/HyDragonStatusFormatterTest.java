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
                true, true, 1, 1, 0, 3, 0, 5,
                List.of(new HyDragonPersistenceStatus.OrphanedLink(
                        "SOUL_BOND_PROFILE_MISSING",
                        "player=11111111-1111-1111-1111-111111111111, profile=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        "restore the matching profile extension or quarantine the Soul Bond record")),
                null);
        List<String> lines = HyDragonStatusFormatter.format("1.0.0", config, bridge, diagnostics, localPersistence);

        assertTrue(lines.stream().anyMatch(line -> line.equals("HyDragon 1.0.0 status")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("required=>=3.0.0 <4.0.0")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Config: INVALID")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("missing bundled assets")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("CAPTURE_AND_BOND: DISABLED")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("TAMEWORK_DIAGNOSTICS: READY")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Tamework persistence: HEALTHY")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("HyDragon persistence: READ_WRITE")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("pendingProfileProjections=3")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("orphan[SOUL_BOND_PROFILE_MISSING]")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("action=restore the matching profile extension")));
    }
}
