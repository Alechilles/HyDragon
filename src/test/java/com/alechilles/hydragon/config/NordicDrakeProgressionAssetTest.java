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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class NordicDrakeProgressionAssetTest {
    private static final Path LEVELING_PATH = Path.of(
            "src/main/resources", "Server", "Tamework", "Leveling", "HyDragonNordicDrake.json");
    private static final Path TALENTS_PATH = Path.of(
            "src/main/resources", "Server", "Tamework", "Talents", "HyDragonNordicDrake.json");
    private static final List<String> LOCALES = List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR");
    private static final String LOCALE_PREFIX = "hydragon.talents.nordic_drake.";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]+}");
    private static final double EPSILON = 0.000_001d;

    @Test
    void levelingConfigHasTheApprovedNordicDrakeCurveSourcesAndGrowth() throws IOException {
        JsonObject leveling = loadJson(LEVELING_PATH);

        assertTrue(leveling.get("Enabled").getAsBoolean());
        assertEquals(100, leveling.get("Priority").getAsInt());
        assertEquals(List.of("Tamed_NordicDrake"), strings(leveling.getAsJsonArray("RoleIds")));

        JsonObject levels = leveling.getAsJsonObject("Levels");
        assertEquals(30, levels.get("MaxLevel").getAsInt());
        assertDouble(155.0d, levels.get("BaseXp").getAsDouble());
        assertDouble(1.09d, levels.get("GrowthFactor").getAsDouble());

        JsonObject sources = leveling.getAsJsonObject("XpSources");
        assertFalse(sources.getAsJsonObject("Feed").get("Enabled").getAsBoolean());
        assertFalse(sources.getAsJsonObject("Harvest").get("Enabled").getAsBoolean());
        assertFalse(sources.getAsJsonObject("Breeding").get("Enabled").getAsBoolean());

        JsonObject combat = sources.getAsJsonObject("Combat");
        assertTrue(combat.get("Enabled").getAsBoolean());
        assertDouble(0.35d, combat.get("DamageDealtXpPerPoint").getAsDouble());
        assertDouble(0.12d, combat.get("DamageTakenXpPerPoint").getAsDouble());
        assertDouble(2.0d, combat.get("MinimumDamageEvent").getAsDouble());
        assertFalse(combat.get("AwardVsPlayers").getAsBoolean());
        assertFalse(combat.get("AwardVsOwnedAllies").getAsBoolean());

        JsonObject flight = sources.getAsJsonObject("Flight");
        assertTrue(flight.get("Enabled").getAsBoolean());
        assertDouble(0.15d, flight.get("XpPerQualifiedSecond").getAsDouble());
        assertDouble(10.0d, flight.get("AwardIntervalSeconds").getAsDouble());
        assertDouble(9.0d, flight.get("MaxXpPerMinute").getAsDouble());

        assertGrowthEffects(leveling.getAsJsonObject("StatGrowth").getAsJsonArray("Effects"), Map.of(
                "MaxHealthMultiplier", 0.042482758620689655d,
                "DamageDealtMultiplier", 0.038482758620689655d));
        assertEquals(1, leveling.getAsJsonObject("TalentPoints").get("PointsPerLevel").getAsInt());
    }

    @Test
    void talentTreeHasAllApprovedNodesIncludingTheSummonTimerLine() throws IOException {
        JsonObject talentConfig = loadJson(TALENTS_PATH);
        assertTrue(talentConfig.get("Enabled").getAsBoolean());
        assertEquals(100, talentConfig.get("Priority").getAsInt());
        assertEquals(List.of("Tamed_NordicDrake"), strings(talentConfig.getAsJsonArray("RoleIds")));

        Map<String, JsonObject> talents = talentsById(talentConfig.getAsJsonArray("Talents"));
        assertEquals(22, talents.size());
        assertEquals(expectedTalents().keySet(), talents.keySet());
        for (Map.Entry<String, TalentExpectation> entry : expectedTalents().entrySet()) {
            JsonObject talent = talents.get(entry.getKey());
            TalentExpectation expected = entry.getValue();
            TalentTextExpectation text = expectedTalentText().get(entry.getKey());
            assertNotNull(talent, entry.getKey());
            assertEquals(expected.branch(), talent.get("Branch").getAsString(), entry.getKey());
            assertNotNull(text, entry.getKey() + " text expectation");
            assertEquals(text.displayName(), talent.get("DisplayName").getAsString(), entry.getKey());
            assertEquals(text.description(), talent.get("Description").getAsString(), entry.getKey());
            assertEquals(expected.cost(), talent.get("PointCost").getAsInt(), entry.getKey());
            assertEquals(expected.level(), talent.get("MinLevel").getAsInt(), entry.getKey());
            assertEquals(expected.requires(), talent.has("RequiresTalentIds")
                    ? strings(talent.getAsJsonArray("RequiresTalentIds")) : List.of(), entry.getKey());
            assertEffects(talent.getAsJsonArray("Effects"), expected.effects());
        }

        Set<String> effectKeys = new java.util.HashSet<>();
        for (JsonObject talent : talents.values()) {
            for (JsonElement effect : talent.getAsJsonArray("Effects")) {
                effectKeys.add(effect.getAsJsonObject().get("EffectKey").getAsString());
            }
        }
        assertTrue(effectKeys.containsAll(Set.of(
                "AvatarFlightVigourCapacityMultiplier",
                "AvatarFlightVigourRechargeRateMultiplier",
                "AvatarFlightForwardBoostCostMultiplier",
                "AvatarFlightForwardBoostImpulseMultiplier",
                "AvatarFlightGlideSinkMultiplier",
                "AvatarFlightClimbLiftMultiplier",
                "SummonSessionDurationMultiplier",
                "SummonCooldownMultiplier")));
    }

    @Test
    void allNordicDrakeLocaleKeysAndPlaceholdersMatchEnglish() throws IOException {
        Map<String, String> english = localeEntries("en-US");
        JsonObject talentConfig = loadJson(TALENTS_PATH);
        Set<String> requiredKeys = referencedLocaleKeys(talentConfig.getAsJsonArray("Talents"));
        assertEquals(expectedLocaleKeys(), requiredKeys);
        Map<String, String> required = new LinkedHashMap<>();
        for (String key : requiredKeys) {
            String englishValue = english.get(key);
            assertNotNull(englishValue, "en-US missing " + key);
            required.put(key, englishValue);
        }

        for (String locale : LOCALES) {
            Map<String, String> entries = localeEntries(locale);
            for (Map.Entry<String, String> englishEntry : required.entrySet()) {
                String translated = entries.get(englishEntry.getKey());
                assertNotNull(translated, locale + " missing " + englishEntry.getKey());
                assertEquals(placeholders(englishEntry.getValue()), placeholders(translated),
                        locale + " placeholder mismatch for " + englishEntry.getKey());
            }
        }
    }

    private static Map<String, TalentExpectation> expectedTalents() {
        String aerial = "hydragon.talents.nordic_drake.branch.aerial_mastery";
        String war = "hydragon.talents.nordic_drake.branch.war_drake";
        String wyrmguard = "hydragon.talents.nordic_drake.branch.wyrmguard";
        String summoner = "hydragon.talents.nordic_drake.branch.summoners_pact";
        Map<String, TalentExpectation> expected = new LinkedHashMap<>();
        expected.put("NordicDrake_NorthwindResolve", talent(aerial, 1, 1, List.of(), Map.of(
                "AvatarFlightVigourCapacityMultiplier", 1.15d)));
        expected.put("NordicDrake_SustainedCurrent", talent(aerial, 2, 6, List.of("NordicDrake_NorthwindResolve"), Map.of(
                "AvatarFlightVigourRechargeRateMultiplier", 1.15d)));
        expected.put("NordicDrake_Flamewake", talent(aerial, 2, 6, List.of("NordicDrake_NorthwindResolve"), Map.of(
                "AvatarFlightForwardBoostCostMultiplier", 0.88d)));
        expected.put("NordicDrake_ThermalGuard", talent(aerial, 3, 14, List.of("NordicDrake_SustainedCurrent"), Map.of(
                "AvatarFlightGlideSinkMultiplier", 0.86d)));
        expected.put("NordicDrake_Skyrend", talent(aerial, 3, 14, List.of("NordicDrake_Flamewake"), Map.of(
                "AvatarFlightForwardBoostImpulseMultiplier", 1.15d)));
        expected.put("NordicDrake_StormSovereign", talent(aerial, 4, 24,
                List.of("NordicDrake_ThermalGuard", "NordicDrake_Skyrend"), Map.of(
                        "AvatarFlightVigourCapacityMultiplier", 1.10d,
                        "AvatarFlightClimbLiftMultiplier", 1.12d)));
        expected.put("NordicDrake_EmberDiscipline", talent(war, 2, 1, List.of(), Map.of(
                "DamageDealtMultiplier", 1.02d)));
        expected.put("NordicDrake_FurnaceHeart", talent(war, 2, 8, List.of("NordicDrake_EmberDiscipline"), Map.of(
                "DamageTakenMultiplier", toughnessForReduction(0.03d))));
        expected.put("NordicDrake_ScorchingMomentum", talent(war, 3, 8, List.of("NordicDrake_EmberDiscipline"), Map.of(
                "DamageDealtMultiplier", 1.035d)));
        expected.put("NordicDrake_RuinousBreath", talent(war, 4, 16, List.of("NordicDrake_ScorchingMomentum"), Map.of(
                "DamageDealtMultiplier", 1.045d)));
        expected.put("NordicDrake_IronTalons", talent(war, 3, 16, List.of("NordicDrake_FurnaceHeart"), Map.of(
                "MaxHealthMultiplier", 1.05d)));
        expected.put("NordicDrake_JarlsBane", talent(war, 4, 24,
                List.of("NordicDrake_RuinousBreath", "NordicDrake_IronTalons"), Map.of(
                        "DamageDealtMultiplier", 1.04d,
                        "DamageTakenMultiplier", toughnessForReduction(0.03d))));
        expected.put("NordicDrake_RunestoneHide", talent(wyrmguard, 1, 1, List.of(), Map.of(
                "MaxHealthMultiplier", 1.04d)));
        expected.put("NordicDrake_GlacierScales", talent(wyrmguard, 2, 7, List.of("NordicDrake_RunestoneHide"), Map.of(
                "DamageTakenMultiplier", toughnessForReduction(0.04d))));
        expected.put("NordicDrake_LongVigil", talent(wyrmguard, 2, 7, List.of("NordicDrake_RunestoneHide"), Map.of(
                "MaxHealthMultiplier", 1.04d)));
        expected.put("NordicDrake_Unyielding", talent(wyrmguard, 3, 15, List.of("NordicDrake_LongVigil"), Map.of(
                "MaxHealthMultiplier", 1.06d)));
        expected.put("NordicDrake_Sagascar", talent(wyrmguard, 3, 15, List.of("NordicDrake_GlacierScales"), Map.of(
                "DamageTakenMultiplier", toughnessForReduction(0.06d))));
        expected.put("NordicDrake_NorthernBulwark", talent(wyrmguard, 4, 24,
                List.of("NordicDrake_Unyielding", "NordicDrake_Sagascar"), Map.of(
                        "MaxHealthMultiplier", 1.05d,
                        "DamageTakenMultiplier", toughnessForReduction(0.04d))));
        expected.put("NordicDrake_DragonboundPact", talent(summoner, 1, 1, List.of(), Map.of(
                "SummonSessionDurationMultiplier", 1.25d)));
        expected.put("NordicDrake_SwiftRecall", talent(summoner, 2, 8,
                List.of("NordicDrake_DragonboundPact"), Map.of(
                        "SummonCooldownMultiplier", 0.8d)));
        expected.put("NordicDrake_EternalWings", talent(summoner, 3, 16,
                List.of("NordicDrake_SwiftRecall"), Map.of(
                        "SummonSessionDurationMultiplier", 1.6d)));
        expected.put("NordicDrake_HornmastersCall", talent(summoner, 4, 24,
                List.of("NordicDrake_EternalWings"), Map.of(
                        "SummonCooldownMultiplier", 0.625d)));
        return expected;
    }

    private static Map<String, TalentTextExpectation> expectedTalentText() {
        Map<String, TalentTextExpectation> expected = new LinkedHashMap<>();
        expected.put("NordicDrake_NorthwindResolve", text("northwind_resolve"));
        expected.put("NordicDrake_SustainedCurrent", text("sustained_current"));
        expected.put("NordicDrake_Flamewake", text("flamewake"));
        expected.put("NordicDrake_ThermalGuard", text("thermal_guard"));
        expected.put("NordicDrake_Skyrend", text("skyrend"));
        expected.put("NordicDrake_StormSovereign", text("storm_sovereign"));
        expected.put("NordicDrake_EmberDiscipline", text("ember_discipline"));
        expected.put("NordicDrake_FurnaceHeart", text("furnace_heart"));
        expected.put("NordicDrake_ScorchingMomentum", text("scorching_momentum"));
        expected.put("NordicDrake_RuinousBreath", text("ruinous_breath"));
        expected.put("NordicDrake_IronTalons", text("iron_talons"));
        expected.put("NordicDrake_JarlsBane", text("jarls_bane"));
        expected.put("NordicDrake_RunestoneHide", text("runestone_hide"));
        expected.put("NordicDrake_GlacierScales", text("glacier_scales"));
        expected.put("NordicDrake_LongVigil", text("long_vigil"));
        expected.put("NordicDrake_Unyielding", text("unyielding"));
        expected.put("NordicDrake_Sagascar", text("sagascar"));
        expected.put("NordicDrake_NorthernBulwark", text("northern_bulwark"));
        expected.put("NordicDrake_DragonboundPact", text("dragonbound_pact"));
        expected.put("NordicDrake_SwiftRecall", text("swift_recall"));
        expected.put("NordicDrake_EternalWings", text("eternal_wings"));
        expected.put("NordicDrake_HornmastersCall", text("hornmasters_call"));
        return expected;
    }

    private static Set<String> expectedLocaleKeys() {
        Set<String> expected = new java.util.LinkedHashSet<>(Set.of(
                "hydragon.talents.nordic_drake.branch.aerial_mastery",
                "hydragon.talents.nordic_drake.branch.war_drake",
                "hydragon.talents.nordic_drake.branch.wyrmguard",
                "hydragon.talents.nordic_drake.branch.summoners_pact"));
        for (TalentTextExpectation text : expectedTalentText().values()) {
            expected.add(text.displayName());
            expected.add(text.description());
        }
        return Set.copyOf(expected);
    }

    private static TalentExpectation talent(String branch, int cost, int level, List<String> requires,
                                             Map<String, Double> effects) {
        return new TalentExpectation(branch, cost, level, requires, effects);
    }

    private static double toughnessForReduction(double reduction) {
        return 1.0d / (1.0d - reduction);
    }

    private static TalentTextExpectation text(String node) {
        return new TalentTextExpectation(
                LOCALE_PREFIX + node + ".name",
                LOCALE_PREFIX + node + ".description");
    }

    private static JsonObject loadJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Map<String, JsonObject> talentsById(JsonArray talents) {
        Map<String, JsonObject> byId = new LinkedHashMap<>();
        for (JsonElement element : talents) {
            JsonObject talent = element.getAsJsonObject();
            assertTrue(byId.put(talent.get("Id").getAsString(), talent) == null, "duplicate talent ID");
        }
        return byId;
    }

    private static Set<String> referencedLocaleKeys(JsonArray talents) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        for (JsonElement element : talents) {
            JsonObject talent = element.getAsJsonObject();
            keys.add(talent.get("Branch").getAsString());
            keys.add(talent.get("DisplayName").getAsString());
            keys.add(talent.get("Description").getAsString());
        }
        assertTrue(keys.stream().allMatch(key -> key.startsWith(LOCALE_PREFIX)));
        return Set.copyOf(keys);
    }

    private static void assertEffects(JsonArray effects, Map<String, Double> expected) {
        Map<String, Double> actual = new LinkedHashMap<>();
        for (JsonElement element : effects) {
            JsonObject effect = element.getAsJsonObject();
            assertTrue(actual.put(effect.get("EffectKey").getAsString(), effect.get("Multiplier").getAsDouble()) == null,
                    "duplicate effect key");
        }
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((key, value) -> assertDouble(value, actual.get(key)));
    }

    private static void assertGrowthEffects(JsonArray effects, Map<String, Double> expected) {
        Map<String, Double> actual = new LinkedHashMap<>();
        for (JsonElement element : effects) {
            JsonObject effect = element.getAsJsonObject();
            assertTrue(actual.put(effect.get("EffectKey").getAsString(), effect.get("PerLevel").getAsDouble()) == null,
                    "duplicate growth effect key");
        }
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((key, value) -> assertDouble(value, actual.get(key)));
    }

    private static List<String> strings(JsonArray values) {
        java.util.ArrayList<String> strings = new java.util.ArrayList<>();
        for (JsonElement value : values) {
            strings.add(value.getAsString());
        }
        return List.copyOf(strings);
    }

    private static Map<String, String> localeEntries(String locale) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of("src/main/resources", "Server", "Languages", locale, "server.lang"))) {
            int separator = line.indexOf('=');
            if (separator > 0 && !line.startsWith("#")) {
                entries.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return entries;
    }

    private static List<String> placeholders(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        java.util.ArrayList<String> placeholders = new java.util.ArrayList<>();
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
        return placeholders;
    }

    private static void assertDouble(double expected, double actual) {
        assertEquals(expected, actual, EPSILON);
    }

    private record TalentExpectation(String branch, int cost, int level, List<String> requires,
                                     Map<String, Double> effects) {
    }

    private record TalentTextExpectation(String displayName, String description) {
    }
}
