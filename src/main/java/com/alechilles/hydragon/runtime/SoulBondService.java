package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.CompanionProvisioningOperationStatus;
import com.alechilles.alecstamework.api.CompanionProvisioningOperationView;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Crash-recoverable once-per-player Soul Bond provisioning saga. */
public final class SoulBondService {
    private final TameworkGameplayAdapter tamework;
    private final SoulBondLedger ledger;
    private final OperationJournal journal;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, InFlightClaim> inFlight = new ConcurrentHashMap<>();

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
        String operationId = item.operationId();
        String inFlightKey = playerUuid.toString();
        ClaimIdentity identity = new ClaimIdentity(
                playerUuid, ownershipWorldName, destination, item.sourceEvidence(), item.quantity());
        InFlightClaim proposed = new InFlightClaim(identity, new CompletableFuture<>());
        InFlightClaim existing = inFlight.putIfAbsent(inFlightKey, proposed);
        if (existing != null) {
            return existing.identity().equals(identity)
                    ? existing.result()
                    : released(item, GameplayResult.reconciliation(
                    "Soul Bond operation identity conflicts with an active claim"));
        }

        CompletionStage<GameplayResult> attempt;
        try {
            attempt = claimOnce(playerUuid, ownershipWorldName, destination, item);
        } catch (RuntimeException failure) {
            inFlight.remove(inFlightKey, proposed);
            proposed.result().completeExceptionally(failure);
            return proposed.result();
        }
        attempt.whenComplete((result, failure) -> {
            if (failure == null) proposed.result().complete(result);
            else proposed.result().completeExceptionally(failure);
            inFlight.remove(inFlightKey, proposed);
        });
        return proposed.result();
    }

    private CompletionStage<GameplayResult> claimOnce(UUID playerUuid,
                                                       String ownershipWorldName,
                                                       PopulationAdmissionLocation destination,
                                                       ConsumableReservation item) {
        TameworkGameplayAdapter.Readiness readiness = tamework.soulBondReadiness();
        if (!readiness.ready()) return released(item, GameplayResult.unavailable(readiness.reason()));
        if (!journal.available()) {
            return released(item, GameplayResult.unavailable("HyDragon durable operation journal is unavailable"));
        }

        String operationId = item.operationId();
        Optional<OperationJournal.Entry> prior = journal.find(operationId);
        Optional<SoulBondLedger.Claim> durableClaim = ledger.find(playerUuid);
        if (prior.isEmpty() && durableClaim.isPresent()) {
            SoulBondLedger.Claim claim = durableClaim.orElseThrow();
            Optional<OperationJournal.Entry> recoverable = journal.find(claim.operationId());
            if (recoverable.isEmpty()) {
                return released(item, GameplayResult.reconciliation(
                        "Soul Bond entitlement has no matching durable operation"));
            }
            return resumeExisting(recoverable.orElseThrow(), playerUuid, item);
        }
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
            if (entry.phase() == OperationJournal.Phase.CANCELED) {
                return released(item, GameplayResult.denied("Soul Bond provisioning was already denied"));
            }
            if (entry.phase() == OperationJournal.Phase.MATERIAL_CONSUMED) {
                return recover(entry).thenCompose(result -> released(item, result));
            }
            if (entry.phase() != OperationJournal.Phase.PREPARED) {
                return released(item, GameplayResult.reconciliation(
                        "Soul Bond operation requires operator reconciliation"));
            }
            return resumePrepared(entry, item);
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

        OperationJournal.Entry prepared = journal.find(operationId).orElse(null);
        return prepared == null
                ? released(item, GameplayResult.reconciliation("Soul Bond journal write is not readable"))
                : attemptProvision(prepared, item);
    }

    private CompletionStage<GameplayResult> resumeExisting(
            OperationJournal.Entry entry, UUID playerUuid, ConsumableReservation item) {
        if (entry.kind() != OperationJournal.Kind.SOUL_BOND
                || !entry.descriptor().ownerUuid().equals(playerUuid)) {
            return released(item, GameplayResult.reconciliation(
                    "Soul Bond entitlement conflicts with durable operation identity"));
        }
        return switch (entry.phase()) {
            case PREPARED -> resumePrepared(entry, item);
            case MATERIAL_CONSUMED -> recover(entry).thenCompose(result -> released(item, result));
            case COMMITTED -> released(item, GameplayResult.denied("Soul Bond entitlement already consumed"));
            case CANCELED -> released(item, GameplayResult.denied("Soul Bond provisioning was already denied"));
            case QUARANTINED, REFUND_DUE, REFUNDED -> released(item, GameplayResult.reconciliation(
                    "Soul Bond operation requires operator reconciliation"));
        };
    }

    private CompletionStage<GameplayResult> resumePrepared(
            OperationJournal.Entry entry, ConsumableReservation item) {
        return tamework.findMiniwyvernProvisioning(entry.operationId())
                .handle((found, failure) -> failure == null ? found : null)
                .thenCompose(found -> {
                    if (found != null && found.isPresent()) {
                        if (isNonterminal(found.orElseThrow().status())) {
                            return attemptProvision(entry, item);
                        }
                        return afterOperationView(entry, item, found.orElseThrow());
                    }
                    return attemptProvision(entry, item);
                });
    }

    private CompletionStage<GameplayResult> attemptProvision(
            OperationJournal.Entry entry, ConsumableReservation item) {
        SoulBondIntent destination = decodeIntent(entry.descriptor().intentId());
        if (destination == null) {
            return finish(item, quarantine(entry, "Soul Bond activation destination is invalid"));
        }
        return tamework.provisionDormantMiniwyvern(
                        entry.descriptor().ownerUuid(), entry.operationId(), destination.worldName())
                .handle((result, failure) -> failure == null ? result : null)
                .thenCompose(result -> {
                    AuthorityEvidence evidence = evidence(result, entry);
                    if (evidence != null) return acceptEvidence(entry, item, evidence);
                    if (result != null && result.status() == CompanionProvisioningResult.Status.DENIED
                            && matchesDeniedOrigin(result, entry.descriptor().ownerUuid(), entry.operationId())) {
                        return compensateDenied(entry, item,
                                Optional.ofNullable(result.operationId()).map(UUID::toString), result.reason());
                    }
                    String reason = result == null
                            ? "Tamework provisioning result is unknown"
                            : "Tamework provisioning result is noncanonical: " + result.reason();
                    return resolveAmbiguous(entry, item, reason);
                });
    }

    private CompletionStage<GameplayResult> resolveAmbiguous(
            OperationJournal.Entry entry, ConsumableReservation item, String reason) {
        return tamework.findMiniwyvernProvisioning(entry.operationId())
                .handle((found, failure) -> failure == null ? found : null)
                .thenCompose(found -> found == null || found.isEmpty()
                        ? finish(item, GameplayResult.retryable(reason))
                        : afterOperationView(entry, item, found.orElseThrow()));
    }

    private CompletionStage<GameplayResult> afterOperationView(
            OperationJournal.Entry entry,
            ConsumableReservation item,
            CompanionProvisioningOperationView view) {
        if (!matches(view, entry)) {
            return finish(item, quarantine(entry, "Tamework provisioning operation identity conflicts"));
        }
        AuthorityEvidence evidence = evidence(view);
        if (evidence != null) return acceptEvidence(entry, item, evidence);
        if (view.status() == CompanionProvisioningOperationStatus.TERMINAL_DENIED) {
            return compensateDenied(entry, item, Optional.of(view.operationId().toString()), view.reason());
        }
        if (view.status() == CompanionProvisioningOperationStatus.QUARANTINED
                || view.status() == CompanionProvisioningOperationStatus.CANCELED) {
            return finish(item, quarantine(entry, "Tamework provisioning is " + view.status().name().toLowerCase()));
        }
        return finish(item, GameplayResult.retryable(
                "Soul Bond provisioning remains " + view.status().name().toLowerCase()));
    }

    private CompletionStage<GameplayResult> acceptEvidence(
            OperationJournal.Entry entry, ConsumableReservation item, AuthorityEvidence evidence) {
        UUID playerUuid = entry.descriptor().ownerUuid();
        String operationId = entry.operationId();
        SoulBondLedger.Reservation linked = ledger.complete(
                playerUuid, operationId, evidence.profileId(), Math.max(0L, clock.getAsLong()));
        if (linked != SoulBondLedger.Reservation.APPLIED
                && linked != SoulBondLedger.Reservation.ALREADY_APPLIED) {
            ledger.reconcile(playerUuid, operationId, Optional.of(evidence.profileId()));
            return finish(item, GameplayResult.reconciliation(
                    "Miniwyvern exists but HyDragon could not durably link it"));
        }
        if (item == null) {
            return CompletableFuture.completedFuture(GameplayResult.reconciliation(
                    "Miniwyvern linked; Soul Bond item consumption awaits a safe retry"));
        }
        return item.consume().thenCompose(consumed -> {
            if (consumed != ConsumableReservation.Disposition.APPLIED
                    && consumed != ConsumableReservation.Disposition.ALREADY_APPLIED) {
                ledger.reconcile(playerUuid, operationId, Optional.of(evidence.profileId()));
                return CompletableFuture.completedFuture(GameplayResult.reconciliation(
                        "Miniwyvern linked; Soul Bond consumption requires reconciliation"));
            }
            OperationJournal.Decision material = journal.transition(
                    operationId, OperationJournal.Phase.PREPARED,
                    OperationJournal.Phase.MATERIAL_CONSUMED,
                    new OperationJournal.Update(
                            Optional.of(evidence.authorityOperationId().toString()),
                            Optional.of(evidence.profileId().toString()),
                            java.util.OptionalLong.of(evidence.profileRevision()),
                            Optional.empty()));
            if (material == OperationJournal.Decision.CONFLICT || material == OperationJournal.Decision.UNAVAILABLE) {
                ledger.reconcile(playerUuid, operationId, Optional.of(evidence.profileId()));
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

    /** Restarts provisioning or dormant-to-active transition without relying on a process-local receipt. */
    public CompletionStage<GameplayResult> recover(OperationJournal.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.kind() != OperationJournal.Kind.SOUL_BOND) {
            return CompletableFuture.completedFuture(GameplayResult.denied("not a Soul Bond operation"));
        }
        if (entry.phase() == OperationJournal.Phase.COMMITTED) {
            return CompletableFuture.completedFuture(
                    new GameplayResult(GameplayResult.Status.ALREADY_APPLIED, "Soul Bond claimed"));
        }
        if (entry.phase() == OperationJournal.Phase.PREPARED) {
            return resumePrepared(entry, null);
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

    private CompletionStage<GameplayResult> compensateDenied(
            OperationJournal.Entry entry,
            ConsumableReservation item,
            Optional<String> authorityOperationId,
            String reason) {
        SoulBondLedger.Reservation compensated = ledger.compensateDenied(
                entry.descriptor().ownerUuid(), entry.operationId(), authorityOperationId,
                Math.max(0L, clock.getAsLong()));
        if (compensated == SoulBondLedger.Reservation.APPLIED
                || compensated == SoulBondLedger.Reservation.ALREADY_APPLIED) {
            return finish(item, GameplayResult.denied(reason));
        }
        ledger.reconcile(entry.descriptor().ownerUuid(), entry.operationId(), Optional.empty());
        return finish(item, GameplayResult.reconciliation(
                "Tamework denied provisioning, but Soul Bond compensation is pending"));
    }

    private GameplayResult quarantine(OperationJournal.Entry entry, String reason) {
        journal.transition(entry.operationId(), OperationJournal.Phase.PREPARED,
                OperationJournal.Phase.QUARANTINED,
                new OperationJournal.Update(Optional.empty(), Optional.empty(),
                        java.util.OptionalLong.empty(), Optional.of(reason)));
        ledger.reconcile(entry.descriptor().ownerUuid(), entry.operationId(), Optional.empty());
        return new GameplayResult(GameplayResult.Status.QUARANTINED, reason);
    }

    private static AuthorityEvidence evidence(
            CompanionProvisioningResult result, OperationJournal.Entry entry) {
        if (result == null || !result.accepted()
                || !matches(result, entry)
                || !committedLifecycle(result.lifecycle(), result.projectionStatus())) {
            return null;
        }
        UUID profileId = parseUuid(result.profileId());
        if (profileId == null || result.operationId() == null || result.profileRevision() < 0L) return null;
        return new AuthorityEvidence(result.operationId(), profileId, result.profileRevision());
    }

    private static AuthorityEvidence evidence(CompanionProvisioningOperationView view) {
        boolean committed = view.status() == CompanionProvisioningOperationStatus.DORMANT_COMMITTED
                || view.status() == CompanionProvisioningOperationStatus.COMMITTED
                || view.status() == CompanionProvisioningOperationStatus.PARTIAL_DORMANT;
        UUID profileId = parseUuid(view.profileId());
        if (!committed || profileId == null || view.profileRevision() < 0L
                || !committedLifecycle(view.lifecycle(), view.projectionStatus())) {
            return null;
        }
        return new AuthorityEvidence(view.operationId(), profileId, view.profileRevision());
    }

    private static boolean committedLifecycle(
            PopulationCompanionLifecycle lifecycle,
            CompanionProvisioningProjectionStatus projectionStatus) {
        return (lifecycle == PopulationCompanionLifecycle.PROVISIONED_DORMANT
                && (projectionStatus == CompanionProvisioningProjectionStatus.NOT_REQUESTED
                || projectionStatus == CompanionProvisioningProjectionStatus.FAILED_RECOVERABLE))
                || (lifecycle == PopulationCompanionLifecycle.ACTIVE
                && projectionStatus == CompanionProvisioningProjectionStatus.ACTIVE);
    }

    private static boolean matches(CompanionProvisioningResult result, OperationJournal.Entry entry) {
        return TameworkGameplayAdapter.CALLER_NAMESPACE.equals(result.callerNamespace())
                && entry.operationId().equals(result.idempotencyKey())
                && entry.descriptor().ownerUuid().equals(result.ownerUuid())
                && TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE.equals(result.roleId());
    }

    private static boolean matches(CompanionProvisioningOperationView view, OperationJournal.Entry entry) {
        return TameworkGameplayAdapter.CALLER_NAMESPACE.equals(view.callerNamespace())
                && entry.operationId().equals(view.idempotencyKey())
                && entry.descriptor().ownerUuid().equals(view.ownerUuid())
                && TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE.equals(view.roleId());
    }

    private static boolean isNonterminal(CompanionProvisioningOperationStatus status) {
        return status == CompanionProvisioningOperationStatus.PREPARING
                || status == CompanionProvisioningOperationStatus.PREPARED
                || status == CompanionProvisioningOperationStatus.APPLYING
                || status == CompanionProvisioningOperationStatus.PROJECTING;
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            return null;
        }
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

    private static CompletionStage<GameplayResult> finish(
            ConsumableReservation item, GameplayResult result) {
        return item == null ? CompletableFuture.completedFuture(result) : released(item, result);
    }

    private static CompletionStage<GameplayResult> released(ConsumableReservation item, GameplayResult result) {
        return item.release().handle((ignored, failure) -> result);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static boolean matchesDeniedOrigin(
            CompanionProvisioningResult result, UUID playerUuid, String operationId) {
        return result.operationId() != null
                && TameworkGameplayAdapter.CALLER_NAMESPACE.equals(result.callerNamespace())
                && operationId.equals(result.idempotencyKey())
                && playerUuid.equals(result.ownerUuid())
                && TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE.equals(result.roleId());
    }

    private record Activation(boolean active, GameplayResult result) {
        private Activation {
            Objects.requireNonNull(result, "result");
        }
    }

    private record AuthorityEvidence(
            UUID authorityOperationId, UUID profileId, long profileRevision) {
        private AuthorityEvidence {
            Objects.requireNonNull(authorityOperationId, "authorityOperationId");
            Objects.requireNonNull(profileId, "profileId");
            if (profileRevision < 0L) throw new IllegalArgumentException("profileRevision must not be negative");
        }
    }

    private record SoulBondIntent(String worldName, int chunkX, int chunkZ) {
    }

    private record ClaimIdentity(UUID playerUuid,
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

    private record InFlightClaim(ClaimIdentity identity, CompletableFuture<GameplayResult> result) {
        private InFlightClaim {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(result, "result");
        }
    }

}
