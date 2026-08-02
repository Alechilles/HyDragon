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
        assertEquals(220, tamedNordic.getAsJsonObject("Modify").get("MaxHealth").getAsInt());
        assertEquals(800, wildHydra.getAsJsonObject("Modify").get("MaxHealth").getAsInt());
        assertEquals(320, tamedHydra.getAsJsonObject("Modify").get("MaxHealth").getAsInt());

        for (String variable : List.of("Bite_Damage", "Swipe_Left_Damage", "Swipe_Right_Damage",
                "Stomp_Damage", "Tail_Spin_Damage")) {
            assertEquals(32, physicalDamage(tamedNordic, variable), "Nordic " + variable);
            assertEquals(32, physicalDamage(tamedHydra, variable), "Hydra " + variable);
            assertEquals(32, physicalDamage(toxicHydra, variable), "Toxic Hydra " + variable);
        }
        assertEquals(10, elementalDamage(tamedNordic, "Flame_Breath_Damage", "Fire"));
    }

    @Test
    void tamedProjectilesAndLingeringHazardsUseTheCompanionDamageProfile() throws IOException {
        assertProjectile("NordicDrake/Tamed_NordicDrake_Dragon_Fire_Ball.json", 18, 9);
        assertProjectile("Hydra/Tamed_Hydra_Ice_Ball.json", 22, 11);
        assertProjectile("Hydra/Tamed_Hydra_Toxic_Ball.json", 22, 11);
        assertProjectile("Hydra/Tamed_Hydra_Rain_Ice_Ball.json", 10, 5);
        assertProjectile("Hydra/Tamed_Hydra_Rain_Toxic_Ball.json", 10, 5);

        JsonObject tamedHydra = json(ROLE_ROOT.resolve("Hydra/Tamed_Hydra.json"));
        assertLaunch(tamedHydra, "Hydra_Ball_Launch", "Tamed_Hydra_Ice_Ball", null);
        assertLaunch(tamedHydra, "Hydra_Rain_Launch", "Tamed_Hydra_Rain_Ice_Ball",
                new Hazard(3.0, 3.0, 3.0));

        JsonObject toxicHydra = json(ROLE_ROOT.resolve("Hydra/Tamed_Hydra_Toxic.json"));
        assertLaunch(toxicHydra, "Hydra_Ball_Launch", "Tamed_Hydra_Toxic_Ball",
                new Hazard(2.0, 8.0, 2.0));
        assertLaunch(toxicHydra, "Hydra_Rain_Launch", "Tamed_Hydra_Rain_Toxic_Ball",
                new Hazard(3.0, 8.0, 2.0));

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
        assertEquals(0.004, growth(leveling, "MaxHealthMultiplier"));
        assertEquals(0.002, growth(leveling, "DamageDealtMultiplier"));
        assertTrue(leveling.getAsJsonObject("XpSources").getAsJsonObject("Summoned").get("Enabled").getAsBoolean());

        JsonArray talents = json(Path.of("Server", "Tamework", "Talents", "HyDragonHydra.json"))
                .getAsJsonArray("Talents");
        assertEquals(6, talents.size());
        Set<String> ids = talents.asList().stream().map(JsonElement::getAsJsonObject)
                .map(talent -> talent.get("Id").getAsString()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("Hydra_RazorFangs", "Hydra_RelentlessAssault", "Hydra_ThickScales",
                "Hydra_GuardiansHide", "Hydra_EnduringHunt", "Hydra_PrimalFocus"), ids);

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

    private static void assertProjectile(String relativePath, int damage, int explosionDamage) throws IOException {
        JsonObject projectile = json(PROJECTILE_ROOT.resolve(relativePath));
        assertEquals(damage, projectile.get("Damage").getAsInt());
        assertEquals(explosionDamage, projectile.getAsJsonObject("ExplosionConfig").get("EntityDamage").getAsInt());
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
