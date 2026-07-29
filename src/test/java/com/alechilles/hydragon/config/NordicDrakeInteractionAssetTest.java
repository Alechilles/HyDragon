package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    private static JsonObject findMount(JsonArray interactions) {
        for (var interaction : interactions) {
            JsonObject candidate = interaction.getAsJsonObject();
            if ("Mount".equals(candidate.get("Type").getAsString())) return candidate;
        }
        throw new AssertionError("missing Mount interaction");
    }
}
