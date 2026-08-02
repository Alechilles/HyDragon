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

            long bondCount = countBranch(talents, "Essence Bond");
            assertEquals(form.hasBond() ? 9 : 0, bondCount, form.name());
            assertEquals(12, countBranch(talents, "Combat"), form.name());
            assertEquals(10, countBranch(talents, "Vigor"), form.name());
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
                assertFalse(talent.get("DisplayName").getAsString().contains("Not implemented"), id);
                assertFalse(talent.get("Description").getAsString().contains("Not implemented"), id);
                if (!branch.equals("Combat")) {
                    assertTrue(id.startsWith("Miniwyvern_" + form.name() + "_"), id);
                    assertTrue(scopedIds.add(id), "duplicate form-scoped talent " + id);
                }
                if (branch.equals("Vigor")) {
                    for (JsonElement effect : talent.getAsJsonArray("Effects")) {
                        String key = effect.getAsJsonObject().get("EffectKey").getAsString();
                        assertTrue(Set.of("MaxHealthMultiplier", "DamageTakenMultiplier", "MoveSpeedMultiplier")
                                .contains(key), id + " uses unsupported Vigor effect " + key);
                    }
                }
            }
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

    private static long countBranch(JsonArray talents, String branch) {
        long count = 0;
        for (JsonElement element : talents) {
            if (branch.equals(element.getAsJsonObject().get("Branch").getAsString())) {
                count++;
            }
        }
        return count;
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }

    private record Form(String name, String roleId, boolean hasBond) {
    }
}

