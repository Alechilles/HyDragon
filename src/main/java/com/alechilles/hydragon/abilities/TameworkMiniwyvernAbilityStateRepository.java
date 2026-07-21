package com.alechilles.hydragon.abilities;

import com.alechilles.alecstamework.api.ProfileDataApi;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.Objects;

/** Stores namespaced ability state through Tamework's public Profile Data API. */
public final class TameworkMiniwyvernAbilityStateRepository implements MiniwyvernAbilityStateRepository {
    public static final String NAMESPACE = "hydragon";
    public static final String KEY = "miniwyvern_ability_state";
    private static final Gson GSON = new Gson();

    private final ProfileDataApi profileData;

    public TameworkMiniwyvernAbilityStateRepository(ProfileDataApi profileData) {
        this.profileData = Objects.requireNonNull(profileData, "profileData");
    }

    @Override
    public LoadResult load(String profileId) {
        String normalized = requiredText(profileId, "profileId");
        try {
            java.util.Optional<String> encoded = profileData.get(normalized, NAMESPACE, KEY);
            if (encoded.isEmpty()) return LoadResult.missing();
            MiniwyvernAbilityState decoded = decode(encoded.orElseThrow());
            return decoded == null ? LoadResult.unavailable() : LoadResult.loaded(decoded);
        } catch (RuntimeException failure) {
            return LoadResult.unavailable();
        }
    }

    @Override
    public boolean save(String profileId, MiniwyvernAbilityState state) {
        String normalized = requiredText(profileId, "profileId");
        Objects.requireNonNull(state, "state");
        try {
            return profileData.put(normalized, NAMESPACE, KEY, GSON.toJson(state));
        } catch (RuntimeException failure) {
            return false;
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
                    decoded.appliedSourceKeys(),
                    decoded.targetBySourceKey(),
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
