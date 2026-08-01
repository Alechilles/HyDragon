package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToxicHydraVariantAssetTest {
    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();

    @Test
    void sharedRangedChoreographyUsesElementVariablesWithoutTimingDrift() throws Exception {
        JsonArray direct = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Ice_Ball.json")
                .getAsJsonArray("Interactions");
        assertEquals(8, direct.size());
        assertAnimation(direct.get(0).getAsJsonObject(), "PrepareShoot", 0.45);
        assertReplacement(direct.get(1).getAsJsonObject(), "Hydra_Ball_Charge_Effect",
                "Hydra_Ice_Ball_Charge_Effect", 1.0);
        assertReplacement(direct.get(2).getAsJsonObject(), "Hydra_Ball_Launch",
                "Hydra_Ice_Ball_Launch", 0.0);
        assertReplacement(direct.get(3).getAsJsonObject(), "Hydra_Ball_Charge_Effect",
                "Hydra_Ice_Ball_Charge_Effect", 0.5);
        assertReplacement(direct.get(4).getAsJsonObject(), "Hydra_Ball_Launch",
                "Hydra_Ice_Ball_Launch", 0.0);
        assertReplacement(direct.get(5).getAsJsonObject(), "Hydra_Ball_Charge_Effect",
                "Hydra_Ice_Ball_Charge_Effect", 0.5);
        assertReplacement(direct.get(6).getAsJsonObject(), "Hydra_Ball_Launch",
                "Hydra_Ice_Ball_Launch", 0.0);
        assertAnimation(direct.get(7).getAsJsonObject(), "FinishShoot", 0.5);

        JsonObject rainRoot = json("Server/Item/RootInteractions/NPCs/Creature/HyDragon/"
                + "Root_NPC_Hydra_RainShoot_Barrage.json");
        assertFalseBooleanProperty(rainRoot, "RequireNewClick");
        JsonArray rain = rainRoot.getAsJsonArray("Interactions").get(0).getAsJsonObject()
                .getAsJsonArray("Interactions");
        assertEquals(40, rain.size());
        for (int shot = 0; shot < 20; shot++) {
            assertReplacement(rain.get(shot * 2).getAsJsonObject(), "Hydra_Rain_Charge_Effect",
                    "Hydra_Rain_Ice_Charge_Effect", 0.3);
            assertReplacement(rain.get(shot * 2 + 1).getAsJsonObject(), "Hydra_Rain_Launch",
                    "Hydra_Rain_Ice_Launch", 0.0);
        }
    }

    @Test
    void iceRolesProvideAllFourRangedDefaults() throws Exception {
        for (String role : List.of("Hydra.json", "Tamed_Hydra.json")) {
            JsonObject vars = json("Server/NPC/Roles/Creature/HyDragon/Hydra/" + role)
                    .getAsJsonObject("Modify").getAsJsonObject("_InteractionVars");
            assertVariableLeaf(vars, "Hydra_Ball_Charge_Effect", "Hydra_Ice_Ball_Charge_Effect");
            assertVariableLeaf(vars, "Hydra_Ball_Launch", "Hydra_Ice_Ball_Launch");
            assertVariableLeaf(vars, "Hydra_Rain_Charge_Effect", "Hydra_Rain_Ice_Charge_Effect");
            assertVariableLeaf(vars, "Hydra_Rain_Launch", "Hydra_Rain_Ice_Launch");
        }
    }

    @Test
    void iceLeafInteractionsRetainExistingProjectileBehavior() throws Exception {
        assertLeaf("Hydra_Ice_Ball_Charge_Effect.json", """
                {"Type":"Simple","Effects":{"ItemPlayerAnimationsId":"Hydra_Default","ItemAnimationId":"ChargeShoot","Particles":[{"TargetEntityPart":"Entity","TargetNodeName":"Origin_Projectile","SystemId":"Hydra_Ice_Ball_Charging","PositionOffset":{"Z":0}}]}}
                """);
        assertLeaf("Hydra_Ice_Ball_Launch.json", """
                {"Type":"TameworkLaunchProjectile","Effects":{"WorldSoundEventId":"SFX_Staff_Ice_Shoot","LocalSoundEventId":"SFX_Staff_Ice_Shoot","Particles":[{"TargetEntityPart":"Entity","TargetNodeName":"Origin_Projectile","SystemId":"Ice_Staff","DetachedFromModel":false,"PositionOffset":{},"Scale":1.5}]},"ProjectileId":"Hydra_Ice_Ball","TrajectoryMode":"Direct","TargetSlot":"CAETargetSlot","ImpactEffect":{"EffectId":"Chilled","Radius":3.0,"ExcludeSource":true},"Tags":{}}
                """);
        assertLeaf("Hydra_Rain_Ice_Charge_Effect.json", """
                {"Type":"Simple","Effects":{"Particles":[{"TargetEntityPart":"Entity","TargetNodeName":"Origin_Projectile","SystemId":"Hydra_Ice_Ball_Charging","PositionOffset":{"Z":0}}]}}
                """);
        assertLeaf("Hydra_Rain_Ice_Launch.json", """
                {"Type":"TameworkLaunchProjectile","ProjectileId":"Hydra_Rain_Ice_Ball","LaunchPositionOffset":{"X":0.0,"Y":-1.0,"Z":-2.0},"RandomAroundSourceMinRadius":6.0,"RandomAroundSourceMaxRadius":15.0,"RandomAroundSourceVerticalOffset":0.0,"LingeringHazard":{"Radius":4.0,"DurationSeconds":6.0,"TickIntervalSeconds":1.0,"DamagePerTick":5.0,"ExcludeSource":true,"EffectId":"Chilled","SourceTypeId":"hydragon.rain_ice_hazard"},"Effects":{"WorldSoundEventId":"SFX_Staff_Ice_Shoot","LocalSoundEventId":"SFX_Staff_Ice_Shoot","Particles":[{"TargetEntityPart":"Entity","TargetNodeName":"Origin_Projectile","SystemId":"Ice_Staff","DetachedFromModel":false,"PositionOffset":{},"Scale":1.5}]},"Tags":{}}
                """);
    }

    @Test
    void toxicProjectilesPreserveIceMechanicsAndReplaceOnlyElementPresentation() throws Exception {
        assertProjectileParity(
                "Server/Projectiles/HyDragon/Hydra/Hydra_Ice_Ball.json",
                "Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json");
        assertProjectileParity(
                "Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Ice_Ball.json",
                "Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Toxic_Ball.json");

        for (String projectile : List.of(
                "Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json",
                "Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Toxic_Ball.json")) {
            assertEquals("Hydra_Toxic_Ball_Projectile", json(projectile).get("Appearance").getAsString());
        }

        JsonObject model = json("Server/Models/Projectiles/HyDragon/Hydra_Toxic_Ball_Projectile.json");
        assertEquals("Items/Projectiles/Acid.blockymodel", model.get("Model").getAsString());
        assertEquals("Items/Projectiles/Acid_Texture.png", model.get("Texture").getAsString());
        assertEquals(JsonParser.parseString("""
                {"Max":{"X":0.1,"Y":0.1,"Z":0.1},"Min":{"X":-0.1,"Y":-0.1,"Z":-0.1}}
                """), model.get("HitBox"));
        assertEquals(3, model.get("MinScale").getAsInt());
        assertEquals(5, model.get("MaxScale").getAsInt());
        JsonArray particles = model.getAsJsonArray("Particles");
        assertEquals(1, particles.size());
        assertEquals("Status_Poisoned", particles.get(0).getAsJsonObject().get("SystemId").getAsString());
    }

    @Test
    void toxicLeavesUsePoisonT1AndCanonicalPoisonPresentation() throws Exception {
        String direct = read("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Launch.json");
        assertTrue(direct.contains("\"ProjectileId\": \"Hydra_Toxic_Ball\""));
        assertTrue(direct.contains("\"EffectId\": \"Poison_T1\""));
        assertTrue(direct.contains("\"Radius\": 3.0"));
        assertTrue(direct.contains("\"ExcludeSource\": true"));
        assertTrue(direct.contains("Effect_Poison"));
        assertTrue(direct.contains("SFX_Scarak_Spitball_Fire"));

        String rain = read("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Launch.json");
        for (String required : List.of(
                "\"ProjectileId\": \"Hydra_Rain_Toxic_Ball\"",
                "\"EffectId\": \"Poison_T1\"",
                "\"SourceTypeId\": \"hydragon.rain_toxic_hazard\"",
                "\"Radius\": 4.0", "\"DurationSeconds\": 6.0",
                "\"TickIntervalSeconds\": 1.0", "\"DamagePerTick\": 5.0",
                "Effect_Poison", "SFX_Scarak_Spitball_Fire")) {
            assertTrue(rain.contains(required), required);
        }

        JsonObject iceHazard = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Ice_Launch.json")
                .getAsJsonObject("LingeringHazard");
        JsonObject toxicHazard = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Launch.json")
                .getAsJsonObject("LingeringHazard");
        JsonObject expectedToxicHazard = iceHazard.deepCopy();
        expectedToxicHazard.addProperty("EffectId", "Poison_T1");
        expectedToxicHazard.addProperty("SourceTypeId", "hydragon.rain_toxic_hazard");
        assertEquals(iceHazard.keySet(), toxicHazard.keySet());
        assertEquals(expectedToxicHazard, toxicHazard);

        assertChargeEffectLeaf("Hydra_Toxic_Ball_Charge_Effect.json");
        assertChargeEffectLeaf("Hydra_Rain_Toxic_Charge_Effect.json");

        for (String projectile : List.of(
                "Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json",
                "Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Toxic_Ball.json")) {
            String content = read(projectile);
            assertTrue(content.contains("Effect_Poison"));
            assertTrue(content.contains("Impact_Poison"));
            assertTrue(content.contains("SFX_Scarak_Seeker_Spitball_Death"));
        }
    }

    @Test
    void requireNewClickRejectsCoercibleNonBooleanValues() {
        JsonObject stringFalse = JsonParser.parseString("""
                {"RequireNewClick":"false"}
                """).getAsJsonObject();
        assertThrows(AssertionError.class, () -> assertFalseBooleanProperty(stringFalse, "RequireNewClick"));
    }

    private static void assertLeaf(String leaf, String expected) throws IOException {
        JsonObject actual = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/" + leaf);
        assertFalse(actual.has("RunTime"), leaf + " must leave cadence to its caller");
        assertEquals(JsonParser.parseString(expected).getAsJsonObject(), actual, leaf);
    }

    private static void assertProjectileParity(String icePath, String toxicPath) throws IOException {
        JsonObject ice = json(icePath).deepCopy();
        JsonObject toxic = json(toxicPath).deepCopy();
        for (String presentationField : List.of(
                "Appearance", "DeathParticles", "MissParticles", "HitParticles", "DeathSoundEventId")) {
            ice.remove(presentationField);
            toxic.remove(presentationField);
        }
        assertEquals(ice, toxic, toxicPath);
    }

    private static void assertChargeEffectLeaf(String leaf) throws IOException {
        String content = read("Server/Item/Interactions/NPCs/HyDragon/Hydra/" + leaf);
        assertTrue(content.contains("Effect_Poison"), leaf);
    }

    private static void assertFalseBooleanProperty(JsonObject object, String property) {
        assertTrue(object.has(property));
        JsonElement value = object.get(property);
        assertTrue(value.isJsonPrimitive());
        assertTrue(value.getAsJsonPrimitive().isBoolean());
        assertFalse(value.getAsBoolean());
    }

    private static void assertAnimation(JsonObject interaction, String animation, double runTime) {
        assertEquals("Simple", interaction.get("Type").getAsString());
        assertEquals(animation, interaction.getAsJsonObject("Effects").get("ItemAnimationId").getAsString());
        assertEquals(runTime, interaction.get("RunTime").getAsDouble());
    }

    private static void assertReplacement(JsonObject interaction, String variable, String leaf, double runTime) {
        assertEquals("Replace", interaction.get("Type").getAsString());
        assertTrue(interaction.get("DefaultOk").getAsBoolean());
        assertEquals(variable, interaction.get("Var").getAsString());
        assertEquals(runTime, interaction.get("RunTime").getAsDouble());
        JsonArray defaults = interaction.getAsJsonObject("DefaultValue").getAsJsonArray("Interactions");
        assertEquals(1, defaults.size());
        assertEquals(leaf, defaults.get(0).getAsString());
    }

    private static void assertVariableLeaf(JsonObject vars, String variable, String leaf) {
        JsonArray interactions = vars.getAsJsonObject(variable).getAsJsonArray("Interactions");
        assertEquals(1, interactions.size());
        assertEquals(leaf, interactions.get(0).getAsJsonObject().get("Parent").getAsString());
    }

    private static JsonObject json(String relativePath) throws IOException {
        return JsonParser.parseString(read(relativePath)).getAsJsonObject();
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
