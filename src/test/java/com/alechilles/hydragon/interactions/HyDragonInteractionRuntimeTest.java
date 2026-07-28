package com.alechilles.hydragon.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.runtime.ConsumableReservation;
import com.alechilles.hydragon.runtime.GameplayResult;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HyDragonInteractionRuntimeTest {
    @Test
    void missingRuntimeReleasesConsumableAndFailsClosed() {
        CountingReservation item = new CountingReservation();
        GameplayResult result = HyDragonInteractionRuntime.dispatch(
                HyDragonInteractionRuntime.Action.SOUL_BOND,
                HyDragonFeature.SOUL_BOND_CLAIM,
                UUID.randomUUID(), "world", new PopulationAdmissionLocation("world", 0, 0),
                item).toCompletableFuture().join();
        assertEquals(GameplayResult.Status.UNAVAILABLE, result.status());
        assertEquals(1, item.releases.get());
    }

    @Test
    void missingCapabilityReleasesBeforeHandlerMutation() {
        CountingHandler handler = new CountingHandler();
        CountingReservation item = new CountingReservation();
        HyDragonInteractionRuntime.install(handler, () -> snapshot(false));
        try {
            GameplayResult result = HyDragonInteractionRuntime.dispatch(
                    HyDragonInteractionRuntime.Action.SOUL_BOND,
                    HyDragonFeature.SOUL_BOND_CLAIM,
                    UUID.randomUUID(), "world", new PopulationAdmissionLocation("world", 0, 0),
                    item).toCompletableFuture().join();
            assertEquals(GameplayResult.Status.UNAVAILABLE, result.status());
            assertEquals(1, item.releases.get());
            assertEquals(0, handler.claims.get());
        } finally {
            HyDragonInteractionRuntime.uninstall(handler);
        }
    }

    @Test
    void readyFeatureDispatchesExactlyOnceAndInstallIsExclusive() {
        CountingHandler handler = new CountingHandler();
        CountingReservation item = new CountingReservation();
        HyDragonInteractionRuntime.install(handler, () -> snapshot(true));
        try {
            assertThrows(IllegalStateException.class,
                    () -> HyDragonInteractionRuntime.install(new CountingHandler(), () -> snapshot(true)));
            GameplayResult result = HyDragonInteractionRuntime.dispatch(
                    HyDragonInteractionRuntime.Action.SOUL_BOND,
                    HyDragonFeature.SOUL_BOND_CLAIM,
                    UUID.randomUUID(), "world", new PopulationAdmissionLocation("world", 0, 0),
                    item).toCompletableFuture().join();
            assertEquals(GameplayResult.Status.APPLIED, result.status());
            assertEquals(1, handler.claims.get());
            assertEquals(0, item.releases.get());
        } finally {
            HyDragonInteractionRuntime.uninstall(handler);
        }
    }

    private static TameworkBridge.Snapshot snapshot(boolean ready) {
        Map<HyDragonFeature, FeatureGate> gates = new EnumMap<>(HyDragonFeature.class);
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            gates.put(feature, new FeatureGate(feature, ready, feature.requiredCapabilities(),
                    ready ? Set.of() : feature.requiredCapabilities(), ready ? java.util.List.of() : java.util.List.of("missing")));
        }
        return new TameworkBridge.Snapshot("test", Set.of(), Map.copyOf(gates), null);
    }

    private static final class CountingHandler implements HyDragonInteractionRuntime.Handler {
        private final AtomicInteger claims = new AtomicInteger();

        @Override
        public CompletionStage<GameplayResult> soulBond(
                UUID playerUuid, String worldName, PopulationAdmissionLocation destination,
                ConsumableReservation reservation) {
            claims.incrementAndGet();
            return CompletableFuture.completedFuture(GameplayResult.applied("claimed"));
        }
    }

    private static final class CountingReservation implements ConsumableReservation {
        private final AtomicInteger releases = new AtomicInteger();
        @Override public String operationId() { return "test-operation"; }
        @Override public SourceEvidence sourceEvidence() {
            return new SourceEvidence("Wyvern_Egg", "owner", "hotbar", 0, 0, "egg", 1);
        }
        @Override public int quantity() { return 1; }
        @Override public CompletionStage<Disposition> consume() {
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
        @Override public CompletionStage<Disposition> release() {
            releases.incrementAndGet();
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
    }
}
