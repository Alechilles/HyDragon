package com.alechilles.hydragon.bonded;

import com.alechilles.hydragon.abilities.MiniwyvernAbilityState;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Deterministic JSON codec for HyDragon's bonded Miniwyvern extension document. */
public final class BondedMiniwyvernExtensionCodec {
    private static final Gson GSON = new Gson();
    private static final Set<String> DOCUMENT_FIELDS = Set.of(
            "schemaVersion",
            "companionKind",
            "speciesId",
            "abilityState",
            "progression");
    private static final Set<String> ABILITY_FIELDS = Set.of(
            "schemaVersion",
            "formId",
            "cooldownUntilByAbility",
            "iceBuildupByTarget",
            "controlImmunityUntilByTarget",
            "iceTargetUpdatedAtByTarget",
            "appliedSourceKeys",
            "targetBySourceKey",
            "sourceExpiresAtBySourceKey",
            "updatedAtEpochMillis");

    public String encode(BondedMiniwyvernExtensionDocument document) {
        Objects.requireNonNull(document, "document");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", BondedMiniwyvernExtensionDocument.SCHEMA_VERSION);
        root.addProperty("companionKind", BondedMiniwyvernExtensionDocument.COMPANION_KIND);
        root.addProperty("speciesId", document.speciesId());
        JsonObject abilityState = encodeAbilityState(document.abilityState());
        addUnknownFields(abilityState, document.unknownAbilityStateFields(), ABILITY_FIELDS);
        root.add("abilityState", abilityState);
        root.add("progression", document.progression().toJsonElement());
        addUnknownFields(root, document.unknownTopLevelFields(), DOCUMENT_FIELDS);
        return BondedExtensionJsonValue.from(root).json();
    }

    public BondedMiniwyvernExtensionDocument decode(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(Objects.requireNonNull(json, "json"));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Bonded Miniwyvern extension must be a JSON object");
            }
            JsonObject root = parsed.getAsJsonObject();
            long schemaVersion = requiredNonNegativeLong(root, "schemaVersion");
            if (schemaVersion != BondedMiniwyvernExtensionDocument.SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported bonded Miniwyvern schema " + schemaVersion);
            }
            String companionKind = requiredString(root, "companionKind");
            if (!BondedMiniwyvernExtensionDocument.COMPANION_KIND.equals(companionKind)) {
                throw new IllegalArgumentException("Unexpected bonded companion kind " + companionKind);
            }
            String speciesId = requiredString(root, "speciesId");
            JsonObject abilityJson = requiredObject(root, "abilityState");
            MiniwyvernAbilityState abilityState = decodeAbilityState(abilityJson);
            JsonObject progression = requiredObject(root, "progression");
            return new BondedMiniwyvernExtensionDocument(
                    speciesId, abilityState,
                    BondedExtensionJsonValue.from(progression),
                    unknownFields(root, DOCUMENT_FIELDS),
                    unknownFields(abilityJson, ABILITY_FIELDS));
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (JsonParseException | NullPointerException | ClassCastException failure) {
            throw new IllegalArgumentException("Invalid bonded Miniwyvern extension", failure);
        }
    }

    private static MiniwyvernAbilityState decodeAbilityState(JsonObject json) {
        MiniwyvernAbilityState decoded = GSON.fromJson(json, MiniwyvernAbilityState.class);
        if (decoded == null) {
            throw new IllegalArgumentException("abilityState is required");
        }
        return new MiniwyvernAbilityState(
                decoded.schemaVersion(),
                decoded.formId(),
                decoded.cooldownUntilByAbility(),
                decoded.iceBuildupByTarget(),
                decoded.controlImmunityUntilByTarget(),
                decoded.iceTargetUpdatedAtByTarget(),
                decoded.appliedSourceKeys(),
                decoded.targetBySourceKey(),
                decoded.sourceExpiresAtBySourceKey(),
                decoded.updatedAtEpochMillis());
    }

    private static JsonObject encodeAbilityState(MiniwyvernAbilityState state) {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", state.schemaVersion());
        json.addProperty("formId", state.formId());
        json.add("cooldownUntilByAbility", stringLongMap(state.cooldownUntilByAbility()));
        json.add("iceBuildupByTarget", uuidDoubleMap(state.iceBuildupByTarget()));
        json.add("controlImmunityUntilByTarget", uuidLongMap(state.controlImmunityUntilByTarget()));
        json.add("iceTargetUpdatedAtByTarget", uuidLongMap(state.iceTargetUpdatedAtByTarget()));
        JsonArray sources = new JsonArray();
        state.appliedSourceKeys().stream().sorted().forEach(sources::add);
        json.add("appliedSourceKeys", sources);
        JsonObject targets = new JsonObject();
        new TreeMap<>(state.targetBySourceKey()).forEach(
                (source, target) -> targets.addProperty(source, target.toString()));
        json.add("targetBySourceKey", targets);
        json.add("sourceExpiresAtBySourceKey", stringLongMap(state.sourceExpiresAtBySourceKey()));
        json.addProperty("updatedAtEpochMillis", state.updatedAtEpochMillis());
        return json;
    }

    private static JsonObject stringLongMap(Map<String, Long> values) {
        JsonObject json = new JsonObject();
        new TreeMap<>(values).forEach(json::addProperty);
        return json;
    }

    private static JsonObject uuidLongMap(Map<UUID, Long> values) {
        TreeMap<String, Long> ordered = new TreeMap<>();
        values.forEach((key, value) -> ordered.put(key.toString(), value));
        JsonObject json = new JsonObject();
        ordered.forEach(json::addProperty);
        return json;
    }

    private static JsonObject uuidDoubleMap(Map<UUID, Double> values) {
        TreeMap<String, Double> ordered = new TreeMap<>();
        values.forEach((key, value) -> ordered.put(key.toString(), value));
        JsonObject json = new JsonObject();
        ordered.forEach(json::addProperty);
        return json;
    }

    private static Map<String, BondedExtensionJsonValue> unknownFields(
            JsonObject json,
            Set<String> knownFields) {
        Map<String, BondedExtensionJsonValue> unknown = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!knownFields.contains(entry.getKey())) {
                unknown.put(entry.getKey(), BondedExtensionJsonValue.from(entry.getValue()));
            }
        }
        return Map.copyOf(unknown);
    }

    private static void addUnknownFields(
            JsonObject destination,
            Map<String, BondedExtensionJsonValue> unknownFields,
            Set<String> knownFields) {
        for (Map.Entry<String, BondedExtensionJsonValue> entry : unknownFields.entrySet()) {
            if (knownFields.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unknown field conflicts with " + entry.getKey());
            }
            destination.add(entry.getKey(), entry.getValue().toJsonElement());
        }
    }

    private static JsonObject requiredObject(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        String text = value.getAsString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }

    private static Optional<String> optionalNullableString(JsonObject json, String field) {
        if (!json.has(field)) {
            throw new IllegalArgumentException(field + " is required");
        }
        JsonElement value = json.get(field);
        if (value.isJsonNull()) {
            return Optional.empty();
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string or null");
        }
        String text = value.getAsString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return Optional.of(text);
    }

    private static long requiredNonNegativeLong(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        BigDecimal number = primitive.getAsBigDecimal().stripTrailingZeros();
        if (number.scale() > 0) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        long result;
        try {
            result = number.longValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(field + " is outside the supported range", failure);
        }
        if (result < 0L) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return result;
    }
}
