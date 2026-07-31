package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

final class NordicDrakeTamedCombatAssetTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));
    private static final Path TEMPLATE = ROOT.resolve(
            "Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json");
    private static final Path TAMED_NORDIC = ROOT.resolve(
            "Server/NPC/Roles/Creature/HyDragon/NordicDrake/Tamed_NordicDrake.json");
    private static final Path COMPONENT = ROOT.resolve("Server/NPC/Roles/Creature/HyDragon/Components/"
            + "Component_HyDragon_Instruction_NordicDrake_Tamed_Combat.json");
    private static final Path ROLES = ROOT.resolve("Server/NPC/Roles/Creature/HyDragon");
    private static final Set<String> FORBIDDEN_IMPLICIT_COMBAT_TYPES = Set.of(
            "SetTarget", "LockOnTarget", "LockOnInteractionTarget", "SelectTarget", "SelectBasicAttackTarget",
            "CombatActionEvaluator", "SetMarkedTarget", "CombatAbility", "HasHostileTargetMemory",
            "AddToHostileTargetMemory");

    @Test
    void nordicCombatIsAnExclusiveLockedTargetTakeoverWithBoundedSafety() throws IOException {
        JsonObject template = readJson(TEMPLATE);
        JsonObject nordic = readJson(TAMED_NORDIC);
        JsonObject component = readJson(COMPONENT);

        assertEquals(false, template.getAsJsonObject("Parameters")
                .getAsJsonObject("UseNordicDrakeTamedCombat").get("Value").getAsBoolean());
        assertTrue(nordic.getAsJsonObject("Modify")
                .get("UseNordicDrakeTamedCombat").getAsBoolean());
        assertNordicTakeoverPrecedesGenericDefend(defendInstruction(template));
        assertOnlyNordicRoleOptsIn();
        assertHardLeashRelease(component);
        assertOwnerAndFriendlyRelease(component);
        assertLostTargetRelease(component);
        assertNoImplicitTargetSelection(component);
        assertTaskOneSafetyShellIsPreserved(component);
    }

    @Test
    void nordicGroundCombatConsumesLockedTargetWithGroundedDirectAttacks() throws IOException {
        JsonObject component = readJson(COMPONENT);

        assertGroundedParameters(component);
        JsonObject groundCombat = groundedCombat(component);
        assertGroundedEntry(component);
        assertGroundedMovement(groundCombat);
        assertGroundedAttackChoice(groundCombat);
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<JsonObject> objects(JsonElement node, Predicate<JsonObject> predicate) {
        List<JsonObject> matches = new ArrayList<>();
        collectObjects(node, predicate, matches);
        return matches;
    }

    private static void collectObjects(JsonElement node, Predicate<JsonObject> predicate, List<JsonObject> matches) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (predicate.test(object)) matches.add(object);
            for (JsonElement child : object.asMap().values()) collectObjects(child, predicate, matches);
        } else if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) collectObjects(child, predicate, matches);
        }
    }

    private static String string(JsonObject object, String member) {
        return object.has(member) && object.get(member).isJsonPrimitive()
                ? object.get(member).getAsString() : null;
    }

    private static JsonObject defendInstruction(JsonObject template) {
        return objects(template, object -> object.has("Sensor") && object.has("Instructions")
                        && "State".equals(string(object.getAsJsonObject("Sensor"), "Type"))
                        && "Defend".equals(string(object.getAsJsonObject("Sensor"), "State")))
                .stream().findFirst().orElseThrow(() -> new AssertionError("missing outer Defend instruction"));
    }

    private static void assertNordicTakeoverPrecedesGenericDefend(JsonObject defend) {
        JsonArray children = defend.getAsJsonArray("Instructions");
        assertEquals(3, children.size(), "Defend must retain the Nordic takeover and both generic fallbacks");
        JsonObject nordic = children.get(0).getAsJsonObject();
        assertFalse(nordic.has("Continue"), "Nordic takeover must block generic Defend fallbacks");
        assertEquals("UseNordicDrakeTamedCombat", string(nordic.getAsJsonObject("Enabled"), "Compute"));
        assertEquals("Target", string(nordic.getAsJsonObject("Sensor"), "Type"));
        assertEquals("LockedTarget", string(nordic.getAsJsonObject("Sensor"), "TargetSlot"));
        assertTrue(objects(nordic, object -> "Component_HyDragon_Instruction_NordicDrake_Tamed_Combat"
                .equals(string(object, "Reference"))).size() == 1);
        assertTrue(children.asList().subList(1, children.size()).stream().map(JsonElement::getAsJsonObject)
                .allMatch(NordicDrakeTamedCombatAssetTest::isGenericDefendFallback));
    }

    private static boolean isGenericDefendFallback(JsonObject branch) {
        return objects(branch, object -> "Component_Tamework_Instruction_Defend".equals(string(object, "Reference")))
                .size() == 1;
    }

    private static void assertOnlyNordicRoleOptsIn() throws IOException {
        try (var paths = Files.walk(ROLES)) {
            List<Path> optIns = paths.filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> {
                        try {
                            JsonObject json = readJson(path);
                            return json.has("Modify") && json.getAsJsonObject("Modify").has("UseNordicDrakeTamedCombat")
                                    && json.getAsJsonObject("Modify").get("UseNordicDrakeTamedCombat").getAsBoolean();
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }).toList();
            assertEquals(List.of(TAMED_NORDIC), optIns);
        }
    }

    private static void assertHardLeashRelease(JsonObject component) {
        JsonObject branch = directChildren(component).get(0);
        JsonObject not = branch.getAsJsonObject("Sensor");
        assertEquals("Not", string(not, "Type"));
        JsonObject player = not.getAsJsonObject("Sensor");
        assertEquals("Player", string(player, "Type"));
        assertEquals("HardLeashDistance", string(player.getAsJsonObject("Range"), "Compute"));
        assertReleaseAndReset(branch);
    }

    private static void assertOwnerAndFriendlyRelease(JsonObject component) {
        List<JsonObject> children = directChildren(component);
        JsonObject owner = children.get(1);
        assertEquals("Target", string(owner.getAsJsonObject("Sensor"), "Type"));
        assertEquals("LockedTarget", string(owner.getAsJsonObject("Sensor"), "TargetSlot"));
        assertTrue(objects(owner.getAsJsonObject("Sensor"), object -> "TameworkIsOwner".equals(string(object, "Type")))
                .size() == 1);
        assertReleaseAndReset(owner);

        JsonObject friendly = children.get(2);
        JsonObject filter = objects(friendly.getAsJsonObject("Sensor"), object ->
                "TameworkAttitudeFromTargetSlot".equals(string(object, "Type"))).stream().findFirst()
                .orElseThrow(() -> new AssertionError("missing friendly target filter"));
        assertEquals("LockedTarget", string(friendly.getAsJsonObject("Sensor"), "TargetSlot"));
        assertEquals("MasterTargetSlot", string(filter.getAsJsonObject("SourceTargetSlot"), "Compute"));
        assertEquals("Friendly", filter.getAsJsonArray("Attitudes").get(0).getAsString());
        assertFalse(filter.get("UseSelfWhenSourceMissing").getAsBoolean());
        assertReleaseAndReset(friendly);
    }

    private static void assertLostTargetRelease(JsonObject component) {
        JsonObject branch = directChildren(component).get(3);
        JsonObject not = branch.getAsJsonObject("Sensor");
        assertEquals("Not", string(not, "Type"));
        JsonObject lost = not.getAsJsonObject("Sensor");
        assertEquals("Component_Sensor_Lost_Target_Detection", string(lost, "Reference"));
        JsonObject modify = lost.getAsJsonObject("Modify");
        for (String parameter : List.of("ViewRange", "ViewSector", "HearingRange", "AbsoluteDetectionRange")) {
            assertEquals(parameter, string(modify.getAsJsonObject(parameter), "Compute"));
        }
        assertEquals("LockedTarget", string(modify, "TargetSlot"));
        assertReleaseAndReset(branch);
    }

    private static void assertNoImplicitTargetSelection(JsonObject component) {
        assertEquals(0, objects(component, object -> {
            String type = string(object, "Type");
            return type != null && FORBIDDEN_IMPLICIT_COMBAT_TYPES.contains(type);
        }).size(),
                "Task 1 must only consume the outer Defend LockedTarget, never select or mutate a target");
        assertEquals(0, objects(component, object -> object.has("LockOnTarget")).size(),
                "Task 1 must not enable the established LockOnTarget selector flag");
        for (JsonObject sensor : objects(component, object -> "Target".equals(string(object, "Type")))) {
            assertEquals("LockedTarget", string(sensor, "TargetSlot"),
                    "every Task 1 entity-target sensor must explicitly observe LockedTarget");
        }
    }

    private static void assertTaskOneSafetyShellIsPreserved(JsonObject component) {
        List<JsonObject> children = directChildren(component);
        assertTrue(children.size() >= 4, "the four Task 1 safety exits must remain first");
        assertHardLeashRelease(component);
        assertOwnerAndFriendlyRelease(component);
        assertLostTargetRelease(component);
    }

    private static void assertGroundedParameters(JsonObject component) {
        JsonObject parameters = component.getAsJsonObject("Parameters");
        assertParameter(parameters, "GroundBasicAttack", "Root_NPC_NordicDrake_Attack");
        assertParameter(parameters, "GroundBasicAttackDistance", 5.25);
        assertParameter(parameters, "GroundBasicCooldownRange", List.of(1.5, 2.5));
        assertParameter(parameters, "GroundBiteAttack", "Root_NPC_NordicDrake_Bite");
        assertParameter(parameters, "GroundBiteDistance", 4.5);
        assertParameter(parameters, "GroundBiteCooldownRange", List.of(10, 20));
        assertParameter(parameters, "GroundBiteWeight", 3);
        assertParameter(parameters, "GroundBreathAttack", "Root_NPC_NordicDrake_Flame_Breath");
        assertParameter(parameters, "GroundBreathDistance", 9);
        assertParameter(parameters, "GroundBreathCooldownRange", List.of(10, 20));
        assertParameter(parameters, "GroundBreathWeight", 4);
        assertParameter(parameters, "DesiredAttackDistanceRange", List.of(0.5, 5));
        assertParameter(parameters, "CombatBehaviorDistance", 18);
        assertParameter(parameters, "CombatMovingRelativeSpeed", 0.6);
        assertParameter(parameters, "CombatBackwardsRelativeSpeed", 0.4);
        assertParameter(parameters, "CombatRelativeTurnSpeed", 1.5);
    }

    private static void assertGroundedEntry(JsonObject component) {
        JsonObject entry = objects(component, object -> objects(object, child ->
                ".GroundCombat".equals(string(child, "State"))).size() == 1
                && !".GroundCombat".equals(string(object, "State"))).stream()
                .filter(object -> object.has("Sensor") && object.has("Actions"))
                .findFirst().orElseThrow(() -> new AssertionError("missing grounded combat entry"));
        assertGroundedContext(entry.getAsJsonObject("Sensor"));
        assertEquals(List.of("State"), actionTypes(entry));
        assertEquals(".GroundCombat", string(entry.getAsJsonArray("Actions").get(0).getAsJsonObject(), "State"));
    }

    private static JsonObject groundedCombat(JsonObject component) {
        return objects(component, object -> object.has("Sensor") && object.has("Instructions")
                && "State".equals(string(object.getAsJsonObject("Sensor"), "Type"))
                && ".GroundCombat".equals(string(object.getAsJsonObject("Sensor"), "State"))).stream().findFirst()
                .orElseThrow(() -> new AssertionError("missing .GroundCombat branch"));
    }

    private static void assertGroundedMovement(JsonObject groundCombat) {
        JsonObject chase = objects(groundCombat, object -> object.has("Sensor") && object.has("Instructions")
                && object.getAsJsonArray("Instructions").asList().stream().map(JsonElement::getAsJsonObject)
                .anyMatch(child -> "Component_Tamework_Instruction_Intelligent_Chase".equals(string(child, "Reference"))))
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("missing grounded chase"));
        assertGroundedContext(chase.getAsJsonObject("Sensor"));
        JsonObject chaseModify = objects(chase, object -> "Component_Tamework_Instruction_Intelligent_Chase"
                .equals(string(object, "Reference"))).get(0).getAsJsonObject("Modify");
        assertEquals(Set.of("ViewRange", "HearingRange", "RelativeSpeed", "SlowDownDistance", "StopDistance"),
                chaseModify.keySet());

        JsonObject maintain = objects(groundCombat, object ->
                "MaintainDistance".equals(string(object, "Type"))).stream().findFirst()
                .orElseThrow(() -> new AssertionError("missing grounded MaintainDistance"));
        assertEquals("DesiredAttackDistanceRange", string(maintain.getAsJsonObject("DesiredDistanceRange"), "Compute"));
        assertEquals("CombatMovingRelativeSpeed", string(maintain.getAsJsonObject("RelativeForwardsSpeed"), "Compute"));
        assertEquals("CombatBackwardsRelativeSpeed", string(maintain.getAsJsonObject("RelativeBackwardsSpeed"), "Compute"));
    }

    private static void assertGroundedAttackChoice(JsonObject groundCombat) {
        List<JsonObject> attacks = objects(groundCombat, object -> object.has("Actions")
                && actionTypes(object).contains("Attack"));
        assertTrue(attacks.size() >= 5,
                "ground combat must provide both-special random choices, direct one-special choices, and basic fallback");
        JsonObject basic = attackBranch(attacks, "GroundBasicAttack");
        JsonObject bite = attackBranch(attacks, "GroundBiteAttack");
        JsonObject breath = attackBranch(attacks, "GroundBreathAttack");
        assertGroundedAttackBranch(basic, "NordicDrake_Ground_Basic", false);
        assertGroundedAttackBranch(bite, "NordicDrake_Ground_Bite", true);
        assertGroundedAttackBranch(breath, "NordicDrake_Ground_Breath", true);
        assertEquals(List.of(0.1, 0.2), numbers(action(basic, "Attack").getAsJsonArray("AimingTimeRange")));

        JsonObject random = objects(groundCombat, object -> "Random".equals(string(object, "Type"))).stream()
                .findFirst().orElseThrow(() -> new AssertionError("missing special attack random selector"));
        List<JsonObject> selected = random.getAsJsonArray("Instructions").asList().stream()
                .map(JsonElement::getAsJsonObject).toList();
        assertEquals(2, selected.size());
        assertEquals(3, selected.stream().filter(branch -> containsAttack(branch, "GroundBiteAttack"))
                .findFirst().orElseThrow().get("Weight").getAsInt());
        assertEquals(4, selected.stream().filter(branch -> containsAttack(branch, "GroundBreathAttack"))
                .findFirst().orElseThrow().get("Weight").getAsInt());
        assertEquals(2, objects(basic.getAsJsonObject("Sensor"), object -> "Not".equals(string(object, "Type"))
                && objects(object, child -> "Flag".equals(string(child, "Type"))
                && ("NordicDrake_Ground_Bite_Active".equals(string(child, "Name"))
                || "NordicDrake_Ground_Breath_Active".equals(string(child, "Name")))).size() == 1).size(),
                "basic fallback must be gated out while either selected special is executing");
    }

    private static void assertGroundedAttackBranch(JsonObject branch, String timer, boolean blocking) {
        assertGroundedContext(branch.getAsJsonObject("Sensor"));
        assertTrue(objects(branch.getAsJsonObject("Sensor"), object -> "State".equals(string(object, "Type"))
                && ".GroundCombat".equals(string(object, "State"))).size() == 1);
        assertEquals(1, objects(branch.getAsJsonObject("Sensor"), object -> "Timer".equals(string(object, "Type"))
                && timer.equals(string(object, "Name")) && "Stopped".equals(string(object, "State"))).size());
        assertEquals(blocking, branch.has("ActionsBlocking") && branch.get("ActionsBlocking").getAsBoolean());
        assertEquals(timer, actionTypesAfterAttack(branch, "TimerStart").get(0).getAsJsonObject().get("Name").getAsString());
        assertEquals(timer, actionTypesAfterAttack(branch, "TimerRestart").get(0).getAsJsonObject().get("Name").getAsString());
        JsonObject aim = branch.getAsJsonObject("HeadMotion");
        assertEquals("Aim", string(aim, "Type"));
        assertEquals(0, aim.get("Spread").getAsInt());
        assertEquals(1, aim.get("HitProbability").getAsInt());
        assertTrue(aim.get("Deflection").getAsBoolean());
        assertEquals("CombatRelativeTurnSpeed", string(aim.getAsJsonObject("RelativeTurnSpeed"), "Compute"));
    }

    private static JsonObject attackBranch(List<JsonObject> branches, String attack) {
        return branches.stream().filter(branch -> containsAttack(branch, attack)).findFirst()
                .orElseThrow(() -> new AssertionError("missing " + attack + " branch"));
    }

    private static boolean containsAttack(JsonObject branch, String attack) {
        return objects(branch, object -> "Attack".equals(string(object, "Type"))
                && attack.equals(string(object.getAsJsonObject("Attack"), "Compute"))).size() == 1;
    }

    private static JsonObject action(JsonObject branch, String type) {
        return branch.getAsJsonArray("Actions").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(candidate -> type.equals(string(candidate, "Type"))).findFirst().orElseThrow();
    }

    private static List<String> actionTypes(JsonObject branch) {
        return branch.getAsJsonArray("Actions").asList().stream().map(JsonElement::getAsJsonObject)
                .map(action -> string(action, "Type")).toList();
    }

    private static List<String> actionTypesAfterAttack(JsonObject branch) {
        List<String> actions = actionTypes(branch);
        return actions.subList(actions.indexOf("Attack") + 1, actions.size());
    }

    private static List<JsonElement> actionTypesAfterAttack(JsonObject branch, String type) {
        List<JsonElement> actions = branch.getAsJsonArray("Actions").asList();
        int attackIndex = actionTypes(branch).indexOf("Attack");
        return actions.subList(attackIndex + 1, actions.size()).stream()
                .filter(action -> type.equals(string(action.getAsJsonObject(), "Type"))).toList();
    }

    private static void assertGroundedContext(JsonObject sensor) {
        List<JsonObject> sensors = objects(sensor, object -> true);
        assertTrue(sensors.stream().anyMatch(object -> "Target".equals(string(object, "Type"))
                && "LockedTarget".equals(string(object, "TargetSlot"))));
        assertTrue(sensors.stream().anyMatch(object -> "Flag".equals(string(object, "Type"))
                && "AirborneMode".equals(string(object, "Name")) && object.has("Set")
                && !object.get("Set").getAsBoolean()));
        assertTrue(sensors.stream().anyMatch(object -> "MotionController".equals(string(object, "Type"))
                && "Walk".equals(string(object, "MotionController"))));
    }

    private static void assertParameter(JsonObject parameters, String name, Object expected) {
        assertTrue(parameters.has(name), "missing grounded parameter " + name);
        JsonElement value = parameters.getAsJsonObject(name).get("Value");
        if (expected instanceof List<?> expectedList) {
            assertEquals(expectedList.stream().map(number -> ((Number) number).doubleValue()).toList(),
                    numbers(value.getAsJsonArray()));
        } else if (expected instanceof Number expectedNumber) {
            assertEquals(expectedNumber.doubleValue(), value.getAsDouble());
        } else {
            assertEquals(expected, value.getAsString());
        }
    }

    private static List<Double> numbers(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsDouble).toList();
    }

    private static List<JsonObject> directChildren(JsonObject component) {
        JsonObject content = component.getAsJsonObject("Content");
        assertTrue(content.get("Continue").getAsBoolean());
        assertEquals("Any", string(content.getAsJsonObject("Sensor"), "Type"));
        return content.getAsJsonArray("Instructions").asList().stream().map(JsonElement::getAsJsonObject).toList();
    }

    private static void assertReleaseAndReset(JsonObject branch) {
        List<String> actions = branch.getAsJsonArray("Actions").asList().stream()
                .map(JsonElement::getAsJsonObject).map(action -> string(action, "Type")).toList();
        assertEquals(List.of("ReleaseTarget", "ResetInstructions"), actions);
        assertEquals("LockedTarget", string(branch.getAsJsonArray("Actions").get(0).getAsJsonObject(), "TargetSlot"));
    }
}
