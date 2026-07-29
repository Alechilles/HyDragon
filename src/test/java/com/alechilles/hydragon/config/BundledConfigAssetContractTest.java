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
    }

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
            assertTrue(json.contains("\"ApplicationEffects\""), path + " should retain visible feedback");
        }
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
        assertEquals("Scarak_Seeker_Spit_Projectile", archetypes.get("toxic").getActiveAbilities().getFirst().getProjectileId());
        assertEquals("HyDragon_Miniwyvern_Void_Exposure", archetypes.get("toxic").getOwnerAttackAura().getEffectId());
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
