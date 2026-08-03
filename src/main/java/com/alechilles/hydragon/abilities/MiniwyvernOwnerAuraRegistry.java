package com.alechilles.hydragon.abilities;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ephemeral, live-projection owner-hit aura state. This is not form persistence or authority. */
public final class MiniwyvernOwnerAuraRegistry implements AutoCloseable {
    private static final Set<String> ELEMENTAL_FORMS =
            Set.of("fire", "ice", "void", "toxic", "lightning", "nature");
    private static final Set<String> PLAYER_ONLY_FORMS = Set.of("lightning", "nature");
    private final ConcurrentHashMap<UUID, Aura> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ToxicWeakness> toxicWeaknesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TargetAuraKey, TargetAura> targetAuras = new ConcurrentHashMap<>();

    public boolean update(UUID ownerUuid, String profileId, String leaseId, UUID npcUuid,
                          String formId, String effectId, double durationSeconds,
                          Double damageReductionFraction) {
        return update(ownerUuid, profileId, leaseId, npcUuid, formId, effectId, durationSeconds,
                damageReductionFraction, 0.0D, 0.0D, null, 0.0D, 0.0D, 0L);
    }

    public boolean update(UUID ownerUuid, String profileId, String leaseId, UUID npcUuid,
                          String formId, String effectId, double durationSeconds,
                          Double damageReductionFraction, double targetDamageTakenFraction,
                          double ownerDamageToAffectedFraction, String wardEffectId,
                          double conditionalWardDamageReductionFraction,
                          double siphonMaximumHealthFraction, long siphonCooldownMs) {
        String normalizedForm = normalize(formId);
        boolean playerOnly = PLAYER_ONLY_FORMS.contains(normalizedForm);
        boolean targetEffectMissing = blank(effectId);
        if (ownerUuid == null || npcUuid == null || blank(profileId) || blank(leaseId)
                || !ELEMENTAL_FORMS.contains(normalizedForm)
                || (wardEffectId != null && blank(wardEffectId))
                || !Double.isFinite(durationSeconds) || durationSeconds < 0.0D
                || (!playerOnly && (targetEffectMissing || durationSeconds <= 0.0D))
                || (playerOnly && !targetEffectMissing && durationSeconds <= 0.0D)
                || (damageReductionFraction != null && (!Double.isFinite(damageReductionFraction)
                || damageReductionFraction < 0.0D || damageReductionFraction >= 1.0D))
                || !validFraction(targetDamageTakenFraction)
                || !validFraction(ownerDamageToAffectedFraction)
                || !validFraction(conditionalWardDamageReductionFraction)
                || !validSiphon(normalizedForm, siphonMaximumHealthFraction, siphonCooldownMs)) {
            return false;
        }
        active.put(ownerUuid, new Aura(ownerUuid, profileId.trim(), leaseId.trim(), npcUuid,
                normalizedForm, targetEffectMissing ? "" : effectId.trim(), durationSeconds,
                damageReductionFraction == null ? 0.0D : damageReductionFraction,
                targetDamageTakenFraction, ownerDamageToAffectedFraction,
                blank(wardEffectId) ? null : wardEffectId.trim(),
                conditionalWardDamageReductionFraction, siphonMaximumHealthFraction,
                siphonCooldownMs));
        return true;
    }

    public Optional<Aura> activeFor(UUID ownerUuid) {
        return Optional.ofNullable(active.get(ownerUuid));
    }

    public boolean clear(UUID ownerUuid, String profileId, String leaseId) {
        if (ownerUuid == null || blank(profileId)) return false;
        Aura current = active.get(ownerUuid);
        if (current == null || !current.profileId().equals(profileId.trim())
                || (!blank(leaseId) && !current.leaseId().equals(leaseId.trim()))) return false;
        return active.remove(ownerUuid, current);
    }

    public boolean clear(UUID ownerUuid, String profileId) { return clear(ownerUuid, profileId, null); }

    public void recordToxicWeakness(UUID targetUuid, String effectId, double fraction, double durationSeconds,
                                    long nowMs) {
        if (targetUuid == null || blank(effectId) || !Double.isFinite(fraction) || fraction <= 0.0D
                || fraction >= 1.0D || !Double.isFinite(durationSeconds) || durationSeconds <= 0.0D) return;
        long durationMs = durationMillis(durationSeconds);
        toxicWeaknesses.put(targetUuid, new ToxicWeakness(effectId.trim(), fraction,
                expiryAt(nowMs, durationMs)));
    }

    public Optional<ToxicWeakness> activeToxicWeakness(UUID targetUuid, long nowMs) {
        ToxicWeakness weakness = toxicWeaknesses.get(targetUuid);
        if (weakness == null || weakness.expiresAtMs() <= nowMs) {
            if (weakness != null) toxicWeaknesses.remove(targetUuid, weakness);
            return Optional.empty();
        }
        return Optional.of(weakness);
    }

    /** Records the live target-side projection of a successfully applied owner aura effect. */
    public void recordTargetAura(UUID targetUuid, Aura aura, double durationSeconds, long nowMs) {
        if (targetUuid == null || aura == null || blank(aura.effectId())
                || !Double.isFinite(durationSeconds) || durationSeconds <= 0.0D
                || !validFraction(aura.targetOutgoingDamageReductionFraction())
                || !validFraction(aura.targetDamageTakenFraction())
                || !validFraction(aura.ownerDamageToAffectedFraction())) return;
        long expiresAtMs = expiryAt(nowMs, durationMillis(durationSeconds));
        TargetAura projection = new TargetAura(
                aura.formId(), aura.effectId().trim(), aura.targetOutgoingDamageReductionFraction(),
                aura.targetDamageTakenFraction(), aura.ownerDamageToAffectedFraction(), expiresAtMs);
        targetAuras.put(new TargetAuraKey(targetUuid, projection.effectId()), projection);
        if (projection.targetOutgoingDamageReductionFraction() > 0.0D) {
            recordToxicWeakness(targetUuid, projection.effectId(),
                    projection.targetOutgoingDamageReductionFraction(), durationSeconds, nowMs);
        } else if ("toxic".equals(projection.formId())) {
            ToxicWeakness current = toxicWeaknesses.get(targetUuid);
            if (current != null && current.effectId().equals(projection.effectId())) {
                toxicWeaknesses.remove(targetUuid, current);
            }
        }
    }

    public Optional<TargetAura> activeTargetAura(UUID targetUuid, long nowMs) {
        TargetAura selected = null;
        for (TargetAura projection : activeTargetAuras(targetUuid, nowMs)) {
            if (selected == null || projection.expiresAtMs() > selected.expiresAtMs()) {
                selected = projection;
            }
        }
        return Optional.ofNullable(selected);
    }

    public Optional<TargetAura> activeTargetAura(UUID targetUuid, String effectId, long nowMs) {
        if (targetUuid == null || blank(effectId)) return Optional.empty();
        TargetAuraKey key = new TargetAuraKey(targetUuid, effectId.trim());
        TargetAura projection = targetAuras.get(key);
        if (projection == null || projection.expiresAtMs() <= nowMs) {
            if (projection != null) targetAuras.remove(key, projection);
            return Optional.empty();
        }
        return Optional.of(projection);
    }

    public List<TargetAura> activeTargetAuras(UUID targetUuid, long nowMs) {
        if (targetUuid == null) return List.of();
        List<TargetAura> projections = new ArrayList<>();
        for (var entry : targetAuras.entrySet()) {
            TargetAuraKey key = entry.getKey();
            if (!targetUuid.equals(key.targetUuid())) continue;
            TargetAura projection = entry.getValue();
            if (projection.expiresAtMs() <= nowMs) {
                targetAuras.remove(key, projection);
            } else {
                projections.add(projection);
            }
        }
        return List.copyOf(projections);
    }

    public void clear() {
        active.clear();
        toxicWeaknesses.clear();
        targetAuras.clear();
    }

    @Override public void close() { clear(); }

    public record Aura(UUID ownerUuid, String profileId, String leaseId, UUID npcUuid,
                       String formId, String effectId, double durationSeconds,
                       double damageReductionFraction, double targetDamageTakenFraction,
                       double ownerDamageToAffectedFraction, String wardEffectId,
                       double conditionalWardDamageReductionFraction,
                       double siphonMaximumHealthFraction, long siphonCooldownMs) {
        public Aura { Objects.requireNonNull(ownerUuid); Objects.requireNonNull(npcUuid); }

        public Aura(UUID ownerUuid, String profileId, String leaseId, UUID npcUuid,
                    String formId, String effectId, double durationSeconds,
                    double damageReductionFraction) {
            this(ownerUuid, profileId, leaseId, npcUuid, formId, effectId, durationSeconds,
                    damageReductionFraction, 0.0D, 0.0D, null, 0.0D, 0.0D, 0L);
        }

        /** Explicit name for the target's outgoing-damage reduction value. */
        public double targetOutgoingDamageReductionFraction() { return damageReductionFraction; }
    }
    public record ToxicWeakness(String effectId, double damageReductionFraction, long expiresAtMs) { }
    public record TargetAura(String formId, String effectId,
                             double targetOutgoingDamageReductionFraction,
                             double targetDamageTakenFraction,
                             double ownerDamageToAffectedFraction,
                             long expiresAtMs) { }

    private record TargetAuraKey(UUID targetUuid, String effectId) { }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private static boolean validFraction(double value) {
        return Double.isFinite(value) && value >= 0.0D && value < 1.0D;
    }

    private static boolean validSiphon(String formId, double fraction, long cooldownMs) {
        if (!validFraction(fraction) || cooldownMs < 0L) return false;
        if (fraction <= 0.0D) return true;
        return "void".equals(formId) && fraction <= 0.01D && cooldownMs >= 3_000L;
    }

    private static long durationMillis(double durationSeconds) {
        double millis = durationSeconds * 1_000.0D;
        if (!Double.isFinite(millis) || millis >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(1L, Math.round(millis));
    }

    private static long expiryAt(long nowMs, long durationMs) {
        return nowMs > Long.MAX_VALUE - durationMs ? Long.MAX_VALUE : nowMs + durationMs;
    }
}
