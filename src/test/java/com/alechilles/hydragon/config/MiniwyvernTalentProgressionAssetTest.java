package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Contract for the one persistent, form-independent Miniwyvern progression tree. */
final class MiniwyvernTalentProgressionAssetTest {
    private static final Path LEVELING_PATH = Path.of(
            "Server", "Tamework", "Leveling", "HyDragonMiniwyvern.json");
    private static final Path TALENTS_PATH = Path.of(
            "Server", "Tamework", "Talents", "HyDragonMiniwyvern.json");
    private static final Path ROSTER_PATH = Path.of(
            "Server", "Tamework", "BondedCompanions", "Rosters", "HyDragonMiniwyvern.json");
    private static final Path COMPANION_PATH = Path.of(
            "Server", "Tamework", "Companion", "HyDragonMiniwyvern.json");
    private static final List<String> ROLE_IDS = List.of(
            "Tamed_Wyvern_Mini_Wild", "Tamed_Wyvern_Mini_Nature", "Tamed_Wyvern_Mini_Toxic",
            "Tamed_Wyvern_Mini_Fire", "Tamed_Wyvern_Mini_Void", "Tamed_Wyvern_Mini_Lightning",
            "Tamed_Wyvern_Mini_Ice");
    private static final List<String> LOCALES = List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR");
    private static final String LOCALE_PREFIX = "hydragon.talents.miniwyvern.";

    @Test
    void allMiniwyvernFormsResolveTheSameEnabledLevelingAndTalentConfigs() throws IOException {
        JsonObject leveling = loadJson(LEVELING_PATH);
        JsonObject talents = loadJson(TALENTS_PATH);
        JsonObject roster = loadJson(ROSTER_PATH);
        JsonObject companion = loadJson(COMPANION_PATH);

        assertTrue(leveling.get("Enabled").getAsBoolean());
        assertEquals(30, leveling.getAsJsonObject("Levels").get("MaxLevel").getAsInt());
        assertEquals(1, leveling.getAsJsonObject("TalentPoints").get("PointsPerLevel").getAsInt());
        assertTrue(talents.get("Enabled").getAsBoolean());
        assertEquals(ROLE_IDS, strings(leveling.getAsJsonArray("RoleIds")));
        assertEquals(ROLE_IDS, strings(talents.getAsJsonArray("RoleIds")));
        assertEquals(ROLE_IDS, strings(roster.getAsJsonArray("AllowedRoles")));
        assertEquals(ROLE_IDS, strings(companion.getAsJsonArray("RoleIds")));

        JsonObject combat = leveling.getAsJsonObject("XpSources").getAsJsonObject("Combat");
        assertTrue(combat.get("Enabled").getAsBoolean());
        assertFalse(combat.get("AwardVsPlayers").getAsBoolean());
        assertFalse(combat.get("AwardVsOwnedAllies").getAsBoolean());
        assertTrue(combat.get("DamageTakenXpPerPoint").getAsDouble()
                < combat.get("DamageDealtXpPerPoint").getAsDouble());

        JsonObject summoned = leveling.getAsJsonObject("XpSources").getAsJsonObject("Summoned");
        assertTrue(summoned.get("Enabled").getAsBoolean());
        assertTrue(summoned.get("XpPerActiveSecond").getAsDouble() > 0.0d);
        assertEquals(Math.rint(summoned.get("AwardIntervalSeconds").getAsDouble()),
                summoned.get("AwardIntervalSeconds").getAsDouble());
        assertTrue(summoned.get("MaxXpPerHour").getAsDouble() > 0.0d);
    }

    @Test
    void sharedTalentGraphUsesAllGenericNodesWithTheApprovedBudgetsAndPrerequisites() throws IOException {
        JsonObject config = loadJson(TALENTS_PATH);
        Map<String, JsonObject> talents = talentsById(config.getAsJsonArray("Talents"));

        assertEquals(30, talents.size());
        assertEquals(expectedNodeIds(), talents.keySet());
        Map<String, Integer> costsByBranch = new LinkedHashMap<>();
        for (JsonObject talent : talents.values()) {
            String id = talent.get("Id").getAsString();
            assertFalse(id.startsWith("Miniwyvern_"), id + " must remain form-independent");
            assertTrue(talent.get("MinLevel").getAsInt() <= 30, id);
            assertTrue(talent.get("PointCost").getAsInt() > 0, id);
            assertTrue(talent.get("Effects").getAsJsonArray().size() > 0, id);
            for (String prerequisite : optionalStrings(talent, "RequiresTalentIds")) {
                assertNotNull(talents.get(prerequisite), id + " prerequisite " + prerequisite);
            }
            costsByBranch.merge(talent.get("Branch").getAsString(), talent.get("PointCost").getAsInt(), Integer::sum);
        }
        assertEquals(Map.of(
                LOCALE_PREFIX + "branch.bond", 15,
                LOCALE_PREFIX + "branch.combat", 19,
                LOCALE_PREFIX + "branch.vigor", 17), costsByBranch);
        assertEquals(51, costsByBranch.values().stream().mapToInt(Integer::intValue).sum());

        assertHealthEffect(talents.get("VitalScales"), 1.05d);
        assertHealthEffect(talents.get("HardenedScales"), 1.05d);
        assertHealthEffect(talents.get("ElderScales"), 1.08d);
    }

    @Test
    void everyTalentPanelKeyIsPresentInEveryShippedLocale() throws IOException {
        JsonObject config = loadJson(TALENTS_PATH);
        Set<String> keys = new LinkedHashSet<>();
        for (JsonElement element : config.getAsJsonArray("Talents")) {
            JsonObject talent = element.getAsJsonObject();
            keys.add(talent.get("Branch").getAsString());
            keys.add(talent.get("DisplayName").getAsString());
            keys.add(talent.get("Description").getAsString());
        }
        assertEquals(63, keys.size());
        assertTrue(keys.stream().allMatch(key -> key.startsWith(LOCALE_PREFIX)));

        for (String locale : LOCALES) {
            Map<String, String> entries = localeEntries(locale);
            for (String key : keys) {
                assertNotNull(entries.get(key), locale + " missing " + key);
            }
        }
    }

    private static Set<String> expectedNodeIds() {
        return new LinkedHashSet<>(List.of(
                "EssenceBond", "EssenceFocus", "EssenceAttunement", "EssenceAmplification", "EssenceResonance",
                "EssenceEfficiency", "EssenceHarmony", "EssenceMastery", "EssenceAscendance",
                "DraconicProjectile", "ProjectileRange", "ProjectileCadence", "ProjectileForce", "ProjectileGuidance",
                "ProjectileImpact", "ProjectilePattern", "DraconicAssault", "AssaultUtility", "AssaultMastery", "DraconicApex",
                "VitalScales", "HardenedScales", "ElderScales", "ScaleGuard", "ScaleBulwark", "RapidRecovery",
                "SurvivalInstinct", "LastingScales", "WyrmFortitude", "VigorAscendance"));
    }

    private static void assertHealthEffect(JsonObject talent, double multiplier) {
        JsonObject effect = talent.getAsJsonArray("Effects").get(0).getAsJsonObject();
        assertEquals("MaxHealthMultiplier", effect.get("EffectKey").getAsString());
        assertEquals(multiplier, effect.get("Multiplier").getAsDouble(), 0.000_001d);
    }

    private static JsonObject loadJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Map<String, JsonObject> talentsById(JsonArray values) {
        Map<String, JsonObject> talents = new LinkedHashMap<>();
        for (JsonElement element : values) {
            JsonObject talent = element.getAsJsonObject();
            assertTrue(talents.put(talent.get("Id").getAsString(), talent) == null, "duplicate talent ID");
        }
        return talents;
    }

    private static List<String> strings(JsonArray values) {
        List<String> results = new ArrayList<>();
        for (JsonElement value : values) {
            results.add(value.getAsString());
        }
        return List.copyOf(results);
    }

    private static List<String> optionalStrings(JsonObject object, String name) {
        return object.has(name) ? strings(object.getAsJsonArray(name)) : List.of();
    }

    private static Map<String, String> localeEntries(String locale) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of("Server", "Languages", locale, "server.lang"))) {
            int separator = line.indexOf('=');
            if (separator > 0 && !line.startsWith("#")) {
                entries.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return entries;
    }
}
