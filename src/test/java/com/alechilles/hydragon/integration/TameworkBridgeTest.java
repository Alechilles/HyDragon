package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TameworkBridgeTest {
    private static final Set<String> BASELINE = Set.of(
            "INTERACTION_EXTENSIONS", "EVENTS", "DIAGNOSTICS",
            "CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION");

    @Test
    void oldBondedVesselCapabilityCannotEnableNewRosterFeatures() {
        Set<String> capabilities = new HashSet<>(BASELINE);
        capabilities.add("BONDED_VESSELS");
        capabilities.add("CAPTURE_POLICY");

        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("3.0.0", capabilities, null);

        assertFalse(snapshot.feature(HyDragonFeature.CAPTURE_AND_ROSTER).available());
        assertTrue(snapshot.feature(HyDragonFeature.CAPTURE_AND_ROSTER).missingCapabilities()
                .contains("BONDED_COMPANIONS"));
        assertFalse(snapshot.feature(HyDragonFeature.SOUL_BOND_CLAIM).available());
    }

    @Test
    void granularCapabilitiesEnableOnlyTheirCompleteContracts() {
        Set<String> capabilities = new HashSet<>(BASELINE);
        capabilities.addAll(Set.of("CAPTURE_POLICY", "BONDED_COMPANIONS"));

        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("3.0.0", capabilities, null);

        assertTrue(snapshot.feature(HyDragonFeature.CAPTURE_AND_ROSTER).available());
        assertTrue(snapshot.feature(HyDragonFeature.DRAGON_HORN).available());
        assertTrue(snapshot.feature(HyDragonFeature.TIMED_SUMMONING).available());
        assertTrue(snapshot.feature(HyDragonFeature.PAID_REVIVAL).available());
        assertTrue(snapshot.feature(HyDragonFeature.SOUL_BOND_CLAIM).available());
    }

    @Test
    void missingBondedAuthorityDisablesAllBondedHornActionsTogether() {
        Set<String> capabilities = new HashSet<>(BASELINE);
        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("3.0.0", capabilities, null);
        assertFalse(snapshot.feature(HyDragonFeature.DRAGON_HORN).available());
        assertFalse(snapshot.feature(HyDragonFeature.TIMED_SUMMONING).available());
        assertFalse(snapshot.feature(HyDragonFeature.PAID_REVIVAL).available());
    }

    @Test
    void semanticEventConsumersRequireTheirAuthoritativeFeatureCapabilities() {
        Set<String> encounterCapabilities = new HashSet<>(
                HyDragonFeature.DYNAMIC_ENCOUNTERS.requiredCapabilities());
        encounterCapabilities.remove("CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION");
        TameworkBridge.Snapshot encounters = TameworkBridge.evaluate(
                "0.9.0", encounterCapabilities, null);

        assertFalse(encounters.feature(HyDragonFeature.DYNAMIC_ENCOUNTERS).available());
        assertTrue(encounters.feature(HyDragonFeature.DYNAMIC_ENCOUNTERS)
                .missingCapabilities().contains("CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION"));

        Set<String> abilityCapabilities = new HashSet<>(
                HyDragonFeature.MINIWYVERN_ABILITIES.requiredCapabilities());
        abilityCapabilities.remove("BONDED_COMPANIONS");
        TameworkBridge.Snapshot abilities = TameworkBridge.evaluate(
                "0.9.0", abilityCapabilities, null);

        assertFalse(abilities.feature(HyDragonFeature.MINIWYVERN_ABILITIES).available());
        assertTrue(abilities.feature(HyDragonFeature.MINIWYVERN_ABILITIES)
                .missingCapabilities().contains("BONDED_COMPANIONS"));
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
