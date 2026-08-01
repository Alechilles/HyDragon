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

    @Test
    void nordicAerialCombatUsesTheApprovedLockedTargetFlightCycle() throws IOException {
        JsonObject component = readJson(COMPONENT);
        JsonObject parameters = component.getAsJsonObject("Parameters");
        List<JsonObject> roots = directChildren(component);
        assertFalse(parameters.has("AirCombatWanderMinMoveDistance"));
        assertFalse(parameters.has("AirCombatWanderTestsPerTick"));
        assertParameter(parameters, "AirFireballAttack", "Root_NPC_NordicDrake_Fire_Ball");
        assertParameter(parameters, "AirFireballAttackDistance", 28);
        assertParameter(parameters, "AirFireballCooldownRange", List.of(1, 3));
        assertParameter(parameters, "AirVolleyAttack2", "Root_NPC_NordicDrake_Fire_Ball_Volley_2");
        assertParameter(parameters, "AirVolleyAttack3", "Root_NPC_NordicDrake_Fire_Ball_Volley_3");
        assertParameter(parameters, "AirVolleyAttack4", "Root_NPC_NordicDrake_Fire_Ball_Volley_4");
        assertParameter(parameters, "AirVolleyCooldownRange", List.of(12, 20));
        assertParameter(parameters, "AirVolleyWeight", 3);
        assertParameter(parameters, "AirBreathAttack", "Root_NPC_NordicDrake_Flying_Flame_Breath");
        assertParameter(parameters, "AirBreathAttackDistance", 13);
        assertParameter(parameters, "AirBreathCooldownRange", List.of(15, 30));
        assertParameter(parameters, "AirBreathWeight", 6);
        assertParameter(parameters, "AirRangedAttackViewSector", 75);
        assertParameter(parameters, "AirBreathAttackViewSector", 60);
        assertParameter(parameters, "AirCombatAltitudeRange", List.of(8, 16));
        assertParameter(parameters, "AirCombatClimbRelativeSpeed", 1);
        assertParameter(parameters, "AirCombatSinkRelativeSpeed", 0.8);
        assertParameter(parameters, "AirCombatWanderRelativeSpeed", 0.9);
        assertParameter(parameters, "AirCombatWanderRadiusRange", List.of(18, 36));
        assertParameter(parameters, "AirCombatWanderRetargetTimeRange", List.of(4, 8));
        assertParameter(parameters, "AirCombatWanderStopDistance", 5);
        assertParameter(parameters, "AirBreathAttackAltitudeRange", List.of(2, 4));
        assertParameter(parameters, "AirBreathAttackRelativeSpeed", 1.15);
        assertParameter(parameters, "AirBreathIngressTimeout", List.of(6, 7));
        assertParameter(parameters, "AirBreathPassDistance", 18);
        assertParameter(parameters, "AirBreathPassStopDistance", 2);
        assertParameter(parameters, "AirBreathPassDuration", List.of(1.85, 2));
        assertParameter(parameters, "AirCombatRecoveryDuration", List.of(3, 4));
        assertParameter(parameters, "AirCombatRecoveryRelativeSpeed", 1.1);
        assertParameter(parameters, "AirCombatRecoveryClimbRelativeSpeed", 1.5);

        assertAerialRootOrder(roots);
        assertEquals(0, objects(component, object -> "NordicDrake_Air_Reset_Pending".equals(string(object, "Name"))).size());
        JsonObject ranged = aerialState(component, ".AirRanged");
        JsonObject volley = aerialState(component, ".AirVolley");
        JsonObject ingress = aerialState(component, ".AirBreathIngress");
        JsonObject pass = aerialState(component, ".AirBreathPass");
        JsonObject recovery = aerialState(component, ".AirRecovery");
        for (JsonObject state : List.of(ranged, volley, ingress, pass, recovery)) assertAerialContext(state);
        assertOrbitMode(ranged, "WANDER_TARGET");
        assertOrbitMode(volley, "FACE_TARGET");
        assertOrbitMode(ingress, "APPROACH");
        assertOrbitMode(pass, "PASS_THROUGH_TARGET");
        assertOrbitMode(recovery, "WANDER_TARGET");
        assertAirRanged(ranged);
        assertAirVolley(volley);
        assertAirIngress(ingress);
        assertAirPass(pass);
        assertAirRecovery(recovery);
        assertEquals(0, objects(component, object -> string(object, "Type") != null
                && (string(object, "Type").contains("CombatActionEvaluator")
                || string(object, "Type").contains("HostileTargetMemory")
                || "Health".equals(string(object, "Stat")))).size());
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
        assertEquals("TimerStop", string(branch.getAsJsonArray("Actions").get(0).getAsJsonObject(), "Type"),
                "safety exits must clear aerial cooldowns before releasing a target");
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
        assertParameter(parameters, "GroundBasicAttackPauseRange", List.of(1.5, 2.5));
        assertFalse(parameters.has("GroundBasicCooldownRange"));
        assertParameter(parameters, "GroundBiteAttack", "Root_NPC_NordicDrake_Bite");
        assertParameter(parameters, "GroundBiteDistance", 4.5);
        assertParameter(parameters, "GroundBiteCooldownRange", List.of(10, 20));
        assertParameter(parameters, "GroundBiteWeight", 3);
        assertParameter(parameters, "GroundBreathAttack", "Root_NPC_NordicDrake_Flame_Breath");
        assertParameter(parameters, "GroundBreathDistance", 9);
        assertParameter(parameters, "GroundBreathCooldownRange", List.of(10, 20));
        assertParameter(parameters, "GroundBreathWeight", 4);
        assertParameter(parameters, "DesiredAttackDistanceRange", List.of(3.5, 5));
        assertParameter(parameters, "CombatBehaviorDistance", 18);
        assertParameter(parameters, "CombatMovingRelativeSpeed", 0.6);
        assertParameter(parameters, "CombatBackwardsRelativeSpeed", 0.4);
        assertParameter(parameters, "CombatRelativeTurnSpeed", 1.5);
    }

    private static void assertGroundedEntry(JsonObject component) {
        JsonObject entry = directChildren(component).stream()
                .filter(object -> object.has("Sensor") && object.has("Actions"))
                .filter(object -> actionTypes(object).equals(List.of("State")))
                .filter(object -> ".GroundCombat".equals(string(
                        object.getAsJsonArray("Actions").get(0).getAsJsonObject(), "State")))
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

    private static JsonObject aerialState(JsonObject component, String state) {
        return objects(component, object -> object.has("Sensor") && object.has("Instructions")
                && objects(object.getAsJsonObject("Sensor"), child -> "State".equals(string(child, "Type"))
                && state.equals(string(child, "State"))).size() == 1).stream().findFirst()
                .orElseThrow(() -> new AssertionError("missing " + state + " branch"));
    }

    private static void assertAerialRootOrder(List<JsonObject> roots) {
        assertEquals(15, roots.size());
        assertCancellationClearsBothResetFlags(roots.get(4));
        assertResetHandoff(roots.get(5), ".AirRecovery", "NordicDrake_Air_Reset_To_Recovery");
        assertResetHandoff(roots.get(6), ".AirRanged", "NordicDrake_Air_Reset_To_Ranged");
        assertEquals(List.of("State", "ResetInstructions"), actionTypes(roots.get(7)));
        assertEquals(".AirRanged", string(roots.get(7).getAsJsonArray("Actions").get(0).getAsJsonObject(), "State"));
        assertAerialStateOwner(roots.get(10), ".AirRanged");
        assertAerialStateOwner(roots.get(11), ".AirVolley");
        assertAerialStateOwner(roots.get(12), ".AirBreathIngress");
        assertAerialStateOwner(roots.get(13), ".AirBreathPass");
        assertAerialStateOwner(roots.get(14), ".AirRecovery");
    }

    private static void assertResetHandoff(JsonObject branch, String state, String flag) {
        assertFalse(branch.has("Continue"), "reset handoffs must preempt state owners");
        JsonObject sensor = branch.getAsJsonObject("Sensor");
        assertEquals("And", string(sensor, "Type"));
        JsonArray sensors = sensor.getAsJsonArray("Sensors");
        assertEquals(2, sensors.size());
        assertEquals("State", string(sensors.get(0).getAsJsonObject(), "Type"));
        assertEquals(state, string(sensors.get(0).getAsJsonObject(), "State"));
        assertFlagSensor(sensors.get(1).getAsJsonObject(), flag, true);
        assertEquals(List.of("SetFlag", "ResetInstructions"), actionTypes(branch));
        assertFlagAction(branch.getAsJsonArray("Actions").get(0).getAsJsonObject(), flag, false);
    }

    private static void assertAerialStateOwner(JsonObject branch, String state) {
        JsonArray sensors = branch.getAsJsonObject("Sensor").getAsJsonArray("Sensors");
        assertEquals(4, sensors.size());
        assertEquals("State", string(sensors.get(0).getAsJsonObject(), "Type"));
        assertEquals(state, string(sensors.get(0).getAsJsonObject(), "State"));
        assertEquals("Target", string(sensors.get(1).getAsJsonObject(), "Type"));
        assertEquals("LockedTarget", string(sensors.get(1).getAsJsonObject(), "TargetSlot"));
        assertEquals("Flag", string(sensors.get(2).getAsJsonObject(), "Type"));
        assertEquals("AirborneMode", string(sensors.get(2).getAsJsonObject(), "Name"));
        assertTrue(sensors.get(2).getAsJsonObject().get("Set").getAsBoolean());
        assertEquals("MotionController", string(sensors.get(3).getAsJsonObject(), "Type"));
        assertEquals("Fly", string(sensors.get(3).getAsJsonObject(), "MotionController"));
    }

    private static void assertAirRanged(JsonObject state) {
        List<JsonObject> children = directInstructions(state);
        assertEquals(8, children.size());
        assertBlockedRecovery(children.get(0));
        assertNavigationRecovery(children.get(1));
        assertTrue(children.get(2).get("Continue").getAsBoolean());
        assertEquals("WANDER_TARGET", string(children.get(2).getAsJsonObject("BodyMotion"), "Mode"));
        assertOrbitBindings(children.get(2).getAsJsonObject("BodyMotion"), "AirCombatWanderRadiusRange",
                "AirCombatWanderRetargetTimeRange", "AirCombatWanderStopDistance", "AirCombatWanderRelativeSpeed",
                "AirCombatAltitudeRange", "AirCombatClimbRelativeSpeed", "AirCombatSinkRelativeSpeed");
        assertEquals("Random", string(children.get(3), "Type"));
        assertTargetRange(children.get(3).getAsJsonObject("Sensor"), "AirFireballAttackDistance");
        assertOnlyTimerState(children.get(3).getAsJsonObject("Sensor"), "NordicDrake_Air_Volley", "Stopped");
        assertOnlyTimerState(children.get(3).getAsJsonObject("Sensor"), "NordicDrake_Air_Breath", "Stopped");
        List<JsonObject> choices = directInstructions(children.get(3));
        assertEquals(2, choices.size());
        assertEquals("AirBreathWeight", string(choices.get(0).getAsJsonObject("Weight"), "Compute"));
        assertEquals("AirVolleyWeight", string(choices.get(1).getAsJsonObject("Weight"), "Compute"));
        assertAttackCooldown(choices.get(0), "NordicDrake_Air_Breath", "AirBreathCooldownRange");
        assertAttackCooldown(choices.get(1), "NordicDrake_Air_Volley", "AirVolleyCooldownRange");
        assertTransition(choices.get(0), ".AirBreathIngress");
        assertTransition(choices.get(1), ".AirVolley");
        assertDirectBreathAvailability(children.get(4));
        assertAttackCooldown(children.get(4), "NordicDrake_Air_Breath", "AirBreathCooldownRange");
        assertOnlyTimerState(children.get(5).getAsJsonObject("Sensor"), "NordicDrake_Air_Volley", "Stopped");
        assertOnlyTimerState(children.get(5).getAsJsonObject("Sensor"), "NordicDrake_Air_Breath", "Running");
        assertAttackCooldown(children.get(5), "NordicDrake_Air_Volley", "AirVolleyCooldownRange");
        assertTransition(children.get(4), ".AirBreathIngress");
        assertTransition(children.get(5), ".AirVolley");
        JsonObject fireball = children.get(6);
        assertTrue(containsAttack(fireball, "AirFireballAttack"));
        assertTargetRange(fireball.getAsJsonObject("Sensor"), "AirFireballAttackDistance");
        assertEquals(0, objects(fireball.getAsJsonObject("Sensor"), sensor -> "Timer".equals(string(sensor, "Type"))
                && "Running".equals(string(sensor, "State"))).size(),
                "moving fireball must remain available whenever its own cooldown is stopped");
        assertFireball(children.get(6));
        assertAnyFallback(children.get(7), ".AirRanged", ".AirRecovery");
    }

    private static void assertBlockedRecovery(JsonObject branch) {
        assertFalse(branch.has("Continue"), "blocked recovery must preempt normal aerial loiter");
        JsonArray sensors = assertAerialRecoveryContext(branch.getAsJsonObject("Sensor"));
        assertEquals("Eval", string(sensors.get(3).getAsJsonObject(), "Type"));
        assertEquals("blocked", string(sensors.get(3).getAsJsonObject(), "Expression"));
        assertRecoveryWander(branch.getAsJsonObject("BodyMotion"));
    }

    private static void assertNavigationRecovery(JsonObject branch) {
        assertFalse(branch.has("Continue"), "navigation recovery must preempt normal aerial loiter");
        JsonArray sensors = assertAerialRecoveryContext(branch.getAsJsonObject("Sensor"));
        JsonObject navigation = sensors.get(3).getAsJsonObject();
        assertEquals("Nav", string(navigation, "Type"));
        assertEquals(List.of("Defer", "Blocked"), navigation.getAsJsonArray("NavStates").asList().stream()
                .map(JsonElement::getAsString).toList());
        assertEquals(2.0, navigation.get("ThrottleDuration").getAsDouble());
        assertRecoveryWander(branch.getAsJsonObject("BodyMotion"));
    }

    private static JsonArray assertAerialRecoveryContext(JsonObject sensor) {
        assertEquals("And", string(sensor, "Type"));
        JsonArray sensors = sensor.getAsJsonArray("Sensors");
        assertEquals(4, sensors.size());
        assertEquals("Target", string(sensors.get(0).getAsJsonObject(), "Type"));
        assertEquals("LockedTarget", string(sensors.get(0).getAsJsonObject(), "TargetSlot"));
        assertFlagSensor(sensors.get(1).getAsJsonObject(), "AirborneMode", true);
        assertEquals("MotionController", string(sensors.get(2).getAsJsonObject(), "Type"));
        assertEquals("Fly", string(sensors.get(2).getAsJsonObject(), "MotionController"));
        return sensors;
    }

    private static void assertRecoveryWander(JsonObject motion) {
        assertEquals("Wander", string(motion, "Type"));
        assertEquals(90.0, motion.get("MaxHeadingChange").getAsDouble());
        assertEquals("AirCombatWanderRelativeSpeed", string(motion.getAsJsonObject("RelativeSpeed"), "Compute"));
    }

    private static void assertAirVolley(JsonObject state) {
        List<JsonObject> children = directInstructions(state);
        assertEquals(2, children.size());
        JsonObject random = children.get(0);
        assertEquals("Random", string(random, "Type"));
        assertTargetRange(random.getAsJsonObject("Sensor"), "AirFireballAttackDistance");
        List<JsonObject> volleys = directInstructions(random);
        assertEquals(3, volleys.size());
        for (int i = 0; i < volleys.size(); i++) {
            assertEquals(1, volleys.get(i).get("Weight").getAsInt());
            assertTargetRange(volleys.get(i).getAsJsonObject("Sensor"), "AirFireballAttackDistance");
            JsonArray actions = volleys.get(i).getAsJsonArray("Actions");
            JsonObject attack = actions.get(0).getAsJsonObject();
            assertEquals("AirVolleyAttack" + (i + 2), string(attack.getAsJsonObject("Attack"), "Compute"));
            assertEquals("Short", string(attack, "BallisticMode"));
            assertEquals(List.of(0.25, 1.0), numbers(attack.getAsJsonArray("AimingTimeRange")));
            assertEquals(List.of(10.0, 10.0), numbers(attack.getAsJsonArray("AttackPauseRange")));
            assertFaceTargetBindings(volleys.get(i).getAsJsonObject("BodyMotion"));
            assertEquals(List.of(List.of(4.0, 4.0), List.of(5.8, 5.8), List.of(7.5, 7.5)).get(i),
                    numbers(actions.get(1).getAsJsonObject().getAsJsonArray("Delay")));
            assertBlockingTransition(volleys.get(i), ".AirVolley", ".AirRecovery");
        }
        assertAnyFallback(children.get(1), ".AirVolley", ".AirRecovery");
    }

    private static void assertAirIngress(JsonObject state) {
        List<JsonObject> children = directInstructions(state);
        assertEquals(3, children.size());
        assertTargetRange(children.get(0).getAsJsonObject("Sensor"), "AirBreathAttackDistance");
        assertTargetFilters(children.get(0).getAsJsonObject("Sensor"), "AirBreathAttackDistance", "AirBreathAttackViewSector");
        assertTransition(children.get(0), ".AirBreathPass");
        assertEquals("APPROACH", string(children.get(1).getAsJsonObject("BodyMotion"), "Mode"));
        assertApproachBindings(children.get(1).getAsJsonObject("BodyMotion"));
        assertEquals("AirBreathIngressTimeout", string(children.get(1).getAsJsonArray("Actions").get(0)
                .getAsJsonObject().getAsJsonObject("Delay"), "Compute"));
        assertBlockingTransition(children.get(1), ".AirBreathIngress", ".AirRecovery");
        assertAnyFallback(children.get(2), ".AirBreathIngress", ".AirRecovery");
    }

    private static void assertAirPass(JsonObject state) {
        List<JsonObject> children = directInstructions(state);
        assertEquals(3, children.size());
        assertTrue(children.get(0).get("Continue").getAsBoolean());
        assertTrue(containsAttack(children.get(0), "AirBreathAttack"));
        JsonObject attack = children.get(0).getAsJsonArray("Actions").get(0).getAsJsonObject();
        assertTrue(children.get(0).getAsJsonObject("Sensor").getAsJsonArray("Sensors").get(0).getAsJsonObject()
                .get("Once").getAsBoolean());
        assertEquals(List.of(0.0, 0.0), numbers(attack.getAsJsonArray("AimingTimeRange")));
        assertEquals(List.of(8.0, 8.0), numbers(attack.getAsJsonArray("AttackPauseRange")));
        assertEquals("PASS_THROUGH_TARGET", string(children.get(1).getAsJsonObject("BodyMotion"), "Mode"));
        assertPassThroughBindings(children.get(1).getAsJsonObject("BodyMotion"));
        assertEquals("AirBreathPassDuration", string(children.get(1).getAsJsonArray("Actions").get(0)
                .getAsJsonObject().getAsJsonObject("Delay"), "Compute"));
        assertBlockingTransition(children.get(1), ".AirBreathPass", ".AirRecovery");
        assertAnyFallback(children.get(2), ".AirBreathPass", ".AirRecovery");
    }

    private static void assertAirRecovery(JsonObject state) {
        List<JsonObject> children = directInstructions(state);
        assertEquals(2, children.size());
        assertEquals("WANDER_TARGET", string(children.get(0).getAsJsonObject("BodyMotion"), "Mode"));
        assertOrbitBindings(children.get(0).getAsJsonObject("BodyMotion"), "AirCombatWanderRadiusRange",
                "AirCombatRecoveryDuration", "AirCombatWanderStopDistance", "AirCombatRecoveryRelativeSpeed",
                "AirCombatAltitudeRange", "AirCombatRecoveryClimbRelativeSpeed", "AirCombatSinkRelativeSpeed");
        assertEquals("AirCombatRecoveryDuration", string(children.get(0).getAsJsonArray("Actions").get(0)
                .getAsJsonObject().getAsJsonObject("Delay"), "Compute"));
        assertBlockingTransition(children.get(0), ".AirRecovery", ".AirRanged");
        assertAnyFallback(children.get(1), ".AirRecovery", ".AirRanged");
    }

    private static void assertTransition(JsonObject branch, String state) {
        List<String> actions = actionTypes(branch);
        if (branch.has("ActionsBlocking") && branch.get("ActionsBlocking").getAsBoolean()) {
            assertEquals("SetFlag", actions.get(actions.size() - 2));
            assertEquals("State", actions.get(actions.size() - 1));
            JsonObject pending = branch.getAsJsonArray("Actions").get(actions.size() - 2).getAsJsonObject();
            assertFlagAction(pending, resetFlagFor(state), true);
            assertEquals(state, string(branch.getAsJsonArray("Actions").get(actions.size() - 1).getAsJsonObject(), "State"));
        } else {
            assertEquals("State", actions.get(actions.size() - 2));
            assertEquals("ResetInstructions", actions.get(actions.size() - 1));
            assertEquals(state, string(branch.getAsJsonArray("Actions").get(actions.size() - 2).getAsJsonObject(), "State"));
        }
    }

    private static void assertBlockingTransition(JsonObject branch, String source, String state) {
        assertTrue(branch.get("ActionsBlocking").getAsBoolean());
        assertFalse(source.equals(state), "blocking transitions must leave their source state");
        assertTransition(branch, state);
    }

    private static void assertAnyFallback(JsonObject branch, String source, String state) {
        assertEquals("Any", string(branch.getAsJsonObject("Sensor"), "Type"));
        assertTrue(branch.get("ActionsBlocking").getAsBoolean());
        assertEquals(List.of(0.25, 0.5), numbers(branch.getAsJsonArray("Actions").get(0).getAsJsonObject()
                .getAsJsonArray("Delay")));
        assertBlockingTransition(branch, source, state);
    }

    private static String resetFlagFor(String state) {
        return switch (state) {
            case ".AirRecovery" -> "NordicDrake_Air_Reset_To_Recovery";
            case ".AirRanged" -> "NordicDrake_Air_Reset_To_Ranged";
            default -> throw new AssertionError("blocking aerial transitions must have a reset destination: " + state);
        };
    }

    private static void assertFireball(JsonObject branch) {
        JsonObject sensor = branch.getAsJsonObject("Sensor");
        assertTargetFilters(sensor, "AirFireballAttackDistance", "AirRangedAttackViewSector");
        assertOnlyTimerState(sensor, "NordicDrake_Air_Fireball", "Stopped");
        assertAttackCooldown(branch, "NordicDrake_Air_Fireball", "AirFireballCooldownRange");
    }

    private static void assertTargetFilters(JsonObject sensor, String range, String sector) {
        JsonObject target = objects(sensor, object -> "Target".equals(string(object, "Type"))
                && "LockedTarget".equals(string(object, "TargetSlot")) && object.has("Range")
                && range.equals(string(object.getAsJsonObject("Range"), "Compute"))).stream().findFirst().orElseThrow();
        JsonArray filters = target.getAsJsonArray("Filters");
        assertEquals(2, filters.size());
        assertEquals("LineOfSight", string(filters.get(0).getAsJsonObject(), "Type"));
        assertEquals("ViewSector", string(filters.get(1).getAsJsonObject(), "Type"));
        assertEquals(sector, string(filters.get(1).getAsJsonObject().getAsJsonObject("ViewSector"), "Compute"));
    }

    private static void assertAttackCooldown(JsonObject branch, String timer, String cooldown) {
        List<JsonObject> timers = branch.getAsJsonArray("Actions").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(action -> "TimerStart".equals(string(action, "Type")) || "TimerRestart".equals(string(action, "Type"))).toList();
        assertEquals(List.of("TimerStart", "TimerRestart"), timers.stream().map(action -> string(action, "Type")).toList());
        assertTrue(timers.stream().allMatch(action -> timer.equals(string(action, "Name"))));
        assertEquals(cooldown, string(timers.get(0).getAsJsonObject("StartValueRange"), "Compute"));
        assertEquals(cooldown, string(timers.get(0).getAsJsonObject("RestartValueRange"), "Compute"));
    }

    private static void assertDirectBreathAvailability(JsonObject branch) {
        JsonArray sensors = branch.getAsJsonObject("Sensor").getAsJsonArray("Sensors");
        assertEquals(5, sensors.size());
        assertOnlyTimerState(sensors.get(3).getAsJsonObject(), "NordicDrake_Air_Breath", "Stopped");
        JsonObject unavailable = sensors.get(4).getAsJsonObject();
        assertEquals("Or", string(unavailable, "Type"));
        JsonArray choices = unavailable.getAsJsonArray("Sensors");
        assertEquals(2, choices.size());
        assertOnlyTimerState(choices.get(0).getAsJsonObject(), "NordicDrake_Air_Volley", "Running");
        JsonObject not = choices.get(1).getAsJsonObject();
        assertEquals("Not", string(not, "Type"));
        JsonObject target = not.getAsJsonObject("Sensor");
        assertEquals("Target", string(target, "Type"));
        assertEquals("LockedTarget", string(target, "TargetSlot"));
        assertEquals("AirFireballAttackDistance", string(target.getAsJsonObject("Range"), "Compute"));
    }

    private static void assertOrbitBindings(JsonObject motion, String radius, String retarget, String stop, String speed,
            String altitude, String climb, String sink) {
        assertEquals(radius, string(motion.getAsJsonObject("WanderRadiusRange"), "Compute"));
        assertEquals(retarget, string(motion.getAsJsonObject("WanderRetargetTimeRange"), "Compute"));
        assertEquals(stop, string(motion.getAsJsonObject("WanderStopDistance"), "Compute"));
        assertEquals(speed, string(motion.getAsJsonObject("RelativeSpeed"), "Compute"));
        assertEquals(altitude, string(motion.getAsJsonObject("DesiredAltitudeRange"), "Compute"));
        assertEquals(climb, string(motion.getAsJsonObject("ClimbRelativeSpeed"), "Compute"));
        assertEquals(sink, string(motion.getAsJsonObject("SinkRelativeSpeed"), "Compute"));
    }

    private static void assertFaceTargetBindings(JsonObject motion) {
        assertEquals("TameworkFlyingOrbit", string(motion, "Type"));
        assertEquals("FACE_TARGET", string(motion, "Mode"));
        assertEquals("AirCombatAltitudeRange", string(motion.getAsJsonObject("DesiredAltitudeRange"), "Compute"));
        assertEquals("AirCombatClimbRelativeSpeed", string(motion.getAsJsonObject("ClimbRelativeSpeed"), "Compute"));
        assertEquals("AirCombatSinkRelativeSpeed", string(motion.getAsJsonObject("SinkRelativeSpeed"), "Compute"));
    }

    private static void assertApproachBindings(JsonObject motion) {
        assertEquals("TameworkFlyingOrbit", string(motion, "Type"));
        assertEquals("APPROACH", string(motion, "Mode"));
        assertEquals(0.0, motion.get("ApproachStopDistance").getAsDouble());
        assertEquals(12.0, motion.get("ApproachSlowDownDistance").getAsDouble());
        assertEquals("AirBreathAttackRelativeSpeed", string(motion.getAsJsonObject("RelativeSpeed"), "Compute"));
        assertEquals("AirBreathAttackAltitudeRange", string(motion.getAsJsonObject("DesiredAltitudeRange"), "Compute"));
        assertEquals("AirCombatClimbRelativeSpeed", string(motion.getAsJsonObject("ClimbRelativeSpeed"), "Compute"));
        assertEquals("AirCombatSinkRelativeSpeed", string(motion.getAsJsonObject("SinkRelativeSpeed"), "Compute"));
    }

    private static void assertPassThroughBindings(JsonObject motion) {
        assertEquals("TameworkFlyingOrbit", string(motion, "Type"));
        assertEquals("PASS_THROUGH_TARGET", string(motion, "Mode"));
        assertEquals("AirBreathPassDistance", string(motion.getAsJsonObject("PassThroughDistance"), "Compute"));
        assertEquals("AirBreathPassStopDistance", string(motion.getAsJsonObject("PassThroughStopDistance"), "Compute"));
        assertEquals("AirBreathAttackRelativeSpeed", string(motion.getAsJsonObject("RelativeSpeed"), "Compute"));
        assertEquals("AirBreathAttackAltitudeRange", string(motion.getAsJsonObject("DesiredAltitudeRange"), "Compute"));
        assertEquals("AirCombatClimbRelativeSpeed", string(motion.getAsJsonObject("ClimbRelativeSpeed"), "Compute"));
        assertEquals("AirCombatSinkRelativeSpeed", string(motion.getAsJsonObject("SinkRelativeSpeed"), "Compute"));
    }

    private static List<JsonObject> directInstructions(JsonObject branch) {
        return branch.getAsJsonArray("Instructions").asList().stream().map(JsonElement::getAsJsonObject).toList();
    }

    private static void assertAerialContext(JsonObject state) {
        List<JsonObject> branchSensors = objects(state, object -> object.has("Sensor") && object.has("Actions"));
        assertTrue(branchSensors.stream().anyMatch(branch -> objects(branch.getAsJsonObject("Sensor"), object ->
                "Target".equals(string(object, "Type")) && "LockedTarget".equals(string(object, "TargetSlot"))).size() > 0));
        assertTrue(branchSensors.stream().anyMatch(branch -> objects(branch.getAsJsonObject("Sensor"), object ->
                "Flag".equals(string(object, "Type")) && "AirborneMode".equals(string(object, "Name"))
                        && object.has("Set") && object.get("Set").getAsBoolean()).size() > 0));
        assertTrue(branchSensors.stream().anyMatch(branch -> objects(branch.getAsJsonObject("Sensor"), object ->
                "MotionController".equals(string(object, "Type")) && "Fly".equals(string(object, "MotionController"))).size() > 0));
    }

    private static void assertOrbitMode(JsonObject state, String mode) {
        assertTrue(objects(state, object -> "TameworkFlyingOrbit".equals(string(object, "Type"))
                && mode.equals(string(object, "Mode"))).size() >= 1, "missing " + mode + " orbit");
    }

    private static void assertGroundedMovement(JsonObject groundCombat) {
        List<JsonObject> children = groundCombat.getAsJsonArray("Instructions").asList().stream()
                .map(JsonElement::getAsJsonObject).toList();
        assertEquals(7, children.size(), "ground combat must retain its seven ordered behavior branches");
        JsonObject tracking = children.get(0);
        assertTrue(tracking.has("Continue") && tracking.get("Continue").getAsBoolean());
        assertGroundedContext(tracking.getAsJsonObject("Sensor"));
        assertEquals("Aim", string(tracking.getAsJsonObject("HeadMotion"), "Type"));
        assertTrue(isChaseBranch(children.get(1)));
        assertTrue(isPositioningBranch(children.get(2)));
        assertEquals("Random", string(children.get(3), "Type"));
        assertTrue(containsAttack(children.get(4), "GroundBiteAttack"));
        assertTrue(containsAttack(children.get(5), "GroundBreathAttack"));
        assertTrue(containsAttack(children.get(6), "GroundBasicAttack"));
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

        JsonObject maintain = children.stream().filter(child -> child.has("BodyMotion")
                && "MaintainDistance".equals(string(child.getAsJsonObject("BodyMotion"), "Type"))).findFirst()
                .orElseThrow(() -> new AssertionError("missing grounded MaintainDistance"));
        assertTrue(maintain.has("Continue") && maintain.get("Continue").getAsBoolean(),
                "in-range positioning must continue to sibling attack evaluation");
        assertTrue(children.indexOf(maintain) < children.indexOf(children.stream()
                .filter(child -> "Random".equals(string(child, "Type"))).findFirst().orElseThrow()),
                "in-range positioning must precede attack selection");
        JsonObject maintainMotion = maintain.getAsJsonObject("BodyMotion");
        assertEquals("DesiredAttackDistanceRange", string(maintainMotion.getAsJsonObject("DesiredDistanceRange"), "Compute"));
        assertEquals("CombatMovingRelativeSpeed", string(maintainMotion.getAsJsonObject("RelativeForwardsSpeed"), "Compute"));
        assertEquals("CombatBackwardsRelativeSpeed", string(maintainMotion.getAsJsonObject("RelativeBackwardsSpeed"), "Compute"));
        assertEquals(0.2, maintainMotion.get("MoveThreshold").getAsDouble());
        assertTargetRange(maintain.getAsJsonObject("Sensor"), "CombatBehaviorDistance");
    }

    private static void assertGroundedAttackChoice(JsonObject groundCombat) {
        List<JsonObject> attacks = objects(groundCombat, object -> object.has("Actions")
                && actionTypes(object).contains("Attack"));
        assertEquals(5, attacks.size(),
                "ground combat must provide two random specials, two direct specials, and one basic fallback");
        List<JsonObject> basics = attackBranches(attacks, "GroundBasicAttack");
        List<JsonObject> bites = attackBranches(attacks, "GroundBiteAttack");
        List<JsonObject> breaths = attackBranches(attacks, "GroundBreathAttack");
        assertEquals(1, basics.size());
        assertEquals(2, bites.size());
        assertEquals(2, breaths.size());
        JsonObject basic = basics.get(0);
        assertVanillaBasicFallback(basic);
        bites.forEach(branch -> assertGroundedAttackBranch(branch, "GroundBiteAttack", "GroundBiteDistance",
                "GroundBiteCooldownRange", "NordicDrake_Ground_Bite", true));
        breaths.forEach(branch -> assertGroundedAttackBranch(branch, "GroundBreathAttack", "GroundBreathDistance",
                "GroundBreathCooldownRange", "NordicDrake_Ground_Breath", true));
        List<JsonObject> children = groundCombat.getAsJsonArray("Instructions").asList().stream()
                .map(JsonElement::getAsJsonObject).toList();
        assertDirectSpecialAvailability(children.get(4), "NordicDrake_Ground_Bite", "GroundBiteDistance",
                "NordicDrake_Ground_Breath", "GroundBreathDistance");
        assertDirectSpecialAvailability(children.get(5), "NordicDrake_Ground_Breath", "GroundBreathDistance",
                "NordicDrake_Ground_Bite", "GroundBiteDistance");

        JsonObject random = objects(groundCombat, object -> "Random".equals(string(object, "Type"))).stream()
                .findFirst().orElseThrow(() -> new AssertionError("missing special attack random selector"));
        List<JsonObject> selected = random.getAsJsonArray("Instructions").asList().stream()
                .map(JsonElement::getAsJsonObject).toList();
        assertEquals(2, selected.size());
        assertEquals("GroundBiteWeight", string(selected.stream().filter(branch -> containsAttack(branch, "GroundBiteAttack"))
                .findFirst().orElseThrow().getAsJsonObject("Weight"), "Compute"));
        assertEquals("GroundBreathWeight", string(selected.stream().filter(branch -> containsAttack(branch, "GroundBreathAttack"))
                .findFirst().orElseThrow().getAsJsonObject("Weight"), "Compute"));
        assertGroundedContext(random.getAsJsonObject("Sensor"));
        assertTimerStopped(random.getAsJsonObject("Sensor"), "NordicDrake_Ground_Bite");
        assertTimerStopped(random.getAsJsonObject("Sensor"), "NordicDrake_Ground_Breath");
        assertRangeAndLineOfSight(random.getAsJsonObject("Sensor"), "GroundBiteDistance");
        assertRangeAndLineOfSight(random.getAsJsonObject("Sensor"), "GroundBreathDistance");
        assertTrue(children.indexOf(random) < children.indexOf(basic),
                "special selection must precede the basic fallback");
    }

    private static void assertVanillaBasicFallback(JsonObject branch) {
        assertTrue(branch.has("Continue") && branch.get("Continue").getAsBoolean());
        assertGroundedContext(branch.getAsJsonObject("Sensor"));
        assertTrue(objects(branch.getAsJsonObject("Sensor"), object -> "State".equals(string(object, "Type"))
                && ".GroundCombat".equals(string(object, "State"))).size() == 1);
        assertRangeAndLineOfSight(branch.getAsJsonObject("Sensor"), "GroundBasicAttackDistance");
        assertTrue(branch.has("ActionsBlocking") && branch.get("ActionsBlocking").getAsBoolean());
        assertGroundedAim(branch);

        assertEquals(List.of("Timeout", "Attack", "Timeout"), actionTypes(branch));
        JsonArray actions = branch.getAsJsonArray("Actions");
        assertEquals(List.of(0.3, 0.3), numbers(actions.get(0).getAsJsonObject().getAsJsonArray("Delay")));
        JsonObject attack = actions.get(1).getAsJsonObject();
        assertEquals("GroundBasicAttack", string(attack.getAsJsonObject("Attack"), "Compute"));
        assertEquals(List.of(0.1, 0.2), numbers(attack.getAsJsonArray("AimingTimeRange")));
        assertEquals("GroundBasicAttackPauseRange", string(attack.getAsJsonObject("AttackPauseRange"), "Compute"));
        assertEquals(List.of(0.4, 0.4), numbers(actions.get(2).getAsJsonObject().getAsJsonArray("Delay")));

        assertEquals(0, objects(branch, object -> {
            String type = string(object, "Type");
            return type != null && type.startsWith("Timer")
                    && "NordicDrake_Ground_Basic".equals(string(object, "Name"));
        }).size(), "the basic fallback must use ActionAttack pause semantics, not a custom timer");
        assertEquals(0, objects(branch, object -> "NordicDrake_Ground_Bite_Active".equals(string(object, "Name"))
                || "NordicDrake_Ground_Breath_Active".equals(string(object, "Name"))).size(),
                "the ordered fallback must not be gated by special-active flags");
    }

    private static void assertGroundedAttackBranch(
            JsonObject branch, String attack, String range, String cooldown, String timer, boolean blocking) {
        assertGroundedContext(branch.getAsJsonObject("Sensor"));
        assertTrue(objects(branch.getAsJsonObject("Sensor"), object -> "State".equals(string(object, "Type"))
                && ".GroundCombat".equals(string(object, "State"))).size() == 1);
        assertTimerStopped(branch.getAsJsonObject("Sensor"), timer);
        assertRangeAndLineOfSight(branch.getAsJsonObject("Sensor"), range);
        assertEquals(blocking, branch.has("ActionsBlocking") && branch.get("ActionsBlocking").getAsBoolean());
        assertEquals(attack, string(action(branch, "Attack").getAsJsonObject("Attack"), "Compute"));
        List<JsonObject> timers = actionsAfterAttack(branch).stream()
                .filter(candidate -> string(candidate, "Type").startsWith("Timer")).toList();
        assertEquals(List.of("TimerStart", "TimerRestart"),
                timers.stream().map(timerAction -> string(timerAction, "Type")).toList(),
                "each attack must start and restart exactly its own cooldown timer");
        assertTrue(timers.stream().allMatch(timerAction -> timer.equals(string(timerAction, "Name"))),
                "post-attack timer actions must not target another attack timer");
        JsonObject timerStart = timers.get(0);
        assertEquals(cooldown, string(timerStart.getAsJsonObject("StartValueRange"), "Compute"));
        assertEquals(cooldown, string(timerStart.getAsJsonObject("RestartValueRange"), "Compute"));
        assertGroundedAim(branch);
    }

    private static void assertGroundedAim(JsonObject branch) {
        JsonObject aim = branch.getAsJsonObject("HeadMotion");
        assertEquals("Aim", string(aim, "Type"));
        assertEquals(0, aim.get("Spread").getAsInt());
        assertEquals(1, aim.get("HitProbability").getAsInt());
        assertTrue(aim.get("Deflection").getAsBoolean());
        assertEquals("CombatRelativeTurnSpeed", string(aim.getAsJsonObject("RelativeTurnSpeed"), "Compute"));
    }

    private static List<JsonObject> attackBranches(List<JsonObject> branches, String attack) {
        return branches.stream().filter(branch -> containsAttack(branch, attack)).toList();
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

    private static List<JsonObject> actionsAfterAttack(JsonObject branch) {
        List<JsonElement> actions = branch.getAsJsonArray("Actions").asList();
        int attackIndex = actionTypes(branch).indexOf("Attack");
        return actions.subList(attackIndex + 1, actions.size()).stream().map(JsonElement::getAsJsonObject).toList();
    }

    private static boolean isChaseBranch(JsonObject branch) {
        return branch.has("Instructions") && branch.getAsJsonArray("Instructions").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(child -> "Component_Tamework_Instruction_Intelligent_Chase".equals(string(child, "Reference")));
    }

    private static boolean isPositioningBranch(JsonObject branch) {
        return branch.has("BodyMotion") && "MaintainDistance".equals(string(branch.getAsJsonObject("BodyMotion"), "Type"));
    }

    private static void assertTimerStopped(JsonObject sensor, String timer) {
        assertEquals(1, objects(sensor, object -> "Timer".equals(string(object, "Type"))
                && timer.equals(string(object, "Name")) && "Stopped".equals(string(object, "State"))).size());
    }

    private static void assertTimerRunning(JsonObject sensor, String timer) {
        assertEquals(1, objects(sensor, object -> "Timer".equals(string(object, "Type"))
                && timer.equals(string(object, "Name")) && "Running".equals(string(object, "State"))).size());
    }

    private static void assertOnlyTimerState(JsonObject sensor, String timer, String state) {
        List<JsonObject> timers = objects(sensor, object -> "Timer".equals(string(object, "Type"))
                && timer.equals(string(object, "Name")));
        assertEquals(1, timers.size());
        assertEquals(state, string(timers.get(0), "State"));
    }

    private static void assertRangeAndLineOfSight(JsonObject sensor, String range) {
        assertEquals(1, objects(sensor, object -> "Target".equals(string(object, "Type"))
                && "LockedTarget".equals(string(object, "TargetSlot"))
                && object.has("Range") && range.equals(string(object.getAsJsonObject("Range"), "Compute"))
                && objects(object, filter -> "LineOfSight".equals(string(filter, "Type"))).size() == 1).size());
    }

    private static void assertTargetRange(JsonObject sensor, String range) {
        assertEquals(1, objects(sensor, object -> "Target".equals(string(object, "Type"))
                && "LockedTarget".equals(string(object, "TargetSlot"))
                && object.has("Range") && range.equals(string(object.getAsJsonObject("Range"), "Compute"))).size());
    }

    private static void assertDirectSpecialAvailability(
            JsonObject branch, String readyTimer, String readyRange, String unavailableTimer, String unavailableRange) {
        JsonObject sensor = branch.getAsJsonObject("Sensor");
        assertTimerStopped(sensor, readyTimer);
        assertRangeAndLineOfSight(sensor, readyRange);
        List<JsonObject> unavailable = objects(sensor, object -> "Or".equals(string(object, "Type"))).stream().toList();
        assertEquals(1, unavailable.size(), "direct special must explicitly require the other special to be unavailable");
        JsonObject gates = unavailable.get(0);
        assertEquals(1, objects(gates, object -> "Timer".equals(string(object, "Type"))
                && unavailableTimer.equals(string(object, "Name")) && "Running".equals(string(object, "State"))).size());
        assertEquals(1, objects(gates, object -> "Not".equals(string(object, "Type"))
                && objects(object, child -> "Target".equals(string(child, "Type"))
                && "LockedTarget".equals(string(child, "TargetSlot"))
                && child.has("Range") && unavailableRange.equals(string(child.getAsJsonObject("Range"), "Compute"))
                && objects(child, filter -> "LineOfSight".equals(string(filter, "Type"))).size() == 1).size() == 1).size());
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
        List<JsonObject> actions = branch.getAsJsonArray("Actions").asList().stream()
                .map(JsonElement::getAsJsonObject).toList();
        assertEquals(List.of("TimerStop", "TimerStop", "TimerStop", "SetFlag", "SetFlag", "State", "ReleaseTarget", "ResetInstructions"),
                actions.stream().map(action -> string(action, "Type")).toList());
        assertCleanupTimerStops(actions);
        assertFlagAction(actions.get(3), "NordicDrake_Air_Reset_To_Recovery", false);
        assertFlagAction(actions.get(4), "NordicDrake_Air_Reset_To_Ranged", false);
        assertEquals(".Default", string(actions.get(5), "State"));
        assertEquals("LockedTarget", string(actions.get(6), "TargetSlot"));
        assertEquals("ResetInstructions", string(actions.get(7), "Type"));
    }

    private static void assertCancellationClearsBothResetFlags(JsonObject branch) {
        assertTrue(branch.get("Continue").getAsBoolean());
        List<JsonObject> actions = branch.getAsJsonArray("Actions").asList().stream()
                .map(JsonElement::getAsJsonObject).toList();
        assertEquals(List.of("TimerStop", "TimerStop", "TimerStop", "SetFlag", "SetFlag", "State", "ResetInstructions"),
                actions.stream().map(action -> string(action, "Type")).toList());
        assertCleanupTimerStops(actions);
        assertFlagAction(actions.get(3), "NordicDrake_Air_Reset_To_Recovery", false);
        assertFlagAction(actions.get(4), "NordicDrake_Air_Reset_To_Ranged", false);
        assertEquals(".Default", string(actions.get(5), "State"));
        assertEquals("ResetInstructions", string(actions.get(6), "Type"));
        assertEquals(0, objects(branch, object -> "ReleaseTarget".equals(string(object, "Type"))).size(),
                "aerial cancellation must leave LockedTarget ownership to Defend");
    }

    private static void assertCleanupTimerStops(List<JsonObject> actions) {
        assertEquals(List.of("NordicDrake_Air_Fireball", "NordicDrake_Air_Volley", "NordicDrake_Air_Breath"),
                actions.subList(0, 3).stream().map(action -> string(action, "Name")).toList());
    }

    private static void assertFlagSensor(JsonObject sensor, String name, boolean value) {
        assertEquals("Flag", string(sensor, "Type"));
        assertEquals(name, string(sensor, "Name"));
        assertTrue(sensor.has("Set"));
        assertFalse(sensor.has("SetTo"));
        assertEquals(value, sensor.get("Set").getAsBoolean());
    }

    private static void assertFlagAction(JsonObject action, String name, boolean value) {
        assertEquals("SetFlag", string(action, "Type"));
        assertEquals(name, string(action, "Name"));
        assertFalse(action.has("Set"));
        assertTrue(action.has("SetTo"));
        assertEquals(value, action.get("SetTo").getAsBoolean());
    }
}
