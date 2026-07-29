package com.alechilles.hydragon.bonded;

import com.alechilles.hydragon.abilities.MiniwyvernAbilityState;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable HyDragon domain data stored under one bonded Miniwyvern profile. */
public final class BondedMiniwyvernExtensionDocument {
    public static final String NAMESPACE = "Alechilles:HyDragon";
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPANION_KIND = "SOULBOUND_MINIWYVERN";

    private final String speciesId;
    private final MiniwyvernAbilityState abilityState;
    private final BondedExtensionJsonValue progression;
    private final Map<String, BondedExtensionJsonValue> unknownTopLevelFields;
    private final Map<String, BondedExtensionJsonValue> unknownAbilityStateFields;

    BondedMiniwyvernExtensionDocument(
            String speciesId,
            MiniwyvernAbilityState abilityState,
            BondedExtensionJsonValue progression,
            Map<String, BondedExtensionJsonValue> unknownTopLevelFields,
            Map<String, BondedExtensionJsonValue> unknownAbilityStateFields) {
        this.speciesId = requiredText(speciesId, "speciesId");
        this.abilityState = Objects.requireNonNull(abilityState, "abilityState");
        this.progression = Objects.requireNonNull(progression, "progression");
        this.unknownTopLevelFields = immutableFields(
                unknownTopLevelFields, "unknownTopLevelFields");
        this.unknownAbilityStateFields = immutableFields(
                unknownAbilityStateFields, "unknownAbilityStateFields");
    }

    public static BondedMiniwyvernExtensionDocument wild(String speciesId, long nowEpochMillis) {
        return new BondedMiniwyvernExtensionDocument(
                speciesId,
                MiniwyvernAbilityState.empty("wild", nowEpochMillis),
                BondedExtensionJsonValue.emptyObject(),
                Map.of(),
                Map.of());
    }

    public String speciesId() {
        return speciesId;
    }

    public MiniwyvernAbilityState abilityState() {
        return abilityState;
    }

    public BondedExtensionJsonValue progression() {
        return progression;
    }

    public Map<String, BondedExtensionJsonValue> unknownTopLevelFields() {
        return unknownTopLevelFields;
    }

    public Map<String, BondedExtensionJsonValue> unknownAbilityStateFields() {
        return unknownAbilityStateFields;
    }

    /** Replaces non-authoritative scheduler cleanup state without selecting a live form. */
    public BondedMiniwyvernExtensionDocument withAbilityState(MiniwyvernAbilityState replacement) {
        MiniwyvernAbilityState state = Objects.requireNonNull(replacement, "replacement");
        return new BondedMiniwyvernExtensionDocument(
                speciesId,
                state,
                progression,
                unknownTopLevelFields,
                unknownAbilityStateFields);
    }

    /** Replaces domain-specific progression without disturbing lifecycle or scheduler cleanup state. */
    public BondedMiniwyvernExtensionDocument withProgression(BondedExtensionJsonValue replacement) {
        BondedExtensionJsonValue value = Objects.requireNonNull(replacement, "replacement");
        if (!value.isObject()) {
            throw new IllegalArgumentException("progression must be a JSON object");
        }
        return new BondedMiniwyvernExtensionDocument(
                speciesId, abilityState,
                value,
                unknownTopLevelFields,
                unknownAbilityStateFields);
    }

    private static Map<String, BondedExtensionJsonValue> immutableFields(
            Map<String, BondedExtensionJsonValue> fields,
            String name) {
        Objects.requireNonNull(fields, name);
        for (Map.Entry<String, BondedExtensionJsonValue> entry : fields.entrySet()) {
            requiredText(entry.getKey(), name + " key");
            Objects.requireNonNull(entry.getValue(), name + " value");
        }
        return Map.copyOf(fields);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }
}
