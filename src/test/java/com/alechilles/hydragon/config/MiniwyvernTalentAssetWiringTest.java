package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Contract for the asset-owned Miniwyvern combat execution path. */
final class MiniwyvernTalentAssetWiringTest {
    private static final Path TEMPLATE = Path.of("Server", "NPC", "Roles", "Creature", "HyDragon",
            "Templates", "Template_Wyvern_Mini_Flying_Tamed.json");
    private static final List<String> ROLES = List.of("Wild", "Nature", "Toxic", "Fire", "Void", "Lightning", "Ice");
    private static final List<String> COMBAT_TALENTS = List.of("DraconicProjectile", "ProjectileRange",
            "ProjectileCadence", "ProjectileForce", "ProjectileGuidance", "ProjectileImpact",
            "ProjectilePattern", "DraconicAssault", "AssaultUtility", "AssaultMastery", "DraconicApex");
    private static final String AIM_FLAG = "Miniwyvern_Projectile_Aiming";
    private static final String AIM_TIMER = "Miniwyvern_Projectile_Aim";
    private static final String COOLDOWN_TIMER = "Miniwyvern_Projectile_Cooldown";

    @Test
    void templateContainsTalentGatedExecutableVariants() throws IOException {
        JsonObject template = load(TEMPLATE);
        JsonArray instructions = template.getAsJsonArray("Instructions");

        for (int index = 0; index < COMBAT_TALENTS.size(); index++) {
            String talentId = COMBAT_TALENTS.get(index);
            JsonObject instruction = instructionForTalent(instructions, talentId);
            assertTrue(hasExecutableAction(instruction), talentId + " must select an executable action");
            assertEquals(Set.of(talentId), positiveTalentGates(instruction),
                    talentId + " must be selected only by its positive talent gate");
            assertEquals(new HashSet<>(COMBAT_TALENTS.subList(index + 1, COMBAT_TALENTS.size())),
                    excludedTalents(instruction),
                    talentId + " must exclude every higher owned combat variant");
        }
    }

    @Test
    void defendDispatchContinuesToTheUnlockedProjectileVariant() throws IOException {
        JsonArray instructions = load(TEMPLATE).getAsJsonArray("Instructions");
        JsonObject defendDispatch = defendDispatch(instructions);

        assertTrue(defendDispatch.has("Continue") && defendDispatch.get("Continue").getAsBoolean(),
                "the Defend state dispatch must continue so its later talent projectile instruction can run");
    }

    @Test
    void formPassivesRequireEssenceBond() throws IOException {
        for (String form : List.of("Fire", "Ice", "Lightning", "Nature", "Toxic", "Void")) {
            JsonObject archetype = load(Path.of("Server", "HyDragon", "MiniwyvernArchetypes",
                    form + ".json"));
            assertEquals("EssenceBond", string(archetype, "RequiredTalentId"),
                    form + " owner passive must remain locked until Essence Bond is purchased");
        }
    }

    @Test
    void projectileCadenceUsesDeliberateRandomizedProgressionBands() throws IOException {
        JsonArray instructions = load(TEMPLATE).getAsJsonArray("Instructions");
        Map<String, JsonElement> expected = Map.ofEntries(
                Map.entry("DraconicProjectile", JsonParser.parseString("[5,7]")),
                Map.entry("ProjectileRange", JsonParser.parseString("[5,7]")),
                Map.entry("ProjectileCadence", JsonParser.parseString("[4,6]")),
                Map.entry("ProjectileForce", JsonParser.parseString("[5,7]")),
                Map.entry("ProjectileGuidance", JsonParser.parseString("[4,6]")),
                Map.entry("ProjectileImpact", JsonParser.parseString("[5,7]")),
                Map.entry("ProjectilePattern", JsonParser.parseString("[4,6]")),
                Map.entry("DraconicAssault", JsonParser.parseString("[3,5]")),
                Map.entry("AssaultUtility", JsonParser.parseString("[3,5]")),
                Map.entry("AssaultMastery", JsonParser.parseString("[3,5]")),
                Map.entry("DraconicApex", JsonParser.parseString("[3,5]")));

        for (String talentId : COMBAT_TALENTS) {
            JsonObject instruction = instructionForTalent(instructions, talentId);
            JsonObject attack = projectileAttack(instruction);
            JsonObject cooldown = action(instruction, "TimerStart", COOLDOWN_TIMER);
            JsonArray actions = instruction.getAsJsonArray("Actions");
            JsonArray startRange = cooldown.getAsJsonArray("StartValueRange");

            assertEquals(JsonParser.parseString("[0.1,0.2]"), attack.get("AimingTimeRange"), talentId);
            assertEquals(JsonParser.parseString("[0,0]"), attack.get("AttackPauseRange"), talentId);
            assertEquals(expected.get(talentId), startRange, talentId);
            assertEquals(expected.get(talentId), cooldown.get("RestartValueRange"), talentId);
            assertTrue(startRange.get(0).getAsDouble() >= 3.0, talentId + " fires too quickly");
            assertTrue(startRange.get(0).getAsDouble() < startRange.get(1).getAsDouble(),
                    talentId + " cadence must be randomized");
            assertTrue(hasAction(instruction, "TimerRestart", COOLDOWN_TIMER), talentId);
            assertTrue(hasStoppedTimerSensor(instruction, AIM_TIMER), talentId);
            assertTrue(hasPositiveFlagSensor(instruction, AIM_FLAG), talentId);
            assertTrue(instruction.get("ActionsBlocking").getAsBoolean(), talentId);
            assertEquals("SetFlag", string(actions.get(actions.size() - 1).getAsJsonObject(), "Type"), talentId);
            assertTrue(hasSetFlag(actions.get(actions.size() - 1), AIM_FLAG, false), talentId);
            assertFalse(instruction.has("BodyMotion"), talentId + " motion belongs to the shared priority phase");
            assertFalse(instruction.has("HeadMotion"), talentId + " motion belongs to the shared priority phase");
        }
    }

    @Test
    void nativeFlightCanBrakeToAStationaryHover() throws IOException {
        JsonObject template = load(TEMPLATE);
        JsonObject fly = template.getAsJsonArray("MotionControllerList").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(controller -> "Fly".equals(string(controller, "Type")))
                .findFirst().orElseThrow();

        assertEquals(0.0, fly.get("MinAirSpeed").getAsDouble());
        assertEquals(12.0, fly.get("Deceleration").getAsDouble());
    }

    @Test
    void dedicatedAimPhaseOwnsMotionBeforeOrdinaryDefendMovement() throws IOException {
        JsonArray instructions = load(TEMPLATE).getAsJsonArray("Instructions");
        JsonObject aim = instructionUsingFlagAndMotion(instructions, AIM_FLAG, "MatchLook");
        JsonObject defendDispatch = defendDispatch(instructions);

        assertTrue(topLevelIndex(instructions, aim) < topLevelIndex(instructions, defendDispatch));
        assertEquals(JsonParser.parseString("{\"Type\":\"MatchLook\"}"), aim.get("BodyMotion"));
        assertEquals(JsonParser.parseString(
                "{\"Type\":\"Aim\",\"Spread\":0,\"HitProbability\":1,\"Deflection\":true}"),
                aim.get("HeadMotion"));
    }

    @Test
    void projectileAimSchedulerAndRecoveryAreExplicit() throws IOException {
        JsonArray instructions = load(TEMPLATE).getAsJsonArray("Instructions");
        JsonObject scheduler = instructionWithAction(instructions, "TimerStart", AIM_TIMER);
        JsonObject recovery = instructionWithAction(instructions, "TimerStop", AIM_TIMER);
        JsonObject defendDispatch = defendDispatch(instructions);

        assertTrue(topLevelIndex(instructions, recovery) < topLevelIndex(instructions, scheduler));
        assertTrue(topLevelIndex(instructions, scheduler) < topLevelIndex(instructions, defendDispatch));

        JsonObject start = action(scheduler, "TimerStart", AIM_TIMER);
        assertEquals(JsonParser.parseString("[0.4,0.7]"), start.get("StartValueRange"));
        assertEquals(JsonParser.parseString("[0.4,0.7]"), start.get("RestartValueRange"));
        assertTrue(hasAction(scheduler, "TimerRestart", AIM_TIMER));
        assertTrue(hasSetFlag(scheduler, AIM_FLAG, true));
        JsonArray schedulerActions = scheduler.getAsJsonArray("Actions");
        assertTrue(hasSetFlag(schedulerActions.get(schedulerActions.size() - 1), AIM_FLAG, true));

        assertTrue(hasSetFlag(recovery, AIM_FLAG, false));
        assertTrue(hasAction(recovery, "TimerStop", AIM_TIMER));
        assertTrue(hasAction(recovery, "ResetInstructions", null));
    }

    @Test
    void allFormsProvideOnlyGenericTalentBindingsAndWildCombatIsRawOnly() throws IOException {
        for (String form : ROLES) {
            JsonObject role = load(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini",
                    "Tamed_Wyvern_Mini_" + form + ".json"));
            String source = role.toString();
            assertFalse(role.getAsJsonObject("Modify").has("TalentCombatFlags"),
                    form + " must not override an undeclared template parameter");
            assertFalse(source.contains("Miniwyvern_"), form + " must not introduce form-specific talent IDs");
        }
        JsonObject wildRole = load(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini",
                "Tamed_Wyvern_Mini_Wild.json")).getAsJsonObject("Modify");
        for (String parameter : List.of("TalentProjectileBase", "TalentProjectileIntermediate", "TalentProjectileApex")) {
            Path rootPath = rootPath(string(wildRole, parameter));
            for (JsonElement interaction : load(rootPath).getAsJsonArray("Interactions")) {
                String launcherId = interaction.getAsString();
                JsonObject launcher = load(interactionPath(launcherId));
                assertTrue(launcher.has("ProjectileId"), "Wild root interaction must launch a projectile");
                assertRawOnlyInteractionChain(launcherId, parameter + " launcher", new HashSet<>());
                JsonObject projectile = load(projectilePath(string(launcher, "ProjectileId")));
                assertRawOnly(projectile, parameter + " projectile");
            }
        }
    }

    @Test
    void elementalTalentProjectilesRetainTheirThemedBaseAppearance() throws IOException {
        for (String form : List.of("Lightning", "Toxic", "Void")) {
            JsonObject role = load(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini",
                    "Tamed_Wyvern_Mini_" + form + ".json")).getAsJsonObject("Modify");
            String baseProjectileId = projectileIdForRoot(string(role, "TalentProjectileBase"));
            for (String parameter : List.of("TalentProjectileIntermediate", "TalentProjectileApex")) {
                JsonObject upgraded = load(projectilePath(projectileIdForRoot(string(role, parameter))));
                assertFalse("Rubble_Stone".equals(string(upgraded, "Appearance")),
                        form + " must not replace its themed projectile with generic rubble");
                assertEquals(baseProjectileId, string(upgraded, "Parent"),
                        form + " must inherit its base projectile presentation");
            }
        }
    }

    @Test
    void elementalProjectileTalentRootsAreDistinctAndResolvable() throws IOException {
        for (String form : List.of("Nature", "Toxic", "Fire", "Void", "Lightning", "Ice")) {
            JsonObject role = load(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini",
                    "Tamed_Wyvern_Mini_" + form + ".json")).getAsJsonObject("Modify");
            List<String> roots = List.of(
                    string(role, "TalentProjectileBase"),
                    string(role, "TalentProjectileIntermediate"),
                    string(role, "TalentProjectileApex"));
            assertTrue(new HashSet<>(roots).size() == 3,
                    form + " must use distinct base, intermediate, and apex projectile roots");
            for (String rootId : roots) {
                Path rootPath = Path.of("Server", "Item", "RootInteractions", "NPCs", "HyDragon", "Wyvern_Mini",
                        rootId + ".json");
                assertTrue(Files.isRegularFile(rootPath), form + " root must exist: " + rootId);
                for (JsonElement interaction : load(rootPath).getAsJsonArray("Interactions")) {
                    Path interactionPath = Path.of("Server", "Item", "Interactions", "NPCs", "HyDragon",
                            "Wyvern_Mini", interaction.getAsString() + ".json");
                    assertTrue(Files.isRegularFile(interactionPath), rootId + " interaction must exist: " + interaction);
                }
            }
        }
    }

    private static boolean hasTalentGate(JsonElement value, String talentId) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("TameworkHasTalent".equals(string(object, "Type")) && talentId.equals(string(object, "TalentId"))) {
                return true;
            }
            for (JsonElement child : object.asMap().values()) {
                if (hasTalentGate(child, talentId)) return true;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (hasTalentGate(child, talentId)) return true;
        }
        return false;
    }

    private static boolean containsDefendStateInstruction(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("State".equals(string(object, "Type")) && "Defend".equals(string(object, "State"))) {
                return true;
            }
            for (JsonElement child : object.asMap().values()) {
                if (containsDefendStateInstruction(child)) return true;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                if (containsDefendStateInstruction(child)) return true;
            }
        }
        return false;
    }

    private static boolean hasExecutableAction(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Attack".equals(string(object, "Type")) || "ApplyEntityEffect".equals(string(object, "Type"))) return true;
            for (JsonElement child : object.asMap().values()) if (hasExecutableAction(child)) return true;
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (hasExecutableAction(child)) return true;
        }
        return false;
    }

    private static JsonObject instructionForTalent(JsonArray instructions, String talentId) {
        for (JsonElement element : instructions) {
            if (element.isJsonObject()
                    && hasExecutableAction(element)
                    && positiveTalentGates(element).contains(talentId)) {
                return element.getAsJsonObject();
            }
        }
        throw new AssertionError("missing executable gate for " + talentId);
    }

    private static JsonObject defendDispatch(JsonArray instructions) {
        return instructions.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(instruction -> instruction.has("Instructions")
                        && containsDefendStateInstruction(instruction.get("Instructions")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing Miniwyvern Defend state dispatch"));
    }

    private static int topLevelIndex(JsonArray instructions, JsonObject expected) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index) == expected) return index;
        }
        throw new AssertionError("instruction is not top-level");
    }

    private static JsonObject instructionUsingFlagAndMotion(
            JsonArray instructions, String flagName, String bodyMotionType) {
        for (JsonElement element : instructions) {
            if (!element.isJsonObject()) continue;
            JsonObject instruction = element.getAsJsonObject();
            if (hasPositiveFlagSensor(instruction.get("Sensor"), flagName)
                    && instruction.has("BodyMotion")
                    && bodyMotionType.equals(string(instruction.getAsJsonObject("BodyMotion"), "Type"))) {
                return instruction;
            }
        }
        throw new AssertionError("missing top-level " + bodyMotionType + " instruction for " + flagName);
    }

    private static JsonObject instructionWithAction(JsonArray instructions, String type, String name) {
        for (JsonElement element : instructions) {
            if (element.isJsonObject() && findAction(element, type, name) != null) return element.getAsJsonObject();
        }
        throw new AssertionError("missing top-level instruction with " + type + " action for " + name);
    }

    private static JsonObject action(JsonElement value, String type, String name) {
        JsonObject match = findAction(value, type, name);
        if (match == null) throw new AssertionError("missing " + type + " action for " + name);
        return match;
    }

    private static boolean hasAction(JsonElement value, String type, String name) {
        return findAction(value, type, name) != null;
    }

    private static JsonObject findAction(JsonElement value, String type, String name) {
        if (value == null) return null;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (type.equals(string(object, "Type")) && (name == null || name.equals(string(object, "Name")))) {
                return object;
            }
            for (JsonElement child : object.asMap().values()) {
                JsonObject match = findAction(child, type, name);
                if (match != null) return match;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                JsonObject match = findAction(child, type, name);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static boolean hasSetFlag(JsonElement value, String name, boolean setTo) {
        if (value == null) return false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("SetFlag".equals(string(object, "Type"))
                    && name.equals(string(object, "Name"))
                    && object.has("SetTo")
                    && object.get("SetTo").getAsBoolean() == setTo) {
                return true;
            }
            for (JsonElement child : object.asMap().values()) {
                if (hasSetFlag(child, name, setTo)) return true;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                if (hasSetFlag(child, name, setTo)) return true;
            }
        }
        return false;
    }

    private static boolean hasStoppedTimerSensor(JsonElement value, String name) {
        if (value == null) return false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Timer".equals(string(object, "Type"))
                    && name.equals(string(object, "Name"))
                    && "Stopped".equals(string(object, "State"))) {
                return true;
            }
            for (JsonElement child : object.asMap().values()) {
                if (hasStoppedTimerSensor(child, name)) return true;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                if (hasStoppedTimerSensor(child, name)) return true;
            }
        }
        return false;
    }

    private static boolean hasPositiveFlagSensor(JsonElement value, String name) {
        if (value == null) return false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Flag".equals(string(object, "Type"))
                    && name.equals(string(object, "Name"))
                    && (!object.has("Set") || object.get("Set").getAsBoolean())) {
                return true;
            }
            for (JsonElement child : object.asMap().values()) {
                if (hasPositiveFlagSensor(child, name)) return true;
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                if (hasPositiveFlagSensor(child, name)) return true;
            }
        }
        return false;
    }

    private static JsonObject projectileAttack(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Attack".equals(string(object, "Type")) && object.has("AttackPauseRange")) return object;
            for (JsonElement child : object.asMap().values()) {
                try {
                    return projectileAttack(child);
                } catch (IllegalArgumentException ignored) {
                    // Search the next child.
                }
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                try {
                    return projectileAttack(child);
                } catch (IllegalArgumentException ignored) {
                    // Search the next child.
                }
            }
        }
        throw new IllegalArgumentException("missing projectile Attack action");
    }

    private static Set<String> positiveTalentGates(JsonElement value) {
        Set<String> talentIds = new HashSet<>();
        collectTalentGates(value, false, talentIds);
        return talentIds;
    }

    private static Set<String> excludedTalents(JsonElement value) {
        Set<String> talentIds = new HashSet<>();
        collectExcludedTalentGates(value, false, talentIds);
        return talentIds;
    }

    private static void collectTalentGates(JsonElement value, boolean negated, Set<String> talentIds) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("TameworkHasTalent".equals(string(object, "Type"))) {
                if (!negated) talentIds.add(string(object, "TalentId"));
                return;
            }
            boolean childNegated = negated ^ "Not".equals(string(object, "Type"));
            for (JsonElement child : object.asMap().values()) collectTalentGates(child, childNegated, talentIds);
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) collectTalentGates(child, negated, talentIds);
        }
    }

    private static void collectExcludedTalentGates(JsonElement value, boolean negated, Set<String> talentIds) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("TameworkHasTalent".equals(string(object, "Type"))) {
                if (negated) talentIds.add(string(object, "TalentId"));
                return;
            }
            boolean childNegated = negated ^ "Not".equals(string(object, "Type"));
            for (JsonElement child : object.asMap().values()) collectExcludedTalentGates(child, childNegated, talentIds);
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) collectExcludedTalentGates(child, negated, talentIds);
        }
    }

    private static Path rootPath(String rootId) {
        return Path.of("Server", "Item", "RootInteractions", "NPCs", "HyDragon", "Wyvern_Mini", rootId + ".json");
    }

    private static Path interactionPath(String interactionId) {
        return Path.of("Server", "Item", "Interactions", "NPCs", "HyDragon", "Wyvern_Mini", interactionId + ".json");
    }

    private static Path projectilePath(String projectileId) {
        return Path.of("Server", "Projectiles", "HyDragon", "Wyvern_Mini", projectileId + ".json");
    }

    private static String projectileIdForRoot(String rootId) throws IOException {
        JsonArray interactions = load(rootPath(rootId)).getAsJsonArray("Interactions");
        assertEquals(1, interactions.size(), rootId + " must select exactly one projectile interaction");
        return string(load(interactionPath(interactions.get(0).getAsString())), "ProjectileId");
    }

    private static void assertRawOnly(JsonObject projectile, String description) {
        String text = projectile.toString().toLowerCase(Locale.ROOT);
        for (String forbidden : List.of("fire", "ice", "lightning", "nature", "toxic", "void")) {
            assertFalse(text.contains(forbidden), description + " must remain raw-only: " + forbidden);
        }
        assertNoEffectOrStatusFields(projectile, description);
    }

    private static void assertRawOnlyInteractionChain(
            String interactionId, String description, Set<String> visited) throws IOException {
        if (!visited.add(interactionId)) return;
        JsonObject interaction = load(interactionPath(interactionId));
        assertNoEffectOrStatusFields(interaction, description);
        assertRawOnlyInteractionProjectiles(interaction, description);
        collectReferencedInteractions(interaction, visited, description);
    }

    private static void assertRawOnlyInteractionProjectiles(
            JsonElement value, String description) throws IOException {
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().asMap().entrySet()) {
                JsonElement child = entry.getValue();
                if (entry.getKey().equals("ProjectileId")
                        && child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    String projectileId = child.getAsString();
                    Path projectile = projectilePath(projectileId);
                    assertTrue(Files.isRegularFile(projectile),
                            description + " must not launch an uninspected projectile: " + projectileId);
                    assertRawOnly(load(projectile), description + " projectile");
                } else {
                    assertRawOnlyInteractionProjectiles(child, description);
                }
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                assertRawOnlyInteractionProjectiles(child, description);
            }
        }
    }

    private static void collectReferencedInteractions(
            JsonElement value, Set<String> visited, String description) throws IOException {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String interactionId = value.getAsString();
            if (!interactionId.matches("[A-Za-z0-9_-]+")) return;
            Path interaction = interactionPath(interactionId);
            if (Files.isRegularFile(interaction)) {
                assertRawOnlyInteractionChain(interactionId, description + " chained interaction", visited);
            }
        } else if (value.isJsonObject()) {
            for (JsonElement child : value.getAsJsonObject().asMap().values()) {
                collectReferencedInteractions(child, visited, description);
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                collectReferencedInteractions(child, visited, description);
            }
        }
    }

    private static void assertNoEffectOrStatusFields(JsonElement value, String description) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            assertFalse("ApplyEffect".equals(string(object, "Type"))
                            || "ApplyEntityEffect".equals(string(object, "Type")),
                    description + " must not apply an effect/status action");
            for (Map.Entry<String, JsonElement> entry : object.asMap().entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                assertFalse(key.equals("effectid") || key.equals("entityeffectid")
                                || key.contains("status"),
                        description + " must not include elemental effect/status fields: " + entry.getKey());
                assertNoEffectOrStatusFields(entry.getValue(), description);
            }
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) assertNoEffectOrStatusFields(child, description);
        }
    }

    private static String string(JsonObject object, String name) {
        return object.has(name) ? object.get(name).getAsString() : "";
    }

    private static JsonObject load(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
