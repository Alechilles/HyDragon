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
    private static final Set<String> HORN_COMMAND_LANGUAGE_KEYS = Set.of(
            "hydragon.commands.defend.name",
            "hydragon.commands.defend.hud",
            "hydragon.commands.toggleAirborneMode.name",
            "hydragon.commands.toggleAirborneMode.hud");
    private static final List<String> LOCALES = List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR");

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
    void tamedDragonTemplatesUseTheSharedNativeAirborneModeTransition() throws IOException {
        JsonObject miniwyvern = readJson("Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json");
        JsonObject fullDragon = readJson("Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json");
        JsonObject transition = readJson("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Airborne_Mode_Transition.json");

        assertGlobalContinuingReference(miniwyvern);
        assertGlobalContinuingReference(fullDragon);

        assertEquals("Component", transition.get("Type").getAsString());
        assertEquals("Instruction", transition.get("Class").getAsString());
        JsonObject content = transition.getAsJsonObject("Content");
        assertTrue(hasOnceSetFlag(content, "AirborneMode", false),
                "newly spawned roles must reset AirborneMode to false exactly once");
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
    void miniwyvernSelectsLocomotionInsideEachCommandWithoutMutatingCommandStateOrTargets() throws IOException {
        JsonObject miniwyvern = readJson("Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json");

        JsonObject idle = stateBehavior(miniwyvern, "Idle");
        assertModeBranch(idle, false, "Walk", "WanderInCircle", null);
        assertModeBranch(idle, true, "Fly", "WanderInCircle", null);

        JsonObject follow = stateBehavior(miniwyvern, "Follow");
        assertModeBranch(follow, false, "Walk", null, "Component_Tamework_Instruction_Follow_Advanced");
        assertModeBranch(follow, true, "Fly", null, "Component_Tamework_Instruction_Follow_Flying");

        JsonObject defend = stateBehavior(miniwyvern, "Defend");
        assertModeBranch(defend, false, "Walk", null, "Component_Tamework_Instruction_Defend");
        assertModeBranch(defend, true, "Fly", null, "Component_Tamework_Instruction_Defend");
        assertDefendFollowMacro(defend, false, "Component_Tamework_Instruction_Follow_Advanced");
        assertDefendFollowMacro(defend, true, "Component_Tamework_Instruction_Follow_Flying");

        JsonObject hold = stateBehavior(miniwyvern, "Hold");
        assertModeBranch(hold, false, "Walk", "Nothing", null);
        assertModeBranch(hold, true, "Fly", "Nothing", null);
        assertFalse(anyObject(hold, object -> object.has("BodyMotion")
                && "Sleep".equals(string(object.getAsJsonObject("BodyMotion"), "Type"))));

        for (JsonObject behavior : List.of(idle, follow, defend, hold)) {
            for (JsonObject branch : modeBranches(behavior)) {
                assertNoModeSelectionMutation(branch);
            }
        }
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
        JsonObject landingInstruction = content.getAsJsonArray("Instructions").get(3).getAsJsonObject();
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

    private static boolean hasOnceSetFlag(JsonElement node, String name, boolean value) {
        return anyObject(node, object -> object.has("Actions")
                && object.has("Sensor")
                && object.getAsJsonObject("Sensor").has("Once")
                && object.getAsJsonObject("Sensor").get("Once").getAsBoolean()
                && actionExists(object.getAsJsonArray("Actions"), "SetFlag", name, value));
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
        return objects(template, object -> object.has("Sensor")
                        && "State".equals(string(object.getAsJsonObject("Sensor"), "Type"))
                        && state.equals(string(object.getAsJsonObject("Sensor"), "State"))
                        && object.has("Instructions"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing behavior for state " + state));
    }

    private static void assertModeBranch(
            JsonObject behavior, boolean airborne, String controller, String bodyMotion, String reference) {
        JsonObject branch = modeBranches(behavior).stream()
                .filter(candidate -> hasAirborneMode(candidate.getAsJsonObject("Sensor"), airborne)
                        && hasMotionController(candidate.getAsJsonObject("Sensor"), controller))
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
        JsonObject branch = modeBranches(defend).stream()
                .filter(candidate -> hasAirborneMode(candidate.getAsJsonObject("Sensor"), airborne))
                .findFirst()
                .orElseThrow();
        JsonObject defendReference = objects(branch,
                        object -> "Component_Tamework_Instruction_Defend".equals(string(object, "Reference")))
                .stream().findFirst().orElseThrow();
        assertEquals(expectedMacro, string(defendReference.getAsJsonObject("Modify"), "DefendFollowMacroElement"));
    }

    private static List<JsonObject> modeBranches(JsonObject behavior) {
        return objects(behavior, object -> object.has("Sensor")
                && "And".equals(string(object.getAsJsonObject("Sensor"), "Type"))
                && (hasAirborneMode(object.getAsJsonObject("Sensor"), true)
                        || hasAirborneMode(object.getAsJsonObject("Sensor"), false)));
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

    private static void assertNoModeSelectionMutation(JsonObject branch) {
        Set<String> forbidden = Set.of("State", "SetTarget", "ReleaseTarget", "ClearTarget");
        assertEquals(0, countObjects(branch, object -> object.has("Actions")
                && object.getAsJsonArray("Actions").asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .map(action -> string(action, "Type"))
                        .anyMatch(forbidden::contains)));
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
        Set<String> prohibited = Set.of("State", "ParentState", "SetTarget", "ReleaseTarget", "ClearTarget",
                "TameworkSetFlyingCompanionMode");
        assertEquals(0, countObjects(node, object -> {
            String type = string(object, "Type");
            return type != null && prohibited.contains(type);
        }));
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

    private static JsonObject readJson(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relativePath))).getAsJsonObject();
    }
}
