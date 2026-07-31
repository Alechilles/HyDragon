package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MiniwyvernProjectileBalanceAssetTest {
    private static final Path CONFIG_ROOT = Path.of("Server", "ProjectileConfigs", "HyDragon", "Wyvern_Mini");
    private static final Path ROOT_ROOT = Path.of("Server", "Item", "RootInteractions", "NPCs", "HyDragon", "Wyvern_Mini");
    private static final Path INTERACTION_ROOT = Path.of("Server", "Item", "Interactions", "NPCs", "HyDragon", "Wyvern_Mini");
    private static final Map<String, FormProfile> FORMS = forms();

    // A wrong projectile profile, status route, or legacy launch type must fail this contract.
    @Test
    void formOwnedProjectilesUseTheApprovedDamagePhysicsAndTerminalSafetyChains() throws IOException {
        for (Map.Entry<String, FormProfile> entry : FORMS.entrySet()) {
            String form = entry.getKey();
            FormProfile profile = entry.getValue();
            assertRoot(form, "Base", List.of(configId(form, "Base")));
            assertRoot(form, "Intermediate", List.of(configId(form, "Intermediate")));
            assertSerialRoot(form, "Pattern", configId(form, "Pattern_First"), configId(form, "Pattern_Echo"));
            assertSerialRoot(form, "Mastery", configId(form, "Mastery_First"), configId(form, "Mastery_Echo"));

            assertConfig(form, "Base", profile.baseDamage(), 28, 32, 6, 4.0, profile.status(), true);
            assertConfig(form, "Intermediate", profile.intermediateDamage(), 34, 40, 4, 5.0, profile.status(), true);
            assertConfig(form, "Pattern_First", profile.intermediateDamage(), 34, 40, 4, 5.0, profile.status(), true);
            assertConfig(form, "Pattern_Echo", profile.intermediateDamage(), 34, 40, 4, 5.0, null, false);
            assertConfig(form, "Mastery_First", profile.apexDamage(), 40, 48, 3, 6.0, profile.status(), true);
            assertConfig(form, "Mastery_Echo", profile.apexDamage(), 40, 48, 3, 6.0, null, false);
        }
    }

    @Test
    void miniwyvernNamespaceContainsNoDeprecatedProjectileLaunchOrLegacyProfiles() throws IOException {
        Path legacyRoot = Path.of("Server", "Projectiles", "HyDragon", "Wyvern_Mini");
        if (Files.isDirectory(legacyRoot)) {
            try (var profiles = Files.list(legacyRoot)) {
                assertEquals(0, profiles.filter(path -> path.getFileName().toString().endsWith(".json")).count(),
                        "legacy profiles remain");
            }
        }
        try (var paths = Files.walk(INTERACTION_ROOT)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("LaunchProjectile"), path + " uses deprecated LaunchProjectile");
                assertFalse(source.contains("ProjectileId"), path + " uses deprecated ProjectileId");
            }
        }
    }

    private static void assertRoot(String form, String tier, List<String> configs) throws IOException {
        JsonObject root = load(ROOT_ROOT.resolve("Root_NPC_Wyvern_Mini_" + form + "_Projectile_" + tier + ".json"));
        assertEquals(List.of("Ranged"), strings(root.getAsJsonObject("Tags").getAsJsonArray("Attack")));
        JsonObject interaction = load(INTERACTION_ROOT.resolve("Wyvern_Mini_" + form + "_Projectile_" + tier + ".json"));
        assertEquals("Projectile", interaction.get("Type").getAsString());
        assertEquals(configs.getFirst(), interaction.get("Config").getAsString());
    }

    private static void assertSerialRoot(String form, String tier, String first, String echo) throws IOException {
        JsonObject root = load(ROOT_ROOT.resolve("Root_NPC_Wyvern_Mini_" + form + "_Projectile_" + tier + ".json"));
        assertEquals(List.of("Ranged"), strings(root.getAsJsonObject("Tags").getAsJsonArray("Attack")));
        JsonObject serial = load(INTERACTION_ROOT.resolve("Wyvern_Mini_" + form + "_Projectile_" + tier + ".json"));
        assertEquals("Serial", serial.get("Type").getAsString());
        JsonArray launches = serial.getAsJsonArray("Interactions");
        assertEquals(3, launches.size());
        assertEquals("Projectile", launches.get(0).getAsJsonObject().get("Type").getAsString());
        assertEquals(first, launches.get(0).getAsJsonObject().get("Config").getAsString());
        assertEquals("Simple", launches.get(1).getAsJsonObject().get("Type").getAsString());
        assertEquals(0.30, launches.get(1).getAsJsonObject().get("RunTime").getAsDouble());
        assertEquals("Projectile", launches.get(2).getAsJsonObject().get("Type").getAsString());
        assertEquals(echo, launches.get(2).getAsJsonObject().get("Config").getAsString());
    }

    private static void assertConfig(String form, String tier, int damage, int force, int terminalVelocity,
                                     int gravity, double timeout, String status, boolean first) throws IOException {
        JsonObject config = load(CONFIG_ROOT.resolve(configId(form, tier) + ".json"));
        assertNotNull(config.get("Model"));
        assertEquals(force, config.get("LaunchForce").getAsInt());
        assertEquals(0, config.getAsJsonObject("SpawnOffset").get("X").getAsInt());
        assertEquals(0, config.getAsJsonObject("SpawnOffset").get("Y").getAsInt());
        assertEquals(1, config.getAsJsonObject("SpawnOffset").get("Z").getAsInt());
        JsonObject physics = config.getAsJsonObject("Physics");
        assertEquals("Standard", physics.get("Type").getAsString());
        assertEquals(gravity, physics.get("Gravity").getAsInt());
        assertEquals(terminalVelocity, physics.get("TerminalVelocityAir").getAsInt());
        assertFalse(config.has("Parent"), "adult projectile inheritance in " + form + " " + tier);
        assertFalse(config.has("Damage"), "damage belongs only in the hit interaction for " + form + " " + tier);
        assertFalse(config.has("Splash") || config.has("BlockDamage") || config.has("Knockback")
                || config.has("Impact"), "forbidden projectile behavior in " + form + " " + tier);

        JsonObject interactions = config.getAsJsonObject("Interactions");
        JsonObject hit = interactions.getAsJsonObject("ProjectileHit");
        JsonObject damageInteraction = hit.getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("DamageEntity", damageInteraction.get("Type").getAsString());
        assertEquals(damage, damageInteraction.getAsJsonObject("DamageCalculator")
                .getAsJsonObject("BaseDamage").get("Physical").getAsInt());
        assertEquals(0, damageInteraction.getAsJsonObject("DamageCalculator").get("RandomPercentageModifier").getAsInt());
        assertTerminal(damageInteraction.getAsJsonObject("Failed"));
        assertTerminal(damageInteraction.getAsJsonObject("Blocked"));
        JsonObject next = damageInteraction.getAsJsonObject("Next");
        assertNotNull(next, "accepted damage must own terminal chain");
        String serializedNext = next.toString();
        if (status == null || !first) {
            assertFalse(serializedNext.contains("ApplyEffect"), "echo/wild must not apply status");
        } else {
            assertTrue(serializedNext.contains(status), "first hit must apply its status");
            if (form.equals("Lightning")) {
                assertTrue(serializedNext.contains("\"Type\":\"Interrupt\""));
                assertTrue(serializedNext.contains("\"ExcludedTag\":\"Uninterruptable\""));
            }
        }
        assertTerminal(lastInteraction(next));
        assertTerminal(interactions.getAsJsonObject("ProjectileMiss").getAsJsonArray("Interactions").get(0).getAsJsonObject());
        JsonArray timeoutInteractions = interactions.getAsJsonObject("ProjectileSpawn").getAsJsonArray("Interactions");
        assertEquals(2, timeoutInteractions.size());
        assertEquals("Simple", timeoutInteractions.get(0).getAsJsonObject().get("Type").getAsString());
        assertEquals(timeout, timeoutInteractions.get(0).getAsJsonObject().get("RunTime").getAsDouble());
        assertTerminal(timeoutInteractions.get(1).getAsJsonObject());
    }

    private static JsonObject lastInteraction(JsonObject chain) {
        if ("RemoveEntity".equals(chain.get("Type").getAsString())) {
            return chain;
        }
        return chain.getAsJsonArray("Interactions").get(chain.getAsJsonArray("Interactions").size() - 1).getAsJsonObject();
    }

    private static void assertTerminal(JsonObject interaction) {
        assertEquals("RemoveEntity", interaction.get("Type").getAsString());
        assertEquals("User", interaction.get("Entity").getAsString());
    }

    private static String configId(String form, String tier) {
        return "Projectile_Config_HyDragon_Miniwyvern_" + form + "_" + tier;
    }

    private static JsonObject load(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }

    private static Map<String, FormProfile> forms() {
        Map<String, FormProfile> forms = new LinkedHashMap<>();
        forms.put("Fire", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Fire_Burn"));
        forms.put("Ice", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Ice_Slow"));
        forms.put("Lightning", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Lightning_Shock"));
        forms.put("Nature", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Nature_Root"));
        forms.put("Toxic", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Toxic_Projectile_Weakness"));
        forms.put("Void", new FormProfile(8, 12, 10, "HyDragon_Miniwyvern_Void_Projectile_Exposure"));
        forms.put("Wild", new FormProfile(10, 15, 12, null));
        return Map.copyOf(forms);
    }

    private record FormProfile(int baseDamage, int intermediateDamage, int apexDamage, String status) {
    }
}
