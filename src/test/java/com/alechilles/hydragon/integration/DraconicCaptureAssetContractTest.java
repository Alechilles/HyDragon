package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DraconicCaptureAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));

    @Test
    void baseStoneUsesPrimaryCaptureChannelWithTerminalCompletion() throws Exception {
        String item = read("Server/Item/Items/Ingredient/Draconic_Stone.json");

        assertTrue(item.contains("\"Primary\""));
        assertTrue(item.contains("\"Type\": \"TameworkCaptureChannel\""));
        assertTrue(item.contains("\"Phase\": \"Begin\""));
        assertTrue(item.contains("\"Type\": \"Charging\""));
        assertTrue(item.contains("\"Phase\": \"Cancel\""));
        assertTrue(item.contains("\"Phase\": \"Complete\""));
    }

    @Test
    void everyMetalStoneResolvesToItsOwnSpawnerPowerTier() throws Exception {
        Map<String, Integer> tiers = Map.of(
                "Draconic_Stone", 1,
                "Draconic_Stone_Thorium", 2,
                "Draconic_Stone_Cobalt", 3,
                "Draconic_Stone_Adamantium", 4,
                "Draconic_Stone_Ancient", 5);
        Map<String, String> configs = Map.of(
                "Draconic_Stone", "HyDragonDraconicStone.json",
                "Draconic_Stone_Thorium", "HyDragonDraconicStoneThorium.json",
                "Draconic_Stone_Cobalt", "HyDragonDraconicStoneCobalt.json",
                "Draconic_Stone_Adamantium", "HyDragonDraconicStoneAdamantium.json",
                "Draconic_Stone_Ancient", "HyDragonDraconicStoneAncient.json");

        for (Map.Entry<String, Integer> tier : tiers.entrySet()) {
            String config = read("Server/Tamework/Items/Spawners/" + configs.get(tier.getKey()));
            assertTrue(config.contains("\"EmptyItemId\": \"" + tier.getKey() + "\""));
            assertTrue(config.contains("\"Power\": " + tier.getValue()));
        }
    }

    @Test
    void metalStonesScaleQualityAndUseDistinctMaterialArt() throws Exception {
        Map<String, String> qualities = Map.of(
                "Draconic_Stone", "Common",
                "Draconic_Stone_Thorium", "Uncommon",
                "Draconic_Stone_Cobalt", "Rare",
                "Draconic_Stone_Adamantium", "Epic",
                "Draconic_Stone_Ancient", "Legendary");
        Set<String> textureDigests = new HashSet<>();
        Set<String> iconDigests = new HashSet<>();

        for (Map.Entry<String, String> stone : qualities.entrySet()) {
            String item = read("Server/Item/Items/Ingredient/" + stone.getKey() + ".json");
            assertTrue(item.contains("\"Quality\": \"" + stone.getValue() + "\""),
                    () -> stone.getKey() + " must use " + stone.getValue() + " quality");
            textureDigests.add(sha256("Common/Items/HyDragon/" + stone.getKey() + ".png"));
            iconDigests.add(sha256("Common/Icons/ItemsGenerated/" + stone.getKey() + ".png"));
        }

        assertEquals(qualities.size(), textureDigests.size(), "Each metal tier needs distinct texture bytes");
        assertEquals(qualities.size(), iconDigests.size(), "Each metal tier needs distinct icon bytes");
    }

    @Test
    void higherTierStonesExplicitlyDeclareEveryBondedLifecycleState() throws Exception {
        Map<String, String> tiers = Map.of(
                "Draconic_Stone_Thorium", "HyDragonDraconicStoneThorium.json",
                "Draconic_Stone_Cobalt", "HyDragonDraconicStoneCobalt.json",
                "Draconic_Stone_Adamantium", "HyDragonDraconicStoneAdamantium.json",
                "Draconic_Stone_Ancient", "HyDragonDraconicStoneAncient.json");
        Map<String, String> stateInteractions = Map.of(
                "Filled", "TameworkSpawn",
                "Active", "TameworkSpawn",
                "Damaged", "HyDragonRepairBondedStone",
                "Lost", "Simple",
                "Unavailable", "Simple");

        for (Map.Entry<String, String> tier : tiers.entrySet()) {
            String itemId = tier.getKey();
            String item = read("Server/Item/Items/Ingredient/" + itemId + ".json");
            String config = read("Server/Tamework/Items/Spawners/" + tier.getValue());

            for (Map.Entry<String, String> state : stateInteractions.entrySet()) {
                String stateName = state.getKey();
                String block = objectValueForKey(objectValueForKey(item, "State"), stateName);
                assertTrue(block.contains("\"Variant\": true"),
                        () -> itemId + " " + stateName + " must be a concrete item variant");
                assertTrue(block.contains("\"Type\": \"" + state.getValue() + "\""),
                        () -> itemId + " " + stateName + " must use " + state.getValue());
                assertTrue(block.contains("\"Model\": \"Items/HyDragon/Draconic_Stone_Filled.blockymodel\""));
                assertTrue(block.contains("\"Icon\": \"Icons/ItemsGenerated/" + itemId + ".png\""));
                assertTrue(block.contains("server.items." + itemId + "_" + stateName + ".name"));
                assertTrue(block.contains("server.items." + itemId + "_" + stateName + ".description"));
                assertTrue(config.contains("*" + itemId + "_State_" + stateName),
                        () -> tier.getValue() + " must address the declared " + stateName + " variant");
            }
        }
    }

    @Test
    void everySupportedLocaleTranslatesHigherTierLifecycleStates() throws Exception {
        for (String locale : new String[] {"en-US", "de-DE", "fr-FR", "es-ES", "pt-BR"}) {
            String translations = read("Server/Languages/" + locale + "/server.lang");
            for (String tier : new String[] {"Thorium", "Cobalt", "Adamantium", "Ancient"}) {
                for (String state : new String[] {"Filled", "Active", "Damaged", "Lost", "Unavailable"}) {
                    String key = "items.Draconic_Stone_" + tier + "_" + state;
                    assertTrue(translations.contains(key + ".name="),
                            () -> locale + " is missing " + key + ".name");
                    assertTrue(translations.contains(key + ".description="),
                            () -> locale + " is missing " + key + ".description");
                }
            }
        }
    }

    @Test
    void wildDragonCaptureRequiresTranquilizationAndMapsEverySupportedRole() throws Exception {
        String config = read("Server/Tamework/Items/Spawners/HyDragonDraconicStone.json");

        assertTrue(config.contains("\"RequiredEffectId\": \"Tw_Status_Tranquilized\""));
        assertTrue(config.contains("\"MaxHealthPercent\": 20.0"));
        for (String role : new String[] {
                "NordicDrake", "Hydra", "RockDrakeT1", "RockDrakeT2", "RockDrakeT3"
        }) {
            assertTrue(config.contains("\"" + role + "\""));
            assertTrue(config.contains("\"" + role + "\": \"Tamed_" + role + "\""));
        }
    }

    @Test
    void wyvernEggAndSoulBoundFocusDispatchTheirRegisteredInteractions() throws Exception {
        String item = read("Server/Item/Items/Ingredient/Wyvern_Egg.json");
        String focus = read("Server/Item/Items/Ingredient/Soul_Bound_Wyvern.json");

        assertTrue(item.contains("\"Primary\""));
        assertTrue(item.contains("\"Type\": \"HyDragonSoulBond\""));
        assertTrue(item.contains("\"Icon\": \"Icons/ItemsGenerated/Drake_Egg.png\""));
        assertTrue(item.contains("\"Model\": \"Items/HyDragon/Drake_Egg.blockymodel\""));
        assertTrue(item.contains("\"Texture\": \"Items/HyDragon/Drake_Egg.png\""));
        assertFalse(item.contains("\"BlockType\""), "Soul Bond egg must remain a non-placeable item");
        assertTrue(focus.contains("\"Type\": \"HyDragonMiniwyvernRecall\""));
        assertFalse(focus.contains("\"Recipe\""), "Soul Bound Wyvern must only come from egg consumption");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }

    private static String sha256(String relative) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ROOT.resolve(relative)));
        return HexFormat.of().formatHex(digest);
    }

    private static String objectValueForKey(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        assertTrue(keyIndex >= 0, () -> "Missing JSON key " + key);
        int objectStart = json.indexOf('{', keyIndex);
        assertTrue(objectStart >= 0, () -> "JSON key " + key + " is not followed by an object");

        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = objectStart; index < json.length(); index++) {
            char character = json.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
                continue;
            }
            if (character == '"') {
                quoted = true;
            } else if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return json.substring(objectStart, index + 1);
            }
        }
        throw new AssertionError("Unclosed JSON object for key " + key);
    }
}
