package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Contract for the separate Ice and Toxic Hydra talent trees. */
final class HydraTalentProgressionAssetTest {
    private static final Path ICE = Path.of(
            "Server", "Tamework", "Talents", "HyDragonHydra.json");
    private static final Path TOXIC = Path.of(
            "Server", "Tamework", "Talents", "HyDragonToxicHydra.json");
    private static final List<String> LOCALES =
            List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[^}]+}");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final double EPSILON = 0.000_001d;

    @Test
    void hydrasHaveSeparateRoleScopedTreesWithTheApprovedNordicShape() throws IOException {
        assertTree(load(ICE), List.of("Tamed_Hydra"), "hydragon.talents.ice_hydra.", iceTalents());
        assertTree(load(TOXIC), List.of("Tamed_Hydra_Toxic"),
                "hydragon.talents.toxic_hydra.", toxicTalents());
    }

    @Test
    void onlyTheToxicHydraTreeContainsAvatarFlightEffects() throws IOException {
        Set<String> iceEffects = effectKeys(load(ICE));
        Set<String> toxicEffects = effectKeys(load(TOXIC));

        assertTrue(iceEffects.stream().noneMatch(key -> key.startsWith("AvatarFlight")));
        assertTrue(toxicEffects.containsAll(Set.of(
                "AvatarFlightVigourCapacityMultiplier",
                "AvatarFlightVigourRechargeRateMultiplier",
                "AvatarFlightForwardBoostCostMultiplier",
                "AvatarFlightForwardBoostImpulseMultiplier",
                "AvatarFlightGlideSinkMultiplier",
                "AvatarFlightClimbLiftMultiplier")));
    }

    @Test
    void everyHydraTalentKeyIsTranslatedInEverySupportedLocale() throws IOException {
        Set<String> requiredKeys = new LinkedHashSet<>();
        collectLocaleKeys(load(ICE), requiredKeys);
        collectLocaleKeys(load(TOXIC), requiredKeys);
        assertEquals(83, requiredKeys.size());

        Map<String, String> english = localeEntries("en-US");
        for (String key : requiredKeys) {
            assertNotNull(english.get(key), "en-US missing " + key);
        }

        for (String locale : LOCALES) {
            List<String> lines = Files.readAllLines(
                    Path.of("Server", "Languages", locale, "server.lang"));
            Map<String, String> entries = localeEntries(locale);
            assertTrue(lines.stream().noneMatch(line -> line.startsWith("hydragon.talents.hydra.")),
                    locale + " retains legacy shared Hydra talent keys");
            for (String key : requiredKeys) {
                assertEquals(1L, lines.stream().filter(line -> line.startsWith(key + "=")).count(),
                        locale + " must define " + key + " exactly once");
                String translated = entries.get(key);
                assertNotNull(translated, locale + " missing " + key);
                assertFalse(translated.isBlank(), locale + " has blank " + key);
                assertEquals(placeholders(english.get(key)), placeholders(translated),
                        locale + " placeholder mismatch for " + key);
                assertEquals(numbers(english.get(key)), numbers(translated),
                        locale + " numeric meaning mismatch for " + key);
                if (!locale.equals("en-US")) {
                    assertNotEquals(english.get(key), translated,
                            locale + " must translate " + key);
                }
            }
        }
    }

    private static void assertTree(JsonObject config,
                                   List<String> roles,
                                   String localePrefix,
                                   Map<String, TalentExpectation> expected) {
        assertTrue(config.get("Enabled").getAsBoolean());
        assertEquals(100, config.get("Priority").getAsInt());
        assertEquals(1, config.get("AllocationRevision").getAsInt());
        assertEquals(roles, strings(config.getAsJsonArray("RoleIds")));

        Map<String, JsonObject> actual = talentsById(config.getAsJsonArray("Talents"));
        assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<String, TalentExpectation> entry : expected.entrySet()) {
            String id = entry.getKey();
            TalentExpectation want = entry.getValue();
            JsonObject talent = actual.get(id);
            assertEquals(want.tier(), talent.get("Tier").getAsInt(), id);
            assertEquals(want.branch(), talent.get("Branch").getAsString(), id);
            assertEquals(want.cost(), talent.get("PointCost").getAsInt(), id);
            assertEquals(want.level(), talent.get("MinLevel").getAsInt(), id);
            assertEquals(want.icon(), talent.get("IconPath").getAsString(), id);
            assertEquals(want.requires(), talent.has("RequiresTalentIds")
                    ? strings(talent.getAsJsonArray("RequiresTalentIds")) : List.of(), id);
            assertTrue(talent.get("DisplayName").getAsString().startsWith(localePrefix), id);
            assertTrue(talent.get("DisplayName").getAsString().endsWith(".name"), id);
            assertTrue(talent.get("Description").getAsString().startsWith(localePrefix), id);
            assertTrue(talent.get("Description").getAsString().endsWith(".description"), id);
            assertEffects(talent.getAsJsonArray("Effects"), want.effects(), id);
        }
    }

    private static Map<String, TalentExpectation> toxicTalents() {
        String flight = "hydragon.talents.toxic_hydra.branch.plaguewing";
        String attack = "hydragon.talents.toxic_hydra.branch.venomous_onslaught";
        String guard = "hydragon.talents.toxic_hydra.branch.blightguard";
        String summon = "hydragon.talents.toxic_hydra.branch.broodcallers_pact";
        Map<String, TalentExpectation> expected = new LinkedHashMap<>();
        expected.put("ToxicHydra_VirulentLift", talent(1, flight, 1, 1, SWIFTNESS, List.of(),
                Map.of("AvatarFlightVigourCapacityMultiplier", 1.15d)));
        expected.put("ToxicHydra_CausticCurrent", talent(2, flight, 2, 6, SWIFTNESS,
                List.of("ToxicHydra_VirulentLift"),
                Map.of("AvatarFlightVigourRechargeRateMultiplier", 1.15d)));
        expected.put("ToxicHydra_Venomwake", talent(2, flight, 2, 6, STRENGTH,
                List.of("ToxicHydra_VirulentLift"),
                Map.of("AvatarFlightForwardBoostCostMultiplier", 0.88d)));
        expected.put("ToxicHydra_MiasmaMantle", talent(3, flight, 3, 14, TOUGHNESS,
                List.of("ToxicHydra_CausticCurrent"),
                Map.of("AvatarFlightGlideSinkMultiplier", 0.86d)));
        expected.put("ToxicHydra_BlightSurge", talent(3, flight, 3, 14, STRENGTH,
                List.of("ToxicHydra_Venomwake"),
                Map.of("AvatarFlightForwardBoostImpulseMultiplier", 1.15d)));
        expected.put("ToxicHydra_PlagueSovereign", talent(4, flight, 4, 24, SWIFTNESS,
                List.of("ToxicHydra_MiasmaMantle", "ToxicHydra_BlightSurge"), Map.of(
                        "AvatarFlightVigourCapacityMultiplier", 1.10d,
                        "AvatarFlightClimbLiftMultiplier", 1.12d)));
        addAttackBranch(expected, "ToxicHydra_", attack,
                "VenomDiscipline", "CausticHeart", "ToxicMomentum", "RuinousMiasma",
                "BarbedTalons", "ThreefoldBlight");
        addGuardBranch(expected, "ToxicHydra_", guard,
                "BogscaleHide", "AcidHardenedScales", "PatientStalker", "DeathlessBrood",
                "Blightscar", "PestilentBulwark");
        addSummonBranch(expected, "ToxicHydra_", summon,
                "BlightboundPact", "SwiftRecall", "LingeringPresence", "BroodmastersCall");
        return expected;
    }

    private static Map<String, TalentExpectation> iceTalents() {
        String attack = "hydragon.talents.ice_hydra.branch.winters_wrath";
        String guard = "hydragon.talents.ice_hydra.branch.glacierguard";
        String summon = "hydragon.talents.ice_hydra.branch.broodcallers_pact";
        Map<String, TalentExpectation> expected = new LinkedHashMap<>();
        addAttackBranch(expected, "IceHydra_", attack,
                "FrostDiscipline", "FrozenCore", "RimeboundMomentum", "ShatteringBreath",
                "IcecladTalons", "ThreefoldWinter");
        addGuardBranch(expected, "IceHydra_", guard,
                "GlacialHide", "PermafrostScales", "LongHibernation", "UnyieldingWinter",
                "Frostscar", "FrozenBulwark");
        addSummonBranch(expected, "IceHydra_", summon,
                "FrostboundPact", "SwiftRecall", "EndlessWinter", "BroodmastersCall");
        return expected;
    }

    private static void addAttackBranch(Map<String, TalentExpectation> expected,
                                        String prefix,
                                        String branch,
                                        String root,
                                        String defense,
                                        String offense,
                                        String offenseUpgrade,
                                        String health,
                                        String capstone) {
        expected.put(prefix + root, talent(1, branch, 2, 1, STRENGTH, List.of(),
                Map.of("DamageDealtMultiplier", 1.02d)));
        expected.put(prefix + defense, talent(2, branch, 2, 8, TOUGHNESS,
                List.of(prefix + root), Map.of("DamageTakenMultiplier", toughness(0.03d))));
        expected.put(prefix + offense, talent(2, branch, 3, 8, STRENGTH,
                List.of(prefix + root), Map.of("DamageDealtMultiplier", 1.035d)));
        expected.put(prefix + offenseUpgrade, talent(3, branch, 4, 16, STRENGTH,
                List.of(prefix + offense), Map.of("DamageDealtMultiplier", 1.045d)));
        expected.put(prefix + health, talent(3, branch, 3, 16, HEALTH,
                List.of(prefix + defense), Map.of("MaxHealthMultiplier", 1.05d)));
        expected.put(prefix + capstone, talent(4, branch, 4, 24, STRENGTH,
                List.of(prefix + offenseUpgrade, prefix + health), Map.of(
                        "DamageDealtMultiplier", 1.04d,
                        "DamageTakenMultiplier", toughness(0.03d))));
    }

    private static void addGuardBranch(Map<String, TalentExpectation> expected,
                                       String prefix,
                                       String branch,
                                       String root,
                                       String toughness,
                                       String health,
                                       String healthUpgrade,
                                       String toughnessUpgrade,
                                       String capstone) {
        expected.put(prefix + root, talent(1, branch, 1, 1, HEALTH, List.of(),
                Map.of("MaxHealthMultiplier", 1.04d)));
        expected.put(prefix + toughness, talent(2, branch, 2, 7, TOUGHNESS,
                List.of(prefix + root), Map.of("DamageTakenMultiplier", toughness(0.04d))));
        expected.put(prefix + health, talent(2, branch, 2, 7, HEALTH,
                List.of(prefix + root), Map.of("MaxHealthMultiplier", 1.04d)));
        expected.put(prefix + healthUpgrade, talent(3, branch, 3, 15, HEALTH,
                List.of(prefix + health), Map.of("MaxHealthMultiplier", 1.06d)));
        expected.put(prefix + toughnessUpgrade, talent(3, branch, 3, 15, TOUGHNESS,
                List.of(prefix + toughness), Map.of("DamageTakenMultiplier", toughness(0.06d))));
        expected.put(prefix + capstone, talent(4, branch, 4, 24, HEALTH,
                List.of(prefix + healthUpgrade, prefix + toughnessUpgrade), Map.of(
                        "MaxHealthMultiplier", 1.05d,
                        "DamageTakenMultiplier", toughness(0.04d))));
    }

    private static void addSummonBranch(Map<String, TalentExpectation> expected,
                                        String prefix,
                                        String branch,
                                        String pact,
                                        String recall,
                                        String duration,
                                        String capstone) {
        expected.put(prefix + pact, talent(1, branch, 1, 1, SWIFTNESS, List.of(),
                Map.of("SummonSessionDurationMultiplier", 1.25d)));
        expected.put(prefix + recall, talent(2, branch, 2, 8, SWIFTNESS,
                List.of(prefix + pact), Map.of("SummonCooldownMultiplier", 0.8d)));
        expected.put(prefix + duration, talent(3, branch, 3, 16, SWIFTNESS,
                List.of(prefix + recall), Map.of("SummonSessionDurationMultiplier", 1.6d)));
        expected.put(prefix + capstone, talent(4, branch, 4, 24, SWIFTNESS,
                List.of(prefix + duration), Map.of("SummonCooldownMultiplier", 0.625d)));
    }

    private static TalentExpectation talent(int tier,
                                             String branch,
                                             int cost,
                                             int level,
                                             String icon,
                                             List<String> requires,
                                             Map<String, Double> effects) {
        return new TalentExpectation(tier, branch, cost, level, icon, requires, effects);
    }

    private static JsonObject load(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Map<String, JsonObject> talentsById(JsonArray talents) {
        Map<String, JsonObject> byId = new LinkedHashMap<>();
        for (JsonElement element : talents) {
            JsonObject talent = element.getAsJsonObject();
            String id = talent.get("Id").getAsString();
            assertFalse(byId.containsKey(id), "duplicate talent ID " + id);
            byId.put(id, talent);
        }
        return byId;
    }

    private static Set<String> effectKeys(JsonObject config) {
        Set<String> keys = new LinkedHashSet<>();
        for (JsonElement talentElement : config.getAsJsonArray("Talents")) {
            for (JsonElement effectElement : talentElement.getAsJsonObject().getAsJsonArray("Effects")) {
                keys.add(effectElement.getAsJsonObject().get("EffectKey").getAsString());
            }
        }
        return keys;
    }

    private static void collectLocaleKeys(JsonObject config, Set<String> keys) {
        for (JsonElement element : config.getAsJsonArray("Talents")) {
            JsonObject talent = element.getAsJsonObject();
            keys.add(talent.get("Branch").getAsString());
            keys.add(talent.get("DisplayName").getAsString());
            keys.add(talent.get("Description").getAsString());
        }
    }

    private static Map<String, String> localeEntries(String locale) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(
                Path.of("Server", "Languages", locale, "server.lang"))) {
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
        return List.copyOf(placeholders);
    }

    private static List<String> numbers(String value) {
        Matcher matcher = NUMBER.matcher(value);
        java.util.ArrayList<String> numbers = new java.util.ArrayList<>();
        while (matcher.find()) {
            numbers.add(matcher.group().replace(',', '.'));
        }
        return List.copyOf(numbers);
    }

    private static void assertEffects(JsonArray effects, Map<String, Double> expected, String id) {
        Map<String, Double> actual = new LinkedHashMap<>();
        for (JsonElement element : effects) {
            JsonObject effect = element.getAsJsonObject();
            actual.put(effect.get("EffectKey").getAsString(), effect.get("Multiplier").getAsDouble());
        }
        assertEquals(expected.keySet(), actual.keySet(), id);
        expected.forEach((key, value) -> assertEquals(value, actual.get(key), EPSILON, id + " " + key));
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }

    private static double toughness(double reduction) {
        return 1.0d / (1.0d - reduction);
    }

    private static final String STRENGTH = "Tamework/LinkedPanelIcons/Trait_Strength.png";
    private static final String HEALTH = "Tamework/LinkedPanelIcons/Trait_Health.png";
    private static final String TOUGHNESS = "Tamework/LinkedPanelIcons/Trait_Toughness.png";
    private static final String SWIFTNESS = "Tamework/LinkedPanelIcons/Trait_Swiftness.png";

    private record TalentExpectation(int tier,
                                     String branch,
                                     int cost,
                                     int level,
                                     String icon,
                                     List<String> requires,
                                     Map<String, Double> effects) {
    }
}
