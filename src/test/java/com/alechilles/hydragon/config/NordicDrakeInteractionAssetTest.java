package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NordicDrakeInteractionAssetTest {
    private static final Path INTERACTION_PATH = Path.of(
            "Server", "Tamework", "Interactions", "HyDragonIntDragon.json");
    private static final String FLIGHTMASTERS_TALISMAN = "Tamework_Flightmasters_Talisman";

    @Test
    void mountRequiresTheFlightmastersTalismanInHand() throws IOException {
        JsonObject interactionConfig = JsonParser.parseString(Files.readString(INTERACTION_PATH)).getAsJsonObject();
        JsonObject mount = findMount(interactionConfig.getAsJsonArray("Interactions"));

        JsonObject requirements = mount.getAsJsonObject("Requires");
        assertNotNull(requirements);
        JsonObject allRequirements = requirements.getAsJsonObject("All");
        assertNotNull(allRequirements);
        JsonArray heldItemRequirements = allRequirements.getAsJsonArray("ItemsInHand");
        assertNotNull(heldItemRequirements);
        assertEquals(1, heldItemRequirements.size());
        assertEquals(FLIGHTMASTERS_TALISMAN,
                heldItemRequirements.get(0).getAsJsonObject().getAsJsonArray("Items").get(0).getAsString());
    }

    @Test
    void avatarCombatRootsResolveOnlyPlayerSafeInteractionChains() throws IOException {
        assertRootResolvesPlayerSafeInteraction(
                "Root_NPC_NordicDrake_Avatar_Fire_Ball",
                "NordicDrake_Avatar_Fire_Ball");
        assertRootResolvesPlayerSafeInteraction(
                "Root_NPC_NordicDrake_Avatar_Flying_Flame_Breath",
                "NordicDrake_Avatar_Flying_Flame_Breath");
    }

    @Test
    void avatarFireballUsesLookTargetingAndFlameBreathRetainsForwardDamageWithoutNpcTargeting()
            throws IOException {
        JsonObject fireball = readInteraction("NordicDrake_Avatar_Fire_Ball");
        JsonArray steps = fireball.getAsJsonArray("Interactions");
        JsonObject charge = steps.get(0).getAsJsonObject();
        assertEquals("Simple", charge.get("Type").getAsString());
        JsonObject chargeEffects = charge.getAsJsonObject("Effects");
        assertEquals("ChargeShoot", chargeEffects.get("ItemAnimationId").getAsString());
        assertEquals("SFX_HyDragon_NordicDrake_Avatar_Fireball_Roar",
                chargeEffects.get("WorldSoundEventId").getAsString());
        assertEquals(0.35, charge.get("RunTime").getAsDouble());

        JsonObject launch = steps.get(1).getAsJsonObject();
        assertEquals("TameworkLaunchProjectile", launch.get("Type").getAsString());
        assertEquals(48.0, launch.get("LookTargetDistance").getAsDouble());
        assertFalse(launch.has("TargetSlot"));
        JsonObject launchOffset = launch.getAsJsonObject("LaunchPositionOffset");
        assertEquals(-1.0, launchOffset.get("Y").getAsDouble());
        assertEquals(-3.0, launchOffset.get("Z").getAsDouble());
        JsonObject launchSound = steps.get(2).getAsJsonObject();
        assertEquals("Simple", launchSound.get("Type").getAsString());
        assertEquals("SFX_HyDragon_NordicDrake_Avatar_Fireball_Launch",
                launchSound.getAsJsonObject("Effects").get("WorldSoundEventId").getAsString());
        assertEquals(0, launchSound.get("RunTime").getAsInt());
        assertFalse(fireball.toString().contains("PrepareShoot"));
        assertSoundEvent("SFX_HyDragon_NordicDrake_Avatar_Fireball_Roar", "Avatar_Fireball_Roar.ogg");
        assertSoundEvent("SFX_HyDragon_NordicDrake_Avatar_Fireball_Launch", "Avatar_Fireball_Launch.ogg");

        JsonObject flameBreath = readInteraction("NordicDrake_Avatar_Flying_Flame_Breath");
        JsonArray flameBreathSteps = flameBreath.getAsJsonArray("Interactions");
        assertEquals("Parallel", flameBreathSteps.get(0).getAsJsonObject().get("Type").getAsString());
        JsonObject effects = flameBreathSteps.get(0).getAsJsonObject().getAsJsonArray("Interactions")
                .get(0).getAsJsonObject().getAsJsonArray("Interactions")
                .get(1).getAsJsonObject().getAsJsonObject("Effects");
        assertEquals(4.05, effects.getAsJsonArray("Particles").get(0).getAsJsonObject()
                .getAsJsonObject("PositionOffset").get("Z").getAsDouble());
        String flameBreathJson = flameBreath.toString();
        assertTrue(flameBreathJson.contains("\"Type\":\"Selector\""));
        assertTrue(flameBreathJson.contains("\"NordicDrake_Flame_Breath_Damage\""));
        assertFalse(flameBreathJson.contains("LockedTarget"));
        assertFalse(flameBreathJson.contains("\"TargetSlot\""));
    }

    private static JsonObject findMount(JsonArray interactions) {
        for (var interaction : interactions) {
            JsonObject candidate = interaction.getAsJsonObject();
            if ("Mount".equals(candidate.get("Type").getAsString())) return candidate;
        }
        throw new AssertionError("missing Mount interaction");
    }

    private static void assertRootResolvesPlayerSafeInteraction(String rootId, String interactionId)
            throws IOException {
        Path rootPath = Path.of("Server", "Item", "RootInteractions", "NPCs", "Creature", "HyDragon",
                rootId + ".json");
        JsonObject root = JsonParser.parseString(Files.readString(rootPath)).getAsJsonObject();
        assertEquals(interactionId, root.getAsJsonArray("Interactions").get(0).getAsString());

        String interactionJson = Files.readString(interactionPath(interactionId));
        assertFalse(interactionJson.contains("TargetSlot: LockedTarget"));
        assertFalse(interactionJson.contains("\"TargetSlot\": \"LockedTarget\""));
    }

    private static JsonObject readInteraction(String interactionId) throws IOException {
        return JsonParser.parseString(Files.readString(interactionPath(interactionId))).getAsJsonObject();
    }

    private static void assertSoundEvent(String soundEventId, String soundFileName) throws IOException {
        Path soundEventPath = Path.of("Server", "Audio", "SoundEvents", "SFX", "HyDragon", "NordicDrake",
                soundEventId + ".json");
        JsonObject soundEvent = JsonParser.parseString(Files.readString(soundEventPath)).getAsJsonObject();
        assertEquals("SFX_Attn_Quiet", soundEvent.get("Parent").getAsString());
        assertEquals("Sounds/HyDragon/NordicDrake/" + soundFileName,
                soundEvent.getAsJsonArray("Layers").get(0).getAsJsonObject().getAsJsonArray("Files")
                        .get(0).getAsString());
        Path soundPath = Path.of("Common", "Sounds", "HyDragon", "NordicDrake", soundFileName);
        assertTrue(Files.isRegularFile(soundPath));
        assertMonoVorbis(soundPath);
    }

    private static void assertMonoVorbis(Path soundPath) throws IOException {
        byte[] bytes = Files.readAllBytes(soundPath);
        byte[] identificationHeader = {1, 'v', 'o', 'r', 'b', 'i', 's'};
        for (int index = 0; index <= bytes.length - identificationHeader.length - 5; index++) {
            boolean matches = true;
            for (int offset = 0; offset < identificationHeader.length; offset++) {
                if (bytes[index + offset] != identificationHeader[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                assertEquals(1, Byte.toUnsignedInt(bytes[index + identificationHeader.length + 4]));
                return;
            }
        }
        throw new AssertionError("missing Vorbis identification header in " + soundPath);
    }

    private static Path interactionPath(String interactionId) {
        return Path.of("Server", "Item", "Interactions", "NPCs", "HyDragon", "NordicDrake",
                interactionId + ".json");
    }
}
