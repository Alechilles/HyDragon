package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.JsonAsset;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class BundledConfigAssetContractTest {
    private static final Path CONFIG_ROOT = Path.of("Server", "HyDragon");

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
