package com.alechilles.hydragon.interactions;

import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.runtime.ConsumableReservation;
import com.alechilles.hydragon.runtime.GameplayResult;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Single installable dispatch seam between Hytale interaction codecs and domain sagas. */
public final class HyDragonInteractionRuntime {
    private static final AtomicReference<Installation> INSTALLATION = new AtomicReference<>();

    private HyDragonInteractionRuntime() {
    }

    public static void install(Handler handler) {
        install(handler, () -> null);
    }

    /** Installs gameplay plus the same immutable capability snapshot used by status diagnostics. */
    public static void install(Handler handler, Supplier<TameworkBridge.Snapshot> gates) {
        Installation installation = new Installation(
                Objects.requireNonNull(handler, "handler"), Objects.requireNonNull(gates, "gates"));
        if (!INSTALLATION.compareAndSet(null, installation)) {
            throw new IllegalStateException("HyDragon interaction runtime is already installed");
        }
    }

    public static void uninstall(Handler handler) {
        Objects.requireNonNull(handler, "handler");
        INSTALLATION.updateAndGet(installed -> installed != null && installed.handler() == handler ? null : installed);
    }

    public static boolean installed() {
        return INSTALLATION.get() != null;
    }

    static CompletionStage<GameplayResult> dispatch(Action action,
                                                     HyDragonFeature requiredFeature,
                                                     UUID playerUuid,
                                                     String worldName,
                                                     PopulationAdmissionLocation destination,
                                                     String archetypeId,
                                                     ConsumableReservation reservation,
                                                     HeldItemLocator heldItemLocator) {
        Installation installation = INSTALLATION.get();
        if (installation == null) {
            return reservation.release().handle((ignored, failure) ->
                    GameplayResult.unavailable("HyDragon gameplay runtime is unavailable"));
        }
        FeatureGate gate;
        try {
            TameworkBridge.Snapshot snapshot = installation.gates().get();
            gate = snapshot == null ? null : snapshot.feature(requiredFeature);
        } catch (RuntimeException failure) {
            gate = null;
        }
        if (gate == null || !gate.available()) {
            String reason = gate == null ? "Tamework feature readiness is unavailable" : gate.reason();
            return reservation.release().handle((ignored, failure) ->
                    GameplayResult.unavailable(requiredFeature + " is disabled: " + reason));
        }
        try {
            return switch (action) {
                case SOUL_BOND -> installation.handler().soulBond(
                        playerUuid, worldName, destination, reservation);
                case ATTUNE -> installation.handler().attune(playerUuid, archetypeId, reservation);
                case REPAIR -> installation.handler().repair(
                        playerUuid, worldName, heldItemLocator, reservation);
            };
        } catch (RuntimeException failure) {
            return reservation.release().handle((ignored, releaseFailure) ->
                    GameplayResult.retryable("HyDragon gameplay request failed before dispatch"));
        }
    }

    enum Action { SOUL_BOND, ATTUNE, REPAIR }

    private record Installation(Handler handler, Supplier<TameworkBridge.Snapshot> gates) {
    }

    public interface Handler {
        CompletionStage<GameplayResult> soulBond(
                UUID playerUuid,
                String worldName,
                PopulationAdmissionLocation destination,
                ConsumableReservation reservation);

        CompletionStage<GameplayResult> attune(
                UUID playerUuid, String archetypeId, ConsumableReservation reservation);

        CompletionStage<GameplayResult> repair(
                UUID playerUuid,
                String worldName,
                HeldItemLocator heldItemLocator,
                ConsumableReservation reservation);
    }

    /** Location-only evidence; Tamework resolves canonical revision/fingerprint and binding identity. */
    public record HeldItemLocator(
            String holderEvidenceId,
            String containerPath,
            int inventorySlot,
            String expectedItemId) {
        public HeldItemLocator {
            holderEvidenceId = required(holderEvidenceId, "holderEvidenceId");
            containerPath = required(containerPath, "containerPath");
            expectedItemId = required(expectedItemId, "expectedItemId");
            if (inventorySlot < 0) throw new IllegalArgumentException("inventorySlot cannot be negative");
        }

        private static String required(String value, String field) {
            String normalized = Objects.requireNonNull(value, field).trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
            return normalized;
        }
    }
}
