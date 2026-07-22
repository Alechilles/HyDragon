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

    @Test
    void bundledAssetsDecodeWithoutUnknownFieldsAndCrossValidate() throws IOException {
        List<DragonSpeciesConfig> species = decodeDirectory(
                "DragonSpecies", DragonSpeciesConfig.class, DragonSpeciesConfig.CODEC);
        List<StoneMaintenanceConfig> maintenance = decodeDirectory(
                "StoneMaintenance", StoneMaintenanceConfig.class, StoneMaintenanceConfig.CODEC);
        List<MiniwyvernArchetypeConfig> archetypes = decodeDirectory(
                "MiniwyvernArchetypes", MiniwyvernArchetypeConfig.class, MiniwyvernArchetypeConfig.CODEC);
        List<DragonEncounterConfig> encounters = decodeDirectory(
                "Encounters", DragonEncounterConfig.class, DragonEncounterConfig.CODEC);

        HyDragonConfigRepository.Snapshot snapshot = HyDragonConfigRepository.buildSnapshot(
                species, maintenance, archetypes, encounters);
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
    void elementalArchetypeMatrixHasEssenceAppearancePresentationAndBehavior() throws IOException {
        Map<String, MiniwyvernArchetypeConfig> archetypes = decodeDirectory(
                "MiniwyvernArchetypes", MiniwyvernArchetypeConfig.class,
                MiniwyvernArchetypeConfig.CODEC).stream().collect(Collectors.toMap(
                        MiniwyvernArchetypeConfig::getId, Function.identity()));
        assertEquals(Set.of("neutral", "lightning", "wind", "ice", "fire", "water", "nature", "void"),
                archetypes.keySet());

        for (String id : Set.of("lightning", "wind", "ice", "fire", "water", "nature", "void")) {
            MiniwyvernArchetypeConfig archetype = archetypes.get(id);
            String title = Character.toUpperCase(id.charAt(0)) + id.substring(1);
            assertEquals(id, archetype.getEssenceSemanticId(), id);
            assertEquals("Draconic_Essence_" + title, archetype.getEssenceItemId(), id);
            assertEquals("Wyvern_Mini_" + title, archetype.getAppearanceId(), id);
            assertFalse(archetype.getParticleAndSoundIds().isEmpty(), id + " lacks presentation");
            assertTrue(!archetype.getActiveAbilities().isEmpty()
                            || !archetype.getPassiveEffects().isEmpty(),
                    id + " lacks an active or passive behavior");
        }

        MiniwyvernArchetypeConfig neutral = archetypes.get("neutral");
        assertTrue(neutral.getEssenceSemanticId() == null || neutral.getEssenceSemanticId().isBlank());
        assertTrue(neutral.getEssenceItemId() == null || neutral.getEssenceItemId().isBlank());
        assertEquals("Wyvern_Mini", neutral.getAppearanceId());
        assertEquals("BASIC_BITE", neutral.getFallbackBehavior());
    }

    @Test
    void miniwyvernAppearancesResolveToByteDistinctTexturesAndPresentationVocabularies()
            throws IOException, NoSuchAlgorithmException {
        List<MiniwyvernArchetypeConfig> archetypes = decodeDirectory(
                "MiniwyvernArchetypes", MiniwyvernArchetypeConfig.class,
                MiniwyvernArchetypeConfig.CODEC);
        Map<String, String> textureHashByArchetype = new HashMap<>();
        Map<String, List<String>> vocabularyByArchetype = new HashMap<>();

        for (MiniwyvernArchetypeConfig archetype : archetypes) {
            String appearanceId = archetype.getAppearanceId();
            Path appearancePath = Path.of(
                    "Server", "Models", "HyDragon", "Wyvern_Mini", appearanceId + ".json");
            assertTrue(Files.isRegularFile(appearancePath),
                    () -> archetype.getId() + " appearance does not resolve: " + appearancePath);

            Matcher textureMatcher = TEXTURE_FIELD.matcher(Files.readString(appearancePath));
            assertTrue(textureMatcher.find(),
                    () -> archetype.getId() + " appearance has no concrete texture: " + appearancePath);
            Path texturePath = Path.of("Common").resolve(textureMatcher.group(1));
            assertTrue(Files.isRegularFile(texturePath),
                    () -> archetype.getId() + " texture does not resolve: " + texturePath);

            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(texturePath));
            textureHashByArchetype.put(archetype.getId(), HexFormat.of().formatHex(digest));
            vocabularyByArchetype.put(archetype.getId(), List.copyOf(archetype.getParticleAndSoundIds()));
        }

        assertEquals(8, textureHashByArchetype.size());
        assertEquals(8, new HashSet<>(textureHashByArchetype.values()).size(),
                () -> "Each resolved Miniwyvern appearance must have distinct texture bytes: "
                        + textureHashByArchetype);
        assertEquals(8, vocabularyByArchetype.size());
        assertEquals(8, new HashSet<>(vocabularyByArchetype.values()).size(),
                () -> "Each Miniwyvern archetype must have a distinct particle/audio vocabulary: "
                        + vocabularyByArchetype);
        assertFalse(vocabularyByArchetype.get("lightning").equals(vocabularyByArchetype.get("void")));
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
