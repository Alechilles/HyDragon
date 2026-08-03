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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Retained Java owner-passive and owner-aura metadata for a Miniwyvern form. */
public final class MiniwyvernArchetypeConfig
        implements JsonAssetWithMap<String, DefaultAssetMap<String, MiniwyvernArchetypeConfig>> {
    private static final String[] EMPTY = new String[0];
    private static final Upgrade[] EMPTY_UPGRADES = new Upgrade[0];
    private static final MapCodec<Double, Map<String, Double>> PASSIVE_MODIFIERS_CODEC =
            new MapCodec<>(Codec.DOUBLE, LinkedHashMap::new);
    private static final MapCodec<String, Map<String, String>> PASSIVE_MODIFIER_EFFECTS_CODEC =
            new MapCodec<>(Codec.STRING, LinkedHashMap::new);
    private static final Set<String> ALLOWED_ARCHETYPES = Set.of(
            "wild", "nature", "toxic", "fire", "void", "lightning", "ice");

    private static final BuilderCodec<OwnerAttackAura> OWNER_ATTACK_AURA_CODEC = BuilderCodec.builder(
            OwnerAttackAura.class, OwnerAttackAura::new)
            .<String>append(new KeyedCodec<>("EffectId", Codec.STRING),
                    (aura, value) -> aura.effectId = value, aura -> aura.effectId).add()
            .<Double>append(new KeyedCodec<>("DurationSeconds", Codec.DOUBLE),
                    (aura, value) -> aura.durationSeconds = value == null ? 0.0D : value,
                    aura -> aura.durationSeconds).add()
            .<Double>append(new KeyedCodec<>("DamageReductionFraction", Codec.DOUBLE),
                    (aura, value) -> aura.damageReductionFraction = value,
                    aura -> aura.damageReductionFraction).add()
            .<Double>append(new KeyedCodec<>("TargetDamageTakenFraction", Codec.DOUBLE),
                    (aura, value) -> aura.targetDamageTakenFraction = value,
                    aura -> aura.targetDamageTakenFraction).add()
            .build();

    private static final BuilderCodec<Upgrade> ESSENCE_BOND_UPGRADE_CODEC = BuilderCodec.builder(
            Upgrade.class, Upgrade::new)
            .<String>append(new KeyedCodec<>("TalentId", Codec.STRING),
                    (upgrade, value) -> upgrade.talentId = value, upgrade -> upgrade.talentId).add()
            .<String>append(new KeyedCodec<>("Semantic", Codec.STRING),
                    (upgrade, value) -> upgrade.semantic = value, upgrade -> upgrade.semantic).add()
            .<String>append(new KeyedCodec<>("TargetEffectId", Codec.STRING),
                    (upgrade, value) -> upgrade.targetEffectId = value, upgrade -> upgrade.targetEffectId).add()
            .<Double>append(new KeyedCodec<>("TargetDurationSeconds", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.targetDurationSeconds = value,
                    upgrade -> upgrade.targetDurationSeconds).add()
            .<Double>append(new KeyedCodec<>("TargetOutgoingDamageReductionFraction", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.targetOutgoingDamageReductionFraction = value,
                    upgrade -> upgrade.targetOutgoingDamageReductionFraction).add()
            .<Double>append(new KeyedCodec<>("TargetDamageReductionFraction", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.targetDamageReductionFraction = value,
                    upgrade -> upgrade.targetDamageReductionFraction).add()
            .<Double>append(new KeyedCodec<>("DamageReductionFraction", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.damageReductionFraction = value,
                    upgrade -> upgrade.damageReductionFraction).add()
            .<Double>append(new KeyedCodec<>("TargetDamageTakenFraction", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.targetDamageTakenFraction = value,
                    upgrade -> upgrade.targetDamageTakenFraction).add()
            .<Double>append(new KeyedCodec<>("OwnerDamageToAffectedFraction", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.ownerDamageToAffectedFraction = value,
                    upgrade -> upgrade.ownerDamageToAffectedFraction).add()
            .<String>append(new KeyedCodec<>("WardEffectId", Codec.STRING),
                    (upgrade, value) -> upgrade.wardEffectId = value, upgrade -> upgrade.wardEffectId).add()
            .<Double>append(new KeyedCodec<>("ConditionalWardDamageReductionFraction", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.conditionalWardDamageReductionFraction = value,
                    upgrade -> upgrade.conditionalWardDamageReductionFraction).add()
            .<Double>append(new KeyedCodec<>("WardDamageReductionFraction", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.wardDamageReductionFraction = value,
                    upgrade -> upgrade.wardDamageReductionFraction).add()
            .<Double>append(new KeyedCodec<>("SpeedBurstMultiplier", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.speedBurstMultiplier = value,
                    upgrade -> upgrade.speedBurstMultiplier).add()
            .<Double>append(new KeyedCodec<>("SpeedBurstDurationSeconds", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.speedBurstDurationSeconds = value,
                    upgrade -> upgrade.speedBurstDurationSeconds).add()
            .<Double>append(new KeyedCodec<>("SiphonMaximumHealthFraction", Codec.DOUBLE),
                    (upgrade, value) -> upgrade.siphonMaximumHealthFraction = value,
                    upgrade -> upgrade.siphonMaximumHealthFraction).add()
            .<Long>append(new KeyedCodec<>("SiphonCooldownMs", Codec.LONG),
                    (upgrade, value) -> upgrade.siphonCooldownMs = value,
                    upgrade -> upgrade.siphonCooldownMs).add()
            .build();

    private static final ArrayCodec<Upgrade> ESSENCE_BOND_UPGRADE_ARRAY_CODEC =
            new ArrayCodec<>(ESSENCE_BOND_UPGRADE_CODEC, Upgrade[]::new);

    private static final BuilderCodec<EssenceBondAura> ESSENCE_BOND_AURA_CODEC = BuilderCodec.builder(
            EssenceBondAura.class, EssenceBondAura::new)
            .<Upgrade[]>append(new KeyedCodec<>("Upgrades", ESSENCE_BOND_UPGRADE_ARRAY_CODEC),
                    (aura, value) -> aura.upgrades = value == null ? EMPTY_UPGRADES : value,
                    aura -> aura.upgrades).add()
            .build();

    public static final AssetBuilderCodec<String, MiniwyvernArchetypeConfig> CODEC = AssetBuilderCodec.builder(
            MiniwyvernArchetypeConfig.class, MiniwyvernArchetypeConfig::new, Codec.STRING,
            (asset, key) -> asset.assetKey = key, asset -> asset.assetKey,
            (asset, data) -> asset.data = data, asset -> asset.data)
            .documentation("HyDragon Miniwyvern Java-owned owner-passive and owner-aura metadata.")
            .<String>append(new KeyedCodec<>("Id", Codec.STRING),
                    (asset, value) -> asset.id = value, asset -> asset.id).add()
            .<String>append(new KeyedCodec<>("RoleId", Codec.STRING),
                    (asset, value) -> asset.roleId = value, asset -> asset.roleId).add()
            .<String>append(new KeyedCodec<>("RequiredTalentId", Codec.STRING),
                    (asset, value) -> asset.requiredTalentId = value, asset -> asset.requiredTalentId).add()
            .<String[]>append(new KeyedCodec<>("ParticleAndSoundIds", Codec.STRING_ARRAY),
                    (asset, value) -> asset.particleAndSoundIds = value == null ? EMPTY : value,
                    asset -> asset.particleAndSoundIds).add()
            .<String[]>append(new KeyedCodec<>("PassiveEffects", Codec.STRING_ARRAY),
                    (asset, value) -> asset.passiveEffects = value == null ? EMPTY : value,
                    asset -> asset.passiveEffects).add()
            .<Map<String, Double>>append(new KeyedCodec<>("PassiveModifiers", PASSIVE_MODIFIERS_CODEC),
                    (asset, value) -> asset.passiveModifiers = value == null ? Map.of() : value,
                    asset -> asset.passiveModifiers).add()
            .<Map<String, String>>append(new KeyedCodec<>("PassiveModifierEffects", PASSIVE_MODIFIER_EFFECTS_CODEC),
                    (asset, value) -> asset.passiveModifierEffects = value == null ? Map.of() : value,
                    asset -> asset.passiveModifierEffects).add()
            .<OwnerAttackAura>append(new KeyedCodec<>("OwnerAttackAura", OWNER_ATTACK_AURA_CODEC),
                    (asset, value) -> asset.ownerAttackAura = value, asset -> asset.ownerAttackAura).add()
            .<EssenceBondAura>append(new KeyedCodec<>("EssenceBondAura", ESSENCE_BOND_AURA_CODEC),
                    (asset, value) -> asset.essenceBondAura = value, asset -> asset.essenceBondAura).add()
            .<String>append(new KeyedCodec<>("FallbackBehavior", Codec.STRING),
                    (asset, value) -> asset.fallbackBehavior = value, asset -> asset.fallbackBehavior).add()
            .build();

    private AssetExtraInfo.Data data;
    private String assetKey;
    String id;
    String roleId;
    String requiredTalentId;
    String[] particleAndSoundIds = EMPTY;
    String[] passiveEffects = EMPTY;
    Map<String, Double> passiveModifiers = Map.of();
    Map<String, String> passiveModifierEffects = Map.of();
    OwnerAttackAura ownerAttackAura;
    EssenceBondAura essenceBondAura;
    String fallbackBehavior;

    MiniwyvernArchetypeConfig() { }

    @Nonnull
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        String normalized = normalize(id);
        if (!ALLOWED_ARCHETYPES.contains(normalized)) errors.add("Id must be one of " + ALLOWED_ARCHETYPES);
        if (blank(roleId)) errors.add("RoleId is required");
        else if (!roleId.trim().equals("Tamed_Wyvern_Mini_" + titleCase(normalized))) {
            errors.add("RoleId must map form to its tamed role");
        }
        if (blank(fallbackBehavior)) errors.add("FallbackBehavior is required");
        Set<String> presentationIds = new java.util.HashSet<>();
        for (String presentationId : particleAndSoundIds) {
            if (blank(presentationId)) errors.add("ParticleAndSoundIds cannot contain blank values");
            else if (!presentationIds.add(trim(presentationId))) {
                errors.add("ParticleAndSoundIds contains duplicate " + trim(presentationId));
            }
        }
        errors.addAll(validatePassiveModifiers(normalized));
        if (ownerAttackAura != null) errors.addAll(ownerAttackAura.validate());
        if (essenceBondAura != null) errors.addAll(essenceBondAura.validate(normalized));
        return List.copyOf(errors);
    }

    public String getAssetKey() { return assetKey; }
    public String getId() { return normalize(id); }
    public String getRoleId() { return trim(roleId); }
    public String getRequiredTalentId() { return trim(requiredTalentId); }
    public List<String> getParticleAndSoundIds() { return List.of(particleAndSoundIds.clone()); }
    public List<String> getPassiveEffects() { return List.of(passiveEffects.clone()); }
    public Map<String, Double> getPassiveModifiers() { return Map.copyOf(passiveModifiers); }
    public Map<String, String> getPassiveModifierEffects() { return Map.copyOf(passiveModifierEffects); }
    @Nullable public OwnerAttackAura getOwnerAttackAura() { return ownerAttackAura; }
    @Nullable public EssenceBondAura getEssenceBondAura() { return essenceBondAura; }
    public String getFallbackBehavior() { return trim(fallbackBehavior); }

    /** Optional form-local modifiers keyed by purchased Essence Bond talent IDs. */
    public static final class EssenceBondAura {
        Upgrade[] upgrades = EMPTY_UPGRADES;

        private List<String> validate(String archetypeId) {
            List<String> errors = new ArrayList<>();
            Set<String> talentIds = new HashSet<>();
            if (upgrades == null) {
                errors.add("EssenceBondAura.Upgrades must not be null");
                return errors;
            }
            for (int index = 0; index < upgrades.length; index++) {
                Upgrade upgrade = upgrades[index];
                if (upgrade == null) {
                    errors.add("EssenceBondAura.Upgrades[" + index + "] must not be null");
                    continue;
                }
                String talentId = upgrade.getTalentId();
                if (blank(talentId)) {
                    errors.add("EssenceBondAura.Upgrades[" + index + "].TalentId is required");
                } else if (!talentIds.add(talentId.toLowerCase(Locale.ROOT))) {
                    errors.add("EssenceBondAura.Upgrades contains duplicate " + talentId);
                }
                for (String error : upgrade.validate(archetypeId)) {
                    errors.add("EssenceBondAura.Upgrades[" + index + "]." + error);
                }
            }
            return errors;
        }

        public List<Upgrade> getUpgrades() {
            return upgrades == null ? List.of() : List.of(upgrades.clone());
        }
    }

    /** One ordered, optional contribution to a live Essence Bond aura. */
    public static final class Upgrade {
        private static final Set<String> KNOWN_SEMANTICS = Set.of(
                "targeteffect", "targetoutgoingdamagereduction", "targetdamagetaken",
                "ownerdamagetoaffected", "ward", "conditionalward", "speedburst", "siphon");

        String talentId;
        String semantic;
        String targetEffectId;
        Double targetDurationSeconds;
        Double targetOutgoingDamageReductionFraction;
        // Compatibility aliases accepted by older hand-authored data.
        Double targetDamageReductionFraction;
        Double damageReductionFraction;
        Double targetDamageTakenFraction;
        Double ownerDamageToAffectedFraction;
        String wardEffectId;
        Double conditionalWardDamageReductionFraction;
        Double wardDamageReductionFraction;
        Double speedBurstMultiplier;
        Double speedBurstDurationSeconds;
        Double siphonMaximumHealthFraction;
        Long siphonCooldownMs;

        private List<String> validate(String archetypeId) {
            List<String> errors = new ArrayList<>();
            if (!blank(semantic) && !KNOWN_SEMANTICS.contains(normalize(semantic))) {
                errors.add("Semantic is unknown: " + trim(semantic));
            }
            validateTarget(errors);
            validateFraction(errors, "TargetOutgoingDamageReductionFraction", targetOutgoingDamageReductionFraction);
            validateFraction(errors, "TargetDamageReductionFraction", targetDamageReductionFraction);
            validateFraction(errors, "DamageReductionFraction", damageReductionFraction);
            validateFraction(errors, "TargetDamageTakenFraction", targetDamageTakenFraction);
            validateFraction(errors, "OwnerDamageToAffectedFraction", ownerDamageToAffectedFraction);
            if (wardEffectId != null && blank(wardEffectId)) {
                errors.add("WardEffectId must not be blank when configured");
            }
            validateFraction(errors, "ConditionalWardDamageReductionFraction",
                    conditionalWardDamageReductionFraction);
            validateFraction(errors, "WardDamageReductionFraction", wardDamageReductionFraction);
            validateFraction(errors, "SiphonMaximumHealthFraction", siphonMaximumHealthFraction);
            if (speedBurstMultiplier != null
                    && (!Double.isFinite(speedBurstMultiplier) || speedBurstMultiplier <= 0.0D)) {
                errors.add("SpeedBurstMultiplier must be positive and finite");
            }
            validatePositive(errors, "SpeedBurstDurationSeconds", speedBurstDurationSeconds);
            if (speedBurstMultiplier != null && speedBurstDurationSeconds == null) {
                errors.add("SpeedBurstDurationSeconds is required when SpeedBurstMultiplier is configured");
            }
            if (speedBurstDurationSeconds != null && speedBurstMultiplier == null) {
                errors.add("SpeedBurstMultiplier is required when SpeedBurstDurationSeconds is configured");
            }
            validatePositiveLong(errors, "SiphonCooldownMs", siphonCooldownMs);
            if (siphonMaximumHealthFraction != null && siphonMaximumHealthFraction > 0.0D
                    && siphonCooldownMs == null) {
                errors.add("SiphonCooldownMs is required when SiphonMaximumHealthFraction is configured");
            }
            if (siphonCooldownMs != null && siphonMaximumHealthFraction == null) {
                errors.add("SiphonMaximumHealthFraction is required when SiphonCooldownMs is configured");
            }
            if (siphonMaximumHealthFraction != null && siphonMaximumHealthFraction > 0.0D) {
                if (!"void".equals(archetypeId)) {
                    errors.add("SiphonMaximumHealthFraction is only valid for void");
                }
                if (siphonMaximumHealthFraction > 0.01D) {
                    errors.add("SiphonMaximumHealthFraction must not exceed 0.01");
                }
                if (siphonCooldownMs != null && siphonCooldownMs < 3_000L) {
                    errors.add("SiphonCooldownMs must be at least 3000 for a siphon");
                }
            }
            return errors;
        }

        private void validateTarget(List<String> errors) {
            if (targetDurationSeconds != null) {
                if (!Double.isFinite(targetDurationSeconds) || targetDurationSeconds <= 0.0D) {
                    errors.add("TargetDurationSeconds must be positive and finite");
                }
                if (blank(targetEffectId)) {
                    errors.add("TargetEffectId is required when TargetDurationSeconds is configured");
                }
            } else if (!blank(targetEffectId)) {
                errors.add("TargetDurationSeconds is required when TargetEffectId is configured");
            }
        }

        private static void validateFraction(List<String> errors, String name, Double value) {
            if (value != null && (!Double.isFinite(value) || value < 0.0D || value >= 1.0D)) {
                errors.add(name + " must be a finite fraction in [0, 1)");
            }
        }

        private static void validatePositive(List<String> errors, String name, Double value) {
            if (value != null && (!Double.isFinite(value) || value <= 0.0D)) {
                errors.add(name + " must be positive and finite");
            }
        }

        private static void validatePositiveLong(List<String> errors, String name, Long value) {
            if (value != null && value <= 0L) errors.add(name + " must be positive");
        }

        public String getTalentId() { return trim(talentId); }
        @Nullable public String getSemantic() { return blank(semantic) ? null : trim(semantic); }
        @Nullable public String getTargetEffectId() { return blank(targetEffectId) ? null : trim(targetEffectId); }
        @Nullable public Double getTargetDurationSecondsOverride() { return targetDurationSeconds; }
        public double getTargetDurationSeconds() { return targetDurationSeconds == null ? 0.0D : targetDurationSeconds; }
        @Nullable public Double getTargetOutgoingDamageReductionFractionOverride() {
            if (targetOutgoingDamageReductionFraction != null) return targetOutgoingDamageReductionFraction;
            if (targetDamageReductionFraction != null) return targetDamageReductionFraction;
            return damageReductionFraction;
        }
        public double getTargetOutgoingDamageReductionFraction() {
            if (targetOutgoingDamageReductionFraction != null) return targetOutgoingDamageReductionFraction;
            if (targetDamageReductionFraction != null) return targetDamageReductionFraction;
            return damageReductionFraction == null ? 0.0D : damageReductionFraction;
        }
        @Nullable public Double getTargetDamageTakenFractionOverride() { return targetDamageTakenFraction; }
        public double getTargetDamageTakenFraction() {
            return targetDamageTakenFraction == null ? 0.0D : targetDamageTakenFraction;
        }
        @Nullable public Double getOwnerDamageToAffectedFractionOverride() {
            return ownerDamageToAffectedFraction;
        }
        public double getOwnerDamageToAffectedFraction() {
            return ownerDamageToAffectedFraction == null ? 0.0D : ownerDamageToAffectedFraction;
        }
        @Nullable public String getWardEffectId() { return blank(wardEffectId) ? null : trim(wardEffectId); }
        @Nullable public Double getConditionalWardDamageReductionFractionOverride() {
            return conditionalWardDamageReductionFraction;
        }
        public double getConditionalWardDamageReductionFraction() {
            return conditionalWardDamageReductionFraction == null
                    ? 0.0D : conditionalWardDamageReductionFraction;
        }
        @Nullable public Double getWardDamageReductionFractionOverride() {
            return wardDamageReductionFraction;
        }
        public double getWardDamageReductionFraction() {
            return wardDamageReductionFraction == null ? 0.0D : wardDamageReductionFraction;
        }
        @Nullable public Double getSpeedBurstMultiplierOverride() { return speedBurstMultiplier; }
        public double getSpeedBurstMultiplier() { return speedBurstMultiplier == null ? 0.0D : speedBurstMultiplier; }
        @Nullable public Double getSpeedBurstDurationSecondsOverride() { return speedBurstDurationSeconds; }
        public double getSpeedBurstDurationSeconds() {
            return speedBurstDurationSeconds == null ? 0.0D : speedBurstDurationSeconds;
        }
        @Nullable public Double getSiphonMaximumHealthFractionOverride() {
            return siphonMaximumHealthFraction;
        }
        public double getSiphonMaximumHealthFraction() {
            return siphonMaximumHealthFraction == null ? 0.0D : siphonMaximumHealthFraction;
        }
        @Nullable public Long getSiphonCooldownMsOverride() { return siphonCooldownMs; }
        public long getSiphonCooldownMs() { return siphonCooldownMs == null ? 0L : siphonCooldownMs; }
    }

    /** Data-only owner-hit effect metadata; its runtime is deliberately separate. */
    public static final class OwnerAttackAura {
        String effectId;
        double durationSeconds;
        Double damageReductionFraction;
        Double targetDamageTakenFraction;

        private List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (blank(effectId)) errors.add("OwnerAttackAura.EffectId is required");
            if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0D) {
                errors.add("OwnerAttackAura.DurationSeconds must be positive");
            }
            if (damageReductionFraction != null && (!Double.isFinite(damageReductionFraction)
                    || damageReductionFraction <= 0.0D || damageReductionFraction >= 1.0D)) {
                errors.add("OwnerAttackAura.DamageReductionFraction must be in (0, 1)");
            }
            if (targetDamageTakenFraction != null && (!Double.isFinite(targetDamageTakenFraction)
                    || targetDamageTakenFraction < 0.0D || targetDamageTakenFraction >= 1.0D)) {
                errors.add("OwnerAttackAura.TargetDamageTakenFraction must be in [0, 1)");
            }
            return errors;
        }

        @Nullable public String getEffectId() { return blank(effectId) ? null : effectId.trim(); }
        public double getDurationSeconds() { return durationSeconds; }
        @Nullable public Double getDamageReductionFraction() { return damageReductionFraction; }
        @Nullable public Double getTargetDamageTakenFractionOverride() { return targetDamageTakenFraction; }
        public double getTargetDamageTakenFraction() {
            return targetDamageTakenFraction == null ? 0.0D : targetDamageTakenFraction;
        }
    }

    private List<String> validatePassiveModifiers(String archetypeId) {
        List<String> errors = new ArrayList<>();
        if ("lightning".equals(archetypeId)) {
            if (passiveModifiers.containsKey("MovementSpeedMultiplier")) {
                requireMultiplier(errors, "MovementSpeedMultiplier");
            } else if (!List.of(passiveEffects).contains("HyDragon_Miniwyvern_Lightning_Boon")) {
                errors.add("Lightning requires HyDragon_Miniwyvern_Lightning_Boon in PassiveEffects");
            }
        } else if ("nature".equals(archetypeId)) {
            if (!positive(passiveModifiers.get("RegenerationTickSeconds"))) {
                errors.add("Nature PassiveModifiers.RegenerationTickSeconds must be positive");
            }
            if (!fraction(passiveModifiers.get("MaximumHealFractionPerTick"))) {
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
        if ("lightning".equals(archetypeId)
                && passiveModifiers.containsKey("MovementSpeedMultiplier")
                && !passiveModifierEffects.containsKey("MovementSpeedMultiplier")) {
            errors.add("PassiveModifierEffects.MovementSpeedMultiplier is required for " + archetypeId);
        }
        return errors;
    }

    private void requireMultiplier(List<String> errors, String key) {
        Double value = passiveModifiers.get(key);
        if (value == null || !Double.isFinite(value) || value <= 0.0D) {
            errors.add("PassiveModifiers." + key + " must be positive");
        }
    }

    private static boolean positive(@Nullable Double value) {
        return value != null && Double.isFinite(value) && value > 0.0D;
    }

    private static boolean fraction(@Nullable Double value) {
        return value != null && Double.isFinite(value) && value > 0.0D && value <= 1.0D;
    }

    private static String normalize(@Nullable String value) { return trim(value).toLowerCase(Locale.ROOT); }
    private static String titleCase(String value) {
        return value.isEmpty() ? "" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
    private static boolean blank(@Nullable String value) { return value == null || value.isBlank(); }
    private static String trim(@Nullable String value) { return value == null ? "" : value.trim(); }
}
