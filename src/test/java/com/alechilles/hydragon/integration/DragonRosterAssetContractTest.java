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
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));
    private static final Pattern COST = Pattern.compile(
            "\\{\\s*\\\"ItemId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"Quantity\\\"\\s*:\\s*(\\d+)\\s*}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^{}]+}");

    @Test
    void bondedPoliciesShareTheHornButKeepIndependentFamilyLimitsAndEconomics()
            throws Exception {
        String full = read("Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json");
        String mini = read("Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json");

        assertPolicy(full, "hydragon:full_dragons", 0, 1, 300, 1800,
                List.of("Revitalizing_Essence:2", "Draconic_Essence:4"));
        assertPolicy(mini, "hydragon:soulbound_mini", 1, 1, null, null,
                List.of("Revitalizing_Essence:1", "Draconic_Essence:2"));
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
    void obsoleteGenericPopulationGroupsAndEncounterFieldAreAbsent() throws Exception {
        assertFalse(Files.exists(ROOT.resolve(
                "Server/Tamework/PopulationGroups/HyDragonFullDragons.json")));
        assertFalse(Files.exists(ROOT.resolve(
                "Server/Tamework/PopulationGroups/HyDragonSoulboundMiniwyvern.json")));
        assertFalse(read("Server/HyDragon/Encounters/NordicDrakeHighAltitude.json")
                .contains("ActiveCompanionGroup"));
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

    private static void assertPolicy(
            String json,
            String familyId,
            int maximumOwned,
            int maximumActive,
            Integer sessionSeconds,
            Integer cooldownSeconds,
            List<String> expectedCosts
    ) {
        assertTrue(json.contains("\"RosterId\": \"hydragon:dragon_horn\""));
        assertTrue(json.contains("\"FamilyId\": \"" + familyId + "\""));
        assertTrue(json.contains("\"MaximumOwned\": " + maximumOwned));
        assertTrue(json.contains("\"MaximumActive\": " + maximumActive));
        assertTimer(json, "SessionDurationSeconds", sessionSeconds);
        assertTimer(json, "SummonCooldownSeconds", cooldownSeconds);
        assertTrue(json.contains("\"RevivePrice\""));
        Matcher costs = COST.matcher(json);
        List<String> actual = new java.util.ArrayList<>();
        while (costs.find()) actual.add(costs.group(1) + ":" + costs.group(2));
        assertEquals(expectedCosts, actual);
    }

    private static void assertTimer(String json, String field, Integer seconds) {
        if (seconds == null) {
            assertFalse(json.contains("\"" + field + "\""), field);
            return;
        }
        assertTrue(json.contains("\"" + field + "\": " + seconds), field);
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }
}
