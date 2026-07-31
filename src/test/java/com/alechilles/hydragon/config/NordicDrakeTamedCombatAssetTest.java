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
            "AddToHostileTargetMemory", "State", "BodyMotion", "Attack");

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
        assertTaskOneShellStopsBeforeCombat(component);
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

    private static void assertTaskOneShellStopsBeforeCombat(JsonObject component) {
        List<JsonObject> children = directChildren(component);
        assertEquals(5, children.size(), "Task 1 must contain exactly four safety exits and one terminal no-op");
        assertEquals(JsonParser.parseString("""
                {"Sensor":{"Type":"Any"}}
                """).getAsJsonObject(), children.get(4),
                "the fifth Task 1 child must be the exact terminal no-op");

        assertEquals(0, objects(component, object -> {
            String type = string(object, "Type");
            return object.has("BodyMotion") || object.has("Attack")
                    || type != null && FORBIDDEN_IMPLICIT_COMBAT_TYPES.contains(type);
        }).size(),
                "Task 1 must not add combat state, motion, or attack behavior before the later combat tasks");
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
