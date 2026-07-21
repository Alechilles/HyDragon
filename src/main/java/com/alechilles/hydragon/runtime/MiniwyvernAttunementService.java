package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.ProfileDataCompareAndSetRequest;
import com.alechilles.alecstamework.api.ProfileDataCompareAndSetResult;
import com.alechilles.alecstamework.api.ProfileDataEntryView;
import com.alechilles.alecstamework.api.ProfileDataOperationStatus;
import com.alechilles.alecstamework.api.ProfileDataOperationView;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Crash-recoverable elemental attunement of a player's canonical Soul Bond profile. */
public final class MiniwyvernAttunementService {
    public static final String NAMESPACE = "hydragon";
    public static final String KEY = "miniwyvern_attunement";
    private static final int PAYLOAD_SCHEMA = 1;
    private static final Gson GSON = new Gson();
    private static final Map<String, String> ESSENCE_ITEMS = Map.of(
            "lightning", "Draconic_Essence_Lightning",
            "wind", "Draconic_Essence_Wind",
            "ice", "Draconic_Essence_Ice",
            "fire", "Draconic_Essence_Fire",
            "water", "Draconic_Essence_Water",
            "nature", "Draconic_Essence_Nature",
            "void", "Draconic_Essence_Void");

    private final TameworkGameplayAdapter tamework;
    private final SoulBondLedger soulBonds;
    private final OperationJournal journal;
    private final ProfileProjection projection;

    public MiniwyvernAttunementService(
            TameworkGameplayAdapter tamework,
            SoulBondLedger soulBonds,
            OperationJournal journal,
            ProfileProjection projection) {
        this.tamework = Objects.requireNonNull(tamework, "tamework");
        this.soulBonds = Objects.requireNonNull(soulBonds, "soulBonds");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.projection = Objects.requireNonNull(projection, "projection");
    }

    public CompletionStage<GameplayResult> attune(
            UUID playerUuid,
            String archetypeId,
            ConsumableReservation essence) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        String archetype = normalize(archetypeId);
        Objects.requireNonNull(essence, "essence");
        String expectedItem = ESSENCE_ITEMS.get(archetype);
        if (expectedItem == null) {
            return released(essence, GameplayResult.denied("unsupported archetype"));
        }
        if (!expectedItem.equals(essence.sourceEvidence().itemId()) || essence.quantity() != 1) {
            return released(essence, GameplayResult.denied("essence does not match target archetype"));
        }
        TameworkGameplayAdapter.Readiness readiness = tamework.attunementReadiness();
        if (!readiness.ready()) return released(essence, GameplayResult.unavailable(readiness.reason()));
        if (!journal.available()) {
            return released(essence, GameplayResult.unavailable("HyDragon attunement journal is unavailable"));
        }

        Optional<SoulBondLedger.Claim> foundClaim = soulBonds.find(playerUuid);
        if (foundClaim.isEmpty()) {
            return released(essence, GameplayResult.denied("Soul Bond Miniwyvern is not claimed"));
        }
        SoulBondLedger.Claim claim = foundClaim.orElseThrow();
        if (claim.state() != SoulBondLedger.Claim.State.CLAIMED || claim.profileId().isEmpty()) {
            return released(essence, GameplayResult.reconciliation(
                    "Soul Bond profile identity requires reconciliation"));
        }
        UUID profileUuid = claim.profileId().orElseThrow();
        String profileId = profileUuid.toString();

        Optional<OperationJournal.Entry> existing = journal.find(essence.operationId());
        if (existing.isPresent()) {
            OperationJournal.Entry entry = existing.orElseThrow();
            if (!matches(entry, playerUuid, profileId, archetype, essence)) {
                return released(essence, GameplayResult.reconciliation(
                        "attunement operation identity conflicts with durable evidence"));
            }
            return resume(entry, profileUuid, archetype, essence);
        }

        final Optional<ProfileDataEntryView> current;
        try {
            current = tamework.findVersionedProfileData(profileId, NAMESPACE, KEY);
        } catch (RuntimeException unavailable) {
            return released(essence, GameplayResult.unavailable(
                    "Tamework attunement profile data is unavailable"));
        }
        long expectedRevision = ProfileDataCompareAndSetRequest.MISSING_REVISION;
        if (current.isPresent()) {
            ProfileDataEntryView entry = current.orElseThrow();
            AttunementPayload payload = decode(entry.jsonPayload());
            if (!matches(entry, profileId) || payload == null) {
                return released(essence, GameplayResult.reconciliation(
                        "Miniwyvern attunement profile data is invalid"));
            }
            if (payload.archetypeId().equals(archetype)) {
                return released(essence, GameplayResult.denied("Miniwyvern already has this archetype"));
            }
            expectedRevision = entry.revision();
        }

        OperationJournal.Decision begun = journal.begin(new OperationJournal.Descriptor(
                essence.operationId(),
                essence.operationId(),
                OperationJournal.Kind.MINIWYVERN_ATTUNEMENT,
                playerUuid,
                intent(archetype),
                essence.sourceEvidence(),
                essence.quantity(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(profileId),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalLong.of(expectedRevision)));
        if (begun != OperationJournal.Decision.APPLIED
                && begun != OperationJournal.Decision.ALREADY_APPLIED) {
            return released(essence, begun == OperationJournal.Decision.QUARANTINED
                    ? new GameplayResult(GameplayResult.Status.QUARANTINED,
                    "attunement operation is quarantined")
                    : GameplayResult.reconciliation("attunement journal reservation failed"));
        }
        return executeCas(profileUuid, archetype, essence, expectedRevision, true);
    }

    private CompletionStage<GameplayResult> resume(
            OperationJournal.Entry entry,
            UUID profileId,
            String archetype,
            ConsumableReservation essence) {
        return switch (entry.phase()) {
            case COMMITTED -> released(essence,
                    new GameplayResult(GameplayResult.Status.ALREADY_APPLIED, "Miniwyvern attuned"));
            case MATERIAL_CONSUMED -> {
                OperationJournal.Decision closed = journal.transition(
                        essence.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                        OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
                yield released(essence,
                        closed == OperationJournal.Decision.APPLIED
                                || closed == OperationJournal.Decision.ALREADY_APPLIED
                                ? new GameplayResult(GameplayResult.Status.ALREADY_APPLIED, "Miniwyvern attuned")
                                : GameplayResult.reconciliation("attunement journal closure is pending"));
            }
            case REFUND_DUE -> releaseDenied(essence, "attunement was denied by Tamework");
            case REFUNDED -> released(essence, GameplayResult.denied("attunement was denied by Tamework"));
            case QUARANTINED -> released(essence,
                    new GameplayResult(GameplayResult.Status.QUARANTINED,
                            "attunement operation is quarantined"));
            case PREPARED -> recoverAuthority(entry, profileId, archetype, essence);
        };
    }

    private CompletionStage<GameplayResult> recoverAuthority(
            OperationJournal.Entry entry,
            UUID profileId,
            String archetype,
            ConsumableReservation essence) {
        final CompletionStage<Optional<ProfileDataOperationView>> lookup;
        try {
            lookup = tamework.findProfileDataOperation(NAMESPACE, essence.operationId());
        } catch (RuntimeException unavailable) {
            return completed(GameplayResult.reconciliation(
                    "attunement authority is unavailable; consumption remains pending"));
        }
        return lookup
                .handle((operation, failure) -> failure == null ? operation : null)
                .thenCompose(found -> {
                    if (found == null) {
                        return completed(GameplayResult.reconciliation(
                                "attunement authority is unavailable; consumption remains pending"));
                    }
                    if (found.isEmpty()) {
                        return executeCas(profileId, archetype, essence,
                                entry.descriptor().profileRevision().orElseThrow(), false);
                    }
                    ProfileDataOperationView operation = found.orElseThrow();
                    if (!matches(operation, profileId.toString(), essence.operationId(),
                            entry.descriptor().profileRevision().orElseThrow())) {
                        quarantine(essence.operationId(), "Tamework attunement operation identity conflict");
                        return completed(new GameplayResult(GameplayResult.Status.QUARANTINED,
                                "attunement authority identity is quarantined"));
                    }
                    return switch (operation.status()) {
                        case COMMITTED -> afterCommittedOperation(profileId, archetype, essence, operation);
                        case TERMINAL_DENIED -> releaseDenied(essence, operation.reason());
                        case QUARANTINED -> {
                            quarantine(essence.operationId(), operation.reason());
                            yield completed(new GameplayResult(GameplayResult.Status.QUARANTINED,
                                    "Tamework quarantined the attunement operation"));
                        }
                        case PREPARED, APPLYING -> executeCas(profileId, archetype, essence,
                                entry.descriptor().profileRevision().orElseThrow(), false);
                    };
                });
    }

    private CompletionStage<GameplayResult> executeCas(
            UUID profileUuid,
            String archetype,
            ConsumableReservation essence,
            long expectedRevision,
            boolean recoverFailure) {
        String payload = encode(archetype, essence.operationId());
        ProfileDataCompareAndSetRequest request = new ProfileDataCompareAndSetRequest(
                profileUuid.toString(), NAMESPACE, KEY, expectedRevision, essence.operationId(), payload);
        final CompletionStage<ProfileDataCompareAndSetResult> comparison;
        try {
            comparison = tamework.compareAndSetProfileData(request);
        } catch (RuntimeException unavailable) {
            return recoverFailure
                    ? recoverAuthority(journal.find(essence.operationId()).orElseThrow(),
                    profileUuid, archetype, essence)
                    : completed(GameplayResult.reconciliation(
                    "attunement outcome is indeterminate; consumption remains pending"));
        }
        return comparison
                .handle((result, failure) -> failure == null ? result : null)
                .thenCompose(result -> {
                    if (result == null) {
                        if (!recoverFailure) return completed(GameplayResult.reconciliation(
                                "attunement outcome is indeterminate; consumption remains pending"));
                        OperationJournal.Entry entry = journal.find(essence.operationId()).orElseThrow();
                        return recoverAuthority(entry, profileUuid, archetype, essence);
                    }
                    return switch (result.status()) {
                        case COMMITTED -> afterCommittedResult(
                                profileUuid, archetype, essence, expectedRevision, result);
                        case TERMINAL_DENIED -> releaseDenied(essence, result.reason());
                        case QUARANTINED -> {
                            quarantine(essence.operationId(), result.reason());
                            yield completed(new GameplayResult(GameplayResult.Status.QUARANTINED,
                                    "Tamework quarantined the attunement operation"));
                        }
                        case UNAVAILABLE -> recoverFailure
                                ? recoverAuthority(journal.find(essence.operationId()).orElseThrow(),
                                profileUuid, archetype, essence)
                                : completed(GameplayResult.reconciliation(
                                "attunement authority is unavailable; consumption remains pending"));
                    };
                });
    }

    private CompletionStage<GameplayResult> afterCommittedResult(
            UUID profileId,
            String archetype,
            ConsumableReservation essence,
            long expectedRevision,
            ProfileDataCompareAndSetResult result) {
        ProfileDataOperationView operation = result.durableOperation().orElseThrow();
        ProfileDataEntryView entry = result.committedEntry().orElseThrow();
        if (!matches(operation, profileId.toString(), essence.operationId(), expectedRevision)
                || !matches(entry, profileId.toString())
                || entry.revision() != operation.resultingRevision()
                || !payloadMatches(entry.jsonPayload(), archetype, essence.operationId())) {
            quarantine(essence.operationId(), "Tamework returned inconsistent attunement proof");
            return completed(new GameplayResult(GameplayResult.Status.QUARANTINED,
                    "Tamework attunement proof is inconsistent"));
        }
        return applyProjectionAndConsume(profileId, archetype, essence, operation);
    }

    private CompletionStage<GameplayResult> afterCommittedOperation(
            UUID profileId,
            String archetype,
            ConsumableReservation essence,
            ProfileDataOperationView operation) {
        final Optional<ProfileDataEntryView> found;
        try {
            found = tamework.findVersionedProfileData(profileId.toString(), NAMESPACE, KEY);
        } catch (RuntimeException unavailable) {
            return completed(GameplayResult.reconciliation(
                    "committed attunement value cannot yet be verified"));
        }
        if (found.isEmpty()) {
            return completed(GameplayResult.reconciliation(
                    "committed attunement value cannot yet be verified"));
        }
        ProfileDataEntryView entry = found.orElseThrow();
        if (!matches(entry, profileId.toString())
                || entry.revision() != operation.resultingRevision()
                || !payloadMatches(entry.jsonPayload(), archetype, essence.operationId())) {
            quarantine(essence.operationId(), "Committed attunement value conflicts with journal");
            return completed(new GameplayResult(GameplayResult.Status.QUARANTINED,
                    "committed attunement value conflicts with durable evidence"));
        }
        return applyProjectionAndConsume(profileId, archetype, essence, operation);
    }

    private CompletionStage<GameplayResult> applyProjectionAndConsume(
            UUID profileId,
            String archetype,
            ConsumableReservation essence,
            ProfileDataOperationView authority) {
        final ProfileProjection.Decision synchronizedProjection;
        try {
            synchronizedProjection = projection.synchronize(
                    profileId, archetype, essence.operationId());
        } catch (RuntimeException unavailable) {
            return completed(GameplayResult.reconciliation(
                    "attunement committed; HyDragon profile projection remains pending"));
        }
        if (synchronizedProjection == ProfileProjection.Decision.QUARANTINED) {
            quarantine(essence.operationId(), "HyDragon profile projection is quarantined");
            return completed(new GameplayResult(GameplayResult.Status.QUARANTINED,
                    "HyDragon profile projection is quarantined"));
        }
        if (synchronizedProjection != ProfileProjection.Decision.APPLIED
                && synchronizedProjection != ProfileProjection.Decision.ALREADY_APPLIED) {
            return completed(GameplayResult.reconciliation(
                    "attunement committed; HyDragon profile projection remains pending"));
        }
        final CompletionStage<ConsumableReservation.Disposition> consumption;
        try {
            consumption = essence.consume();
        } catch (RuntimeException unavailable) {
            return completed(GameplayResult.reconciliation(
                    "attunement committed; essence consumption requires reconciliation"));
        }
        return consumption.handle((consumed, failure) -> {
            if (failure != null) {
                return GameplayResult.reconciliation(
                        "attunement committed; essence consumption requires reconciliation");
            }
            if (consumed != ConsumableReservation.Disposition.APPLIED
                    && consumed != ConsumableReservation.Disposition.ALREADY_APPLIED) {
                return GameplayResult.reconciliation(
                        "attunement committed; essence consumption requires reconciliation");
            }
            OperationJournal.Decision material = journal.transition(
                    essence.operationId(), OperationJournal.Phase.PREPARED,
                    OperationJournal.Phase.MATERIAL_CONSUMED,
                    new OperationJournal.Update(
                            Optional.of(authority.operationId().toString()),
                            Optional.of(profileId.toString()),
                            OptionalLong.empty(),
                            Optional.empty()));
            if (material != OperationJournal.Decision.APPLIED
                    && material != OperationJournal.Decision.ALREADY_APPLIED) {
                return GameplayResult.reconciliation(
                        "essence consumed; attunement journal requires reconciliation");
            }
            OperationJournal.Decision closed = journal.transition(
                    essence.operationId(), OperationJournal.Phase.MATERIAL_CONSUMED,
                    OperationJournal.Phase.COMMITTED, OperationJournal.Update.EMPTY);
            return closed == OperationJournal.Decision.APPLIED
                    || closed == OperationJournal.Decision.ALREADY_APPLIED
                    ? GameplayResult.applied("Miniwyvern attuned")
                    : GameplayResult.reconciliation("attunement succeeded; journal closure is pending");
        });
    }

    private CompletionStage<GameplayResult> releaseDenied(
            ConsumableReservation essence,
            String reason) {
        return essence.release().handle((released, failure) -> {
            if (failure == null && (released == ConsumableReservation.Disposition.APPLIED
                    || released == ConsumableReservation.Disposition.ALREADY_APPLIED)) {
                // A profile-data CAS denial occurs before material consumption. Keep PREPARED so
                // retries can re-read the durable Tamework denial; refund phases are reserved for
                // sagas whose material was actually consumed.
                if (journal.find(essence.operationId())
                        .map(OperationJournal.Entry::phase)
                        .orElse(OperationJournal.Phase.PREPARED) == OperationJournal.Phase.REFUND_DUE) {
                    journal.transition(essence.operationId(), OperationJournal.Phase.REFUND_DUE,
                            OperationJournal.Phase.REFUNDED, OperationJournal.Update.EMPTY);
                }
                return GameplayResult.denied(reason);
            }
            return GameplayResult.reconciliation("attunement denied; essence release remains pending");
        });
    }

    private void quarantine(String operationId, String reason) {
        journal.transition(operationId, OperationJournal.Phase.PREPARED,
                OperationJournal.Phase.QUARANTINED,
                new OperationJournal.Update(Optional.empty(), Optional.empty(),
                        OptionalLong.empty(), Optional.of(reason)));
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
                && descriptor.source().itemId().equals(essence.sourceEvidence().itemId())
                && descriptor.materialQuantity() == essence.quantity();
    }

    private static boolean matches(ProfileDataEntryView entry, String profileId) {
        return entry.profileId().equals(profileId)
                && entry.namespace().equals(NAMESPACE)
                && entry.key().equals(KEY);
    }

    private static boolean matches(
            ProfileDataOperationView operation,
            String profileId,
            String operationId,
            long expectedRevision) {
        return operation.namespace().equals(NAMESPACE)
                && operation.idempotencyKey().equals(operationId)
                && operation.profileId().equals(profileId)
                && operation.key().equals(KEY)
                && operation.expectedRevision() == expectedRevision;
    }

    private static String encode(String archetype, String operationId) {
        return GSON.toJson(new AttunementPayload(PAYLOAD_SCHEMA, archetype, operationId));
    }

    private static AttunementPayload decode(String json) {
        try {
            AttunementPayload decoded = GSON.fromJson(json, AttunementPayload.class);
            if (decoded == null) return null;
            return new AttunementPayload(
                    decoded.schemaVersion(), decoded.archetypeId(), decoded.attunementOperationId());
        } catch (JsonParseException | IllegalArgumentException | NullPointerException invalid) {
            return null;
        }
    }

    private static boolean payloadMatches(String json, String archetype, String operationId) {
        AttunementPayload payload = decode(json);
        return payload != null
                && payload.archetypeId().equals(archetype)
                && payload.attunementOperationId().equals(operationId);
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

    private static CompletionStage<GameplayResult> released(
            ConsumableReservation essence,
            GameplayResult result) {
        return essence.release().handle((ignored, failure) -> result);
    }

    private static CompletionStage<GameplayResult> completed(GameplayResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private record AttunementPayload(
            int schemaVersion,
            String archetypeId,
            String attunementOperationId) {
        private AttunementPayload {
            if (schemaVersion != PAYLOAD_SCHEMA) {
                throw new IllegalArgumentException("unsupported attunement payload schema");
            }
            archetypeId = normalize(archetypeId);
            if (!ESSENCE_ITEMS.containsKey(archetypeId)) {
                throw new IllegalArgumentException("unsupported attunement archetype");
            }
            attunementOperationId = Objects.requireNonNull(
                    attunementOperationId, "attunementOperationId").trim();
            if (attunementOperationId.isEmpty()) {
                throw new IllegalArgumentException("attunementOperationId is required");
            }
        }
    }

    /** Local projection commit which makes the persisted archetype visible to HyDragon runtimes. */
    @FunctionalInterface
    public interface ProfileProjection {
        Decision synchronize(UUID profileId, String archetypeId, String operationId);

        enum Decision { APPLIED, ALREADY_APPLIED, CONFLICT, QUARANTINED, UNAVAILABLE }
    }
}
