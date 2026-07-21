package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.CompanionProvisioningResult;
import com.alechilles.alecstamework.api.CompanionProvisioningProjectionStatus;
import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.alecstamework.api.PopulationCompanionLifecycle;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;

/** Crash-recoverable once-per-player Soul Bond provisioning saga. */
public final class SoulBondService {
    private final TameworkGameplayAdapter tamework;
    private final SoulBondLedger ledger;
    private final OperationJournal journal;
    private final LongSupplier clock;

    public SoulBondService(TameworkGameplayAdapter tamework,
                           SoulBondLedger ledger,
                           OperationJournal journal,
                           LongSupplier clock) {
        this.tamework = Objects.requireNonNull(tamework, "tamework");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<GameplayResult> claim(UUID playerUuid,
                                                  String ownershipWorldName,
                                                  ConsumableReservation item) {
        return claim(playerUuid, ownershipWorldName,
                new PopulationAdmissionLocation(ownershipWorldName, 0, 0), item);
    }

    public CompletionStage<GameplayResult> claim(UUID playerUuid,
                                                  String ownershipWorldName,
                                                  PopulationAdmissionLocation destination,
                                                  ConsumableReservation item) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        ownershipWorldName = requiredText(ownershipWorldName, "ownershipWorldName");
        Objects.requireNonNull(destination, "destination");
        if (!ownershipWorldName.equals(destination.worldName())) {
            throw new IllegalArgumentException("destination must be in the ownership world");
        }
        Objects.requireNonNull(item, "item");
        TameworkGameplayAdapter.Readiness readiness = tamework.soulBondReadiness();
        if (!readiness.ready()) return released(item, GameplayResult.unavailable(readiness.reason()));
        if (!journal.available()) {
            return released(item, GameplayResult.unavailable("HyDragon durable operation journal is unavailable"));
        }

        String operationId = item.operationId();
        Optional<OperationJournal.Entry> prior = journal.find(operationId);
        if (prior.isPresent()) {
            OperationJournal.Entry entry = prior.orElseThrow();
            if (entry.kind() != OperationJournal.Kind.SOUL_BOND
                    || !entry.descriptor().ownerUuid().equals(playerUuid)
                    || !entry.descriptor().source().itemFingerprint()
                    .equals(item.sourceEvidence().itemFingerprint())) {
                return released(item, GameplayResult.reconciliation(
                        "Soul Bond operation identity conflicts with durable evidence"));
            }
            if (entry.phase() == OperationJournal.Phase.COMMITTED) {
                return released(item, GameplayResult.denied("Soul Bond entitlement already consumed"));
            }
            if (entry.phase() == OperationJournal.Phase.MATERIAL_CONSUMED) {
                return recover(entry).thenCompose(result -> released(item, result));
            }
            if (entry.phase() != OperationJournal.Phase.PREPARED) {
                return released(item, GameplayResult.reconciliation(
                        "Soul Bond operation requires operator reconciliation"));
            }
        }

        SoulBondLedger.Reservation reservation = ledger.reserve(playerUuid, operationId);
        if (reservation == SoulBondLedger.Reservation.CONFLICT) {
            return released(item, GameplayResult.denied("Soul Bond already claimed or reserved"));
        }
        if (reservation == SoulBondLedger.Reservation.QUARANTINED) {
            return released(item, new GameplayResult(GameplayResult.Status.QUARANTINED,
                    "Soul Bond entitlement is quarantined"));
        }
        if (reservation == SoulBondLedger.Reservation.UNAVAILABLE) {
            return released(item, GameplayResult.unavailable("Soul Bond entitlement store is unavailable"));
        }

        OperationJournal.Decision begun = prior.isPresent()
                ? OperationJournal.Decision.ALREADY_APPLIED
                : journal.begin(new OperationJournal.Descriptor(
                operationId,
                operationId,
                OperationJournal.Kind.SOUL_BOND,
                playerUuid,
                intent(destination),
                item.sourceEvidence(),
                item.quantity(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                java.util.OptionalLong.empty(),
                java.util.OptionalLong.empty()));
        if (begun == OperationJournal.Decision.CONFLICT || begun == OperationJournal.Decision.QUARANTINED) {
            ledger.reconcile(playerUuid, operationId, Optional.empty());
            return released(item, GameplayResult.reconciliation("Soul Bond journal conflict requires reconciliation"));
        }
        if (begun == OperationJournal.Decision.UNAVAILABLE) {
            ledger.reconcile(playerUuid, operationId, Optional.empty());
            return released(item, GameplayResult.reconciliation("Soul Bond journal write failed"));
        }

        final String world = ownershipWorldName;
        final PopulationAdmissionLocation activationDestination = destination;
        return tamework.provisionDormantMiniwyvern(playerUuid, operationId, world)
                .handle((result, failure) -> failure == null ? result : null)
                .thenCompose(result -> result == null
                        ? keepPending(playerUuid, operationId, Optional.empty(), item,
                        "Tamework provisioning result is unknown")
                        : afterProvision(playerUuid, operationId, world, activationDestination, item, result));
    }

    private CompletionStage<GameplayResult> afterProvision(UUID playerUuid,
                                                            String operationId,
                                                            String ownershipWorldName,
                                                            PopulationAdmissionLocation destination,
                                                            ConsumableReservation item,
                                                            CompanionProvisioningResult result) {
        if (!result.accepted() || result.profileId() == null) {
            return released(item, result.status() == CompanionProvisioningResult.Status.DENIED
                    ? GameplayResult.denied(result.reason())
                    : GameplayResult.retryable(result.reason()));
        }
        UUID profileId;
        try {
            profileId = UUID.fromString(result.profileId());
        } catch (IllegalArgumentException invalidProfileId) {
            return keepPending(playerUuid, operationId, Optional.empty(), item,
                    "Tamework returned a non-UUID profile identity");
        }

        SoulBondLedger.Reservation linked = ledger.complete(
                playerUuid, operationId, profileId, Math.max(0L, clock.getAsLong()));
        if (linked != SoulBondLedger.Reservation.APPLIED
                && linked != SoulBondLedger.Reservation.ALREADY_APPLIED) {
            return keepPending(playerUuid, operationId, Optional.of(profileId), item,
                    "Miniwyvern exists but HyDragon could not durably link it");
        }
        return item.consume().thenCompose(consumed -> {
            if (consumed != ConsumableReservation.Disposition.APPLIED
                    && consumed != ConsumableReservation.Disposition.ALREADY_APPLIED) {
                ledger.reconcile(playerUuid, operationId, Optional.of(profileId));
                return CompletableFuture.completedFuture(GameplayResult.reconciliation(
                        "Miniwyvern linked; Soul Bond consumption requires reconciliation"));
            }
            OperationJournal.Decision material = journal.transition(
                    operationId, OperationJournal.Phase.PREPARED,
                    OperationJournal.Phase.MATERIAL_CONSUMED,
                    new OperationJournal.Update(
                            Optional.of(result.operationId().toString()),
                            Optional.of(profileId.toString()),
                            java.util.OptionalLong.of(result.profileRevision()),
                            Optional.empty()));
            if (material == OperationJournal.Decision.CONFLICT || material == OperationJournal.Decision.UNAVAILABLE) {
                ledger.reconcile(playerUuid, operationId, Optional.of(profileId));
                return CompletableFuture.completedFuture(GameplayResult.reconciliation(
                        "Soul Bond consumed; journal closure requires reconciliation"));
            }
            OperationJournal.Entry recoverable = journal.find(operationId).orElse(null);
            return recoverable == null
                    ? CompletableFuture.completedFuture(GameplayResult.reconciliation(
                    "Soul Bond consumed; activation journal is unavailable"))
                    : recover(recoverable);
        });
    }

    /** Restarts a consumed Soul Bond's dormant-to-active transition without the consumed item receipt. */
    public CompletionStage<GameplayResult> recover(OperationJournal.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.kind() != OperationJournal.Kind.SOUL_BOND) {
            return CompletableFuture.completedFuture(GameplayResult.denied("not a Soul Bond operation"));
        }
        if (entry.phase() == OperationJournal.Phase.COMMITTED) {
            return CompletableFuture.completedFuture(
                    new GameplayResult(GameplayResult.Status.ALREADY_APPLIED, "Soul Bond claimed"));
        }
        if (entry.phase() != OperationJournal.Phase.MATERIAL_CONSUMED
                || entry.descriptor().profileId().isEmpty()
                || entry.descriptor().profileRevision().isEmpty()) {
            return CompletableFuture.completedFuture(GameplayResult.reconciliation(
                    "Soul Bond activation is not recoverable from this journal phase"));
        }
        SoulBondIntent intent = decodeIntent(entry.descriptor().intentId());
        if (intent == null) {
            return CompletableFuture.completedFuture(GameplayResult.reconciliation(
                    "Soul Bond activation destination is invalid"));
        }
        UUID profileId;
        try {
            profileId = UUID.fromString(entry.descriptor().profileId().orElseThrow());
        } catch (IllegalArgumentException failure) {
            return CompletableFuture.completedFuture(GameplayResult.reconciliation(
                    "Soul Bond activation profile is invalid"));
        }
        return activate(
                entry.descriptor().ownerUuid(),
                entry.operationId(),
                profileId,
                entry.descriptor().profileRevision().getAsLong(),
                intent.worldName(),
                new PopulationAdmissionLocation(intent.worldName(), intent.chunkX(), intent.chunkZ()))
                .thenApply(activation -> {
                    if (!activation.active()) return activation.result();
                    OperationJournal.Decision closed = journal.transition(
                            entry.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                            OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
                    return closed == OperationJournal.Decision.APPLIED
                            || closed == OperationJournal.Decision.ALREADY_APPLIED
                            ? activation.result()
                            : GameplayResult.reconciliation(
                            "Miniwyvern activated; final Soul Bond journal closure is pending");
                });
    }

    private CompletionStage<Activation> activate(
            UUID playerUuid,
            String operationId,
            UUID profileId,
            long expectedProfileRevision,
            String ownershipWorldName,
            PopulationAdmissionLocation destination) {
        return tamework.activateDormantMiniwyvern(
                        playerUuid,
                        operationId + ":activate",
                        profileId.toString(),
                        expectedProfileRevision,
                        ownershipWorldName,
                        destination.chunkX(),
                        destination.chunkZ())
                .handle((result, failure) -> {
                    if (failure != null || result == null) {
                        return new Activation(false, GameplayResult.reconciliation(
                                "Soul Bond claimed; Miniwyvern activation is pending recovery"));
                    }
                    boolean active = result.accepted()
                            && result.lifecycle() == PopulationCompanionLifecycle.ACTIVE
                            && result.projectionStatus() == CompanionProvisioningProjectionStatus.ACTIVE;
                    return new Activation(active, active
                            ? GameplayResult.applied("Soul Bond claimed and Miniwyvern activated")
                            : GameplayResult.reconciliation(
                            "Soul Bond claimed; Miniwyvern activation is pending recovery"));
                });
    }

    private static String intent(PopulationAdmissionLocation destination) {
        String world = Base64.getUrlEncoder().withoutPadding().encodeToString(
                destination.worldName().getBytes(StandardCharsets.UTF_8));
        return "soul_bond:" + world + ':' + destination.chunkX() + ':' + destination.chunkZ();
    }

    private static SoulBondIntent decodeIntent(String value) {
        try {
            String[] parts = value.split(":", -1);
            if (parts.length != 4 || !"soul_bond".equals(parts[0])) return null;
            String world = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            if (world.isBlank()) return null;
            return new SoulBondIntent(world, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private CompletionStage<GameplayResult> keepPending(UUID playerUuid,
                                                         String operationId,
                                                         Optional<UUID> profileId,
                                                         ConsumableReservation item,
                                                         String reason) {
        ledger.reconcile(playerUuid, operationId, profileId);
        return released(item, GameplayResult.reconciliation(reason));
    }

    private static CompletionStage<GameplayResult> released(ConsumableReservation item, GameplayResult result) {
        return item.release().handle((ignored, failure) -> result);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private record Activation(boolean active, GameplayResult result) {
        private Activation {
            Objects.requireNonNull(result, "result");
        }
    }

    private record SoulBondIntent(String worldName, int chunkX, int chunkZ) {
    }

}
