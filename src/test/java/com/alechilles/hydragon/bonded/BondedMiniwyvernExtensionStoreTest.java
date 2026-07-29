package com.alechilles.hydragon.bonded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataKey;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionResultCode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/** Contract tests for validated, asynchronous bonded Miniwyvern extension access. */
final class BondedMiniwyvernExtensionStoreTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final String PROFILE = "profile-mini";
    private static final BondedMiniwyvernExtensionCodec CODEC =
            new BondedMiniwyvernExtensionCodec();

    @Test
    void loadsAndDecodesOnlyExactExtensionAuthority() {
        BondedMiniwyvernExtensionDocument wild =
                BondedMiniwyvernExtensionDocument.wild("hydragon:miniwyvern", 10L);
        FakeGateway gateway = new FakeGateway();
        gateway.read.complete(success(data(OWNER, PROFILE, CODEC.encode(wild), 4L)));

        BondedMiniwyvernExtensionStore.ReadResult result =
                new BondedMiniwyvernExtensionStore(gateway, CODEC)
                        .load(OWNER, PROFILE).toCompletableFuture().join();

        assertEquals(BondedMiniwyvernExtensionStore.ReadStatus.LOADED, result.status());
        assertEquals(4L, result.revision());
        assertEquals("wild", result.document().abilityState().formId());
    }

    @Test
    void missingUnavailableAndMalformedReadsRemainDistinct() {
        assertEquals(BondedMiniwyvernExtensionStore.ReadStatus.MISSING,
                load(new BondedCompanionResult<>(
                        BondedCompanionResultCode.NOT_FOUND, null, "missing")).status());
        assertEquals(BondedMiniwyvernExtensionStore.ReadStatus.UNAVAILABLE,
                load(BondedCompanionResult.unavailable("offline")).status());
        BondedMiniwyvernExtensionStore.ReadResult malformed = load(success(
                data(OWNER, PROFILE, "{\"wrong\":true}", 2L)));
        assertEquals(BondedMiniwyvernExtensionStore.ReadStatus.INVALID, malformed.status());
        assertNull(malformed.document());
    }

    @Test
    void compareAndSetAcceptsOnlyExactPayloadKeyAndNextRevision() {
        BondedMiniwyvernExtensionDocument wild =
                BondedMiniwyvernExtensionDocument.wild("hydragon:miniwyvern", 10L);
        String payload = CODEC.encode(wild);
        FakeGateway gateway = new FakeGateway();
        gateway.write.complete(success(data(OWNER, PROFILE, payload, 5L)));
        BondedMiniwyvernExtensionStore store =
                new BondedMiniwyvernExtensionStore(gateway, CODEC);

        BondedMiniwyvernExtensionStore.WriteResult applied = store.compareAndSet(
                OWNER, PROFILE, "ability-5", 4L, wild).toCompletableFuture().join();

        assertEquals(BondedMiniwyvernExtensionStore.WriteStatus.APPLIED, applied.status());
        assertEquals(5L, applied.revision());
        assertEquals("ability-5", gateway.operationId);
        assertEquals(4L, gateway.expectedRevision);
        assertEquals(payload, gateway.payload);

        FakeGateway wrongRevision = new FakeGateway();
        wrongRevision.write.complete(success(data(OWNER, PROFILE, payload, 7L)));
        assertEquals(BondedMiniwyvernExtensionStore.WriteStatus.INVALID,
                new BondedMiniwyvernExtensionStore(wrongRevision, CODEC)
                        .compareAndSet(OWNER, PROFILE, "ability-6", 4L, wild)
                        .toCompletableFuture().join().status());
    }

    @Test
    void conflictAndUnavailableWritesRemainRetryableWithoutBlocking() {
        FakeGateway gateway = new FakeGateway();
        BondedMiniwyvernExtensionStore store =
                new BondedMiniwyvernExtensionStore(gateway, CODEC);
        BondedMiniwyvernExtensionDocument wild =
                BondedMiniwyvernExtensionDocument.wild("hydragon:miniwyvern", 10L);

        CompletionStage<BondedMiniwyvernExtensionStore.WriteResult> pending =
                store.compareAndSet(OWNER, PROFILE, "ability-7", -1L, wild);
        assertFalse(pending.toCompletableFuture().isDone());

        gateway.write.complete(new BondedCompanionResult<>(
                BondedCompanionResultCode.REVISION_CONFLICT, null, "conflict"));
        assertEquals(BondedMiniwyvernExtensionStore.WriteStatus.CONFLICT,
                pending.toCompletableFuture().join().status());

        FakeGateway unavailable = new FakeGateway();
        unavailable.write.complete(BondedCompanionResult.unavailable("offline"));
        assertEquals(BondedMiniwyvernExtensionStore.WriteStatus.UNAVAILABLE,
                new BondedMiniwyvernExtensionStore(unavailable, CODEC)
                        .compareAndSet(OWNER, PROFILE, "ability-8", -1L, wild)
                        .toCompletableFuture().join().status());
    }

    @Test
    void exceptionalGatewayCompletionDegradesToUnavailable() {
        FakeGateway gateway = new FakeGateway();
        gateway.read.completeExceptionally(new IllegalStateException("database failed"));

        BondedMiniwyvernExtensionStore.ReadResult result =
                new BondedMiniwyvernExtensionStore(gateway, CODEC)
                        .load(OWNER, PROFILE).toCompletableFuture().join();

        assertEquals(BondedMiniwyvernExtensionStore.ReadStatus.UNAVAILABLE, result.status());
        assertTrue(result.reason().contains("database failed"));
    }

    private static BondedMiniwyvernExtensionStore.ReadResult load(
            BondedCompanionResult<BondedCompanionExtensionData> response) {
        FakeGateway gateway = new FakeGateway();
        gateway.read.complete(response);
        return new BondedMiniwyvernExtensionStore(gateway, CODEC)
                .load(OWNER, PROFILE).toCompletableFuture().join();
    }

    private static BondedCompanionExtensionData data(
            UUID owner,
            String profile,
            String payload,
            long revision) {
        return new BondedCompanionExtensionData(
                new BondedCompanionExtensionDataKey(
                        owner, profile, BondedMiniwyvernExtensionDocument.NAMESPACE),
                payload, revision, 10L);
    }

    private static <T> BondedCompanionResult<T> success(T value) {
        return new BondedCompanionResult<>(BondedCompanionResultCode.SUCCESS, value, null);
    }

    private static final class FakeGateway implements BondedMiniwyvernExtensionGateway {
        private final CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>> read =
                new CompletableFuture<>();
        private final CompletableFuture<BondedCompanionResult<BondedCompanionExtensionData>> write =
                new CompletableFuture<>();
        private String operationId;
        private String payload;
        private long expectedRevision;

        @Override
        public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                getMiniwyvernExtension(UUID ownerUuid, String profileId) {
            return read;
        }

        @Override
        public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                compareAndSetMiniwyvernExtension(
                        UUID ownerUuid,
                        String profileId,
                        String idempotencyKey,
                        String jsonPayload,
                        long expectedRevision) {
            operationId = idempotencyKey;
            payload = jsonPayload;
            this.expectedRevision = expectedRevision;
            return write;
        }
    }
}
