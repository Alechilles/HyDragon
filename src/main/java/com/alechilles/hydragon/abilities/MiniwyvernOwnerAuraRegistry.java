package com.alechilles.hydragon.abilities;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ephemeral, live-projection owner-hit aura state. This is not form persistence or authority. */
public final class MiniwyvernOwnerAuraRegistry implements AutoCloseable {
    private static final Set<String> ELEMENTAL_FORMS = Set.of("fire", "ice", "void", "toxic");
    private final ConcurrentHashMap<UUID, Aura> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ToxicWeakness> toxicWeaknesses = new ConcurrentHashMap<>();

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
        if (ownerUuid == null || npcUuid == null || blank(profileId) || blank(leaseId)
                || !ELEMENTAL_FORMS.contains(normalize(formId)) || blank(effectId)
                || !Double.isFinite(durationSeconds) || durationSeconds <= 0.0D
                || (damageReductionFraction != null && (!Double.isFinite(damageReductionFraction)
                || damageReductionFraction <= 0.0D || damageReductionFraction >= 1.0D))
                || !validFraction(targetDamageTakenFraction)
                || !validFraction(ownerDamageToAffectedFraction)
                || !validFraction(conditionalWardDamageReductionFraction)
                || !validFraction(siphonMaximumHealthFraction)
                || siphonCooldownMs < 0L
                || (siphonMaximumHealthFraction > 0.0D && siphonCooldownMs <= 0L)) {
            return false;
        }
        active.put(ownerUuid, new Aura(ownerUuid, profileId.trim(), leaseId.trim(), npcUuid,
                normalize(formId), effectId.trim(), durationSeconds,
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
        long durationMs = Math.max(1L, Math.round(durationSeconds * 1_000.0D));
        toxicWeaknesses.put(targetUuid, new ToxicWeakness(effectId.trim(), fraction,
                nowMs > Long.MAX_VALUE - durationMs ? Long.MAX_VALUE : nowMs + durationMs));
    }

    public Optional<ToxicWeakness> activeToxicWeakness(UUID targetUuid, long nowMs) {
        ToxicWeakness weakness = toxicWeaknesses.get(targetUuid);
        if (weakness == null || weakness.expiresAtMs() <= nowMs) {
            if (weakness != null) toxicWeaknesses.remove(targetUuid, weakness);
            return Optional.empty();
        }
        return Optional.of(weakness);
    }

    public void clear() { active.clear(); toxicWeaknesses.clear(); }

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

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private static boolean validFraction(double value) {
        return Double.isFinite(value) && value >= 0.0D && value < 1.0D;
    }
}
