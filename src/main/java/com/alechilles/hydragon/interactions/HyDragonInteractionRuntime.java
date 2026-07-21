package com.alechilles.hydragon.interactions;

import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.hydragon.runtime.ConsumableReservation;
import com.alechilles.hydragon.runtime.GameplayResult;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/** Single installable dispatch seam between Hytale interaction codecs and domain sagas. */
public final class HyDragonInteractionRuntime {
    private static final AtomicReference<Handler> HANDLER = new AtomicReference<>();

    private HyDragonInteractionRuntime() {
    }

    public static void install(Handler handler) {
        if (!HANDLER.compareAndSet(null, Objects.requireNonNull(handler, "handler"))) {
            throw new IllegalStateException("HyDragon interaction runtime is already installed");
        }
    }

    public static void uninstall(Handler handler) {
        HANDLER.compareAndSet(Objects.requireNonNull(handler, "handler"), null);
    }

    public static boolean installed() {
        return HANDLER.get() != null;
    }

    static CompletionStage<GameplayResult> dispatch(Action action,
                                                     UUID playerUuid,
                                                     String worldName,
                                                     PopulationAdmissionLocation destination,
                                                     String archetypeId,
                                                     ConsumableReservation reservation,
                                                     HeldItemLocator heldItemLocator) {
        Handler handler = HANDLER.get();
        if (handler == null) {
            return reservation.release().handle((ignored, failure) ->
                    GameplayResult.unavailable("HyDragon gameplay runtime is unavailable"));
        }
        try {
            return switch (action) {
                case SOUL_BOND -> handler.soulBond(playerUuid, worldName, destination, reservation);
                case ATTUNE -> handler.attune(playerUuid, archetypeId, reservation);
                case REPAIR -> handler.repair(playerUuid, worldName, heldItemLocator, reservation);
            };
        } catch (RuntimeException failure) {
            return reservation.release().handle((ignored, releaseFailure) ->
                    GameplayResult.retryable("HyDragon gameplay request failed before dispatch"));
        }
    }

    enum Action { SOUL_BOND, ATTUNE, REPAIR }

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
