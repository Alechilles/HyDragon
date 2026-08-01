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

        assertProjectilePresentation(
                json("Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json"), false);
        assertProjectilePresentation(
                json("Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Toxic_Ball.json"), true);

        JsonObject model = json("Server/Models/Projectiles/HyDragon/Hydra_Toxic_Ball_Projectile.json");
        assertEquals(JsonParser.parseString("""
                {"Model":"Items/Projectiles/Acid.blockymodel","Texture":"Items/Projectiles/Acid_Texture.png","HitBox":{"Max":{"X":0.1,"Y":0.1,"Z":0.1},"Min":{"X":-0.1,"Y":-0.1,"Z":-0.1}},"MinScale":3,"MaxScale":5,"Particles":[{"PositionOffset":{"X":0,"Y":0,"Z":0},"SystemId":"Status_Poisoned","TargetNodeName":""}]}
                """), model);
    }

    @Test
    void toxicLeavesUsePoisonT1AndCanonicalPoisonPresentation() throws Exception {
        JsonObject direct = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Launch.json");
        JsonObject expectedDirect = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Ice_Ball_Launch.json")
                .deepCopy();
        replaceLaunchPresentation(expectedDirect, "Hydra_Toxic_Ball");
        expectedDirect.getAsJsonObject("ImpactEffect").addProperty("EffectId", "Poison_T1");
        assertEquals(expectedDirect, direct);
        assertEquals("Poison_T1", direct.getAsJsonObject("ImpactEffect").get("EffectId").getAsString());
        assertEquals(3.0, direct.getAsJsonObject("ImpactEffect").get("Radius").getAsDouble());
        assertTrue(direct.getAsJsonObject("ImpactEffect").get("ExcludeSource").getAsBoolean());

        JsonObject rain = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Launch.json");
        JsonObject expectedRain = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Ice_Launch.json")
                .deepCopy();
        replaceLaunchPresentation(expectedRain, "Hydra_Rain_Toxic_Ball");
        JsonObject expectedToxicHazard = expectedRain.getAsJsonObject("LingeringHazard");
        expectedToxicHazard.addProperty("EffectId", "Poison_T1");
        expectedToxicHazard.addProperty("SourceTypeId", "hydragon.rain_toxic_hazard");
        assertEquals(expectedRain, rain);
        assertEquals("Poison_T1", rain.getAsJsonObject("LingeringHazard").get("EffectId").getAsString());
        assertEquals("hydragon.rain_toxic_hazard",
                rain.getAsJsonObject("LingeringHazard").get("SourceTypeId").getAsString());

        assertChargeLeafParity("Hydra_Ice_Ball_Charge_Effect.json", "Hydra_Toxic_Ball_Charge_Effect.json");
        assertChargeLeafParity("Hydra_Rain_Ice_Charge_Effect.json", "Hydra_Rain_Toxic_Charge_Effect.json");
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

    private static void assertProjectilePresentation(JsonObject projectile, boolean rain) {
        assertEquals("Hydra_Toxic_Ball_Projectile", projectile.get("Appearance").getAsString());
        JsonObject effectPoison = JsonParser.parseString(rain
                ? "{\"SystemId\":\"Effect_Poison\",\"Scale\":2.0}"
                : "{\"SystemId\":\"Effect_Poison\"}").getAsJsonObject();
        JsonObject impactPoison = JsonParser.parseString(rain
                ? "{\"SystemId\":\"Impact_Poison\",\"Scale\":2.0}"
                : "{\"SystemId\":\"Impact_Poison\"}").getAsJsonObject();
        assertEquals(effectPoison, projectile.getAsJsonObject("DeathParticles"));
        assertEquals(effectPoison, projectile.getAsJsonObject("MissParticles"));
        assertEquals(impactPoison, projectile.getAsJsonObject("HitParticles"));
        assertEquals("SFX_Scarak_Seeker_Spitball_Death", projectile.get("DeathSoundEventId").getAsString());
    }

    private static void replaceLaunchPresentation(JsonObject launch, String projectileId) {
        launch.addProperty("ProjectileId", projectileId);
        JsonObject effects = launch.getAsJsonObject("Effects");
        effects.addProperty("WorldSoundEventId", "SFX_Scarak_Spitball_Fire");
        effects.addProperty("LocalSoundEventId", "SFX_Scarak_Spitball_Fire");
        effects.getAsJsonArray("Particles").get(0).getAsJsonObject()
                .addProperty("SystemId", "Effect_Poison");
    }

    private static void assertChargeLeafParity(String iceLeaf, String toxicLeaf) throws IOException {
        JsonObject expected = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/" + iceLeaf).deepCopy();
        expected.getAsJsonObject("Effects").getAsJsonArray("Particles").get(0).getAsJsonObject()
                .addProperty("SystemId", "Effect_Poison");
        assertEquals(expected, json("Server/Item/Interactions/NPCs/HyDragon/Hydra/" + toxicLeaf), toxicLeaf);
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
