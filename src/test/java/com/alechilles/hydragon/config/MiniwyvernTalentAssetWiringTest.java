package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

/** Exact, mutation-resistant contract for Miniwyvern projectile arbitration. */
final class MiniwyvernTalentAssetWiringTest {
    private static final Path COMPONENT = Path.of("src/main/resources/Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json");
    private static final Path TEMPLATE = Path.of("src/main/resources/Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json");
    private static final List<String> ROOTS = List.of("TalentProjectileBase", "TalentProjectileIntermediate", "TalentProjectilePattern", "TalentProjectilePatternEcho", "TalentProjectileMastery", "TalentProjectileMasteryEcho");
    private static final List<String> FORMS = List.of("Fire", "Ice", "Lightning", "Nature", "Toxic", "Void", "Wild");
    private static final String AIMING = "Miniwyvern_Projectile_Aiming";
    private static final String VOLLEY = "Miniwyvern_Projectile_Volley_Active";
    private static final String ECHO = "Miniwyvern_Projectile_Echo_Pending";

    @Test
    void everyAimSchedulerAndPhaseOneBranchHasTheExactSafeContract() throws IOException {
        JsonArray instructions = instructions();
        assertReadinessBeforeSchedulers(instructions);
        List<JsonObject> schedulers = instructions.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(i -> hasAction(i, "TimerStart", "Miniwyvern_Projectile_Aim")).toList();
        assertEquals(2, schedulers.size(), "exactly standard and guided aim schedulers are permitted");
        for (JsonObject scheduler : schedulers) assertSafeScheduler(scheduler);
        assertEquals(Set.of("Base and range/cadence lineups use the standard aim window.", "Guidance and mastery lineups use the extended aim window."), schedulers.stream().map(s -> string(s, "$Comment")).collect(java.util.stream.Collectors.toSet()));

        List<JsonObject> phaseOne = attacks(instructions).stream().filter(i -> Set.of("TalentProjectilePattern", "TalentProjectileMastery").contains(attack(i))).toList();
        assertEquals(3, phaseOne.size());
        for (JsonObject branch : phaseOne) assertPhaseOne(branch);
    }

    @Test
    void echoBranchesUseOnlyMandatoryContinuationContextAndExactSerialActions() throws IOException {
        List<JsonObject> echoes = attacks(instructions()).stream().filter(i -> attack(i).endsWith("Echo")).toList();
        assertEquals(3, echoes.size());
        for (JsonObject echo : echoes) assertEcho(echo);
    }

    @Test
    void profilesHaveDirectTalentGatesAndNoBranchMotion() throws IOException {
        List<JsonObject> branches = attacks(instructions());
        assertProjectileInventory(branches);
        assertProfile(branches, "TalentProjectileMastery", "ProjectileMastery", Set.of("ProjectileMastery"), Set.of());
        assertProfile(branches, "TalentProjectilePattern", "ProjectilePattern", Set.of("ProjectilePattern", "ProjectileGuidance"), Set.of("ProjectileMastery"));
        assertProfile(branches, "TalentProjectilePattern", "ProjectilePattern", Set.of("ProjectilePattern"), Set.of("ProjectileGuidance", "ProjectileMastery"));
        assertProfile(branches, "TalentProjectileMasteryEcho", "ProjectileMastery", Set.of("ProjectileMastery"), Set.of());
        assertProfile(branches, "TalentProjectilePatternEcho", "ProjectilePattern", Set.of("ProjectilePattern", "ProjectileGuidance"), Set.of("ProjectileMastery"));
        assertProfile(branches, "TalentProjectilePatternEcho", "ProjectilePattern", Set.of("ProjectilePattern"), Set.of("ProjectileGuidance", "ProjectileMastery"));
        assertProfile(branches, "TalentProjectileIntermediate", "ProjectileGuidance", Set.of("ProjectileGuidance", "ProjectileCadence"), Set.of("ProjectilePattern", "ProjectileMastery"));
        assertProfile(branches, "TalentProjectileIntermediate", "ProjectileGuidance", Set.of("ProjectileGuidance"), Set.of("ProjectileCadence", "ProjectilePattern", "ProjectileMastery"));
        assertProfile(branches, "TalentProjectileIntermediate", "ProjectileCadence", Set.of("ProjectileCadence"), Set.of("ProjectileGuidance", "ProjectilePattern", "ProjectileMastery"));
        assertProfile(branches, "TalentProjectileIntermediate", "ProjectileRange", Set.of("ProjectileRange"), Set.of("ProjectileCadence", "ProjectileGuidance", "ProjectilePattern", "ProjectileMastery"));
        assertProfile(branches, "TalentProjectileBase", "DraconicProjectile", Set.of("DraconicProjectile"), Set.of("ProjectileRange", "ProjectileCadence", "ProjectileGuidance", "ProjectilePattern", "ProjectileMastery"));
        for (JsonObject branch : branches) {
            if (attack(branch).endsWith("Echo")) continue;
            assertFalse(branch.has("BodyMotion"));
            assertFalse(branch.has("HeadMotion"));
            if (!Set.of("TalentProjectilePattern", "TalentProjectileMastery").contains(attack(branch))) assertSingleShot(branch);
        }
    }

    @Test
    void templateForwardsSixExactComputesAndFormsUseTheirOwnPrefixedRoots() throws IOException {
        JsonObject template = load(TEMPLATE);
        JsonObject modify = componentModify(template);
        assertForwarding(modify);
        for (String form : FORMS) assertFormRoots(form, load(role(form)).getAsJsonObject("Modify"));
    }

    @Test
    void aerialCombatComponentKeepsMainStateOwnershipInTheTemplate() throws IOException {
        JsonObject component = load(COMPONENT);
        List<JsonObject> componentStates = all(component, o -> "State".equals(string(o, "Type")));
        assertFalse(componentStates.isEmpty());
        assertTrue(componentStates.stream().allMatch(o -> string(o, "State").startsWith(".")),
                "components may only sense or set their dot-prefixed local states");

        JsonObject template = load(TEMPLATE);
        List<JsonObject> defendOwners = all(template, o ->
                o.has("Sensor")
                        && "State".equals(string(o.getAsJsonObject("Sensor"), "Type"))
                        && "Defend".equals(string(o.getAsJsonObject("Sensor"), "State"))
                        && containsReference(o, "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend"));
        assertEquals(1, defendOwners.size(), "the template Defend branch must remain the component's sole main-state owner");
    }

    @Test
    void everyWatchMotionHasLockedTargetContext() throws IOException {
        List<JsonObject> watchers = all(load(COMPONENT), o ->
                o.has("HeadMotion") && "Watch".equals(string(o.getAsJsonObject("HeadMotion"), "Type")));
        assertEquals(3, watchers.size());
        assertTrue(watchers.stream().allMatch(o -> containsTargetSlot(o.get("Sensor"), "LockedTarget")),
                "Watch requires a target-providing sensor in the same instruction context");
    }

    @Test
    void bothCancellationBranchesHaveInvalidContextAndClearEchoBeforeReset() throws IOException {
        for (JsonObject asset : List.of(load(COMPONENT), load(TEMPLATE))) {
            List<JsonObject> cancellations = cancellations(asset);
            assertEquals(asset.has("Content") ? 2 : 1, cancellations.size(), "every reset-bearing cancellation branch must be inspected");
            for (JsonObject cancel : cancellations) {
                assertInvalidContext(cancel.get("Sensor"));
                JsonArray actions = cancel.getAsJsonArray("Actions");
                int echoOff = index(actions, "SetFlag", ECHO, false);
                int reset = index(actions, "ResetInstructions", null, false);
                assertTrue(echoOff >= 0 && reset > echoOff, "echo must clear before reset");
                int aimOff = index(actions, "SetFlag", AIMING, false);
                int volleyOff = index(actions, "SetFlag", VOLLEY, false);
                int aimStop = index(actions, "TimerStop", "Miniwyvern_Projectile_Aim", false);
                assertTrue(aimOff >= 0 && aimOff < reset);
                assertTrue(volleyOff >= 0 && volleyOff < reset);
                assertTrue(aimStop >= 0 && aimStop < reset);
                assertEquals(actions.size() - 1, reset, "ResetInstructions must be the final cancellation action");
            }
        }
    }

    @Test
    void mutationProbesProveTheContractRejectsEachReviewDefect() throws IOException {
        JsonArray readinessWithVolleyAlternative = instructions().deepCopy();
        JsonObject readiness = readinessWithVolleyAlternative.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(i -> string(i, "$Comment").startsWith("Swoop readiness")).findFirst().orElseThrow();
        JsonArray alternatives = directTerms(readiness.get("Sensor")).stream().filter(t -> "Or".equals(string(t, "Type"))).findFirst().orElseThrow().getAsJsonArray("Sensors");
        alternatives.remove(1); alternatives.add(flag(VOLLEY, true));
        assertThrows(AssertionError.class, () -> assertReadinessBeforeSchedulers(readinessWithVolleyAlternative));

        JsonArray secondVolleyReadiness = instructions().deepCopy();
        secondVolleyReadiness.add(JsonParser.parseString("{\"Sensor\":{\"Type\":\"And\",\"Sensors\":[{\"Type\":\"Flag\",\"Name\":\"Miniwyvern_Projectile_Volley_Active\"}]},\"Actions\":[{\"Type\":\"SetFlag\",\"Name\":\"Miniwyvern_Swoop_Pending\",\"SetTo\":true}]}"));
        assertThrows(AssertionError.class, () -> assertReadinessBeforeSchedulers(secondVolleyReadiness));

        JsonObject template = load(TEMPLATE);
        JsonObject missingEcho = template.deepCopy(); componentModify(missingEcho).remove("TalentProjectilePatternEcho");
        assertThrows(AssertionError.class, () -> assertForwarding(componentModify(missingEcho)));

        JsonObject extraApex = template.deepCopy(); componentModify(extraApex).add("TalentProjectileApex", new JsonObject());
        assertThrows(AssertionError.class, () -> assertForwarding(componentModify(extraApex)));

        JsonObject crossForm = load(role("Fire")).getAsJsonObject("Modify").deepCopy();
        crossForm.addProperty("TalentProjectileBase", "Root_NPC_Wyvern_Mini_Ice_Projectile_Base");
        assertThrows(AssertionError.class, () -> assertFormRoots("Fire", crossForm));

        JsonObject roleApex = load(role("Fire")).getAsJsonObject("Modify").deepCopy(); roleApex.addProperty("TalentProjectileApex", "Root_NPC_Wyvern_Mini_Fire_Projectile_Apex");
        assertThrows(AssertionError.class, () -> assertFormRoots("Fire", roleApex));

        JsonArray unsafeInstructions = instructions().deepCopy();
        unsafeInstructions.add(JsonParser.parseString("{\"Sensor\":{\"Type\":\"And\",\"Sensors\":[]},\"Actions\":[{\"Type\":\"TimerStart\",\"Name\":\"Miniwyvern_Projectile_Aim\"}]}"));
        assertThrows(AssertionError.class, () -> unsafeInstructions.asList().stream().map(JsonElement::getAsJsonObject).filter(i -> hasAction(i, "TimerStart", "Miniwyvern_Projectile_Aim")).forEach(MiniwyvernTalentAssetWiringTest::assertSafeScheduler));

        JsonObject gateUnderOr = phaseOne().get(0).deepCopy(); JsonArray direct = gateUnderOr.getAsJsonObject("Sensor").getAsJsonArray("Sensors");
        JsonObject required = direct.remove(direct.size() - 1).getAsJsonObject(); JsonArray or = new JsonArray(); or.add(required); or.add(new JsonObject());
        JsonObject wrapper = new JsonObject(); wrapper.addProperty("Type", "Or"); wrapper.add("Sensors", or); direct.add(wrapper);
        assertThrows(AssertionError.class, () -> assertDirectTalent(gateUnderOr.get("Sensor"), attack(gateUnderOr).equals("TalentProjectileMastery") ? "ProjectileMastery" : "ProjectilePattern", false));

        JsonObject nonblocking = phaseOne().get(0).deepCopy(); nonblocking.addProperty("ActionsBlocking", false);
        assertThrows(AssertionError.class, () -> assertPhaseOne(nonblocking));

        JsonObject selfInvalidating = phaseOne().get(0).deepCopy(); selfInvalidating.getAsJsonObject("Sensor").getAsJsonArray("Sensors").add(flag(VOLLEY, false));
        assertThrows(AssertionError.class, () -> assertPhaseOne(selfInvalidating));

        JsonObject earlyCleanup = echoes().get(0).deepCopy(); JsonArray echoActions = earlyCleanup.getAsJsonArray("Actions");
        JsonElement cleanup = echoActions.remove(echoActions.size() - 1); JsonArray reordered = new JsonArray(); reordered.add(cleanup); echoActions.forEach(reordered::add); earlyCleanup.add("Actions", reordered);
        assertThrows(AssertionError.class, () -> assertEcho(earlyCleanup));

        List<JsonObject> duplicateProfiles = new java.util.ArrayList<>(attacks(instructions())); duplicateProfiles.add(duplicateProfiles.get(0));
        assertThrows(AssertionError.class, () -> assertProjectileInventory(duplicateProfiles));

        JsonObject swappedEcho = echoes().get(0).deepCopy(); JsonArray swappedActions = swappedEcho.getAsJsonArray("Actions"); swappedActions.get(index(swappedActions, "Attack", null, false)).getAsJsonObject().getAsJsonObject("Attack").addProperty("Compute", "TalentProjectilePatternEcho");
        assertThrows(AssertionError.class, () -> assertEcho(swappedEcho));
    }

    private static void assertSafeScheduler(JsonObject scheduler) {
        for (String required : List.of("LockedTarget", "AirborneMode", "Fly")) assertDirectContext(scheduler.get("Sensor"), required);
        for (String flag : List.of("Miniwyvern_Swoop_Pending", "Miniwyvern_Swooping")) assertDirectFlag(scheduler.get("Sensor"), flag, false);
        assertDirectFlag(scheduler.get("Sensor"), AIMING, false);
        assertDirectFlag(scheduler.get("Sensor"), VOLLEY, false);
        assertDirectTimer(scheduler.get("Sensor"), "Miniwyvern_Projectile_Cooldown", "Stopped");
        assertFalse(scheduler.has("BodyMotion")); assertFalse(scheduler.has("HeadMotion"));
        assertFalse(scheduler.has("ActionsBlocking"));
        JsonArray a = scheduler.getAsJsonArray("Actions"); assertEquals(3, a.size());
        assertAction(a.get(0), "TimerStart", "Miniwyvern_Projectile_Aim", false);
        JsonElement range = a.get(0).getAsJsonObject().get("StartValueRange");
        if (string(scheduler, "$Comment").startsWith("Base and")) {
            assertEquals(JsonParser.parseString("[0.4,0.7]"), range); assertNestedTalentSelection(scheduler.get("Sensor"), "And", Set.of(), Set.of("ProjectileGuidance", "ProjectileMastery"));
        } else {
            assertEquals(JsonParser.parseString("[0.55,0.85]"), range); assertNestedTalentSelection(scheduler.get("Sensor"), "Or", Set.of("ProjectileGuidance", "ProjectileMastery"), Set.of());
        }
        assertEquals(range, a.get(0).getAsJsonObject().get("RestartValueRange"));
        assertAction(a.get(1), "TimerRestart", "Miniwyvern_Projectile_Aim", false); assertAction(a.get(2), "SetFlag", AIMING, true);
    }
    private static void assertReadinessBeforeSchedulers(JsonArray instructions) {
        int readinessIndex = -1;
        List<JsonObject> pendingSetters = all(instructions, instruction ->
                instruction.has("Actions")
                        && instruction.get("Actions").isJsonArray()
                        && index(instruction.getAsJsonArray("Actions"), "SetFlag", "Miniwyvern_Swoop_Pending", true) >= 0);
        assertEquals(1, pendingSetters.size(), "exactly one instruction may set swoop pending");
        assertFalse(containsNamedFlag(pendingSetters.get(0).get("Sensor"), VOLLEY), "no swoop-readiness setter may depend on volley");
        for (int i = 0; i < instructions.size(); i++) {
            JsonObject instruction = instructions.get(i).getAsJsonObject();
            if (string(instruction, "$Comment").startsWith("Swoop readiness")) {
                readinessIndex = i;
                JsonObject sensor = instruction.getAsJsonObject("Sensor");
                assertEquals("And", string(sensor, "Type"));
                JsonObject readinessOr = directTerms(sensor).stream().filter(t -> "Or".equals(string(t, "Type"))).findFirst().orElseThrow();
                JsonArray alternatives = readinessOr.getAsJsonArray("Sensors");
                assertEquals(2, alternatives.size());
                assertEquals(JsonParser.parseString("{\"Type\":\"Flag\",\"Name\":\"Miniwyvern_Projectile_Aiming\",\"Set\":false}"), alternatives.get(0));
                assertEquals(JsonParser.parseString("{\"Type\":\"Flag\",\"Name\":\"Miniwyvern_Projectile_Echo_Pending\"}"), alternatives.get(1));
                assertFalse(alternatives.asList().stream().anyMatch(t -> VOLLEY.equals(string(t.getAsJsonObject(), "Name"))));
            }
        }
        assertTrue(readinessIndex >= 0, "missing swoop readiness setter");
        for (int i = 0; i < instructions.size(); i++) if (hasAction(instructions.get(i).getAsJsonObject(), "TimerStart", "Miniwyvern_Projectile_Aim")) assertTrue(readinessIndex < i, "readiness must precede every aim scheduler");
    }
    private static void assertPhaseOne(JsonObject branch) {
        for (String required : List.of("LockedTarget", "AirborneMode", "Fly")) assertDirectContext(branch.get("Sensor"), required);
        assertDirectFlag(branch.get("Sensor"), AIMING, true); assertFalse(directFlagPresent(branch.get("Sensor"), VOLLEY), "phase one must remain eligible after it latches volley"); assertDirectFlag(branch.get("Sensor"), ECHO, false);
        assertDirectTimer(branch.get("Sensor"), "Miniwyvern_Projectile_Aim", "Stopped");
        assertDirectFlag(branch.get("Sensor"), "Miniwyvern_Swoop_Pending", false); assertDirectFlag(branch.get("Sensor"), "Miniwyvern_Swooping", false);
        assertTrue(branch.has("ActionsBlocking") && branch.get("ActionsBlocking").getAsBoolean());
        JsonArray a = branch.getAsJsonArray("Actions"); assertEquals(5, a.size());
        assertAction(a.get(0), "SetFlag", VOLLEY, true); assertAttack(a.get(2), attack(branch)); assertAction(a.get(4), "SetFlag", ECHO, true);
    }
    private static void assertSingleShot(JsonObject branch) {
        assertTrue(branch.has("ActionsBlocking") && branch.get("ActionsBlocking").getAsBoolean());
        JsonArray a = branch.getAsJsonArray("Actions"); assertEquals(6, a.size()); assertAttack(a.get(1), attack(branch));
        String cooldown = directPositiveTalents(branch.get("Sensor")).contains("ProjectileCadence") ? "[4,6]" : "[5,7]";
        assertCooldown(a.get(3), cooldown); assertAction(a.get(4), "TimerRestart", "Miniwyvern_Projectile_Cooldown", false); assertAction(a.get(5), "SetFlag", AIMING, false);
    }
    private static void assertEcho(JsonObject echo) {
        assertTrue(echo.has("ActionsBlocking") && echo.get("ActionsBlocking").getAsBoolean());
        for (String required : List.of("LockedTarget", "AirborneMode", "Fly")) assertDirectContext(echo.get("Sensor"), required);
        assertDirectFlag(echo.get("Sensor"), ECHO, true); assertDirectFlag(echo.get("Sensor"), "Miniwyvern_Swooping", false);
        assertFalse(directFlagPresent(echo.get("Sensor"), AIMING)); assertFalse(directFlagPresent(echo.get("Sensor"), VOLLEY));
        JsonArray a = echo.getAsJsonArray("Actions"); assertEquals(9, a.size());
        assertEquals("Timeout", type(a.get(0))); assertEquals(JsonParser.parseString("[0.3,0.3]"), a.get(0).getAsJsonObject().get("Delay"));
        assertAttack(a.get(2), attack(echo)); assertCooldown(a.get(4), attack(echo).contains("Mastery") ? "[3,5]" : "[4,6]");
        assertEquals(directPositiveTalents(echo.get("Sensor")).contains("ProjectileMastery") ? "TalentProjectileMasteryEcho" : "TalentProjectilePatternEcho", attack(echo));
        assertAction(a.get(5), "TimerRestart", "Miniwyvern_Projectile_Cooldown", false); assertAction(a.get(6), "SetFlag", VOLLEY, false); assertAction(a.get(7), "SetFlag", AIMING, false); assertAction(a.get(8), "SetFlag", ECHO, false);
    }
    private static void assertForwarding(JsonObject modify) { assertEquals(Set.copyOf(ROOTS), modify.keySet().stream().filter(k -> k.startsWith("TalentProjectile")).collect(java.util.stream.Collectors.toSet())); for (String root : ROOTS) { assertTrue(modify.has(root)); assertEquals(root, modify.getAsJsonObject(root).get("Compute").getAsString()); } }
    private static void assertProjectileInventory(List<JsonObject> branches) {
        List<JsonObject> primary = branches.stream().filter(b -> !attack(b).endsWith("Echo")).toList();
        List<JsonObject> echoes = branches.stream().filter(b -> attack(b).endsWith("Echo")).toList();
        assertEquals(8, primary.size()); assertEquals(3, echoes.size());
        assertEquals(8, primary.stream().map(b -> attack(b) + directPositiveTalents(b.get("Sensor")) + directNegatedTalents(b.get("Sensor"))).distinct().count());
        assertEquals(3, echoes.stream().map(b -> attack(b) + directPositiveTalents(b.get("Sensor")) + directNegatedTalents(b.get("Sensor"))).distinct().count());
    }
    private static void assertFormRoots(String form, JsonObject modify) { assertEquals(Set.copyOf(ROOTS), modify.keySet().stream().filter(k -> k.startsWith("TalentProjectile")).collect(java.util.stream.Collectors.toSet())); for (String root : ROOTS) assertEquals("Root_NPC_Wyvern_Mini_" + form + "_Projectile_" + root.substring("TalentProjectile".length()).replace("Echo", "_Echo"), modify.get(root).getAsString()); }
    private static void assertProfile(List<JsonObject> branches, String root, String required, Set<String> positive, Set<String> negative) { JsonObject branch = branches.stream().filter(i -> root.equals(attack(i))).filter(i -> directPositiveTalents(i.get("Sensor")).equals(positive)).findFirst().orElseThrow(); assertDirectTalent(branch.get("Sensor"), required, false); assertEquals(negative, directNegatedTalents(branch.get("Sensor"))); }
    private static List<JsonObject> attacks(JsonArray i) { return i.asList().stream().map(JsonElement::getAsJsonObject).filter(x -> hasAction(x, "Attack", null)).filter(x -> attack(x).startsWith("TalentProjectile")).toList(); }
    private static List<JsonObject> phaseOne() throws IOException { return attacks(instructions()).stream().filter(i -> !attack(i).endsWith("Echo") && Set.of("TalentProjectilePattern", "TalentProjectileMastery").contains(attack(i))).toList(); }
    private static List<JsonObject> echoes() throws IOException { return attacks(instructions()).stream().filter(i -> attack(i).endsWith("Echo")).toList(); }
    private static JsonObject componentModify(JsonObject template) { return find(template, o -> "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend".equals(string(o, "Reference"))).getAsJsonObject("Modify"); }
    private static List<JsonObject> cancellations(JsonObject asset) { return all(asset, o -> hasAction(o, "ResetInstructions", null)); }
    private static Path role(String form) { return Path.of("src/main/resources/Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini_" + form + ".json"); }
    private static JsonArray instructions() throws IOException { return load(COMPONENT).getAsJsonObject("Content").getAsJsonArray("Instructions"); }
    private static JsonObject load(Path p) throws IOException { return JsonParser.parseString(Files.readString(p)).getAsJsonObject(); }
    private static boolean hasAction(JsonObject i, String t, String n) { return i.has("Actions") && index(i.getAsJsonArray("Actions"), t, n, false) >= 0; }
    private static String attack(JsonObject i) { return i.getAsJsonArray("Actions").asList().stream().map(JsonElement::getAsJsonObject).filter(a -> "Attack".equals(string(a, "Type"))).findFirst().orElseThrow().getAsJsonObject("Attack").get("Compute").getAsString(); }
    private static void assertAttack(JsonElement a, String root) { assertEquals("Attack", type(a)); assertEquals(root, a.getAsJsonObject().getAsJsonObject("Attack").get("Compute").getAsString()); assertEquals(JsonParser.parseString("[0.1,0.2]"), a.getAsJsonObject().get("AimingTimeRange")); assertEquals(JsonParser.parseString("[0,0]"), a.getAsJsonObject().get("AttackPauseRange")); }
    private static void assertCooldown(JsonElement a, String range) { assertAction(a, "TimerStart", "Miniwyvern_Projectile_Cooldown", false); assertEquals(JsonParser.parseString(range), a.getAsJsonObject().get("StartValueRange")); assertEquals(JsonParser.parseString(range), a.getAsJsonObject().get("RestartValueRange")); }
    private static void assertAction(JsonElement a, String t, String n, boolean set) { assertEquals(t, type(a)); if (n != null) assertEquals(n, string(a.getAsJsonObject(), "Name")); if ("SetFlag".equals(t)) assertEquals(set, a.getAsJsonObject().get("SetTo").getAsBoolean()); }
    private static int index(JsonArray a, String t, String n, boolean set) { for (int x=0;x<a.size();x++) { JsonObject o=a.get(x).getAsJsonObject(); if(t.equals(string(o,"Type")) && (n==null || n.equals(string(o,"Name"))) && (!"SetFlag".equals(t) || o.get("SetTo").getAsBoolean()==set)) return x; } return -1; }
    private static JsonObject flag(String name, boolean set) { JsonObject flag = new JsonObject(); flag.addProperty("Type", "Flag"); flag.addProperty("Name", name); if (!set) flag.addProperty("Set", false); return flag; }
    private static boolean directType(JsonElement sensor, String wanted) { return sensor != null && sensor.isJsonObject() && wanted.equals(string(sensor.getAsJsonObject(), "Type")); }
    private static void assertDirectTalent(JsonElement sensor, String talent, boolean negated) { assertTrue(directTerms(sensor).stream().anyMatch(t -> negated ? "Not".equals(string(t,"Type")) && directType(t.get("Sensor"), "TameworkHasTalent") && talent.equals(string(t.getAsJsonObject("Sensor"), "TalentId")) : "TameworkHasTalent".equals(string(t,"Type")) && talent.equals(string(t,"TalentId")))); }
    private static Set<String> directPositiveTalents(JsonElement sensor) { return directTerms(sensor).stream().filter(t -> "TameworkHasTalent".equals(string(t,"Type"))).map(t -> string(t,"TalentId")).collect(java.util.stream.Collectors.toSet()); }
    private static Set<String> directNegatedTalents(JsonElement sensor) { return directTerms(sensor).stream().filter(t -> "Not".equals(string(t,"Type")) && directType(t.get("Sensor"), "TameworkHasTalent")).map(t -> string(t.getAsJsonObject("Sensor"), "TalentId")).collect(java.util.stream.Collectors.toSet()); }
    private static void assertDirectFlag(JsonElement sensor, String flag, boolean value) { assertTrue(directTerms(sensor).stream().anyMatch(t -> "Flag".equals(string(t,"Type")) && flag.equals(string(t,"Name")) && (!t.has("Set") ? value : t.get("Set").getAsBoolean()==value))); }
    private static boolean directFlagPresent(JsonElement sensor, String flag) { return directTerms(sensor).stream().anyMatch(t -> "Flag".equals(string(t,"Type")) && flag.equals(string(t,"Name"))); }
    private static void assertDirectContext(JsonElement sensor, String wanted) {
        assertTrue(directTerms(sensor).stream().anyMatch(t -> switch (wanted) {
            case "LockedTarget" -> "Target".equals(string(t, "Type")) && wanted.equals(string(t, "TargetSlot"));
            case "AirborneMode" -> "Flag".equals(string(t, "Type")) && wanted.equals(string(t, "Name"));
            case "Fly" -> "MotionController".equals(string(t, "Type")) && wanted.equals(string(t, "MotionController"));
            default -> false;
        }));
    }
    private static void assertDirectTimer(JsonElement sensor, String name, String state) { assertTrue(directTerms(sensor).stream().anyMatch(t -> "Timer".equals(string(t, "Type")) && name.equals(string(t, "Name")) && state.equals(string(t, "State")))); }
    private static void assertInvalidContext(JsonElement sensor) {
        int invalidTerms = 0;
        if (containsObject(sensor, JsonParser.parseString("{\"Type\":\"Not\",\"Sensor\":{\"Type\":\"State\",\"State\":\"Defend\"}}"))) invalidTerms++;
        if (containsObject(sensor, JsonParser.parseString("{\"Type\":\"Not\",\"Sensor\":{\"Type\":\"Target\",\"TargetSlot\":\"LockedTarget\"}}"))) invalidTerms++;
        if (containsObject(sensor, JsonParser.parseString("{\"Type\":\"Flag\",\"Name\":\"AirborneMode\",\"Set\":false}"))
                || containsObject(sensor, JsonParser.parseString("{\"Type\":\"Not\",\"Sensor\":{\"Type\":\"Flag\",\"Name\":\"AirborneMode\"}}"))) invalidTerms++;
        if (containsObject(sensor, JsonParser.parseString("{\"Type\":\"Not\",\"Sensor\":{\"Type\":\"MotionController\",\"MotionController\":\"Fly\"}}"))) invalidTerms++;
        assertTrue(invalidTerms >= 3, "cancellation must enumerate lost target/flight/controller or outer state context");
    }
    private static boolean containsNamedFlag(JsonElement e, String name) { if (e == null) return false; if (e.isJsonObject()) { JsonObject o = e.getAsJsonObject(); if ("Flag".equals(string(o, "Type")) && name.equals(string(o, "Name"))) return true; for (JsonElement child : o.asMap().values()) if (containsNamedFlag(child, name)) return true; } else if (e.isJsonArray()) for (JsonElement child : e.getAsJsonArray()) if (containsNamedFlag(child, name)) return true; return false; }
    private static boolean containsTargetSlot(JsonElement e, String targetSlot) { if (e == null) return false; if (e.isJsonObject()) { JsonObject o = e.getAsJsonObject(); if ("Target".equals(string(o, "Type")) && targetSlot.equals(string(o, "TargetSlot"))) return true; for (JsonElement child : o.asMap().values()) if (containsTargetSlot(child, targetSlot)) return true; } else if (e.isJsonArray()) for (JsonElement child : e.getAsJsonArray()) if (containsTargetSlot(child, targetSlot)) return true; return false; }
    private static boolean containsReference(JsonElement e, String reference) { if (e == null) return false; if (e.isJsonObject()) { JsonObject o = e.getAsJsonObject(); if (reference.equals(string(o, "Reference"))) return true; for (JsonElement child : o.asMap().values()) if (containsReference(child, reference)) return true; } else if (e.isJsonArray()) for (JsonElement child : e.getAsJsonArray()) if (containsReference(child, reference)) return true; return false; }
    private static boolean containsObject(JsonElement haystack, JsonElement needle) { if (haystack == null) return false; if (haystack.equals(needle)) return true; if (haystack.isJsonObject()) for (JsonElement child : haystack.getAsJsonObject().asMap().values()) if (containsObject(child, needle)) return true; if (haystack.isJsonArray()) for (JsonElement child : haystack.getAsJsonArray()) if (containsObject(child, needle)) return true; return false; }
    private static void assertNestedTalentSelection(JsonElement sensor, String connector, Set<String> positive, Set<String> negative) { JsonObject choice = directTerms(sensor).stream().filter(t -> connector.equals(string(t, "Type"))).findFirst().orElseThrow(); List<JsonObject> terms = choice.getAsJsonArray("Sensors").asList().stream().map(JsonElement::getAsJsonObject).toList(); assertEquals(positive, terms.stream().filter(t -> "TameworkHasTalent".equals(string(t, "Type"))).map(t -> string(t, "TalentId")).collect(java.util.stream.Collectors.toSet())); assertEquals(negative, terms.stream().filter(t -> "Not".equals(string(t, "Type")) && directType(t.get("Sensor"), "TameworkHasTalent")).map(t -> string(t.getAsJsonObject("Sensor"), "TalentId")).collect(java.util.stream.Collectors.toSet())); }
    private static List<JsonObject> directTerms(JsonElement sensor) { assertTrue(directType(sensor, "And")); return sensor.getAsJsonObject().getAsJsonArray("Sensors").asList().stream().map(JsonElement::getAsJsonObject).toList(); }
    private static JsonObject find(JsonElement e, java.util.function.Predicate<JsonObject> p) { if(e.isJsonObject()) { JsonObject o=e.getAsJsonObject(); if(p.test(o)) return o; for(JsonElement c:o.asMap().values()) try { return find(c,p); } catch(IllegalArgumentException ignored) {} } else if(e.isJsonArray()) for(JsonElement c:e.getAsJsonArray()) try { return find(c,p); } catch(IllegalArgumentException ignored) {} throw new IllegalArgumentException("not found"); }
    private static List<JsonObject> all(JsonElement e, java.util.function.Predicate<JsonObject> p) { java.util.ArrayList<JsonObject> found = new java.util.ArrayList<>(); collect(e, p, found); return found; }
    private static void collect(JsonElement e, java.util.function.Predicate<JsonObject> p, List<JsonObject> found) { if (e.isJsonObject()) { JsonObject o=e.getAsJsonObject(); if (p.test(o)) found.add(o); for (JsonElement c : o.asMap().values()) collect(c, p, found); } else if (e.isJsonArray()) for (JsonElement c : e.getAsJsonArray()) collect(c, p, found); }
    private static String type(JsonElement e) { return e != null && e.isJsonObject() ? string(e.getAsJsonObject(), "Type") : ""; }
    private static String string(JsonObject o, String n) { return o != null && o.has(n) ? o.get(n).getAsString() : ""; }
}
