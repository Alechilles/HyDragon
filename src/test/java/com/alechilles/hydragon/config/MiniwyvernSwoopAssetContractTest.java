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

    private static String type(JsonElement value) { return value != null && value.isJsonObject() ? string(value.getAsJsonObject(), "Type") : ""; }

    private static String string(JsonObject object, String name) { return object.has(name) ? object.get(name).getAsString() : ""; }

    private static JsonObject load(String relativePath) throws IOException {
        return JsonParser.parseString(read(relativePath)).getAsJsonObject();
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
