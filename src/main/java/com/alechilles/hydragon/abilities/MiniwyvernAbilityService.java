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
import java.util.UUID;

/**
 * Deterministic, source-keyed Miniwyvern ability scheduler.
 *
 * <p>The caller must invoke this service on the owning Hytale world thread. Durable cooldown state
 * is committed before a gameplay mutation, so a crash can suppress a cast but cannot duplicate one.</p>
 */
public final class MiniwyvernAbilityService {
    private static final String SOURCE_PREFIX = "hydragon:mini:";
    private final MiniwyvernAbilityStateRepository states;

    public MiniwyvernAbilityService(MiniwyvernAbilityStateRepository states) {
        this.states = Objects.requireNonNull(states, "states");
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

        String archetypeId = normalize(context.archetypeId());
        MiniwyvernArchetypeConfig config = archetypes.get(archetypeId);
        if (config == null || !config.validate().isEmpty()) {
            return cleanupAndDeny(context, archetypes, world, "archetype-config-invalid", nowMs);
        }
        if (!world.synchronizeAppearance(context.npcUuid(), config.getAppearanceId())) {
            return cleanupAndDeny(context, archetypes, world, "appearance-sync-unavailable", nowMs);
        }
        boolean modifiersUnavailable = !config.getId().equals("nature")
                && !config.getPassiveModifiers().isEmpty()
                && !world.supportsOwnerModifiers(config.getPassiveModifiers());

        MiniwyvernAbilityStateRepository.LoadResult loaded = states.load(context.profileId());
        if (loaded.status() == MiniwyvernAbilityStateRepository.Status.UNAVAILABLE) {
            return TickResult.denied("ability-state-unavailable");
        }
        MiniwyvernAbilityState state = loaded.status() == MiniwyvernAbilityStateRepository.Status.LOADED
                ? loaded.state() : MiniwyvernAbilityState.empty(archetypeId, nowMs);
        if (!state.archetypeId().equals(archetypeId)) {
            cleanupSources(context, state, archetypes, world);
            state = MiniwyvernAbilityState.empty(archetypeId, nowMs);
        }
        MutableState mutable = new MutableState(state);
        PassiveExecution passive = preparePassives(context, config, world, mutable, nowMs, modifiersUnavailable);
        // Establish source ownership and every non-idempotent cooldown before mutating the world.
        if (!states.save(context.profileId(), mutable.freeze(archetypeId))) {
            return TickResult.denied("ability-state-unavailable");
        }

        int effectsApplied = executePassives(context, config, passive, world, modifiersUnavailable);
        int abilitiesExecuted = 0;
        for (MiniwyvernArchetypeConfig.Ability ability : config.getActiveAbilities()) {
            if (nowMs < mutable.cooldowns.getOrDefault(ability.getId(), 0L)) continue;
            AbilityExecution execution = prepareExecution(context, config, ability, world, mutable, nowMs);
            if (!execution.ready()) continue;

            long cooldownUntil = saturatingAdd(nowMs, secondsToMs(ability.getCooldownSeconds()));
            mutable.cooldowns.put(ability.getId(), cooldownUntil);
            String source = sourceKey(context.profileId(), config.getId(), ability.getId());
            mutable.sources.add(source);
            mutable.targetsBySource.put(source, execution.targetUuid());
            mutable.updatedAt = nowMs;
            MiniwyvernAbilityState beforeMutation = mutable.freeze(archetypeId);
            if (!states.save(context.profileId(), beforeMutation)) {
                return new TickResult(false, "ability-state-commit-failed", effectsApplied, abilitiesExecuted);
            }
            if (execute(context, config, ability, execution, world, mutable, nowMs)) {
                abilitiesExecuted++;
            }
        }

        mutable.prune(nowMs);
        MiniwyvernAbilityState finalState = mutable.freeze(archetypeId);
        if (!finalState.equals(state) && !states.save(context.profileId(), finalState)) {
            return new TickResult(false, "ability-state-finalize-failed", effectsApplied, abilitiesExecuted);
        }
        return new TickResult(true,
                modifiersUnavailable ? "owner-modifier-capability-unavailable" : "ready",
                effectsApplied,
                abilitiesExecuted);
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
        MiniwyvernAbilityStateRepository.LoadResult loaded = states.load(context.profileId());
        if (loaded.status() == MiniwyvernAbilityStateRepository.Status.UNAVAILABLE) {
            return TickResult.denied("ability-state-cleanup-pending");
        }
        MiniwyvernAbilityState state = loaded.status() == MiniwyvernAbilityStateRepository.Status.LOADED
                ? loaded.state()
                : MiniwyvernAbilityState.empty(normalize(context.archetypeId()), nowMs);
        cleanupSources(context, state, archetypes, world);
        MiniwyvernAbilityState cleared = MiniwyvernAbilityState.empty(normalize(context.archetypeId()), nowMs);
        return states.save(context.profileId(), cleared)
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
            long nowMs,
            boolean modifiersUnavailable) {
        String passiveSource = sourceKey(context.profileId(), config.getId(), "passive");
        boolean hasPassive = !config.getPassiveEffects().isEmpty() || !config.getPassiveModifiers().isEmpty();
        if (hasPassive) {
            state.sources.add(passiveSource);
            state.targetsBySource.put(passiveSource, context.ownerUuid());
        }

        double natureHeal = 0.0D;
        if (config.getId().equals("nature")) {
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
        return new PassiveExecution(passiveSource, hasPassive, natureHeal);
    }

    private int executePassives(
            ProfileContext context,
            MiniwyvernArchetypeConfig config,
            PassiveExecution passive,
            MiniwyvernAbilityWorld world,
            boolean modifiersUnavailable) {
        int applied = 0;
        if (passive.hasPassive()) {
            double refreshSeconds = passiveRefreshSeconds(config);
            for (String effectId : config.getPassiveEffects()) {
                if (world.applyEffect(context.ownerUuid(), passive.sourceKey(), effectId, refreshSeconds)) applied++;
            }
            if (!modifiersUnavailable && !config.getPassiveModifiers().isEmpty()
                    && world.applyOwnerModifiers(context.ownerUuid(), passive.sourceKey(),
                    config.getPassiveModifiers(), refreshSeconds)) {
                applied++;
            }
        }
        if (passive.natureHeal() > 0.0D && world.heal(context.ownerUuid(), passive.natureHeal())) applied++;
        return applied;
    }

    private AbilityExecution prepareExecution(
            ProfileContext context,
            MiniwyvernArchetypeConfig config,
            MiniwyvernArchetypeConfig.Ability ability,
            MiniwyvernAbilityWorld world,
            MutableState state,
            long nowMs) {
        String policy = ability.getTargetPolicy().toUpperCase(Locale.ROOT);
        if (policy.equals("OWNER_ONLY")) {
            MiniwyvernAbilityWorld.Health health = world.health(context.ownerUuid());
            Double threshold = ability.getOwnerHealthThreshold();
            if (threshold != null && health.fraction() > threshold) return AbilityExecution.notReady();
            return new AbilityExecution(true, context.ownerUuid(), health);
        }

        Optional<MiniwyvernAbilityWorld.Target> candidate = world.hostileTarget(ability.getRange());
        if (candidate.isEmpty()) return AbilityExecution.notReady();
        MiniwyvernAbilityWorld.Target target = candidate.orElseThrow();
        if (!target.alive() || target.distance() > ability.getRange()
                || !world.worldName().equals(target.worldName())
                || target.entityUuid().equals(context.ownerUuid())
                || target.entityUuid().equals(context.npcUuid())
                || context.ownerUuid().equals(target.ownerUuid())
                || world.areAllies(context.ownerUuid(), target.entityUuid())) {
            return AbilityExecution.notReady();
        }
        if (config.getId().equals("ice")
                && nowMs < state.immunityUntil.getOrDefault(target.entityUuid(), 0L)) {
            return AbilityExecution.notReady();
        }
        return new AbilityExecution(true, target.entityUuid(), world.health(target.entityUuid()));
    }

    private boolean execute(
            ProfileContext context,
            MiniwyvernArchetypeConfig config,
            MiniwyvernArchetypeConfig.Ability ability,
            AbilityExecution execution,
            MiniwyvernAbilityWorld world,
            MutableState state,
            long nowMs) {
        UUID target = execution.targetUuid();
        String source = sourceKey(context.profileId(), config.getId(), ability.getId());
        boolean applied = false;

        if (ability.getProjectileId() != null) {
            applied |= world.launchProjectile(context.npcUuid(), target, ability.getProjectileId());
        }
        if (ability.getEffectId() != null) {
            applied |= world.applyEffect(target, source, ability.getEffectId(), ability.getDurationSeconds());
        }

        switch (config.getId()) {
            case "lightning" -> {
                if (ability.getMagnitude() > 0.0D) {
                    applied |= world.dealDamage(context.npcUuid(), target, ability.getMagnitude());
                }
            }
            case "ice" -> applied |= applyIce(context, ability, target, source, world, state, nowMs);
            case "water" -> {
                MiniwyvernAbilityWorld.Health health = execution.health();
                double cap = health.maximum() * Objects.requireNonNullElse(ability.getMaximumHealFraction(), 0.0D);
                double amount = Math.min(Math.min(ability.getMagnitude(), cap), health.maximum() - health.current());
                if (amount > 0.0D) applied |= world.heal(target, amount);
            }
            case "void" -> {
                // The bounded defense reduction is supplied by the validated effect asset. Source refresh prevents stacking.
                if (!Set.of("SOURCE_REFRESH", "NON_STACKING", "CLAMPED")
                        .contains(ability.getStackingPolicy().toUpperCase(Locale.ROOT))) {
                    return false;
                }
            }
            default -> {
                // Fire and Wind execute through their projectile/effect assets.
            }
        }
        state.updatedAt = nowMs;
        return applied;
    }

    private boolean applyIce(
            ProfileContext context,
            MiniwyvernArchetypeConfig.Ability ability,
            UUID target,
            String source,
            MiniwyvernAbilityWorld world,
            MutableState state,
            long nowMs) {
        double perHit = Objects.requireNonNullElse(ability.getBuildupPerHit(), 0.0D);
        double threshold = Objects.requireNonNullElse(ability.getBuildupThreshold(), Double.POSITIVE_INFINITY);
        double cap = Objects.requireNonNullElse(ability.getBuildupCap(), threshold);
        double buildup = Math.min(cap, state.iceBuildup.getOrDefault(target, 0.0D) + perHit);
        if (buildup < threshold) {
            state.iceBuildup.put(target, buildup);
            return false;
        }
        state.iceBuildup.remove(target);
        long immunity = secondsToMs(Objects.requireNonNullElse(ability.getControlImmunitySeconds(), 0.0D));
        state.immunityUntil.put(target, saturatingAdd(nowMs, immunity));
        String control = ability.getControlEffectId();
        return control != null && world.applyEffect(target, source + ":control", control, ability.getDurationSeconds());
    }

    private void cleanupSources(
            ProfileContext context,
            MiniwyvernAbilityState state,
            Map<String, MiniwyvernArchetypeConfig> archetypes,
            MiniwyvernAbilityWorld world) {
        for (String source : state.appliedSourceKeys()) {
            UUID targetUuid = state.targetBySourceKey().get(source);
            world.removeOwnerModifiers(context.ownerUuid(), source);
            for (MiniwyvernArchetypeConfig config : archetypes.values()) {
                for (String effectId : config.getPassiveEffects()) {
                    world.removeEffect(context.ownerUuid(), source, effectId);
                }
                for (MiniwyvernArchetypeConfig.Ability ability : config.getActiveAbilities()) {
                    if (ability.getEffectId() != null) {
                        world.removeEffect(context.ownerUuid(), source, ability.getEffectId());
                        world.removeEffect(context.npcUuid(), source, ability.getEffectId());
                        if (targetUuid != null) world.removeEffect(targetUuid, source, ability.getEffectId());
                    }
                    if (ability.getControlEffectId() != null) {
                        world.removeEffect(context.ownerUuid(), source + ":control", ability.getControlEffectId());
                        world.removeEffect(context.npcUuid(), source + ":control", ability.getControlEffectId());
                        if (targetUuid != null) {
                            world.removeEffect(targetUuid, source + ":control", ability.getControlEffectId());
                        }
                    }
                }
            }
        }
    }

    private static double passiveRefreshSeconds(MiniwyvernArchetypeConfig config) {
        return config.getId().equals("nature") ? 10.0D : 8.0D;
    }

    private static String sourceKey(String profileId, String archetypeId, String abilityId) {
        return SOURCE_PREFIX + requiredText(profileId, "profileId") + ":"
                + normalize(archetypeId) + ":" + requiredText(abilityId, "abilityId");
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
            String archetypeId,
            boolean owned,
            boolean active,
            boolean alive,
            boolean featureAvailable) {
        public ProfileContext {
            profileId = requiredText(profileId, "profileId");
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            Objects.requireNonNull(npcUuid, "npcUuid");
            archetypeId = normalize(archetypeId);
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

    private record AbilityExecution(boolean ready, UUID targetUuid, MiniwyvernAbilityWorld.Health health) {
        static AbilityExecution notReady() {
            return new AbilityExecution(false, new UUID(0L, 0L), new MiniwyvernAbilityWorld.Health(0, 0));
        }
    }

    private record PassiveExecution(String sourceKey, boolean hasPassive, double natureHeal) {
    }

    private static final class MutableState {
        final Map<String, Long> cooldowns;
        final Map<UUID, Double> iceBuildup;
        final Map<UUID, Long> immunityUntil;
        final Set<String> sources;
        final Map<String, UUID> targetsBySource;
        long updatedAt;

        MutableState(MiniwyvernAbilityState state) {
            cooldowns = new LinkedHashMap<>(state.cooldownUntilByAbility());
            iceBuildup = new LinkedHashMap<>(state.iceBuildupByTarget());
            immunityUntil = new LinkedHashMap<>(state.controlImmunityUntilByTarget());
            sources = new LinkedHashSet<>(state.appliedSourceKeys());
            targetsBySource = new LinkedHashMap<>(state.targetBySourceKey());
            updatedAt = state.updatedAtEpochMillis();
        }

        void prune(long nowMs) {
            cooldowns.entrySet().removeIf(entry -> entry.getValue() < nowMs - 86_400_000L);
            immunityUntil.entrySet().removeIf(entry -> entry.getValue() <= nowMs);
            iceBuildup.entrySet().removeIf(entry -> entry.getValue() <= 0.0D);
        }

        MiniwyvernAbilityState freeze(String archetypeId) {
            return new MiniwyvernAbilityState(
                    MiniwyvernAbilityState.SCHEMA_VERSION,
                    archetypeId,
                    cooldowns,
                    iceBuildup,
                    immunityUntil,
                    sources,
                    targetsBySource,
                    updatedAt);
        }
    }
}
