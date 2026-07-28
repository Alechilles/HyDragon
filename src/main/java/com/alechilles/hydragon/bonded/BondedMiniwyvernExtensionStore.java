package com.alechilles.hydragon.bonded;

import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataKey;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataUpdate;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionResultCode;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Validates and translates Tamework extension results into HyDragon domain outcomes. */
public final class BondedMiniwyvernExtensionStore {
    private final BondedMiniwyvernExtensionGateway gateway;
    private final BondedMiniwyvernExtensionCodec codec;

    public BondedMiniwyvernExtensionStore(
            BondedMiniwyvernExtensionGateway gateway,
            BondedMiniwyvernExtensionCodec codec) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public CompletionStage<ReadResult> load(UUID ownerUuid, String profileId) {
        UUID owner = Objects.requireNonNull(ownerUuid, "ownerUuid");
        String profile = requiredText(profileId, "profileId");
        return gateway.getMiniwyvernExtension(owner, profile)
                .handle((result, failure) -> readResult(
                        owner, profile, result, failure));
    }

    public CompletionStage<WriteResult> compareAndSet(
            UUID ownerUuid,
            String profileId,
            String operationId,
            long expectedRevision,
            BondedMiniwyvernExtensionDocument desired) {
        UUID owner = Objects.requireNonNull(ownerUuid, "ownerUuid");
        String profile = requiredText(profileId, "profileId");
        String operation = requiredText(operationId, "operationId");
        Objects.requireNonNull(desired, "desired");
        String payload = codec.encode(desired);
        return gateway.compareAndSetMiniwyvernExtension(
                        owner, profile, operation, payload, expectedRevision)
                .handle((result, failure) -> writeResult(
                        owner, profile, payload, expectedRevision,
                        result, failure));
    }

    private ReadResult readResult(
            UUID owner,
            String profile,
            BondedCompanionResult<BondedCompanionExtensionData> result,
            Throwable failure) {
        if (failure != null) return ReadResult.unavailable(message(failure));
        if (result == null) return ReadResult.unavailable("empty bonded extension response");
        if (result.code() == BondedCompanionResultCode.NOT_FOUND) {
            return ReadResult.missing(result.reason());
        }
        if (unavailable(result.code())) return ReadResult.unavailable(result.reason());
        if (!result.successful() || result.value() == null) {
            return ReadResult.invalid(result.reason());
        }
        return decodeRead(owner, profile, result.value());
    }

    private ReadResult decodeRead(
            UUID owner,
            String profile,
            BondedCompanionExtensionData data) {
        if (!matches(data.key(), owner, profile)) {
            return ReadResult.invalid("bonded extension authority mismatch");
        }
        try {
            return ReadResult.loaded(codec.decode(data.jsonPayload()), data.revision());
        } catch (RuntimeException failure) {
            return ReadResult.invalid(message(failure));
        }
    }

    private WriteResult writeResult(
            UUID owner,
            String profile,
            String payload,
            long expectedRevision,
            BondedCompanionResult<BondedCompanionExtensionData> result,
            Throwable failure) {
        if (failure != null) return WriteResult.unavailable(message(failure));
        if (result == null) return WriteResult.unavailable("empty bonded extension response");
        if (result.code() == BondedCompanionResultCode.REVISION_CONFLICT) {
            return WriteResult.conflict(result.reason());
        }
        if (unavailable(result.code())) return WriteResult.unavailable(result.reason());
        if (!result.successful() || result.value() == null) {
            return WriteResult.rejected(result.reason());
        }
        return validateWrite(owner, profile, payload, expectedRevision, result.value());
    }

    private WriteResult validateWrite(
            UUID owner,
            String profile,
            String payload,
            long expectedRevision,
            BondedCompanionExtensionData data) {
        long nextRevision = nextRevision(expectedRevision);
        if (nextRevision < 0L
                || !matches(data.key(), owner, profile)
                || data.revision() != nextRevision
                || !payload.equals(data.jsonPayload())) {
            return WriteResult.invalid("bonded extension result mismatch");
        }
        try {
            return WriteResult.applied(codec.decode(data.jsonPayload()), data.revision());
        } catch (RuntimeException failure) {
            return WriteResult.invalid(message(failure));
        }
    }

    private static boolean matches(
            BondedCompanionExtensionDataKey key,
            UUID owner,
            String profile) {
        return key != null
                && owner.equals(key.ownerUuid())
                && profile.equals(key.profileId())
                && BondedMiniwyvernExtensionDocument.NAMESPACE.equals(key.namespace());
    }

    private static boolean unavailable(BondedCompanionResultCode code) {
        return code == BondedCompanionResultCode.UNAVAILABLE
                || code == BondedCompanionResultCode.INTERNAL_FAILURE
                || code == BondedCompanionResultCode.WORLD_UNAVAILABLE;
    }

    private static long nextRevision(long expectedRevision) {
        if (expectedRevision == BondedCompanionExtensionDataUpdate.MISSING_REVISION) return 0L;
        return expectedRevision < 0L || expectedRevision == Long.MAX_VALUE
                ? -1L
                : expectedRevision + 1L;
    }

    private static String message(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message.trim();
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    public enum ReadStatus { LOADED, MISSING, UNAVAILABLE, INVALID }

    public record ReadResult(
            ReadStatus status,
            BondedMiniwyvernExtensionDocument document,
            long revision,
            String reason) {
        public ReadResult {
            Objects.requireNonNull(status, "status");
            if ((status == ReadStatus.LOADED) != (document != null && revision >= 0L)) {
                throw new IllegalArgumentException("Only loaded reads carry a document revision");
            }
            reason = normalizeReason(reason, status.name());
        }

        static ReadResult loaded(BondedMiniwyvernExtensionDocument document, long revision) {
            return new ReadResult(ReadStatus.LOADED, document, revision, "loaded");
        }

        static ReadResult missing(String reason) {
            return new ReadResult(ReadStatus.MISSING, null, -1L, reason);
        }

        static ReadResult unavailable(String reason) {
            return new ReadResult(ReadStatus.UNAVAILABLE, null, -1L, reason);
        }

        static ReadResult invalid(String reason) {
            return new ReadResult(ReadStatus.INVALID, null, -1L, reason);
        }
    }

    public enum WriteStatus { APPLIED, CONFLICT, UNAVAILABLE, REJECTED, INVALID }

    public record WriteResult(
            WriteStatus status,
            BondedMiniwyvernExtensionDocument document,
            long revision,
            String reason) {
        public WriteResult {
            Objects.requireNonNull(status, "status");
            if ((status == WriteStatus.APPLIED) != (document != null && revision >= 0L)) {
                throw new IllegalArgumentException("Only applied writes carry a document revision");
            }
            reason = normalizeReason(reason, status.name());
        }

        static WriteResult applied(BondedMiniwyvernExtensionDocument document, long revision) {
            return new WriteResult(WriteStatus.APPLIED, document, revision, "applied");
        }

        static WriteResult conflict(String reason) {
            return new WriteResult(WriteStatus.CONFLICT, null, -1L, reason);
        }

        static WriteResult unavailable(String reason) {
            return new WriteResult(WriteStatus.UNAVAILABLE, null, -1L, reason);
        }

        static WriteResult rejected(String reason) {
            return new WriteResult(WriteStatus.REJECTED, null, -1L, reason);
        }

        static WriteResult invalid(String reason) {
            return new WriteResult(WriteStatus.INVALID, null, -1L, reason);
        }
    }

    private static String normalizeReason(String reason, String fallback) {
        return reason == null || reason.isBlank()
                ? fallback.toLowerCase(java.util.Locale.ROOT)
                : reason.trim();
    }
}
