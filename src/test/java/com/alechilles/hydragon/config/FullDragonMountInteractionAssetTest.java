package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

final class FullDragonMountInteractionAssetTest {
    private static final String REVITALIZING_ESSENCE = "Revitalizing_Essence";
    private static final int REVITALIZING_ESSENCE_HEAL = 60;

    @Test
    void fullDragonInteractionsReserveCrouchForMountingAndPlainFForModeCycling() throws IOException {
        assertInteractionContract("HyDragonIntRockDrake.json",
                List.of("Tamed_RockDrakeT1", "Tamed_RockDrakeT2", "Tamed_RockDrakeT3"),
                List.of("Follow", "Hold", "Defend", "Aggressive", "Idle"));
        assertInteractionContract("HyDragonIntBeast.json",
                List.of("Tamed_Hydra", "Tamed_Hydra_Toxic"),
                List.of("Follow", "Hold", "Idle"));
        assertInteractionContract("HyDragonIntDragon.json",
                List.of("Tamed_NordicDrake"),
                List.of("Follow", "Hold", "Idle"));
    }

    @Test
    void rockDrakesAndIceHydraKeepNativeMountWiring() throws IOException {
        for (String tier : List.of("Tamed_RockDrakeT1", "Tamed_RockDrakeT2", "Tamed_RockDrakeT3")) {
            JsonObject role = json(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "RockDrake",
                    tier + ".json"));
            assertTrue(role.getAsJsonObject("Modify").get("IsMountable").getAsBoolean());
        }

        JsonObject iceHydra = json(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Hydra",
                "Tamed_Hydra.json"));
        assertTrue(iceHydra.getAsJsonObject("Modify").get("IsMountable").getAsBoolean());
        assertEquals("", iceHydra.getAsJsonObject("Parameters").getAsJsonObject("MountMode")
                .get("Value").getAsString());
    }

    private static void assertInteractionContract(String file, List<String> roles, List<String> cycle)
            throws IOException {
        JsonObject config = json(Path.of("Server", "Tamework", "Interactions", file));
        assertEquals(roles, strings(config.getAsJsonArray("RoleIds")));

        JsonArray interactions = config.getAsJsonArray("Interactions");
        JsonObject mount = interactions.get(0).getAsJsonObject();
        assertEquals("Mount", mount.get("Type").getAsString());
        assertTrue(mount.get("RequireCrouching").getAsBoolean());

        JsonObject feed = interactions.get(1).getAsJsonObject();
        assertEquals("Feed", feed.get("Type").getAsString());
        assertTrue(feed.get("Enabled").getAsBoolean());
        assertTrue(!feed.get("UseLovedItems").getAsBoolean());
        JsonObject item = feed.getAsJsonArray("ItemsInHand").get(0).getAsJsonObject();
        assertEquals(REVITALIZING_ESSENCE, item.get("Item").getAsString());
        assertEquals(REVITALIZING_ESSENCE_HEAL, item.get("Heal").getAsInt());
        assertTrue(feed.getAsJsonObject("Requires").getAsJsonObject("All")
                .get("PlayerIsOwner").getAsBoolean());

        JsonObject modeCycle = interactions.get(2).getAsJsonObject();
        assertEquals("ModeCycle", modeCycle.get("Type").getAsString());
        assertEquals(cycle, modeCycle.getAsJsonArray("Cycle").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(entry -> entry.get("State").getAsString()).toList());
        assertEquals(3, interactions.size(), file + " must not retain unreachable ordinary-F interactions");
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<String> strings(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }
}
