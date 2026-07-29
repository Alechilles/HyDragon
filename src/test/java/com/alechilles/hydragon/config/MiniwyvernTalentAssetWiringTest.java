package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.Test;

/** Contract for the asset-owned Miniwyvern combat execution path. */
final class MiniwyvernTalentAssetWiringTest {
    private static final Path TEMPLATE = Path.of("Server", "NPC", "Roles", "Creature", "HyDragon",
            "Templates", "Template_Wyvern_Mini_Flying_Tamed.json");
    private static final List<String> ROLES = List.of("Wild", "Nature", "Toxic", "Fire", "Void", "Lightning", "Ice");
    private static final List<String> COMBAT_TALENTS = List.of("DraconicProjectile", "ProjectileRange",
            "ProjectileCadence", "ProjectileForce", "ProjectileGuidance", "ProjectileImpact",
            "ProjectilePattern", "DraconicAssault", "AssaultUtility", "AssaultMastery", "DraconicApex");

    @Test
    void templateContainsTalentGatedExecutableVariants() throws IOException {
        JsonObject template = load(TEMPLATE);
        JsonArray instructions = template.getAsJsonArray("Instructions");

        for (int index = 0; index < COMBAT_TALENTS.size(); index++) {
            String talentId = COMBAT_TALENTS.get(index);
            JsonObject instruction = instructionForTalent(instructions, talentId);
            assertTrue(hasExecutableAction(instruction), talentId + " must select an executable action");
            if (index + 1 < COMBAT_TALENTS.size()) {
                assertTrue(hasHigherTierExclusion(instruction, COMBAT_TALENTS.subList(index + 1, COMBAT_TALENTS.size())),
                        talentId + " must exclude a higher Combat variant");
            }
        }
    }

    @Test
    void allFormsProvideOnlyGenericTalentBindingsAndWildCombatIsRawOnly() throws IOException {
        for (String form : ROLES) {
            JsonObject role = load(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini",
                    "Tamed_Wyvern_Mini_" + form + ".json"));
            String source = role.toString();
            assertTrue(source.contains("DraconicProjectile"), form + " must bind the shared Combat flag");
            assertTrue(source.contains("DraconicApex"), form + " must bind the shared Combat capstone");
            assertFalse(source.contains("Miniwyvern_"), form + " must not introduce form-specific talent IDs");
        }
        for (String tier : List.of("", "_Intermediate", "_Apex")) {
            String wild = Files.readString(Path.of("Server", "Item", "Interactions", "NPCs", "HyDragon",
                    "Wyvern_Mini", "Wyvern_Mini_Wild_Projectile" + tier + ".json"));
            for (String status : List.of("Fire", "Ice", "Lightning", "Nature", "Toxic", "Void", "EffectId")) {
                assertFalse(wild.contains(status), "Wild projectile must remain raw-only: " + status);
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
            if (element.isJsonObject() && hasTalentGate(element, talentId)) return element.getAsJsonObject();
        }
        throw new AssertionError("missing executable gate for " + talentId);
    }

    private static boolean hasHigherTierExclusion(JsonElement value, List<String> higherTalents) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Not".equals(string(object, "Type")) && higherTalents.stream().anyMatch(id -> hasTalentGate(object, id))) return true;
            for (JsonElement child : object.asMap().values()) if (hasHigherTierExclusion(child, higherTalents)) return true;
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (hasHigherTierExclusion(child, higherTalents)) return true;
        }
        return false;
    }

    private static String string(JsonObject object, String name) {
        return object.has(name) ? object.get(name).getAsString() : "";
    }

    private static JsonObject load(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
