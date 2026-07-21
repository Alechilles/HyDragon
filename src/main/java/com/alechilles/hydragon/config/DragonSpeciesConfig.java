package com.alechilles.hydragon.config;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Asset-backed species metadata stored under {@code Server/HyDragon/DragonSpecies}. */
public final class DragonSpeciesConfig
        implements JsonAssetWithMap<String, DefaultAssetMap<String, DragonSpeciesConfig>> {
    private static final String[] EMPTY = new String[0];

    private static final BuilderCodec<MountSettings> MOUNT_CODEC = BuilderCodec.builder(
            MountSettings.class,
            MountSettings::new
    )
            .<String>append(new KeyedCodec<>("Mode", Codec.STRING),
                    (settings, value) -> settings.mode = value,
                    settings -> settings.mode)
            .add()
            .<String>append(new KeyedCodec<>("AvatarFlightConfigId", Codec.STRING),
                    (settings, value) -> settings.avatarFlightConfigId = value,
                    settings -> settings.avatarFlightConfigId)
            .add()
            .build();

    private static final BuilderCodec<CaptureSettings> CAPTURE_CODEC = BuilderCodec.builder(
            CaptureSettings.class,
            CaptureSettings::new
    )
            .<Double>append(new KeyedCodec<>("Resistance", Codec.DOUBLE),
                    (settings, value) -> settings.resistance = value == null ? 0.0 : value,
                    settings -> settings.resistance)
            .add()
            .<Integer>append(new KeyedCodec<>("MinimumStoneTier", Codec.INTEGER),
                    (settings, value) -> settings.minimumStoneTier = value == null ? 1 : value,
                    settings -> settings.minimumStoneTier)
            .add()
            .<Double>append(new KeyedCodec<>("MaxHealthPercentOverride", Codec.DOUBLE),
                    (settings, value) -> settings.maxHealthPercentOverride = value,
                    settings -> settings.maxHealthPercentOverride)
            .add()
            .<String[]>append(new KeyedCodec<>("SpecialRequirementIds", Codec.STRING_ARRAY),
                    (settings, value) -> settings.specialRequirementIds = value == null ? EMPTY : value,
                    settings -> settings.specialRequirementIds)
            .add()
            .build();

    private static final BuilderCodec<SpawnSettings> SPAWN_CODEC = BuilderCodec.builder(
            SpawnSettings.class,
            SpawnSettings::new
    )
            .<String[]>append(new KeyedCodec<>("OrdinarySpawnAssetIds", Codec.STRING_ARRAY),
                    (settings, value) -> settings.ordinarySpawnAssetIds = value == null ? EMPTY : value,
                    settings -> settings.ordinarySpawnAssetIds)
            .add()
            .<String[]>append(new KeyedCodec<>("PluginEncounterIds", Codec.STRING_ARRAY),
                    (settings, value) -> settings.pluginEncounterIds = value == null ? EMPTY : value,
                    settings -> settings.pluginEncounterIds)
            .add()
            .build();

    private static final BuilderCodec<PresentationSettings> PRESENTATION_CODEC = BuilderCodec.builder(
            PresentationSettings.class,
            PresentationSettings::new
    )
            .<String>append(new KeyedCodec<>("LocalizationPrefix", Codec.STRING),
                    (settings, value) -> settings.localizationPrefix = value,
                    settings -> settings.localizationPrefix)
            .add()
            .<String[]>append(new KeyedCodec<>("ModelIds", Codec.STRING_ARRAY),
                    (settings, value) -> settings.modelIds = value == null ? EMPTY : value,
                    settings -> settings.modelIds)
            .add()
            .build();

    public static final AssetBuilderCodec<String, DragonSpeciesConfig> CODEC = AssetBuilderCodec.builder(
            DragonSpeciesConfig.class,
            DragonSpeciesConfig::new,
            Codec.STRING,
            (asset, key) -> asset.assetKey = key,
            asset -> asset.assetKey,
            (asset, data) -> asset.data = data,
            asset -> asset.data
    )
            .documentation("HyDragon full-dragon species, capture, mount, spawn, and presentation metadata.")
            .<String>append(new KeyedCodec<>("Id", Codec.STRING),
                    (asset, value) -> asset.id = value,
                    asset -> asset.id)
            .add()
            .<String[]>append(new KeyedCodec<>("WildRoleIds", Codec.STRING_ARRAY),
                    (asset, value) -> asset.wildRoleIds = value == null ? EMPTY : value,
                    asset -> asset.wildRoleIds)
            .add()
            .<Map<String, String>>append(new KeyedCodec<>("TamedRoleIdByWildRole", MapCodec.STRING_HASH_MAP_CODEC),
                    (asset, value) -> asset.tamedRoleIdByWildRole = value == null ? Map.of() : value,
                    asset -> asset.tamedRoleIdByWildRole)
            .add()
            .<String>append(new KeyedCodec<>("DifficultyId", Codec.STRING),
                    (asset, value) -> asset.difficultyId = value,
                    asset -> asset.difficultyId)
            .add()
            .<String[]>append(new KeyedCodec<>("StatsAndBehaviorAssetIds", Codec.STRING_ARRAY),
                    (asset, value) -> asset.statsAndBehaviorAssetIds = value == null ? EMPTY : value,
                    asset -> asset.statsAndBehaviorAssetIds)
            .add()
            .<String>append(new KeyedCodec<>("DropListId", Codec.STRING),
                    (asset, value) -> asset.dropListId = value,
                    asset -> asset.dropListId)
            .add()
            .<MountSettings>append(new KeyedCodec<>("Mount", MOUNT_CODEC),
                    (asset, value) -> asset.mount = value == null ? new MountSettings() : value,
                    asset -> asset.mount)
            .add()
            .<CaptureSettings>append(new KeyedCodec<>("Capture", CAPTURE_CODEC),
                    (asset, value) -> asset.capture = value == null ? new CaptureSettings() : value,
                    asset -> asset.capture)
            .add()
            .<SpawnSettings>append(new KeyedCodec<>("Spawn", SPAWN_CODEC),
                    (asset, value) -> asset.spawn = value == null ? new SpawnSettings() : value,
                    asset -> asset.spawn)
            .add()
            .<PresentationSettings>append(new KeyedCodec<>("Presentation", PRESENTATION_CODEC),
                    (asset, value) -> asset.presentation = value == null ? new PresentationSettings() : value,
                    asset -> asset.presentation)
            .add()
            .build();

    private AssetExtraInfo.Data data;
    private String assetKey;
    String id;
    String[] wildRoleIds = EMPTY;
    Map<String, String> tamedRoleIdByWildRole = Map.of();
    String difficultyId;
    String[] statsAndBehaviorAssetIds = EMPTY;
    String dropListId;
    MountSettings mount = new MountSettings();
    CaptureSettings capture = new CaptureSettings();
    SpawnSettings spawn = new SpawnSettings();
    PresentationSettings presentation = new PresentationSettings();

    DragonSpeciesConfig() {
    }

    @Nonnull
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        requireNamespaced(errors, "Id", id);
        if (wildRoleIds.length == 0) {
            errors.add("WildRoleIds must contain at least one full-dragon role");
        }
        for (String roleId : wildRoleIds) {
            if (blank(roleId)) {
                errors.add("WildRoleIds contains a blank role");
            } else if (roleId.toLowerCase(Locale.ROOT).contains("wyvern_mini")) {
                errors.add("Miniwyvern is Soul Bond-exclusive and cannot be a full-dragon wild role");
            } else if (blank(tamedRoleIdByWildRole.get(roleId))) {
                errors.add("TamedRoleIdByWildRole is missing " + roleId);
            }
        }
        if (blank(difficultyId)) errors.add("DifficultyId is required");
        if (blank(dropListId)) errors.add("DropListId is required");
        errors.addAll(mount.validate());
        errors.addAll(capture.validate());
        if (blank(presentation.localizationPrefix)) errors.add("Presentation.LocalizationPrefix is required");
        return List.copyOf(errors);
    }

    public String getAssetKey() { return assetKey; }
    public String getId() { return trim(id); }
    public List<String> getWildRoleIds() { return List.of(wildRoleIds.clone()); }
    public Map<String, String> getTamedRoleIdByWildRole() { return Map.copyOf(tamedRoleIdByWildRole); }
    public String getDifficultyId() { return trim(difficultyId); }
    public List<String> getStatsAndBehaviorAssetIds() { return List.of(statsAndBehaviorAssetIds.clone()); }
    public String getDropListId() { return trim(dropListId); }
    public MountSettings getMount() { return mount; }
    public CaptureSettings getCapture() { return capture; }
    public SpawnSettings getSpawn() { return spawn; }
    public PresentationSettings getPresentation() { return presentation; }

    /** Mount mode and optional avatar-flight profile. */
    public static final class MountSettings {
        String mode = "NONE";
        String avatarFlightConfigId;

        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            MountMode parsed = getMode();
            if (parsed == null) errors.add("Mount.Mode must be NONE, GROUND, or AVATAR_FLIGHT");
            if (parsed == MountMode.AVATAR_FLIGHT && blank(avatarFlightConfigId)) {
                errors.add("Mount.AvatarFlightConfigId is required for AVATAR_FLIGHT");
            }
            return errors;
        }

        @Nullable
        public MountMode getMode() {
            try {
                return MountMode.valueOf(trim(mode).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        @Nullable public String getAvatarFlightConfigId() {
            return blank(avatarFlightConfigId) ? null : avatarFlightConfigId.trim();
        }
    }

    /** Capture resistance and minimum-stone data consumed by Tamework capture policy. */
    public static final class CaptureSettings {
        double resistance;
        int minimumStoneTier = 1;
        Double maxHealthPercentOverride;
        String[] specialRequirementIds = EMPTY;

        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (!Double.isFinite(resistance) || resistance < 0.0 || resistance > 1.0) {
                errors.add("Capture.Resistance must be between 0 and 1");
            }
            if (minimumStoneTier < 1 || minimumStoneTier > 5) {
                errors.add("Capture.MinimumStoneTier must be between 1 and 5");
            }
            if (maxHealthPercentOverride != null && (!Double.isFinite(maxHealthPercentOverride)
                    || maxHealthPercentOverride <= 0.0 || maxHealthPercentOverride > 100.0)) {
                errors.add("Capture.MaxHealthPercentOverride must be in (0, 100]");
            }
            return errors;
        }

        public double getResistance() { return resistance; }
        public int getMinimumStoneTier() { return minimumStoneTier; }
        @Nullable public Double getMaxHealthPercentOverride() { return maxHealthPercentOverride; }
        public List<String> getSpecialRequirementIds() { return List.of(specialRequirementIds.clone()); }
    }

    /** References to base-game ordinary spawns and plugin-controlled encounters. */
    public static final class SpawnSettings {
        String[] ordinarySpawnAssetIds = EMPTY;
        String[] pluginEncounterIds = EMPTY;

        public List<String> getOrdinarySpawnAssetIds() { return List.of(ordinarySpawnAssetIds.clone()); }
        public List<String> getPluginEncounterIds() { return List.of(pluginEncounterIds.clone()); }
    }

    /** Player-facing presentation references. */
    public static final class PresentationSettings {
        String localizationPrefix;
        String[] modelIds = EMPTY;

        public String getLocalizationPrefix() { return trim(localizationPrefix); }
        public List<String> getModelIds() { return List.of(modelIds.clone()); }
    }

    public enum MountMode { NONE, GROUND, AVATAR_FLIGHT }

    private static void requireNamespaced(List<String> errors, String field, String value) {
        if (blank(value) || value.indexOf(':') <= 0 || value.endsWith(":")) {
            errors.add(field + " must be a namespaced identifier");
        }
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
