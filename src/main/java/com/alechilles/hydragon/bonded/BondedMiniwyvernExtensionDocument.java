package com.alechilles.hydragon.bonded;

import com.alechilles.hydragon.abilities.MiniwyvernAbilityState;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable HyDragon domain data stored under one bonded Miniwyvern profile. */
public final class BondedMiniwyvernExtensionDocument {
    public static final String NAMESPACE = "Alechilles:HyDragon";
    public static final int SCHEMA_VERSION = 1;
    public static final String COMPANION_KIND = "SOULBOUND_MINIWYVERN";

    private final String speciesId;
    private final String archetypeId;
    private final long archetypeRevision;
    private final Optional<String> lastAttunementOperationId;
    private final MiniwyvernAbilityState abilityState;
    private final BondedExtensionJsonValue progression;
    private final Map<String, BondedExtensionJsonValue> unknownTopLevelFields;
    private final Map<String, BondedExtensionJsonValue> unknownAbilityStateFields;

    BondedMiniwyvernExtensionDocument(
            String speciesId,
            String archetypeId,
            long archetypeRevision,
            Optional<String> lastAttunementOperationId,
            MiniwyvernAbilityState abilityState,
            BondedExtensionJsonValue progression,
            Map<String, BondedExtensionJsonValue> unknownTopLevelFields,
            Map<String, BondedExtensionJsonValue> unknownAbilityStateFields) {
        this.speciesId = requiredText(speciesId, "speciesId");
        this.archetypeId = requiredText(archetypeId, "archetypeId").toLowerCase(Locale.ROOT);
        if (archetypeRevision < 0L) {
            throw new IllegalArgumentException("archetypeRevision must not be negative");
        }
        this.archetypeRevision = archetypeRevision;
        Optional<String> operationId = Objects.requireNonNull(
                lastAttunementOperationId, "lastAttunementOperationId");
        this.lastAttunementOperationId = operationId.map(
                value -> requiredText(value, "lastAttunementOperationId"));
        this.abilityState = Objects.requireNonNull(abilityState, "abilityState");
        this.progression = Objects.requireNonNull(progression, "progression");
        this.unknownTopLevelFields = immutableFields(
                unknownTopLevelFields, "unknownTopLevelFields");
        this.unknownAbilityStateFields = immutableFields(
                unknownAbilityStateFields, "unknownAbilityStateFields");
    }

    public static BondedMiniwyvernExtensionDocument neutral(String speciesId, long nowEpochMillis) {
        return new BondedMiniwyvernExtensionDocument(
                speciesId,
                "neutral",
                0L,
                Optional.empty(),
                MiniwyvernAbilityState.empty("neutral", nowEpochMillis),
                BondedExtensionJsonValue.emptyObject(),
                Map.of(),
                Map.of());
    }

    public String speciesId() {
        return speciesId;
    }

    public String archetypeId() {
        return archetypeId;
    }

    public long archetypeRevision() {
        return archetypeRevision;
    }

    public Optional<String> lastAttunementOperationId() {
        return lastAttunementOperationId;
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

    /**
     * Changes attunement while retaining the old scheduler state as explicit cleanup evidence.
     * The resulting archetype mismatch is intentional: the ability runtime must remove every
     * source tracked by the old state before replacing it with target-archetype state.
     */
    public BondedMiniwyvernExtensionDocument attune(String targetArchetypeId, String operationId) {
        String target = requiredText(targetArchetypeId, "targetArchetypeId").toLowerCase(Locale.ROOT);
        String operation = requiredText(operationId, "operationId");
        if (hasAttunementEvidence(operation, target)) {
            return this;
        }
        if (target.equals(archetypeId)) {
            throw new IllegalArgumentException("Miniwyvern is already attuned to " + target);
        }
        if (archetypeRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("archetypeRevision is exhausted");
        }
        return new BondedMiniwyvernExtensionDocument(
                speciesId,
                target,
                archetypeRevision + 1L,
                Optional.of(operation),
                abilityState,
                progression,
                unknownTopLevelFields,
                unknownAbilityStateFields);
    }

    /** Whether old source-keyed effects must be cleaned before the next ability-state write. */
    public boolean abilityStateCleanupPending() {
        return !archetypeId.equals(abilityState.archetypeId());
    }

    public boolean hasAttunementEvidence(String operationId, String targetArchetypeId) {
        if (operationId == null || targetArchetypeId == null) {
            return false;
        }
        String operation = operationId.trim();
        String target = targetArchetypeId.trim().toLowerCase(Locale.ROOT);
        return !operation.isEmpty()
                && !target.isEmpty()
                && lastAttunementOperationId.filter(operation::equals).isPresent()
                && archetypeId.equals(target);
    }

    /** Replaces scheduler state without discarding attunement, progression, or future fields. */
    public BondedMiniwyvernExtensionDocument withAbilityState(MiniwyvernAbilityState replacement) {
        MiniwyvernAbilityState state = Objects.requireNonNull(replacement, "replacement");
        if (!archetypeId.equals(state.archetypeId())) {
            throw new IllegalArgumentException("Ability state archetype does not match current attunement");
        }
        return new BondedMiniwyvernExtensionDocument(
                speciesId,
                archetypeId,
                archetypeRevision,
                lastAttunementOperationId,
                state,
                progression,
                unknownTopLevelFields,
                unknownAbilityStateFields);
    }

    /** Replaces domain-specific progression without disturbing lifecycle or scheduler evidence. */
    public BondedMiniwyvernExtensionDocument withProgression(BondedExtensionJsonValue replacement) {
        BondedExtensionJsonValue value = Objects.requireNonNull(replacement, "replacement");
        if (!value.isObject()) {
            throw new IllegalArgumentException("progression must be a JSON object");
        }
        return new BondedMiniwyvernExtensionDocument(
                speciesId,
                archetypeId,
                archetypeRevision,
                lastAttunementOperationId,
                abilityState,
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
