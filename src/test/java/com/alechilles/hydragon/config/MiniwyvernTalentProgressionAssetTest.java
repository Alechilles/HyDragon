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
        JsonObject command = companion.getAsJsonObject("Command");
        assertEquals(-4.0d, command.get("PlacementMinRelativeY").getAsDouble());
        assertEquals(8.0d, command.get("PlacementMaxRelativeY").getAsDouble());

        JsonObject combat = leveling.getAsJsonObject("XpSources").getAsJsonObject("Combat");
        assertTrue(combat.get("Enabled").getAsBoolean());
        assertFalse(combat.get("AwardVsPlayers").getAsBoolean());
        assertFalse(combat.get("AwardVsOwnedAllies").getAsBoolean());
        assertTrue(combat.get("DamageTakenXpPerPoint").getAsDouble()
                < combat.get("DamageDealtXpPerPoint").getAsDouble());

        JsonObject summoned = leveling.getAsJsonObject("XpSources").getAsJsonObject("Summoned");
        assertTrue(summoned.get("Enabled").getAsBoolean());
        assertEquals(Set.of("Enabled", "XpPerActiveSecond", "AwardIntervalSeconds", "MaxXpPerHour"),
                summoned.keySet());
        assertTrue(summoned.get("XpPerActiveSecond").getAsDouble() > 0.0d);
        assertEquals(Math.rint(summoned.get("AwardIntervalSeconds").getAsDouble()),
                summoned.get("AwardIntervalSeconds").getAsDouble());
        assertTrue(summoned.get("MaxXpPerHour").getAsDouble() > 0.0d);
        assertFalse(talents.has("ResetCost"));
        assertFalse(talents.has("ResetCooldownSeconds"));
    }

    @Test
    void sharedTalentGraphUsesAllGenericNodesWithTheApprovedBudgetsAndPrerequisites() throws IOException {
        JsonObject config = loadJson(TALENTS_PATH);
        Map<String, JsonObject> talents = talentsById(config.getAsJsonArray("Talents"));

        Map<String, TalentExpectation> expected = expectedTalents();
        assertEquals(expected.keySet(), talents.keySet());
        Map<String, Integer> costsByBranch = new LinkedHashMap<>();
        for (Map.Entry<String, TalentExpectation> entry : expected.entrySet()) {
            String id = entry.getKey();
            JsonObject talent = talents.get(id);
            TalentExpectation expectation = entry.getValue();
            assertFalse(id.startsWith("Miniwyvern_"), id + " must remain form-independent");
            assertEquals(expectation.branch(), talent.get("Branch").getAsString(), id);
            assertEquals(expectation.tier(), talent.get("Tier").getAsInt(), id);
            assertEquals(expectation.minLevel(), talent.get("MinLevel").getAsInt(), id);
            assertEquals(expectation.pointCost(), talent.get("PointCost").getAsInt(), id);
            assertEquals(expectation.prerequisites(), optionalStrings(talent, "RequiresTalentIds"), id);
            assertTrue(talent.get("Effects").getAsJsonArray().size() > 0, id);
            for (String prerequisite : expectation.prerequisites()) {
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

    private static Map<String, TalentExpectation> expectedTalents() {
        String bond = LOCALE_PREFIX + "branch.bond";
        String combat = LOCALE_PREFIX + "branch.combat";
        String vigor = LOCALE_PREFIX + "branch.vigor";
        Map<String, TalentExpectation> expected = new LinkedHashMap<>();
        expected.put("EssenceBond", talent(bond, 1, 2, 1));
        expected.put("EssenceFocus", talent(bond, 2, 4, 1, "EssenceBond"));
        expected.put("EssenceAttunement", talent(bond, 2, 6, 1, "EssenceBond"));
        expected.put("EssenceAmplification", talent(bond, 3, 9, 2, "EssenceFocus"));
        expected.put("EssenceResonance", talent(bond, 3, 11, 2, "EssenceAttunement"));
        expected.put("EssenceEfficiency", talent(bond, 4, 14, 1, "EssenceAmplification"));
        expected.put("EssenceHarmony", talent(bond, 4, 17, 1, "EssenceResonance"));
        expected.put("EssenceMastery", talent(bond, 5, 20, 2, "EssenceEfficiency"));
        expected.put("EssenceAscendance", talent(bond, 6, 26, 4, "EssenceMastery", "EssenceHarmony"));
        expected.put("DraconicProjectile", talent(combat, 1, 3, 1));
        expected.put("ProjectileRange", talent(combat, 2, 5, 1, "DraconicProjectile"));
        expected.put("ProjectileCadence", talent(combat, 2, 5, 1, "DraconicProjectile"));
        expected.put("ProjectileForce", talent(combat, 3, 8, 2, "ProjectileRange"));
        expected.put("ProjectileGuidance", talent(combat, 3, 9, 1, "ProjectileCadence"));
        expected.put("ProjectileImpact", talent(combat, 4, 12, 2, "ProjectileForce"));
        expected.put("ProjectilePattern", talent(combat, 4, 14, 2, "ProjectileGuidance"));
        expected.put("DraconicAssault", talent(combat, 5, 17, 2, "ProjectileImpact", "ProjectilePattern"));
        expected.put("AssaultUtility", talent(combat, 5, 18, 1, "DraconicAssault"));
        expected.put("AssaultMastery", talent(combat, 5, 21, 2, "DraconicAssault"));
        expected.put("DraconicApex", talent(combat, 6, 27, 4, "AssaultUtility", "AssaultMastery"));
        expected.put("VitalScales", talent(vigor, 1, 2, 1));
        expected.put("HardenedScales", talent(vigor, 2, 4, 1, "VitalScales"));
        expected.put("ElderScales", talent(vigor, 3, 7, 2, "HardenedScales"));
        expected.put("ScaleGuard", talent(vigor, 3, 8, 1, "ElderScales"));
        expected.put("ScaleBulwark", talent(vigor, 4, 11, 2, "ScaleGuard"));
        expected.put("RapidRecovery", talent(vigor, 4, 12, 1, "ScaleBulwark"));
        expected.put("SurvivalInstinct", talent(vigor, 5, 15, 2, "RapidRecovery"));
        expected.put("LastingScales", talent(vigor, 5, 17, 1, "SurvivalInstinct"));
        expected.put("WyrmFortitude", talent(vigor, 5, 20, 2, "LastingScales"));
        expected.put("VigorAscendance", talent(vigor, 6, 25, 4, "WyrmFortitude"));
        return expected;
    }

    private static TalentExpectation talent(String branch, int tier, int minLevel, int pointCost,
                                             String... prerequisites) {
        return new TalentExpectation(branch, tier, minLevel, pointCost, List.of(prerequisites));
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

    private record TalentExpectation(String branch, int tier, int minLevel, int pointCost,
                                     List<String> prerequisites) {
    }
}
