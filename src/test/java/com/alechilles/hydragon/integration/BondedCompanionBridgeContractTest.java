package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.BondedCompanionApi;
import com.alechilles.alecstamework.api.BondedCompanionAvailability;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Protects HyDragon's dedicated bonded-companion capability boundary. */
final class BondedCompanionBridgeContractTest {
    private static final Set<HyDragonFeature> BONDED_ONLY_FEATURES = Set.of(
            HyDragonFeature.DRAGON_HORN,
            HyDragonFeature.TIMED_SUMMONING,
            HyDragonFeature.PAID_REVIVAL,
            HyDragonFeature.SOUL_BOND_CLAIM,
            HyDragonFeature.MINIWYVERN_ABILITIES);

    private static final Set<String> LEGACY_GENERIC_CAPABILITIES = Set.of(
            "PROFILES", "POLICY", "PERSISTENCE_RESILIENCE",
            "POPULATION_GROUPS", "COMPANION_PROVISIONING",
            "COMMAND_FAMILY_ROSTERS", "COMMAND_TIMED_SUMMONING",
            "PAID_COMMAND_REVIVAL", "PROFILE_DATA",
            "PROFILE_DATA_TRANSACTIONS", "CAPTURE_TAME_AND_LINK");

    @Test
    void bondedCapabilityAloneEnablesEveryBondedOnlyFeature() {
        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate(
                "0.9.0", Set.of("BONDED_COMPANIONS"), null);

        for (HyDragonFeature feature : BONDED_ONLY_FEATURES) {
            assertTrue(snapshot.feature(feature).available(), feature.name());
            assertEquals(Set.of("BONDED_COMPANIONS"),
                    feature.requiredCapabilities(), feature.name());
        }
    }

    @Test
    void legacyGenericCapabilitiesNeverEnableBondedFeatures() {
        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate(
                "0.9.0", LEGACY_GENERIC_CAPABILITIES, null);

        for (HyDragonFeature feature : BONDED_ONLY_FEATURES) {
            assertFalse(snapshot.feature(feature).available(), feature.name());
            assertEquals(Set.of("BONDED_COMPANIONS"),
                    snapshot.feature(feature).missingCapabilities(),
                    feature.name());
        }
    }

    @Test
    void captureAndEncounterFeaturesRetainOnlyTheirCaptureEventDependencies() {
        Set<String> expected = Set.of(
                "BONDED_COMPANIONS",
                "CAPTURE_POLICY",
                "CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION",
                "INTERACTION_EXTENSIONS",
                "EVENTS");

        assertEquals(expected,
                HyDragonFeature.CAPTURE_AND_ROSTER.requiredCapabilities());
        assertEquals(expected,
                HyDragonFeature.DYNAMIC_ENCOUNTERS.requiredCapabilities());

        Set<String> withoutEvents = new HashSet<>(expected);
        withoutEvents.remove("EVENTS");
        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate(
                "0.9.0", withoutEvents, null);
        assertFalse(snapshot.feature(HyDragonFeature.DYNAMIC_ENCOUNTERS)
                .available());
        assertEquals(Set.of("EVENTS"), snapshot.feature(
                HyDragonFeature.DYNAMIC_ENCOUNTERS).missingCapabilities());
    }

    @Test
    void diagnosticsRemainIndependentFromBondedAuthority() {
        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate(
                "0.9.0", Set.of("DIAGNOSTICS"), null);

        assertTrue(snapshot.feature(HyDragonFeature.TAMEWORK_DIAGNOSTICS)
                .available());
        for (HyDragonFeature feature : BONDED_ONLY_FEATURES) {
            assertFalse(snapshot.feature(feature).available(), feature.name());
        }
    }

    @Test
    void advertisedButUnreadyBondedAuthorityFailsWithItsSpecificReason() {
        TameworkBridge bridge = TameworkBridge.connect(api(
                bondedUnavailable("bonded-schema-failed")));

        FeatureGate gate = bridge.snapshot().feature(
                HyDragonFeature.SOUL_BOND_CLAIM);

        assertFalse(gate.available());
        assertTrue(gate.contractBlockers().contains("bonded-schema-failed"));
        assertTrue(gate.missingCapabilities().isEmpty());
    }

    private static TameworkApi api(BondedCompanionApi bonded) {
        return (TameworkApi) Proxy.newProxyInstance(
                TameworkApi.class.getClassLoader(),
                new Class<?>[] {TameworkApi.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getApiVersion" -> "0.9.0";
                    case "getCapabilities" -> EnumSet.of(
                            TameworkApiCapability.BONDED_COMPANIONS);
                    case "bondedCompanions" -> bonded;
                    case "toString" -> "BondedOnlyTameworkApi";
                    default -> throw new AssertionError(
                            "Legacy facade must not be queried: " + method.getName());
                });
    }

    private static BondedCompanionApi bondedUnavailable(String reason) {
        return (BondedCompanionApi) Proxy.newProxyInstance(
                BondedCompanionApi.class.getClassLoader(),
                new Class<?>[] {BondedCompanionApi.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "availability" ->
                            BondedCompanionAvailability.unavailable(reason);
                    case "toString" -> "UnavailableBondedCompanionApi";
                    default -> throw new AssertionError(
                            "Availability probe called operation: "
                                    + method.getName());
                });
    }
}
