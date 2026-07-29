package com.alechilles.hydragon.abilities;

import com.alechilles.hydragon.config.MiniwyvernArchetypeConfig;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Deterministic, source-keyed Miniwyvern ability scheduler.
 *
 * <p>The caller must invoke this service on the owning Hytale world thread. Durable cooldown state
 * is committed before a gameplay mutation, so a crash can suppress a cast but cannot duplicate one.</p>
 */
public final class MiniwyvernAbilityService {
    private static final String SOURCE_PREFIX = "hydragon:mini:";
    private static final long ICE_TARGET_RETENTION_MS = 60_000L;
    private final MiniwyvernAbilityStateRepository states;
    private final MiniwyvernOwnerAuraRegistry ownerAuras;

    public MiniwyvernAbilityService(MiniwyvernAbilityStateRepository states) {
        this(states, new MiniwyvernOwnerAuraRegistry());
    }

    public MiniwyvernAbilityService(
            MiniwyvernAbilityStateRepository states, MiniwyvernOwnerAuraRegistry ownerAuras) {
        this.states = Objects.requireNonNull(states, "states");
        this.ownerAuras = Objects.requireNonNull(ownerAuras, "ownerAuras");
    }

    public TickResult tick(
            ProfileContext context,
            Map<String, MiniwyvernArchetypeConfig> archetypes,
            MiniwyvernAbilityWorld world,
            long nowMs) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(archetypes, "archetypes");
        Objects.requireNonNull(world, "world");
        if (nowMs < 0L) throw new IllegalArgumentException("nowMs must not be negative");
        if (!world.isWorldThread()) return TickResult.denied("not-world-thread");
        if (!context.featureAvailable()) return cleanupAndDeny(context, archetypes, world, "feature-gated", nowMs);
        if (!context.active() || !context.alive() || !context.owned()) {
            return cleanupAndDeny(context, archetypes, world, "inactive-or-unowned", nowMs);
        }
        Optional<MiniwyvernAbilityWorld.Target> owner = world.owner();
        Optional<MiniwyvernAbilityWorld.Target> companion = world.companion();
        if (owner.isEmpty() || companion.isEmpty()
                || !context.ownerUuid().equals(owner.orElseThrow().entityUuid())
                || !context.npcUuid().equals(companion.orElseThrow().entityUuid())
                || !world.worldName().equals(owner.orElseThrow().worldName())
                || !world.worldName().equals(companion.orElseThrow().worldName())) {
            return cleanupAndDeny(context, archetypes, world, "projection-unresolved", nowMs);
        }

        String roleId = world.companionRoleId().orElse(null);
        if (roleId == null) {
            return cleanupAndDeny(context, archetypes, world, "companion-role-unresolved", nowMs);
        }
        MiniwyvernArchetypeConfig config = configForRole(archetypes, roleId);
        if (config == null || !config.validate().isEmpty()) {
            return cleanupAndDeny(context, archetypes, world, "role-config-invalid", nowMs);
        }
        String formId = config.getId();
        synchronizeOwnerAttackAura(context, config);

        MiniwyvernAbilityStateRepository.LoadResult loaded = states.load(
                context.ownerUuid(), context.profileId());
        if (loaded.status() == MiniwyvernAbilityStateRepository.Status.UNAVAILABLE) {
            return TickResult.denied("ability-state-unavailable");
        }
        MiniwyvernAbilityState state = loaded.status() == MiniwyvernAbilityStateRepository.Status.LOADED
                ? loaded.state() : MiniwyvernAbilityState.empty(formId, nowMs);
        if (!state.formId().equals(formId)) {
            cleanupSources(context, state, archetypes, world);
            state = MiniwyvernAbilityState.empty(formId, nowMs);
        }
        MutableState mutable = new MutableState(state);
        mutable.prune(nowMs);
        PassiveExecution passive = preparePassives(context, config, world, mutable, nowMs);
        Set<String> diagnostics = new LinkedHashSet<>(passive.diagnostics());
        // Establish source ownership and every non-idempotent cooldown before mutating the world.
        if (!states.save(context.ownerUuid(), context.profileId(),
                mutable.freeze(formId))) {
            return TickResult.denied("ability-state-unavailable");
        }

        int effectsApplied = executePassives(context, config, passive, world);
        // Combat execution belongs to the role/root-interaction assets. This retained service only
        // refreshes Java-owned owner passives and their lifecycle cleanup.
        int abilitiesExecuted = 0;

        mutable.prune(nowMs);
        MiniwyvernAbilityState finalState = mutable.freeze(formId);
        if (!finalState.equals(state) && !states.save(
                context.ownerUuid(), context.profileId(), finalState)) {
            return new TickResult(false, "ability-state-finalize-failed", effectsApplied, abilitiesExecuted);
        }
        String reason = diagnostics.isEmpty()
                ? "ready"
                : "ready-with-degraded-semantics:" + String.join(",", diagnostics);
        return new TickResult(true, reason, effectsApplied, abilitiesExecuted);
    }

    /** Removes all tracked effects when lifecycle state changes, even if the ability gate is unavailable. */
    public TickResult deactivate(
            ProfileContext context,
            Map<String, MiniwyvernArchetypeConfig> archetypes,
            MiniwyvernAbilityWorld world,
            long nowMs) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(archetypes, "archetypes");
        Objects.requireNonNull(world, "world");
        if (!world.isWorldThread()) return TickResult.denied("not-world-thread");
        ownerAuras.clear(context.ownerUuid(), context.profileId(), context.npcUuid().toString());
        MiniwyvernAbilityStateRepository.LoadResult loaded = states.load(
                context.ownerUuid(), context.profileId());
        if (loaded.status() == MiniwyvernAbilityStateRepository.Status.UNAVAILABLE) {
            return TickResult.denied("ability-state-cleanup-pending");
        }
        MiniwyvernAbilityState state = loaded.status() == MiniwyvernAbilityStateRepository.Status.LOADED
                ? loaded.state()
                : MiniwyvernAbilityState.empty("wild", nowMs);
        cleanupSources(context, state, archetypes, world);
        MiniwyvernAbilityState cleared = MiniwyvernAbilityState.empty(state.formId(), nowMs);
        return states.save(context.ownerUuid(), context.profileId(), cleared)
                ? TickResult.denied("inactive")
                : TickResult.denied("ability-state-cleanup-pending");
    }

    private TickResult cleanupAndDeny(
            ProfileContext context,
            Map<String, MiniwyvernArchetypeConfig> archetypes,
            MiniwyvernAbilityWorld world,
            String reason,
            long nowMs) {
        deactivate(context, archetypes, world, nowMs);
        return TickResult.denied(reason);
    }

    private PassiveExecution preparePassives(
            ProfileContext context,
            MiniwyvernArchetypeConfig config,
            MiniwyvernAbilityWorld world,
            MutableState state,
            long nowMs) {
        String passiveSource = sourceKey(context.profileId(), config.getId(), "passive");
        Map<String, String> supportedEffectModifiers = new LinkedHashMap<>();
        Map<String, Double> supportedRawModifiers = new LinkedHashMap<>();
        Map<String, Double> rawModifierCandidates = new LinkedHashMap<>();
        Set<String> unsupportedModifiers = new TreeSet<>();
        for (Map.Entry<String, Double> modifier : config.getPassiveModifiers().entrySet()) {
            String modifierId = modifier.getKey();
            if (isModifierConstraint(modifierId) || isNatureSchedulerSetting(modifierId)) continue;
            String effectId = config.getPassiveModifierEffects().get(modifierId);
            if (effectId != null) {
                double maximum = configuredMaximum(config.getPassiveModifiers(), modifierId, modifier.getValue());
                if (world.supportsPassiveModifierEffect(
                        modifierId, modifier.getValue(), maximum, effectId)) {
                    supportedEffectModifiers.put(modifierId, effectId);
                } else {
                    unsupportedModifiers.add(modifierId);
                }
            } else {
                rawModifierCandidates.put(modifierId, modifier.getValue());
            }
        }
        if (!rawModifierCandidates.isEmpty()) {
            if (world.supportsOwnerModifiers(rawModifierCandidates)) {
                supportedRawModifiers.putAll(rawModifierCandidates);
            } else {
                unsupportedModifiers.addAll(rawModifierCandidates.keySet());
            }
        }
        boolean passiveDisabled = !unsupportedModifiers.isEmpty();
        List<String> diagnostics = passiveDisabled
                ? List.of("passive-ability-disabled:" + String.join("+", unsupportedModifiers))
                : List.of();
        boolean hasPassive = !passiveDisabled && (!config.getPassiveEffects().isEmpty()
                || !supportedEffectModifiers.isEmpty() || !supportedRawModifiers.isEmpty());
        boolean cleanupDisabledPassive = false;
        if (passiveDisabled) {
            cleanupDisabledPassive = state.sources.remove(passiveSource);
            cleanupDisabledPassive |= state.targetsBySource.remove(passiveSource) != null;
            state.sourceExpiresAt.remove(passiveSource);
            if (cleanupDisabledPassive) state.updatedAt = nowMs;
            supportedEffectModifiers.clear();
            supportedRawModifiers.clear();
        }
        if (hasPassive && !state.trackSource(
                passiveSource,
                context.ownerUuid(),
                saturatingAdd(nowMs, secondsToMs(passiveRefreshSeconds(config))))) {
            hasPassive = false;
            diagnostics = List.of("passive-ability-disabled:source-tracking-capacity");
            supportedEffectModifiers.clear();
            supportedRawModifiers.clear();
        }

        double natureHeal = 0.0D;
        if (!passiveDisabled && config.getId().equals("nature")) {
            String abilityId = "nature_regeneration";
            long next = state.cooldowns.getOrDefault(abilityId, 0L);
            if (nowMs >= next) {
                double tickSeconds = config.getPassiveModifiers().getOrDefault("RegenerationTickSeconds", 0.0D);
                double maximumFraction = config.getPassiveModifiers().getOrDefault("MaximumHealFractionPerTick", 0.0D);
                MiniwyvernAbilityWorld.Health health = world.health(context.ownerUuid());
                natureHeal = Math.min(health.maximum() * maximumFraction, health.maximum() - health.current());
                state.cooldowns.put(abilityId, saturatingAdd(nowMs, secondsToMs(tickSeconds)));
                state.updatedAt = nowMs;
            }
        }
        return new PassiveExecution(
                passiveSource,
                hasPassive,
                cleanupDisabledPassive,
                natureHeal,
                Map.copyOf(supportedEffectModifiers),
                Map.copyOf(supportedRawModifiers),
                List.copyOf(diagnostics));
    }

    private int executePassives(
            ProfileContext context,
            MiniwyvernArchetypeConfig config,
            PassiveExecution passive,
            MiniwyvernAbilityWorld world) {
        int applied = 0;
        boolean gameplayApplied = false;
        if (passive.cleanupDisabledPassive()) {
            for (String effectId : config.getPassiveEffects()) {
                world.removeEffect(context.ownerUuid(), passive.sourceKey(), effectId);
            }
            for (String effectId : new LinkedHashSet<>(config.getPassiveModifierEffects().values())) {
                world.removeEffect(context.ownerUuid(), passive.sourceKey(), effectId);
            }
            world.removeOwnerModifiers(context.ownerUuid(), passive.sourceKey());
        }
        if (passive.hasPassive()) {
            double refreshSeconds = passiveRefreshSeconds(config);
            for (String effectId : config.getPassiveEffects()) {
                if (world.applyEffect(context.ownerUuid(), passive.sourceKey(), effectId, refreshSeconds)) applied++;
            }
            for (String effectId : new LinkedHashSet<>(passive.effectModifiers().values())) {
                if (world.applyEffect(context.ownerUuid(), passive.sourceKey(), effectId, refreshSeconds)) applied++;
            }
            if (!passive.rawModifiers().isEmpty()
                    && world.applyOwnerModifiers(context.ownerUuid(), passive.sourceKey(),
                    passive.rawModifiers(), refreshSeconds)) {
                applied++;
            }
        }
        if (passive.natureHeal() > 0.0D && world.heal(context.ownerUuid(), passive.natureHeal())) {
            applied++;
            gameplayApplied = true;
        }
        if (gameplayApplied) world.emitAttachedPresentation(context.ownerUuid(), config.getParticleAndSoundIds());
        return applied;
    }

    private void cleanupSources(
            ProfileContext context,
            MiniwyvernAbilityState state,
            Map<String, MiniwyvernArchetypeConfig> archetypes,
        MiniwyvernAbilityWorld world) {
        for (String source : state.appliedSourceKeys()) {
            world.removeOwnerModifiers(context.ownerUuid(), source);
            for (MiniwyvernArchetypeConfig config : archetypes.values()) {
                for (String effectId : config.getPassiveEffects()) {
                    world.removeEffect(context.ownerUuid(), source, effectId);
                }
                for (String effectId : new LinkedHashSet<>(config.getPassiveModifierEffects().values())) {
                    world.removeEffect(context.ownerUuid(), source, effectId);
                }
            }
        }
    }

    private static double passiveRefreshSeconds(MiniwyvernArchetypeConfig config) {
        return config.getId().equals("nature") ? 10.0D : 8.0D;
    }

    private static boolean isModifierConstraint(String modifierId) {
        return modifierId.startsWith("Maximum") && modifierId.endsWith("Multiplier");
    }

    private static boolean isNatureSchedulerSetting(String modifierId) {
        return modifierId.equals("RegenerationTickSeconds")
                || modifierId.equals("MaximumHealFractionPerTick");
    }

    private static double configuredMaximum(
            Map<String, Double> modifiers,
            String modifierId,
            double requestedValue) {
        String maximumId = switch (modifierId) {
            case "MovementSpeedMultiplier" -> "MaximumMovementSpeedMultiplier";
            case "JumpMultiplier" -> "MaximumJumpMultiplier";
            default -> null;
        };
        return maximumId == null ? requestedValue : modifiers.getOrDefault(maximumId, requestedValue);
    }

    private static MiniwyvernArchetypeConfig configForRole(
            Map<String, MiniwyvernArchetypeConfig> archetypes,
            String roleId) {
        return archetypes.values().stream()
                .filter(config -> roleId.equals(config.getRoleId()))
                .findFirst()
                .orElse(null);
    }

    /** Clears ephemeral owner-hit state even when the world projection is already gone. */
    public void clearOwnerAuras() { ownerAuras.clear(); }

    private void synchronizeOwnerAttackAura(ProfileContext context, MiniwyvernArchetypeConfig config) {
        MiniwyvernArchetypeConfig.OwnerAttackAura aura = config.getOwnerAttackAura();
        if (aura == null || aura.getEffectId() == null || !ownerAuras.update(
                context.ownerUuid(), context.profileId(), context.npcUuid().toString(), context.npcUuid(),
                config.getId(), aura.getEffectId(), aura.getDurationSeconds(),
                aura.getDamageReductionFraction())) {
            ownerAuras.clear(context.ownerUuid(), context.profileId(), context.npcUuid().toString());
        }
    }

    private static String sourceKey(String profileId, String formId, String abilityId) {
        return SOURCE_PREFIX + requiredText(profileId, "profileId") + ":"
                + normalize(formId) + ":" + requiredText(abilityId, "abilityId");
    }

    private static long secondsToMs(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0D) return Long.MAX_VALUE;
        double millis = seconds * 1000.0D;
        return millis >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(millis);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
        return left + right;
    }

    private static String normalize(String value) {
        return requiredText(value, "value").toLowerCase(Locale.ROOT);
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    public record ProfileContext(
            String profileId,
            UUID ownerUuid,
            UUID npcUuid,
            boolean owned,
            boolean active,
            boolean alive,
            boolean featureAvailable) {
        public ProfileContext {
            profileId = requiredText(profileId, "profileId");
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            Objects.requireNonNull(npcUuid, "npcUuid");
        }
    }

    public record TickResult(boolean ready, String reason, int effectsApplied, int abilitiesExecuted) {
        public TickResult {
            reason = requiredText(reason, "reason");
            if (effectsApplied < 0 || abilitiesExecuted < 0) throw new IllegalArgumentException("negative count");
        }

        static TickResult denied(String reason) {
            return new TickResult(false, reason, 0, 0);
        }
    }

    private record PassiveExecution(
            String sourceKey,
            boolean hasPassive,
            boolean cleanupDisabledPassive,
            double natureHeal,
            Map<String, String> effectModifiers,
            Map<String, Double> rawModifiers,
            List<String> diagnostics) {
    }

    private static final class MutableState {
        final Map<String, Long> cooldowns;
        final Map<UUID, Double> iceBuildup;
        final Map<UUID, Long> immunityUntil;
        final Map<UUID, Long> iceTargetUpdatedAt;
        final Set<String> sources;
        final Map<String, UUID> targetsBySource;
        final Map<String, Long> sourceExpiresAt;
        long updatedAt;

        MutableState(MiniwyvernAbilityState state) {
            cooldowns = new LinkedHashMap<>(state.cooldownUntilByAbility());
            iceBuildup = new LinkedHashMap<>(state.iceBuildupByTarget());
            immunityUntil = new LinkedHashMap<>(state.controlImmunityUntilByTarget());
            iceTargetUpdatedAt = new LinkedHashMap<>(state.iceTargetUpdatedAtByTarget());
            sources = new LinkedHashSet<>(state.appliedSourceKeys());
            targetsBySource = new LinkedHashMap<>(state.targetBySourceKey());
            sourceExpiresAt = new LinkedHashMap<>(state.sourceExpiresAtBySourceKey());
            updatedAt = state.updatedAtEpochMillis();
        }

        void prune(long nowMs) {
            cooldowns.entrySet().removeIf(entry -> entry.getValue() < nowMs - 86_400_000L);
            immunityUntil.entrySet().removeIf(entry -> entry.getValue() <= nowMs);
            iceBuildup.entrySet().removeIf(entry -> entry.getValue() <= 0.0D);
            iceTargetUpdatedAt.entrySet().removeIf(entry -> {
                UUID target = entry.getKey();
                boolean untracked = !iceBuildup.containsKey(target) && !immunityUntil.containsKey(target);
                boolean stale = entry.getValue() < nowMs - ICE_TARGET_RETENTION_MS
                        && !immunityUntil.containsKey(target);
                if (stale) iceBuildup.remove(target);
                return untracked || stale;
            });
            sourceExpiresAt.entrySet().removeIf(entry -> {
                if (entry.getValue() > nowMs) return false;
                sources.remove(entry.getKey());
                targetsBySource.remove(entry.getKey());
                return true;
            });
            sources.retainAll(sourceExpiresAt.keySet());
            targetsBySource.keySet().retainAll(sources);
        }

        Set<UUID> trackedIceTargets() {
            Set<UUID> tracked = new LinkedHashSet<>(iceBuildup.keySet());
            tracked.addAll(immunityUntil.keySet());
            return tracked;
        }

        boolean trackSource(String source, UUID target, long expiresAtMs) {
            if (!sources.contains(source)
                    && sources.size() >= MiniwyvernAbilityState.MAX_TRACKED_SOURCE_KEYS) {
                return false;
            }
            sources.add(source);
            targetsBySource.put(source, target);
            sourceExpiresAt.put(source, expiresAtMs);
            return true;
        }

        MiniwyvernAbilityState freeze(String formId) {
            return new MiniwyvernAbilityState(
                    MiniwyvernAbilityState.SCHEMA_VERSION,
                    formId,
                    cooldowns,
                    iceBuildup,
                    immunityUntil,
                    iceTargetUpdatedAt,
                    sources,
                    targetsBySource,
                    sourceExpiresAt,
                    updatedAt);
        }
    }
}
