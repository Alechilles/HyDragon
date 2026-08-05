package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Contract for element-specific Miniwyvern projectile impact audio. */
final class MiniwyvernProjectileImpactAudioAssetTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", ".")).resolve("src/main/resources");
    private static final Path PROJECTILES = ROOT.resolve(
            Path.of("Server", "ProjectileConfigs", "HyDragon", "Wyvern_Mini"));
    private static final Path ROOT_INTERACTIONS = ROOT.resolve(
            Path.of("Server", "Item", "RootInteractions", "NPCs", "HyDragon", "Wyvern_Mini",
                    "ProjectileHits"));
    private static final Path HIT_INTERACTIONS = ROOT.resolve(
            Path.of("Server", "Item", "Interactions", "NPCs", "HyDragon", "Wyvern_Mini",
                    "ProjectileHits"));
    private static final Map<String, String> IMPACT_EVENTS = Map.of(
            "Fire", "SFX_Staff_Flame_Fireball_Impact",
            "Ice", "SFX_Ice_Bolt_Death",
            "Lightning", "SFX_Spear_Projectile_Impact",
            "Nature", "SFX_Plant_Hit",
            "Toxic", "SFX_Effect_Poison_World",
            "Void", "SFX_Eye_Void_Attack_Blast",
            "Wild", "SFX_Rubble_Hit");

    @Test
    void everyElementalProjectilePlaysItsMappedImpactSoundForEveryCollisionRoute()
            throws IOException {
        List<Path> configs;
        try (Stream<Path> paths = Files.list(PROJECTILES)) {
            configs = paths.filter(path -> path.getFileName().toString()
                            .startsWith("Projectile_Config_HyDragon_Miniwyvern_"))
                    .sorted().toList();
        }
        assertEquals(42, configs.size(), "seven elements must retain six projectile variants each");
        for (Path configPath : configs) {
            String element = configPath.getFileName().toString().split("_")[4];
            String event = IMPACT_EVENTS.get(element);
            assertNotNull(event, "unknown Miniwyvern projectile element: " + configPath);
            JsonObject interactions = load(configPath).getAsJsonObject("Interactions");
            assertMissRoute(interactions.getAsJsonObject("ProjectileMiss"), event, configPath);
            assertBounceRoute(interactions.getAsJsonObject("ProjectileBounce"), event, configPath);
            assertHitRoute(interactions.get("ProjectileHit").getAsString(), event, configPath);
        }
    }

    private static void assertMissRoute(JsonObject route, String event, Path configPath) {
        assertNotNull(route, "ProjectileMiss must remain configured: " + configPath);
        JsonArray actions = route.getAsJsonArray("Interactions");
        assertEquals(2, actions.size(), "miss must play sound then remove projectile: " + configPath);
        assertSimpleWorldSound(actions.get(0).getAsJsonObject(), event, configPath);
        JsonObject removal = actions.get(1).getAsJsonObject();
        assertEquals("RemoveEntity", removal.get("Type").getAsString(), configPath.toString());
        assertEquals("User", removal.get("Entity").getAsString(), configPath.toString());
    }

    private static void assertBounceRoute(JsonObject route, String event, Path configPath) {
        assertNotNull(route, "ProjectileBounce must cover continuing terrain collision: " + configPath);
        JsonArray actions = route.getAsJsonArray("Interactions");
        assertEquals(1, actions.size(), "bounce must only emit sound and continue: " + configPath);
        assertSimpleWorldSound(actions.get(0).getAsJsonObject(), event, configPath);
    }

    private static void assertHitRoute(String rootId, String event, Path configPath) throws IOException {
        JsonArray rootActions = load(ROOT_INTERACTIONS.resolve(rootId + ".json"))
                .getAsJsonArray("Interactions");
        assertEquals(1, rootActions.size(), "hit root must resolve one gameplay interaction: " + configPath);
        String interactionId = rootActions.get(0).getAsString();
        JsonObject interaction = load(HIT_INTERACTIONS.resolve(interactionId + ".json"));
        assertEquals("Parallel", interaction.get("Type").getAsString(), interactionId);
        JsonArray branches = interaction.getAsJsonArray("Interactions");
        assertEquals(2, branches.size(), interactionId + " must contain sound and damage branches");
        JsonObject soundBranch = branches.get(0).getAsJsonObject();
        assertEquals(Set.of("Interactions"), soundBranch.keySet(), interactionId);
        assertSimpleWorldSound(soundBranch.getAsJsonArray("Interactions").get(0).getAsJsonObject(), event,
                HIT_INTERACTIONS.resolve(interactionId + ".json"));
        JsonObject damageBranch = branches.get(1).getAsJsonObject();
        assertEquals(Set.of("Interactions"), damageBranch.keySet(), interactionId);
        JsonObject damage = damageBranch.getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("DamageEntity", damage.get("Type").getAsString(), interactionId);
        assertNoElementImpactSound(damage, interactionId);
    }

    private static void assertSimpleWorldSound(JsonObject action, String event, Path path) {
        assertEquals("Simple", action.get("Type").getAsString(), path.toString());
        assertEquals(0.0, action.get("RunTime").getAsDouble(), 0.0, path.toString());
        assertEquals(event, action.getAsJsonObject("Effects")
                .get("WorldSoundEventId").getAsString(), path.toString());
    }

    private static void assertNoElementImpactSound(JsonElement element, String interactionId) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                assertNoElementImpactSound(child, interactionId);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("WorldSoundEventId")) {
            assertTrue(!IMPACT_EVENTS.containsValue(object.get("WorldSoundEventId").getAsString()),
                    interactionId + " must not add elemental audio to a damage outcome branch");
        }
        for (var entry : object.entrySet()) {
            assertNoElementImpactSound(entry.getValue(), interactionId);
        }
    }

    private static JsonObject load(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
