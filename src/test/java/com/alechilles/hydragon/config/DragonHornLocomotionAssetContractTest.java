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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class DragonHornLocomotionAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));
    private static final List<String> COMMAND_IDS = List.of(
            "Follow", "Hold", "Recall", "MoveToPing", "Defend", "AttackTarget", "Idle", "ToggleAirborneMode");
    private static final Map<String, String> HORN_COMMAND_SOUNDS = Map.of(
            "Follow", "SFX_HyDragon_Dragon_Flute_SE_01",
            "Hold", "SFX_HyDragon_Dragon_Flute_SE_09",
            "Recall", "SFX_HyDragon_Dragon_Flute_SE_17",
            "MoveToPing", "SFX_HyDragon_Dragon_Flute_SE_04",
            "Defend", "SFX_HyDragon_Dragon_Flute_SE_11",
            "AttackTarget", "SFX_HyDragon_Dragon_Flute_SE_07",
            "Idle", "SFX_HyDragon_Dragon_Flute_SE_02",
            "ToggleAirborneMode", "SFX_HyDragon_Dragon_Flute_SE_05");
    private static final Set<String> HORN_COMMAND_LANGUAGE_KEYS = Set.of(
            "hydragon.commands.defend.name",
            "hydragon.commands.defend.hud",
            "hydragon.commands.toggleAirborneMode.name",
            "hydragon.commands.toggleAirborneMode.hud");
    private static final List<String> LOCALES = List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR");
    private static final Map<String, JsonElement> FULL_DRAGON_UNCHANGED_CONTROLLERS = Map.of(
            "Walk", JsonParser.parseString("""
                    {"Type":"Walk","MaxWalkSpeed":{"Compute":"MaxSpeed"},"Gravity":10,"RunThreshold":{"Compute":"RunThreshold"},"RunThresholdRange":0.05,"MaxFallSpeed":15,"MaxRotationSpeed":180,"Acceleration":10,"MaxClimbHeight":{"Compute":"ClimbHeight"},"MinJumpHeight":{"Compute":"MinJumpHeight"},"AscentAnimationType":{"Compute":"AscentAnimationType"},"ClimbSpeedMult":{"Compute":"ClimbSpeedMult"},"ClimbSpeedPow":{"Compute":"ClimbSpeedPow"},"ClimbSpeedConst":{"Compute":"ClimbSpeedConst"},"DescendSpeedCompensation":{"Compute":"DescendSpeedCompensation"},"DescendFlatness":{"Compute":"DescendFlatness"},"DescentSteepness":{"Compute":"DescentSteepness"},"DescentBlending":{"Compute":"DescentBlending"}}
                    """),
            "TameworkRideWalk", JsonParser.parseString("""
                    {"Type":"TameworkRideWalk","MaxWalkSpeed":{"Compute":"MaxSpeed"},"MountedMaxWalkSpeed":50,"MountedSprintMultiplier":1.35,"Gravity":10,"RunThreshold":{"Compute":"RunThreshold"},"RunThresholdRange":0.05,"MaxFallSpeed":15,"MaxRotationSpeed":180,"Acceleration":25,"MaxClimbHeight":{"Compute":"ClimbHeight"},"MinJumpHeight":{"Compute":"MinJumpHeight"},"AscentAnimationType":{"Compute":"AscentAnimationType"},"ClimbSpeedMult":{"Compute":"ClimbSpeedMult"},"ClimbSpeedPow":{"Compute":"ClimbSpeedPow"},"ClimbSpeedConst":{"Compute":"ClimbSpeedConst"},"DescendSpeedCompensation":{"Compute":"DescendSpeedCompensation"},"DescendFlatness":{"Compute":"DescendFlatness"},"DescentSteepness":{"Compute":"DescentSteepness"},"DescentBlending":{"Compute":"DescentBlending"}}
                    """),
            "TameworkFly", JsonParser.parseString("""
                    {"Type":"TameworkFly","MinAirSpeed":0,"MaxHorizontalSpeed":10,"MaxClimbSpeed":8,"MaxSinkSpeed":10,"MaxFallSpeed":25,"MaxSinkSpeedFluid":4,"MaxClimbAngle":65,"MaxSinkAngle":75,"Acceleration":5,"Deceleration":12,"Gravity":18,"MaxTurnSpeed":240,"MaxRollAngle":35,"MaxRollSpeed":240,"RollDamping":0.78,"MinHeightOverGround":8,"MaxHeightOverGround":25,"FastFlyThreshold":0.55,"AutoLevel":true,"DesiredAltitudeWeight":0.8,"MountedMaxHorizontalSpeed":20,"MountedMaxClimbSpeed":8,"MountedMaxSinkSpeed":8,"MountedAcceleration":8,"MountedDeceleration":20,"MountedSprintMultiplier":1.70}
                    """),
            "TameworkMountedGlide", JsonParser.parseString("""
                    {"Type":"TameworkMountedGlide","MinAirSpeed":0,"MaxHorizontalSpeed":48,"MaxClimbSpeed":16,"MaxSinkSpeed":18,"MaxFallSpeed":32,"MaxSinkSpeedFluid":4,"MaxClimbAngle":70,"MaxSinkAngle":80,"Acceleration":10,"Deceleration":16,"Gravity":14,"MaxTurnSpeed":260,"MaxRollAngle":40,"MaxRollSpeed":260,"RollDamping":0.78,"MinHeightOverGround":4,"MaxHeightOverGround":36,"FastFlyThreshold":0.55,"AutoLevel":false,"DesiredAltitudeWeight":0.35}
                    """));
    private static final JsonElement FULL_DRAGON_RIDDEN_BEHAVIOR = JsonParser.parseString("""
            {"$Comment":"Mounted State: rider input fully drives land and flight movement.","Sensor":{"Type":"State","State":"Ridden"},"Instructions":[{"BodyMotion":{"Type":"TameworkMountedGlide"}}]}
            """);

    @Test
    void dragonHornDefinesTheExplicitStatePreservingLocomotionCommandContract() throws IOException {
        JsonObject horn = readJson("Server/Tamework/Items/Commands/HyDragonDragonHorn.json");

        assertTrue(horn.has("CommandList"),
                "HyDragonDragonHorn must explicitly replace the inherited CommandList array");
        JsonArray commands = horn.getAsJsonArray("CommandList");
        assertNotNull(commands);
        assertEquals(COMMAND_IDS, commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(command -> command.get("Id").getAsString())
                .toList());
        assertFalse(commandIds(commands).contains("SetHome"));
        assertFalse(commandIds(commands).contains("ReturnHome"));
        assertHornCommandFeedback(commands);
        assertHornSoundAssets();

        JsonObject follow = command(commands, "Follow");
        assertTrue(follow.get("Default").getAsBoolean());
        assertEquals(1, commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(command -> command.has("Default") && command.get("Default").getAsBoolean())
                .count());

        assertDefendSteps(command(commands, "Defend").getAsJsonArray("Steps"));
        assertAttackTargetSteps(command(commands, "AttackTarget").getAsJsonArray("Steps"));
        assertToggleSteps(command(commands, "ToggleAirborneMode").getAsJsonArray("Steps"));

        assertIdenticalHornCommandCatalogs();
    }

    @Test
    void companionFlightToggleCapabilityIsOptInForMiniwyvernsAndNordicDrakesOnly() throws IOException {
        JsonObject miniwyvern = readJson("Server/Tamework/Companion/HyDragonMiniwyvern.json");
        JsonObject nordic = readJson("Server/Tamework/Companion/HyDragonNordicDrake.json");
        JsonObject groundOnly = readJson("Server/Tamework/Companion/HyDragonFullDragons.json");

        assertEquals(List.of(
                "Tamed_Wyvern_Mini_Wild", "Tamed_Wyvern_Mini_Nature", "Tamed_Wyvern_Mini_Toxic",
                "Tamed_Wyvern_Mini_Fire", "Tamed_Wyvern_Mini_Void", "Tamed_Wyvern_Mini_Lightning",
                "Tamed_Wyvern_Mini_Ice"), roleIds(miniwyvern));
        assertFlightToggle(miniwyvern);

        assertEquals(List.of("Tamed_NordicDrake"), roleIds(nordic));
        assertEquals(128.0, number(nordic, "Command.ReturnHomeTeleportDistance"));
        assertEquals(32.0, number(nordic, "Command.ReturnHomePathDistanceBeforeTeleport"));
        assertEquals(24.0, number(nordic, "Command.RecallSafeSpawnDistance"));
        assertEquals(96.0, number(nordic, "Command.RecallForceRelocateDistance"));
        assertEquals(-4.0, number(nordic, "Command.PlacementMinRelativeY"));
        assertEquals(8.0, number(nordic, "Command.PlacementMaxRelativeY"));
        assertFlightToggle(nordic);

        assertEquals(List.of(
                "Tamed_Hydra", "Tamed_Hydra_Toxic", "Tamed_RockDrakeT1", "Tamed_RockDrakeT2",
                "Tamed_RockDrakeT3"),
                roleIds(groundOnly));
        String serializedGroundOnly = groundOnly.toString();
        assertFalse(groundOnly.getAsJsonObject("Command").has("FlightToggle"));
        assertFalse(serializedGroundOnly.contains("FlightToggle"));
        assertFalse(serializedGroundOnly.contains("AirborneMode"));
        assertFalse(serializedGroundOnly.contains("HyDragon.Command.ToggleAirborneMode"));
    }

    @Test
    void tamedDragonTemplatesUseTheSharedNativeAirborneModeTransition() throws IOException {
        JsonObject miniwyvern = readJson("Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json");
        JsonObject fullDragon = readJson("Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json");
        JsonObject transition = readJson("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Airborne_Mode_Transition.json");

        assertGlobalContinuingReference(miniwyvern);
        assertGlobalContinuingReference(fullDragon);
        assertEquals("Walk", string(miniwyvern, "InitialMotionController"),
                "Miniwyverns must spawn grounded instead of randomly selecting flight");
        assertEquals("Walk", string(fullDragon, "InitialMotionController"),
                "Nordic Drakes must spawn grounded instead of randomly selecting a ride or flight controller");
        assertNonFlyingDragonRolesRemainWalkOnly();

        assertEquals("Component", transition.get("Type").getAsString());
        assertEquals("Instruction", transition.get("Class").getAsString());
        JsonObject content = transition.getAsJsonObject("Content");
        assertFalse(hasOnceSetFlag(content, "AirborneMode", false),
                "changing a horn command state must never reset a dragon's selected airborne mode");
        assertTrue(hasLegacyFlyingControllerNeutralizer(content),
                "legacy flying-controller state must be neutralized before it can force a grounded handoff");
        assertEquals(1, countHookSensors(content, "HyDragon.Command.ToggleAirborneMode"),
                "the transition component must consume exactly the ToggleAirborneMode hook");
        assertTrue(hasConsumingHook(content, "HyDragon.Command.ToggleAirborneMode"),
                "the exact ToggleAirborneMode hook must explicitly consume its one signal");
        assertTrue(hasMutuallyExclusiveToggle(content),
                "the native Flag branches must clear when set and set when unset");
        assertTrue(hasTakeOff(content), "AirborneMode=true on Walk must take off");
        assertTrue(hasSafeLanding(content), "AirborneMode=false on Fly must safely land and reset its search ray");
        assertNoTransitionScopeViolations(content);
    }

    @Test
    void nativeTakeoffClearsGroundedStatusAnimationBeforeFlightControllerStarts() throws IOException {
        JsonObject transition = readJson("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Airborne_Mode_Transition.json");

        assertTrue(hasTakeOffWithStatusAnimationClear(transition.getAsJsonObject("Content")),
                "native takeoff must clear the grounded Status animation so FlyIdle can be selected without a command");
    }

    @Test
    void miniwyvernSelectsLocomotionInsideEachCommandWithoutMutatingCommandStateOrTargets() throws IOException {
        JsonObject miniwyvern = readJson("Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json");

        JsonObject idle = stateBehavior(miniwyvern, "Idle");
        assertExactlyTwoDirectModeBranches(idle);
        assertModeBranch(idle, false, "Walk", "WanderInCircle", null);
        assertModeBranch(idle, true, "Fly", "WanderInCircle", null);

        JsonObject follow = stateBehavior(miniwyvern, "Follow");
        assertExactlyTwoDirectModeBranches(follow);
        assertModeBranch(follow, false, "Walk", null, "Component_Tamework_Instruction_Follow_Advanced");
        assertModeBranch(follow, true, "Fly", null, "Component_Tamework_Instruction_Follow_Flying");
        assertAerialFollowTuning(follow);

        JsonObject defend = stateBehavior(miniwyvern, "Defend");
        assertExactlyTwoDirectModeBranches(defend);
        assertModeBranch(defend, false, "Walk", null, "Component_Tamework_Instruction_Defend");
        assertModeBranch(defend, true, "Fly", null,
                "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend");
        assertDefendFollowMacro(defend, false, "Component_Tamework_Instruction_Follow_Advanced");
        assertGroundedDefendTuning(defend);
        assertAerialDefendTuning(defend);

        JsonObject hold = stateBehavior(miniwyvern, "Hold");
        assertExactlyTwoDirectModeBranches(hold);
        assertModeBranch(hold, false, "Walk", "Nothing", null);
        assertModeBranch(hold, true, "Fly", "Nothing", null);
        assertFalse(anyObject(hold, object -> object.has("BodyMotion")
                && "Sleep".equals(string(object.getAsJsonObject("BodyMotion"), "Type"))));

        for (JsonObject behavior : List.of(idle, follow, defend, hold)) {
            for (JsonObject branch : directModeBranches(behavior)) {
                assertNoModeSelectionMutation(branch);
            }
        }
    }

    @Test
    void fullDragonSelectsNativeFlightInsideEachCommandWithoutChangingMountedBehaviorOrCommandState()
            throws IOException {
        JsonObject fullDragon = readJson("Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json");

        assertNativeFlyController(fullDragon);
        assertEquals("Walk", string(fullDragon, "InitialMotionController"));
        assertMountedControllerContract(fullDragon);

        JsonObject idle = stateBehavior(fullDragon, "Idle");
        assertExactlyTwoDirectModeBranches(idle);
        assertModeBranch(idle, false, "Walk", null, "Component_Tamework_Instruction_Wander");
        assertModeBranch(idle, true, "Fly", "WanderInCircle", null);

        JsonObject follow = stateBehavior(fullDragon, "Follow");
        assertExactlyTwoDirectModeBranches(follow);
        assertModeBranch(follow, false, "Walk", null, "Component_Tamework_Instruction_Follow_Advanced");
        assertModeBranch(follow, true, "Fly", null, "Component_Tamework_Instruction_Follow_Flying");
        assertAerialFollowTuning(follow);

        JsonObject defend = stateBehavior(fullDragon, "Defend");
        assertExactlyTwoDirectModeBranches(defend);
        assertModeBranch(defend, false, "Walk", null, "Component_Tamework_Instruction_Defend");
        assertModeBranch(defend, true, "Fly", null, "Component_Tamework_Instruction_Defend");
        assertNoDefendFollowMacro(defend, false);
        assertDefendFollowMacro(defend, true, "Component_Tamework_Instruction_Follow_Flying");
        assertFullDragonDefendTuning(defend, false);
        assertFullDragonDefendTuning(defend, true);

        JsonObject hold = stateBehavior(fullDragon, "Hold");
        assertExactlyTwoDirectModeBranches(hold);
        assertModeBranch(hold, false, "Walk", null, "Component_Tamework_Instruction_Hold");
        assertModeBranch(hold, true, "Fly", "Nothing", null);
        assertFalse(anyObject(hold, object -> object.has("BodyMotion")
                && "Sleep".equals(string(object.getAsJsonObject("BodyMotion"), "Type"))));

        for (JsonObject behavior : List.of(idle, follow, defend, hold)) {
            for (JsonObject branch : directModeBranches(behavior)) {
                assertNoModeSelectionMutation(branch);
            }
        }

        JsonObject ridden = stateBehavior(fullDragon, "Ridden");
        assertEquals(FULL_DRAGON_RIDDEN_BEHAVIOR, ridden,
                "Ridden behavior must remain exactly the pre-flight-mode baseline structure");
    }

    @Test
    void modeSelectorContractRejectsConflictingLooseAndOverconstrainedDirectBranches() {
        JsonObject conflicting = JsonParser.parseString("""
                { "Instructions": [
                  { "Sensor": { "Type": "And", "Sensors": [
                    { "Type": "Flag", "Name": "AirborneMode", "Set": false },
                    { "Type": "MotionController", "MotionController": "Walk" } ] } },
                  { "Sensor": { "Type": "And", "Sensors": [
                    { "Type": "Flag", "Name": "AirborneMode" },
                    { "Type": "MotionController", "MotionController": "Walk" } ] } }
                ] }
                """).getAsJsonObject();
        assertFalse(hasExactModeSelectorPairSet(conflicting));

        JsonObject looseFlag = JsonParser.parseString("""
                { "Instructions": [
                  { "Sensor": { "Type": "And", "Sensors": [
                    { "Type": "Flag", "Name": "AirborneMode", "Set": false },
                    { "Type": "MotionController", "MotionController": "Walk" } ] } },
                  { "Sensor": { "Type": "And", "Sensors": [
                    { "Type": "Flag", "Name": "AirborneMode" },
                    { "Type": "MotionController", "MotionController": "Fly" } ] } },
                  { "Sensor": { "Type": "Flag", "Name": "AirborneMode" } }
                ] }
                """).getAsJsonObject();
        assertFalse(hasExactModeSelectorPairSet(looseFlag));

        JsonObject extraLockedTarget = JsonParser.parseString("""
                { "Instructions": [
                  { "Sensor": { "Type": "And", "Sensors": [
                    { "Type": "Flag", "Name": "AirborneMode", "Set": false },
                    { "Type": "MotionController", "MotionController": "Walk" },
                    { "Type": "Target", "TargetSlot": "LockedTarget" } ] } },
                  { "Sensor": { "Type": "And", "Sensors": [
                    { "Type": "Flag", "Name": "AirborneMode" },
                    { "Type": "MotionController", "MotionController": "Fly" } ] } }
                ] }
                """).getAsJsonObject();
        assertFalse(hasExactModeSelectorPairSet(extraLockedTarget));

        JsonObject controllerOnly = JsonParser.parseString("""
                { "Instructions": [
                  { "Sensor": { "Type": "And", "Sensors": [
                    { "Type": "Flag", "Name": "AirborneMode", "Set": false },
                    { "Type": "MotionController", "MotionController": "Walk" } ] } },
                  { "Sensor": { "Type": "And", "Sensors": [
                    { "Type": "Flag", "Name": "AirborneMode" },
                    { "Type": "MotionController", "MotionController": "Fly" } ] } },
                  { "Sensor": { "Type": "MotionController", "MotionController": "Fly" } }
                ] }
                """).getAsJsonObject();
        assertFalse(hasExactModeSelectorPairSet(controllerOnly));

        JsonObject extra = looseFlag.deepCopy();
        extra.getAsJsonArray("Instructions").remove(2);
        extra.getAsJsonArray("Instructions").add(JsonParser.parseString("""
                { "Sensor": { "Type": "And", "Sensors": [
                  { "Type": "Flag", "Name": "AirborneMode" },
                  { "Type": "MotionController", "MotionController": "Fly" } ] } }
                """));
        extra.getAsJsonArray("Instructions").add(JsonParser.parseString("""
                { "Sensor": { "Type": "And", "Sensors": [
                  { "Type": "Flag", "Name": "AirborneMode", "Set": false },
                  { "Type": "MotionController", "MotionController": "Walk" } ] } }
                """));
        assertFalse(hasExactModeSelectorPairSet(extra));
    }

    @Test
    void miniwyvernHasNoLegacyFlightStateMachineAndTalentProjectilesRemainDefendTargetGated() throws IOException {
        JsonObject miniwyvern = readJson("Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json");

        assertEquals(0, countObjects(miniwyvern, object -> "TameworkSetFlyingCompanionMode".equals(string(object, "Type"))));
        assertEquals(0, countObjects(miniwyvern, object -> "State".equals(string(object, "Type"))
                && Set.of("TakeOff", "Land", "HoldGrounded").contains(string(object, "State"))));
        assertEquals(0, countObjects(miniwyvern, object -> object.has("BodyMotion")
                && Set.of("TakeOff", "Land").contains(string(object.getAsJsonObject("BodyMotion"), "Type"))));

        for (JsonObject projectile : objects(miniwyvern, object -> hasTalentGate(object) && hasProjectileAction(object))) {
            assertTrue(hasStateSensor(projectile, "Defend"), "talent projectile must require State=Defend");
            assertTrue(hasTargetSensor(projectile, "LockedTarget"), "talent projectile must require LockedTarget");
        }
    }

    @Test
    void fullDragonLandingContractRejectsFractionalValuesAndAnUnwrappedLandingRay() throws IOException {
        JsonObject fractionalRay = JsonParser.parseString("""
                { "Type": "SearchRay", "Name": "LandingRay", "Range": 64.5,
                  "Angle": 90, "Blocks": "StoneAndSoil" }
                """).getAsJsonObject();
        JsonObject fractionalLand = JsonParser.parseString("""
                { "BodyMotion": { "Type": "Land", "UsePathfinder": false, "SkipSteering": false,
                  "SlowDownDistance": 5, "StopDistance": 0.55, "HeightDifference": [-3, 2],
                  "GoalLenience": 3, "DesiredAltitudeWeight": 0 } }
                """).getAsJsonObject();
        assertFalse(isFullDragonLandingRay(fractionalRay));
        assertFalse(isFullDragonLandMotion(fractionalLand));

        JsonObject content = readJson("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Airborne_Mode_Transition.json")
                .getAsJsonObject("Content").deepCopy();
        JsonObject landingInstruction = content.getAsJsonArray("Instructions").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(instruction -> isAirborneControllerBranch(instruction, false, "Fly"))
                .findFirst().orElseThrow();
        JsonObject landingAttempt = landingInstruction.getAsJsonArray("Instructions").get(0).getAsJsonObject();
        landingAttempt.add("Sensor", landingAttempt.getAsJsonObject("Sensor").get("Sensor"));
        assertFalse(hasSafeLanding(content), "a SearchRay outside AdjustPosition is not a safe landing branch");
    }

    private static void assertGlobalContinuingReference(JsonObject template) {
        JsonArray instructions = template.getAsJsonArray("Instructions");
        assertTrue(instructions.asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .anyMatch(instruction -> "Component_HyDragon_Instruction_Airborne_Mode_Transition".equals(
                                        string(instruction, "Reference"))
                                && instruction.has("Continue")
                                && instruction.get("Continue").getAsBoolean()),
                "tamed template must invoke the shared transition as a global continuing instruction");
    }

    private static void assertNonFlyingDragonRolesRemainWalkOnly() throws IOException {
        JsonObject genericDragon = readJson("Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Tamed.json");
        assertEquals(1, genericDragon.getAsJsonArray("MotionControllerList").size(),
                "the Hydra/Rock Drake shared template must remain walk-only");
        assertEquals("Walk", string(genericDragon.getAsJsonArray("MotionControllerList")
                .get(0).getAsJsonObject(), "Type"));
        for (String role : List.of(
                "Hydra/Tamed_Hydra", "RockDrake/Tamed_RockDrakeT1", "RockDrake/Tamed_RockDrakeT2",
                "RockDrake/Tamed_RockDrakeT3")) {
            assertEquals("Template_HyDragon_Tamed", string(readJson(
                    "Server/NPC/Roles/Creature/HyDragon/" + role + ".json"), "Reference"));
        }
    }

    private static void assertNativeFlyController(JsonObject template) {
        JsonObject parameters = template.getAsJsonObject("Parameters");
        assertTrue(parameters.has("FlightSpeed"), "full dragon must define FlightSpeed");
        assertEquals(JsonParser.parseString("12"), parameters.getAsJsonObject("FlightSpeed").get("Value"));
        JsonObject fly = template.getAsJsonArray("MotionControllerList").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(controller -> "Fly".equals(string(controller, "Type")))
                .findFirst().orElseThrow(() -> new AssertionError("missing native Fly controller"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"FlightSpeed\"}"), fly.get("MaxHorizontalSpeed"));
        assertEquals(JsonParser.parseString("8"), fly.get("MaxSinkSpeed"));
        assertEquals(JsonParser.parseString("10"), fly.get("MaxClimbSpeed"));
        assertEquals(JsonParser.parseString("0"), fly.get("MinAirSpeed"));
        assertEquals(JsonParser.parseString("6"), fly.get("Acceleration"));
        assertEquals(JsonParser.parseString("8"), fly.get("MinHeightOverGround"));
        assertEquals(JsonParser.parseString("25"), fly.get("MaxHeightOverGround"));
        assertEquals(JsonParser.parseString("70"), fly.get("MaxRollAngle"));
        assertEquals(JsonParser.parseString("180"), fly.get("MaxTurnSpeed"));
        assertEquals(JsonParser.parseString("0.8"), fly.get("DesiredAltitudeWeight"));
        assertTrue(fly.get("AutoLevel").getAsBoolean());
    }

    private static void assertMountedControllerContract(JsonObject template) {
        JsonArray controllers = template.getAsJsonArray("MotionControllerList");
        for (Map.Entry<String, JsonElement> expected : FULL_DRAGON_UNCHANGED_CONTROLLERS.entrySet()) {
            JsonElement actual = controllers.asList().stream()
                    .filter(JsonElement::isJsonObject)
                    .filter(controller -> expected.getKey().equals(string(controller.getAsJsonObject(), "Type")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing " + expected.getKey() + " controller"));
            assertEquals(expected.getValue(), actual,
                    expected.getKey() + " controller must remain exactly the pre-flight-mode baseline structure");
        }
    }

    private static boolean hasOnceSetFlag(JsonElement node, String name, boolean value) {
        return anyObject(node, object -> object.has("Actions")
                && object.has("Sensor")
                && object.getAsJsonObject("Sensor").has("Once")
                && object.getAsJsonObject("Sensor").get("Once").getAsBoolean()
                && actionExists(object.getAsJsonArray("Actions"), "SetFlag", name, value));
    }

    private static boolean hasLegacyFlyingControllerNeutralizer(JsonElement node) {
        return anyObject(node, object -> "TameworkSetFlyingCompanionMode".equals(string(object, "Type"))
                && "Follow".equals(string(object, "Mode")));
    }

    private static int countHookSensors(JsonElement node, String hookId) {
        return countObjects(node, object -> "TameworkHook".equals(string(object, "Type"))
                && hookId.equals(string(object, "HookId")));
    }

    private static boolean hasConsumingHook(JsonElement node, String hookId) {
        return anyObject(node, object -> "TameworkHook".equals(string(object, "Type"))
                && hookId.equals(string(object, "HookId"))
                && object.has("Consume") && object.get("Consume").getAsBoolean());
    }

    private static boolean hasMutuallyExclusiveToggle(JsonElement node) {
        return anyObject(node, object -> "Flag".equals(string(object, "Type"))
                        && "AirborneMode".equals(string(object, "Name"))
                        && !object.has("Set")
                        && actionExistsInParent(node, object, "SetFlag", "AirborneMode", false))
                && anyObject(node, object -> "Flag".equals(string(object, "Type"))
                        && "AirborneMode".equals(string(object, "Name"))
                        && object.has("Set")
                        && !object.get("Set").getAsBoolean()
                        && actionExistsInParent(node, object, "SetFlag", "AirborneMode", true));
    }

    private static boolean hasTakeOff(JsonElement node) {
        return anyObject(node, instruction -> isAirborneControllerBranch(instruction, true, "Walk")
                && instruction.has("BodyMotion")
                && "TakeOff".equals(string(instruction.getAsJsonObject("BodyMotion"), "Type")));
    }

    private static boolean hasTakeOffWithStatusAnimationClear(JsonElement node) {
        return anyObject(node, instruction -> isAirborneControllerBranch(instruction, true, "Walk")
                && instruction.has("BodyMotion")
                && "TakeOff".equals(string(instruction.getAsJsonObject("BodyMotion"), "Type"))
                && instruction.has("Actions")
                && instruction.getAsJsonArray("Actions").asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .anyMatch(action -> "PlayAnimation".equals(string(action, "Type"))
                                && "Status".equals(string(action, "Slot"))
                                && !action.has("Animation")));
    }

    private static boolean hasSafeLanding(JsonElement node) {
        return anyObject(node, instruction -> isAirborneControllerBranch(instruction, false, "Fly")
                && anyObject(instruction, object -> "AdjustPosition".equals(string(object, "Type"))
                        && object.getAsJsonArray("Offset").equals(JsonParser.parseString("[0,1,0]").getAsJsonArray())
                        && object.has("Sensor") && isFullDragonLandingRay(object.getAsJsonObject("Sensor")))
                && anyObject(instruction, DragonHornLocomotionAssetContractTest::isFullDragonLandMotion)
                && !anyObject(instruction, object -> object.has("BodyMotion")
                        && "Wander".equals(string(object.getAsJsonObject("BodyMotion"), "Type"))))
                && anyObject(node, instruction -> isAirborneControllerBranch(instruction, false, "Walk")
                        && instruction.has("Actions")
                        && actionTypeExists(instruction.getAsJsonArray("Actions"), "ResetSearchRays"));
    }

    private static boolean isFullDragonLandingRay(JsonObject object) {
        return "SearchRay".equals(string(object, "Type"))
                && "LandingRay".equals(string(object, "Name"))
                && object.get("Range").equals(JsonParser.parseString("64"))
                && object.get("Angle").equals(JsonParser.parseString("90"))
                && "StoneAndSoil".equals(string(object, "Blocks"));
    }

    private static boolean isFullDragonLandMotion(JsonObject object) {
        if (!object.has("BodyMotion")) {
            return false;
        }
        JsonObject motion = object.getAsJsonObject("BodyMotion");
        return "Land".equals(string(motion, "Type"))
                && !motion.get("UsePathfinder").getAsBoolean()
                && !motion.get("SkipSteering").getAsBoolean()
                && motion.get("SlowDownDistance").equals(JsonParser.parseString("5"))
                && motion.get("StopDistance").equals(JsonParser.parseString("0.5"))
                && motion.getAsJsonArray("HeightDifference").equals(JsonParser.parseString("[-3,2]"))
                && motion.get("GoalLenience").equals(JsonParser.parseString("3"))
                && motion.get("DesiredAltitudeWeight").equals(JsonParser.parseString("0"));
    }

    private static boolean isAirborneControllerBranch(JsonObject instruction, boolean airborne, String controller) {
        if (!instruction.has("Sensor")) {
            return false;
        }
        JsonObject sensor = instruction.getAsJsonObject("Sensor");
        if (!"And".equals(string(sensor, "Type")) || !sensor.has("Sensors")) {
            return false;
        }
        return sensor.getAsJsonArray("Sensors").asList().stream().map(JsonElement::getAsJsonObject)
                        .anyMatch(child -> "Flag".equals(string(child, "Type"))
                                && "AirborneMode".equals(string(child, "Name"))
                                && (airborne ? !child.has("Set") || child.get("Set").getAsBoolean()
                                        : child.has("Set") && !child.get("Set").getAsBoolean()))
                && sensor.getAsJsonArray("Sensors").asList().stream().map(JsonElement::getAsJsonObject)
                        .anyMatch(child -> "MotionController".equals(string(child, "Type"))
                                && controller.equals(string(child, "MotionController")));
    }

    private static JsonObject stateBehavior(JsonObject template, String state) {
        List<JsonObject> candidates = objects(template, object -> object.has("Sensor")
                        && "State".equals(string(object.getAsJsonObject("Sensor"), "Type"))
                        && state.equals(string(object.getAsJsonObject("Sensor"), "State"))
                        && object.has("Instructions"));
        return candidates.stream().filter(DragonHornLocomotionAssetContractTest::hasExactModeSelectorPairSet)
                .findFirst().orElseGet(() -> candidates.stream().findFirst()
                        .orElseThrow(() -> new AssertionError("missing behavior for state " + state)));
    }

    private static void assertModeBranch(
            JsonObject behavior, boolean airborne, String controller, String bodyMotion, String reference) {
        JsonObject branch = directModeBranches(behavior).stream()
                .filter(candidate -> isModeControllerPair(candidate, airborne, controller))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + (airborne ? "airborne" : "grounded")
                        + " " + controller + " branch"));
        if (bodyMotion != null) {
            assertTrue(branch.has("BodyMotion"));
            assertEquals(bodyMotion, string(branch.getAsJsonObject("BodyMotion"), "Type"));
        }
        if (reference != null) {
            assertTrue(anyObject(branch, object -> reference.equals(string(object, "Reference"))),
                    "branch must invoke " + reference);
        }
    }

    private static void assertDefendFollowMacro(JsonObject defend, boolean airborne, String expectedMacro) {
        JsonObject branch = directModeBranches(defend).stream()
                .filter(candidate -> hasAirborneMode(candidate.getAsJsonObject("Sensor"), airborne))
                .findFirst()
                .orElseThrow();
        JsonObject defendReference = objects(branch,
                        object -> "Component_Tamework_Instruction_Defend".equals(string(object, "Reference")))
                .stream().findFirst().orElseThrow();
        assertEquals(expectedMacro, string(defendReference.getAsJsonObject("Modify"), "DefendFollowMacroElement"));
    }

    private static void assertNoDefendFollowMacro(JsonObject defend, boolean airborne) {
        JsonObject branch = directModeBranches(defend).stream()
                .filter(candidate -> isModeControllerPair(candidate, airborne, airborne ? "Fly" : "Walk"))
                .findFirst().orElseThrow();
        JsonObject modify = objects(branch,
                        object -> "Component_Tamework_Instruction_Defend".equals(string(object, "Reference")))
                .stream().findFirst().orElseThrow().getAsJsonObject("Modify");
        assertFalse(modify.has("DefendFollowMacroElement"),
                "grounded Defend must retain Tamework's inherited follow macro default");
    }

    private static void assertExactlyTwoDirectModeBranches(JsonObject behavior) {
        assertTrue(hasExactModeSelectorPairSet(behavior),
                "behavior must have exactly direct AirborneMode=false/Walk and AirborneMode=true/Fly branches");
    }

    private static boolean hasExactModeSelectorPairSet(JsonObject behavior) {
        List<JsonObject> branches = directModeBranches(behavior);
        return branches.size() == 2
                && branches.stream().anyMatch(branch -> isModeControllerPair(branch, false, "Walk"))
                && branches.stream().anyMatch(branch -> isModeControllerPair(branch, true, "Fly"));
    }

    private static List<JsonObject> directModeBranches(JsonObject behavior) {
        if (!behavior.has("Instructions")) return List.of();
        return behavior.getAsJsonArray("Instructions").asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(branch -> branch.has("Sensor")
                        && (hasAirborneModeSelector(branch.getAsJsonObject("Sensor"))
                                || hasMotionControllerSelector(branch.getAsJsonObject("Sensor"))))
                .toList();
    }

    private static boolean isModeControllerPair(JsonObject branch, boolean airborne, String controller) {
        JsonObject sensor = branch.getAsJsonObject("Sensor");
        if (!"And".equals(string(sensor, "Type")) || !sensor.has("Sensors") || sensor.getAsJsonArray("Sensors").size() != 2) {
            return false;
        }
        return sensor.getAsJsonArray("Sensors").asList().stream().map(JsonElement::getAsJsonObject)
                        .anyMatch(child -> isAirborneModeFlag(child, airborne))
                && sensor.getAsJsonArray("Sensors").asList().stream().map(JsonElement::getAsJsonObject)
                        .anyMatch(child -> "MotionController".equals(string(child, "Type"))
                                && controller.equals(string(child, "MotionController")));
    }

    private static boolean hasAirborneModeSelector(JsonObject sensor) {
        return anyObject(sensor, object -> "Flag".equals(string(object, "Type"))
                && "AirborneMode".equals(string(object, "Name")));
    }

    private static boolean hasMotionControllerSelector(JsonObject sensor) {
        return anyObject(sensor, object -> "MotionController".equals(string(object, "Type")));
    }

    private static boolean isAirborneModeFlag(JsonObject object, boolean airborne) {
        return "Flag".equals(string(object, "Type"))
                && "AirborneMode".equals(string(object, "Name"))
                && (airborne ? !object.has("Set") : object.has("Set") && !object.get("Set").getAsBoolean());
    }

    private static boolean hasAirborneMode(JsonObject sensor, boolean airborne) {
        return objects(sensor, object -> "Flag".equals(string(object, "Type"))
                        && "AirborneMode".equals(string(object, "Name"))
                        && (airborne ? !object.has("Set") || object.get("Set").getAsBoolean()
                                : object.has("Set") && !object.get("Set").getAsBoolean()))
                .size() == 1;
    }

    private static boolean hasMotionController(JsonObject sensor, String controller) {
        return anyObject(sensor, object -> "MotionController".equals(string(object, "Type"))
                && controller.equals(string(object, "MotionController")));
    }

    private static void assertAerialFollowTuning(JsonObject follow) {
        JsonObject branch = directModeBranches(follow).stream()
                .filter(candidate -> isModeControllerPair(candidate, true, "Fly"))
                .findFirst().orElseThrow();
        JsonObject reference = objects(branch,
                        object -> "Component_Tamework_Instruction_Follow_Flying".equals(string(object, "Reference")))
                .stream().findFirst().orElseThrow();
        JsonObject modify = reference.getAsJsonObject("Modify");
        assertEquals("MasterTarget", string(modify, "MasterTargetSlot"));
        assertEquals(JsonParser.parseString("[4,8]"), modify.get("FollowDesiredAltitudeRange"));
        assertEquals(JsonParser.parseString("32"), modify.get("FollowTeleportThresholdRange"));
        assertEquals(JsonParser.parseString("10"), modify.get("FollowSeekSlowDownDistance"));
        assertEquals(JsonParser.parseString("6"), modify.get("FollowSeekStopDistance"));
        assertEquals(JsonParser.parseString("0.9"), modify.get("FollowSeekRelativeSpeed"));
        assertEquals(JsonParser.parseString("1.75"), modify.get("FollowHoverRadius"));
        assertEquals(JsonParser.parseString("0.12"), modify.get("FollowHoverRelativeSpeed"));
    }

    private static void assertGroundedDefendTuning(JsonObject defend) {
        JsonObject branch = directModeBranches(defend).stream()
                .filter(candidate -> isModeControllerPair(candidate, false, "Walk"))
                .findFirst().orElseThrow();
        JsonObject reference = objects(branch,
                        object -> "Component_Tamework_Instruction_Defend".equals(string(object, "Reference")))
                .stream().findFirst().orElseThrow();
        JsonObject modify = reference.getAsJsonObject("Modify");
        assertEquals(JsonParser.parseString("{\"Compute\":\"HardLeashDistance\"}"), modify.get("HardLeashDistance"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"ViewRange\"}"), modify.get("AlertedRange"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"ViewSector\"}"), modify.get("ViewSector"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"HearingRange\"}"), modify.get("HearingRange"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"AbsoluteDetectionRange\"}"), modify.get("AbsoluteDetectionRange"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"ViewRange\"}"), modify.get("ViewRange"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"Attack\"}"), modify.get("Attack"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"AttackDistance\"}"), modify.get("AttackDistance"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"AttackPauseRange\"}"), modify.get("AttackPauseRange"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"CombatAttackPreDelay\"}"), modify.get("CombatAttackPreDelay"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"CombatAttackPostDelay\"}"), modify.get("CombatAttackPostDelay"));
        assertEquals(JsonParser.parseString("8"), modify.get("CombatBehaviorDistance"));
        assertEquals(JsonParser.parseString("[2.5,4]"), modify.get("CombatBackOffDistanceRange"));
        assertEquals(JsonParser.parseString("4"), modify.get("CombatStrafeWeight"));
        assertEquals(JsonParser.parseString("8"), modify.get("CombatDirectWeight"));
        assertEquals(JsonParser.parseString("8"), modify.get("CombatAlwaysMovingWeight"));
        assertEquals(JsonParser.parseString("0.9"), modify.get("ChaseRelativeSpeed"));
        assertEquals("Component_Instruction_Null", string(modify, "AdditionalCombatBehaviorMacroElement"));
    }

    private static void assertAerialDefendTuning(JsonObject defend) throws IOException {
        JsonObject branch = directModeBranches(defend).stream()
                .filter(candidate -> isModeControllerPair(candidate, true, "Fly"))
                .findFirst().orElseThrow();
        JsonObject reference = objects(branch, object ->
                "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend"
                        .equals(string(object, "Reference"))).stream().findFirst().orElseThrow();
        JsonObject modify = reference.getAsJsonObject("Modify");
        assertEquals(JsonParser.parseString("[8,14]"), modify.get("LoiterDistanceRange"));
        assertEquals(JsonParser.parseString("[5,9]"), modify.get("LoiterAltitudeRange"));
        assertEquals(JsonParser.parseString("0.28"), modify.get("LoiterRelativeSpeed"));
        assertEquals(JsonParser.parseString("[3,6]"), modify.get("LoiterRetargetTimeRange"));
        assertEquals(JsonParser.parseString("2.5"), modify.get("LoiterStopDistance"));
        assertFalse(modify.has("LoiterWeight"));
        assertFalse(modify.has("DiveWeight"));
        assertFalse(modify.has("DiveRelativeSpeed"));
        assertEquals(JsonParser.parseString("[8,14]"), modify.get("CombatBackOffDistanceRange"));
        assertEquals(JsonParser.parseString("[2,4]"), modify.get("CombatBackOffDurationRange"));
        assertFalse(modify.has("BitePauseRange"));
        assertFalse(modify.has("Attack"));
        assertEquals("Component_Tamework_Instruction_Follow_Flying",
                string(modify, "DefendFollowMacroElement"));

        JsonObject component = readJson("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json");
        assertEquals("Component", string(component, "Type"));
        assertEquals("Instruction", string(component, "Class"));
        assertMiniwyvernAerialDefendStructure(component);
    }

    private static void assertMiniwyvernAerialDefendStructure(JsonObject component) {
        JsonObject parameters = component.getAsJsonObject("Parameters");
        assertEquals(JsonParser.parseString("[0,2]"), parameters.getAsJsonObject("SwoopAltitudeRange").get("Value"));
        assertEquals(JsonParser.parseString("[6,6]"), parameters.getAsJsonObject("SwoopApproachTimeout").get("Value"));
        assertTrue(parameters.has("HardLeashDistance"));
        assertTrue(parameters.has("DefendFollowMacroElement"));
        assertFalse(component.toString().contains("\"Type\":\"Random\""));
        JsonObject content = component.getAsJsonObject("Content");
        JsonArray rootInstructions = content.getAsJsonArray("Instructions");

        assertHardLeashReleaseAndReset(rootInstructions);
        assertOwnerTargetRejection(rootInstructions);
        assertFlyingFollowFallback(rootInstructions);
        assertTrue(anyObject(component, object -> "Component_Tamework_Sensor_Defend_Attacked_MasterTarget"
                .equals(string(object, "Reference"))));
        assertTrue(anyObject(component, object -> "Component_Tamework_Sensor_Defend_Hostile_To_MasterTarget"
                .equals(string(object, "Reference"))));
        assertTrue(anyObject(component, object -> ".Swoop".equals(string(object, "State"))));
        assertTrue(anyObject(component, object -> ".Recovery".equals(string(object, "State"))));
        assertTrue(anyObject(component, object -> "TameworkFlyingOrbit".equals(string(object, "Type"))));
    }

    private static void assertHardLeashReleaseAndReset(JsonArray rootInstructions) {
        JsonObject hardLeash = directInstruction(rootInstructions, instruction -> instruction.has("Sensor")
                && "Not".equals(string(instruction.getAsJsonObject("Sensor"), "Type"))
                && hasPlayerRange(instruction.getAsJsonObject("Sensor"), "HardLeashDistance"));
        assertReleaseAndDefaultReset(hardLeash.getAsJsonArray("Actions"));
    }

    private static void assertOwnerTargetRejection(JsonArray rootInstructions) {
        JsonObject ownerTarget = directInstruction(rootInstructions, instruction -> instruction.has("Sensor")
                && "LockedTarget".equals(string(instruction.getAsJsonObject("Sensor"), "TargetSlot"))
                && instruction.getAsJsonObject("Sensor").has("Filters")
                && instruction.getAsJsonObject("Sensor").getAsJsonArray("Filters").asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .anyMatch(filter -> "TameworkIsOwner".equals(string(filter, "Type"))));
        assertReleaseAndDefaultReset(ownerTarget.getAsJsonArray("Actions"));
    }

    private static void assertFlyingFollowFallback(JsonArray rootInstructions) {
        JsonObject fallback = directInstruction(rootInstructions, instruction -> instruction.has("Sensor")
                && hasStateSensor(instruction.getAsJsonObject("Sensor"), ".Default")
                && hasMissingLockedTargetSensor(instruction.getAsJsonObject("Sensor"))
                && instruction.has("Instructions"));
        JsonObject followReference = directInstruction(fallback.getAsJsonArray("Instructions"), instruction ->
                computedValue(instruction, "Reference", "DefendFollowMacroElement"));
        assertEquals(JsonParser.parseString("{\"Compute\":\"DefendFollowMacroElement\"}"),
                followReference.get("Reference"));
    }

    private static void assertLostTargetReleaseAndReset(JsonArray combatInstructions) {
        JsonObject lostTarget = directInstruction(combatInstructions, instruction -> instruction.has("Sensor")
                && "Not".equals(string(instruction.getAsJsonObject("Sensor"), "Type"))
                && hasLostTargetDetection(instruction.getAsJsonObject("Sensor")));
        assertReleaseAndDefaultReset(lostTarget.getAsJsonArray("Actions"));
    }

    private static JsonObject directInstruction(
            JsonArray instructions, java.util.function.Predicate<JsonObject> predicate) {
        List<JsonObject> matches = instructions.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(predicate)
                .toList();
        assertEquals(1, matches.size(), "expected exactly one matching direct instruction");
        return matches.get(0);
    }

    private static int countDirectInstructions(
            JsonArray instructions, java.util.function.Predicate<JsonObject> predicate) {
        return (int) instructions.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(predicate)
                .count();
    }

    private static JsonObject directAction(JsonArray actions, String type) {
        List<JsonObject> matches = actions.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(action -> type.equals(string(action, "Type")))
                .toList();
        assertEquals(1, matches.size(), "expected exactly one " + type + " action");
        return matches.get(0);
    }

    private static boolean computedValue(JsonObject object, String property, String parameter) {
        return object.has(property)
                && JsonParser.parseString("{\"Compute\":\"" + parameter + "\"}").equals(object.get(property));
    }

    private static boolean hasPlayerRange(JsonObject notSensor, String parameter) {
        return notSensor.has("Sensor")
                && "Player".equals(string(notSensor.getAsJsonObject("Sensor"), "Type"))
                && computedValue(notSensor.getAsJsonObject("Sensor"), "Range", parameter);
    }

    private static boolean hasMissingLockedTargetSensor(JsonObject sensor) {
        return anyObject(sensor, candidate -> "Not".equals(string(candidate, "Type"))
                && candidate.has("Sensor")
                && "LockedTarget".equals(string(candidate.getAsJsonObject("Sensor"), "TargetSlot")));
    }

    private static boolean hasLostTargetDetection(JsonObject notSensor) {
        return notSensor.has("Sensor")
                && "Component_Sensor_Lost_Target_Detection".equals(
                        string(notSensor.getAsJsonObject("Sensor"), "Reference"));
    }

    private static void assertReleaseAndDefaultReset(JsonArray actions) {
        assertEquals(2, actions.size());
        assertEquals("ReleaseTarget", string(actions.get(0).getAsJsonObject(), "Type"));
        assertEquals("LockedTarget", string(actions.get(0).getAsJsonObject(), "TargetSlot"));
        assertEquals("State", string(actions.get(1).getAsJsonObject(), "Type"));
        assertEquals(".Default", string(actions.get(1).getAsJsonObject(), "State"));
    }

    private static void assertFullDragonDefendTuning(JsonObject defend, boolean airborne) {
        JsonObject branch = directModeBranches(defend).stream()
                .filter(candidate -> isModeControllerPair(candidate, airborne, airborne ? "Fly" : "Walk"))
                .findFirst().orElseThrow();
        JsonObject modify = objects(branch,
                        object -> "Component_Tamework_Instruction_Defend".equals(string(object, "Reference")))
                .stream().findFirst().orElseThrow().getAsJsonObject("Modify");
        for (String computed : List.of(
                "HardLeashDistance", "AlertedRange", "ViewSector", "HearingRange", "AbsoluteDetectionRange",
                "ViewRange", "Attack", "AttackDistance", "AttackPauseRange", "CombatAttackPreDelay",
                "CombatAttackPostDelay", "CombatBackOffAfterAttack", "CombatBackOffDistanceRange",
                "CombatBackOffDurationRange", "BlockAbility", "BlockProbability", "CombatStrafingDurationRange",
                "CombatStrafingFrequencyRange", "CombatBehaviorDistance", "CombatMovingRelativeSpeed",
                "CombatStrafeWeight", "CombatDirectWeight", "CombatAlwaysMovingWeight", "CombatRelativeTurnSpeed",
                "ChaseRelativeSpeed", "AdditionalCombatBehaviorMacroElement")) {
            assertEquals(JsonParser.parseString("{\"Compute\":\"" + computed + "\"}"), modify.get(computed));
        }
    }

    private static void assertNoModeSelectionMutation(JsonObject branch) {
        Set<String> forbidden = Set.of(
                "State", "ParentState", "SetState", "SetParentState", "SetTarget", "ReleaseTarget", "ClearTarget");
        assertEquals(0, countObjects(branch, object -> {
            String type = string(object, "Type");
            return type != null && forbidden.contains(type);
        }),
                "AirborneMode selection must not change command state, parent state, or target slots");
    }

    private static boolean hasTalentGate(JsonObject object) {
        return anyObject(object, candidate -> "TameworkHasTalent".equals(string(candidate, "Type")));
    }

    private static boolean hasProjectileAction(JsonObject object) {
        return anyObject(object, candidate -> "Attack".equals(string(candidate, "Type"))
                || "ApplyEntityEffect".equals(string(candidate, "Type")));
    }

    private static boolean hasStateSensor(JsonObject object, String state) {
        return anyObject(object, candidate -> "State".equals(string(candidate, "Type"))
                && state.equals(string(candidate, "State")));
    }

    private static boolean hasTargetSensor(JsonObject object, String targetSlot) {
        return anyObject(object, candidate -> "Target".equals(string(candidate, "Type"))
                && targetSlot.equals(string(candidate, "TargetSlot")));
    }

    private static List<JsonObject> objects(JsonElement node, java.util.function.Predicate<JsonObject> predicate) {
        java.util.ArrayList<JsonObject> matches = new java.util.ArrayList<>();
        collectObjects(node, predicate, matches);
        return matches;
    }

    private static void collectObjects(
            JsonElement node, java.util.function.Predicate<JsonObject> predicate, List<JsonObject> matches) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (predicate.test(object)) matches.add(object);
            for (JsonElement child : object.asMap().values()) collectObjects(child, predicate, matches);
        } else if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) collectObjects(child, predicate, matches);
        }
    }

    private static void assertNoTransitionScopeViolations(JsonElement node) {
        Set<String> prohibited = Set.of("State", "ParentState", "SetTarget", "ReleaseTarget", "ClearTarget");
        assertEquals(0, countObjects(node, object -> {
            String type = string(object, "Type");
            return type != null && prohibited.contains(type);
        }));
        assertEquals(0, countObjects(node, object -> "TameworkSetFlyingCompanionMode".equals(string(object, "Type"))
                && !"Follow".equals(string(object, "Mode"))),
                "the legacy migration may only neutralize, never command, Tamework's flying controller");
    }

    private static boolean actionExists(JsonArray actions, String type, String name, boolean setTo) {
        return actions.asList().stream().map(JsonElement::getAsJsonObject)
                .anyMatch(action -> type.equals(string(action, "Type")) && name.equals(string(action, "Name"))
                        && action.has("SetTo") && setTo == action.get("SetTo").getAsBoolean());
    }

    private static boolean actionTypeExists(JsonArray actions, String type) {
        return actions.asList().stream().map(JsonElement::getAsJsonObject)
                .anyMatch(action -> type.equals(string(action, "Type")));
    }

    private static boolean actionExistsInParent(JsonElement root, JsonObject sensor, String type, String name, boolean setTo) {
        return anyObject(root, object -> object.has("Sensor") && object.get("Sensor").equals(sensor)
                && object.has("Actions") && actionExists(object.getAsJsonArray("Actions"), type, name, setTo));
    }

    private static boolean anyObject(JsonElement node, java.util.function.Predicate<JsonObject> predicate) {
        return countObjects(node, predicate) > 0;
    }

    private static int countObjects(JsonElement node, java.util.function.Predicate<JsonObject> predicate) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            int count = predicate.test(object) ? 1 : 0;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                count += countObjects(entry.getValue(), predicate);
            }
            return count;
        }
        if (node.isJsonArray()) {
            int count = 0;
            for (JsonElement element : node.getAsJsonArray()) {
                count += countObjects(element, predicate);
            }
            return count;
        }
        return 0;
    }

    private static String string(JsonObject object, String property) {
        return object.has(property) && object.get(property).isJsonPrimitive()
                ? object.get(property).getAsString()
                : null;
    }

    private static void assertDefendSteps(JsonArray steps) {
        assertEquals(3, steps.size());
        assertEquals(JsonParser.parseString("""
                { "Type": "ClearTarget", "TargetSlot": "LockedTarget" }
                """), steps.get(0));
        assertEquals(JsonParser.parseString("""
                { "Type": "SetTarget", "TargetSlot": "MasterTarget", "Source": "OwnerPlayer" }
                """), steps.get(1));
        assertEquals(JsonParser.parseString("""
                { "Type": "SetState", "State": "Defend" }
                """), steps.get(2));
    }

    private static void assertAttackTargetSteps(JsonArray steps) {
        assertEquals(2, steps.size());
        assertEquals(JsonParser.parseString("""
                {
                  "Type": "SetTarget",
                  "TargetSlot": "LockedTarget",
                  "Source": "CrosshairTarget",
                  "FailurePolicy": "AbortCommandForNpc"
                }
                """), steps.get(0));
        assertEquals(JsonParser.parseString("""
                { "Type": "SetState", "State": "Defend" }
                """), steps.get(1));
        assertFalse(steps.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(step -> "SetTarget".equals(step.get("Type").getAsString())
                        && "MasterTarget".equals(step.get("TargetSlot").getAsString())));
    }

    private static void assertToggleSteps(JsonArray steps) {
        assertEquals(1, steps.size());
        assertEquals(JsonParser.parseString("""
                { "Type": "TriggerHook", "HookId": "HyDragon.Command.ToggleAirborneMode" }
                """), steps.get(0));
    }

    private static void assertIdenticalHornCommandCatalogs() throws IOException {
        Map<String, Set<String>> languageKeysByLocale = new LinkedHashMap<>();
        for (String locale : LOCALES) {
            Set<String> keys = Files.readAllLines(ROOT.resolve("Server/Languages").resolve(locale).resolve("server.lang"))
                    .stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                    .map(line -> line.substring(0, line.indexOf('=')))
                    .filter(HORN_COMMAND_LANGUAGE_KEYS::contains)
                    .collect(Collectors.toSet());
            assertEquals(HORN_COMMAND_LANGUAGE_KEYS, keys, locale);
            languageKeysByLocale.put(locale, keys);
        }
        assertEquals(languageKeysByLocale.get("en-US"), Set.copyOf(languageKeysByLocale.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet())));
    }

    private static Set<String> commandIds(JsonArray commands) {
        return commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(command -> command.get("Id").getAsString())
                .collect(Collectors.toSet());
    }

    private static JsonObject command(JsonArray commands, String id) {
        return commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(command -> id.equals(command.get("Id").getAsString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing command " + id));
    }

    private static void assertHornCommandFeedback(JsonArray commands) {
        for (Map.Entry<String, String> expected : HORN_COMMAND_SOUNDS.entrySet()) {
            JsonObject feedback = command(commands, expected.getKey()).getAsJsonObject("Feedback");
            assertEquals(expected.getValue(), feedback.get("SoundEvent").getAsString(), expected.getKey());
            assertFalse(feedback.has("ParticleSystem"), expected.getKey() + " must not spawn particles");
        }
    }

    private static void assertHornSoundAssets() throws IOException {
        for (String soundId : HORN_COMMAND_SOUNDS.values()) {
            String suffix = soundId.substring("SFX_HyDragon_Dragon_Flute_".length());
            String relativeAudioPath = "Sounds/Items/HyDragon/DragonFlute/HyDragon_Dragon_Flute_" + suffix + ".ogg";
            String eventRelativePath = "Server/Audio/SoundEvents/SFX/Items/HyDragon/DragonFlute/" + soundId + ".json";
            JsonObject event = readJson(eventRelativePath);
            assertEquals(relativeAudioPath,
                    event.getAsJsonArray("Layers").get(0).getAsJsonObject()
                            .getAsJsonArray("Files").get(0).getAsString(),
                    soundId);
            assertTrue(Files.isRegularFile(ROOT.resolve("Common").resolve(relativeAudioPath)), soundId);
        }
    }

    private static List<String> roleIds(JsonObject config) {
        return config.getAsJsonArray("RoleIds").asList().stream()
                .map(JsonElement::getAsString)
                .toList();
    }

    private static double number(JsonObject object, String dottedPath) {
        JsonElement current = object;
        for (String segment : dottedPath.split("\\.")) current = current.getAsJsonObject().get(segment);
        return current.getAsDouble();
    }

    private static void assertFlightToggle(JsonObject config) {
        JsonObject toggle = config.getAsJsonObject("Command").getAsJsonObject("FlightToggle");
        assertEquals(Set.of("Enabled", "HookId"), toggle.keySet());
        assertTrue(toggle.get("Enabled").getAsBoolean());
        assertEquals("HyDragon.Command.ToggleAirborneMode", toggle.get("HookId").getAsString());
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relativePath))).getAsJsonObject();
    }
}
