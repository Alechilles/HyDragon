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
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract for the asset-owned Miniwyvern combat execution path. */
final class MiniwyvernTalentAssetWiringTest {
    private static final Path TEMPLATE = Path.of("Server", "NPC", "Roles", "Creature", "HyDragon",
            "Templates", "Template_Wyvern_Mini_Flying_Tamed.json");
    private static final List<String> ROLES = List.of("Wild", "Nature", "Toxic", "Fire", "Void", "Lightning", "Ice");

    @Test
    void templateContainsTalentGatedExecutableVariants() throws IOException {
        JsonObject template = load(TEMPLATE);
        JsonArray instructions = template.getAsJsonArray("Instructions");

        assertTrue(hasTalentGate(instructions, "DraconicProjectile"));
        assertTrue(hasTalentGate(instructions, "ProjectileForce"));
        assertTrue(hasTalentGate(instructions, "DraconicApex"));
        assertTrue(hasExecutableAction(instructions));
        assertTrue(hasHigherTierExclusion(instructions));
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
        String wild = Files.readString(Path.of("Server", "Item", "Interactions", "NPCs", "HyDragon",
                "Wyvern_Mini", "Wyvern_Mini_Wild_Projectile.json"));
        for (String status : List.of("Fire", "Ice", "Lightning", "Nature", "Toxic", "Void")) {
            assertFalse(wild.contains(status), "Wild projectile must remain raw-only: " + status);
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

    private static boolean hasHigherTierExclusion(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if ("Not".equals(string(object, "Type")) && hasTalentGate(object, "ProjectileForce")) return true;
            for (JsonElement child : object.asMap().values()) if (hasHigherTierExclusion(child)) return true;
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (hasHigherTierExclusion(child)) return true;
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
