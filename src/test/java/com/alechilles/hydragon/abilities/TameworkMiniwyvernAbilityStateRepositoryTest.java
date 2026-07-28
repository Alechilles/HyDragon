package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.BondedCompanionExtensionData;
import com.alechilles.alecstamework.api.BondedCompanionExtensionDataKey;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionResultCode;
import com.alechilles.hydragon.bonded.BondedExtensionJsonValue;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionCodec;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionDocument;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionGateway;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/** Regression coverage for ability-state merges into the shared bonded extension document. */
final class TameworkMiniwyvernAbilityStateRepositoryTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final String PROFILE = "profile-mini";
    private static final BondedMiniwyvernExtensionCodec CODEC =
            new BondedMiniwyvernExtensionCodec();

    @Test
    void saveMergesAbilityStateWithoutDiscardingAttunementOrProgression() {
        MemoryGateway gateway = new MemoryGateway();
        BondedMiniwyvernExtensionDocument original = CODEC.decode("""
                {
                  "schemaVersion":1,
                  "companionKind":"SOULBOUND_MINIWYVERN",
                  "speciesId":"hydragon:miniwyvern",
                  "archetypeId":"ice",
                  "archetypeRevision":2,
                  "lastAttunementOperationId":"attune-2",
                  "progression":{"level":7},
                  "futureTop":{"kept":true},
                  "abilityState":{
                    "schemaVersion":2,
                    "archetypeId":"ice",
                    "cooldownUntilByAbility":{},
                    "iceBuildupByTarget":{},
                    "controlImmunityUntilByTarget":{},
                    "iceTargetUpdatedAtByTarget":{},
                    "appliedSourceKeys":[],
                    "targetBySourceKey":{},
                    "sourceExpiresAtBySourceKey":{},
                    "updatedAtEpochMillis":10
                  }
                }
                """);
        gateway.install(original, 3L);
        TameworkMiniwyvernAbilityStateRepository repository = repository(gateway);
        MiniwyvernAbilityState replacement = MiniwyvernAbilityState.empty("ice", 25L);

        assertEquals(MiniwyvernAbilityStateRepository.Status.LOADED,
                repository.load(OWNER, PROFILE).status());
        assertTrue(repository.save(OWNER, PROFILE, replacement));

        BondedMiniwyvernExtensionDocument saved = gateway.document();
        assertEquals(replacement, saved.abilityState());
        assertEquals("ice", saved.archetypeId());
        assertEquals(2L, saved.archetypeRevision());
        assertEquals("attune-2", saved.lastAttunementOperationId().orElseThrow());
        assertEquals(BondedExtensionJsonValue.parse("{\"level\":7}"), saved.progression());
        assertTrue(saved.unknownTopLevelFields().containsKey("futureTop"));
        assertEquals(4L, gateway.revision);
    }

    @Test
    void missingOrInvalidDocumentFailsClosedInsteadOfCreatingParallelState() {
        MemoryGateway missing = new MemoryGateway();
        TameworkMiniwyvernAbilityStateRepository missingRepository = repository(missing);

        assertEquals(MiniwyvernAbilityStateRepository.Status.MISSING,
                missingRepository.load(OWNER, PROFILE).status());
        assertFalse(missingRepository.save(
                OWNER, PROFILE, MiniwyvernAbilityState.empty("fire", 10L)));

        MemoryGateway malformed = new MemoryGateway();
        malformed.rawPayload = "{}";
        malformed.revision = 0L;
        TameworkMiniwyvernAbilityStateRepository malformedRepository = repository(malformed);
        assertEquals(MiniwyvernAbilityStateRepository.Status.UNAVAILABLE,
                malformedRepository.load(OWNER, PROFILE).status());
        assertFalse(malformedRepository.save(
                OWNER, PROFILE, MiniwyvernAbilityState.empty("fire", 10L)));
    }

    @Test
    void concurrentAttunementCannotBeOverwrittenByAStaleAbilityTick() {
        MemoryGateway gateway = new MemoryGateway();
        gateway.install(BondedMiniwyvernExtensionDocument.neutral(
                "hydragon:miniwyvern", 10L), 0L);
        TameworkMiniwyvernAbilityStateRepository repository = repository(gateway);
        assertEquals(MiniwyvernAbilityStateRepository.Status.LOADED,
                repository.load(OWNER, PROFILE).status());
        gateway.externalWrite(gateway.document().attune("ice", "attune-concurrent"));

        assertFalse(repository.save(
                OWNER, PROFILE, MiniwyvernAbilityState.empty("neutral", 20L)));
        assertEquals("ice", gateway.document().archetypeId());
        assertEquals("attune-concurrent",
                gateway.document().lastAttunementOperationId().orElseThrow());
    }

    @Test
    void exactDeterministicRetryRecoversALostCasResponse() {
        MemoryGateway gateway = new MemoryGateway();
        gateway.install(BondedMiniwyvernExtensionDocument.neutral(
                "hydragon:miniwyvern", 10L), 0L);
        gateway.failNextResponseAfterCommit = true;
        TameworkMiniwyvernAbilityStateRepository repository = repository(gateway);
        MiniwyvernAbilityState desired = MiniwyvernAbilityState.empty("neutral", 20L);
        repository.load(OWNER, PROFILE);

        assertFalse(repository.save(OWNER, PROFILE, desired));
        assertTrue(repository.save(OWNER, PROFILE, desired));
        assertEquals(1, gateway.commits);
        assertEquals(desired, gateway.document().abilityState());
    }

    @Test
    void incompleteAsyncResponseFailsClosedWithoutBlockingTheWorldThread() {
        BondedMiniwyvernExtensionGateway pending = new BondedMiniwyvernExtensionGateway() {
            @Override
            public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                    getMiniwyvernExtension(UUID ownerUuid, String profileId) {
                return new CompletableFuture<>();
            }

            @Override
            public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                    compareAndSetMiniwyvernExtension(
                            UUID ownerUuid, String profileId, String idempotencyKey,
                            String jsonPayload, long expectedRevision) {
                return new CompletableFuture<>();
            }
        };
        TameworkMiniwyvernAbilityStateRepository repository = new
                TameworkMiniwyvernAbilityStateRepository(
                new BondedMiniwyvernExtensionStore(pending, CODEC));

        assertEquals(MiniwyvernAbilityStateRepository.Status.UNAVAILABLE,
                repository.load(OWNER, PROFILE).status());
        assertFalse(repository.save(
                OWNER, PROFILE, MiniwyvernAbilityState.empty("neutral", 20L)));
    }

    private static TameworkMiniwyvernAbilityStateRepository repository(
            MemoryGateway gateway) {
        return new TameworkMiniwyvernAbilityStateRepository(
                new BondedMiniwyvernExtensionStore(gateway, CODEC));
    }

    private static final class MemoryGateway implements BondedMiniwyvernExtensionGateway {
        private final Map<String, Operation> operations = new LinkedHashMap<>();
        private String rawPayload;
        private long revision = -1L;
        private boolean failNextResponseAfterCommit;
        private int commits;

        private void install(BondedMiniwyvernExtensionDocument document, long revision) {
            rawPayload = CODEC.encode(document);
            this.revision = revision;
        }

        private BondedMiniwyvernExtensionDocument document() {
            return CODEC.decode(rawPayload);
        }

        private void externalWrite(BondedMiniwyvernExtensionDocument document) {
            install(document, revision + 1L);
        }

        @Override
        public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                getMiniwyvernExtension(UUID ownerUuid, String profileId) {
            if (rawPayload == null) {
                return CompletableFuture.completedFuture(new BondedCompanionResult<>(
                        BondedCompanionResultCode.NOT_FOUND, null, "missing"));
            }
            return CompletableFuture.completedFuture(success(data(rawPayload, revision)));
        }

        @Override
        public CompletionStage<BondedCompanionResult<BondedCompanionExtensionData>>
                compareAndSetMiniwyvernExtension(
                        UUID ownerUuid, String profileId, String idempotencyKey,
                        String jsonPayload, long expectedRevision) {
            Operation prior = operations.get(idempotencyKey);
            if (prior != null) {
                boolean exact = prior.payload.equals(jsonPayload)
                        && prior.expectedRevision == expectedRevision;
                return CompletableFuture.completedFuture(exact
                        ? success(data(prior.payload, prior.resultingRevision))
                        : conflict());
            }
            if (revision != expectedRevision) {
                operations.put(idempotencyKey,
                        new Operation(jsonPayload, expectedRevision, -1L));
                return CompletableFuture.completedFuture(conflict());
            }
            revision++;
            rawPayload = jsonPayload;
            commits++;
            operations.put(idempotencyKey,
                    new Operation(jsonPayload, expectedRevision, revision));
            if (failNextResponseAfterCommit) {
                failNextResponseAfterCommit = false;
                return CompletableFuture.failedFuture(
                        new IllegalStateException("lost response"));
            }
            return CompletableFuture.completedFuture(success(data(rawPayload, revision)));
        }

        private static BondedCompanionResult<BondedCompanionExtensionData> conflict() {
            return new BondedCompanionResult<>(
                    BondedCompanionResultCode.REVISION_CONFLICT, null, "conflict");
        }

        private static BondedCompanionResult<BondedCompanionExtensionData> success(
                BondedCompanionExtensionData data) {
            return new BondedCompanionResult<>(BondedCompanionResultCode.SUCCESS, data, null);
        }

        private static BondedCompanionExtensionData data(String payload, long revision) {
            return new BondedCompanionExtensionData(
                    new BondedCompanionExtensionDataKey(
                            OWNER, PROFILE, BondedMiniwyvernExtensionDocument.NAMESPACE),
                    payload, revision, 10L);
        }

        private record Operation(String payload, long expectedRevision, long resultingRevision) {
        }
    }
}
