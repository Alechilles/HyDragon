package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behavioral coverage for independently gated HyDragon runtime composition. */
final class HyDragonRuntimeCompositionTest {

    @Test
    void bondedOnlyDegradedTameworkStartsGameplayWithoutInstallingUnavailableFeatures() {
        List<HyDragonRuntimeComposition.Failure> failures = new ArrayList<>();
        HyDragonRuntimeComposition composition = new HyDragonRuntimeComposition(failures::add);
        TrackingRuntime gameplay = new TrackingRuntime("gameplay", new ArrayList<>());

        TrackingRuntime installedGameplay = composition.install(
                HyDragonRuntimeComposition.Slot.BONDED_GAMEPLAY,
                available(HyDragonFeature.SOUL_BOND_CLAIM),
                () -> gameplay);
        TrackingRuntime installedAbilities = composition.install(
                HyDragonRuntimeComposition.Slot.MINIWYVERN_ABILITIES,
                unavailable(HyDragonFeature.MINIWYVERN_ABILITIES),
                () -> {
                    throw new AssertionError("unavailable ability installer ran");
                });
        TrackingRuntime installedEncounters = composition.install(
                HyDragonRuntimeComposition.Slot.DYNAMIC_ENCOUNTERS,
                unavailable(HyDragonFeature.DYNAMIC_ENCOUNTERS),
                () -> {
                    throw new AssertionError("unavailable encounter installer ran");
                });

        assertSame(gameplay, installedGameplay);
        assertNull(installedAbilities);
        assertNull(installedEncounters);
        assertEquals(Set.of(HyDragonRuntimeComposition.Slot.BONDED_GAMEPLAY),
                composition.startedSlots());
        assertTrue(failures.isEmpty());
    }

    @Test
    void encounterInstallationFailureLeavesGameplayRunningAndStillStartsAbilities() {
        List<HyDragonRuntimeComposition.Failure> failures = new ArrayList<>();
        HyDragonRuntimeComposition composition = new HyDragonRuntimeComposition(failures::add);
        List<String> closed = new ArrayList<>();
        TrackingRuntime gameplay = new TrackingRuntime("gameplay", closed);
        TrackingRuntime abilities = new TrackingRuntime("abilities", closed);

        composition.install(HyDragonRuntimeComposition.Slot.BONDED_GAMEPLAY,
                available(HyDragonFeature.SOUL_BOND_CLAIM), () -> gameplay);
        TrackingRuntime encounters = composition.install(
                HyDragonRuntimeComposition.Slot.DYNAMIC_ENCOUNTERS,
                available(HyDragonFeature.DYNAMIC_ENCOUNTERS),
                () -> {
                    throw new IllegalStateException("encounter failed");
                });
        TrackingRuntime installedAbilities = composition.install(
                HyDragonRuntimeComposition.Slot.MINIWYVERN_ABILITIES,
                available(HyDragonFeature.MINIWYVERN_ABILITIES), () -> abilities);

        assertNull(encounters);
        assertSame(abilities, installedAbilities);
        assertFalse(gameplay.closed);
        assertEquals(Set.of(
                        HyDragonRuntimeComposition.Slot.BONDED_GAMEPLAY,
                        HyDragonRuntimeComposition.Slot.MINIWYVERN_ABILITIES),
                composition.startedSlots());
        assertEquals(List.of(HyDragonRuntimeComposition.Slot.DYNAMIC_ENCOUNTERS),
                failures.stream().map(HyDragonRuntimeComposition.Failure::slot).toList());
    }

    @Test
    void abilityInstallationFailureLeavesGameplayRunningAndStillStartsEncounters() {
        List<HyDragonRuntimeComposition.Failure> failures = new ArrayList<>();
        HyDragonRuntimeComposition composition = new HyDragonRuntimeComposition(failures::add);
        List<String> closed = new ArrayList<>();
        TrackingRuntime gameplay = new TrackingRuntime("gameplay", closed);
        TrackingRuntime encounters = new TrackingRuntime("encounters", closed);

        composition.install(HyDragonRuntimeComposition.Slot.BONDED_GAMEPLAY,
                available(HyDragonFeature.SOUL_BOND_CLAIM), () -> gameplay);
        TrackingRuntime abilities = composition.install(
                HyDragonRuntimeComposition.Slot.MINIWYVERN_ABILITIES,
                available(HyDragonFeature.MINIWYVERN_ABILITIES),
                () -> {
                    throw new LinkageError("ability failed");
                });
        TrackingRuntime installedEncounters = composition.install(
                HyDragonRuntimeComposition.Slot.DYNAMIC_ENCOUNTERS,
                available(HyDragonFeature.DYNAMIC_ENCOUNTERS), () -> encounters);

        assertNull(abilities);
        assertSame(encounters, installedEncounters);
        assertFalse(gameplay.closed);
        assertEquals(Set.of(
                        HyDragonRuntimeComposition.Slot.BONDED_GAMEPLAY,
                        HyDragonRuntimeComposition.Slot.DYNAMIC_ENCOUNTERS),
                composition.startedSlots());
        assertEquals(List.of(HyDragonRuntimeComposition.Slot.MINIWYVERN_ABILITIES),
                failures.stream().map(HyDragonRuntimeComposition.Failure::slot).toList());
    }

    @Test
    void closeStopsOnlySuccessfulInstallationsInReverseOrder() {
        List<HyDragonRuntimeComposition.Failure> failures = new ArrayList<>();
        HyDragonRuntimeComposition composition = new HyDragonRuntimeComposition(failures::add);
        List<String> closed = new ArrayList<>();
        TrackingRuntime gameplay = new TrackingRuntime("gameplay", closed);
        TrackingRuntime abilities = new TrackingRuntime("abilities", closed);

        composition.install(HyDragonRuntimeComposition.Slot.BONDED_GAMEPLAY,
                available(HyDragonFeature.SOUL_BOND_CLAIM), () -> gameplay);
        composition.install(HyDragonRuntimeComposition.Slot.DYNAMIC_ENCOUNTERS,
                available(HyDragonFeature.DYNAMIC_ENCOUNTERS),
                () -> { throw new IllegalStateException("encounter failed"); });
        composition.install(HyDragonRuntimeComposition.Slot.MINIWYVERN_ABILITIES,
                available(HyDragonFeature.MINIWYVERN_ABILITIES), () -> abilities);

        composition.close();
        composition.close();

        assertEquals(List.of("abilities", "gameplay"), closed);
        assertTrue(gameplay.closed);
        assertTrue(abilities.closed);
        assertTrue(composition.startedSlots().isEmpty());
        assertEquals(1, failures.stream()
                .filter(failure -> failure.phase()
                        == HyDragonRuntimeComposition.Phase.INSTALL)
                .count());
    }

    private static FeatureGate available(HyDragonFeature feature) {
        return new FeatureGate(feature, true, feature.requiredCapabilities(), Set.of(), List.of());
    }

    private static FeatureGate unavailable(HyDragonFeature feature) {
        return new FeatureGate(feature, false, feature.requiredCapabilities(),
                feature.requiredCapabilities(), List.of());
    }

    private static final class TrackingRuntime implements AutoCloseable {
        private final String name;
        private final List<String> closedOrder;
        private boolean closed;

        private TrackingRuntime(String name, List<String> closedOrder) {
            this.name = name;
            this.closedOrder = closedOrder;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            closedOrder.add(name);
        }
    }
}
