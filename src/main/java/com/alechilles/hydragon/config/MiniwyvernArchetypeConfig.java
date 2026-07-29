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
    private static final MapCodec<String, Map<String, String>> PASSIVE_MODIFIER_EFFECTS_CODEC =
            new MapCodec<>(Codec.STRING, LinkedHashMap::new);
    private static final Set<String> ALLOWED_ARCHETYPES = Set.of(
            "wild", "nature", "toxic", "fire", "void", "lightning", "ice"
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
            .<Integer>append(new KeyedCodec<>("MaximumTargets", Codec.INTEGER),
                    (ability, value) -> ability.maximumTargets = value,
                    ability -> ability.maximumTargets)
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

    private static final BuilderCodec<OwnerAttackAura> OWNER_ATTACK_AURA_CODEC = BuilderCodec.builder(
            OwnerAttackAura.class, OwnerAttackAura::new)
            .<String>append(new KeyedCodec<>("EffectId", Codec.STRING), (aura, value) -> aura.effectId = value, aura -> aura.effectId).add()
            .<Double>append(new KeyedCodec<>("DurationSeconds", Codec.DOUBLE),
                    (aura, value) -> aura.durationSeconds = value == null ? 0.0 : value, aura -> aura.durationSeconds).add()
            .<Double>append(new KeyedCodec<>("DamageReductionFraction", Codec.DOUBLE),
                    (aura, value) -> aura.damageReductionFraction = value, aura -> aura.damageReductionFraction).add()
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
            .documentation("HyDragon Miniwyvern role-bound effects, owner-hit aura metadata, and active abilities.")
            .<String>append(new KeyedCodec<>("Id", Codec.STRING),
                    (asset, value) -> asset.id = value,
                    asset -> asset.id)
            .add()
            .<String>append(new KeyedCodec<>("RoleId", Codec.STRING),
                    (asset, value) -> asset.roleId = value, asset -> asset.roleId)
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
            .<Map<String, String>>append(new KeyedCodec<>("PassiveModifierEffects", PASSIVE_MODIFIER_EFFECTS_CODEC),
                    (asset, value) -> asset.passiveModifierEffects = value == null ? Map.of() : value,
                    asset -> asset.passiveModifierEffects)
            .add()
            .<Ability[]>append(new KeyedCodec<>("ActiveAbilities", ABILITY_ARRAY_CODEC),
                    (asset, value) -> asset.activeAbilities = value == null ? EMPTY_ABILITIES : value,
                    asset -> asset.activeAbilities)
            .add()
            .<OwnerAttackAura>append(new KeyedCodec<>("OwnerAttackAura", OWNER_ATTACK_AURA_CODEC),
                    (asset, value) -> asset.ownerAttackAura = value, asset -> asset.ownerAttackAura)
            .add()
            .<String>append(new KeyedCodec<>("FallbackBehavior", Codec.STRING),
                    (asset, value) -> asset.fallbackBehavior = value,
                    asset -> asset.fallbackBehavior)
            .add()
            .build();

    private AssetExtraInfo.Data data;
    private String assetKey;
    String id;
    String roleId;
    String[] particleAndSoundIds = EMPTY;
    String[] passiveEffects = EMPTY;
    Map<String, Double> passiveModifiers = Map.of();
    Map<String, String> passiveModifierEffects = Map.of();
    Ability[] activeAbilities = EMPTY_ABILITIES;
    OwnerAttackAura ownerAttackAura;
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
        if (blank(roleId)) errors.add("RoleId is required");
        else if (!roleId.trim().equals("Tamed_Wyvern_Mini_" + titleCase(normalized))) errors.add("RoleId must map form to its tamed role");
        if (blank(fallbackBehavior)) errors.add("FallbackBehavior is required");
        Set<String> presentationIds = new java.util.HashSet<>();
        for (String presentationId : particleAndSoundIds) {
            if (blank(presentationId)) {
                errors.add("ParticleAndSoundIds cannot contain blank values");
            } else if (!presentationIds.add(trim(presentationId))) {
                errors.add("ParticleAndSoundIds contains duplicate " + trim(presentationId));
            }
        }
        if (Set.of("toxic", "fire", "ice", "void", "lightning").contains(normalized) && activeAbilities.length == 0) {
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
        if (ownerAttackAura != null) errors.addAll(ownerAttackAura.validate());
        return List.copyOf(errors);
    }

    public String getAssetKey() { return assetKey; }
    public String getId() { return normalize(id); }
    public String getRoleId() { return trim(roleId); }
    public List<String> getParticleAndSoundIds() { return List.of(particleAndSoundIds.clone()); }
    public List<String> getPassiveEffects() { return List.of(passiveEffects.clone()); }
    public Map<String, Double> getPassiveModifiers() { return Map.copyOf(passiveModifiers); }
    public Map<String, String> getPassiveModifierEffects() { return Map.copyOf(passiveModifierEffects); }
    public List<Ability> getActiveAbilities() { return List.of(activeAbilities.clone()); }
    @Nullable public OwnerAttackAura getOwnerAttackAura() { return ownerAttackAura; }
    public String getFallbackBehavior() { return trim(fallbackBehavior); }

    /** One source-keyed, cooldown-gated Miniwyvern ability definition. */
    public static final class Ability {
        String id;
        String trigger;
        String targetPolicy;
        double range;
        Integer maximumTargets;
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
            String normalizedTrigger = trim(trigger).toUpperCase(Locale.ROOT);
            String normalizedTargetPolicy = trim(targetPolicy).toUpperCase(Locale.ROOT);
            if (!Set.of("COMBAT_INTERVAL", "OWNER_HEALTH_BELOW_PERCENT").contains(normalizedTrigger)) {
                errors.add("ActiveAbilities.Trigger is unsupported for " + trim(id) + ": " + trim(trigger));
            } else if (normalizedTrigger.equals("COMBAT_INTERVAL")
                    && !Set.of("OWNER_HOSTILE_ONLY", "OWNER_HOSTILE_AREA").contains(normalizedTargetPolicy)) {
                errors.add("COMBAT_INTERVAL requires a hostile target policy for " + trim(id));
            } else if (normalizedTrigger.equals("OWNER_HEALTH_BELOW_PERCENT")
                    && !normalizedTargetPolicy.equals("OWNER_ONLY")) {
                errors.add("OWNER_HEALTH_BELOW_PERCENT requires OWNER_ONLY for " + trim(id));
            }
            if (!Double.isFinite(range) || range < 0.0) errors.add("ActiveAbilities.Range must be non-negative");
            if (maximumTargets != null && maximumTargets <= 0) {
                errors.add("ActiveAbilities.MaximumTargets must be positive");
            }
            if ("OWNER_HOSTILE_AREA".equalsIgnoreCase(trim(targetPolicy)) && maximumTargets == null) {
                errors.add("Area abilities require ActiveAbilities.MaximumTargets");
            }
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
            if (maximumStacks != null && blank(effectId)) {
                errors.add("ActiveAbilities.MaximumStacks requires EffectId for " + trim(id));
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
        public int getMaximumTargets() { return maximumTargets == null ? 1 : maximumTargets; }
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

    /** Data-only owner-hit effect metadata; its runtime is deliberately separate. */
    public static final class OwnerAttackAura {
        String effectId;
        double durationSeconds;
        Double damageReductionFraction;
        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (blank(effectId)) errors.add("OwnerAttackAura.EffectId is required");
            if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0) errors.add("OwnerAttackAura.DurationSeconds must be positive");
            if (damageReductionFraction != null && (!Double.isFinite(damageReductionFraction)
                    || damageReductionFraction <= 0.0 || damageReductionFraction >= 1.0)) {
                errors.add("OwnerAttackAura.DamageReductionFraction must be in (0, 1)");
            }
            return errors;
        }
        @Nullable public String getEffectId() { return blank(effectId) ? null : effectId.trim(); }
        public double getDurationSeconds() { return durationSeconds; }
        @Nullable public Double getDamageReductionFraction() { return damageReductionFraction; }
    }

    private List<String> validatePassiveModifiers(String archetypeId) {
        List<String> errors = new ArrayList<>();
        if ("lightning".equals(archetypeId)) {
            requireMultiplier(errors, "MovementSpeedMultiplier");
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
        for (Map.Entry<String, String> entry : passiveModifierEffects.entrySet()) {
            if (!passiveModifiers.containsKey(entry.getKey())) {
                errors.add("PassiveModifierEffects." + entry.getKey()
                        + " does not name a configured PassiveModifiers semantic");
            }
            if (blank(entry.getValue())) {
                errors.add("PassiveModifierEffects." + entry.getKey() + " must name an EntityEffect asset");
            }
        }
        if (Set.of("lightning").contains(archetypeId)
                && !passiveModifierEffects.containsKey("MovementSpeedMultiplier")) {
            errors.add("PassiveModifierEffects.MovementSpeedMultiplier is required for " + archetypeId);
        }
        return errors;
    }

    private void requireMultiplier(List<String> errors, String key) {
        Double value = passiveModifiers.get(key);
        if (value == null || !Double.isFinite(value) || value <= 0.0) {
            errors.add("PassiveModifiers." + key + " must be positive");
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

    private static String titleCase(String value) {
        return value.isEmpty() ? "" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
