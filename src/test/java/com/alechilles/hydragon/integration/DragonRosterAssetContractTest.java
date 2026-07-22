package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    void populationGroupsEnforceIndependentFullDragonAndMiniwyvernLimits() throws Exception {
        String full = read("Server/Tamework/PopulationGroups/HyDragonFullDragons.json");
        String mini = read("Server/Tamework/PopulationGroups/HyDragonSoulboundMiniwyvern.json");
        assertTrue(full.contains("\"GroupId\": \"hydragon:full_dragons\""));
        assertTrue(full.contains("\"MaxOwnedPerOwner\": 0"));
        assertTrue(full.contains("\"MaxActivePerOwner\": 1"));
        assertTrue(mini.contains("\"GroupId\": \"hydragon:soulbound_mini\""));
        assertTrue(mini.contains("\"MaxOwnedPerOwner\": 1"));
        assertTrue(mini.contains("\"MaxActivePerOwner\": 1"));
    }

    @Test
    void everyCompanionFamilyHasPositiveLeaseCooldownWarningsAndMultiItemReviveCost() throws Exception {
        for (String file : List.of("HyDragonFullDragons.json", "HyDragonMiniwyvern.json")) {
            String config = read("Server/Tamework/Companion/" + file);
            assertTrue(config.contains("\"Summon\""), file);
            assertTrue(config.contains("\"Enabled\": true"), file);
            assertPositive(config, "ActiveDurationMs", file);
            assertPositive(config, "ResummonCooldownMs", file);
            assertTrue(config.contains("\"AutoStoreOnOwnerLogout\": true"), file);
            assertTrue(config.contains("\"ExpiryWarningThresholdsMs\": [ 60000, 30000, 10000 ]"), file);
            assertTrue(config.contains("\"Revive\""), file);
            Matcher costs = COST.matcher(config);
            Set<String> ids = new HashSet<>();
            int count = 0;
            while (costs.find()) {
                count++;
                ids.add(costs.group(1));
                assertTrue(Integer.parseInt(costs.group(2)) > 0, file);
            }
            assertTrue(count >= 2, file + " should exercise generic multi-component costs");
            assertEquals(count, ids.size(), file + " has duplicate cost item IDs");
        }
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

    private static void assertPositive(String json, String key, String context) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(matcher.find(), context + " missing " + key);
        assertTrue(Long.parseLong(matcher.group(1)) > 0, context + " requires positive " + key);
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }
}
