package com.alechilles.hydragon.bonded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.hydragon.abilities.MiniwyvernAbilityState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class BondedMiniwyvernExtensionCodecTest {
    private static final BondedMiniwyvernExtensionCodec CODEC = new BondedMiniwyvernExtensionCodec();

    @Test
    void neutralInitializationProducesCanonicalVersionedDocument() {
        BondedMiniwyvernExtensionDocument document =
                BondedMiniwyvernExtensionDocument.neutral("hydragon:miniwyvern", 125L);

        assertEquals(BondedMiniwyvernExtensionDocument.NAMESPACE, "Alechilles:HyDragon");
        assertEquals("hydragon:miniwyvern", document.speciesId());
        assertEquals("neutral", document.archetypeId());
        assertEquals(0L, document.archetypeRevision());
        assertTrue(document.lastAttunementOperationId().isEmpty());
        assertEquals("neutral", document.abilityState().archetypeId());
        assertEquals(125L, document.abilityState().updatedAtEpochMillis());
        assertEquals("{}", document.progression().json());
        assertEquals(
                "{\"abilityState\":{\"appliedSourceKeys\":[],\"archetypeId\":\"neutral\","
                        + "\"controlImmunityUntilByTarget\":{},\"cooldownUntilByAbility\":{},"
                        + "\"iceBuildupByTarget\":{},\"iceTargetUpdatedAtByTarget\":{},"
                        + "\"schemaVersion\":2,\"sourceExpiresAtBySourceKey\":{},"
                        + "\"targetBySourceKey\":{},\"updatedAtEpochMillis\":125},"
                        + "\"archetypeId\":\"neutral\",\"archetypeRevision\":0,"
                        + "\"companionKind\":\"SOULBOUND_MINIWYVERN\","
                        + "\"lastAttunementOperationId\":null,\"progression\":{},"
                        + "\"schemaVersion\":1,\"speciesId\":\"hydragon:miniwyvern\"}",
                CODEC.encode(document));
    }

    @Test
    void roundTripPreservesUnknownTopLevelProgressionAndAbilityFields() {
        BondedMiniwyvernExtensionDocument decoded = CODEC.decode(existingPayload());
        JsonObject encoded = JsonParser.parseString(CODEC.encode(decoded)).getAsJsonObject();

        assertEquals(
                JsonParser.parseString("{\"enabled\":true,\"nested\":{\"a\":1,\"z\":2}}"),
                encoded.get("futureTop"));
        assertEquals(
                JsonParser.parseString("{\"future\":{\"a\":\"first\",\"z\":\"last\"},\"level\":3}"),
                encoded.get("progression"));
        assertEquals(
                JsonParser.parseString("{\"mode\":\"burst\",\"nested\":{\"a\":1,\"z\":2}}"),
                encoded.getAsJsonObject("abilityState").get("futureAbility"));
        assertEquals("attune-17", decoded.lastAttunementOperationId().orElseThrow());
        assertThrows(
                UnsupportedOperationException.class,
                () -> decoded.unknownTopLevelFields().put(
                        "mutation", BondedExtensionJsonValue.emptyObject()));
    }

    @Test
    void decodeRejectsWrongSchemaKindAndBlankArchetype() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CODEC.decode(existingPayload().replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
        assertThrows(
                IllegalArgumentException.class,
                () -> CODEC.decode(existingPayload().replace(
                        "\"companionKind\":\"SOULBOUND_MINIWYVERN\"",
                        "\"companionKind\":\"FULL_DRAGON\"")));
        JsonObject blankArchetype = JsonParser.parseString(existingPayload()).getAsJsonObject();
        blankArchetype.addProperty("archetypeId", "  ");
        assertThrows(
                IllegalArgumentException.class,
                () -> CODEC.decode(blankArchetype.toString()));
    }

    @Test
    void attunementMergeRetainsOldAbilityAndUnrelatedData() {
        BondedMiniwyvernExtensionDocument original = CODEC.decode(existingPayload());

        BondedMiniwyvernExtensionDocument attuned = original.attune("ICE", "attune-22");
        JsonObject encoded = JsonParser.parseString(CODEC.encode(attuned)).getAsJsonObject();

        assertEquals("ice", attuned.archetypeId());
        assertEquals(5L, attuned.archetypeRevision());
        assertEquals("attune-22", attuned.lastAttunementOperationId().orElseThrow());
        assertEquals("fire", attuned.abilityState().archetypeId());
        assertTrue(attuned.abilityStateCleanupPending());
        assertTrue(encoded.has("futureTop"));
        assertTrue(encoded.getAsJsonObject("progression").has("future"));
        assertTrue(encoded.getAsJsonObject("abilityState").has("futureAbility"));
    }

    @Test
    void exactAttunementEvidenceMakesReplayIdempotent() {
        BondedMiniwyvernExtensionDocument attuned =
                CODEC.decode(existingPayload()).attune("ice", "attune-22");

        assertTrue(attuned.hasAttunementEvidence("attune-22", "ice"));
        assertFalse(attuned.hasAttunementEvidence("attune-21", "ice"));
        assertFalse(attuned.hasAttunementEvidence("attune-22", "fire"));
        assertSame(attuned, attuned.attune("ice", "attune-22"));
    }

    @Test
    void abilityReplacementRetainsAttunementProgressionAndUnknownData() {
        BondedMiniwyvernExtensionDocument attuned =
                CODEC.decode(existingPayload()).attune("ice", "attune-22");
        MiniwyvernAbilityState replacement = MiniwyvernAbilityState.empty("ice", 200L);

        BondedMiniwyvernExtensionDocument replaced = attuned.withAbilityState(replacement);
        JsonObject encoded = JsonParser.parseString(CODEC.encode(replaced)).getAsJsonObject();

        assertEquals("ice", replaced.archetypeId());
        assertEquals(5L, replaced.archetypeRevision());
        assertEquals("attune-22", replaced.lastAttunementOperationId().orElseThrow());
        assertEquals(200L, replaced.abilityState().updatedAtEpochMillis());
        assertFalse(replaced.abilityStateCleanupPending());
        assertTrue(encoded.has("futureTop"));
        assertTrue(encoded.getAsJsonObject("progression").has("future"));
        assertTrue(encoded.getAsJsonObject("abilityState").has("futureAbility"));
        assertThrows(
                IllegalArgumentException.class,
                () -> attuned.withAbilityState(MiniwyvernAbilityState.empty("fire", 201L)));
    }

    @Test
    void semanticallyEqualInputsEncodeIdentically() {
        String reordered = existingPayload()
                .replace("{\"enabled\":true,\"nested\":{\"z\":2,\"a\":1}}",
                        "{\"nested\":{\"a\":1,\"z\":2},\"enabled\":true}")
                .replace("{\"level\":3,\"future\":{\"z\":\"last\",\"a\":\"first\"}}",
                        "{\"future\":{\"a\":\"first\",\"z\":\"last\"},\"level\":3}");

        assertEquals(
                CODEC.encode(CODEC.decode(existingPayload())),
                CODEC.encode(CODEC.decode(reordered)));
    }

    @Test
    void progressionReplacementIsPublicImmutableAndPreservesOtherDomains() {
        BondedMiniwyvernExtensionDocument original = CODEC.decode(existingPayload());
        BondedExtensionJsonValue progression = BondedExtensionJsonValue.parse(
                "{\"talents\":{\"z\":2,\"a\":1},\"xp\":90}");

        BondedMiniwyvernExtensionDocument replaced = original.withProgression(progression);
        JsonObject encoded = JsonParser.parseString(CODEC.encode(replaced)).getAsJsonObject();

        assertEquals("{\"talents\":{\"a\":1,\"z\":2},\"xp\":90}", replaced.progression().json());
        assertEquals("fire", replaced.archetypeId());
        assertEquals(4L, replaced.archetypeRevision());
        assertEquals("attune-17", replaced.lastAttunementOperationId().orElseThrow());
        assertEquals(125L, replaced.abilityState().updatedAtEpochMillis());
        assertTrue(encoded.has("futureTop"));
        assertTrue(encoded.getAsJsonObject("abilityState").has("futureAbility"));
        assertThrows(
                IllegalArgumentException.class,
                () -> original.withProgression(BondedExtensionJsonValue.parse("[1,2,3]")));
        assertThrows(
                IllegalArgumentException.class,
                () -> BondedExtensionJsonValue.parse("{not-json"));
    }

    private static String existingPayload() {
        return """
                {
                  "futureTop":{"enabled":true,"nested":{"z":2,"a":1}},
                  "progression":{"level":3,"future":{"z":"last","a":"first"}},
                  "lastAttunementOperationId":"attune-17",
                  "archetypeRevision":4,
                  "archetypeId":"fire",
                  "speciesId":"hydragon:miniwyvern",
                  "companionKind":"SOULBOUND_MINIWYVERN",
                  "schemaVersion":1,
                  "abilityState":{
                    "schemaVersion":2,
                    "archetypeId":"fire",
                    "cooldownUntilByAbility":{},
                    "iceBuildupByTarget":{},
                    "controlImmunityUntilByTarget":{},
                    "iceTargetUpdatedAtByTarget":{},
                    "appliedSourceKeys":[],
                    "targetBySourceKey":{},
                    "sourceExpiresAtBySourceKey":{},
                    "updatedAtEpochMillis":125,
                    "futureAbility":{"mode":"burst","nested":{"z":2,"a":1}}
                  }
                }
                """;
    }
}
