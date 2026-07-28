package com.alechilles.hydragon.runtime;

import com.alechilles.alecstamework.api.BondedCompanionExtensionDataUpdate;
import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionResultCode;
import com.alechilles.alecstamework.api.BondedCompanionStateView;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionDocument;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionStore;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Provisions one stored bonded Miniwyvern and installs its extension document. */
public final class BondedMiniwyvernProvisioningService {
    private static final String SPECIES_ID = "miniwyvern";

    private final TameworkGameplayAdapter tamework;
    private final BondedMiniwyvernExtensionStore extensions;

    public BondedMiniwyvernProvisioningService(
            TameworkGameplayAdapter tamework,
            BondedMiniwyvernExtensionStore extensions) {
        this.tamework = Objects.requireNonNull(tamework, "tamework");
        this.extensions = Objects.requireNonNull(extensions, "extensions");
    }

    public CompletionStage<Outcome> provision(UUID ownerUuid, String operationId) {
        UUID owner = Objects.requireNonNull(ownerUuid, "ownerUuid");
        String operation = requiredText(operationId, "operationId");
        try {
            return tamework.provisionMiniwyvern(owner, operation)
                    .handle((result, failure) -> failure == null ? result : null)
                    .thenCompose(result -> resolveProvision(owner, operation, result));
        } catch (RuntimeException | LinkageError failure) {
            return completed(Outcome.retryable(
                    "Tamework bonded provision authority is unavailable"));
        }
    }

    private CompletionStage<Outcome> resolveProvision(
            UUID owner,
            String operation,
            BondedCompanionResult<BondedCompanionProfileView> result) {
        if (result == null || retryable(result.code())) {
            return completed(Outcome.retryable(result == null
                    ? "Tamework bonded provision result is unknown"
                    : result.reason()));
        }
        if (terminalDenial(result.code())) {
            return completed(Outcome.denied(result.reason()));
        }
        if (!result.successful() || result.value() == null) {
            return completed(Outcome.quarantined(
                    "Tamework bonded provision returned " + result.code()));
        }
        ProfileEvidence evidence = evidence(owner, result.value());
        return evidence == null
                ? completed(Outcome.quarantined(
                "Tamework bonded provision returned noncanonical authority evidence"))
                : initializeExtension(owner, operation, evidence);
    }

    private CompletionStage<Outcome> initializeExtension(
            UUID owner,
            String operation,
            ProfileEvidence evidence) {
        return load(owner, evidence).thenCompose(read -> {
            if (valid(read)) return completed(applied(operation, evidence));
            if (read == null
                    || read.status() == BondedMiniwyvernExtensionStore.ReadStatus.UNAVAILABLE) {
                return completed(Outcome.retryable(
                        "Miniwyvern extension authority is unavailable"));
            }
            if (read.status() == BondedMiniwyvernExtensionStore.ReadStatus.INVALID) {
                return completed(Outcome.quarantined(
                        "Miniwyvern extension authority is invalid"));
            }
            return create(owner, operation, evidence);
        });
    }

    private CompletionStage<Outcome> create(
            UUID owner,
            String operation,
            ProfileEvidence evidence) {
        BondedMiniwyvernExtensionDocument desired =
                BondedMiniwyvernExtensionDocument.wild(SPECIES_ID, 0L);
        try {
            return extensions.compareAndSet(
                            owner, evidence.profileId().toString(),
                            extensionOperationId(operation),
                            BondedCompanionExtensionDataUpdate.MISSING_REVISION,
                            desired)
                    .thenCompose(write -> resolveWrite(
                            owner, operation, evidence, write));
        } catch (RuntimeException failure) {
            return completed(Outcome.retryable(
                    "Miniwyvern extension initialization is unavailable"));
        }
    }

    private CompletionStage<Outcome> resolveWrite(
            UUID owner,
            String operation,
            ProfileEvidence evidence,
            BondedMiniwyvernExtensionStore.WriteResult write) {
        if (write != null
                && write.status() == BondedMiniwyvernExtensionStore.WriteStatus.APPLIED
                && valid(write.document())) {
            return completed(applied(operation, evidence));
        }
        if (write != null
                && (write.status() == BondedMiniwyvernExtensionStore.WriteStatus.CONFLICT
                || write.status() == BondedMiniwyvernExtensionStore.WriteStatus.UNAVAILABLE)) {
            return verify(owner, operation, evidence);
        }
        return completed(Outcome.quarantined(
                "Miniwyvern extension initialization returned invalid evidence"));
    }

    private CompletionStage<Outcome> verify(
            UUID owner,
            String operation,
            ProfileEvidence evidence) {
        return load(owner, evidence).thenApply(read -> {
            if (valid(read)) return applied(operation, evidence);
            if (read != null
                    && read.status() == BondedMiniwyvernExtensionStore.ReadStatus.INVALID) {
                return Outcome.quarantined(
                        "Miniwyvern extension verification is invalid");
            }
            return Outcome.retryable(
                    "Miniwyvern extension initialization remains pending");
        });
    }

    private CompletionStage<BondedMiniwyvernExtensionStore.ReadResult> load(
            UUID owner,
            ProfileEvidence evidence) {
        try {
            return extensions.load(owner, evidence.profileId().toString());
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static ProfileEvidence evidence(
            UUID owner,
            BondedCompanionProfileView profile) {
        if (!owner.equals(profile.ownerUuid())
                || !TameworkGameplayAdapter.DRAGON_HORN_ROSTER.equals(profile.rosterId())
                || !TameworkGameplayAdapter.MINIWYVERN_FAMILY.equals(profile.familyId())
                || !TameworkGameplayAdapter.WILD_MINIWYVERN_ROLE.equals(profile.roleId())
                || profile.state() != BondedCompanionStateView.STORED
                || profile.activeLease() != null) {
            return null;
        }
        UUID profileId = parseUuid(profile.profileId());
        return profileId == null || profile.revision() < 0L
                ? null : new ProfileEvidence(profileId, profile.revision());
    }

    private static boolean valid(BondedMiniwyvernExtensionStore.ReadResult read) {
        return read != null
                && read.status() == BondedMiniwyvernExtensionStore.ReadStatus.LOADED
                && valid(read.document());
    }

    private static boolean valid(BondedMiniwyvernExtensionDocument document) {
        return document != null && SPECIES_ID.equals(document.speciesId());
    }

    private static boolean retryable(BondedCompanionResultCode code) {
        return code == BondedCompanionResultCode.UNAVAILABLE
                || code == BondedCompanionResultCode.INTERNAL_FAILURE
                || code == BondedCompanionResultCode.WORLD_UNAVAILABLE;
    }

    private static boolean terminalDenial(BondedCompanionResultCode code) {
        return code == BondedCompanionResultCode.POLICY_DENIED
                || code == BondedCompanionResultCode.VALIDATION_FAILED;
    }

    private static Outcome applied(String operation, ProfileEvidence evidence) {
        return Outcome.applied(
                evidence.profileId(), evidence.profileRevision(),
                extensionOperationId(operation));
    }

    private static String extensionOperationId(String operationId) {
        return operationId + ":extension:init";
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static CompletionStage<Outcome> completed(Outcome outcome) {
        return CompletableFuture.completedFuture(outcome);
    }

    public enum Status { APPLIED, RETRYABLE, DENIED, QUARANTINED }

    /** Complete domain result without leaking Tamework's response envelope. */
    public record Outcome(
            Status status,
            String reason,
            UUID profileId,
            long profileRevision,
            String authorityOperationId) {
        public Outcome {
            status = Objects.requireNonNull(status, "status");
            reason = requiredText(reason, "reason");
            boolean applied = status == Status.APPLIED;
            if (applied != (profileId != null && profileRevision >= 0L
                    && authorityOperationId != null)) {
                throw new IllegalArgumentException(
                        "Only applied provisioning carries authority evidence");
            }
        }

        static Outcome applied(
                UUID profileId,
                long revision,
                String authorityOperationId) {
            return new Outcome(Status.APPLIED, "provisioned", profileId,
                    revision, authorityOperationId);
        }

        static Outcome retryable(String reason) {
            return new Outcome(Status.RETRYABLE, reason, null, -1L, null);
        }

        static Outcome denied(String reason) {
            return new Outcome(Status.DENIED, reason, null, -1L, null);
        }

        static Outcome quarantined(String reason) {
            return new Outcome(Status.QUARANTINED, reason, null, -1L, null);
        }
    }

    private record ProfileEvidence(UUID profileId, long profileRevision) {
    }
}
