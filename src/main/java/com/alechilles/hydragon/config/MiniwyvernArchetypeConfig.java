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
    public String getFallbackBehavior() { return trim(fallbackBehavior); }

    /** Data-only owner-hit effect metadata; its runtime is deliberately separate. */
    public static final class OwnerAttackAura {
        String effectId;
        double durationSeconds;
        Double damageReductionFraction;

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
            return errors;
        }

        @Nullable public String getEffectId() { return blank(effectId) ? null : effectId.trim(); }
        public double getDurationSeconds() { return durationSeconds; }
        @Nullable public Double getDamageReductionFraction() { return damageReductionFraction; }
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
