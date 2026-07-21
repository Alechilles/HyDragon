package com.alechilles.hydragon.config;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Data-driven Miniwyvern elemental archetype stored under {@code Server/HyDragon/MiniwyvernArchetypes}. */
public final class MiniwyvernArchetypeConfig
        implements JsonAssetWithMap<String, DefaultAssetMap<String, MiniwyvernArchetypeConfig>> {
    private static final String[] EMPTY = new String[0];
    private static final Ability[] EMPTY_ABILITIES = new Ability[0];
    private static final MapCodec<Double, Map<String, Double>> PASSIVE_MODIFIERS_CODEC =
            new MapCodec<>(Codec.DOUBLE, LinkedHashMap::new);
    private static final Set<String> ALLOWED_ARCHETYPES = Set.of(
            "neutral", "lightning", "wind", "ice", "fire", "water", "nature", "void"
    );

    private static final BuilderCodec<Ability> ABILITY_CODEC = BuilderCodec.builder(Ability.class, Ability::new)
            .<String>append(new KeyedCodec<>("Id", Codec.STRING),
                    (ability, value) -> ability.id = value,
                    ability -> ability.id)
            .add()
            .<String>append(new KeyedCodec<>("Trigger", Codec.STRING),
                    (ability, value) -> ability.trigger = value,
                    ability -> ability.trigger)
            .add()
            .<String>append(new KeyedCodec<>("TargetPolicy", Codec.STRING),
                    (ability, value) -> ability.targetPolicy = value,
                    ability -> ability.targetPolicy)
            .add()
            .<Double>append(new KeyedCodec<>("Range", Codec.DOUBLE),
                    (ability, value) -> ability.range = value == null ? 0.0 : value,
                    ability -> ability.range)
            .add()
            .<Double>append(new KeyedCodec<>("CooldownSeconds", Codec.DOUBLE),
                    (ability, value) -> ability.cooldownSeconds = value == null ? 0.0 : value,
                    ability -> ability.cooldownSeconds)
            .add()
            .<String>append(new KeyedCodec<>("EffectId", Codec.STRING),
                    (ability, value) -> ability.effectId = value,
                    ability -> ability.effectId)
            .add()
            .<String>append(new KeyedCodec<>("ProjectileId", Codec.STRING),
                    (ability, value) -> ability.projectileId = value,
                    ability -> ability.projectileId)
            .add()
            .<Double>append(new KeyedCodec<>("Magnitude", Codec.DOUBLE),
                    (ability, value) -> ability.magnitude = value == null ? 0.0 : value,
                    ability -> ability.magnitude)
            .add()
            .<Integer>append(new KeyedCodec<>("MaximumStacks", Codec.INTEGER),
                    (ability, value) -> ability.maximumStacks = value,
                    ability -> ability.maximumStacks)
            .add()
            .<Double>append(new KeyedCodec<>("BuildupPerHit", Codec.DOUBLE),
                    (ability, value) -> ability.buildupPerHit = value,
                    ability -> ability.buildupPerHit)
            .add()
            .<Double>append(new KeyedCodec<>("BuildupThreshold", Codec.DOUBLE),
                    (ability, value) -> ability.buildupThreshold = value,
                    ability -> ability.buildupThreshold)
            .add()
            .<Double>append(new KeyedCodec<>("BuildupCap", Codec.DOUBLE),
                    (ability, value) -> ability.buildupCap = value,
                    ability -> ability.buildupCap)
            .add()
            .<String>append(new KeyedCodec<>("ControlEffectId", Codec.STRING),
                    (ability, value) -> ability.controlEffectId = value,
                    ability -> ability.controlEffectId)
            .add()
            .<Double>append(new KeyedCodec<>("ControlImmunitySeconds", Codec.DOUBLE),
                    (ability, value) -> ability.controlImmunitySeconds = value,
                    ability -> ability.controlImmunitySeconds)
            .add()
            .<Double>append(new KeyedCodec<>("OwnerHealthThreshold", Codec.DOUBLE),
                    (ability, value) -> ability.ownerHealthThreshold = value,
                    ability -> ability.ownerHealthThreshold)
            .add()
            .<Double>append(new KeyedCodec<>("MaximumHealFraction", Codec.DOUBLE),
                    (ability, value) -> ability.maximumHealFraction = value,
                    ability -> ability.maximumHealFraction)
            .add()
            .<Double>append(new KeyedCodec<>("DurationSeconds", Codec.DOUBLE),
                    (ability, value) -> ability.durationSeconds = value == null ? 0.0 : value,
                    ability -> ability.durationSeconds)
            .add()
            .<String>append(new KeyedCodec<>("StackingPolicy", Codec.STRING),
                    (ability, value) -> ability.stackingPolicy = value,
                    ability -> ability.stackingPolicy)
            .add()
            .<Double>append(new KeyedCodec<>("MinimumDefenseMultiplier", Codec.DOUBLE),
                    (ability, value) -> ability.minimumDefenseMultiplier = value,
                    ability -> ability.minimumDefenseMultiplier)
            .add()
            .<Double>append(new KeyedCodec<>("MaximumReduction", Codec.DOUBLE),
                    (ability, value) -> ability.maximumReduction = value,
                    ability -> ability.maximumReduction)
            .add()
            .build();

    private static final ArrayCodec<Ability> ABILITY_ARRAY_CODEC =
            new ArrayCodec<>(ABILITY_CODEC, Ability[]::new);

    public static final AssetBuilderCodec<String, MiniwyvernArchetypeConfig> CODEC = AssetBuilderCodec.builder(
            MiniwyvernArchetypeConfig.class,
            MiniwyvernArchetypeConfig::new,
            Codec.STRING,
            (asset, key) -> asset.assetKey = key,
            asset -> asset.assetKey,
            (asset, data) -> asset.data = data,
            asset -> asset.data
    )
            .documentation("HyDragon Miniwyvern appearance, attunement item, passive effects, and active abilities.")
            .<String>append(new KeyedCodec<>("Id", Codec.STRING),
                    (asset, value) -> asset.id = value,
                    asset -> asset.id)
            .add()
            .<String>append(new KeyedCodec<>("EssenceSemanticId", Codec.STRING),
                    (asset, value) -> asset.essenceSemanticId = value,
                    asset -> asset.essenceSemanticId)
            .add()
            .<String>append(new KeyedCodec<>("EssenceItemId", Codec.STRING),
                    (asset, value) -> asset.essenceItemId = value,
                    asset -> asset.essenceItemId)
            .add()
            .<String>append(new KeyedCodec<>("AppearanceId", Codec.STRING),
                    (asset, value) -> asset.appearanceId = value,
                    asset -> asset.appearanceId)
            .add()
            .<String[]>append(new KeyedCodec<>("ParticleAndSoundIds", Codec.STRING_ARRAY),
                    (asset, value) -> asset.particleAndSoundIds = value == null ? EMPTY : value,
                    asset -> asset.particleAndSoundIds)
            .add()
            .<String[]>append(new KeyedCodec<>("PassiveEffects", Codec.STRING_ARRAY),
                    (asset, value) -> asset.passiveEffects = value == null ? EMPTY : value,
                    asset -> asset.passiveEffects)
            .add()
            .<Map<String, Double>>append(new KeyedCodec<>("PassiveModifiers", PASSIVE_MODIFIERS_CODEC),
                    (asset, value) -> asset.passiveModifiers = value == null ? Map.of() : value,
                    asset -> asset.passiveModifiers)
            .add()
            .<Ability[]>append(new KeyedCodec<>("ActiveAbilities", ABILITY_ARRAY_CODEC),
                    (asset, value) -> asset.activeAbilities = value == null ? EMPTY_ABILITIES : value,
                    asset -> asset.activeAbilities)
            .add()
            .<String>append(new KeyedCodec<>("FallbackBehavior", Codec.STRING),
                    (asset, value) -> asset.fallbackBehavior = value,
                    asset -> asset.fallbackBehavior)
            .add()
            .build();

    private AssetExtraInfo.Data data;
    private String assetKey;
    String id;
    String essenceSemanticId;
    String essenceItemId;
    String appearanceId;
    String[] particleAndSoundIds = EMPTY;
    String[] passiveEffects = EMPTY;
    Map<String, Double> passiveModifiers = Map.of();
    Ability[] activeAbilities = EMPTY_ABILITIES;
    String fallbackBehavior;

    MiniwyvernArchetypeConfig() {
    }

    @Nonnull
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        String normalized = normalize(id);
        if (!ALLOWED_ARCHETYPES.contains(normalized)) {
            errors.add("Id must be one of " + ALLOWED_ARCHETYPES);
        }
        if (!normalized.equals("neutral")) {
            if (blank(essenceSemanticId)) errors.add("EssenceSemanticId is required");
            if (blank(essenceItemId)) errors.add("EssenceItemId is required");
        }
        if (blank(appearanceId)) errors.add("AppearanceId is required");
        if (blank(fallbackBehavior)) errors.add("FallbackBehavior is required");
        if (Set.of("fire", "ice", "water", "void").contains(normalized) && activeAbilities.length == 0) {
            errors.add("Archetype " + normalized + " requires at least one active ability");
        }
        errors.addAll(validatePassiveModifiers(normalized));
        for (Ability ability : activeAbilities) {
            if (ability == null) {
                errors.add("ActiveAbilities contains null");
            } else {
                errors.addAll(ability.validate(normalized));
            }
        }
        return List.copyOf(errors);
    }

    public String getAssetKey() { return assetKey; }
    public String getId() { return normalize(id); }
    public String getEssenceSemanticId() { return normalize(essenceSemanticId); }
    @Nullable public String getEssenceItemId() { return blank(essenceItemId) ? null : essenceItemId.trim(); }
    public String getAppearanceId() { return trim(appearanceId); }
    public List<String> getParticleAndSoundIds() { return List.of(particleAndSoundIds.clone()); }
    public List<String> getPassiveEffects() { return List.of(passiveEffects.clone()); }
    public Map<String, Double> getPassiveModifiers() { return Map.copyOf(passiveModifiers); }
    public List<Ability> getActiveAbilities() { return List.of(activeAbilities.clone()); }
    public String getFallbackBehavior() { return trim(fallbackBehavior); }

    /** One source-keyed, cooldown-gated Miniwyvern ability definition. */
    public static final class Ability {
        String id;
        String trigger;
        String targetPolicy;
        double range;
        double cooldownSeconds;
        String effectId;
        String projectileId;
        double magnitude;
        Integer maximumStacks;
        Double buildupPerHit;
        Double buildupThreshold;
        Double buildupCap;
        String controlEffectId;
        Double controlImmunitySeconds;
        Double ownerHealthThreshold;
        Double maximumHealFraction;
        double durationSeconds;
        String stackingPolicy;
        Double minimumDefenseMultiplier;
        Double maximumReduction;

        private List<String> validate(String archetypeId) {
            List<String> errors = new ArrayList<>();
            if (blank(id)) errors.add("ActiveAbilities.Id is required");
            if (blank(trigger)) errors.add("ActiveAbilities.Trigger is required for " + trim(id));
            if (blank(targetPolicy)) errors.add("ActiveAbilities.TargetPolicy is required for " + trim(id));
            if (!Double.isFinite(range) || range < 0.0) errors.add("ActiveAbilities.Range must be non-negative");
            if (!Double.isFinite(cooldownSeconds) || cooldownSeconds < 0.0) {
                errors.add("ActiveAbilities.CooldownSeconds must be non-negative");
            }
            if (blank(effectId) && blank(projectileId) && magnitude == 0.0) {
                errors.add("ActiveAbilities requires EffectId, ProjectileId, or non-zero Magnitude for " + trim(id));
            }
            if (!Double.isFinite(magnitude)) errors.add("ActiveAbilities.Magnitude must be finite");
            if (maximumStacks != null && maximumStacks <= 0) {
                errors.add("ActiveAbilities.MaximumStacks must be positive");
            }
            if (!Double.isFinite(durationSeconds) || durationSeconds < 0.0) {
                errors.add("ActiveAbilities.DurationSeconds must be non-negative");
            }
            if (blank(stackingPolicy)) errors.add("ActiveAbilities.StackingPolicy is required for " + trim(id));
            if ("ice".equals(archetypeId)) {
                if (!positive(buildupPerHit)) errors.add("Ice abilities require positive BuildupPerHit");
                if (!positive(buildupThreshold)) errors.add("Ice abilities require positive BuildupThreshold");
                if (!positive(buildupCap)) errors.add("Ice abilities require positive BuildupCap");
                if (buildupThreshold != null && buildupCap != null && buildupThreshold > buildupCap) {
                    errors.add("Ice BuildupThreshold cannot exceed BuildupCap");
                }
                if (blank(controlEffectId)) errors.add("Ice abilities require ControlEffectId");
                if (!positive(controlImmunitySeconds)) {
                    errors.add("Ice abilities require positive ControlImmunitySeconds");
                }
            }
            if ("water".equals(archetypeId)) {
                if (!fraction(ownerHealthThreshold)) {
                    errors.add("Water abilities require OwnerHealthThreshold in (0, 1]");
                }
                if (!fraction(maximumHealFraction)) {
                    errors.add("Water abilities require MaximumHealFraction in (0, 1]");
                }
            }
            if ("void".equals(archetypeId)) {
                if (minimumDefenseMultiplier == null || !Double.isFinite(minimumDefenseMultiplier)
                        || minimumDefenseMultiplier <= 0.0 || minimumDefenseMultiplier > 1.0) {
                    errors.add("Void abilities require MinimumDefenseMultiplier in (0, 1]");
                }
                if (maximumReduction == null || !Double.isFinite(maximumReduction)
                        || maximumReduction <= 0.0 || maximumReduction >= 1.0) {
                    errors.add("Void abilities require MaximumReduction in (0, 1)");
                }
                if (maximumReduction != null && Double.isFinite(maximumReduction)
                        && Math.abs(magnitude) > maximumReduction) {
                    errors.add("Void ability Magnitude cannot exceed MaximumReduction");
                }
                if (minimumDefenseMultiplier != null && maximumReduction != null
                        && Double.isFinite(minimumDefenseMultiplier) && Double.isFinite(maximumReduction)
                        && maximumReduction > 1.0 - minimumDefenseMultiplier) {
                    errors.add("Void MaximumReduction cannot cross MinimumDefenseMultiplier");
                }
                String policy = trim(stackingPolicy).toUpperCase(Locale.ROOT);
                if (!policy.equals("SOURCE_REFRESH") && !policy.equals("NON_STACKING") && !policy.equals("CLAMPED")) {
                    errors.add("Void abilities require SOURCE_REFRESH, NON_STACKING, or CLAMPED stacking");
                }
            }
            return errors;
        }

        public String getId() { return trim(id); }
        public String getTrigger() { return trim(trigger); }
        public String getTargetPolicy() { return trim(targetPolicy); }
        public double getRange() { return range; }
        public double getCooldownSeconds() { return cooldownSeconds; }
        @Nullable public String getEffectId() { return blank(effectId) ? null : effectId.trim(); }
        @Nullable public String getProjectileId() { return blank(projectileId) ? null : projectileId.trim(); }
        public double getMagnitude() { return magnitude; }
        @Nullable public Integer getMaximumStacks() { return maximumStacks; }
        @Nullable public Double getBuildupPerHit() { return buildupPerHit; }
        @Nullable public Double getBuildupThreshold() { return buildupThreshold; }
        @Nullable public Double getBuildupCap() { return buildupCap; }
        @Nullable public String getControlEffectId() { return blank(controlEffectId) ? null : controlEffectId.trim(); }
        @Nullable public Double getControlImmunitySeconds() { return controlImmunitySeconds; }
        @Nullable public Double getOwnerHealthThreshold() { return ownerHealthThreshold; }
        @Nullable public Double getMaximumHealFraction() { return maximumHealFraction; }
        public double getDurationSeconds() { return durationSeconds; }
        public String getStackingPolicy() { return trim(stackingPolicy); }
        @Nullable public Double getMinimumDefenseMultiplier() { return minimumDefenseMultiplier; }
        @Nullable public Double getMaximumReduction() { return maximumReduction; }
    }

    private List<String> validatePassiveModifiers(String archetypeId) {
        List<String> errors = new ArrayList<>();
        if ("lightning".equals(archetypeId)) {
            requireMultiplier(errors, "MovementSpeedMultiplier");
            requireMultiplier(errors, "ActionSpeedMultiplier");
        } else if ("wind".equals(archetypeId)) {
            requireMultiplier(errors, "MovementSpeedMultiplier");
            requireMultiplier(errors, "JumpMultiplier");
            requireMultiplier(errors, "MobilityMultiplier");
            requireAtLeast(errors, "MaximumMovementSpeedMultiplier", "MovementSpeedMultiplier");
            requireAtLeast(errors, "MaximumJumpMultiplier", "JumpMultiplier");
        } else if ("nature".equals(archetypeId)) {
            Double tick = passiveModifiers.get("RegenerationTickSeconds");
            if (!positive(tick)) errors.add("Nature PassiveModifiers.RegenerationTickSeconds must be positive");
            Double cap = passiveModifiers.get("MaximumHealFractionPerTick");
            if (!fraction(cap)) {
                errors.add("Nature PassiveModifiers.MaximumHealFractionPerTick must be in (0, 1]");
            }
        }
        for (Map.Entry<String, Double> entry : passiveModifiers.entrySet()) {
            if (entry.getValue() == null || !Double.isFinite(entry.getValue())) {
                errors.add("PassiveModifiers." + entry.getKey() + " must be finite");
            }
        }
        return errors;
    }

    private void requireMultiplier(List<String> errors, String key) {
        Double value = passiveModifiers.get(key);
        if (value == null || !Double.isFinite(value) || value <= 0.0) {
            errors.add("PassiveModifiers." + key + " must be positive");
        }
    }

    private void requireAtLeast(List<String> errors, String maximumKey, String valueKey) {
        Double maximum = passiveModifiers.get(maximumKey);
        Double value = passiveModifiers.get(valueKey);
        if (maximum == null || !Double.isFinite(maximum) || maximum <= 0.0) {
            errors.add("PassiveModifiers." + maximumKey + " must be positive");
        } else if (value != null && Double.isFinite(value) && maximum < value) {
            errors.add("PassiveModifiers." + maximumKey + " cannot be below " + valueKey);
        }
    }

    private static boolean positive(@Nullable Double value) {
        return value != null && Double.isFinite(value) && value > 0.0;
    }

    private static boolean fraction(@Nullable Double value) {
        return value != null && Double.isFinite(value) && value > 0.0 && value <= 1.0;
    }

    private static String normalize(@Nullable String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
