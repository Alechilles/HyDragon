package com.alechilles.hydragon.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class HyDragonInteractionCodecTest {
    @Test
    void exposesExactAssetTypeIds() {
        assertEquals("HyDragonSoulBond", HyDragonSoulBondInteraction.TYPE_ID);
        assertEquals("HyDragonMiniwyvernAttune", HyDragonMiniwyvernAttuneInteraction.TYPE_ID);
        assertEquals("HyDragonRepairBondedStone", HyDragonRepairBondedStoneInteraction.TYPE_ID);
    }

    @Test
    void attunementCodecReadsRequiredArchetypeId() throws IOException {
        ExtraInfo extra = new ExtraInfo();
        HyDragonMiniwyvernAttuneInteraction interaction;
        try (RawJsonReader reader = new RawJsonReader("{\"ArchetypeId\":\"Fire\"}".toCharArray())) {
            interaction = HyDragonMiniwyvernAttuneInteraction.CODEC.decodeJson(reader, extra);
        }

        assertEquals("fire", interaction.getArchetypeId());
        assertTrue(interaction.isRequestValid());
        assertTrue(extra.getUnknownKeys().isEmpty());
    }

    @Test
    void missingAttunementArchetypeFailsBeforeDispatch() throws IOException {
        HyDragonMiniwyvernAttuneInteraction interaction;
        try (RawJsonReader reader = new RawJsonReader("{}".toCharArray())) {
            interaction = HyDragonMiniwyvernAttuneInteraction.CODEC.decodeJson(reader, new ExtraInfo());
        }

        assertFalse(interaction.isRequestValid());
    }
}
