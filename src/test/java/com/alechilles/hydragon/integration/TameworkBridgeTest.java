package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TameworkBridgeTest {
    private static final Set<String> BASELINE_08 = Set.of(
            "PROFILES", "COMMAND_LINKS", "PROGRESSION", "PROGRESSION_MUTATIONS", "POLICY",
            "INTERACTION_EXTENSIONS", "TRAIT_EFFECTS", "PROFILE_DATA", "EVENTS",
            "COMPANION_XP_EVENTS", "CONFIG_READ", "DIAGNOSTICS", "PERSISTENCE_RESILIENCE");

    @Test
    void tameworkThreePublicApiBaselineFailsNewSystemsClosed() {
        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("0.8.0", BASELINE_08, null);

        assertTrue(snapshot.apiAvailable());
        assertTrue(snapshot.feature(HyDragonFeature.TAMEWORK_DIAGNOSTICS).available());
        assertFalse(snapshot.feature(HyDragonFeature.CAPTURE_AND_BOND).available());
        assertTrue(snapshot.feature(HyDragonFeature.CAPTURE_AND_BOND).missingCapabilities()
                .containsAll(Set.of("CAPTURE_POLICY", "BONDED_VESSELS", "POPULATION_GROUPS")));
        assertFalse(snapshot.feature(HyDragonFeature.SOUL_BOND_CLAIM).available());
        assertFalse(snapshot.feature(HyDragonFeature.MINIWYVERN_ATTUNEMENT).available());
        assertTrue(snapshot.feature(HyDragonFeature.MINIWYVERN_ATTUNEMENT).missingCapabilities()
                .contains("PROFILE_DATA_TRANSACTIONS"));
    }

    @Test
    void newCapabilitiesEnableOnlyFullySpecifiedContracts() {
        Set<String> capabilities = new HashSet<>(BASELINE_08);
        capabilities.addAll(Set.of(
                "CAPTURE_POLICY", "BONDED_VESSELS", "POPULATION_GROUPS", "COMPANION_PROVISIONING",
                "PROFILE_DATA_TRANSACTIONS"));

        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("0.9.0", capabilities, null);

        assertTrue(snapshot.feature(HyDragonFeature.CAPTURE_AND_BOND).available());
        assertTrue(snapshot.feature(HyDragonFeature.BONDED_STONE_REPAIR).available());
        assertTrue(snapshot.feature(HyDragonFeature.DYNAMIC_ENCOUNTERS).available());
        assertTrue(snapshot.feature(HyDragonFeature.BONDED_STONE_TRANSITIONS).available());
        assertTrue(snapshot.feature(HyDragonFeature.SOUL_BOND_CLAIM).available());
        assertTrue(snapshot.feature(HyDragonFeature.MINIWYVERN_ATTUNEMENT).available());
        assertTrue(snapshot.feature(HyDragonFeature.MINIWYVERN_ABILITIES).available());
    }

    @Test
    void bootstrapFailureDisablesEverythingWithOneStableReason() {
        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate(null, Set.of(), "not loaded");

        assertFalse(snapshot.apiAvailable());
        for (FeatureGate gate : snapshot.features().values()) {
            assertFalse(gate.available());
            assertTrue(gate.reason().contains("not loaded") || !gate.missingCapabilities().isEmpty());
        }
    }

    @Test
    void snapshotsDoNotRetainMutableCapabilityInput() {
        Set<String> capabilities = new HashSet<>(BASELINE_08);
        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("0.8.0", capabilities, null);
        capabilities.clear();

        assertTrue(snapshot.capabilities().contains("DIAGNOSTICS"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.capabilities().add("OTHER"));
    }
}
