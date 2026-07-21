package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.ProfileDataApi;
import com.alechilles.alecstamework.api.ProfileDataCompareAndSetRequest;
import com.alechilles.alecstamework.api.ProfileDataCompareAndSetResult;
import com.alechilles.alecstamework.api.ProfileDataEntryView;
import com.alechilles.alecstamework.api.ProfileDataOperationStatus;
import com.alechilles.alecstamework.api.ProfileDataOperationView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class TameworkMiniwyvernAbilityStateRepositoryTest {
    @Test
    void missingStateIsCreatedWithRevisionFencedCas() {
        MemoryProfileData data = new MemoryProfileData();
        TameworkMiniwyvernAbilityStateRepository repository =
                new TameworkMiniwyvernAbilityStateRepository(data);
        MiniwyvernAbilityState expected = MiniwyvernAbilityState.empty("fire", 100L);

        assertEquals(MiniwyvernAbilityStateRepository.Status.MISSING,
                repository.load("profile-1").status());
        assertTrue(repository.save("profile-1", expected));

        MiniwyvernAbilityStateRepository.LoadResult loaded = repository.load("profile-1");
        assertEquals(MiniwyvernAbilityStateRepository.Status.LOADED, loaded.status());
        assertEquals(expected, loaded.state());
        assertEquals(1L, data.entry("profile-1").orElseThrow().revision());
    }

    @Test
    void corruptStateAndSaveWithoutObservedRevisionFailClosed() {
        MemoryProfileData data = new MemoryProfileData();
        data.externalWrite("profile-1", "{}");
        TameworkMiniwyvernAbilityStateRepository repository =
                new TameworkMiniwyvernAbilityStateRepository(data);

        assertEquals(MiniwyvernAbilityStateRepository.Status.UNAVAILABLE,
                repository.load("profile-1").status());
        assertFalse(repository.save("profile-1", MiniwyvernAbilityState.empty("fire", 100L)));
        assertEquals("{}", data.entry("profile-1").orElseThrow().jsonPayload());
    }

    @Test
    void concurrentWriterCannotBeOverwrittenByAStaleAbilityTick() {
        MemoryProfileData data = new MemoryProfileData();
        data.externalWrite("profile-1", json(MiniwyvernAbilityState.empty("fire", 100L)));
        TameworkMiniwyvernAbilityStateRepository repository =
                new TameworkMiniwyvernAbilityStateRepository(data);
        assertEquals(MiniwyvernAbilityStateRepository.Status.LOADED,
                repository.load("profile-1").status());
        MiniwyvernAbilityState concurrent = MiniwyvernAbilityState.empty("ice", 200L);
        data.externalWrite("profile-1", json(concurrent));

        assertFalse(repository.save("profile-1", MiniwyvernAbilityState.empty("wind", 300L)));
        assertEquals(json(concurrent), data.entry("profile-1").orElseThrow().jsonPayload());
    }

    @Test
    void retryFindsCommittedOperationAfterLostCasResponse() {
        MemoryProfileData data = new MemoryProfileData();
        data.failNextResponseAfterCommit = true;
        TameworkMiniwyvernAbilityStateRepository repository =
                new TameworkMiniwyvernAbilityStateRepository(data);
        MiniwyvernAbilityState expected = MiniwyvernAbilityState.empty("nature", 100L);
        assertEquals(MiniwyvernAbilityStateRepository.Status.MISSING,
                repository.load("profile-1").status());

        assertFalse(repository.save("profile-1", expected));
        assertTrue(repository.save("profile-1", expected));
        assertEquals(1, data.commits);
        assertEquals(json(expected), data.entry("profile-1").orElseThrow().jsonPayload());
    }

    private static String json(MiniwyvernAbilityState state) {
        return new com.google.gson.Gson().toJson(state);
    }

    private static final class MemoryProfileData implements ProfileDataApi {
        private final Map<String, ProfileDataEntryView> values = new LinkedHashMap<>();
        private final Map<String, ProfileDataOperationView> operations = new LinkedHashMap<>();
        private boolean failNextResponseAfterCommit;
        private int commits;

        Optional<ProfileDataEntryView> entry(String profileId) {
            return Optional.ofNullable(values.get(profileId));
        }

        void externalWrite(String profileId, String payload) {
            long revision = entry(profileId).map(ProfileDataEntryView::revision).orElse(0L) + 1L;
            values.put(profileId, new ProfileDataEntryView(profileId, "hydragon",
                    "miniwyvern_ability_state", revision, payload, revision));
        }

        @Override public Optional<String> get(String profileId, String namespace, String key) {
            return entry(profileId).map(ProfileDataEntryView::jsonPayload);
        }

        @Override public Map<String, String> list(String profileId, String namespace) { return Map.of(); }

        @Override public boolean put(String profileId, String namespace, String key, String value) {
            throw new AssertionError("unfenced put must not be used");
        }

        @Override public boolean delete(String profileId, String namespace, String key) { return false; }

        @Override public Optional<ProfileDataEntryView> getVersioned(
                String profileId, String namespace, String key) {
            return entry(profileId);
        }

        @Override public CompletionStage<Optional<ProfileDataOperationView>> findOperation(
                String namespace, String idempotencyKey) {
            return CompletableFuture.completedFuture(Optional.ofNullable(operations.get(idempotencyKey)));
        }

        @Override public CompletionStage<ProfileDataCompareAndSetResult> compareAndSet(
                ProfileDataCompareAndSetRequest request) {
            ProfileDataOperationView prior = operations.get(request.idempotencyKey());
            if (prior != null) return CompletableFuture.completedFuture(committed(prior));
            long currentRevision = entry(request.profileId())
                    .map(ProfileDataEntryView::revision).orElse(0L);
            if (currentRevision != request.expectedRevision()) {
                ProfileDataOperationView denied = operation(
                        request, ProfileDataOperationStatus.TERMINAL_DENIED, -1L);
                operations.put(request.idempotencyKey(), denied);
                return CompletableFuture.completedFuture(new ProfileDataCompareAndSetResult(
                        ProfileDataCompareAndSetResult.Status.TERMINAL_DENIED,
                        "revision-conflict", denied, null));
            }
            long resulting = currentRevision + 1L;
            ProfileDataEntryView entry = new ProfileDataEntryView(
                    request.profileId(), request.namespace(), request.key(), resulting,
                    request.jsonPayload(), resulting);
            values.put(request.profileId(), entry);
            ProfileDataOperationView operation = operation(
                    request, ProfileDataOperationStatus.COMMITTED, resulting);
            operations.put(request.idempotencyKey(), operation);
            commits++;
            if (failNextResponseAfterCommit) {
                failNextResponseAfterCommit = false;
                return CompletableFuture.failedFuture(new IllegalStateException("lost response"));
            }
            return CompletableFuture.completedFuture(new ProfileDataCompareAndSetResult(
                    ProfileDataCompareAndSetResult.Status.COMMITTED,
                    "committed", operation, entry));
        }

        private ProfileDataCompareAndSetResult committed(ProfileDataOperationView operation) {
            return new ProfileDataCompareAndSetResult(
                    ProfileDataCompareAndSetResult.Status.COMMITTED, "replayed", operation,
                    values.get(operation.profileId()));
        }

        private static ProfileDataOperationView operation(
                ProfileDataCompareAndSetRequest request,
                ProfileDataOperationStatus status,
                long resultingRevision) {
            return new ProfileDataOperationView(
                    UUID.randomUUID(), request.namespace(), request.idempotencyKey(),
                    request.profileId(), request.key(), request.expectedRevision(), resultingRevision,
                    "fingerprint", status, status.name().toLowerCase(), 1L);
        }
    }
}
