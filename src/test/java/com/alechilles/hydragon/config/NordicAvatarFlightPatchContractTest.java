package com.alechilles.hydragon.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class NordicAvatarFlightPatchContractTest {
    @Test
    void avatarFlightConfigMapsNativeCombatSlotsToPlayerSafeNordicRoots() throws IOException {
        JsonObject config = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources", "Server", "Tamework", "AvatarFlight", "HyDragonNordicDrake.json"))).getAsJsonObject();
        JsonObject abilities = config.getAsJsonObject("CombatAbilities");

        assertEquals("Root_NPC_Tamed_NordicDrake_Avatar_Fire_Ball",
                abilities.getAsJsonObject("Ability2").get("RootInteraction").getAsString());
        assertEquals("FIRE", abilities.getAsJsonObject("Ability2").get("Glyph").getAsString());
        assertEquals("HyDragon/AvatarFlightIcons/NordicFireball.png",
                abilities.getAsJsonObject("Ability2").get("GlyphTexturePath").getAsString());
        assertEquals(15.0, abilities.getAsJsonObject("Ability2").get("CooldownSeconds").getAsDouble());
        assertEquals("Root_NPC_NordicDrake_Avatar_Flying_Flame_Breath",
                abilities.getAsJsonObject("Ability3").get("RootInteraction").getAsString());
        assertEquals("BREATH", abilities.getAsJsonObject("Ability3").get("Glyph").getAsString());
        assertEquals("HyDragon/AvatarFlightIcons/NordicFireBreath.png",
                abilities.getAsJsonObject("Ability3").get("GlyphTexturePath").getAsString());
        assertEquals(15.0, abilities.getAsJsonObject("Ability3").get("CooldownSeconds").getAsDouble());
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources", "Common", "UI", "Custom", "HyDragon", "AvatarFlightIcons", "NordicFireball.png")));
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources", "Common", "UI", "Custom", "HyDragon", "AvatarFlightIcons", "NordicFireBreath.png")));
    }

    @Test
    void avatarFlightModelKeepsRiderArmorOnTheRiderAndOutOfMountPoseTracks() throws IOException {
        JsonObject config = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources", "Server", "Tamework", "AvatarFlight", "HyDragonNordicDrake.json"))).getAsJsonObject();
        JsonObject riderVisual = config.getAsJsonObject("RiderVisual");
        assertTrue(riderVisual.get("ShowRider").getAsBoolean());
        assertTrue(riderVisual.get("IncludeAppearanceAttachments").getAsBoolean());
        assertTrue(riderVisual.get("HideOwnerEquipment").getAsBoolean());
        assertTrue(riderVisual.get("HideOwnerArmor").getAsBoolean());

        String model = Files.readString(Path.of(
                "src/main/resources", "Common", "NPC", "HyDragon", "NordicDrake", "Model",
                "NordicDrake_AvatarFlight.blockymodel"));

        assertTrue(model.contains("\"name\": \"AF_Origin\""));
        assertFalse(model.contains("\"name\": \"Origin\""));
        Path animationRoot = Path.of(
                "src/main/resources", "Common", "NPC", "HyDragon", "NordicDrake", "Animations", "AvatarFlight");
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
