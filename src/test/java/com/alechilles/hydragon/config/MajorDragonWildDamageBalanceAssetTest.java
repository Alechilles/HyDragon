package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MajorDragonWildDamageBalanceAssetTest {
    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final double SCALE = 0.85;

    @Test
    void wildHydraDamageIsReducedAboutFifteenPercentWithoutChangingTamedOrToxicDamage() throws Exception {
        JsonObject wildVars = roleVars("Hydra/Hydra.json");
        for (String attack : List.of(
                "Bite_Damage", "Swipe_Left_Damage", "Swipe_Right_Damage", "Stomp_Damage",
                "Tail_Spin_Damage")) {
            assertEquals(68 * SCALE, physicalDamage(wildVars, attack), 0.000001, attack);
        }

        JsonObject direct = json("Server/Projectiles/HyDragon/Hydra/Hydra_Ice_Ball.json");
        assertEquals(40 * SCALE, direct.get("Damage").getAsDouble(), 0.000001);
        assertEquals(20 * SCALE,
                direct.getAsJsonObject("ExplosionConfig").get("EntityDamage").getAsDouble(), 0.000001);

        JsonObject rain = json("Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Ice_Ball.json");
        assertEquals("15", rain.get("Damage").getAsString());
        assertEquals(10 * SCALE,
                rain.getAsJsonObject("ExplosionConfig").get("EntityDamage").getAsDouble(), 0.000001);
        JsonObject rainLaunch = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/"
                + "Hydra_Rain_Ice_Launch.json");
        assertEquals(5 * SCALE,
                rainLaunch.getAsJsonObject("LingeringHazard").get("DamagePerTick").getAsDouble(), 0.000001);

        assertEquals(16, physicalDamage(roleVars("Hydra/Tamed_Hydra.json"), "Bite_Damage"));
        assertEquals(40, json("Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json")
                .get("Damage").getAsDouble());
        assertEquals(11, json("Server/Projectiles/HyDragon/Hydra/Tamed_Hydra_Ice_Ball.json")
                .get("Damage").getAsDouble());
    }

    @Test
    void wildNordicDrakeDamageIsReducedAboutFifteenPercentThroughWildOnlyOverrides() throws Exception {
        JsonObject wildVars = roleVars("NordicDrake/NordicDrake.json");
        for (String attack : List.of(
                "Bite_Damage", "Swipe_Left_Damage", "Swipe_Right_Damage", "Stomp_Damage",
                "Tail_Spin_Damage")) {
            assertEquals(68 * SCALE, physicalDamage(wildVars, attack), 0.000001, attack);
        }
        assertEquals("NordicDrake_Fire_Ball_Launch_Wild",
                interactionParent(wildVars, "Dragon_Fire_Ball_Launch"));
        assertEquals("NordicDrake_Flame_Breath_Damage_Wild",
                interactionParent(wildVars, "Flame_Breath_Damage"));

        JsonObject projectile = json("Server/Projectiles/HyDragon/NordicDrake/"
                + "NordicDrake_Dragon_Fire_Ball_Wild.json");
        assertEquals("27", projectile.get("Damage").getAsString());
        assertEquals(16 * SCALE,
                projectile.getAsJsonObject("ExplosionConfig").get("EntityDamage").getAsDouble(), 0.000001);
        JsonObject wildLaunch = json("Server/Item/Interactions/NPCs/HyDragon/NordicDrake/"
                + "NordicDrake_Fire_Ball_Launch_Wild.json");
        assertEquals("NordicDrake_Dragon_Fire_Ball_Wild", wildLaunch.get("ProjectileId").getAsString());
        assertEquals("Flame_Staff_Burn",
                wildLaunch.getAsJsonObject("ImpactEffect").get("EffectId").getAsString());

        JsonObject breathDamage = json("Server/Item/Interactions/NPCs/HyDragon/NordicDrake/"
                + "NordicDrake_Flame_Breath_Damage_Wild.json");
        assertEquals(18 * SCALE, breathDamage.getAsJsonObject("DamageCalculator")
                .getAsJsonObject("BaseDamage").get("Fire").getAsDouble(), 0.000001);
        assertEquals("Flame_Staff_Burn",
                breathDamage.getAsJsonObject("Next").get("EffectId").getAsString());

        assertWildReplacementCount("NordicDrake_Flame_Breath.json", 4);
        assertWildReplacementCount("NordicDrake_Flying_Flame_Breath.json", 4);
        assertTamedNordicDefaultsRemainUnscaled();
    }

    private static void assertTamedNordicDefaultsRemainUnscaled() throws Exception {
        assertEquals(34, physicalDamage(roleVars("NordicDrake/Tamed_NordicDrake.json"),
                "Swipe_Left_Damage"));
        JsonObject sharedProjectile = json("Server/Projectiles/HyDragon/NordicDrake/"
                + "NordicDrake_Dragon_Fire_Ball.json");
        assertEquals(32, sharedProjectile.get("Damage").getAsDouble());
        assertEquals(16, sharedProjectile.getAsJsonObject("ExplosionConfig")
                .get("EntityDamage").getAsDouble());

        JsonObject sharedFireball = json("Server/Item/Interactions/NPCs/HyDragon/NordicDrake/"
                + "NordicDrake_Fire_Ball.json");
        JsonObject replacement = sharedFireball.getAsJsonArray("Interactions").get(2).getAsJsonObject();
        assertEquals("Dragon_Fire_Ball_Launch", replacement.get("Var").getAsString());
        JsonObject defaultLaunch = replacement.getAsJsonObject("DefaultValue")
                .getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("NordicDrake_Dragon_Fire_Ball", defaultLaunch.get("ProjectileId").getAsString());
        assertEquals("Flame_Staff_Burn",
                defaultLaunch.getAsJsonObject("ImpactEffect").get("EffectId").getAsString());

        String avatarBreath = Files.readString(ROOT.resolve("Server/Item/Interactions/NPCs/HyDragon/"
                + "NordicDrake/NordicDrake_Avatar_Flying_Flame_Breath.json"));
        assertFalse(avatarBreath.contains("NordicDrake_Flame_Breath_Damage_Wild"));
    }

    private static void assertWildReplacementCount(String file, int expected) throws IOException {
        JsonObject interaction = json("Server/Item/Interactions/NPCs/HyDragon/NordicDrake/" + file);
        List<JsonObject> replacements = new ArrayList<>();
        collectReplacements(interaction, replacements);
        assertEquals(expected, replacements.size());
        for (JsonObject replacement : replacements) {
            assertEquals("Replace", replacement.get("Type").getAsString());
            assertEquals("NordicDrake_Flame_Breath_Damage", replacement.getAsJsonObject("DefaultValue")
                    .getAsJsonArray("Interactions").get(0).getAsString());
        }
    }

    private static void collectReplacements(com.google.gson.JsonElement element, List<JsonObject> matches) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectReplacements(child, matches));
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (object.has("Var") && "Flame_Breath_Damage".equals(object.get("Var").getAsString())) {
            matches.add(object);
        }
        object.entrySet().forEach(entry -> collectReplacements(entry.getValue(), matches));
    }

    private static JsonObject roleVars(String relativePath) throws IOException {
        return json("Server/NPC/Roles/Creature/HyDragon/" + relativePath)
                .getAsJsonObject("Modify").getAsJsonObject("_InteractionVars");
    }

    private static double physicalDamage(JsonObject vars, String attack) {
        return vars.getAsJsonObject(attack).getAsJsonArray("Interactions").get(0).getAsJsonObject()
                .getAsJsonObject("DamageCalculator").getAsJsonObject("BaseDamage")
                .get("Physical").getAsDouble();
    }

    private static String interactionParent(JsonObject vars, String variable) {
        return vars.getAsJsonObject(variable).getAsJsonArray("Interactions").get(0).getAsJsonObject()
                .get("Parent").getAsString();
    }

    private static JsonObject json(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relativePath))).getAsJsonObject();
    }
}
