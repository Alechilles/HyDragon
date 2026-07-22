package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DraconicAltarAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));

    @Test
    void altarAnimationRunsAtQuarterOfItsOriginalPlaybackRate() throws Exception {
        String animation = read("Common/Blocks/HyDragon/Draconic_Altar/Draconic_Altar_Idle.blockyanim");

        assertTrue(animation.contains("\"duration\": 228"));
        assertTrue(animation.contains("\"time\": 120"));
        assertTrue(animation.contains("\"time\": 152"));
        assertTrue(animation.contains("\"time\": 228"));
    }

    @Test
    void altarUsesOneBlockCollisionAndFullbrightEnergyOverlays() throws Exception {
        String item = read("Server/Item/Items/Bench/Draconic_Altar.json");
        String model = read("Common/Blocks/HyDragon/Draconic_Altar/Draconic_Altar.blockymodel");

        assertTrue(item.contains("\"HitboxType\": \"Statue_Small\""));
        assertEquals(4, occurrences(model, "\"name\": \"Energy_"));
        assertEquals(4, occurrences(model, "\"type\": \"quad\""));
        assertTrue(occurrences(model, "\"shadingMode\": \"fullbright\"") >= 9);
        assertTrue(model.contains("\"offset\": {\"x\": 0, \"y\": 96}"));
        assertTrue(model.contains("\"offset\": {\"x\": 24, \"y\": 96}"));
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }
}
