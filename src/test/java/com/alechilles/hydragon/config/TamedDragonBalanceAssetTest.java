package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Test;

final class TamedDragonBalanceAssetTest {
    private static final Path ROLE_ROOT = Path.of("Server", "NPC", "Roles", "Creature", "HyDragon");
    private static final Path PROJECTILE_ROOT = Path.of("Server", "Projectiles", "HyDragon");
    private static final List<String> LOCALES = List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR");

    @Test
    void tamedRolesKeepBaseHealthAndMeleeFarBelowTheirWildCounterparts() throws IOException {
        JsonObject wildNordic = json(ROLE_ROOT.resolve("NordicDrake/NordicDrake.json"));
        JsonObject tamedNordic = json(ROLE_ROOT.resolve("NordicDrake/Tamed_NordicDrake.json"));
        JsonObject wildHydra = json(ROLE_ROOT.resolve("Hydra/Hydra.json"));
        JsonObject tamedHydra = json(ROLE_ROOT.resolve("Hydra/Tamed_Hydra.json"));
        JsonObject toxicHydra = json(ROLE_ROOT.resolve("Hydra/Tamed_Hydra_Toxic.json"));

        assertEquals(600, wildNordic.getAsJsonObject("Modify").get("MaxHealth").getAsInt());
        assertEquals(100, tamedNordic.getAsJsonObject("Modify").get("MaxHealth").getAsInt());
        assertEquals(650, wildHydra.getAsJsonObject("Modify").get("MaxHealth").getAsInt());
        assertEquals(150, tamedHydra.getAsJsonObject("Modify").get("MaxHealth").getAsInt());

        for (String variable : List.of("Swipe_Left_Damage", "Swipe_Right_Damage", "Stomp_Damage")) {
            assertEquals(34, physicalDamage(tamedNordic, variable), "Nordic " + variable);
        }
        for (String variable : List.of("Bite_Damage", "Swipe_Left_Damage", "Swipe_Right_Damage",
                "Stomp_Damage", "Tail_Spin_Damage")) {
            assertEquals(16, physicalDamage(tamedHydra, variable), "Hydra " + variable);
            assertEquals(16, physicalDamage(toxicHydra, variable), "Toxic Hydra " + variable);
        }
    }

    @Test
    void tamedProjectilesAndLingeringHazardsUseTheCompanionDamageProfile() throws IOException {
        assertProjectile("NordicDrake/Tamed_NordicDrake_Dragon_Fire_Ball.json", 9, 4.5);
        assertProjectile("Hydra/Tamed_Hydra_Ice_Ball.json", 11, 5.5);
        assertProjectile("Hydra/Tamed_Hydra_Toxic_Ball.json", 11, 5.5);
        assertProjectile("Hydra/Tamed_Hydra_Rain_Ice_Ball.json", 5, 2.5);
        assertProjectile("Hydra/Tamed_Hydra_Rain_Toxic_Ball.json", 5, 2.5);

        JsonObject tamedHydra = json(ROLE_ROOT.resolve("Hydra/Tamed_Hydra.json"));
        assertLaunch(tamedHydra, "Hydra_Ball_Launch", "Tamed_Hydra_Ice_Ball", null);
        assertLaunch(tamedHydra, "Hydra_Rain_Launch", "Tamed_Hydra_Rain_Ice_Ball",
                new Hazard(3.0, 3.0, 1.5));

        JsonObject toxicHydra = json(ROLE_ROOT.resolve("Hydra/Tamed_Hydra_Toxic.json"));
        assertLaunch(toxicHydra, "Hydra_Ball_Launch", "Tamed_Hydra_Toxic_Ball",
                new Hazard(2.0, 8.0, 1.0));
        assertLaunch(toxicHydra, "Hydra_Rain_Launch", "Tamed_Hydra_Rain_Toxic_Ball",
                new Hazard(3.0, 8.0, 1.0));

        JsonObject flight = json(Path.of("Server", "Tamework", "AvatarFlight", "HyDragonNordicDrake.json"));
        assertEquals("Root_NPC_Tamed_NordicDrake_Avatar_Fire_Ball", flight
                .getAsJsonObject("CombatAbilities").getAsJsonObject("Ability2").get("RootInteraction").getAsString());
        JsonObject root = json(Path.of("Server", "Item", "RootInteractions", "NPCs", "Creature", "HyDragon",
                "Root_NPC_Tamed_NordicDrake_Avatar_Fire_Ball.json"));
        assertEquals(List.of("Tamed_NordicDrake_Avatar_Fire_Ball"), strings(root.getAsJsonArray("Interactions")));
    }

    @Test
    void hydraProgressionIsSharedByBothVariantsAndHasBoundedTalentGrowth() throws IOException {
        JsonObject leveling = json(Path.of("Server", "Tamework", "Leveling", "HyDragonHydra.json"));
        assertEquals(List.of("Tamed_Hydra", "Tamed_Hydra_Toxic"), strings(leveling.getAsJsonArray("RoleIds")));
        assertEquals(30, leveling.getAsJsonObject("Levels").get("MaxLevel").getAsInt());
        assertEquals(0.042482758620689655, growth(leveling, "MaxHealthMultiplier"));
        assertEquals(0.038482758620689655, growth(leveling, "DamageDealtMultiplier"));
        assertTrue(leveling.getAsJsonObject("XpSources").getAsJsonObject("Summoned").get("Enabled").getAsBoolean());

        JsonArray talents = json(Path.of("Server", "Tamework", "Talents", "HyDragonHydra.json"))
                .getAsJsonArray("Talents");
        assertEquals(10, talents.size());
        Set<String> ids = talents.asList().stream().map(JsonElement::getAsJsonObject)
                .map(talent -> talent.get("Id").getAsString()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("Hydra_RazorFangs", "Hydra_RelentlessAssault", "Hydra_ThickScales",
                "Hydra_GuardiansHide", "Hydra_EnduringHunt", "Hydra_PrimalFocus",
                "Hydra_DragonboundPact", "Hydra_SwiftRecall", "Hydra_EternalWings",
                "Hydra_HornmastersCall"), ids);

        for (String locale : LOCALES) {
            Map<String, String> entries = localeEntries(locale);
            for (JsonElement element : talents) {
                JsonObject talent = element.getAsJsonObject();
                assertTrue(entries.containsKey(talent.get("Branch").getAsString()), locale + " branch translation");
                assertTrue(entries.containsKey(talent.get("DisplayName").getAsString()), locale + " name translation");
                assertTrue(entries.containsKey(talent.get("Description").getAsString()), locale + " description translation");
            }
        }
    }

    @Test
    void dragonSummonsStartAtFiveMinutesAndTimerTalentsReachTheTenAndFifteenMinuteCaps()
            throws IOException {
        JsonObject roster = json(Path.of("Server", "Tamework", "BondedCompanions", "Rosters",
                "HyDragonFullDragons.json"));
        assertEquals(300L, roster.get("SessionDurationSeconds").getAsLong());
        assertEquals(1_800L, roster.get("SummonCooldownSeconds").getAsLong());

        assertTimerTalentCap("HyDragonNordicDrake.json");
        assertTimerTalentCap("HyDragonHydra.json");
        assertTimerTalentCap("HyDragonRockDrake.json");
    }

    @Test
    void miniwyvernsAndRockDrakesStartAtHalfStrengthAndRecoverTheirCurrentCap() throws IOException {
        JsonObject miniLeveling = json(Path.of("Server", "Tamework", "Leveling", "HyDragonMiniwyvern.json"));
        assertEquals(0.034482758620689655, growth(miniLeveling, "MaxHealthMultiplier"));
        assertEquals(0.034482758620689655, growth(miniLeveling, "DamageDealtMultiplier"));
        for (String form : List.of("Fire", "Ice", "Lightning", "Nature", "Toxic", "Void", "Wild")) {
            JsonObject role = json(ROLE_ROOT.resolve("Wyvern_Mini/Tamed_Wyvern_Mini_" + form + ".json"));
            assertEquals(40, role.getAsJsonObject("Modify").get("MaxHealth").getAsInt(), form);
            assertEquals(8, physicalDamage(role, "Bite_Damage"), form);
        }

        JsonObject rockLeveling = json(Path.of("Server", "Tamework", "Leveling", "HyDragonRockDrake.json"));
        assertEquals(0.042482758620689655, growth(rockLeveling, "MaxHealthMultiplier"));
        assertEquals(0.038482758620689655, growth(rockLeveling, "DamageDealtMultiplier"));
        for (Map.Entry<String, Integer> expectedHealth : Map.of(
                "T1", 130, "T2", 180, "T3", 230).entrySet()) {
            JsonObject role = json(ROLE_ROOT.resolve("RockDrake/Tamed_RockDrake" + expectedHealth.getKey() + ".json"));
            assertEquals(expectedHealth.getValue().intValue(), role.getAsJsonObject("Modify").get("MaxHealth").getAsInt());
            assertEquals(17, physicalDamage(role, "Stomp_Damage"));
        }
    }

    private static void assertProjectile(String relativePath, double damage, double explosionDamage) throws IOException {
        JsonObject projectile = json(PROJECTILE_ROOT.resolve(relativePath));
        assertEquals(damage, projectile.get("Damage").getAsDouble());
        assertEquals(explosionDamage, projectile.getAsJsonObject("ExplosionConfig").get("EntityDamage").getAsDouble());
    }

    private static void assertLaunch(JsonObject role, String variable, String projectileId, Hazard hazard) {
        JsonObject launch = role.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars")
                .getAsJsonObject(variable).getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals(projectileId, launch.get("ProjectileId").getAsString());
        if (hazard == null) return;
        JsonObject actual = launch.getAsJsonObject("LingeringHazard");
        assertEquals(hazard.radius(), actual.get("Radius").getAsDouble());
        assertEquals(hazard.duration(), actual.get("DurationSeconds").getAsDouble());
        assertEquals(hazard.damagePerTick(), actual.get("DamagePerTick").getAsDouble());
    }

    private static int physicalDamage(JsonObject role, String variable) {
        return elementalDamage(role, variable, "Physical");
    }

    private static int elementalDamage(JsonObject role, String variable, String type) {
        return role.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars")
                .getAsJsonObject(variable).getAsJsonArray("Interactions").get(0).getAsJsonObject()
                .getAsJsonObject("DamageCalculator").getAsJsonObject("BaseDamage").get(type).getAsInt();
    }

    private static double growth(JsonObject leveling, String effectKey) {
        return leveling.getAsJsonObject("StatGrowth").getAsJsonArray("Effects").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(effect -> effectKey.equals(effect.get("EffectKey").getAsString())).findFirst().orElseThrow()
                .get("PerLevel").getAsDouble();
    }

    private static void assertTimerTalentCap(String file) throws IOException {
        JsonArray talents = json(Path.of("Server", "Tamework", "Talents", file))
                .getAsJsonArray("Talents");
        double durationMultiplier = effectProduct(talents, "SummonSessionDurationMultiplier");
        double cooldownMultiplier = effectProduct(talents, "SummonCooldownMultiplier");
        assertEquals(2.0, durationMultiplier, 0.000_001, file + " duration multiplier");
        assertEquals(0.5, cooldownMultiplier, 0.000_001, file + " cooldown multiplier");
        assertEquals(600L, Math.round(300L * durationMultiplier), file + " active duration");
        assertEquals(900L, Math.round(1_800L * cooldownMultiplier), file + " cooldown");
    }

    private static double effectProduct(JsonArray talents, String effectKey) {
        double product = 1.0;
        for (JsonElement talentElement : talents) {
            for (JsonElement effectElement : talentElement.getAsJsonObject()
                    .getAsJsonArray("Effects")) {
                JsonObject effect = effectElement.getAsJsonObject();
                if (effectKey.equals(effect.get("EffectKey").getAsString())) {
                    product *= effect.get("Multiplier").getAsDouble();
                }
            }
        }
        return product;
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<String> strings(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }

    private static Map<String, String> localeEntries(String locale) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of("Server", "Languages", locale, "server.lang"))) {
            int separator = line.indexOf('=');
            if (separator > 0 && !line.startsWith("#")) entries.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return entries;
    }

    private record Hazard(double radius, double duration, double damagePerTick) {
    }
}
