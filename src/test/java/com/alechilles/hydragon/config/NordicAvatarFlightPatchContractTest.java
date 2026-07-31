package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.assets.patches.AssetPatchDefinition;
import com.alechilles.alecstamework.assets.patches.AssetPatchEngine;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NordicAvatarFlightPatchContractTest {
    private static final Path PATCH_PATH = Path.of(
            "Server", "Tamework", "Patches", "HyDragonRoles",
            "Tamed_NordicDrake_AvatarFlight.json");

    @Test
    void cleanPatchWiresAvatarFlightWithoutEditingTheAuthoredNordicRole() throws IOException {
        JsonObject patchJson = JsonParser.parseString(Files.readString(PATCH_PATH)).getAsJsonObject();
        AssetPatchDefinition patch = AssetPatchDefinition.parse(
                patchJson, "Alechilles:HyDragon", PATCH_PATH.toString().replace('\\', '/'));
        JsonObject unpatchedRole = JsonParser.parseString("""
                {
                  "Type": "Variant",
                  "Reference": "Template_HyDragon_Dragon_Tamed",
                  "Modify": {
                    "IsMountable": true,
                    "InteractionConfigId": "HyDragonIntDragon"
                  }
                }
                """).getAsJsonObject();

        AssetPatchEngine.PatchResult result = new AssetPatchEngine().apply(
                unpatchedRole, List.of(patch));
        JsonObject modify = result.patched().getAsJsonObject("Modify");

        assertEquals("TameworkAvatarFlight", modify.get("MountMode").getAsString());
        assertEquals("HyDragonNordicDrake", modify.get("AvatarFlightConfig").getAsString());
        assertTrue(modify.get("IsMountable").getAsBoolean());
        assertEquals("HyDragonIntDragon", modify.get("InteractionConfigId").getAsString());
    }

    @Test
    void avatarFlightConfigMapsNativeCombatSlotsToPlayerSafeNordicRoots() throws IOException {
        JsonObject config = JsonParser.parseString(Files.readString(Path.of(
                "Server", "Tamework", "AvatarFlight", "HyDragonNordicDrake.json"))).getAsJsonObject();
        JsonObject abilities = config.getAsJsonObject("CombatAbilities");

        assertEquals("Root_NPC_NordicDrake_Avatar_Fire_Ball",
                abilities.getAsJsonObject("Ability2").get("RootInteraction").getAsString());
        assertEquals("FIRE", abilities.getAsJsonObject("Ability2").get("Glyph").getAsString());
        assertEquals("Root_NPC_NordicDrake_Avatar_Flying_Flame_Breath",
                abilities.getAsJsonObject("Ability3").get("RootInteraction").getAsString());
        assertEquals("BREATH", abilities.getAsJsonObject("Ability3").get("Glyph").getAsString());
    }
}
