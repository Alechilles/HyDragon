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
    void baseStoneChannelsThenConsumesEveryResolvedRollIntoTheDragonHorn() throws Exception {
        String item = read("Server/Item/Items/Ingredient/Draconic_Stone.json");
        String config = read("Server/Tamework/Items/Spawners/HyDragonDraconicStone.json");

        assertTrue(item.contains("\"Type\": \"TameworkCaptureChannel\""));
        assertTrue(item.contains("\"Phase\": \"Begin\""));
        assertTrue(item.contains("\"Phase\": \"Cancel\""));
        assertTrue(item.contains("\"Phase\": \"Complete\""));
        assertFalse(item.contains("\"State\""));
        assertFalse(item.contains("MaxDurability"));
        assertTrue(config.contains("\"SourceConsumption\": \"ResolvedAttempt\""));
        assertTrue(config.contains("\"SuccessDisposition\": \"TameAndCommandLink\""));
        assertTrue(config.contains("\"CommandFamilyId\": \"hydragon:dragon_horn\""));
        assertTrue(config.contains("\"RequiredCommandConfigId\": \"HyDragonDragonHorn\""));
        assertTrue(config.contains("\"RequireCommandAccessItem\": true"));
        assertFalse(config.contains("FilledItemId"));
        assertFalse(config.contains("\"Vessel\""));
    }

    @Test
    void everyMetalStoneResolvesToItsOwnIncreasingPowerTier() throws Exception {
        Map<String, Integer> tiers = Map.of(
                "Draconic_Stone", 1,
                "Draconic_Stone_Thorium", 2,
                "Draconic_Stone_Cobalt", 3,
                "Draconic_Stone_Adamantium", 4,
                "Draconic_Stone_Ancient", 5);
        for (Map.Entry<String, Integer> tier : tiers.entrySet()) {
            String suffix = tier.getKey().equals("Draconic_Stone")
                    ? "" : tier.getKey().substring("Draconic_Stone".length());
            String config = read("Server/Tamework/Items/Spawners/HyDragonDraconicStone"
                    + suffix.replace("_", "") + ".json");
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
            assertTrue(item.contains("\"Quality\": \"" + stone.getValue() + "\""));
            assertFalse(item.contains("Filled"));
            assertFalse(item.contains("Damaged"));
            textureDigests.add(sha256("Common/Items/HyDragon/" + stone.getKey() + ".png"));
            iconDigests.add(sha256("Common/Icons/ItemsGenerated/" + stone.getKey() + ".png"));
        }
        assertEquals(qualities.size(), textureDigests.size());
        assertEquals(qualities.size(), iconDigests.size());
    }

    @Test
    void captureExcludesMiniwyvernsAndRequiresTranquilization() throws Exception {
        String config = read("Server/Tamework/Items/Spawners/HyDragonDraconicStone.json");
        assertTrue(config.contains("\"RequiredEffectId\": \"Tw_Status_Tranquilized\""));
        assertFalse(config.contains("\"MaxHealthPercent\""));
        assertFalse(config.contains("Wyvern_Mini"));
        assertFalse(config.contains("Tamed_Wyvern_Mini"));
    }

    @Test
    void eggClaimsOnceAndDragonHornOwnsRecurringControl() throws Exception {
        String egg = read("Server/Item/Items/Ingredient/Wyvern_Egg.json");
        String horn = read("Server/Item/Items/Tool/HyDragon_Dragon_Horn.json");
        String command = read("Server/Tamework/Items/Commands/HyDragonDragonHorn.json");

        assertTrue(egg.contains("\"Type\": \"HyDragonSoulBond\""));
        assertFalse(egg.contains("\"BlockType\""));
        assertTrue(horn.contains("\"ItemId\": \"HyDragon_Dragon_Horn\""));
        assertTrue(command.contains("\"CommandFamilyId\": \"hydragon:dragon_horn\""));
        assertTrue(command.contains("\"RosterStorage\": \"OwnerCommandFamily\""));
        assertTrue(command.contains("Tamed_Wyvern_Mini"));
        assertFalse(Files.exists(ROOT.resolve("Server/Item/Items/Ingredient/Soul_Bound_Wyvern.json")));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }

    private static String sha256(String relative) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ROOT.resolve(relative)));
        return HexFormat.of().formatHex(digest);
    }
}
