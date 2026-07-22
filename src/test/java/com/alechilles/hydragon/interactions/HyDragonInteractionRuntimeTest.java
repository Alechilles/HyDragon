package com.alechilles.hydragon.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.config.StoneMaintenanceConfig;
import com.alechilles.hydragon.runtime.ConsumableReservation;
import com.alechilles.hydragon.runtime.GameplayResult;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HyDragonInteractionRuntimeTest {
    @Test
    void disabledDeclaredFeatureReleasesInputBeforeHandlerDispatch() throws Exception {
        CountingHandler handler = new CountingHandler();
        CountingReservation reservation = new CountingReservation();
        FeatureGate disabled = new FeatureGate(
                HyDragonFeature.MINIWYVERN_ATTUNEMENT,
                false,
                HyDragonFeature.MINIWYVERN_ATTUNEMENT.requiredCapabilities(),
                Set.of("PROFILE_DATA_TRANSACTIONS"),
                java.util.List.of());
        TameworkBridge.Snapshot snapshot = snapshot(disabled);
        HyDragonInteractionRuntime.install(handler, () -> snapshot);
        try {
            GameplayResult result = HyDragonInteractionRuntime.dispatch(
                    HyDragonInteractionRuntime.Action.ATTUNE,
                    HyDragonFeature.MINIWYVERN_ATTUNEMENT,
                    UUID.randomUUID(),
                    "default",
                    new PopulationAdmissionLocation("default", 0, 0),
                    "fire",
                    reservation,
                    null).toCompletableFuture().get();

            assertEquals(GameplayResult.Status.UNAVAILABLE, result.status());
            assertEquals(0, handler.calls.get());
            assertEquals(1, reservation.releases.get());
        } finally {
            HyDragonInteractionRuntime.uninstall(handler);
        }
    }

    @Test
    void enabledDeclaredFeatureDispatchesWithoutReleasingInput() throws Exception {
        CountingHandler handler = new CountingHandler();
        CountingReservation reservation = new CountingReservation();
        FeatureGate enabled = new FeatureGate(
                HyDragonFeature.MINIWYVERN_ATTUNEMENT,
                true,
                HyDragonFeature.MINIWYVERN_ATTUNEMENT.requiredCapabilities(),
                Set.of(),
                java.util.List.of());
        TameworkBridge.Snapshot snapshot = snapshot(enabled);
        HyDragonInteractionRuntime.install(handler, () -> snapshot);
        try {
            GameplayResult result = HyDragonInteractionRuntime.dispatch(
                    HyDragonInteractionRuntime.Action.ATTUNE,
                    HyDragonFeature.MINIWYVERN_ATTUNEMENT,
                    UUID.randomUUID(),
                    "default",
                    new PopulationAdmissionLocation("default", 0, 0),
                    "fire",
                    reservation,
                    null).toCompletableFuture().get();

            assertEquals(GameplayResult.Status.APPLIED, result.status());
            assertEquals(1, handler.calls.get());
            assertEquals(0, reservation.releases.get());
        } finally {
            HyDragonInteractionRuntime.uninstall(handler);
        }
    }

    @Test
    void missingOrFailingGateSnapshotFailsClosed() throws Exception {
        CountingHandler handler = new CountingHandler();
        CountingReservation reservation = new CountingReservation();
        HyDragonInteractionRuntime.install(handler, () -> {
            throw new IllegalStateException("bridge refresh failed");
        });
        try {
            GameplayResult result = HyDragonInteractionRuntime.dispatch(
                    HyDragonInteractionRuntime.Action.SOUL_BOND,
                    HyDragonFeature.SOUL_BOND_CLAIM,
                    UUID.randomUUID(),
                    "default",
                    new PopulationAdmissionLocation("default", 0, 0),
                    "",
                    reservation,
                    null).toCompletableFuture().get();

            assertEquals(GameplayResult.Status.UNAVAILABLE, result.status());
            assertEquals(0, handler.calls.get());
            assertEquals(1, reservation.releases.get());
        } finally {
            HyDragonInteractionRuntime.uninstall(handler);
        }
    }

    @Test
    void repairRequirementTracksCurrentConfigGeneration() {
        CountingHandler handler = new CountingHandler();
        AtomicReference<Optional<StoneMaintenanceConfig.RepairRequirement>> repair = new AtomicReference<>(
                Optional.of(new StoneMaintenanceConfig.RepairRequirement("Alternate_Repair_Material", 5)));
        HyDragonInteractionRuntime.install(handler, () -> null, repair::get);
        try {
            assertEquals(repair.get(), HyDragonInteractionRuntime.repairRequirement());

            repair.set(Optional.of(new StoneMaintenanceConfig.RepairRequirement("Reloaded_Material", 2)));
            assertEquals(repair.get(), HyDragonInteractionRuntime.repairRequirement());
        } finally {
            HyDragonInteractionRuntime.uninstall(handler);
        }
    }

    @Test
    void missingOrFailingRepairConfigFailsClosed() {
        CountingHandler handler = new CountingHandler();
        AtomicReference<Optional<StoneMaintenanceConfig.RepairRequirement>> repair =
                new AtomicReference<>(Optional.empty());
        HyDragonInteractionRuntime.install(handler, () -> null, repair::get);
        try {
            assertTrue(HyDragonInteractionRuntime.repairRequirement().isEmpty());

            repair.set(null);
            assertTrue(HyDragonInteractionRuntime.repairRequirement().isEmpty());
        } finally {
            HyDragonInteractionRuntime.uninstall(handler);
        }

        HyDragonInteractionRuntime.install(handler, () -> null, () -> {
            throw new IllegalStateException("config reload failed");
        });
        try {
            assertTrue(HyDragonInteractionRuntime.repairRequirement().isEmpty());
        } finally {
            HyDragonInteractionRuntime.uninstall(handler);
        }
    }

    private static TameworkBridge.Snapshot snapshot(FeatureGate gate) {
        return new TameworkBridge.Snapshot(
                "0.9.0", gate.requiredCapabilities(), Map.of(gate.feature(), gate), null);
    }

    private static final class CountingHandler implements HyDragonInteractionRuntime.Handler {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletionStage<GameplayResult> soulBond(
                UUID playerUuid,
                String worldName,
                PopulationAdmissionLocation destination,
                ConsumableReservation reservation) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(GameplayResult.applied("called"));
        }

        @Override
        public CompletionStage<GameplayResult> attune(
                UUID playerUuid, String archetypeId, ConsumableReservation reservation) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(GameplayResult.applied("called"));
        }

        @Override
        public CompletionStage<GameplayResult> repair(
                UUID playerUuid,
                String worldName,
                HyDragonInteractionRuntime.HeldItemLocator heldItemLocator,
                ConsumableReservation reservation) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(GameplayResult.applied("called"));
        }
    }

    private static final class CountingReservation implements ConsumableReservation {
        private final AtomicInteger releases = new AtomicInteger();

        @Override public String operationId() { return "op-1"; }

        @Override
        public SourceEvidence sourceEvidence() {
            return new SourceEvidence("item", "player:1", "hotbar", 0, 1L, "fingerprint", 1);
        }

        @Override public int quantity() { return 1; }

        @Override
        public CompletionStage<Disposition> consume() {
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }

        @Override
        public CompletionStage<Disposition> release() {
            releases.incrementAndGet();
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
    }
}
