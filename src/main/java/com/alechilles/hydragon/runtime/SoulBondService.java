package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.CompanionProvisioningLinkResult;
import com.alechilles.alecstamework.api.CommandTimedSummoningResult;
import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Crash-recoverable once-per-player Wyvern Egg provisioning and Dragon Horn linking saga. */
public final class SoulBondService {
    public static final String WYVERN_EGG_ITEM_ID = "Wyvern_Egg";

    private final TameworkGameplayAdapter tamework;
    private final SoulBondLedger ledger;
    private final OperationJournal journal;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, InFlightClaim> inFlight = new ConcurrentHashMap<>();

    public SoulBondService(
            TameworkGameplayAdapter tamework,
            SoulBondLedger ledger,
            OperationJournal journal,
            LongSupplier clock) {
        this.tamework = Objects.requireNonNull(tamework, "tamework");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<GameplayResult> claim(
            UUID playerUuid,
            String ownershipWorldName,
            ConsumableReservation item) {
        return claim(playerUuid, ownershipWorldName,
                new PopulationAdmissionLocation(ownershipWorldName, 0, 0), item);
    }

    public CompletionStage<GameplayResult> claim(
            UUID playerUuid,
            String ownershipWorldName,
            PopulationAdmissionLocation destination,
            ConsumableReservation item) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        ownershipWorldName = requiredText(ownershipWorldName, "ownershipWorldName");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(item, "item");
        if (!ownershipWorldName.equals(destination.worldName())) {
            throw new IllegalArgumentException("destination must be in the ownership world");
        }

        ClaimIdentity identity = new ClaimIdentity(
                playerUuid, ownershipWorldName, destination, item.sourceEvidence(), item.quantity());
        InFlightClaim proposed = new InFlightClaim(identity, new CompletableFuture<>());
        InFlightClaim existing = inFlight.putIfAbsent(playerUuid.toString(), proposed);
        if (existing != null) {
            return existing.identity().equals(identity)
                    ? existing.result()
                    : released(item, GameplayResult.reconciliation(
                    "Soul Bond operation identity conflicts with an active claim"));
        }

        CompletionStage<GameplayResult> attempt;
        try {
            attempt = claimOnce(playerUuid, destination, item);
        } catch (RuntimeException failure) {
            inFlight.remove(playerUuid.toString(), proposed);
            proposed.result().completeExceptionally(failure);
            return proposed.result();
        }
        attempt.whenComplete((result, failure) -> {
            if (failure == null) proposed.result().complete(result);
            else proposed.result().completeExceptionally(failure);
            inFlight.remove(playerUuid.toString(), proposed);
        });
        return proposed.result();
    }

    private CompletionStage<GameplayResult> claimOnce(
            UUID playerUuid,
            PopulationAdmissionLocation destination,
            ConsumableReservation item) {
        TameworkGameplayAdapter.Readiness readiness = tamework.soulBondReadiness();
        if (!readiness.ready()) return released(item, GameplayResult.unavailable(readiness.reason()));
        if (!journal.available()) {
            return released(item, GameplayResult.unavailable(
                    "HyDragon durable operation journal is unavailable"));
        }

        String operationId = item.operationId();
        Optional<OperationJournal.Entry> prior = journal.find(operationId);
        Optional<SoulBondLedger.Claim> existingClaim = ledger.find(playerUuid);
        if (prior.isEmpty() && existingClaim.isPresent()) {
            SoulBondLedger.Claim claim = existingClaim.orElseThrow();
            Optional<OperationJournal.Entry> recoverable = journal.find(claim.operationId());
            return recoverable.isEmpty()
                    ? released(item, GameplayResult.reconciliation(
                    "Soul Bond entitlement has no matching durable operation"))
                    : resumeExisting(recoverable.orElseThrow(), playerUuid, item);
        }
        if (prior.isPresent()) {
            return resumeExisting(prior.orElseThrow(), playerUuid, item);
        }

        SoulBondLedger.Reservation reservation = ledger.reserve(playerUuid, operationId);
        if (reservation == SoulBondLedger.Reservation.CONFLICT) {
            return released(item, GameplayResult.denied("Soul Bond already claimed or reserved"));
        }
        if (reservation == SoulBondLedger.Reservation.QUARANTINED) {
            return released(item, new GameplayResult(
                    GameplayResult.Status.QUARANTINED, "Soul Bond entitlement is quarantined"));
        }
        if (reservation == SoulBondLedger.Reservation.UNAVAILABLE) {
            return released(item, GameplayResult.unavailable("Soul Bond entitlement store is unavailable"));
        }

        OperationJournal.Decision begun = journal.begin(new OperationJournal.Descriptor(
                operationId,
                operationId,
                OperationJournal.Kind.SOUL_BOND,
                playerUuid,
                intent(destination),
                item.sourceEvidence(),
                item.quantity(),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty()));
        if (begun != OperationJournal.Decision.APPLIED
                && begun != OperationJournal.Decision.ALREADY_APPLIED) {
            ledger.reconcile(playerUuid, operationId, Optional.empty());
            return released(item, begun == OperationJournal.Decision.QUARANTINED
                    ? new GameplayResult(GameplayResult.Status.QUARANTINED,
                    "Soul Bond journal operation is quarantined")
                    : GameplayResult.reconciliation("Soul Bond journal reservation failed"));
        }
        OperationJournal.Entry prepared = journal.find(operationId).orElse(null);
        return prepared == null
                ? released(item, GameplayResult.reconciliation("Soul Bond journal write is not readable"))
                : consumePrepared(prepared, item);
    }

    private CompletionStage<GameplayResult> resumeExisting(
            OperationJournal.Entry entry,
            UUID playerUuid,
            ConsumableReservation item) {
        if (entry.kind() != OperationJournal.Kind.SOUL_BOND
                || !entry.descriptor().ownerUuid().equals(playerUuid)
                || (!entry.operationId().equals(item.operationId())
                && entry.phase() == OperationJournal.Phase.PREPARED)
                || (entry.operationId().equals(item.operationId())
                && !entry.descriptor().source().itemFingerprint()
                .equals(item.sourceEvidence().itemFingerprint()))) {
            return released(item, GameplayResult.reconciliation(
                    "Soul Bond operation conflicts with durable evidence"));
        }
        return switch (entry.phase()) {
            case PREPARED -> consumePrepared(entry, item);
            case MATERIAL_CONSUMED -> recover(entry).thenCompose(result -> released(item, result));
            case COMMITTED -> released(item, GameplayResult.denied(
                    "Soul Bond entitlement already consumed"));
            case CANCELED -> released(item, GameplayResult.denied(
                    "Soul Bond provisioning was denied without consuming this Egg"));
            case REFUND_DUE, REFUNDED -> released(item, GameplayResult.reconciliation(
                    "Soul Bond Egg recovery already exists"));
            case QUARANTINED -> released(item, new GameplayResult(
                    GameplayResult.Status.QUARANTINED,
                    "Soul Bond operation requires operator reconciliation"));
        };
    }

    private CompletionStage<GameplayResult> consumePrepared(
            OperationJournal.Entry entry,
            ConsumableReservation item) {
        return item.consume().handle((disposition, failure) -> failure == null ? disposition : null)
                .thenCompose(disposition -> {
                    if (disposition != ConsumableReservation.Disposition.APPLIED
                            && disposition != ConsumableReservation.Disposition.ALREADY_APPLIED) {
                        return released(item, GameplayResult.retryable(
                                "Wyvern Egg consumption could not be durably confirmed"));
                    }
                    OperationJournal.Decision consumed = journal.transition(
                            entry.operationId(), OperationJournal.Phase.PREPARED,
                            OperationJournal.Phase.MATERIAL_CONSUMED, OperationJournal.Update.EMPTY);
                    if (consumed != OperationJournal.Decision.APPLIED
                            && consumed != OperationJournal.Decision.ALREADY_APPLIED) {
                        ledger.reconcile(entry.descriptor().ownerUuid(), entry.operationId(), Optional.empty());
                        return CompletableFuture.completedFuture(GameplayResult.reconciliation(
                                "Wyvern Egg consumed; durable provisioning recovery is pending"));
                    }
                    OperationJournal.Entry recoverable = journal.find(entry.operationId()).orElse(null);
                    return recoverable == null
                            ? CompletableFuture.completedFuture(GameplayResult.reconciliation(
                            "Wyvern Egg consumed; provisioning journal is unavailable"))
                            : recover(recoverable);
                });
    }

    /** Replays the atomic profile-and-Horn operation after the Egg spend is durably known. */
    public CompletionStage<GameplayResult> recover(OperationJournal.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.kind() != OperationJournal.Kind.SOUL_BOND) {
            return completed(GameplayResult.denied("not a Soul Bond operation"));
        }
        if (entry.phase() == OperationJournal.Phase.COMMITTED) {
            return completed(new GameplayResult(
                    GameplayResult.Status.ALREADY_APPLIED, "Soul Bond claimed"));
        }
        if (entry.phase() == OperationJournal.Phase.PREPARED) {
            return completed(GameplayResult.retryable(
                    "Wyvern Egg consumption still requires its exact inventory reservation"));
        }
        if (entry.phase() != OperationJournal.Phase.MATERIAL_CONSUMED) {
            return completed(GameplayResult.reconciliation(
                    "Soul Bond operation is not recoverable from this phase"));
        }

        SoulBondIntent destination = decodeIntent(entry.descriptor().intentId());
        if (destination == null) {
            return completed(quarantine(entry, "Soul Bond projection destination is invalid"));
        }
        return tamework.provisionAndLinkMiniwyvern(
                        entry.descriptor().ownerUuid(), entry.operationId(), destination.worldName())
                .handle((result, failure) -> failure == null ? result : null)
                .thenCompose(result -> resolveProvisioning(entry, destination, result));
    }

    private CompletionStage<GameplayResult> resolveProvisioning(
            OperationJournal.Entry entry,
            SoulBondIntent destination,
            CompanionProvisioningLinkResult result) {
        if (result == null || result.status() == CompanionProvisioningLinkResult.Status.UNAVAILABLE) {
            return completed(GameplayResult.retryable(
                    result == null ? "Tamework provision-and-link result is unknown" : result.reason()));
        }
        if (result.status() == CompanionProvisioningLinkResult.Status.DENIED) {
            return completed(compensateConsumedDenial(entry, result));
        }
        if (result.status() == CompanionProvisioningLinkResult.Status.QUARANTINED) {
            return completed(quarantine(entry, result.reason()));
        }
        AuthorityEvidence evidence = evidence(result, entry);
        if (evidence == null) {
            return completed(quarantine(entry,
                    "Tamework provision-and-link returned noncanonical authority evidence"));
        }

        SoulBondLedger.Reservation linked = ledger.complete(
                entry.descriptor().ownerUuid(), entry.operationId(), evidence.profileId(),
                Math.max(0L, clock.getAsLong()));
        if (linked != SoulBondLedger.Reservation.APPLIED
                && linked != SoulBondLedger.Reservation.ALREADY_APPLIED) {
            ledger.reconcile(entry.descriptor().ownerUuid(), entry.operationId(),
                    Optional.of(evidence.profileId()));
            return completed(GameplayResult.reconciliation(
                    "Miniwyvern is in the Dragon Horn, but entitlement linkage needs recovery"));
        }

        return completed(finishCommittedClaim(entry, evidence, result.initialProjection()));
    }

    private GameplayResult finishCommittedClaim(
            OperationJournal.Entry entry,
            AuthorityEvidence evidence,
            CommandTimedSummoningResult summon) {
        OperationJournal.Decision committed = journal.transition(
                entry.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                OperationJournal.Phase.COMMITTED,
                new OperationJournal.Update(
                        Optional.of(evidence.authorityOperationId().toString()),
                        Optional.of(evidence.profileId().toString()),
                        OptionalLong.of(evidence.profileRevision()),
                        Optional.empty()));
        if (committed != OperationJournal.Decision.APPLIED
                && committed != OperationJournal.Decision.ALREADY_APPLIED) {
            return GameplayResult.reconciliation(
                    "Miniwyvern is in the Dragon Horn; claim closure remains pending");
        }
        return summon != null && summon.successful()
                ? GameplayResult.applied(
                "Soul Bond claimed and Miniwyvern summoned from the Dragon Horn")
                : GameplayResult.applied(
                "Soul Bond claimed; Miniwyvern is stored in the Dragon Horn");
    }

    private GameplayResult compensateConsumedDenial(
            OperationJournal.Entry entry,
            CompanionProvisioningLinkResult result) {
        SoulBondLedger.Reservation compensated = ledger.compensateDenied(
                entry.descriptor().ownerUuid(), entry.operationId(),
                Optional.ofNullable(result.provisioning().operationId()).map(UUID::toString),
                Math.max(0L, clock.getAsLong()));
        return compensated == SoulBondLedger.Reservation.APPLIED
                || compensated == SoulBondLedger.Reservation.ALREADY_APPLIED
                ? GameplayResult.reconciliation(
                "Miniwyvern provisioning was denied; one Wyvern Egg recovery claim is available")
                : GameplayResult.reconciliation(
                "Miniwyvern provisioning was denied; Egg recovery reconciliation is pending");
    }

    private GameplayResult quarantine(OperationJournal.Entry entry, String reason) {
        OperationJournal.Decision decision = journal.transition(
                entry.operationId(), entry.phase(), OperationJournal.Phase.QUARANTINED,
                new OperationJournal.Update(
                        Optional.empty(), Optional.empty(), OptionalLong.empty(), Optional.of(reason)));
        ledger.reconcile(entry.descriptor().ownerUuid(), entry.operationId(), Optional.empty());
        return decision == OperationJournal.Decision.APPLIED
                || decision == OperationJournal.Decision.ALREADY_APPLIED
                ? new GameplayResult(GameplayResult.Status.QUARANTINED, reason)
                : GameplayResult.reconciliation(reason);
    }

    private static AuthorityEvidence evidence(
            CompanionProvisioningLinkResult result,
            OperationJournal.Entry entry) {
        if (!result.accepted()
                || !TameworkGameplayAdapter.CALLER_NAMESPACE.equals(
                result.provisioning().callerNamespace())
                || !entry.operationId().equals(result.provisioning().idempotencyKey())
                || !entry.descriptor().ownerUuid().equals(result.provisioning().ownerUuid())
                || !TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE.equals(
                result.provisioning().roleId())
                || result.roster() == null
                || result.membership() == null) {
            return null;
        }
        UUID profileId = parseUuid(result.provisioning().profileId());
        if (profileId == null
                || result.provisioning().operationId() == null
                || result.provisioning().profileRevision() < 0) {
            return null;
        }
        return new AuthorityEvidence(
                result.provisioning().operationId(), profileId,
                result.provisioning().profileRevision());
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
            String world = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            if (world.isBlank()) return null;
            return new SoulBondIntent(
                    world, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }

    private static CompletionStage<GameplayResult> released(
            ConsumableReservation item,
            GameplayResult result) {
        return item.release().handle((ignored, failure) -> result);
    }

    private static CompletionStage<GameplayResult> completed(GameplayResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private record AuthorityEvidence(
            UUID authorityOperationId,
            UUID profileId,
            long profileRevision) {
        private AuthorityEvidence {
            Objects.requireNonNull(authorityOperationId, "authorityOperationId");
            Objects.requireNonNull(profileId, "profileId");
            if (profileRevision < 0) throw new IllegalArgumentException("profileRevision is negative");
        }
    }

    private record SoulBondIntent(String worldName, int chunkX, int chunkZ) {
    }

    private record ClaimIdentity(
            UUID playerUuid,
            String ownershipWorldName,
            PopulationAdmissionLocation destination,
            ConsumableReservation.SourceEvidence source,
            int quantity) {
        private ClaimIdentity {
            Objects.requireNonNull(playerUuid, "playerUuid");
            ownershipWorldName = requiredText(ownershipWorldName, "ownershipWorldName");
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(source, "source");
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private record InFlightClaim(
            ClaimIdentity identity,
            CompletableFuture<GameplayResult> result) {
        private InFlightClaim {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(result, "result");
        }
    }
}
