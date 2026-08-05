package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DraconicAltarAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", ".")).resolve("src/main/resources");

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

    @Test
    void altarCategoriesAndDraconicEssenceResourceUseTheirDedicatedIcons() throws Exception {
        String altar = read("Server/Item/Items/Bench/Draconic_Altar.json");
        String resourceType = read("Server/Item/ResourceTypes/HyDragon_DraconicEssences.json");

        assertTrue(altar.contains("\"Icon\": \"Icons/CraftingCategories/HyDragon/Draconic_Stone.png\""));
        assertTrue(altar.contains("\"Icon\": \"Icons/CraftingCategories/HyDragon/Drake_Egg.png\""));
        assertTrue(altar.contains("\"Icon\": \"Icons/CraftingCategories/HyDragon/Draconic_Essences.png\""));
        assertTrue(resourceType.contains("\"Icon\": \"Icons/ResourceTypes/HyDragon_DraconicEssences.png\""));
        assertArrayEquals(icon("ItemsGenerated/Draconic_Stone.png"), categoryIcon("Draconic_Stone.png"));
        assertArrayEquals(icon("ItemsGenerated/Drake_Egg.png"), categoryIcon("Drake_Egg.png"));
        assertArrayEquals(icon("ResourceTypes/HyDragon_DraconicEssences.png"), categoryIcon("Draconic_Essences.png"));

        for (String locale : new String[] {"de-DE", "en-US", "es-ES", "fr-FR", "pt-BR"}) {
            assertTrue(read("Server/Languages/" + locale + "/server.lang")
                    .contains("resourceType.HyDragon_DraconicEssences.name="));
        }
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }

    private static byte[] icon(String relative) throws Exception {
        return Files.readAllBytes(ROOT.resolve("Common/Icons").resolve(relative));
    }

    private static byte[] categoryIcon(String name) throws Exception {
        return icon("CraftingCategories/HyDragon/" + name);
    }
}
