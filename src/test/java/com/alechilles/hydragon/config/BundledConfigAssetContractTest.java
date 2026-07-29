package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.JsonAsset;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BundledConfigAssetContractTest {
    private static final Path CONFIG_ROOT = Path.of("Server", "HyDragon");
    private static final Pattern TEXTURE_FIELD = Pattern.compile("\\\"Texture\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final List<String> MINI_WYVERN_FORMS = List.of(
            "Wild", "Nature", "Toxic", "Fire", "Void", "Lightning", "Ice");

    @Test
    void miniwyvernRoleSwapAssetsCoverEveryNonSelfDestinationAtExactCost() throws IOException {
        Map<String, String> essenceByForm = Map.of(
                "Wild", "Draconic_Essence", "Nature", "Draconic_Essence_Nature",
                "Toxic", "Draconic_Essence_Toxic", "Fire", "Draconic_Essence_Fire",
                "Void", "Draconic_Essence_Void", "Lightning", "Draconic_Essence_Lightning",
                "Ice", "Draconic_Essence_Ice");
        List<String> roleIds = MINI_WYVERN_FORMS.stream().map(form -> "Tamed_Wyvern_Mini_" + form).toList();
        String roster = Files.readString(Path.of("Server", "Tamework", "BondedCompanions", "Rosters", "HyDragonMiniwyvern.json"));
        String companion = Files.readString(Path.of("Server", "Tamework", "Companion", "HyDragonMiniwyvern.json"));
        String horn = Files.readString(Path.of("Server", "Tamework", "Items", "Commands", "HyDragonDragonHorn.json"));
        String breeding = Files.readString(Path.of("Server", "Tamework", "Breeding", "HyDragonBondedCompanions.json"));
        assertEquals(7, occurrences(roster, "\"Tamed_Wyvern_Mini_"), "roster must contain exactly seven form roles");
        assertEquals(7, occurrences(companion, "\"Tamed_Wyvern_Mini_"), "companion must contain exactly seven form roles");
        for (String roleId : roleIds) {
            assertTrue(roster.contains("\"" + roleId + "\""), "roster omits " + roleId);
            assertTrue(companion.contains("\"" + roleId + "\""), "companion omits " + roleId);
            assertTrue(horn.contains("\"" + roleId + "\""), "Dragon Horn omits " + roleId);
            assertTrue(breeding.contains("\"" + roleId + "\""), "breeding config omits " + roleId);
        }
        for (String source : MINI_WYVERN_FORMS) {
            String roleId = "Tamed_Wyvern_Mini_" + source;
            Path rolePath = Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini", roleId + ".json");
            String role = Files.readString(rolePath);
            String configId = "HyDragonIntWyvernMini_" + source;
            assertTrue(role.contains("\"Reference\": \"Template_Wyvern_Mini_Flying_Tamed\""), rolePath.toString());
            assertTrue(role.contains("\"InteractionConfigId\": \"" + configId + "\""), rolePath.toString());
            String interaction = Files.readString(Path.of("Server", "Tamework", "Interactions", configId + ".json"));
            assertEquals(6, occurrences(interaction, "\"Type\": \"Custom\""), configId);
            assertTrue(interaction.contains("\"Type\": \"Feed\""), configId);
            assertTrue(interaction.contains("\"Type\": \"ModeCycle\""), configId);
            assertFalse(interaction.contains("\"Role\": \"" + roleId + "\""), configId + " must exclude self");
            for (String destination : MINI_WYVERN_FORMS) {
                if (destination.equals(source)) continue;
                String destinationRole = "Tamed_Wyvern_Mini_" + destination;
                String essence = essenceByForm.get(destination);
                assertTrue(interaction.contains("\"Role\": \"" + destinationRole + "\", \"ChangeAppearance\": true"), configId + " -> " + destinationRole);
                assertTrue(interaction.contains("\"Items\": [\"" + essence + "\"], \"Quantity\": 8"), configId + " must charge " + essence);
            }
            assertEquals(6, occurrences(interaction, "\"IsTamed\": true"), configId);
            assertEquals(6, occurrences(interaction, "\"PlayerIsOwner\": true"), configId);
        }
        assertFalse(Files.exists(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini", "Tamed_Wyvern_Mini_Water.json")));
        assertFalse(Files.exists(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini", "Tamed_Wyvern_Mini_Wind.json")));
        assertFalse(Files.exists(Path.of("Server", "Tamework", "Interactions", "HyDragonIntWyvernMini_Water.json")));
        assertFalse(Files.exists(Path.of("Server", "Tamework", "Interactions", "HyDragonIntWyvernMini_Wind.json")));
        assertFalse(Files.exists(Path.of("Server", "Models", "HyDragon", "Wyvern_Mini", "Wyvern_Mini_Water.json")));
        assertFalse(Files.exists(Path.of("Server", "Models", "HyDragon", "Wyvern_Mini", "Wyvern_Mini_Wind.json")));
    }

    @Test
    void elementalMiniwyvernsApplyAVisibleOwnerAuraEffect() throws IOException {
        Map<String, AuraVisual> expectedAuraByForm = Map.of(
                "Nature", new AuraVisual("HyDragon_Miniwyvern_Nature_Regeneration", "Icons/StatusEffects/HyDragon/Miniwyvern_Nature_Aura.png"),
                "Toxic", new AuraVisual("HyDragon_Miniwyvern_Toxic_Aura", "Icons/StatusEffects/HyDragon/Miniwyvern_Toxic_Aura.png"),
                "Fire", new AuraVisual("HyDragon_Miniwyvern_Fire_Aura", "Icons/StatusEffects/HyDragon/Miniwyvern_Fire_Aura.png"),
                "Void", new AuraVisual("HyDragon_Miniwyvern_Void_Aura", "Icons/StatusEffects/HyDragon/Miniwyvern_Void_Aura.png"),
                "Lightning", new AuraVisual("HyDragon_Miniwyvern_Lightning_Boon", "Icons/StatusEffects/HyDragon/Miniwyvern_Lightning_Aura.png"),
                "Ice", new AuraVisual("HyDragon_Miniwyvern_Ice_Aura", "Icons/StatusEffects/HyDragon/Miniwyvern_Ice_Aura.png"));
        for (Map.Entry<String, AuraVisual> expected : expectedAuraByForm.entrySet()) {
            Path archetype = Path.of("Server", "HyDragon", "MiniwyvernArchetypes", expected.getKey() + ".json");
            String json = Files.readString(archetype);
            assertTrue(json.contains("\"PassiveEffects\": [ \"" + expected.getValue().effectId() + "\" ]"),
                    archetype + " must maintain its owner aura EntityEffect while summoned");
            Path effect = Path.of("Server", "Entity", "Effects", "Status", expected.getValue().effectId() + ".json");
            assertTrue(Files.exists(effect), expected.getValue().effectId() + " must be a bundled EntityEffect");
            String effectJson = Files.readString(effect);
            assertTrue(effectJson.contains("\"StatusEffectIcon\": \"" + expected.getValue().iconPath() + "\""),
                    expected.getValue().effectId() + " must declare its generated HUD icon");
            assertFalse(effectJson.contains("\"EntityBottomTint\""),
                    expected.getValue().effectId() + " must not tint the owner while its aura is active");
            assertFalse(effectJson.contains("\"EntityTopTint\""),
                    expected.getValue().effectId() + " must not tint the owner while its aura is active");
            assertTrue(Files.exists(Path.of("Common", expected.getValue().iconPath())),
                    expected.getValue().iconPath() + " must be bundled with the HUD effect");
        }
    }

    private record AuraVisual(String effectId, String iconPath) { }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    @Test
    void itemAssetsDoNotUseEmptyLightColors() throws IOException {
        try (var assets = Files.walk(Path.of("Server", "Item", "Items"))) {
            for (Path asset : assets.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                assertFalse(Files.readString(asset).matches("(?s).*\\\"Light\\\"\\s*:\\s*\\{\\s*\\\"Color\\\"\\s*:\\s*\\\"\\\".*"),
                        asset + " must omit Light rather than supplying an invalid empty color");
            }
        }
    }

    @Test
    void bundledAssetsDecodeWithoutUnknownFieldsAndCrossValidate() throws IOException {
        List<DragonSpeciesConfig> species = decodeDirectory(
                "DragonSpecies", DragonSpeciesConfig.class, DragonSpeciesConfig.CODEC);
        List<MiniwyvernArchetypeConfig> archetypes = decodeDirectory(
                "MiniwyvernArchetypes", MiniwyvernArchetypeConfig.class, MiniwyvernArchetypeConfig.CODEC);
        List<DragonEncounterConfig> encounters = decodeDirectory(
                "Encounters", DragonEncounterConfig.class, DragonEncounterConfig.CODEC);

        HyDragonConfigRepository.Snapshot snapshot = HyDragonConfigRepository.buildSnapshot(
                species, archetypes, encounters);
        assertTrue(snapshot.isValid(), () -> String.join("\n", snapshot.issues()));
    }

    @Test
    void serviceOwnedHealingEffectsArePresentationOnly() throws IOException {
        for (String asset : List.of(
                "HyDragon_Miniwyvern_Nature_Regeneration.json",
                "HyDragon_Miniwyvern_Water_Heal.json")) {
            Path path = Path.of("Server", "Entity", "Effects", "Status", asset);
            String json = Files.readString(path);
            assertFalse(json.contains("\"Parent\""), path + " must not inherit an additional healing mechanic");
            assertFalse(json.contains("\"StatModifiers\""), path + " must not apply health outside the capped service");
            assertTrue(json.contains("\"StatusEffectIcon\"") || json.contains("\"ApplicationEffects\""),
                    path + " should retain visible feedback");
        }
    }

    @Test
    void natureHealingPresentationIsSilentAndFinite() throws IOException {
        Path archetype = Path.of("Server", "HyDragon", "MiniwyvernArchetypes", "Nature.json");
        String archetypeJson = Files.readString(archetype);
        String mistId = "HyDragon_Miniwyvern_Nature_HealingMist";
        assertTrue(archetypeJson.contains("\"ParticleAndSoundIds\": [ \"" + mistId + "\" ]"),
                "Nature healing must emit only its bounded custom mist");
        assertFalse(archetypeJson.contains("Effect_Heal"), "Nature healing must not use the persistent stock heal effect");
        assertFalse(archetypeJson.contains("SFX_"), "Nature healing must not play a sound");

        Path system = Path.of("Server", "Particles", "HyDragon", "Miniwyvern", mistId + ".particlesystem");
        Path spawner = Path.of("Server", "Particles", "HyDragon", "Miniwyvern", "Spawners", mistId + ".particlespawner");
        assertTrue(Files.exists(system), "Nature healing mist particle system must be bundled");
        assertTrue(Files.exists(spawner), "Nature healing mist spawner must be bundled");
        String systemJson = Files.readString(system);
        String spawnerJson = Files.readString(spawner);
        assertTrue(systemJson.contains("\"LifeSpan\": 0.75"), "Nature healing mist must have a bounded system lifetime");
        assertTrue(spawnerJson.contains("\"Max\": 0.65"), "Nature healing mist particles must expire quickly");
    }

    @Test
    void miniwyvernFormConfigsBindOnlyApprovedFormsToTheirRoles() throws IOException {
        Map<String, MiniwyvernArchetypeConfig> archetypes = decodeDirectory(
                "MiniwyvernArchetypes", MiniwyvernArchetypeConfig.class,
                MiniwyvernArchetypeConfig.CODEC).stream().collect(Collectors.toMap(
                        MiniwyvernArchetypeConfig::getId, Function.identity()));
        assertEquals(Set.of("wild", "nature", "toxic", "fire", "void", "lightning", "ice"),
                archetypes.keySet());
        for (String id : archetypes.keySet()) {
            MiniwyvernArchetypeConfig archetype = archetypes.get(id);
            String title = Character.toUpperCase(id.charAt(0)) + id.substring(1);
            assertEquals("Tamed_Wyvern_Mini_" + title, archetype.getRoleId(), id);
        }
        assertTrue(archetypes.get("nature").getActiveAbilities().isEmpty());
        assertTrue(archetypes.get("nature").getPassiveEffects().contains("HyDragon_Miniwyvern_Nature_Regeneration"));
        assertEquals("Scarak_Seeker_Spitball", archetypes.get("toxic").getActiveAbilities().getFirst().getProjectileId());
        assertOwnerAttackAura(archetypes, "toxic", "HyDragon_Miniwyvern_Toxic_Weakness", 6.0);
        assertEquals(0.12, archetypes.get("toxic").getOwnerAttackAura().getDamageReductionFraction());
        assertOwnerAttackAura(archetypes, "fire", "HyDragon_Miniwyvern_Fire_Burn", 4.0);
        assertOwnerAttackAura(archetypes, "ice", "HyDragon_Miniwyvern_Ice_Slow", 4.0);
        assertOwnerAttackAura(archetypes, "void", "HyDragon_Miniwyvern_Void_Exposure", 6.0);
    }

    private static void assertOwnerAttackAura(Map<String, MiniwyvernArchetypeConfig> archetypes,
            String form, String effectId, double durationSeconds) {
        MiniwyvernArchetypeConfig.OwnerAttackAura aura = archetypes.get(form).getOwnerAttackAura();
        assertEquals(effectId, aura.getEffectId(), form);
        assertEquals(durationSeconds, aura.getDurationSeconds(), form);
    }

    private static <T extends JsonAsset<String>> List<T> decodeDirectory(
            String directory,
            Class<T> type,
            AssetBuilderCodec<String, T> codec) throws IOException {
        List<Path> paths;
        try (var stream = Files.list(CONFIG_ROOT.resolve(directory))) {
            paths = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        assertTrue(!paths.isEmpty(), "No bundled assets in " + directory);

        List<T> decoded = new ArrayList<>();
        for (Path path : paths) {
            String fileName = path.getFileName().toString();
            String key = fileName.substring(0, fileName.length() - ".json".length());
            AssetExtraInfo.Data data = new AssetExtraInfo.Data(type, key, null);
            AssetExtraInfo<String> extra = new AssetExtraInfo<>(path, data);
            try (RawJsonReader reader = new RawJsonReader(Files.readString(path).toCharArray())) {
                decoded.add(codec.decodeJsonAsset(reader, extra));
            }
            assertTrue(extra.getUnknownKeys().isEmpty(),
                    () -> path + " has unknown keys " + extra.getUnknownKeys());
        }
        return List.copyOf(decoded);
    }
}
