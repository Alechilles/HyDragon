package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ToxicHydraVariantAssetTest {
    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();

    @Test
    void toxicRolesInheritIceRolesAndOverrideEveryElementSeam() throws Exception {
        assertToxicRole("Hydra_Toxic", "Hydra", "Tamed_Hydra_Toxic");
        assertToxicRole("Tamed_Hydra_Toxic", "Tamed_Hydra", null);
    }

    @Test
    void toxicRoleContractRejectsUnexpectedNameAndRangedInteractionFields() throws Exception {
        JsonObject wrongNameCompute = toxicRole("Hydra_Toxic");
        wrongNameCompute.getAsJsonObject("Modify").getAsJsonObject("NameTranslationKey")
                .addProperty("Compute", "WrongNameTranslationKey");
        assertThrows(AssertionError.class,
                () -> assertToxicRole(wrongNameCompute, "Hydra_Toxic", "Hydra", "Tamed_Hydra_Toxic"));

        JsonObject extraRangedWrapperField = toxicRole("Hydra_Toxic");
        extraRangedWrapperField.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars")
                .getAsJsonObject("Hydra_Ball_Launch").addProperty("Unexpected", true);
        assertThrows(AssertionError.class,
                () -> assertToxicRole(extraRangedWrapperField, "Hydra_Toxic", "Hydra", "Tamed_Hydra_Toxic"));

        JsonObject extraRangedInteractionField = toxicRole("Hydra_Toxic");
        extraRangedInteractionField.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars")
                .getAsJsonObject("Hydra_Rain_Launch").getAsJsonArray("Interactions")
                .get(0).getAsJsonObject().addProperty("DamageCalculator", "unexpected");
        assertThrows(AssertionError.class,
                () -> assertToxicRole(extraRangedInteractionField, "Hydra_Toxic", "Hydra", "Tamed_Hydra_Toxic"));
    }

    @Test
    void toxicModelUsesWingedHydraRuntimePresentation() throws Exception {
        Path runtimeModel = ROOT.resolve(
                "Common/NPC/HyDragon/Hydra_Winged/Model/Hydra_Winged.blockymodel");
        assertTrue(Files.isRegularFile(runtimeModel), "Winged Hydra export must use the runtime asset name");

        JsonObject toxicModel = json("Server/Models/HyDragon/Hydra/Hydra_Toxic.json");
        assertEquals(JsonParser.parseString("""
                {"Parent":"Hydra_Winged"}
                """).getAsJsonObject(), toxicModel);

        JsonObject wingedModel = json("Server/Models/HyDragon/Hydra_Winged/Hydra_Winged.json");
        assertEquals("NPC/HyDragon/Hydra_Winged/Model/Hydra_Winged.blockymodel",
                wingedModel.get("Model").getAsString());
        String texture = wingedModel.get("Texture").getAsString();
        assertEquals("NPC/HyDragon/Hydra_Winged/Model/texture.png", texture);
        assertTrue(Files.isRegularFile(ROOT.resolve("Common").resolve(texture)));

        JsonObject animationSets = wingedModel.getAsJsonObject("AnimationSets");
        assertAnimationPath(animationSets, "Idle", "Animation/Default/Idle.blockyanim");
        assertAnimationPath(animationSets, "LeftSwipe", "Animation/Attacks/Swipe_Left.blockyanim");
        assertAnimationPath(animationSets, "Rainshoot", "Animation/Rainshoot.blockyanim");
        assertAnimationPath(animationSets, "FlyIdle", "Animation/Fly/Fly_Idle.blockyanim");
        assertAnimationPath(animationSets, "Fly", "Animation/Fly/Fly.blockyanim");
        assertAnimationPath(animationSets, "FlyFast", "Animation/Fly/Fly_Fast.blockyanim");

        JsonObject exported = json("Common/NPC/HyDragon/Hydra_Winged/Model/Hydra_Winged.blockymodel");
        assertEquals("character", exported.get("format").getAsString());
        assertTrue(modelNodeNames(exported).contains("Origin"));
    }

    @Test
    void onlyTamedToxicHydraEnablesDedicatedAvatarFlight() throws Exception {
        Path patchPath = ROOT.resolve(
                "Server/Tamework/Patches/HyDragonRoles/Tamed_Hydra_Toxic_AvatarFlight.json");
        assertTrue(Files.isRegularFile(patchPath), "Tamed Toxic Hydra needs avatar-flight role wiring");

        JsonObject wildModify = toxicRole("Hydra_Toxic").getAsJsonObject("Modify");
        assertFalse(wildModify.has("MountMode"));
        assertFalse(wildModify.has("AvatarFlightConfig"));
        assertEquals("CAE_Hydra_Toxic", wildModify.get("_CombatConfig").getAsString());

        JsonObject patch = json(
                "Server/Tamework/Patches/HyDragonRoles/Tamed_Hydra_Toxic_AvatarFlight.json");
        assertEquals("Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra_Toxic.json",
                patch.get("Target").getAsString());
        JsonObject patchValue = patch.getAsJsonArray("Operations").get(0).getAsJsonObject()
                .getAsJsonObject("Value");
        assertEquals("TameworkAvatarFlight", patchValue.get("MountMode").getAsString());
        assertEquals("HyDragonToxicHydra", patchValue.get("AvatarFlightConfig").getAsString());

        JsonObject flight = json("Server/Tamework/AvatarFlight/HyDragonToxicHydra.json");
        assertTrue(flight.get("Enabled").getAsBoolean());
        assertEquals("Hydra_Winged_AvatarFlight",
                flight.getAsJsonObject("Model").get("ModelId").getAsString());
        assertTrue(flight.getAsJsonObject("Model").get("ApplyModel").getAsBoolean());
        assertFalse(flight.has("CombatAbilities"), "Airborne Toxic abilities are deferred");
        assertEquals("FlyIdle", flight.getAsJsonObject("Animation")
                .get("IdleAnimation").getAsString());
        assertEquals("Fly", flight.getAsJsonObject("Animation")
                .get("FlightAnimation").getAsString());
        assertEquals("FlyFast", flight.getAsJsonObject("Animation")
                .get("FastFlightAnimation").getAsString());

        JsonObject avatarModel = json(
                "Server/Models/HyDragon/Hydra_Winged/Hydra_Winged_AvatarFlight.json");
        String avatarRuntimePath = avatarModel.get("Model").getAsString();
        assertTrue(Files.isRegularFile(ROOT.resolve("Common").resolve(avatarRuntimePath)));
        JsonObject avatarRuntime = json("Common/" + avatarRuntimePath);
        Set<String> avatarNodes = modelNodeNames(avatarRuntime);
        assertTrue(avatarNodes.contains("AF_Origin"));
        assertFalse(avatarNodes.contains("Origin"));
    }

    @Test
    void toxicTexturePreservesIceDimensionsAndPerPixelAlpha() throws Exception {
        BufferedImage ice = ImageIO.read(ROOT.resolve(
                "Common/NPC/HyDragon/Hydra/Model/Ice.png").toFile());
        BufferedImage toxic = ImageIO.read(ROOT.resolve(
                "Common/NPC/HyDragon/Hydra/Model/Toxic.png").toFile());
        assertNotNull(ice);
        assertNotNull(toxic);
        assertEquals(ice.getWidth(), toxic.getWidth());
        assertEquals(ice.getHeight(), toxic.getHeight());
        int alphaMismatches = 0;
        for (int y = 0; y < ice.getHeight(); y++) {
            for (int x = 0; x < ice.getWidth(); x++) {
                if ((ice.getRGB(x, y) >>> 24) != (toxic.getRGB(x, y) >>> 24)) alphaMismatches++;
            }
        }
        assertEquals(0, alphaMismatches, "Toxic recolor must preserve the complete UV alpha mask");
    }

    @Test
    void sharedRangedChoreographyKeepsCadenceOnBlockingSimpleSteps() throws Exception {
        JsonArray direct = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Ice_Ball.json")
                .getAsJsonArray("Interactions");
        assertEquals(11, direct.size());
        assertAnimation(direct.get(0).getAsJsonObject(), "PrepareShoot", 0.45);
        assertReplacement(direct.get(1).getAsJsonObject(), "Hydra_Ball_Charge_Effect",
                "Hydra_Ice_Ball_Charge_Effect");
        assertDelay(direct.get(2).getAsJsonObject(), 1.0);
        assertReplacement(direct.get(3).getAsJsonObject(), "Hydra_Ball_Launch",
                "Hydra_Ice_Ball_Launch");
        assertReplacement(direct.get(4).getAsJsonObject(), "Hydra_Ball_Charge_Effect",
                "Hydra_Ice_Ball_Charge_Effect");
        assertDelay(direct.get(5).getAsJsonObject(), 0.5);
        assertReplacement(direct.get(6).getAsJsonObject(), "Hydra_Ball_Launch",
                "Hydra_Ice_Ball_Launch");
        assertReplacement(direct.get(7).getAsJsonObject(), "Hydra_Ball_Charge_Effect",
                "Hydra_Ice_Ball_Charge_Effect");
        assertDelay(direct.get(8).getAsJsonObject(), 0.5);
        assertReplacement(direct.get(9).getAsJsonObject(), "Hydra_Ball_Launch",
                "Hydra_Ice_Ball_Launch");
        assertAnimation(direct.get(10).getAsJsonObject(), "FinishShoot", 0.5);

        JsonObject rainRoot = json("Server/Item/RootInteractions/NPCs/Creature/HyDragon/"
                + "Root_NPC_Hydra_RainShoot_Barrage.json");
        assertFalseBooleanProperty(rainRoot, "RequireNewClick");
        JsonArray rain = rainRoot.getAsJsonArray("Interactions").get(0).getAsJsonObject()
                .getAsJsonArray("Interactions");
        assertEquals(60, rain.size());
        for (int shot = 0; shot < 20; shot++) {
            assertReplacement(rain.get(shot * 3).getAsJsonObject(), "Hydra_Rain_Charge_Effect",
                    "Hydra_Rain_Ice_Charge_Effect");
            assertDelay(rain.get(shot * 3 + 1).getAsJsonObject(), 0.3);
            assertReplacement(rain.get(shot * 3 + 2).getAsJsonObject(), "Hydra_Rain_Launch",
                    "Hydra_Rain_Ice_Launch");
        }
    }

    @Test
    void iceParentRolesExposeEveryOrdinaryToxicOverride() throws Exception {
        assertPublicParameter("Hydra", "Appearance", "\"Hydra\"");
        assertPublicParameter("Hydra", "FlockArray", "[\"Hydra\"]");
        assertPublicParameter("Hydra", "MemoriesNameOverride", "\"Hydra\"");
        assertPublicParameter("Hydra", "TameRoleChange", "\"Tamed_Hydra\"");
        assertPublicParameter("Tamed_Hydra", "Appearance", "\"Hydra\"");
        assertPublicParameter("Tamed_Hydra", "FlockArray", "[\"Tamed_Hydra\"]");
        assertPublicParameter("Tamed_Hydra", "MemoriesNameOverride", "\"Hydra\"");
    }

    @Test
    void tamedHydraPublishesAvatarFlightParametersThroughBothRoleLayers() throws Exception {
        JsonObject templateParameters = json(
                "Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Tamed.json")
                .getAsJsonObject("Parameters");
        for (String parameter : List.of("MountMode", "AvatarFlightConfig")) {
            JsonObject declaration = templateParameters.getAsJsonObject(parameter);
            assertNotNull(declaration, "Template_HyDragon_Tamed must declare " + parameter);
            assertEquals("", declaration.get("Value").getAsString());
            assertPublicParameter("Tamed_Hydra", parameter, "\"\"");
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
    void toxicWildCombatEvaluatorSuppliesToxicVarsAtEveryAttackEntryPoint() throws Exception {
        JsonObject toxicRole = toxicRole("Hydra_Toxic");
        assertEquals("CAE_Hydra_Toxic", toxicRole.getAsJsonObject("Modify")
                .get("_CombatConfig").getAsString());

        JsonObject toxicConfig = json("Server/NPC/Balancing/CAE_Hydra_Toxic.json");
        JsonObject evaluator = toxicConfig.getAsJsonObject("CombatActionEvaluator");
        JsonObject actions = evaluator.getAsJsonObject("AvailableActions");

        JsonObject meleeVars = actions.getAsJsonObject("MeleeAttack")
                .getAsJsonObject("InteractionVars");
        assertPoisonMelee(meleeVars, "Swipe_Left_Damage", "Hydra_Swipe_Left_Damage");
        assertPoisonMelee(meleeVars, "Swipe_Right_Damage", "Hydra_Swipe_Right_Damage");
        assertPoisonMelee(meleeVars, "Stomp_Damage", "Hydra_Stomp_Damage");
        assertPoisonMelee(actions.getAsJsonObject("BiteAttack").getAsJsonObject("InteractionVars"),
                "Bite_Damage", "Hydra_Bite_Damage");
        assertPoisonMelee(actions.getAsJsonObject("TailSpinAttack").getAsJsonObject("InteractionVars"),
                "Tail_Spin_Damage", "Hydra_Tail_Spin_Damage");

        JsonObject rainVars = actions.getAsJsonObject("RainShootAttack")
                .getAsJsonObject("InteractionVars");
        assertVariableLeaf(rainVars, "Hydra_Rain_Charge_Effect", "Hydra_Rain_Toxic_Charge_Effect");
        assertVariableLeaf(rainVars, "Hydra_Rain_Launch", "Hydra_Rain_Toxic_Launch");

        JsonObject actionSets = evaluator.getAsJsonObject("ActionSets");
        JsonObject defaultVars = actionSets.getAsJsonObject("Default")
                .getAsJsonObject("BasicAttacks").getAsJsonObject("InteractionVars");
        assertPoisonMelee(defaultVars, "Swipe_Left_Damage", "Hydra_Swipe_Left_Damage");
        assertPoisonMelee(defaultVars, "Swipe_Right_Damage", "Hydra_Swipe_Right_Damage");
        assertPoisonMelee(defaultVars, "Stomp_Damage", "Hydra_Stomp_Damage");

        JsonObject rangedVars = actionSets.getAsJsonObject("Ranged")
                .getAsJsonObject("BasicAttacks").getAsJsonObject("InteractionVars");
        assertVariableLeaf(rangedVars, "Hydra_Ball_Charge_Effect", "Hydra_Toxic_Ball_Charge_Effect");
        assertVariableLeaf(rangedVars, "Hydra_Ball_Launch", "Hydra_Toxic_Ball_Launch");

        JsonObject behaviorParity = toxicConfig.deepCopy();
        JsonObject parityEvaluator = behaviorParity.getAsJsonObject("CombatActionEvaluator");
        JsonObject parityActions = parityEvaluator.getAsJsonObject("AvailableActions");
        for (String action : List.of("MeleeAttack", "BiteAttack", "TailSpinAttack", "RainShootAttack")) {
            parityActions.getAsJsonObject(action).remove("InteractionVars");
        }
        JsonObject paritySets = parityEvaluator.getAsJsonObject("ActionSets");
        paritySets.getAsJsonObject("Default").getAsJsonObject("BasicAttacks")
                .remove("InteractionVars");
        paritySets.getAsJsonObject("Ranged").getAsJsonObject("BasicAttacks")
                .remove("InteractionVars");
        assertEquals(json("Server/NPC/Balancing/CAE_Hydra.json"), behaviorParity,
                "Toxic evaluator must preserve Ice Hydra combat behavior");
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

    @Test
    void jsonReaderRejectsDuplicateObjectKeysAtEveryNestingLevel() {
        assertThrows(IllegalArgumentException.class,
                () -> parseJson("{\"Hydra\":\"first\",\"Hydra\":\"second\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> parseJson("{\"Hydra\":{\"Toxic\":\"first\",\"Toxic\":\"second\"}}"));
    }

    @Test
    void toxicHydraHasExactlyOneDaytimeSwampSpawnRegistration() throws Exception {
        List<JsonObject> registrations;
        try (Stream<Path> paths = Files.walk(ROOT.resolve("Server/NPC/Spawn/World"))) {
            registrations = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(ToxicHydraVariantAssetTest::parseSpawn)
                    .filter(spawn -> spawn.has("NPCs"))
                    .filter(spawn -> spawn.getAsJsonArray("NPCs").asList().stream()
                            .map(JsonElement::getAsJsonObject)
                            .anyMatch(npc -> "Hydra_Toxic".equals(npc.get("Id").getAsString())))
                    .toList();
        }
        assertEquals(1, registrations.size());
        JsonObject spawn = registrations.getFirst();
        assertEquals(List.of("Env_Zone1_Swamps"), strings(spawn.getAsJsonArray("Environments")));
        assertFalse(strings(spawn.getAsJsonArray("Environments")).stream()
                .anyMatch(environment -> environment.toLowerCase().contains("cave")
                        && environment.toLowerCase().contains("swamp")));
        JsonObject npc = spawn.getAsJsonArray("NPCs").get(0).getAsJsonObject();
        assertEquals(1, spawn.getAsJsonArray("NPCs").size());
        assertEquals(1, npc.get("Weight").getAsInt());
        assertEquals("Mud", npc.get("SpawnBlockSet").getAsString());
        assertEquals("Hydra_Toxic", npc.get("Id").getAsString());
        assertEquals(List.of(6, 18), ints(spawn.getAsJsonArray("DayTimeRange")));
        assertEquals(List.of(0, 4), ints(spawn.getAsJsonArray("MoonPhaseRange")));
        assertEquals(List.of(0.7, 0.85, 1.0, 1.15, 1.3),
                doubles(spawn.getAsJsonArray("MoonPhaseWeightModifiers")));
    }

    @Test
    void toxicHydraIsRegisteredAcrossSpeciesCaptureCompanionAndCommandAssets() throws Exception {
        JsonObject species = json("Server/HyDragon/DragonSpecies/Hydra.json");
        assertEquals(List.of("Hydra", "Hydra_Toxic"), strings(species.getAsJsonArray("WildRoleIds")));
        assertEquals(Map.of("Hydra", "Tamed_Hydra", "Hydra_Toxic", "Tamed_Hydra_Toxic"),
                stringMap(species.getAsJsonObject("TamedRoleIdByWildRole")));
        assertEquals(List.of("Spawns_Zone3_Glacial_HyDragon_Predator",
                        "Spawns_Zone1_Swamps_HyDragon_Predator"),
                strings(species.getAsJsonObject("Spawn").getAsJsonArray("OrdinarySpawnAssetIds")));
        assertEquals(List.of("Hydra", "Hydra_Toxic"),
                strings(species.getAsJsonObject("Presentation").getAsJsonArray("ModelIds")));

        assertEquals(List.of("Hydra", "Hydra_Toxic"),
                strings(json("Server/Tamework/CapturePolicies/HyDragonHydra.json")
                        .getAsJsonArray("RoleIds")));
        assertEquals(List.of("Tamed_NordicDrake", "Tamed_Hydra", "Tamed_Hydra_Toxic",
                        "Tamed_RockDrakeT1", "Tamed_RockDrakeT2", "Tamed_RockDrakeT3"),
                strings(json("Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json")
                        .getAsJsonArray("AllowedRoles")));
        assertEquals(List.of("Tamed_Hydra", "Tamed_Hydra_Toxic", "Tamed_RockDrakeT1",
                        "Tamed_RockDrakeT2", "Tamed_RockDrakeT3"),
                strings(json("Server/Tamework/Companion/HyDragonFullDragons.json")
                        .getAsJsonArray("RoleIds")));
        assertEquals(List.of("Tamed_NordicDrake", "Tamed_Hydra", "Tamed_Hydra_Toxic",
                        "Tamed_RockDrakeT1", "Tamed_RockDrakeT2", "Tamed_RockDrakeT3",
                        "Tamed_Wyvern_Mini_Wild", "Tamed_Wyvern_Mini_Nature",
                        "Tamed_Wyvern_Mini_Toxic", "Tamed_Wyvern_Mini_Fire",
                        "Tamed_Wyvern_Mini_Void", "Tamed_Wyvern_Mini_Lightning",
                        "Tamed_Wyvern_Mini_Ice"),
                strings(json("Server/Tamework/Breeding/HyDragonBondedCompanions.json")
                        .getAsJsonArray("RoleIds")));

        JsonObject stone = json("Server/Tamework/Items/Spawners/HyDragonDraconicStone.json");
        assertEquals(List.of("NordicDrake", "Hydra", "Hydra_Toxic", "RockDrakeT1", "RockDrakeT2",
                        "RockDrakeT3"),
                strings(stone.getAsJsonObject("AllowedRoles").getAsJsonArray("Allowlist")));
        assertEquals(Map.of("NordicDrake", "Tamed_NordicDrake", "Hydra", "Tamed_Hydra",
                        "Hydra_Toxic", "Tamed_Hydra_Toxic", "RockDrakeT1", "Tamed_RockDrakeT1",
                        "RockDrakeT2", "Tamed_RockDrakeT2", "RockDrakeT3", "Tamed_RockDrakeT3"),
                stringMap(stone.getAsJsonObject("Capture").getAsJsonObject("TamedRoleOverrides")));
        assertEquals(List.of("Tamed_Hydra", "Tamed_Hydra_Toxic", "Tamed_NordicDrake",
                        "Tamed_RockDrakeT1", "Tamed_RockDrakeT2", "Tamed_RockDrakeT3",
                        "Tamed_Wyvern_Mini_Wild", "Tamed_Wyvern_Mini_Nature",
                        "Tamed_Wyvern_Mini_Toxic", "Tamed_Wyvern_Mini_Fire",
                        "Tamed_Wyvern_Mini_Void", "Tamed_Wyvern_Mini_Lightning",
                        "Tamed_Wyvern_Mini_Ice"),
                strings(json("Server/Tamework/Items/Commands/HyDragonDragonHorn.json")
                        .getAsJsonObject("AllowedRoles").getAsJsonArray("Allowlist")));
    }

    @Test
    void toxicHydraNamesAreLocalizedExactlyOnceInEveryCatalog() throws Exception {
        for (LocaleExpectation locale : List.of(
                new LocaleExpectation("en-US", "Toxic Hydra", "Bonded Toxic Hydra"),
                new LocaleExpectation("de-DE", "Toxische Hydra", "Gebundene toxische Hydra"),
                new LocaleExpectation("es-ES", "Hidra tóxica", "Hidra tóxica vinculada"),
                new LocaleExpectation("fr-FR", "Hydre toxique", "Hydre toxique liée"),
                new LocaleExpectation("pt-BR", "Hidra tóxica", "Hidra tóxica vinculada"))) {
            assertLocaleEntryExactlyOnce(locale.locale(), "npcRoles.Hydra_Toxic.name", locale.wildName());
            assertLocaleEntryExactlyOnce(locale.locale(), "npcRoles.Tamed_Hydra_Toxic.name", locale.tamedName());
        }
    }

    private static void assertLeaf(String leaf, String expected) throws IOException {
        JsonObject actual = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/" + leaf);
        assertFalse(actual.has("RunTime"), leaf + " must leave cadence to its caller");
        assertEquals(JsonParser.parseString(expected).getAsJsonObject(), actual, leaf);
    }

    private static void assertToxicRole(String role, String reference, String tameRoleChange)
            throws IOException {
        assertToxicRole(toxicRole(role), role, reference, tameRoleChange);
    }

    private static void assertToxicRole(
            JsonObject root, String role, String reference, String tameRoleChange) {
        assertEquals("Variant", root.get("Type").getAsString());
        assertEquals(reference, root.get("Reference").getAsString());
        assertEquals(Set.of("Type", "Reference", "Modify", "Parameters"), root.keySet());

        JsonObject modify = root.getAsJsonObject("Modify");
        Set<String> expectedModifyKeys = tameRoleChange == null
                ? Set.of("Appearance", "FlockArray", "MemoriesNameOverride", "_InteractionVars", "NameTranslationKey")
                : Set.of("Appearance", "TameRoleChange", "FlockArray", "MemoriesNameOverride",
                        "_CombatConfig", "_InteractionVars", "NameTranslationKey");
        assertEquals(expectedModifyKeys, modify.keySet());
        assertEquals("Hydra_Toxic", modify.get("Appearance").getAsString());
        assertEquals(List.of(role), strings(modify.getAsJsonArray("FlockArray")));
        assertEquals("Toxic Hydra", modify.get("MemoriesNameOverride").getAsString());
        assertEquals(JsonParser.parseString("""
                {"Compute":"NameTranslationKey"}
                """).getAsJsonObject(), modify.getAsJsonObject("NameTranslationKey"));
        if (tameRoleChange == null) {
            assertFalse(modify.has("TameRoleChange"));
            assertFalse(modify.has("_CombatConfig"));
        } else {
            assertEquals(tameRoleChange, modify.get("TameRoleChange").getAsString());
            assertEquals("CAE_Hydra_Toxic", modify.get("_CombatConfig").getAsString());
        }

        JsonObject vars = modify.getAsJsonObject("_InteractionVars");
        assertEquals(Set.of(
                "Bite_Damage", "Swipe_Left_Damage", "Swipe_Right_Damage", "Stomp_Damage",
                "Tail_Spin_Damage", "Hydra_Ball_Charge_Effect", "Hydra_Ball_Launch",
                "Hydra_Rain_Charge_Effect", "Hydra_Rain_Launch"), vars.keySet());
        assertVariableLeaf(vars, "Hydra_Ball_Charge_Effect", "Hydra_Toxic_Ball_Charge_Effect");
        assertVariableLeaf(vars, "Hydra_Ball_Launch", "Hydra_Toxic_Ball_Launch");
        assertVariableLeaf(vars, "Hydra_Rain_Charge_Effect", "Hydra_Rain_Toxic_Charge_Effect");
        assertVariableLeaf(vars, "Hydra_Rain_Launch", "Hydra_Rain_Toxic_Launch");
        assertPoisonMelee(vars, "Bite_Damage", "Hydra_Bite_Damage");
        assertPoisonMelee(vars, "Swipe_Left_Damage", "Hydra_Swipe_Left_Damage");
        assertPoisonMelee(vars, "Swipe_Right_Damage", "Hydra_Swipe_Right_Damage");
        assertPoisonMelee(vars, "Stomp_Damage", "Hydra_Stomp_Damage");
        assertPoisonMelee(vars, "Tail_Spin_Damage", "Hydra_Tail_Spin_Damage");

        assertEquals(Set.of("NameTranslationKey"), root.getAsJsonObject("Parameters").keySet());
        assertEquals("server.npcRoles." + role + ".name", root.getAsJsonObject("Parameters")
                .getAsJsonObject("NameTranslationKey").get("Value").getAsString());
    }

    private static void assertPoisonMelee(JsonObject vars, String variable, String parent) {
        JsonArray interactions = vars.getAsJsonObject(variable).getAsJsonArray("Interactions");
        assertEquals(1, interactions.size());
        JsonObject interaction = interactions.get(0).getAsJsonObject();
        assertEquals(Set.of("Parent", "Next"), interaction.keySet());
        assertEquals(parent, interaction.get("Parent").getAsString());
        JsonObject next = interaction.getAsJsonObject("Next");
        assertEquals(Set.of("Type", "EffectId", "Entity"), next.keySet());
        assertEquals("ApplyEffect", next.get("Type").getAsString());
        assertEquals("Target", next.get("Entity").getAsString());
        assertEquals("Poison_T1", next.get("EffectId").getAsString());
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

    private static void assertReplacement(JsonObject interaction, String variable, String leaf) {
        assertEquals("Replace", interaction.get("Type").getAsString());
        assertTrue(interaction.get("DefaultOk").getAsBoolean());
        assertEquals(variable, interaction.get("Var").getAsString());
        assertFalse(interaction.has("RunTime"), "Replace executes its selected interaction immediately");
        JsonArray defaults = interaction.getAsJsonObject("DefaultValue").getAsJsonArray("Interactions");
        assertEquals(1, defaults.size());
        assertEquals(leaf, defaults.get(0).getAsString());
    }

    private static void assertAnimationPath(JsonObject animationSets, String animationSet, String suffix) {
        String path = animationSets.getAsJsonObject(animationSet).getAsJsonArray("Animations")
                .get(0).getAsJsonObject().get("Animation").getAsString();
        assertEquals("NPC/HyDragon/Hydra_Winged/" + suffix, path);
        assertTrue(Files.isRegularFile(ROOT.resolve("Common").resolve(path)), path);
    }

    private static Set<String> modelNodeNames(JsonObject model) {
        Set<String> names = new HashSet<>();
        collectModelNodeNames(model.getAsJsonArray("nodes"), names);
        return names;
    }

    private static void collectModelNodeNames(JsonArray nodes, Set<String> names) {
        for (JsonElement element : nodes) {
            JsonObject node = element.getAsJsonObject();
            names.add(node.get("name").getAsString());
            if (node.has("children")) collectModelNodeNames(node.getAsJsonArray("children"), names);
        }
    }

    private static void assertDelay(JsonObject interaction, double runTime) {
        assertEquals(Set.of("Type", "RunTime"), interaction.keySet());
        assertEquals("Simple", interaction.get("Type").getAsString());
        assertEquals(runTime, interaction.get("RunTime").getAsDouble());
    }

    private static void assertPublicParameter(String role, String parameter, String expectedValue)
            throws IOException {
        JsonObject root = json("Server/NPC/Roles/Creature/HyDragon/Hydra/" + role + ".json");
        JsonObject declaration = root.getAsJsonObject("Parameters").getAsJsonObject(parameter);
        assertNotNull(declaration, role + " must expose " + parameter + " to nested variants");
        assertEquals(JsonParser.parseString(expectedValue), declaration.get("Value"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"" + parameter + "\"}"),
                root.getAsJsonObject("Modify").get(parameter));
    }

    private static void assertVariableLeaf(JsonObject vars, String variable, String leaf) {
        JsonObject wrapper = vars.getAsJsonObject(variable);
        assertEquals(Set.of("Interactions"), wrapper.keySet());
        JsonArray interactions = wrapper.getAsJsonArray("Interactions");
        assertEquals(1, interactions.size());
        JsonObject interaction = interactions.get(0).getAsJsonObject();
        assertEquals(Set.of("Parent"), interaction.keySet());
        assertEquals(leaf, interaction.get("Parent").getAsString());
    }

    private static JsonObject toxicRole(String role) throws IOException {
        return json("Server/NPC/Roles/Creature/HyDragon/Hydra/" + role + ".json").deepCopy();
    }

    private static JsonObject parseSpawn(Path path) {
        try {
            return parseJson(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read spawn asset " + path, exception);
        }
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }

    private static List<Integer> ints(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsInt).toList();
    }

    private static List<Double> doubles(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsDouble).toList();
    }

    private static Map<String, String> stringMap(JsonObject values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        return result;
    }

    private static void assertLocaleEntryExactlyOnce(String locale, String key, String value)
            throws IOException {
        List<String> matchingLines = Files.readAllLines(ROOT.resolve("Server/Languages")
                        .resolve(locale).resolve("server.lang"), StandardCharsets.UTF_8)
                .stream().filter(line -> line.startsWith(key + "=")).toList();
        assertEquals(1, matchingLines.size(), locale + " " + key);
        assertEquals(key + "=" + value, matchingLines.getFirst(), locale + " " + key);
    }

    private static JsonObject json(String relativePath) throws IOException {
        return parseJson(read(relativePath));
    }

    private static JsonObject parseJson(String content) {
        try (JsonReader reader = new JsonReader(new StringReader(content))) {
            rejectDuplicateObjectKeys(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("JSON contains trailing content");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid JSON", exception);
        }
        return JsonParser.parseString(content).getAsJsonObject();
    }

    private static void rejectDuplicateObjectKeys(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) rejectDuplicateObjectKeys(reader);
                reader.endArray();
            }
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IllegalArgumentException("Duplicate JSON key: " + name);
                    rejectDuplicateObjectKeys(reader);
                }
                reader.endObject();
            }
            default -> reader.skipValue();
        }
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private record LocaleExpectation(String locale, String wildName, String tamedName) {
    }
}
