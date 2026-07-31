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
import org.junit.jupiter.api.Test;

/** Regression contract for the deterministic Miniwyvern aerial swoop cycle. */
final class MiniwyvernSwoopAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));

    @Test
    void aerialSwoopUsesDedicatedTimerPhasesAndExactDamageProfiles() throws IOException {
        JsonObject componentJson = load("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json");
        String component = componentJson.toString();
        assertTrue(component.contains("\"Name\":\"Miniwyvern_Swoop_Cooldown\""));
        assertTrue(component.contains("\"TalentId\":\"SwoopMastery\""));
        assertTrue(component.contains("\"StartValueRange\":[18,24]"));
        assertTrue(component.contains("\"TalentId\":\"RelentlessSwoop\""));
        assertTrue(component.contains("\"StartValueRange\":[20,26]"));
        assertTrue(component.contains("\"TalentId\":\"SwoopCadence\""));
        assertTrue(component.contains("\"StartValueRange\":[22,30]"));
        assertTrue(component.contains("\"StartValueRange\":[25,35]"));
        assertTrue(component.contains("\"Miniwyvern_Swoop_Approach\""));
        assertTrue(component.contains("\"SwoopApproachTimeout\":{\"Value\":[6,6]}"));
        assertTrue(component.contains("\"Miniwyvern_Swoop_Pending\""));
        assertTrue(component.contains("\"Miniwyvern_Swooping\""));
        assertTrue(component.contains("\"Miniwyvern_Swoop_Strike_Committed\""));
        assertTrue(component.contains("\"State\":\".Swoop\""));
        assertTrue(component.contains("\"State\":\".Recovery\""));
        assertTrue(component.contains("\"DesiredAltitudeRange\":{\"Compute\":\"SwoopAltitudeRange\"}"));
        assertTrue(component.contains("\"RelativeSpeed\":0.7"));
        assertTrue(component.contains("\"RelativeSpeed\":0.55"));
        assertFalse(component.contains("\"Type\":\"Random\""));

        for (int damage : new int[] {16, 20, 24, 28}) {
            String suffix = switch (damage) {
                case 16 -> "";
                case 20 -> "_Ferocity";
                case 24 -> "_Rending";
                default -> "_Mastery";
            };
            JsonObject asset = load("Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/"
                    + "Wyvern_Mini_Swoop_Bite_Damage" + suffix + ".json");
            assertEquals(damage, asset.getAsJsonObject("DamageCalculator")
                    .getAsJsonObject("BaseDamage").get("Physical").getAsInt());
            assertEquals(0, asset.getAsJsonObject("DamageCalculator")
                    .get("RandomPercentageModifier").getAsInt());
            assertFalse(asset.toString().contains("Knockback"));
            assertFalse(asset.toString().contains("EffectId"));
        }
    }

    @Test
    void swoopAttemptsRestartTheSelectedCooldownAndUseExclusiveAttackProfiles() throws IOException {
        JsonArray instructions = load("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json")
                .getAsJsonObject("Content").getAsJsonArray("Instructions");

        java.util.List<JsonObject> attempts = new java.util.ArrayList<>();
        collectCommittedSwoopAttempts(instructions, attempts);
        assertEquals(5, attempts.size(), "a swoop tick must expose one committed strike per exclusive profile");
        for (JsonObject attempt : attempts) {
            JsonArray actions = attempt.getAsJsonArray("Actions");
            int attack = actionIndex(actions, "Attack", null);
            assertEquals("SetFlag", type(actions.get(attack - 1)));
            assertEquals("Miniwyvern_Swoop_Strike_Committed", string(actions.get(attack - 1).getAsJsonObject(), "Name"));
            assertTrue(actionIndexAfter(actions, attack, "TimerRestart", "Miniwyvern_Swoop_Cooldown") >= 0,
                    "every committed strike must restart its expired cooldown");
            assertTrue(actionIndexAfter(actions, attack, "State", ".Recovery") >= 0,
                    "every committed strike must enter recovery");
        }

        assertExclusiveProfile(instructions, "SwoopMastery", "SwoopAttackMastery");
        assertExclusiveProfile(instructions, "RendingDive", "SwoopAttackRending", "SwoopMastery");
        assertExclusiveProfile(instructions, "SwoopFerocity", "SwoopAttackFerocity", "SwoopMastery", "RendingDive");
    }

    @Test
    void recoveryCancellationInitializationAndApproachOwnershipAreStructural() throws IOException {
        JsonObject component = load("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json");
        JsonArray instructions = component.getAsJsonObject("Content").getAsJsonArray("Instructions");
        java.util.List<JsonObject> recovery = new java.util.ArrayList<>();
        collectInstructionsWithState(instructions, ".Recovery", recovery);
        assertTrue(recovery.size() >= 2, "recovery start and completion must be sibling instructions");
        JsonObject entry = recovery.stream().filter(i -> actionIndex(i.getAsJsonArray("Actions"), "TimerStart", "Miniwyvern_Swoop_Recovery") >= 0)
                .findFirst().orElseThrow();
        assertFalse(entry.has("Instructions"), "recovery entry actions cannot share an instruction with children");
        assertTrue(actionIndex(entry.getAsJsonArray("Actions"), "SetFlag", "Miniwyvern_Swoop_Recovery_Started") >= 0);
        assertTrue(actionIndex(entry.getAsJsonArray("Actions"), "TimerRestart", "Miniwyvern_Swoop_Recovery") >= 0);
        JsonObject complete = recovery.stream().filter(i -> actionIndex(i.getAsJsonArray("Actions"), "State", ".Combat") >= 0)
                .findFirst().orElseThrow();
        assertTrue(hasPositiveFlagSensor(complete.get("Sensor"), "Miniwyvern_Swoop_Recovery_Started"));
        assertTrue(hasStoppedTimerSensor(complete.get("Sensor"), "Miniwyvern_Swoop_Recovery"));
        assertTrue(hasSetFlag(complete.get("Actions"), "Miniwyvern_Swoop_Recovery_Started", false));
        assertTrue(hasSetFlag(complete.get("Actions"), "Miniwyvern_Swooping", false));
        assertTrue(hasSetFlag(complete.get("Actions"), "Miniwyvern_Swoop_Strike_Committed", false));

        JsonObject template = load("Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json");
        JsonObject templateCancel = findCancellation(template.getAsJsonArray("Instructions"));
        assertEquals("And", type(templateCancel.get("Sensor")));
        assertTrue(hasSetFlag(templateCancel.get("Actions"), "Miniwyvern_Swoop_Recovery_Started", false));
        for (String timer : java.util.List.of("Miniwyvern_Swoop_Cooldown", "Miniwyvern_Swoop_Approach", "Miniwyvern_Swoop_Recovery", "Miniwyvern_Projectile_Aim", "Miniwyvern_Projectile_Cooldown"))
            assertTrue(actionIndex(templateCancel.getAsJsonArray("Actions"), "TimerStop", timer) >= 0);

        for (JsonObject claim : objectsWithAction(instructions, "TimerStart", "Miniwyvern_Swoop_Approach"))
            assertTrue(actionIndex(claim.getAsJsonArray("Actions"), "TimerRestart", "Miniwyvern_Swoop_Approach") >= 0);
        assertTrue(objectsWithMotion(instructions, 0.7, true).size() >= 1);
        assertTrue(objectsWithMotion(instructions, 0.55, false).size() >= 1);
    }

    @Test
    void templateCancellationOwnsEveryInvalidContextAndActiveLifecycleCleanup() throws IOException {
        JsonObject template = load("Server/NPC/Roles/Creature/HyDragon/Templates/"
                + "Template_Wyvern_Mini_Flying_Tamed.json");
        JsonObject cancellation = findCancellation(template.getAsJsonArray("Instructions"));
        JsonArray cancellationTerms = cancellation.getAsJsonObject("Sensor").getAsJsonArray("Sensors");
        JsonObject invalidContext = cancellationTerms.get(0).getAsJsonObject();
        assertEquals("Or", type(invalidContext));
        assertTrue(hasNegatedState(invalidContext, "Defend"));
        assertTrue(hasNegatedTarget(invalidContext, "LockedTarget"));
        assertTrue(hasFalseFlagSensor(invalidContext, "AirborneMode"));
        assertTrue(hasNegatedMotionController(invalidContext, "Fly"));

        JsonObject activeLifecycle = cancellationTerms.get(1).getAsJsonObject();
        assertEquals("Or", type(activeLifecycle));
        for (String flag : java.util.List.of(
                "Miniwyvern_Aerial_Combat_Active",
                "Miniwyvern_Swoop_Pending",
                "Miniwyvern_Swooping",
                "Miniwyvern_Swoop_Strike_Committed",
                "Miniwyvern_Swoop_Recovery_Started",
                "Miniwyvern_Projectile_Aiming")) {
            assertTrue(hasPositiveFlagSensor(activeLifecycle, flag), "missing active lifecycle flag " + flag);
            assertTrue(hasSetFlag(cancellation.get("Actions"), flag, false), "cleanup must clear " + flag);
        }
        for (String timer : java.util.List.of(
                "Miniwyvern_Swoop_Cooldown",
                "Miniwyvern_Swoop_Approach",
                "Miniwyvern_Swoop_Recovery",
                "Miniwyvern_Projectile_Aim",
                "Miniwyvern_Projectile_Cooldown")) {
            assertTrue(actionIndex(cancellation.getAsJsonArray("Actions"), "TimerStop", timer) >= 0,
                    "cleanup must stop " + timer);
        }
    }

    @Test
    void combatEntryCooldownProfilesAreExclusiveExactAndRestartSafe() throws IOException {
        JsonArray instructions = load("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json")
                .getAsJsonObject("Content").getAsJsonArray("Instructions");
        java.util.List<JsonObject> entries = new java.util.ArrayList<>();
        int lastEntryIndex = -1;
        JsonObject readiness = null;
        int readinessIndex = -1;
        for (int index = 0; index < instructions.size(); index++) {
            JsonObject candidate = instructions.get(index).getAsJsonObject();
            if (hasStateSensor(candidate.get("Sensor"), ".Combat")
                    && hasStoppedTimerSensor(candidate.get("Sensor"), "Miniwyvern_Swoop_Cooldown")
                    && actionIndex(candidate.getAsJsonArray("Actions"), "TimerStart", "Miniwyvern_Swoop_Cooldown") >= 0
                    && actionIndex(candidate.getAsJsonArray("Actions"), "TimerRestart", "Miniwyvern_Swoop_Cooldown") >= 0) {
                entries.add(candidate);
                lastEntryIndex = index;
            }
            if (hasStateSensor(candidate.get("Sensor"), ".Combat")
                    && hasStoppedTimerSensor(candidate.get("Sensor"), "Miniwyvern_Swoop_Cooldown")
                    && hasSetFlag(candidate.get("Actions"), "Miniwyvern_Swoop_Pending", true)) {
                readiness = candidate;
                readinessIndex = index;
            }
        }
        assertEquals(4, entries.size(), "exactly four mutually exclusive cooldown-entry profiles are required");
        assertCooldownEntry(entries, 18, 24, "SwoopMastery");
        assertCooldownEntry(entries, 20, 26, "RelentlessSwoop", "SwoopMastery");
        assertCooldownEntry(entries, 22, 30, "SwoopCadence", "SwoopMastery", "RelentlessSwoop");
        assertCooldownEntry(entries, 25, 35, null, "SwoopMastery", "RelentlessSwoop", "SwoopCadence");
        assertTrue(readiness != null && readinessIndex > lastEntryIndex,
                "cooldown readiness must remain after every combat-entry initializer");
        assertTrue(hasDirectFlagTerm(readiness.get("Sensor"), "Miniwyvern_Aerial_Combat_Active", true),
                "ordinary in-combat cooldown expiry must be consumed by readiness");
    }

    @Test
    void everySwoopDamageChainRemainsFreeOfControlAndImmunityEffects() throws IOException {
        for (String suffix : java.util.List.of("", "_Ferocity", "_Rending", "_Mastery")) {
            JsonObject damage = load("Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/"
                    + "Wyvern_Mini_Swoop_Bite_Damage" + suffix + ".json");
            for (String prohibited : java.util.List.of(
                    "knockback", "force", "impact", "launch", "stun", "invulnerability")) {
                assertFalse(containsForbiddenGameplayMechanic(damage, prohibited),
                        "swoop damage " + suffix + " must not add " + prohibited + " mechanics");
            }
        }
    }

    private static JsonObject findCancellation(JsonArray instructions) {
        return instructions.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(i -> i.has("Actions") && actionIndex(i.getAsJsonArray("Actions"), "ResetInstructions", null) >= 0)
                .findFirst().orElseThrow();
    }
    private static void assertCooldownEntry(java.util.List<JsonObject> entries, int minimum, int maximum,
            String requiredTalent, String... excludedTalents) {
        JsonObject entry = entries.stream()
                .filter(candidate -> requiredTalent == null
                        ? java.util.Arrays.stream(excludedTalents).allMatch(talent -> hasNegatedTalent(candidate.get("Sensor"), talent))
                        : hasPositiveTalent(candidate.get("Sensor"), requiredTalent))
                .findFirst().orElseThrow(() -> new AssertionError("missing cooldown profile " + minimum + "-" + maximum));
        for (String excluded : excludedTalents) {
            assertTrue(hasNegatedTalent(entry.get("Sensor"), excluded), "profile must exclude " + excluded);
        }
        assertTrue(hasDirectFlagTerm(entry.get("Sensor"), "Miniwyvern_Aerial_Combat_Active", false),
                "combat entry must require an inactive lifecycle");
        JsonArray actions = entry.getAsJsonArray("Actions");
        int activate = actionIndex(actions, "SetFlag", "Miniwyvern_Aerial_Combat_Active");
        int start = actionIndex(actions, "TimerStart", "Miniwyvern_Swoop_Cooldown");
        int restart = actionIndex(actions, "TimerRestart", "Miniwyvern_Swoop_Cooldown");
        assertTrue(activate >= 0 && actions.get(activate).getAsJsonObject().get("SetTo").getAsBoolean(),
                "combat entry must activate its lifecycle");
        assertTrue(activate < start && restart > start,
                "combat entry must activate before TimerStart then TimerRestart");
        JsonArray range = actions.get(start).getAsJsonObject().getAsJsonArray("StartValueRange");
        assertEquals(minimum, range.get(0).getAsInt());
        assertEquals(maximum, range.get(1).getAsInt());
        JsonArray restartRange = actions.get(start).getAsJsonObject().getAsJsonArray("RestartValueRange");
        assertEquals(minimum, restartRange.get(0).getAsInt());
        assertEquals(maximum, restartRange.get(1).getAsInt());
    }
    private static boolean hasDirectFlagTerm(JsonElement value, String name, boolean set) {
        if (value == null || !value.isJsonObject()) return false;
        JsonObject sensor = value.getAsJsonObject();
        if ("Flag".equals(type(sensor))) {
            return name.equals(string(sensor, "Name"))
                    && sensor.has("Set")
                    && sensor.get("Set").getAsBoolean() == set;
        }
        if (!"And".equals(type(sensor)) || !sensor.has("Sensors")) return false;
        for (JsonElement term : sensor.getAsJsonArray("Sensors")) {
            if (term.isJsonObject() && hasDirectFlagTerm(term, name, set)) return true;
        }
        return false;
    }
    private static boolean hasNegatedState(JsonElement value, String state) {
        if (value == null) return false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Not".equals(type(object)) && hasStateSensor(object.get("Sensor"), state)) return true;
            for (JsonElement child : object.asMap().values()) if (hasNegatedState(child, state)) return true;
        } else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) if (hasNegatedState(child, state)) return true;
        return false;
    }
    private static boolean hasNegatedTarget(JsonElement value, String targetSlot) {
        if (value == null) return false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Not".equals(type(object)) && hasTargetSensor(object.get("Sensor"), targetSlot)) return true;
            for (JsonElement child : object.asMap().values()) if (hasNegatedTarget(child, targetSlot)) return true;
        } else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) if (hasNegatedTarget(child, targetSlot)) return true;
        return false;
    }
    private static boolean hasTargetSensor(JsonElement value, String targetSlot) {
        return value != null && value.isJsonObject() && "Target".equals(type(value))
                && targetSlot.equals(string(value.getAsJsonObject(), "TargetSlot"));
    }
    private static boolean hasFalseFlagSensor(JsonElement value, String name) {
        if (value == null) return false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Flag".equals(type(object)) && name.equals(string(object, "Name"))
                    && object.has("Set") && !object.get("Set").getAsBoolean()) return true;
            for (JsonElement child : object.asMap().values()) if (hasFalseFlagSensor(child, name)) return true;
        } else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) if (hasFalseFlagSensor(child, name)) return true;
        return false;
    }
    private static boolean hasNegatedMotionController(JsonElement value, String controller) {
        if (value == null) return false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            JsonElement sensor = object.get("Sensor");
            if ("Not".equals(type(object)) && sensor != null && sensor.isJsonObject()
                    && "MotionController".equals(type(sensor))
                    && controller.equals(string(sensor.getAsJsonObject(), "MotionController"))) return true;
            for (JsonElement child : object.asMap().values()) if (hasNegatedMotionController(child, controller)) return true;
        } else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) if (hasNegatedMotionController(child, controller)) return true;
        return false;
    }
    private static boolean containsForbiddenGameplayMechanic(JsonElement value, String prohibited) {
        if (value.isJsonObject()) {
            for (java.util.Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                if (key.toLowerCase(java.util.Locale.ROOT).contains(prohibited)) return true;
                if (isPresentationOnlyField(key)) continue;
                JsonElement child = entry.getValue();
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()
                        && ("Type".equals(key) || isGameplayEffectIdentifier(key))
                        && child.getAsString().toLowerCase(java.util.Locale.ROOT).contains(prohibited)) return true;
                if (containsForbiddenGameplayMechanic(child, prohibited)) return true;
            }
        } else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray())
            if (containsForbiddenGameplayMechanic(child, prohibited)) return true;
        return false;
    }
    private static boolean isPresentationOnlyField(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("sound") || normalized.contains("particle") || normalized.equals("systemid")
                || normalized.equals("visualeffectid");
    }
    private static boolean isGameplayEffectIdentifier(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("effectid") || normalized.equals("statusid") || normalized.equals("mechanicid")
                || normalized.equals("interactionid") || normalized.equals("actionid");
    }
    private static void collectInstructionsWithState(JsonElement value, String state, java.util.List<JsonObject> out) {
        if (value.isJsonObject()) { JsonObject o = value.getAsJsonObject(); if (hasStateSensor(o.get("Sensor"), state)) out.add(o); for (JsonElement c : o.asMap().values()) collectInstructionsWithState(c, state, out); }
        else if (value.isJsonArray()) for (JsonElement c : value.getAsJsonArray()) collectInstructionsWithState(c, state, out);
    }
    private static boolean hasStateSensor(JsonElement value, String state) {
        if (value == null) return false; if (value.isJsonObject()) { JsonObject o=value.getAsJsonObject(); if ("State".equals(type(o)) && state.equals(string(o,"State"))) return true; for(JsonElement c:o.asMap().values()) if(hasStateSensor(c,state)) return true; } else if(value.isJsonArray()) for(JsonElement c:value.getAsJsonArray()) if(hasStateSensor(c,state)) return true; return false;
    }
    private static java.util.List<JsonObject> objectsWithAction(JsonElement value, String action, String name) { java.util.List<JsonObject> out=new java.util.ArrayList<>(); collectActionOwners(value,action,name,out); return out; }
    private static void collectActionOwners(JsonElement v,String a,String n,java.util.List<JsonObject> out){ if(v.isJsonObject()){JsonObject o=v.getAsJsonObject();if(actionIndex(o.getAsJsonArray("Actions"),a,n)>=0)out.add(o);for(JsonElement c:o.asMap().values())collectActionOwners(c,a,n,out);}else if(v.isJsonArray())for(JsonElement c:v.getAsJsonArray())collectActionOwners(c,a,n,out); }
    private static java.util.List<JsonObject> objectsWithMotion(JsonElement v,double speed,boolean precision){ java.util.List<JsonObject> out=new java.util.ArrayList<>(); if(v.isJsonObject()){JsonObject o=v.getAsJsonObject();boolean profile=precision?hasTalent(o.get("Sensor"),"SwoopPrecision"):hasNegatedTalent(o.get("Sensor"),"SwoopPrecision");if(o.has("BodyMotion")&&o.getAsJsonObject("BodyMotion").has("RelativeSpeed")&&o.getAsJsonObject("BodyMotion").get("RelativeSpeed").isJsonPrimitive()&&o.getAsJsonObject("BodyMotion").get("RelativeSpeed").getAsDouble()==speed&&profile)out.add(o);for(JsonElement c:o.asMap().values())out.addAll(objectsWithMotion(c,speed,precision));}else if(v.isJsonArray())for(JsonElement c:v.getAsJsonArray())out.addAll(objectsWithMotion(c,speed,precision));return out; }

    private static void assertExclusiveProfile(
            JsonArray instructions, String talent, String attackParameter, String... excluded) {
        JsonObject instruction = topLevelWithTalentAndAttack(instructions, talent, attackParameter);
        for (String excludedTalent : excluded) assertTrue(hasNegatedTalent(instruction.get("Sensor"), excludedTalent));
    }

    private static void assertDefaultProfile(JsonArray instructions, String attackParameter, String... excluded) {
        JsonObject instruction = topLevelWithAttackParameter(instructions, attackParameter);
        for (String excludedTalent : excluded) assertTrue(hasNegatedTalent(instruction.get("Sensor"), excludedTalent));
    }

    private static JsonObject topLevelWithTalentAndAttack(JsonArray instructions, String talent, String attackParameter) {
        for (JsonElement element : instructions) {
            JsonObject match = instructionWithTalentAndAttack(element, talent, attackParameter);
            if (match != null) return match;
        }
        throw new AssertionError("missing swoop profile " + talent + " -> " + attackParameter);
    }

    private static JsonObject topLevelWithAttackParameter(JsonArray instructions, String attackParameter) {
        for (JsonElement element : instructions) {
            JsonObject match = instructionWithAttackParameter(element, attackParameter);
            if (match != null) return match;
        }
        throw new AssertionError("missing default swoop profile " + attackParameter);
    }

    private static JsonObject instructionWithTalentAndAttack(JsonElement value, String talent, String attackParameter) {
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                JsonObject match = instructionWithTalentAndAttack(child, talent, attackParameter);
                if (match != null) return match;
            }
            return null;
        }
        if (!value.isJsonObject()) return null;
        JsonObject object = value.getAsJsonObject();
        if (hasTalent(object.get("Sensor"), talent) && hasAttackParameter(object, attackParameter)) return object;
        for (JsonElement child : object.asMap().values()) {
            JsonObject match = instructionWithTalentAndAttack(child, talent, attackParameter);
            if (match != null) return match;
        }
        return null;
    }

    private static JsonObject instructionWithAttackParameter(JsonElement value, String attackParameter) {
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                JsonObject match = instructionWithAttackParameter(child, attackParameter);
                if (match != null) return match;
            }
            return null;
        }
        if (!value.isJsonObject()) return null;
        JsonObject object = value.getAsJsonObject();
        if (hasAttackParameter(object, attackParameter)) return object;
        for (JsonElement child : object.asMap().values()) {
            JsonObject match = instructionWithAttackParameter(child, attackParameter);
            if (match != null) return match;
        }
        return null;
    }

    private static void collectCommittedSwoopAttempts(JsonElement value, java.util.List<JsonObject> matches) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            JsonArray actions = object.getAsJsonArray("Actions");
            if (actionIndex(actions, "Attack", null) >= 0 && actionIndex(actions, "SetFlag", "Miniwyvern_Swoop_Strike_Committed") >= 0) {
                matches.add(object);
            }
            for (JsonElement child : object.asMap().values()) collectCommittedSwoopAttempts(child, matches);
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) collectCommittedSwoopAttempts(child, matches);
        }
    }

    private static int actionIndex(JsonArray actions, String actionType, String name) {
        if (actions == null) return -1;
        for (int index = 0; index < actions.size(); index++) {
            if (actionType.equals(type(actions.get(index))) && (name == null || name.equals(string(actions.get(index).getAsJsonObject(), "Name")) || name.equals(string(actions.get(index).getAsJsonObject(), "State")))) return index;
        }
        return -1;
    }

    private static int actionIndexAfter(JsonArray actions, int after, String actionType, String name) {
        for (int index = after + 1; index < actions.size(); index++) {
            if (actionType.equals(type(actions.get(index))) && name.equals(string(actions.get(index).getAsJsonObject(), "Name")) || actionType.equals(type(actions.get(index))) && name.equals(string(actions.get(index).getAsJsonObject(), "State"))) return index;
        }
        return -1;
    }

    private static boolean hasAttackParameter(JsonElement value, String parameter) {
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (hasAttackParameter(child, parameter)) return true;
            return false;
        }
        if (!value.isJsonObject()) return false;
        JsonObject object = value.getAsJsonObject();
        if ("Attack".equals(type(object)) && object.has("Attack")
                && parameter.equals(string(object.getAsJsonObject("Attack"), "Compute"))) return true;
        for (JsonElement child : object.asMap().values()) if (hasAttackParameter(child, parameter)) return true;
        return false;
    }

    private static boolean hasTalent(JsonElement value, String talent) {
        if (value == null) return false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("TameworkHasTalent".equals(type(object)) && talent.equals(string(object, "TalentId"))) return true;
            for (JsonElement child : object.asMap().values()) if (hasTalent(child, talent)) return true;
        } else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) if (hasTalent(child, talent)) return true;
        return false;
    }

    private static boolean hasPositiveTalent(JsonElement value, String talent) {
        if (value == null) return false;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Not".equals(type(object))) return false;
            if ("TameworkHasTalent".equals(type(object)) && talent.equals(string(object, "TalentId"))) return true;
            for (JsonElement child : object.asMap().values()) if (hasPositiveTalent(child, talent)) return true;
        } else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) if (hasPositiveTalent(child, talent)) return true;
        return false;
    }

    private static boolean hasNegatedTalent(JsonElement value, String talent) {
        if (value == null) return false;
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (hasNegatedTalent(child, talent)) return true;
            return false;
        }
        if (!value.isJsonObject()) return false;
        JsonObject object = value.getAsJsonObject();
        if ("Not".equals(type(object)) && hasTalent(object.get("Sensor"), talent)) return true;
        for (JsonElement child : object.asMap().values()) if (hasNegatedTalent(child, talent)) return true;
        return false;
    }

    private static boolean hasPositiveFlagSensor(JsonElement value, String name) {
        if (value == null) return false; if (value.isJsonObject()) { JsonObject o=value.getAsJsonObject(); if ("Flag".equals(type(o)) && name.equals(string(o,"Name")) && (!o.has("Set") || o.get("Set").getAsBoolean())) return true; for(JsonElement c:o.asMap().values())if(hasPositiveFlagSensor(c,name))return true; } else if(value.isJsonArray())for(JsonElement c:value.getAsJsonArray())if(hasPositiveFlagSensor(c,name))return true; return false;
    }
    private static boolean hasStoppedTimerSensor(JsonElement value, String name) {
        if (value == null) return false; if(value.isJsonObject()){JsonObject o=value.getAsJsonObject();if("Timer".equals(type(o))&&name.equals(string(o,"Name"))&&"Stopped".equals(string(o,"State")))return true;for(JsonElement c:o.asMap().values())if(hasStoppedTimerSensor(c,name))return true;}else if(value.isJsonArray())for(JsonElement c:value.getAsJsonArray())if(hasStoppedTimerSensor(c,name))return true;return false;
    }
    private static boolean hasSetFlag(JsonElement value, String name, boolean setTo) {
        if(value==null)return false;if(value.isJsonObject()){JsonObject o=value.getAsJsonObject();if("SetFlag".equals(type(o))&&name.equals(string(o,"Name"))&&o.has("SetTo")&&o.get("SetTo").getAsBoolean()==setTo)return true;for(JsonElement c:o.asMap().values())if(hasSetFlag(c,name,setTo))return true;}else if(value.isJsonArray())for(JsonElement c:value.getAsJsonArray())if(hasSetFlag(c,name,setTo))return true;return false;
    }

    private static String type(JsonElement value) { return value != null && value.isJsonObject() ? string(value.getAsJsonObject(), "Type") : ""; }

    private static String string(JsonObject object, String name) { return object.has(name) ? object.get(name).getAsString() : ""; }

    private static JsonObject load(String relativePath) throws IOException {
        return JsonParser.parseString(read(relativePath)).getAsJsonObject();
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
