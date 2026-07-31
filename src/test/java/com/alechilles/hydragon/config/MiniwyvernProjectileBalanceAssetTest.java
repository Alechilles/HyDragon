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
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MiniwyvernProjectileBalanceAssetTest {
    private static final Path CONFIG_ROOT = Path.of("Server", "ProjectileConfigs", "HyDragon", "Wyvern_Mini");
    private static final Path ROOT_ROOT = Path.of("Server", "Item", "RootInteractions", "NPCs", "HyDragon", "Wyvern_Mini");
    private static final Path INTERACTION_ROOT = Path.of("Server", "Item", "Interactions", "NPCs", "HyDragon", "Wyvern_Mini");
    private static final Map<String, FormProfile> FORMS = forms();

    // A wrong projectile profile, status route, or legacy launch type must fail this contract.
    @Test
    void formOwnedProjectilesUseTheApprovedDamagePhysicsAndTerminalSafetyChains() throws IOException {
        Map<String, Integer> hitReferences = new LinkedHashMap<>();
        for (Map.Entry<String, FormProfile> entry : FORMS.entrySet()) {
            String form = entry.getKey();
            FormProfile profile = entry.getValue();
            assertRoot(form, "Base", List.of(configId(form, "Base")));
            assertRoot(form, "Intermediate", List.of(configId(form, "Intermediate")));
            assertSerialRoot(form, "Pattern", configId(form, "Pattern_First"), configId(form, "Pattern_Echo"));
            assertSerialRoot(form, "Mastery", configId(form, "Mastery_First"), configId(form, "Mastery_Echo"));

            assertConfig(form, "Base", "Base", profile.baseDamage(), 28, 32, 6, 4.0, profile.status(), true, hitReferences);
            assertConfig(form, "Intermediate", "Intermediate_First", profile.intermediateDamage(), 34, 40, 4, 5.0, profile.status(), true, hitReferences);
            assertConfig(form, "Pattern_First", "Intermediate_First", profile.intermediateDamage(), 34, 40, 4, 5.0, profile.status(), true, hitReferences);
            assertConfig(form, "Pattern_Echo", "Intermediate_Echo", profile.intermediateDamage(), 34, 40, 4, 5.0, null, false, hitReferences);
            assertConfig(form, "Mastery_First", "Mastery_First", profile.apexDamage(), 40, 48, 3, 6.0, profile.status(), true, hitReferences);
            assertConfig(form, "Mastery_Echo", "Mastery_Echo", profile.apexDamage(), 40, 48, 3, 6.0, null, false, hitReferences);
        }
        for (String form : FORMS.keySet()) {
            assertEquals(1, hitReferences.get("Root_HyDragon_Miniwyvern_" + form + "_ProjectileHit_Base"));
            assertEquals(2, hitReferences.get("Root_HyDragon_Miniwyvern_" + form + "_ProjectileHit_Intermediate_First"));
            assertEquals(1, hitReferences.get("Root_HyDragon_Miniwyvern_" + form + "_ProjectileHit_Intermediate_Echo"));
            assertEquals(1, hitReferences.get("Root_HyDragon_Miniwyvern_" + form + "_ProjectileHit_Mastery_First"));
            assertEquals(1, hitReferences.get("Root_HyDragon_Miniwyvern_" + form + "_ProjectileHit_Mastery_Echo"));
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

    private static void assertConfig(String form, String tier, String hitTier, int damage, int force, int terminalVelocity,
                                     int gravity, double timeout, String status, boolean first,
                                     Map<String, Integer> hitReferences) throws IOException {
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
        String hitRootId = "Root_HyDragon_Miniwyvern_" + form + "_ProjectileHit_" + hitTier;
        JsonElement hitReference = interactions.get("ProjectileHit");
        assertTrue(hitReference.isJsonPrimitive() && hitReference.getAsJsonPrimitive().isString(),
                "ProjectileHit must be a root string in " + form + " " + tier);
        assertEquals(hitRootId, hitReference.getAsString());
        hitReferences.merge(hitRootId, 1, Integer::sum);
        JsonObject hitRoot = load(ROOT_ROOT.resolve("ProjectileHits").resolve(hitRootId + ".json"));
        assertEquals(List.of("HyDragon_Miniwyvern_" + form + "_ProjectileHit_" + hitTier),
                strings(hitRoot.getAsJsonArray("Interactions")));
        JsonObject damageInteraction = load(INTERACTION_ROOT.resolve("ProjectileHits")
                .resolve("HyDragon_Miniwyvern_" + form + "_ProjectileHit_" + hitTier + ".json"));
        assertEquals(Set.of("Type", "Entity", "DamageCalculator", "Next", "Failed", "Blocked"),
                damageInteraction.keySet(), "DamageEntity must not contain extra nested behavior");
        assertEquals("DamageEntity", damageInteraction.get("Type").getAsString());
        assertEquals(damage, damageInteraction.getAsJsonObject("DamageCalculator")
                .getAsJsonObject("BaseDamage").get("Physical").getAsInt());
        assertEquals(0, damageInteraction.getAsJsonObject("DamageCalculator").get("RandomPercentageModifier").getAsInt());
        assertTerminal(damageInteraction.getAsJsonObject("Failed"));
        assertTerminal(damageInteraction.getAsJsonObject("Blocked"));
        assertAcceptedChain(form, tier, damageInteraction.getAsJsonObject("Next"), status, first);
        assertTerminal(interactions.getAsJsonObject("ProjectileMiss").getAsJsonArray("Interactions").get(0).getAsJsonObject());
        JsonArray timeoutInteractions = interactions.getAsJsonObject("ProjectileSpawn").getAsJsonArray("Interactions");
        assertEquals(2, timeoutInteractions.size());
        assertEquals("Simple", timeoutInteractions.get(0).getAsJsonObject().get("Type").getAsString());
        assertEquals(timeout, timeoutInteractions.get(0).getAsJsonObject().get("RunTime").getAsDouble());
        assertTerminal(timeoutInteractions.get(1).getAsJsonObject());
    }

    private static void assertAcceptedChain(String form, String tier, JsonObject next, String status, boolean first) {
        assertNotNull(next, "accepted damage must own terminal chain");
        if (status == null || !first) {
            assertEquals("RemoveEntity", next.get("Type").getAsString(), "echo/wild must remove directly");
            assertTerminal(next);
            return;
        }
        assertEquals("Serial", next.get("Type").getAsString());
        JsonArray steps = next.getAsJsonArray("Interactions");
        if (form.equals("Lightning")) {
            assertEquals(3, steps.size(), "Lightning must interrupt, apply status, then remove");
            JsonObject interrupt = steps.get(0).getAsJsonObject();
            assertEquals(Set.of("Type", "Entity", "ExcludedTag"), interrupt.keySet());
            assertEquals("Interrupt", interrupt.get("Type").getAsString());
            assertEquals("Target", interrupt.get("Entity").getAsString());
            assertEquals("Uninterruptable", interrupt.get("ExcludedTag").getAsString());
            assertEffect(steps.get(1).getAsJsonObject(), status);
            assertTerminal(steps.get(2).getAsJsonObject());
        } else {
            assertEquals(2, steps.size(), form + " " + tier + " has an unexpected accepted-hit chain");
            assertEffect(steps.get(0).getAsJsonObject(), status);
            assertTerminal(steps.get(1).getAsJsonObject());
        }
    }

    private static void assertEffect(JsonObject interaction, String status) {
        assertEquals(Set.of("Type", "Entity", "EffectId"), interaction.keySet());
        assertEquals("ApplyEffect", interaction.get("Type").getAsString());
        assertEquals("Target", interaction.get("Entity").getAsString());
        assertEquals(status, interaction.get("EffectId").getAsString());
    }

    private static void assertTerminal(JsonObject interaction) {
        assertEquals(Set.of("Type", "Entity"), interaction.keySet());
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
