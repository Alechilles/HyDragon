package com.alechilles.hydragon.runtime;

import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionCodec;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionDocument;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionStore;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Crash-recoverable elemental attunement of a player's bonded Miniwyvern profile. */
public final class MiniwyvernAttunementService {
    private static final int MAX_REBASE_ATTEMPTS = 4;
    private static final Map<String, String> ESSENCE_ITEMS = Map.of(
            "lightning", "Draconic_Essence_Lightning",
            "wind", "Draconic_Essence_Wind",
            "ice", "Draconic_Essence_Ice",
            "fire", "Draconic_Essence_Fire",
            "water", "Draconic_Essence_Water",
            "nature", "Draconic_Essence_Nature",
            "void", "Draconic_Essence_Void");

    private final TameworkGameplayAdapter tamework;
    private final BondedMiniwyvernExtensionStore extensions;
    private final SoulBondLedger soulBonds;
    private final OperationJournal journal;

    public MiniwyvernAttunementService(
            TameworkGameplayAdapter tamework,
            BondedMiniwyvernExtensionStore extensions,
            SoulBondLedger soulBonds,
            OperationJournal journal) {
        this.tamework = Objects.requireNonNull(tamework, "tamework");
        this.extensions = Objects.requireNonNull(extensions, "extensions");
        this.soulBonds = Objects.requireNonNull(soulBonds, "soulBonds");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /**
     * Source-compatible bridge for pre-lease composition. The local profile
     * projection is intentionally ignored; bonded extension data is now the
     * sole Miniwyvern archetype authority.
     */
    @Deprecated
    public MiniwyvernAttunementService(
            TameworkGameplayAdapter tamework,
            SoulBondLedger soulBonds,
            OperationJournal journal,
            ProfileProjection ignoredProjection) {
        this(tamework,
                new BondedMiniwyvernExtensionStore(
                        tamework, new BondedMiniwyvernExtensionCodec()),
                soulBonds,
                journal);
        Objects.requireNonNull(ignoredProjection, "ignoredProjection");
    }

    public CompletionStage<GameplayResult> attune(
            UUID playerUuid,
            String archetypeId,
            ConsumableReservation essence) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        String archetype = normalize(archetypeId);
        Objects.requireNonNull(essence, "essence");
        GameplayResult inputFailure = validateInput(archetype, essence);
        if (inputFailure != null) return released(essence, inputFailure);
        TameworkGameplayAdapter.Readiness readiness = tamework.attunementReadiness();
        if (!readiness.ready()) return released(essence, GameplayResult.unavailable(readiness.reason()));
        if (!journal.available()) {
            return released(essence, GameplayResult.unavailable(
                    "HyDragon attunement journal is unavailable"));
        }

        UUID profileId = claimedProfile(playerUuid);
        if (profileId == null) return claimFailure(playerUuid, essence);
        Optional<OperationJournal.Entry> existing = journal.find(essence.operationId());
        if (existing.isPresent()) {
            OperationJournal.Entry entry = existing.orElseThrow();
            if (!matches(entry, playerUuid, profileId.toString(), archetype, essence)) {
                return released(essence, GameplayResult.reconciliation(
                        "attunement operation identity conflicts with durable evidence"));
            }
            return resume(entry, playerUuid, profileId, archetype, essence);
        }
        return load(playerUuid, profileId).thenCompose(read -> begin(
                playerUuid, profileId, archetype, essence, read));
    }

    private GameplayResult validateInput(
            String archetype,
            ConsumableReservation essence) {
        String expectedItem = ESSENCE_ITEMS.get(archetype);
        if (expectedItem == null) return GameplayResult.denied("unsupported archetype");
        if (!expectedItem.equals(essence.sourceEvidence().itemId())
                || essence.quantity() != 1) {
            return GameplayResult.denied("essence does not match target archetype");
        }
        return null;
    }

    private UUID claimedProfile(UUID playerUuid) {
        Optional<SoulBondLedger.Claim> found = soulBonds.find(playerUuid);
        if (found.isEmpty()) return null;
        SoulBondLedger.Claim claim = found.orElseThrow();
        if (claim.state() != SoulBondLedger.Claim.State.CLAIMED
                || claim.profileId().isEmpty()) {
            return null;
        }
        return claim.profileId().orElseThrow();
    }

    private CompletionStage<GameplayResult> claimFailure(
            UUID playerUuid,
            ConsumableReservation essence) {
        Optional<SoulBondLedger.Claim> claim = soulBonds.find(playerUuid);
        return released(essence, claim.isEmpty()
                ? GameplayResult.denied("Soul Bond Miniwyvern is not claimed")
                : GameplayResult.reconciliation(
                "Soul Bond profile identity requires reconciliation"));
    }

    private CompletionStage<GameplayResult> begin(
            UUID ownerUuid,
            UUID profileId,
            String archetype,
            ConsumableReservation essence,
            BondedMiniwyvernExtensionStore.ReadResult read) {
        if (!loaded(read)) return released(essence, invalidRead(read));
        BondedMiniwyvernExtensionDocument document = read.document();
        if (document.archetypeId().equals(archetype)) {
            return released(essence, GameplayResult.denied(
                    "Miniwyvern already has this archetype"));
        }
        OperationJournal.Decision begun = journal.begin(descriptor(
                ownerUuid, profileId, archetype, essence, read.revision()));
        if (begun != OperationJournal.Decision.APPLIED
                && begun != OperationJournal.Decision.ALREADY_APPLIED) {
            return released(essence, begun == OperationJournal.Decision.QUARANTINED
                    ? quarantined("attunement operation is quarantined")
                    : GameplayResult.reconciliation(
                    "attunement journal reservation failed"));
        }
        return apply(ownerUuid, profileId, archetype, essence,
                read, MAX_REBASE_ATTEMPTS);
    }

    private OperationJournal.Descriptor descriptor(
            UUID ownerUuid,
            UUID profileId,
            String archetype,
            ConsumableReservation essence,
            long revision) {
        return new OperationJournal.Descriptor(
                essence.operationId(), essence.operationId(),
                OperationJournal.Kind.MINIWYVERN_ATTUNEMENT,
                ownerUuid, intent(archetype), essence.sourceEvidence(), essence.quantity(),
                Optional.empty(), Optional.of(profileId.toString()),
                OptionalLong.of(revision));
    }

    private CompletionStage<GameplayResult> resume(
            OperationJournal.Entry entry,
            UUID ownerUuid,
            UUID profileId,
            String archetype,
            ConsumableReservation essence) {
        return switch (entry.phase()) {
            case COMMITTED -> released(essence,
                    new GameplayResult(GameplayResult.Status.ALREADY_APPLIED,
                            "Miniwyvern attuned"));
            case MATERIAL_CONSUMED -> closeConsumed(essence);
            case PREPARED -> recoverPrepared(
                    ownerUuid, profileId, archetype, essence);
            case REFUND_DUE -> releaseDenied(essence, "attunement was denied");
            case REFUNDED, CANCELED -> released(essence,
                    GameplayResult.denied("attunement was canceled"));
            case QUARANTINED -> released(essence,
                    quarantined("attunement operation is quarantined"));
        };
    }

    private CompletionStage<GameplayResult> recoverPrepared(
            UUID ownerUuid,
            UUID profileId,
            String archetype,
            ConsumableReservation essence) {
        return load(ownerUuid, profileId).thenCompose(read -> {
            if (!loaded(read)) {
                return completed(GameplayResult.reconciliation(
                        "attunement authority is unavailable; consumption remains pending"));
            }
            if (read.document().hasAttunementEvidence(
                    essence.operationId(), archetype)) {
                return consumeAndCommit(profileId, essence,
                        evidenceOperationId(essence.operationId()));
            }
            if (read.document().archetypeId().equals(archetype)) {
                return releaseDenied(essence,
                        "Miniwyvern was attuned by a different operation");
            }
            return apply(ownerUuid, profileId, archetype, essence,
                    read, MAX_REBASE_ATTEMPTS);
        });
    }

    private CompletionStage<GameplayResult> apply(
            UUID ownerUuid,
            UUID profileId,
            String archetype,
            ConsumableReservation essence,
            BondedMiniwyvernExtensionStore.ReadResult read,
            int attemptsRemaining) {
        BondedMiniwyvernExtensionDocument desired;
        try {
            desired = read.document().attune(archetype, essence.operationId());
        } catch (RuntimeException conflict) {
            return releaseDenied(essence, conflict.getMessage());
        }
        String authorityOperationId = extensionOperationId(
                essence.operationId(), read.revision());
        return extensions.compareAndSet(
                        ownerUuid, profileId.toString(), authorityOperationId,
                        read.revision(), desired)
                .thenCompose(write -> resolveWrite(
                        ownerUuid, profileId, archetype, essence,
                        authorityOperationId, attemptsRemaining, write));
    }

    private CompletionStage<GameplayResult> resolveWrite(
            UUID ownerUuid,
            UUID profileId,
            String archetype,
            ConsumableReservation essence,
            String authorityOperationId,
            int attemptsRemaining,
            BondedMiniwyvernExtensionStore.WriteResult write) {
        if (write.status() == BondedMiniwyvernExtensionStore.WriteStatus.APPLIED) {
            if (!write.document().hasAttunementEvidence(
                    essence.operationId(), archetype)) {
                return completed(quarantine(
                        essence.operationId(), "inconsistent attunement proof"));
            }
            return consumeAndCommit(profileId, essence, authorityOperationId);
        }
        if (write.status() == BondedMiniwyvernExtensionStore.WriteStatus.CONFLICT
                && attemptsRemaining > 0) {
            return rebase(ownerUuid, profileId, archetype, essence,
                    attemptsRemaining - 1);
        }
        if (write.status() == BondedMiniwyvernExtensionStore.WriteStatus.UNAVAILABLE) {
            return verifyIndeterminate(ownerUuid, profileId, archetype, essence);
        }
        if (write.status() == BondedMiniwyvernExtensionStore.WriteStatus.INVALID) {
            return completed(quarantine(
                    essence.operationId(), "invalid attunement authority response"));
        }
        return write.status() == BondedMiniwyvernExtensionStore.WriteStatus.REJECTED
                ? releaseDenied(essence, write.reason())
                : completed(GameplayResult.reconciliation(
                "attunement changed concurrently; retry remains pending"));
    }

    private CompletionStage<GameplayResult> rebase(
            UUID ownerUuid,
            UUID profileId,
            String archetype,
            ConsumableReservation essence,
            int attemptsRemaining) {
        return load(ownerUuid, profileId).thenCompose(read -> {
            if (!loaded(read)) {
                return completed(GameplayResult.reconciliation(
                        "attunement rebase evidence is unavailable"));
            }
            if (read.document().hasAttunementEvidence(
                    essence.operationId(), archetype)) {
                return consumeAndCommit(profileId, essence,
                        evidenceOperationId(essence.operationId()));
            }
            if (read.document().archetypeId().equals(archetype)) {
                return releaseDenied(essence,
                        "Miniwyvern was attuned by a different operation");
            }
            return apply(ownerUuid, profileId, archetype, essence,
                    read, attemptsRemaining);
        });
    }

    private CompletionStage<GameplayResult> verifyIndeterminate(
            UUID ownerUuid,
            UUID profileId,
            String archetype,
            ConsumableReservation essence) {
        return load(ownerUuid, profileId).thenCompose(read -> loaded(read)
                && read.document().hasAttunementEvidence(
                essence.operationId(), archetype)
                ? consumeAndCommit(profileId, essence,
                evidenceOperationId(essence.operationId()))
                : completed(GameplayResult.reconciliation(
                "attunement outcome is indeterminate; consumption remains pending")));
    }

    private CompletionStage<GameplayResult> consumeAndCommit(
            UUID profileId,
            ConsumableReservation essence,
            String authorityOperationId) {
        CompletionStage<ConsumableReservation.Disposition> consumption;
        try {
            consumption = essence.consume();
        } catch (RuntimeException failure) {
            return completed(GameplayResult.reconciliation(
                    "attunement committed; essence consumption requires reconciliation"));
        }
        return consumption.handle((consumed, failure) -> {
            if (failure != null || !applied(consumed)) {
                return GameplayResult.reconciliation(
                        "attunement committed; essence consumption requires reconciliation");
            }
            OperationJournal.Decision material = journal.transition(
                    essence.operationId(), OperationJournal.Phase.PREPARED,
                    OperationJournal.Phase.MATERIAL_CONSUMED,
                    new OperationJournal.Update(
                            Optional.of(authorityOperationId),
                            Optional.of(profileId.toString()),
                            OptionalLong.empty(), Optional.empty()));
            if (!applied(material)) {
                return GameplayResult.reconciliation(
                        "essence consumed; attunement journal requires reconciliation");
            }
            OperationJournal.Decision closed = journal.transition(
                    essence.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                    OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
            return applied(closed)
                    ? GameplayResult.applied("Miniwyvern attuned")
                    : GameplayResult.reconciliation(
                    "attunement succeeded; journal closure is pending");
        });
    }

    private CompletionStage<GameplayResult> closeConsumed(
            ConsumableReservation essence) {
        OperationJournal.Decision closed = journal.transition(
                essence.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
        return released(essence, applied(closed)
                ? new GameplayResult(GameplayResult.Status.ALREADY_APPLIED,
                "Miniwyvern attuned")
                : GameplayResult.reconciliation(
                "attunement journal closure is pending"));
    }

    private CompletionStage<BondedMiniwyvernExtensionStore.ReadResult> load(
            UUID ownerUuid,
            UUID profileId) {
        try {
            return extensions.load(ownerUuid, profileId.toString());
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletionStage<GameplayResult> releaseDenied(
            ConsumableReservation essence,
            String reason) {
        return essence.release().handle((released, failure) ->
                failure == null && applied(released)
                        ? GameplayResult.denied(reason)
                        : GameplayResult.reconciliation(
                        "attunement denied; essence release remains pending"));
    }

    private GameplayResult invalidRead(
            BondedMiniwyvernExtensionStore.ReadResult read) {
        String detail = read == null ? "empty response" : read.reason();
        return GameplayResult.reconciliation(
                "Miniwyvern bonded extension is unavailable: " + detail);
    }

    private GameplayResult quarantine(String operationId, String reason) {
        journal.transition(operationId, OperationJournal.Phase.PREPARED,
                OperationJournal.Phase.QUARANTINED,
                new OperationJournal.Update(
                        Optional.empty(), Optional.empty(), OptionalLong.empty(),
                        Optional.of(reason)));
        return quarantined(reason);
    }

    private static boolean matches(
            OperationJournal.Entry entry,
            UUID ownerUuid,
            String profileId,
            String archetype,
            ConsumableReservation essence) {
        OperationJournal.Descriptor descriptor = entry.descriptor();
        return entry.kind() == OperationJournal.Kind.MINIWYVERN_ATTUNEMENT
                && descriptor.ownerUuid().equals(ownerUuid)
                && descriptor.intentId().equals(intent(archetype))
                && descriptor.profileId().equals(Optional.of(profileId))
                && descriptor.profileRevision().isPresent()
                && descriptor.source().itemFingerprint().equals(
                essence.sourceEvidence().itemFingerprint())
                && descriptor.source().itemId().equals(
                essence.sourceEvidence().itemId())
                && descriptor.materialQuantity() == essence.quantity();
    }

    private static boolean loaded(BondedMiniwyvernExtensionStore.ReadResult read) {
        return read != null
                && read.status() == BondedMiniwyvernExtensionStore.ReadStatus.LOADED;
    }

    private static boolean applied(ConsumableReservation.Disposition disposition) {
        return disposition == ConsumableReservation.Disposition.APPLIED
                || disposition == ConsumableReservation.Disposition.ALREADY_APPLIED;
    }

    private static boolean applied(OperationJournal.Decision decision) {
        return decision == OperationJournal.Decision.APPLIED
                || decision == OperationJournal.Decision.ALREADY_APPLIED;
    }

    private static String extensionOperationId(String operationId, long revision) {
        return operationId + ":extension:" + revision;
    }

    private static String evidenceOperationId(String operationId) {
        return operationId + ":extension:evidence";
    }

    private static String intent(String archetype) {
        return "attune:" + archetype;
    }

    private static String normalize(String archetypeId) {
        String normalized = Objects.requireNonNull(archetypeId, "archetypeId")
                .trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("archetypeId is required");
        return normalized;
    }

    private static GameplayResult quarantined(String reason) {
        return new GameplayResult(GameplayResult.Status.QUARANTINED, reason);
    }

    private static CompletionStage<GameplayResult> released(
            ConsumableReservation essence,
            GameplayResult result) {
        return essence.release().handle((ignored, failure) -> result);
    }

    private static CompletionStage<GameplayResult> completed(GameplayResult result) {
        return CompletableFuture.completedFuture(result);
    }

    /** Legacy local projection contract retained only until old local state is retired. */
    @Deprecated
    @FunctionalInterface
    public interface ProfileProjection {
        Decision synchronize(UUID profileId, String archetypeId, String operationId);

        enum Decision { APPLIED, ALREADY_APPLIED, CONFLICT, QUARANTINED, UNAVAILABLE }
    }
}
