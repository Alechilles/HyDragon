package com.alechilles.hydragon.bonded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** The extension persists scheduler cleanup only; the live NPC role selects the form. */
final class BondedMiniwyvernExtensionCodecTest {
    @Test
    void wildInitializationDoesNotPersistAttunementAuthority() {
        BondedMiniwyvernExtensionDocument document =
                BondedMiniwyvernExtensionDocument.wild("hydragon:miniwyvern", 125L);
        JsonObject json = JsonParser.parseString(
                new BondedMiniwyvernExtensionCodec().encode(document)).getAsJsonObject();

        assertEquals("wild", json.getAsJsonObject("abilityState").get("formId").getAsString());
        assertFalse(json.has("archetypeId"));
        assertFalse(json.has("archetypeRevision"));
        assertFalse(json.has("lastAttunementOperationId"));
    }
}
