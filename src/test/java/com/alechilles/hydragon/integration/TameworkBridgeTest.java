package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TameworkBridgeTest {
    private static final Set<String> BASELINE = Set.of(
            "PROFILES", "POLICY", "INTERACTION_EXTENSIONS", "PROFILE_DATA", "EVENTS",
            "DIAGNOSTICS", "PERSISTENCE_RESILIENCE", "POPULATION_GROUPS",
            "COMPANION_PROVISIONING", "PROFILE_DATA_TRANSACTIONS");

    @Test
    void oldBondedVesselCapabilityCannotEnableNewRosterFeatures() {
        Set<String> capabilities = new HashSet<>(BASELINE);
        capabilities.add("BONDED_VESSELS");
        capabilities.add("CAPTURE_POLICY");

        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("3.0.0", capabilities, null);

        assertFalse(snapshot.feature(HyDragonFeature.CAPTURE_AND_ROSTER).available());
        assertTrue(snapshot.feature(HyDragonFeature.CAPTURE_AND_ROSTER).missingCapabilities()
                .containsAll(Set.of("COMMAND_FAMILY_ROSTERS", "CAPTURE_TAME_AND_LINK",
                        "CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION", "COMMAND_TIMED_SUMMONING")));
        assertFalse(snapshot.feature(HyDragonFeature.SOUL_BOND_CLAIM).available());
    }

    @Test
    void granularCapabilitiesEnableOnlyTheirCompleteContracts() {
        Set<String> capabilities = new HashSet<>(BASELINE);
        capabilities.addAll(Set.of("CAPTURE_POLICY", "COMMAND_FAMILY_ROSTERS",
                "CAPTURE_TAME_AND_LINK", "CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION",
                "COMMAND_TIMED_SUMMONING", "PAID_COMMAND_REVIVAL"));

        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("3.0.0", capabilities, null);

        assertTrue(snapshot.feature(HyDragonFeature.CAPTURE_AND_ROSTER).available());
        assertTrue(snapshot.feature(HyDragonFeature.DRAGON_HORN).available());
        assertTrue(snapshot.feature(HyDragonFeature.TIMED_SUMMONING).available());
        assertTrue(snapshot.feature(HyDragonFeature.PAID_REVIVAL).available());
        assertTrue(snapshot.feature(HyDragonFeature.SOUL_BOND_CLAIM).available());
    }

    @Test
    void missingPaidRevivalDoesNotDisableOrdinaryHornCommands() {
        Set<String> capabilities = new HashSet<>(BASELINE);
        capabilities.addAll(Set.of("COMMAND_FAMILY_ROSTERS", "COMMAND_TIMED_SUMMONING"));
        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("3.0.0", capabilities, null);
        assertTrue(snapshot.feature(HyDragonFeature.DRAGON_HORN).available());
        assertTrue(snapshot.feature(HyDragonFeature.TIMED_SUMMONING).available());
        assertFalse(snapshot.feature(HyDragonFeature.PAID_REVIVAL).available());
    }

    @Test
    void bootstrapFailureDisablesEverythingAndSnapshotIsImmutable() {
        Set<String> input = new HashSet<>(BASELINE);
        TameworkBridge.Snapshot failed = TameworkBridge.evaluate(null, Set.of(), "not loaded");
        assertFalse(failed.apiAvailable());
        for (FeatureGate gate : failed.features().values()) assertFalse(gate.available());

        TameworkBridge.Snapshot copied = TameworkBridge.evaluate("3.0.0", input, null);
        input.clear();
        assertTrue(copied.capabilities().contains("DIAGNOSTICS"));
        assertThrows(UnsupportedOperationException.class, () -> copied.capabilities().add("OTHER"));
    }
}
