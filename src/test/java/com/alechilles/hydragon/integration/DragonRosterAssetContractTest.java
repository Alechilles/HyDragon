package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DragonRosterAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", ".")).resolve("src/main/resources");
    private static final Pattern COST = Pattern.compile(
            "\\{\\s*\\\"ItemId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"Quantity\\\"\\s*:\\s*(\\d+)\\s*}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^{}]+}");

    @Test
    void bondedPoliciesShareTheHornButKeepIndependentFamilyLimitsAndEconomics()
            throws Exception {
        String full = read("Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json");
        String mini = read("Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json");

        assertPolicy(full, "hydragon:full_dragons");
        assertPolicy(mini, "hydragon:soulbound_mini");
        assertTrue(full.contains("\"RevivePriceByRole\""));
        assertTrue(full.contains("\"Tamed_Hydra_Toxic\""));
        assertTrue(full.contains("\"Draconic_Essence_Toxic\", \"Quantity\": 2"));
        assertTrue(mini.contains("\"RevivePriceByRole\""));
        assertTrue(mini.contains("\"Tamed_Wyvern_Mini_Wild\""));
        assertTrue(mini.contains("\"Draconic_Essence\", \"Quantity\": 1"));
        assertTrue(full.contains("\"Tamed_NordicDrake\""));
        assertTrue(full.contains("\"Tamed_Hydra\""));
        assertTrue(full.contains("\"Tamed_RockDrakeT3\""));
        for (String role : List.of(
                "Tamed_Wyvern_Mini_Wild", "Tamed_Wyvern_Mini_Nature", "Tamed_Wyvern_Mini_Toxic",
                "Tamed_Wyvern_Mini_Fire", "Tamed_Wyvern_Mini_Void", "Tamed_Wyvern_Mini_Lightning",
                "Tamed_Wyvern_Mini_Ice")) {
            assertTrue(mini.contains("\"" + role + "\""), role);
        }
        assertTrue(full.contains("\"Capture\": true"));
        assertTrue(full.contains("\"Provision\": false"));
        assertTrue(mini.contains("\"Capture\": false"));
        assertTrue(mini.contains("\"Provision\": true"));
    }

    @Test
    void companionAssetsRetainMovementWithoutOwningBondedLifecycle() throws Exception {
        for (String file : List.of("HyDragonFullDragons.json", "HyDragonMiniwyvern.json", "HyDragonNordicDrake.json")) {
            String config = read("Server/Tamework/Companion/" + file);
            assertTrue(config.contains("\"Enabled\": true"), file);
            assertTrue(config.contains("\"ReturnHomeTeleportDistance\""), file);
            assertTrue(config.contains("\"RecallSafeSpawnDistance\""), file);
            assertTrue(config.contains("\"PlacementMinRelativeY\""), file);
            assertFalse(config.contains("\"Travel\""), file);
            assertFalse(config.contains("\"Summon\""), file);
            assertFalse(config.contains("\"Revive\""), file);
            assertFalse(config.contains("ActiveDurationMs"), file);
            assertFalse(config.contains("ResummonCooldownMs"), file);
            assertFalse(config.contains("AutoStoreOnOwnerLogout"), file);
            assertFalse(config.contains("ExpiryWarningThresholdsMs"), file);
            assertFalse(config.contains("GameplayCooldownMs"), file);
            assertFalse(config.contains("InsufficientCostMessage"), file);
        }
    }

    @Test
    void fullDragonRosterKeepsAllFiveRolesWhileCompanionConfigSeparatesNordicFlightCapability()
            throws Exception {
        String roster = read("Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json");
        for (String role : List.of("Tamed_NordicDrake", "Tamed_Hydra", "Tamed_RockDrakeT1",
                "Tamed_RockDrakeT2", "Tamed_RockDrakeT3")) {
            assertTrue(roster.contains("\"" + role + "\""), role);
        }
        assertTrue(Files.exists(ROOT.resolve("Server/Tamework/Companion/HyDragonNordicDrake.json")));
    }

    @Test
    void fullDragonThirtySecondExpiryWarningUsesTheDesummonModelVfx()
            throws Exception {
        String roster = read("Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json");
        String effect = read("Server/Entity/Effects/Status/HyDragon_Dragon_Desummon.json");
        String modelVfx = read("Server/Entity/ModelVFX/HyDragon_Desummon.json");

        assertTrue(roster.contains("\"ExpiryWarningEffectId\": \"HyDragon_Dragon_Desummon\""));
        assertTrue(effect.contains("\"ModelVFXId\": \"HyDragon_Desummon\""));
        assertTrue(effect.contains("\"Duration\": 30"));
        assertTrue(modelVfx.contains("\"AnimationDuration\": 30"));
    }

    @Test
    void obsoleteGenericPopulationGroupsAndNordicEncounterAreAbsent() throws Exception {
        assertFalse(Files.exists(ROOT.resolve(
                "Server/Tamework/PopulationGroups/HyDragonFullDragons.json")));
        assertFalse(Files.exists(ROOT.resolve(
                "Server/Tamework/PopulationGroups/HyDragonSoulboundMiniwyvern.json")));
        assertFalse(Files.exists(ROOT.resolve(
                "Server/HyDragon/Encounters/NordicDrakeHighAltitude.json")));
    }

    @Test
    void fiveLocalesHaveIdenticalRosterReviveKeysAndPlaceholderSets() throws Exception {
        Map<String, Map<String, Set<String>>> catalogs = new HashMap<>();
        for (String locale : List.of("en-US", "pt-BR", "de-DE", "fr-FR", "es-ES")) {
            Map<String, Set<String>> relevant = new HashMap<>();
            for (String line : read("Server/Languages/" + locale + "/server.lang").split("\\n")) {
                int separator = line.indexOf('=');
                if (separator < 0) continue;
                String key = line.substring(0, separator);
                if (key.equals("items.HyDragon_Dragon_Horn.name")
                        || key.equals("items.HyDragon_Dragon_Horn.description")
                        || key.startsWith("messages.roster.")
                        || key.startsWith("messages.revive.")) {
                    Set<String> placeholders = new HashSet<>();
                    Matcher matcher = PLACEHOLDER.matcher(line.substring(separator + 1));
                    while (matcher.find()) placeholders.add(matcher.group());
                    relevant.put(key, placeholders);
                }
            }
            catalogs.put(locale, relevant);
        }
        Map<String, Set<String>> english = catalogs.get("en-US");
        assertFalse(english.isEmpty());
        for (Map.Entry<String, Map<String, Set<String>>> entry : catalogs.entrySet()) {
            assertEquals(english, entry.getValue(), entry.getKey());
        }
    }

    @Test
    void obsoleteBondedAssetsAndLocalizationAreAbsent() throws Exception {
        List<String> obsoletePaths = List.of(
                "Server/Item/Items/Ingredient/Soul_Bound_Wyvern.json",
                "Server/Item/Items/Tool/HyDragon_Command_Whistle.json",
                "Server/Tamework/Items/Commands/HyDragonDragonCommand.json",
                "Server/HyDragon/StoneMaintenance/Default.json",
                "Common/Items/HyDragon/Draconic_Stone_Filled.blockymodel",
                "Common/Icons/ItemsGenerated/Draconic_Stone_Filled.png");
        for (String path : obsoletePaths) assertFalse(Files.exists(ROOT.resolve(path)), path);
        for (String locale : List.of("en-US", "pt-BR", "de-DE", "fr-FR", "es-ES")) {
            String text = read("Server/Languages/" + locale + "/server.lang");
            for (String token : List.of("Soul_Bound_Wyvern", "HyDragon_Command_Whistle",
                    "messages.vessel.", "messages.repair.", "_Filled.", "_Damaged.", "_Lost.", "_Unavailable.")) {
                assertFalse(text.contains(token), locale + ": " + token);
            }
        }
    }

    private static void assertPolicy(String json, String familyId) {
        assertTrue(json.contains("\"RosterId\": \"hydragon:dragon_horn\""));
        assertTrue(json.contains("\"FamilyId\": \"" + familyId + "\""));
        assertTrue(json.contains("\"RevivePriceByRole\""));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }
}
