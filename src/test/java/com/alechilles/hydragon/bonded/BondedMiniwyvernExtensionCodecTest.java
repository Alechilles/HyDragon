package com.alechilles.hydragon.bonded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.hydragon.abilities.MiniwyvernAbilityState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Contract coverage for form-free bonded extension persistence. */
final class BondedMiniwyvernExtensionCodecTest {
    private static final BondedMiniwyvernExtensionCodec CODEC = new BondedMiniwyvernExtensionCodec();

    @Test
    void wildInitializationDoesNotPersistAttunementAuthority() {
        JsonObject json = encoded(CODEC.decode(payload()));

        assertEquals("wild", json.getAsJsonObject("abilityState").get("formId").getAsString());
        assertFalse(json.has("archetypeId"));
        assertFalse(json.has("archetypeRevision"));
        assertFalse(json.has("lastAttunementOperationId"));
    }

    @Test
    void roundTripPreservesUnknownFieldsAndProgression() {
        JsonObject encoded = encoded(CODEC.decode(payload()));

        assertEquals(JsonParser.parseString("{\"a\":1,\"z\":2}"), encoded.get("futureTop"));
        assertEquals(JsonParser.parseString("{\"level\":3}"), encoded.get("progression"));
        assertEquals(JsonParser.parseString("{\"mode\":\"burst\"}"),
                encoded.getAsJsonObject("abilityState").get("futureAbility"));
    }

    @Test
    void rejectsMissingFormSchedulerStateAndWrongCompanionKind() {
        assertThrows(IllegalArgumentException.class,
                () -> CODEC.decode(payload().replace("\"formId\":\"wild\",", "")));
        assertThrows(IllegalArgumentException.class,
                () -> CODEC.decode(payload().replace("SOULBOUND_MINIWYVERN", "FULL_DRAGON")));
    }

    @Test
    void semanticallyEquivalentInputsEncodeDeterministically() {
        assertEquals(CODEC.encode(CODEC.decode(payload())),
                CODEC.encode(CODEC.decode(payload().replace("\"futureTop\":{\"z\":2,\"a\":1}",
                        "\"futureTop\":{\"a\":1,\"z\":2}"))));
    }

    @Test
    void schedulerReplacementDoesNotSelectLiveFormOrDiscardOtherDomains() {
        BondedMiniwyvernExtensionDocument original = CODEC.decode(payload());
        MiniwyvernAbilityState replacement = MiniwyvernAbilityState.empty("fire", 200L);

        BondedMiniwyvernExtensionDocument replaced = original.withAbilityState(replacement);
        JsonObject encoded = encoded(replaced);

        assertEquals("fire", replaced.abilityState().formId());
        assertEquals(200L, replaced.abilityState().updatedAtEpochMillis());
        assertEquals(JsonParser.parseString("{\"level\":3}"), encoded.get("progression"));
        assertTrue(encoded.has("futureTop"));
        assertFalse(encoded.has("archetypeId"));
    }

    private static JsonObject encoded(BondedMiniwyvernExtensionDocument document) {
        return JsonParser.parseString(CODEC.encode(document)).getAsJsonObject();
    }

    private static String payload() {
        return """
                {"futureTop":{"z":2,"a":1},"progression":{"level":3},
                "schemaVersion":1,"companionKind":"SOULBOUND_MINIWYVERN",
                "speciesId":"hydragon:miniwyvern","abilityState":{"schemaVersion":2,
                "formId":"wild","cooldownUntilByAbility":{},"iceBuildupByTarget":{},
                "controlImmunityUntilByTarget":{},"iceTargetUpdatedAtByTarget":{},
                "appliedSourceKeys":[],"targetBySourceKey":{},"sourceExpiresAtBySourceKey":{},
                "updatedAtEpochMillis":125,"futureAbility":{"mode":"burst"}}}
                """;
    }
}
