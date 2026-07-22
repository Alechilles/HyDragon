package com.alechilles.hydragon.abilities;

import com.alechilles.alecstamework.api.ProfileDataApi;
import com.alechilles.alecstamework.api.ProfileDataCompareAndSetRequest;
import com.alechilles.alecstamework.api.ProfileDataCompareAndSetResult;
import com.alechilles.alecstamework.api.ProfileDataEntryView;
import com.alechilles.alecstamework.api.ProfileDataOperationStatus;
import com.alechilles.alecstamework.api.ProfileDataOperationView;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Stores namespaced ability state through Tamework's public Profile Data API. */
public final class TameworkMiniwyvernAbilityStateRepository implements MiniwyvernAbilityStateRepository {
    public static final String NAMESPACE = "hydragon";
    public static final String KEY = "miniwyvern_ability_state";
    private static final Gson GSON = new Gson();

    private final ProfileDataApi profileData;
    private final ConcurrentHashMap<String, Long> observedRevisions = new ConcurrentHashMap<>();

    public TameworkMiniwyvernAbilityStateRepository(ProfileDataApi profileData) {
        this.profileData = Objects.requireNonNull(profileData, "profileData");
    }

    @Override
    public synchronized LoadResult load(String profileId) {
        String normalized = requiredText(profileId, "profileId");
        try {
            Optional<ProfileDataEntryView> versioned = profileData.getVersioned(normalized, NAMESPACE, KEY);
            if (versioned.isEmpty()) {
                observedRevisions.put(normalized, ProfileDataCompareAndSetRequest.MISSING_REVISION);
                return LoadResult.missing();
            }
            ProfileDataEntryView entry = versioned.orElseThrow();
            if (!matches(entry, normalized)) return LoadResult.unavailable();
            MiniwyvernAbilityState decoded = decode(entry.jsonPayload());
            if (decoded != null) observedRevisions.put(normalized, entry.revision());
            return decoded == null ? LoadResult.unavailable() : LoadResult.loaded(decoded);
        } catch (RuntimeException failure) {
            return LoadResult.unavailable();
        }
    }

    @Override
    public synchronized boolean save(String profileId, MiniwyvernAbilityState state) {
        String normalized = requiredText(profileId, "profileId");
        Objects.requireNonNull(state, "state");
        Long expectedRevision = observedRevisions.get(normalized);
        if (expectedRevision == null) return false;
        String payload = GSON.toJson(state);
        String operationId = operationId(normalized, expectedRevision, payload);
        try {
            Optional<ProfileDataOperationView> prior = profileData.findOperation(NAMESPACE, operationId)
                    .toCompletableFuture().join();
            if (prior.isPresent()) {
                ProfileDataOperationView operation = prior.orElseThrow();
                if (!matches(operation, normalized, expectedRevision, operationId)) return false;
                if (operation.status() == ProfileDataOperationStatus.COMMITTED) {
                    return acceptCommittedReplay(normalized, payload, operation);
                }
                if (operation.status() == ProfileDataOperationStatus.TERMINAL_DENIED
                        || operation.status() == ProfileDataOperationStatus.QUARANTINED) {
                    return false;
                }
            }
            ProfileDataCompareAndSetResult result = profileData.compareAndSet(
                    new ProfileDataCompareAndSetRequest(
                            normalized, NAMESPACE, KEY, expectedRevision, operationId, payload))
                    .toCompletableFuture().join();
            if (result.status() == ProfileDataCompareAndSetResult.Status.COMMITTED) {
                ProfileDataOperationView operation = result.durableOperation().orElseThrow();
                ProfileDataEntryView entry = result.committedEntry().orElseThrow();
                if (!matches(operation, normalized, expectedRevision, operationId)
                        || !matches(entry, normalized) || !payload.equals(entry.jsonPayload())
                        || entry.revision() != expectedRevision + 1L) return false;
                observedRevisions.put(normalized, entry.revision());
                return true;
            }
            if (result.status() == ProfileDataCompareAndSetResult.Status.TERMINAL_DENIED) {
                // A lost response or concurrent identical writer is successful only when the
                // authoritative value proves that the exact desired payload won.
                Optional<ProfileDataEntryView> current = profileData.getVersioned(normalized, NAMESPACE, KEY);
                if (current.isPresent() && matches(current.orElseThrow(), normalized)) {
                    ProfileDataEntryView entry = current.orElseThrow();
                    observedRevisions.put(normalized, entry.revision());
                    return payload.equals(entry.jsonPayload());
                }
            }
            return false;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private boolean acceptCommittedReplay(
            String profileId,
            String payload,
            ProfileDataOperationView operation) {
        Optional<ProfileDataEntryView> current = profileData.getVersioned(profileId, NAMESPACE, KEY);
        if (current.isEmpty()) return false;
        ProfileDataEntryView entry = current.orElseThrow();
        if (!matches(entry, profileId)
                || entry.revision() != operation.resultingRevision()
                || !payload.equals(entry.jsonPayload())) return false;
        observedRevisions.put(profileId, entry.revision());
        return true;
    }

    private static boolean matches(ProfileDataEntryView entry, String profileId) {
        return entry.profileId().equals(profileId)
                && entry.namespace().equals(NAMESPACE)
                && entry.key().equals(KEY);
    }

    private static boolean matches(
            ProfileDataOperationView operation,
            String profileId,
            long expectedRevision,
            String operationId) {
        return operation.namespace().equals(NAMESPACE)
                && operation.idempotencyKey().equals(operationId)
                && operation.key().equals(KEY)
                && operation.profileId().equals(profileId)
                && operation.expectedRevision() == expectedRevision;
    }

    private static String operationId(String profileId, long expectedRevision, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((profileId + '\n' + expectedRevision + '\n' + payload)
                    .getBytes(StandardCharsets.UTF_8));
            return "hydragon:ability-state:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private MiniwyvernAbilityState decode(String json) {
        try {
            MiniwyvernAbilityState decoded = GSON.fromJson(json, MiniwyvernAbilityState.class);
            if (decoded == null) return null;
            // Gson may allocate records without invoking their compact constructor. Reconstruct to
            // enforce every persisted invariant before the scheduler trusts the state.
            return new MiniwyvernAbilityState(
                    decoded.schemaVersion(),
                    decoded.archetypeId(),
                    decoded.cooldownUntilByAbility(),
                    decoded.iceBuildupByTarget(),
                    decoded.controlImmunityUntilByTarget(),
                    decoded.iceTargetUpdatedAtByTarget(),
                    decoded.appliedSourceKeys(),
                    decoded.targetBySourceKey(),
                    decoded.sourceExpiresAtBySourceKey(),
                    decoded.updatedAtEpochMillis());
        } catch (JsonParseException | IllegalArgumentException | NullPointerException failure) {
            return null;
        }
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
