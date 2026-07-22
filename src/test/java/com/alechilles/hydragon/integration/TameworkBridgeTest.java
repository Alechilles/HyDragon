package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
    void liveRuntimeDependenciesArePartOfTheirReportedGates() {
        Set<String> capabilities = new HashSet<>(BASELINE_08);
        capabilities.addAll(Set.of(
                "CAPTURE_POLICY", "BONDED_VESSELS", "POPULATION_GROUPS", "COMPANION_PROVISIONING",
                "PROFILE_DATA_TRANSACTIONS"));
        capabilities.remove("PROFILES");

        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("0.9.0", capabilities, null);

        assertFalse(snapshot.feature(HyDragonFeature.BONDED_STONE_REPAIR).available());
        assertFalse(snapshot.feature(HyDragonFeature.DYNAMIC_ENCOUNTERS).available());
        assertFalse(snapshot.feature(HyDragonFeature.MINIWYVERN_ABILITIES).available());
        assertTrue(snapshot.feature(HyDragonFeature.BONDED_STONE_REPAIR).reason().contains("PROFILES"));
        assertTrue(snapshot.feature(HyDragonFeature.DYNAMIC_ENCOUNTERS).reason().contains("PROFILES"));
        assertTrue(snapshot.feature(HyDragonFeature.MINIWYVERN_ABILITIES).reason().contains("PROFILES"));
    }

    @Test
    void localAbilityEffectsDoNotRequireTameworkTraitEffects() {
        Set<String> capabilities = new HashSet<>(BASELINE_08);
        capabilities.add("PROFILE_DATA_TRANSACTIONS");
        capabilities.remove("TRAIT_EFFECTS");

        TameworkBridge.Snapshot snapshot = TameworkBridge.evaluate("0.9.0", capabilities, null);

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

    @Test
    void bridgeObservesRecoveryCapabilitiesAdvertisedAfterStartup() {
        AtomicReference<EnumSet<TameworkApiCapability>> capabilities = new AtomicReference<>(
                EnumSet.complementOf(EnumSet.of(
                        TameworkApiCapability.BONDED_VESSELS,
                        TameworkApiCapability.POPULATION_GROUPS,
                        TameworkApiCapability.COMPANION_PROVISIONING)));
        TameworkApi api = dynamicApi(capabilities);
        TameworkBridge bridge = TameworkBridge.connect(api);

        assertFalse(bridge.snapshot().feature(HyDragonFeature.SOUL_BOND_CLAIM).available());

        capabilities.updateAndGet(current -> {
            EnumSet<TameworkApiCapability> recovered = current.clone();
            recovered.add(TameworkApiCapability.BONDED_VESSELS);
            recovered.add(TameworkApiCapability.POPULATION_GROUPS);
            recovered.add(TameworkApiCapability.COMPANION_PROVISIONING);
            return recovered;
        });

        assertTrue(bridge.snapshot().feature(HyDragonFeature.SOUL_BOND_CLAIM).available());
        assertTrue(bridge.snapshot().feature(HyDragonFeature.CAPTURE_AND_BOND).available());
    }

    @Test
    void capabilityRefreshFailureFailsClosed() {
        AtomicReference<EnumSet<TameworkApiCapability>> capabilities = new AtomicReference<>(
                EnumSet.allOf(TameworkApiCapability.class));
        TameworkBridge bridge = TameworkBridge.connect(dynamicApi(capabilities));
        capabilities.set(null);

        TameworkBridge.Snapshot failed = bridge.snapshot();

        assertFalse(failed.apiAvailable());
        assertFalse(failed.feature(HyDragonFeature.SOUL_BOND_CLAIM).available());
        assertTrue(failed.bootstrapIssue().contains("refresh failed"));
    }

    private static TameworkApi dynamicApi(
            AtomicReference<EnumSet<TameworkApiCapability>> capabilities) {
        return (TameworkApi) Proxy.newProxyInstance(
                TameworkApi.class.getClassLoader(),
                new Class<?>[] {TameworkApi.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getApiVersion" -> "0.9.0";
                    case "getCapabilities" -> {
                        EnumSet<TameworkApiCapability> current = capabilities.get();
                        if (current == null) throw new IllegalStateException("recovery unavailable");
                        yield current.clone();
                    }
                    case "toString" -> "DynamicTameworkApi";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
    }
}
