package com.alechilles.hydragon.config;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Plugin-controlled dynamic encounter definition stored under {@code Server/HyDragon/Encounters}. */
public final class DragonEncounterConfig
        implements JsonAssetWithMap<String, DefaultAssetMap<String, DragonEncounterConfig>> {
    private static final String[] EMPTY = new String[0];

    private static final BuilderCodec<RegionSettings> REGION_CODEC = BuilderCodec.builder(
            RegionSettings.class,
            RegionSettings::new
    )
            .<String[]>append(new KeyedCodec<>("EnvironmentIds", Codec.STRING_ARRAY),
                    (settings, value) -> settings.environmentIds = value == null ? EMPTY : value,
                    settings -> settings.environmentIds)
            .add()
            .<Double>append(new KeyedCodec<>("MinY", Codec.DOUBLE),
                    (settings, value) -> settings.minY = value == null ? 0.0 : value,
                    settings -> settings.minY)
            .add()
            .<Double>append(new KeyedCodec<>("MaxY", Codec.DOUBLE),
                    (settings, value) -> settings.maxY = value == null ? 0.0 : value,
                    settings -> settings.maxY)
            .add()
            .build();

    private static final BuilderCodec<WeatherSettings> WEATHER_CODEC = BuilderCodec.builder(
            WeatherSettings.class,
            WeatherSettings::new
    )
            .<String>append(new KeyedCodec<>("Mode", Codec.STRING),
                    (settings, value) -> settings.mode = value,
                    settings -> settings.mode)
            .add()
            .<String[]>append(new KeyedCodec<>("WeatherIds", Codec.STRING_ARRAY),
                    (settings, value) -> settings.weatherIds = value == null ? EMPTY : value,
                    settings -> settings.weatherIds)
            .add()
            .build();

    private static final BuilderCodec<PlayerEligibilitySettings> PLAYER_ELIGIBILITY_CODEC = BuilderCodec.builder(
            PlayerEligibilitySettings.class,
            PlayerEligibilitySettings::new
    )
            .<String>append(new KeyedCodec<>("ActiveCompanionGroup", Codec.STRING),
                    (settings, value) -> settings.activeCompanionGroup = value,
                    settings -> settings.activeCompanionGroup)
            .add()
            .<String>append(new KeyedCodec<>("RequiredMountMode", Codec.STRING),
                    (settings, value) -> settings.requiredMountMode = value,
                    settings -> settings.requiredMountMode)
            .add()
            .<String>append(new KeyedCodec<>("RequiredItemId", Codec.STRING),
                    (settings, value) -> settings.requiredItemId = value,
                    settings -> settings.requiredItemId)
            .add()
            .build();

    private static final BuilderCodec<AdmissionSettings> ADMISSION_CODEC = BuilderCodec.builder(
            AdmissionSettings.class,
            AdmissionSettings::new
    )
            .<Double>append(new KeyedCodec<>("Chance", Codec.DOUBLE),
                    (settings, value) -> settings.chance = value == null ? 0.0 : value,
                    settings -> settings.chance)
            .add()
            .<Double>append(new KeyedCodec<>("EvaluationCooldownSeconds", Codec.DOUBLE),
                    (settings, value) -> settings.evaluationCooldownSeconds = value == null ? 0.0 : value,
                    settings -> settings.evaluationCooldownSeconds)
            .add()
            .<Integer>append(new KeyedCodec<>("PerRegionLimit", Codec.INTEGER),
                    (settings, value) -> settings.perRegionLimit = value == null ? 1 : value,
                    settings -> settings.perRegionLimit)
            .add()
            .<Integer>append(new KeyedCodec<>("GlobalLimit", Codec.INTEGER),
                    (settings, value) -> settings.globalLimit = value == null ? 1 : value,
                    settings -> settings.globalLimit)
            .add()
            .build();

    private static final BuilderCodec<GroundingSettings> GROUNDING_CODEC = BuilderCodec.builder(
            GroundingSettings.class,
            GroundingSettings::new
    )
            .<String[]>append(new KeyedCodec<>("BuildupSourceIds", Codec.STRING_ARRAY),
                    (settings, value) -> settings.buildupSourceIds = value == null ? EMPTY : value,
                    settings -> settings.buildupSourceIds)
            .add()
            .<Double>append(new KeyedCodec<>("Threshold", Codec.DOUBLE),
                    (settings, value) -> settings.threshold = value == null ? 0.0 : value,
                    settings -> settings.threshold)
            .add()
            .<String>append(new KeyedCodec<>("GroundedState", Codec.STRING),
                    (settings, value) -> settings.groundedState = value,
                    settings -> settings.groundedState)
            .add()
            .<String>append(new KeyedCodec<>("GroundedEffectId", Codec.STRING),
                    (settings, value) -> settings.groundedEffectId = value,
                    settings -> settings.groundedEffectId)
            .add()
            .<Double>append(new KeyedCodec<>("CaptureWindowSeconds", Codec.DOUBLE),
                    (settings, value) -> settings.captureWindowSeconds = value == null ? 0.0 : value,
                    settings -> settings.captureWindowSeconds)
            .add()
            .build();

    private static final BuilderCodec<CleanupSettings> CLEANUP_CODEC = BuilderCodec.builder(
            CleanupSettings.class,
            CleanupSettings::new
    )
            .<Double>append(new KeyedCodec<>("EncounterTimeoutSeconds", Codec.DOUBLE),
                    (settings, value) -> settings.encounterTimeoutSeconds = value == null ? 0.0 : value,
                    settings -> settings.encounterTimeoutSeconds)
            .add()
            .<Double>append(new KeyedCodec<>("RetryCooldownSeconds", Codec.DOUBLE),
                    (settings, value) -> settings.retryCooldownSeconds = value == null ? 0.0 : value,
                    settings -> settings.retryCooldownSeconds)
            .add()
            .<Double>append(new KeyedCodec<>("EligibilityGraceSeconds", Codec.DOUBLE),
                    (settings, value) -> settings.eligibilityGraceSeconds = value == null ? 0.0 : value,
                    settings -> settings.eligibilityGraceSeconds)
            .add()
            .build();

    public static final AssetBuilderCodec<String, DragonEncounterConfig> CODEC = AssetBuilderCodec.builder(
            DragonEncounterConfig.class,
            DragonEncounterConfig::new,
            Codec.STRING,
            (asset, key) -> asset.assetKey = key,
            asset -> asset.assetKey,
            (asset, data) -> asset.data = data,
            asset -> asset.data
    )
            .documentation("HyDragon weather/player-gated multi-stage dragon encounter.")
            .<String>append(new KeyedCodec<>("Id", Codec.STRING),
                    (asset, value) -> asset.id = value,
                    asset -> asset.id)
            .add()
            .<Boolean>append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (asset, value) -> asset.enabled = value == null || value,
                    asset -> asset.enabled)
            .add()
            .<String>append(new KeyedCodec<>("TargetSpeciesId", Codec.STRING),
                    (asset, value) -> asset.targetSpeciesId = value,
                    asset -> asset.targetSpeciesId)
            .add()
            .<RegionSettings>append(new KeyedCodec<>("RegionsAndAltitude", REGION_CODEC),
                    (asset, value) -> asset.regionsAndAltitude = value == null ? new RegionSettings() : value,
                    asset -> asset.regionsAndAltitude)
            .add()
            .<WeatherSettings>append(new KeyedCodec<>("WeatherPredicate", WEATHER_CODEC),
                    (asset, value) -> asset.weatherPredicate = value == null ? new WeatherSettings() : value,
                    asset -> asset.weatherPredicate)
            .add()
            .<PlayerEligibilitySettings>append(new KeyedCodec<>("PlayerEligibility", PLAYER_ELIGIBILITY_CODEC),
                    (asset, value) -> asset.playerEligibility = value == null ? new PlayerEligibilitySettings() : value,
                    asset -> asset.playerEligibility)
            .add()
            .<AdmissionSettings>append(new KeyedCodec<>("Admission", ADMISSION_CODEC),
                    (asset, value) -> asset.admission = value == null ? new AdmissionSettings() : value,
                    asset -> asset.admission)
            .add()
            .<String[]>append(new KeyedCodec<>("Phases", Codec.STRING_ARRAY),
                    (asset, value) -> asset.phases = value == null ? EMPTY : value,
                    asset -> asset.phases)
            .add()
            .<GroundingSettings>append(new KeyedCodec<>("Grounding", GROUNDING_CODEC),
                    (asset, value) -> asset.grounding = value == null ? new GroundingSettings() : value,
                    asset -> asset.grounding)
            .add()
            .<CleanupSettings>append(new KeyedCodec<>("CleanupAndCooldown", CLEANUP_CODEC),
                    (asset, value) -> asset.cleanupAndCooldown = value == null ? new CleanupSettings() : value,
                    asset -> asset.cleanupAndCooldown)
            .add()
            .build();

    private AssetExtraInfo.Data data;
    private String assetKey;
    String id;
    boolean enabled = true;
    String targetSpeciesId;
    RegionSettings regionsAndAltitude = new RegionSettings();
    WeatherSettings weatherPredicate = new WeatherSettings();
    PlayerEligibilitySettings playerEligibility = new PlayerEligibilitySettings();
    AdmissionSettings admission = new AdmissionSettings();
    String[] phases = EMPTY;
    GroundingSettings grounding = new GroundingSettings();
    CleanupSettings cleanupAndCooldown = new CleanupSettings();

    DragonEncounterConfig() {
    }

    @Nonnull
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        requireNamespaced(errors, "Id", id);
        requireNamespaced(errors, "TargetSpeciesId", targetSpeciesId);
        errors.addAll(regionsAndAltitude.validate());
        errors.addAll(weatherPredicate.validate());
        errors.addAll(playerEligibility.validate());
        errors.addAll(admission.validate());
        errors.addAll(grounding.validate());
        errors.addAll(cleanupAndCooldown.validate());
        if (phases.length == 0) errors.add("Phases must not be empty");
        for (String phase : phases) {
            try {
                EncounterPhase.valueOf(trim(phase).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                errors.add("Unknown encounter phase: " + trim(phase));
            }
        }
        return List.copyOf(errors);
    }

    public String getAssetKey() { return assetKey; }
    public String getId() { return trim(id); }
    public boolean isEnabled() { return enabled; }
    public String getTargetSpeciesId() { return trim(targetSpeciesId); }
    public RegionSettings getRegionsAndAltitude() { return regionsAndAltitude; }
    public WeatherSettings getWeatherPredicate() { return weatherPredicate; }
    public PlayerEligibilitySettings getPlayerEligibility() { return playerEligibility; }
    public AdmissionSettings getAdmission() { return admission; }
    public List<String> getPhases() { return List.of(phases.clone()); }
    public GroundingSettings getGrounding() { return grounding; }
    public CleanupSettings getCleanupAndCooldown() { return cleanupAndCooldown; }

    public static final class RegionSettings {
        String[] environmentIds = EMPTY;
        double minY;
        double maxY;

        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (environmentIds.length == 0) errors.add("RegionsAndAltitude.EnvironmentIds must not be empty");
            if (!Double.isFinite(minY) || !Double.isFinite(maxY) || minY > maxY) {
                errors.add("RegionsAndAltitude requires finite MinY <= MaxY");
            }
            return errors;
        }

        public List<String> getEnvironmentIds() { return List.of(environmentIds.clone()); }
        public double getMinY() { return minY; }
        public double getMaxY() { return maxY; }
    }

    public static final class WeatherSettings {
        String mode = "AnyOf";
        String[] weatherIds = EMPTY;

        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (!trim(mode).equalsIgnoreCase("AnyOf") && !trim(mode).equalsIgnoreCase("AllOf")) {
                errors.add("WeatherPredicate.Mode must be AnyOf or AllOf");
            }
            if (weatherIds.length == 0) errors.add("WeatherPredicate.WeatherIds must not be empty");
            return errors;
        }

        public String getMode() { return trim(mode); }
        public List<String> getWeatherIds() { return List.of(weatherIds.clone()); }
    }

    public static final class PlayerEligibilitySettings {
        String activeCompanionGroup;
        String requiredMountMode;
        String requiredItemId;

        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            requireNamespaced(errors, "PlayerEligibility.ActiveCompanionGroup", activeCompanionGroup);
            if (!trim(requiredMountMode).equals("AVATAR_FLIGHT")) {
                errors.add("PlayerEligibility.RequiredMountMode must be AVATAR_FLIGHT for high-altitude encounters");
            }
            if (blank(requiredItemId)) errors.add("PlayerEligibility.RequiredItemId is required");
            return errors;
        }

        public String getActiveCompanionGroup() { return trim(activeCompanionGroup); }
        public String getRequiredMountMode() { return trim(requiredMountMode); }
        public String getRequiredItemId() { return trim(requiredItemId); }
    }

    public static final class AdmissionSettings {
        double chance;
        double evaluationCooldownSeconds;
        int perRegionLimit = 1;
        int globalLimit = 1;

        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
                errors.add("Admission.Chance must be between 0 and 1");
            }
            if (!Double.isFinite(evaluationCooldownSeconds) || evaluationCooldownSeconds < 0.0) {
                errors.add("Admission.EvaluationCooldownSeconds must be non-negative");
            }
            if (perRegionLimit <= 0) errors.add("Admission.PerRegionLimit must be positive");
            if (globalLimit <= 0 || globalLimit < perRegionLimit) {
                errors.add("Admission.GlobalLimit must be positive and at least PerRegionLimit");
            }
            return errors;
        }

        public double getChance() { return chance; }
        public long getEvaluationCooldownMs() { return secondsToMs(evaluationCooldownSeconds); }
        public int getPerRegionLimit() { return perRegionLimit; }
        public int getGlobalLimit() { return globalLimit; }
    }

    public static final class GroundingSettings {
        String[] buildupSourceIds = EMPTY;
        double threshold;
        String groundedState;
        String groundedEffectId;
        double captureWindowSeconds;

        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (buildupSourceIds.length < 2) {
                errors.add("Grounding.BuildupSourceIds must declare a lure source followed by at least one stagger source");
            }
            java.util.Set<String> distinctSources = new java.util.LinkedHashSet<>();
            for (String sourceId : buildupSourceIds) {
                String normalized = trim(sourceId);
                if (!isConcreteGroundingSource(normalized)) {
                    errors.add("Grounding.BuildupSourceIds contains unsupported source: " + normalized);
                } else if (!distinctSources.add(normalized)) {
                    errors.add("Grounding.BuildupSourceIds contains duplicate source: " + normalized);
                }
            }
            if (!Double.isFinite(threshold) || threshold <= 0.0) errors.add("Grounding.Threshold must be positive");
            if (blank(groundedState)) errors.add("Grounding.GroundedState is required");
            if (blank(groundedEffectId)) errors.add("Grounding.GroundedEffectId is required");
            if (!Double.isFinite(captureWindowSeconds) || captureWindowSeconds <= 0.0) {
                errors.add("Grounding.CaptureWindowSeconds must be positive");
            }
            return errors;
        }

        public List<String> getBuildupSourceIds() { return List.of(buildupSourceIds.clone()); }
        public String getLureSourceId() {
            return buildupSourceIds.length == 0 ? "" : trim(buildupSourceIds[0]);
        }
        public List<String> getStaggerSourceIds() {
            if (buildupSourceIds.length < 2) return List.of();
            String[] stagger = java.util.Arrays.copyOfRange(buildupSourceIds, 1, buildupSourceIds.length);
            return java.util.Arrays.stream(stagger).map(DragonEncounterConfig::trim).toList();
        }
        public double getThreshold() { return threshold; }
        public String getGroundedState() { return trim(groundedState); }
        public String getGroundedEffectId() { return trim(groundedEffectId); }
        public long getCaptureWindowMs() { return secondsToMs(captureWindowSeconds); }

        private static boolean isConcreteGroundingSource(String sourceId) {
            if (sourceId.isEmpty()) return false;
            String[] parts = sourceId.split("\\+", -1);
            if (parts.length == 0 || parts.length > 3) return false;
            java.util.Set<String> kinds = new java.util.HashSet<>();
            for (String part : parts) {
                int separator = part.indexOf(':');
                if (separator <= 0 || separator == part.length() - 1) return false;
                String kind = part.substring(0, separator);
                if (!kind.equals("projectile") && !kind.equals("item") && !kind.equals("damage_cause")) {
                    return false;
                }
                if (!kinds.add(kind)) return false;
            }
            return true;
        }
    }

    public static final class CleanupSettings {
        double encounterTimeoutSeconds;
        double retryCooldownSeconds;
        double eligibilityGraceSeconds;

        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (!positive(encounterTimeoutSeconds)) errors.add("CleanupAndCooldown.EncounterTimeoutSeconds must be positive");
            if (!nonNegative(retryCooldownSeconds)) errors.add("CleanupAndCooldown.RetryCooldownSeconds must be non-negative");
            if (!nonNegative(eligibilityGraceSeconds)) errors.add("CleanupAndCooldown.EligibilityGraceSeconds must be non-negative");
            return errors;
        }

        public long getEncounterTimeoutMs() { return secondsToMs(encounterTimeoutSeconds); }
        public long getRetryCooldownMs() { return secondsToMs(retryCooldownSeconds); }
        public long getEligibilityGraceMs() { return secondsToMs(eligibilityGraceSeconds); }
    }

    public enum EncounterPhase { AERIAL, GROUNDING, GROUNDED_CAPTURE_WINDOW }

    private static long secondsToMs(double seconds) {
        return Math.round(seconds * 1000.0);
    }

    private static boolean positive(double value) { return Double.isFinite(value) && value > 0.0; }
    private static boolean nonNegative(double value) { return Double.isFinite(value) && value >= 0.0; }

    private static void requireNamespaced(List<String> errors, String field, String value) {
        if (blank(value) || value.indexOf(':') <= 0 || value.endsWith(":")) {
            errors.add(field + " must be a namespaced identifier");
        }
    }

    private static boolean blank(@Nullable String value) { return value == null || value.isBlank(); }
    private static String trim(@Nullable String value) { return value == null ? "" : value.trim(); }
}
