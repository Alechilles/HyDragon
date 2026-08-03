package com.alechilles.hydragon.abilities;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Ephemeral, live-projection owner-hit aura state. This is not form persistence or authority. */
public final class MiniwyvernOwnerAuraRegistry implements AutoCloseable {
    private static final Set<String> ELEMENTAL_FORMS =
            Set.of("fire", "ice", "void", "toxic", "lightning", "nature");
    private static final Set<String> PLAYER_ONLY_FORMS = Set.of("lightning", "nature");
    private final ConcurrentHashMap<UUID, Aura> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ToxicWeakness> toxicWeaknesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TargetAuraKey, TargetAura> targetAuras = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> fireConditionalWardUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> pendingSpeedBursts = new ConcurrentHashMap<>();
    private final Set<Runnable> clearHooks = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<UUID>> ownerClearHooks = ConcurrentHashMap.newKeySet();

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
        return update(ownerUuid, profileId, leaseId, npcUuid, formId, effectId, durationSeconds,
                damageReductionFraction, targetDamageTakenFraction, ownerDamageToAffectedFraction,
                wardEffectId, conditionalWardDamageReductionFraction,
                siphonMaximumHealthFraction, siphonCooldownMs,
                null, 0.0D, 0.0D, 0.0D);
    }

    public boolean update(UUID ownerUuid, String profileId, String leaseId, UUID npcUuid,
                          String formId, String effectId, double durationSeconds,
                          Double damageReductionFraction, double targetDamageTakenFraction,
                          double ownerDamageToAffectedFraction, String wardEffectId,
                          double conditionalWardDamageReductionFraction,
                          double siphonMaximumHealthFraction, long siphonCooldownMs,
                          String ownerEffectId, double ownerRegenerationFraction,
                          double speedBurstMultiplier, double speedBurstDurationSeconds) {
        String normalizedForm = normalize(formId);
        boolean playerOnly = PLAYER_ONLY_FORMS.contains(normalizedForm);
        boolean targetEffectMissing = blank(effectId);
        boolean ownerEffectMissing = blank(ownerEffectId);
        boolean speedBurstMissing = speedBurstMultiplier == 0.0D && speedBurstDurationSeconds == 0.0D;
        boolean speedBurstInvalid = !Double.isFinite(speedBurstMultiplier)
                || !Double.isFinite(speedBurstDurationSeconds)
                || speedBurstMultiplier < 0.0D || speedBurstDurationSeconds < 0.0D;
        if (ownerUuid == null || npcUuid == null || blank(profileId) || blank(leaseId)
                || !ELEMENTAL_FORMS.contains(normalizedForm)
                || (wardEffectId != null && blank(wardEffectId))
                || (!ownerEffectMissing && !playerOnly)
                || !Double.isFinite(durationSeconds) || durationSeconds < 0.0D
                || (!playerOnly && (targetEffectMissing || durationSeconds <= 0.0D))
                || (playerOnly && !targetEffectMissing)
                || (damageReductionFraction != null && (!Double.isFinite(damageReductionFraction)
                || damageReductionFraction < 0.0D || damageReductionFraction >= 1.0D))
                || !validFraction(targetDamageTakenFraction)
                || !validFraction(ownerDamageToAffectedFraction)
                || !validFraction(conditionalWardDamageReductionFraction)
                || !validSiphon(normalizedForm, siphonMaximumHealthFraction, siphonCooldownMs)
                || !validFraction(ownerRegenerationFraction)
                || speedBurstInvalid
                || (!speedBurstMissing && (!Double.isFinite(speedBurstMultiplier)
                || speedBurstMultiplier <= 0.0D || !Double.isFinite(speedBurstDurationSeconds)
                || speedBurstDurationSeconds <= 0.0D))) {
            return false;
        }
        Aura prior = active.get(ownerUuid);
        if (prior != null && (!prior.profileId().equals(profileId.trim())
                || !prior.leaseId().equals(leaseId.trim())
                || !prior.formId().equals(normalizedForm))) {
            fireConditionalWardUntil.remove(ownerUuid);
            pendingSpeedBursts.remove(ownerUuid);
        }
        active.put(ownerUuid, new Aura(ownerUuid, profileId.trim(), leaseId.trim(), npcUuid,
                normalizedForm, targetEffectMissing ? "" : effectId.trim(), durationSeconds,
                damageReductionFraction == null ? 0.0D : damageReductionFraction,
                targetDamageTakenFraction, ownerDamageToAffectedFraction,
                blank(wardEffectId) ? null : wardEffectId.trim(),
                conditionalWardDamageReductionFraction, siphonMaximumHealthFraction,
                siphonCooldownMs, ownerEffectMissing ? null : ownerEffectId.trim(),
                ownerRegenerationFraction, speedBurstMissing ? 0.0D : speedBurstMultiplier,
                speedBurstMissing ? 0.0D : speedBurstDurationSeconds));
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
        boolean removed = active.remove(ownerUuid, current);
        if (removed) {
            fireConditionalWardUntil.remove(ownerUuid);
            pendingSpeedBursts.remove(ownerUuid);
            notifyOwnerClear(ownerUuid);
        }
        return removed;
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
                targetUuid, aura.ownerUuid(), aura.profileId(), aura.leaseId(), aura.formId(), aura.effectId().trim(),
                aura.targetOutgoingDamageReductionFraction(),
                aura.targetDamageTakenFraction(), aura.ownerDamageToAffectedFraction(), expiresAtMs);
        targetAuras.put(new TargetAuraKey(targetUuid, projection.effectId(), projection.ownerUuid(),
                projection.profileId(), projection.leaseId()), projection);
        if (isCurrentAura(aura) && "fire".equals(projection.formId())
                && aura.conditionalWardDamageReductionFraction() > 0.0D) {
            fireConditionalWardUntil.put(aura.ownerUuid(), expiryAt(nowMs, 3_000L));
        }
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
        TargetAura selected = null;
        for (TargetAura projection : activeTargetAuras(targetUuid, nowMs)) {
            if (!effectId.trim().equals(projection.effectId())) continue;
            if (selected == null || projection.expiresAtMs() > selected.expiresAtMs()) {
                selected = projection;
            }
        }
        return Optional.ofNullable(selected);
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

    /** Returns whether an owner has a live target projection for the requested form. */
    public boolean hasActiveTargetAuraForOwner(UUID ownerUuid, String formId, long nowMs) {
        return !activeTargetAurasForOwner(ownerUuid, formId, nowMs).isEmpty();
    }

    /** Returns only live projections owned by the current profile/lease. */
    public List<TargetAura> activeTargetAurasForOwner(UUID ownerUuid, String formId, long nowMs) {
        if (ownerUuid == null || blank(formId)) return List.of();
        Aura current = active.get(ownerUuid);
        if (current == null) return List.of();
        String normalizedForm = normalize(formId);
        List<TargetAura> projections = new ArrayList<>();
        for (var entry : targetAuras.entrySet()) {
            TargetAura projection = entry.getValue();
            if (projection.expiresAtMs() <= nowMs) {
                targetAuras.remove(entry.getKey(), projection);
                continue;
            }
            if (ownerUuid.equals(projection.ownerUuid())
                    && current.profileId().equals(projection.profileId())
                    && current.leaseId().equals(projection.leaseId())
                    && normalizedForm.equals(projection.formId())) {
                projections.add(projection);
            }
        }
        return List.copyOf(projections);
    }

    /** True when a queued target aura still belongs to the live owner profile/lease/form. */
    public boolean isCurrentAura(Aura aura) {
        if (aura == null) return false;
        Aura current = active.get(aura.ownerUuid());
        return current != null
                && current.profileId().equals(aura.profileId())
                && current.leaseId().equals(aura.leaseId())
                && current.formId().equals(aura.formId());
    }

    /** Records one valid Lightning damage event for the next ability-service owner-speed refresh. */
    public void recordSpeedBurst(UUID ownerUuid) {
        if (ownerUuid == null) return;
        Aura aura = active.get(ownerUuid);
        if (aura == null || aura.speedBurstMultiplier() <= 0.0D
                || aura.speedBurstDurationSeconds() <= 0.0D) return;
        pendingSpeedBursts.put(ownerUuid, Boolean.TRUE);
    }

    public boolean consumeSpeedBurst(UUID ownerUuid) {
        return ownerUuid != null && pendingSpeedBursts.remove(ownerUuid) != null;
    }

    /** Arms Fire's three-second conditional general damage reduction after a Burn application. */
    public void armFireConditionalWard(UUID ownerUuid, long nowMs) {
        Aura aura = ownerUuid == null ? null : active.get(ownerUuid);
        if (aura == null || !"fire".equals(aura.formId())
                || aura.conditionalWardDamageReductionFraction() <= 0.0D) return;
        fireConditionalWardUntil.put(ownerUuid, expiryAt(nowMs, 3_000L));
    }

    public boolean conditionalWardActive(UUID ownerUuid, boolean ownerBelowHalf, long nowMs) {
        return conditionalWardActive(ownerUuid, ownerBelowHalf, true, nowMs);
    }

    /**
     * Resolves conditional owner protection. Toxic additionally requires the ECS caller to have
     * observed the target's live Weakness EntityEffect in this damage cycle.
     */
    public boolean conditionalWardActive(
            UUID ownerUuid, boolean ownerBelowHalf, boolean toxicTargetStatusActive, long nowMs) {
        Aura aura = ownerUuid == null ? null : active.get(ownerUuid);
        if (aura == null || aura.conditionalWardDamageReductionFraction() <= 0.0D) return false;
        return switch (aura.formId()) {
            case "fire" -> fireConditionalWardUntil.getOrDefault(ownerUuid, 0L) > nowMs;
            case "nature" -> ownerBelowHalf;
            case "toxic" -> toxicTargetStatusActive
                    && hasActiveTargetAuraForOwner(ownerUuid, "toxic", nowMs);
            default -> false;
        };
    }

    /** Registers an ephemeral-state cleanup hook, returning a handle that unregisters it. */
    AutoCloseable addClearHook(Runnable hook) {
        Objects.requireNonNull(hook, "hook");
        clearHooks.add(hook);
        return () -> clearHooks.remove(hook);
    }

    /** Registers a hook for a matching per-owner lease clear. */
    AutoCloseable addOwnerClearHook(Consumer<UUID> hook) {
        Objects.requireNonNull(hook, "hook");
        ownerClearHooks.add(hook);
        return () -> ownerClearHooks.remove(hook);
    }

    public void clear() {
        Set<UUID> owners = Set.copyOf(active.keySet());
        active.clear();
        toxicWeaknesses.clear();
        targetAuras.clear();
        fireConditionalWardUntil.clear();
        pendingSpeedBursts.clear();
        for (UUID ownerUuid : owners) notifyOwnerClear(ownerUuid);
        for (Runnable hook : clearHooks) {
            try {
                hook.run();
            } catch (RuntimeException ignored) {
                // Cleanup hooks are best-effort and must not prevent registry convergence.
            }
        }
    }

    private void notifyOwnerClear(UUID ownerUuid) {
        for (Consumer<UUID> hook : ownerClearHooks) {
            try {
                hook.accept(ownerUuid);
            } catch (RuntimeException ignored) {
                // Cleanup hooks are best-effort and must not prevent registry convergence.
            }
        }
    }

    @Override public void close() { clear(); }

    public record Aura(UUID ownerUuid, String profileId, String leaseId, UUID npcUuid,
                       String formId, String effectId, double durationSeconds,
                       double damageReductionFraction, double targetDamageTakenFraction,
                       double ownerDamageToAffectedFraction, String wardEffectId,
                       double conditionalWardDamageReductionFraction,
                       double siphonMaximumHealthFraction, long siphonCooldownMs,
                       String ownerEffectId, double ownerRegenerationFraction,
                       double speedBurstMultiplier, double speedBurstDurationSeconds) {
        public Aura { Objects.requireNonNull(ownerUuid); Objects.requireNonNull(npcUuid); }

        public Aura(UUID ownerUuid, String profileId, String leaseId, UUID npcUuid,
                    String formId, String effectId, double durationSeconds,
                    double damageReductionFraction) {
            this(ownerUuid, profileId, leaseId, npcUuid, formId, effectId, durationSeconds,
                    damageReductionFraction, 0.0D, 0.0D, null, 0.0D, 0.0D, 0L,
                    null, 0.0D, 0.0D, 0.0D);
        }

        public Aura(UUID ownerUuid, String profileId, String leaseId, UUID npcUuid,
                    String formId, String effectId, double durationSeconds,
                    double damageReductionFraction, double targetDamageTakenFraction,
                    double ownerDamageToAffectedFraction, String wardEffectId,
                    double conditionalWardDamageReductionFraction,
                    double siphonMaximumHealthFraction, long siphonCooldownMs) {
            this(ownerUuid, profileId, leaseId, npcUuid, formId, effectId, durationSeconds,
                    damageReductionFraction, targetDamageTakenFraction, ownerDamageToAffectedFraction,
                    wardEffectId, conditionalWardDamageReductionFraction,
                    siphonMaximumHealthFraction, siphonCooldownMs,
                    null, 0.0D, 0.0D, 0.0D);
        }

        /** Explicit name for the target's outgoing-damage reduction value. */
        public double targetOutgoingDamageReductionFraction() { return damageReductionFraction; }
    }
    public record ToxicWeakness(String effectId, double damageReductionFraction, long expiresAtMs) { }
    public record TargetAura(UUID targetUuid, UUID ownerUuid, String profileId, String leaseId,
                             String formId, String effectId,
                             double targetOutgoingDamageReductionFraction,
                             double targetDamageTakenFraction,
                             double ownerDamageToAffectedFraction,
                             long expiresAtMs) {
        public TargetAura(String formId, String effectId,
                          double targetOutgoingDamageReductionFraction,
                          double targetDamageTakenFraction,
                          double ownerDamageToAffectedFraction,
                          long expiresAtMs) {
            this(null, null, null, null, formId, effectId, targetOutgoingDamageReductionFraction,
                    targetDamageTakenFraction, ownerDamageToAffectedFraction, expiresAtMs);
        }

        public TargetAura(UUID ownerUuid, String formId, String effectId,
                          double targetOutgoingDamageReductionFraction,
                          double targetDamageTakenFraction,
                          double ownerDamageToAffectedFraction,
                          long expiresAtMs) {
            this(null, ownerUuid, null, null, formId, effectId, targetOutgoingDamageReductionFraction,
                    targetDamageTakenFraction, ownerDamageToAffectedFraction, expiresAtMs);
        }

        /** Compatibility constructor retaining the pre-projection owner/lease argument order. */
        public TargetAura(UUID ownerUuid, String leaseId, String formId, String effectId,
                          double targetOutgoingDamageReductionFraction,
                          double targetDamageTakenFraction,
                          double ownerDamageToAffectedFraction,
                          long expiresAtMs) {
            this(null, ownerUuid, null, leaseId, formId, effectId,
                    targetOutgoingDamageReductionFraction, targetDamageTakenFraction,
                    ownerDamageToAffectedFraction, expiresAtMs);
        }

    }

    private record TargetAuraKey(UUID targetUuid, String effectId, UUID ownerUuid,
                                 String profileId, String leaseId) { }

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
