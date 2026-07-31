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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Contract for Miniwyvern's highest-observable projectile talent profile. */
final class MiniwyvernTalentAssetWiringTest {
    private static final Path COMPONENT = Path.of("Server", "NPC", "Roles", "Creature", "HyDragon",
            "Components", "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json");
    private static final Path TEMPLATE = Path.of("Server", "NPC", "Roles", "Creature", "HyDragon",
            "Templates", "Template_Wyvern_Mini_Flying_Tamed.json");
    private static final List<String> TALENTS = List.of("DraconicProjectile", "ProjectileRange", "ProjectileCadence",
            "ProjectileGuidance", "ProjectilePattern", "ProjectileMastery");
    private static final List<String> FORMS = List.of("Fire", "Ice", "Lightning", "Nature", "Toxic", "Void", "Wild");
    private static final String AIMING = "Miniwyvern_Projectile_Aiming";
    private static final String VOLLEY = "Miniwyvern_Projectile_Volley_Active";

    @Test
    void sixMilestonesResolveToTheHighestObservableProfile() throws IOException {
        JsonArray instructions = instructions();
        assertProfile(instructions, "DraconicProjectile", "TalentProjectileBase", "[0.4,0.7]", "[5,7]", false,
                "ProjectileRange", "ProjectileCadence", "ProjectileGuidance", "ProjectilePattern", "ProjectileMastery");
        assertProfile(instructions, "ProjectileRange", "TalentProjectileIntermediate", "[0.4,0.7]", "[5,7]", false,
                "ProjectileCadence", "ProjectileGuidance", "ProjectilePattern", "ProjectileMastery");
        assertProfile(instructions, "ProjectileCadence", "TalentProjectileIntermediate", "[0.4,0.7]", "[4,6]", false,
                "ProjectileGuidance", "ProjectilePattern", "ProjectileMastery");
        assertProfile(instructions, "ProjectileGuidance", "TalentProjectileIntermediate", "[0.55,0.85]", "[5,7]", false,
                "ProjectileCadence", "ProjectilePattern", "ProjectileMastery");
        assertProfile(instructions, "ProjectilePattern", "TalentProjectilePattern", "[0.4,0.7]", "[4,6]", true,
                "ProjectileGuidance", "ProjectileMastery");
        assertProfile(instructions, "ProjectilePattern", "TalentProjectilePattern", "[0.55,0.85]", "[4,6]", true,
                "ProjectileMastery");
        assertProfile(instructions, "ProjectileMastery", "TalentProjectileMastery", "[0.55,0.85]", "[3,5]", true);
    }

    @Test
    void onlyTheSixProjectileMilestonesSelectProjectileBranches() throws IOException {
        String source = Files.readString(COMPONENT);
        for (String stale : List.of("ProjectileForce", "ProjectileImpact", "DraconicAssault", "AssaultUtility",
                "AssaultMastery", "DraconicApex", "TalentProjectileApex")) {
            assertFalse(source.contains(stale), "obsolete projectile branch remains: " + stale);
        }
        for (JsonObject branch : projectileBranches(instructions())) {
            assertTrue(TALENTS.containsAll(talentIds(branch.get("Sensor"))), "branch has only the six milestones");
            assertRejectsSwoop(branch, "every executable branch must yield to swoop lifecycle");
        }
    }

    @Test
    void schedulerIsCentralizedAfterSwoopReadinessAndYieldsToSwoops() throws IOException {
        JsonObject template = load(TEMPLATE);
        assertFalse(containsTimerStart(template, "Miniwyvern_Projectile_Aim"),
                "template must not own a projectile readiness scheduler");
        JsonArray instructions = instructions();
        int readiness = indexWithSetFlag(instructions, "Miniwyvern_Swoop_Pending", true);
        int scheduler = indexWithAction(instructions, "TimerStart", "Miniwyvern_Projectile_Aim");
        assertTrue(readiness >= 0 && scheduler > readiness, "scheduler must follow swoop-pending setter");
        JsonObject schedulerInstruction = instructions.get(scheduler).getAsJsonObject();
        assertRejectsSwoop(schedulerInstruction, "scheduler must yield to swoop lifecycle");
    }

    @Test
    void volleyLatchesBeforeSerialAttackThenReleasesOnlyAfterCooldownRestart() throws IOException {
        for (JsonObject branch : projectileBranches(instructions())) {
            if (!List.of("TalentProjectilePattern", "TalentProjectileMastery").contains(attackCompute(branch))) continue;
            JsonArray actions = branch.getAsJsonArray("Actions");
            int volleyOn = actionIndex(actions, "SetFlag", VOLLEY, true);
            int attack = actionIndex(actions, "Attack", null, false);
            int restart = actionIndex(actions, "TimerRestart", "Miniwyvern_Projectile_Cooldown", false);
            int volleyOff = actionIndex(actions, "SetFlag", VOLLEY, false);
            int aimingOff = actionIndex(actions, "SetFlag", AIMING, false);
            assertTrue(volleyOn >= 0 && volleyOn < attack, "volley must latch before blocking serial attack");
            assertTrue(restart > attack && volleyOff > restart && aimingOff > volleyOff,
                    "volley must cover serial attack and release after exact cooldown restart");
        }
    }

    @Test
    void templateAndEveryFormBindExactlyFourModernRoots() throws IOException {
        JsonObject parameters = load(TEMPLATE).getAsJsonObject("Parameters");
        Set<String> expected = Set.of("TalentProjectileBase", "TalentProjectileIntermediate",
                "TalentProjectilePattern", "TalentProjectileMastery");
        assertTrue(parameters.keySet().containsAll(expected));
        assertFalse(parameters.has("TalentProjectileApex"));
        for (String form : FORMS) {
            JsonObject modify = load(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini",
                    "Tamed_Wyvern_Mini_" + form + ".json")).getAsJsonObject("Modify");
            assertEquals(expected, modify.keySet().stream().filter(expected::contains).collect(java.util.stream.Collectors.toSet()));
            for (String parameter : expected) {
                String root = modify.get(parameter).getAsString();
                assertTrue(root.endsWith("_" + parameter.substring("TalentProjectile".length())), root);
                assertTrue(Files.isRegularFile(Path.of("Server", "Item", "RootInteractions", "NPCs", "HyDragon",
                        "Wyvern_Mini", root + ".json")), form + " root is missing: " + root);
            }
            assertFalse(modify.has("TalentProjectileApex"));
        }
    }

    private static void assertProfile(JsonArray instructions, String requiredTalent, String root, String aim,
            String cooldown, boolean volley, String... forbiddenTalents) {
        JsonObject branch = projectileBranches(instructions).stream()
                .filter(candidate -> hasTalent(candidate.get("Sensor"), requiredTalent))
                .filter(candidate -> root.equals(attackCompute(candidate)))
                .filter(candidate -> java.util.Arrays.stream(forbiddenTalents).allMatch(talent -> hasNegatedTalent(candidate.get("Sensor"), talent)))
                .findFirst().orElseThrow(() -> new AssertionError("missing " + requiredTalent + " profile " + root));
        assertEquals(JsonParser.parseString(aim), aimRangeForBranch(instructions, branch));
        assertEquals(JsonParser.parseString(cooldown), cooldownRange(branch));
        assertEquals(volley, actionIndex(branch.getAsJsonArray("Actions"), "SetFlag", VOLLEY, true) >= 0);
    }

    private static JsonElement aimRangeForBranch(JsonArray instructions, JsonObject branch) {
        boolean guided = hasPositiveTalent(branch.get("Sensor"), "ProjectileGuidance")
                || hasPositiveTalent(branch.get("Sensor"), "ProjectileMastery");
        return instructions.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(i -> actionIndex(i.getAsJsonArray("Actions"), "TimerStart", "Miniwyvern_Projectile_Aim", false) >= 0)
                .filter(i -> guided == (hasPositiveTalent(i.get("Sensor"), "ProjectileGuidance")
                        || hasPositiveTalent(i.get("Sensor"), "ProjectileMastery")))
                .findFirst().orElseThrow().getAsJsonArray("Actions").get(0).getAsJsonObject().get("StartValueRange");
    }

    private static JsonElement cooldownRange(JsonObject branch) {
        JsonObject start = action(branch.getAsJsonArray("Actions"), "TimerStart", "Miniwyvern_Projectile_Cooldown");
        assertEquals(start.get("StartValueRange"), start.get("RestartValueRange"));
        return start.get("StartValueRange");
    }

    private static List<JsonObject> projectileBranches(JsonArray instructions) {
        return instructions.asList().stream().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .filter(i -> actionIndex(i.getAsJsonArray("Actions"), "Attack", null, false) >= 0)
                .filter(i -> attackCompute(i).startsWith("TalentProjectile")).toList();
    }

    private static String attackCompute(JsonObject instruction) {
        for (JsonElement action : instruction.getAsJsonArray("Actions")) if ("Attack".equals(type(action)))
            return action.getAsJsonObject().getAsJsonObject("Attack").get("Compute").getAsString();
        return "";
    }

    private static void assertRejectsSwoop(JsonObject instruction, String message) {
        assertTrue(hasFalseFlag(instruction.get("Sensor"), "Miniwyvern_Swoop_Pending"), message + " pending");
        assertTrue(hasFalseFlag(instruction.get("Sensor"), "Miniwyvern_Swooping"), message + " active");
    }

    private static int indexWithSetFlag(JsonArray instructions, String name, boolean value) {
        for (int i = 0; i < instructions.size(); i++) if (actionIndex(instructions.get(i).getAsJsonObject().getAsJsonArray("Actions"), "SetFlag", name, value) >= 0) return i;
        return -1;
    }
    private static int indexWithAction(JsonArray instructions, String type, String name) {
        for (int i = 0; i < instructions.size(); i++) if (actionIndex(instructions.get(i).getAsJsonObject().getAsJsonArray("Actions"), type, name, false) >= 0) return i;
        return -1;
    }
    private static JsonArray instructions() throws IOException { return load(COMPONENT).getAsJsonObject("Content").getAsJsonArray("Instructions"); }
    private static JsonObject load(Path path) throws IOException { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static boolean containsTimerStart(JsonElement value, String name) { return contains(value, o -> "TimerStart".equals(string(o, "Type")) && name.equals(string(o, "Name"))); }
    private static JsonObject action(JsonArray actions, String type, String name) { return actions.asList().stream().map(JsonElement::getAsJsonObject).filter(a -> type.equals(string(a, "Type")) && (name == null || name.equals(string(a, "Name")))).findFirst().orElseThrow(); }
    private static int actionIndex(JsonArray actions, String type, String name, boolean set) { if (actions == null) return -1; for (int i=0;i<actions.size();i++) { JsonObject a=actions.get(i).getAsJsonObject(); if(type.equals(string(a,"Type")) && (name==null || name.equals(string(a,"Name"))) && (!"SetFlag".equals(type) || a.get("SetTo").getAsBoolean()==set)) return i; } return -1; }
    private static boolean hasTalent(JsonElement value, String talent) { return contains(value, o -> "TameworkHasTalent".equals(string(o,"Type")) && talent.equals(string(o,"TalentId"))); }
    private static boolean hasPositiveTalent(JsonElement value, String talent) { return containsPositiveTalent(value, talent, false); }
    private static boolean containsPositiveTalent(JsonElement value, String talent, boolean negated) { if(value==null)return false; if(value.isJsonObject()){JsonObject o=value.getAsJsonObject(); if("TameworkHasTalent".equals(string(o,"Type"))) return !negated && talent.equals(string(o,"TalentId")); boolean childNegated=negated ^ "Not".equals(string(o,"Type")); for(JsonElement c:o.asMap().values())if(containsPositiveTalent(c,talent,childNegated))return true;}else if(value.isJsonArray())for(JsonElement c:value.getAsJsonArray())if(containsPositiveTalent(c,talent,negated))return true;return false; }
    private static boolean hasNegatedTalent(JsonElement value, String talent) { return contains(value, o -> "Not".equals(string(o,"Type")) && hasTalent(o.get("Sensor"),talent)); }
    private static boolean hasFalseFlag(JsonElement value, String flag) { return contains(value, o -> "Flag".equals(string(o,"Type")) && flag.equals(string(o,"Name")) && o.has("Set") && !o.get("Set").getAsBoolean()); }
    private static boolean contains(JsonElement value, java.util.function.Predicate<JsonObject> predicate) { if(value==null)return false; if(value.isJsonObject()){JsonObject o=value.getAsJsonObject();if(predicate.test(o))return true;for(JsonElement c:o.asMap().values())if(contains(c,predicate))return true;}else if(value.isJsonArray())for(JsonElement c:value.getAsJsonArray())if(contains(c,predicate))return true;return false; }
    private static List<String> talentIds(JsonElement value) { java.util.ArrayList<String> ids=new java.util.ArrayList<>(); collectTalents(value,ids); return ids; }
    private static void collectTalents(JsonElement value, List<String> ids) { if(value==null)return; if(value.isJsonObject()){JsonObject o=value.getAsJsonObject();if("TameworkHasTalent".equals(string(o,"Type")))ids.add(string(o,"TalentId"));for(JsonElement c:o.asMap().values())collectTalents(c,ids);}else if(value.isJsonArray())for(JsonElement c:value.getAsJsonArray())collectTalents(c,ids); }
    private static String type(JsonElement value) { return value != null && value.isJsonObject() ? string(value.getAsJsonObject(), "Type") : ""; }
    private static String string(JsonObject object, String name) { return object != null && object.has(name) ? object.get(name).getAsString() : ""; }
}
