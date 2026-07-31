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
                LOCALE_PREFIX + "branch.combat", 20,
                LOCALE_PREFIX + "branch.vigor", 17), costsByBranch);
        assertEquals(52, costsByBranch.values().stream().mapToInt(Integer::intValue).sum());

        for (String id : List.of(
                "DraconicProjectile", "ProjectileRange", "ProjectileCadence", "ProjectileGuidance",
                "ProjectilePattern", "ProjectileMastery", "SwoopFerocity", "SwoopCadence",
                "SwoopPrecision", "RelentlessSwoop", "RendingDive", "SwoopMastery")) {
            JsonObject effect = talents.get(id).getAsJsonArray("Effects").get(0).getAsJsonObject();
            assertEquals(id, effect.get("EffectKey").getAsString(), id);
            assertEquals(1.0d, effect.get("Multiplier").getAsDouble(), 0.000_001d, id);
        }
        for (String removedId : Set.of("ProjectileForce", "ProjectileImpact", "DraconicAssault",
                "AssaultUtility", "AssaultMastery", "DraconicApex")) {
            assertFalse(talents.containsKey(removedId), removedId + " must not ship in stage one");
        }
        for (JsonObject talent : talents.values()) {
            assertFalse(talent.has("RequiresAnyTalentIds"), talent.get("Id").getAsString());
        }

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
        assertEquals(65, keys.size());
        assertTrue(keys.stream().allMatch(key -> key.startsWith(LOCALE_PREFIX)));

        for (String locale : LOCALES) {
            Map<String, String> entries = localeEntries(locale);
            for (String key : keys) {
                assertNotNull(entries.get(key), locale + " missing " + key);
            }
        }
    }

    @Test
    void englishSwoopDescriptionsStateTheBindingDamageAndCooldownBands() throws IOException {
        Map<String, String> entries = localeEntries("en-US");
        assertEquals(Map.of(
                LOCALE_PREFIX + "swoop_ferocity.description",
                "Unlock a 20-damage swoop with a 25-35 second cooldown.",
                LOCALE_PREFIX + "swoop_cadence.description",
                "Keep the 20-damage swoop and reduce its cooldown to 22-30 seconds.",
                LOCALE_PREFIX + "swoop_precision.description",
                "Increase swoop damage to 24 with a 25-35 second cooldown.",
                LOCALE_PREFIX + "relentless_swoop.description",
                "Keep the 20-damage swoop and reduce its cooldown to 20-26 seconds.",
                LOCALE_PREFIX + "rending_dive.description",
                "Increase swoop damage to 28 with a 25-35 second cooldown.",
                LOCALE_PREFIX + "swoop_mastery.description",
                "Master the swoop route for 28 damage and an 18-24 second cooldown."),
                Map.of(
                        LOCALE_PREFIX + "swoop_ferocity.description",
                        entries.get(LOCALE_PREFIX + "swoop_ferocity.description"),
                        LOCALE_PREFIX + "swoop_cadence.description",
                        entries.get(LOCALE_PREFIX + "swoop_cadence.description"),
                        LOCALE_PREFIX + "swoop_precision.description",
                        entries.get(LOCALE_PREFIX + "swoop_precision.description"),
                        LOCALE_PREFIX + "relentless_swoop.description",
                        entries.get(LOCALE_PREFIX + "relentless_swoop.description"),
                        LOCALE_PREFIX + "rending_dive.description",
                        entries.get(LOCALE_PREFIX + "rending_dive.description"),
                        LOCALE_PREFIX + "swoop_mastery.description",
                        entries.get(LOCALE_PREFIX + "swoop_mastery.description")));
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
        expected.put("ProjectileGuidance", talent(combat, 3, 9, 2, "ProjectileRange"));
        expected.put("ProjectilePattern", talent(combat, 3, 11, 2, "ProjectileCadence"));
        expected.put("ProjectileMastery", talent(combat, 4, 14, 3,
                "ProjectileGuidance", "ProjectilePattern"));
        expected.put("SwoopFerocity", talent(combat, 1, 3, 1));
        expected.put("SwoopCadence", talent(combat, 2, 5, 1, "SwoopFerocity"));
        expected.put("SwoopPrecision", talent(combat, 2, 5, 1, "SwoopFerocity"));
        expected.put("RelentlessSwoop", talent(combat, 3, 9, 2, "SwoopCadence"));
        expected.put("RendingDive", talent(combat, 3, 11, 2, "SwoopPrecision"));
        expected.put("SwoopMastery", talent(combat, 4, 14, 3,
                "RelentlessSwoop", "RendingDive"));
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
