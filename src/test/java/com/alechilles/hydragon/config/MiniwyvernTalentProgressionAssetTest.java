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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Contract for role-specific Miniwyvern talent trees. */
final class MiniwyvernTalentProgressionAssetTest {
    private static final String TALENTS = "Server/Tamework/Talents/";
    private static final List<Form> FORMS = List.of(
            new Form("Fire", "Tamed_Wyvern_Mini_Fire", true),
            new Form("Ice", "Tamed_Wyvern_Mini_Ice", true),
            new Form("Lightning", "Tamed_Wyvern_Mini_Lightning", true),
            new Form("Nature", "Tamed_Wyvern_Mini_Nature", true),
            new Form("Toxic", "Tamed_Wyvern_Mini_Toxic", true),
            new Form("Void", "Tamed_Wyvern_Mini_Void", true),
            new Form("Wild", "Tamed_Wyvern_Mini_Wild", false));

    @Test
    void everyFormHasOneRoleScopedTreeWithTheApprovedBudget() throws IOException {
        for (Form form : FORMS) {
            JsonObject config = load(form);
            assertTrue(config.get("Enabled").getAsBoolean(), form.name());
            assertEquals(1, config.get("AllocationRevision").getAsInt(), form.name());
            assertEquals(List.of(form.roleId()), strings(config.getAsJsonArray("RoleIds")), form.name());

            JsonArray talents = config.getAsJsonArray("Talents");
            assertEquals(form.hasBond() ? 31 : 22, talents.size(), form.name());
            assertEquals(form.hasBond() ? 52 : 37, totalCost(talents), form.name());

            long bondCount = countBranch(talents, form, "essence_bond");
            assertEquals(form.hasBond() ? 9 : 0, bondCount, form.name());
            assertEquals(12, countBranch(talents, form, "combat"), form.name());
            assertEquals(10, countBranch(talents, form, "vigor"), form.name());
        }
    }

    @Test
    void nonCombatTalentsAreFormScopedAndVigorOnlyUsesSupportedEffects() throws IOException {
        Set<String> scopedIds = new LinkedHashSet<>();
        for (Form form : FORMS) {
            JsonArray talents = load(form).getAsJsonArray("Talents");
            for (JsonElement element : talents) {
                JsonObject talent = element.getAsJsonObject();
                String id = talent.get("Id").getAsString();
                String branch = talent.get("Branch").getAsString();
                String keyPrefix = "hydragon.talents.miniwyvern." + form.name().toLowerCase() + ".";
                assertTrue(talent.get("DisplayName").getAsString().startsWith(keyPrefix), id);
                assertTrue(talent.get("Description").getAsString().startsWith(keyPrefix), id);
                assertTrue(branch.startsWith(keyPrefix + "branch."), id);
                assertFalse(talent.get("DisplayName").getAsString().contains("Not implemented"), id);
                assertFalse(talent.get("Description").getAsString().contains("Not implemented"), id);
                if (!branch.endsWith(".combat")) {
                    assertTrue(id.startsWith("Miniwyvern_" + form.name() + "_"), id);
                    assertTrue(scopedIds.add(id), "duplicate form-scoped talent " + id);
                }
                if (branch.endsWith(".essence_bond")) {
                    assertFalse(talent.has("Effects"), id + " must be data-driven by its archetype aura");
                }
                if (branch.endsWith(".vigor")) {
                    for (JsonElement effect : talent.getAsJsonArray("Effects")) {
                        String key = effect.getAsJsonObject().get("EffectKey").getAsString();
                        assertTrue(Set.of("MaxHealthMultiplier", "DamageTakenMultiplier", "MoveSpeedMultiplier")
                                .contains(key), id + " uses unsupported Vigor effect " + key);
                    }
                }
            }
        }
    }

    @Test
    void elementalEssenceBondTreesFollowTwoCompleteBranchesBeforeTheCapstone() throws IOException {
        for (Form form : FORMS.stream().filter(Form::hasBond).toList()) {
            JsonArray talents = load(form).getAsJsonArray("Talents");
            JsonObject bond = findTalent(talents, "Bond");
            JsonObject focus = findTalent(talents, "Focus");
            JsonObject attunement = findTalent(talents, "Attunement");
            JsonObject amplification = findTalent(talents, "Amplification");
            JsonObject resonance = findTalent(talents, "Resonance");
            JsonObject efficiency = findTalent(talents, "Efficiency");
            JsonObject harmony = findTalent(talents, "Harmony");
            JsonObject mastery = findTalent(talents, "Mastery");
            JsonObject ascendance = findTalent(talents, "Ascendance");

            assertEquals(1, bond.get("Tier").getAsInt(), form.name());
            assertEquals(2, focus.get("Tier").getAsInt(), form.name());
            assertEquals(2, attunement.get("Tier").getAsInt(), form.name());
            assertEquals(3, amplification.get("Tier").getAsInt(), form.name());
            assertEquals(3, resonance.get("Tier").getAsInt(), form.name());
            assertEquals(4, efficiency.get("Tier").getAsInt(), form.name());
            assertEquals(4, harmony.get("Tier").getAsInt(), form.name());
            assertEquals(5, mastery.get("Tier").getAsInt(), form.name());
            assertEquals(6, ascendance.get("Tier").getAsInt(), form.name());

            assertEquals(Set.of(), requires(bond), form.name() + " root must have no prerequisite");
            assertEquals(Set.of(bond.get("Id").getAsString()), requires(focus),
                    form.name() + " pressure tier one must require the root");
            assertEquals(Set.of(bond.get("Id").getAsString()), requires(attunement),
                    form.name() + " ward tier one must require the root");
            assertEquals(Set.of(focus.get("Id").getAsString()), requires(amplification),
                    form.name() + " pressure tier two must require pressure tier one");
            assertEquals(Set.of(attunement.get("Id").getAsString()), requires(resonance),
                    form.name() + " ward tier two must require ward tier one");
            assertEquals(Set.of(amplification.get("Id").getAsString()), requires(efficiency),
                    form.name() + " pressure tier three must require pressure tier two");
            assertEquals(Set.of(resonance.get("Id").getAsString()), requires(harmony),
                    form.name() + " ward tier three must require ward tier two");
            assertEquals(Set.of(efficiency.get("Id").getAsString(), harmony.get("Id").getAsString()),
                    requires(mastery), form.name() + " convergence must require both tier-four endpoints");
            assertEquals(Set.of(mastery.get("Id").getAsString()), requires(ascendance),
                    form.name() + " capstone must require convergence");
        }
    }

    private static JsonObject load(Form form) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of(TALENTS + "HyDragonMiniwyvern" + form.name() + ".json")))
                .getAsJsonObject();
    }

    private static int totalCost(JsonArray talents) {
        int total = 0;
        for (JsonElement element : talents) {
            total += element.getAsJsonObject().get("PointCost").getAsInt();
        }
        return total;
    }

    private static long countBranch(JsonArray talents, Form form, String branch) {
        long count = 0;
        String expected = "hydragon.talents.miniwyvern." + form.name().toLowerCase() + ".branch." + branch;
        for (JsonElement element : talents) {
            if (expected.equals(element.getAsJsonObject().get("Branch").getAsString())) {
                count++;
            }
        }
        return count;
    }

    private static JsonObject findTalent(JsonArray talents, String suffix) {
        for (JsonElement element : talents) {
            JsonObject talent = element.getAsJsonObject();
            if (talent.get("Branch").getAsString().endsWith(".essence_bond")
                    && talent.get("Id").getAsString().endsWith(suffix)) return talent;
        }
        throw new AssertionError("Missing Essence Bond talent ending in " + suffix);
    }

    private static Set<String> requires(JsonObject talent) {
        if (!talent.has("RequiresTalentIds")) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : talent.getAsJsonArray("RequiresTalentIds")) ids.add(element.getAsString());
        return ids;
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }

    private record Form(String name, String roleId, boolean hasBond) {
    }
}

