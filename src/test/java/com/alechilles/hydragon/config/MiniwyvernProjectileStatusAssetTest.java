package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract for the dedicated Miniwyvern projectile statuses. */
final class MiniwyvernProjectileStatusAssetTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", ".")).resolve("src/main/resources");

    @Test
    void projectileStatusesHaveExactDurationsAndControlBoundaries() throws IOException {
        JsonObject lightning = load("HyDragon_Miniwyvern_Lightning_Shock");
        assertDuration(lightning, 0.5D);
        JsonArray disabled = lightning.getAsJsonObject("ApplicationEffects")
                .getAsJsonObject("AbilityEffects").getAsJsonArray("Disabled");
        assertEquals(java.util.List.of("Primary", "Secondary", "Ability1", "Ability2", "Ability3"),
                disabled.asList().stream().map(element -> element.getAsString()).toList());
        assertFalse(lightning.getAsJsonObject("ApplicationEffects").has("MovementEffects"));

        JsonObject nature = load("HyDragon_Miniwyvern_Nature_Root");
        assertDuration(nature, 0.6D);
        JsonObject movement = nature.getAsJsonObject("ApplicationEffects").getAsJsonObject("MovementEffects");
        for (String input : java.util.List.of("DisableForward", "DisableBackward", "DisableLeft", "DisableRight")) {
            assertTrue(movement.get(input).getAsBoolean());
        }
        assertFalse(movement.has("DisableJump"));
        assertFalse(movement.has("DisableCrouch"));
        assertFalse(nature.getAsJsonObject("ApplicationEffects").has("AbilityEffects"));

        assertMarker(load("HyDragon_Miniwyvern_Toxic_Projectile_Weakness"), 5.0D);
        assertMarker(load("HyDragon_Miniwyvern_Void_Projectile_Exposure"), 5.0D);
        JsonObject bondVoid = load("HyDragon_Miniwyvern_Void_Exposure");
        assertMarker(bondVoid, 6.0D);
        assertFalse(bondVoid.has("DamageResistance"));
    }

    @Test
    void authoredProjectileStatusModelVfxIdsResolveLocally() throws IOException {
        for (String effectId : java.util.List.of(
                "HyDragon_Miniwyvern_Toxic_Projectile_Weakness",
                "HyDragon_Miniwyvern_Void_Projectile_Exposure",
                "HyDragon_Miniwyvern_Void_Exposure")) {
            String modelVfxId = load(effectId).getAsJsonObject("ApplicationEffects")
                    .get("ModelVFXId").getAsString();
            Path modelVfx = ROOT.resolve("Server/Entity/ModelVFX/" + modelVfxId + ".json");
            assertTrue(Files.isRegularFile(modelVfx),
                    effectId + " ModelVFXId must resolve to a bundled ModelVFX asset: " + modelVfxId);
        }
    }

    private static void assertMarker(JsonObject effect, double duration) {
        assertDuration(effect, duration);
        assertEquals("Overwrite", effect.get("OverlapBehavior").getAsString());
        assertFalse(effect.has("DamageResistance"));
    }

    private static void assertDuration(JsonObject effect, double expected) {
        assertEquals(expected, effect.get("Duration").getAsDouble());
        assertEquals("Overwrite", effect.get("OverlapBehavior").getAsString());
        assertEquals("Duration", effect.get("RemovalBehavior").getAsString());
        assertFalse(effect.get("Infinite").getAsBoolean());
        assertTrue(effect.get("Debuff").getAsBoolean());
    }

    private static JsonObject load(String effectId) throws IOException {
        try (var reader = Files.newBufferedReader(ROOT.resolve(
                "Server/Entity/Effects/Status/" + effectId + ".json"))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
