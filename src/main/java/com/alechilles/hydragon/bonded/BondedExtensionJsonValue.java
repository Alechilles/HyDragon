package com.alechilles.hydragon.bonded;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/** Immutable JSON fragment retained inside a HyDragon bonded-companion extension. */
public final class BondedExtensionJsonValue {
    private static final BondedExtensionJsonValue EMPTY_OBJECT = new BondedExtensionJsonValue("{}");

    private final String json;

    private BondedExtensionJsonValue(String json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    public static BondedExtensionJsonValue emptyObject() {
        return EMPTY_OBJECT;
    }

    public String json() {
        return json;
    }

    static BondedExtensionJsonValue from(JsonElement value) {
        return new BondedExtensionJsonValue(canonical(Objects.requireNonNull(value, "value")).toString());
    }

    JsonElement toJsonElement() {
        return JsonParser.parseString(json);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BondedExtensionJsonValue value && json.equals(value.json);
    }

    @Override
    public int hashCode() {
        return json.hashCode();
    }

    @Override
    public String toString() {
        return json;
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            ArrayList<Map.Entry<String, JsonElement>> entries =
                    new ArrayList<>(value.getAsJsonObject().entrySet());
            entries.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, JsonElement> entry : entries) {
                sorted.add(entry.getKey(), canonical(entry.getValue()));
            }
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray ordered = new JsonArray();
            for (JsonElement element : value.getAsJsonArray()) {
                ordered.add(canonical(element));
            }
            return ordered;
        }
        return value.deepCopy();
    }
}
