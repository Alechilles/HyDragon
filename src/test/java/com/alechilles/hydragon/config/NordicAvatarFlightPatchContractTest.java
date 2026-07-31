package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void avatarFlightModelKeepsRiderArmorOnTheRiderAndOutOfMountPoseTracks() throws IOException {
        JsonObject config = JsonParser.parseString(Files.readString(Path.of(
                "Server", "Tamework", "AvatarFlight", "HyDragonNordicDrake.json"))).getAsJsonObject();
        JsonObject riderVisual = config.getAsJsonObject("RiderVisual");
        assertTrue(riderVisual.get("ShowRider").getAsBoolean());
        assertTrue(riderVisual.get("IncludeAppearanceAttachments").getAsBoolean());
        assertTrue(riderVisual.get("HideOwnerEquipment").getAsBoolean());
        assertTrue(riderVisual.get("HideOwnerArmor").getAsBoolean());

        String model = Files.readString(Path.of(
                "Common", "NPC", "HyDragon", "NordicDrake", "Model",
                "NordicDrake_AvatarFlight.blockymodel"));

        assertTrue(model.contains("\"name\": \"AF_Origin\""));
        assertFalse(model.contains("\"name\": \"Origin\""));
        Path animationRoot = Path.of(
                "Common", "NPC", "HyDragon", "NordicDrake", "Animations", "AvatarFlight");
        List<Path> clips;
        try (var files = Files.list(animationRoot)) {
            clips = files.filter(path -> path.toString().endsWith(".blockyanim")).toList();
        }
        assertFalse(clips.isEmpty());

        int mountRootTrackCount = 0;
        for (Path clip : clips) {
            JsonObject animation = JsonParser.parseString(Files.readString(clip)).getAsJsonObject();
            JsonObject nodes = animation.getAsJsonObject("nodeAnimations");
            assertFalse(nodes.has("Origin"), clip.toString());
            if (nodes.has("AF_Origin")) {
                mountRootTrackCount++;
            }
        }
        assertTrue(mountRootTrackCount > 0);
    }
}
