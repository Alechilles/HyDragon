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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, source-keyed Miniwyvern ability scheduler.
 *
 * <p>The caller must invoke this service on the owning Hytale world thread. Durable cooldown state
 * is committed before a gameplay mutation, so a crash can suppress a cast but cannot duplicate one.</p>
 */
public final class MiniwyvernAbilityService {
    private static final String SOURCE_PREFIX = "hydragon:mini:";
    private static final String WARD_ABILITY_ID = "ward";
    private static final String SPEED_BURST_ABILITY_ID = "speed-burst";
    private static final Map<SpeedBurstKey, String> SPEED_BURST_EFFECT_IDS = Map.of(
            new SpeedBurstKey("lightning", 1.10D, 3.0D),
                    "HyDragon_Miniwyvern_Lightning_SpeedBurst_110_3",
            new SpeedBurstKey("lightning", 1.15D, 4.0D),
                    "HyDragon_Miniwyvern_Lightning_SpeedBurst_115_4",
            new SpeedBurstKey("nature", 1.05D, 2.0D),
                    "HyDragon_Miniwyvern_Nature_SpeedBurst_105_2",
            new SpeedBurstKey("nature", 1.10D, 2.0D),
                    "HyDragon_Miniwyvern_Nature_SpeedBurst_110_2");
    private static final long ICE_TARGET_RETENTION_MS = 60_000L;
    private static final Pattern NATURE_TIER_PATTERN = Pattern.compile("(?:^|\\D)(15|20|25|30)(?:$|\\D)");
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
        boolean requiredTalentPurchased = requiredTalentPurchased(config, world);
        synchronizeOwnerAttackAura(context, config, world, requiredTalentPurchased);

        MiniwyvernAbilityStateRepository.LoadResult loaded = states.load(
                context.ownerUuid(), context.profileId());
        if (loaded.status() == MiniwyvernAbilityStateRepository.Status.UNAVAILABLE) {
            return TickResult.denied("ability-state-unavailable");
        }
        boolean stateMissing = loaded.status() == MiniwyvernAbilityStateRepository.Status.MISSING;
        MiniwyvernAbilityState state = loaded.status() == MiniwyvernAbilityStateRepository.Status.LOADED
                ? loaded.state() : MiniwyvernAbilityState.empty(formId, nowMs);
        if (!state.formId().equals(formId)) {
            cleanupSources(context, state, archetypes, world);
            state = MiniwyvernAbilityState.empty(formId, nowMs);
        }
        MutableState mutable = new MutableState(state);
        mutable.discardRetiredCombatState();
        mutable.prune(nowMs);
        PassiveExecution passive = preparePassives(
                context, config, world, mutable, requiredTalentPurchased, nowMs);
        Set<String> diagnostics = new LinkedHashSet<>(passive.diagnostics());
        // Establish source ownership and every non-idempotent cooldown before mutating the world.
        MiniwyvernAbilityState preparedState = mutable.freeze(formId);
        if ((stateMissing || !preparedState.equals(state)) && !states.save(
                context.ownerUuid(), context.profileId(), preparedState)) {
            return TickResult.denied("ability-state-unavailable");
        }

        int effectsApplied = executePassives(context, config, passive, world);
        // Combat execution belongs to the role/root-interaction assets. This retained service only
        // refreshes Java-owned owner passives and their lifecycle cleanup.
        int abilitiesExecuted = 0;

        mutable.prune(nowMs);
        MiniwyvernAbilityState finalState = mutable.freeze(formId);
        if (!finalState.equals(preparedState) && !states.save(
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
        return deactivate(context, archetypes, world, nowMs, captureWard(context.ownerUuid()));
    }

    /**
     * Removes tracked effects when lifecycle state changes, using a ward identity captured before
     * a world-thread callback was queued. The callback must not depend on the live aura registry,
     * because shutdown may clear that registry before this world-thread work runs.
     */
    TickResult deactivate(
            ProfileContext context,
            Map<String, MiniwyvernArchetypeConfig> archetypes,
            MiniwyvernAbilityWorld world,
            long nowMs,
            WardCleanup wardCleanup) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(archetypes, "archetypes");
        Objects.requireNonNull(world, "world");
        if (!world.isWorldThread()) return TickResult.denied("not-world-thread");
        removeWard(wardCleanup, world);
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
            boolean requiredTalentPurchased,
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
        boolean passiveDisabled = !requiredTalentPurchased || !unsupportedModifiers.isEmpty();
        List<String> diagnostics = !requiredTalentPurchased
                ? List.of("passive-ability-disabled:talent-locked:" + config.getRequiredTalentId())
                : passiveDisabled
                        ? List.of("passive-ability-disabled:" + String.join("+", unsupportedModifiers))
                        : List.of();
        boolean hasPassive = !passiveDisabled && (!config.getPassiveEffects().isEmpty()
                || !supportedEffectModifiers.isEmpty() || !supportedRawModifiers.isEmpty());
        boolean playerOnlyForm = config.getId().equals("lightning") || config.getId().equals("nature");
        boolean refreshPassive = hasPassive && (playerOnlyForm || shouldRefreshPassiveLease(
                state.sourceExpiresAt.get(passiveSource), nowMs, passiveRefreshSeconds(config)));
        boolean cleanupDisabledPassive = false;
        if (passiveDisabled) {
            cleanupDisabledPassive = state.sources.remove(passiveSource);
            cleanupDisabledPassive |= state.targetsBySource.remove(passiveSource) != null;
            state.sourceExpiresAt.remove(passiveSource);
            if (cleanupDisabledPassive) state.updatedAt = nowMs;
            supportedEffectModifiers.clear();
            supportedRawModifiers.clear();
        }
        if (refreshPassive && !state.trackSource(
                passiveSource,
                context.ownerUuid(),
                saturatingAdd(nowMs, secondsToMs(passiveRefreshSeconds(config))))) {
            hasPassive = false;
            refreshPassive = false;
            diagnostics = List.of("passive-ability-disabled:source-tracking-capacity");
            supportedEffectModifiers.clear();
            supportedRawModifiers.clear();
        }

        MiniwyvernOwnerAuraRegistry.Aura ownerAura = ownerAuras.activeFor(context.ownerUuid())
                .filter(aura -> aura.formId().equals(config.getId()))
                .orElse(null);
        double natureHeal = 0.0D;
        double speedBurstMultiplier = ownerAura == null ? 0.0D : ownerAura.speedBurstMultiplier();
        double speedBurstDurationSeconds = ownerAura == null ? 0.0D : ownerAura.speedBurstDurationSeconds();
        boolean speedBurstTriggered = false;
        if (!passiveDisabled && config.getId().equals("nature")) {
            String abilityId = "nature_regeneration";
            long next = state.cooldowns.getOrDefault(abilityId, 0L);
            if (nowMs >= next) {
                double tickSeconds = config.getPassiveModifiers().getOrDefault("RegenerationTickSeconds", 0.0D);
                double maximumFraction = ownerAura == null
                        ? config.getPassiveModifiers().getOrDefault("MaximumHealFractionPerTick", 0.0D)
                        : ownerAura.ownerRegenerationFraction();
                MiniwyvernAbilityWorld.Health health = world.health(context.ownerUuid());
                natureHeal = Math.min(health.maximum() * maximumFraction, health.maximum() - health.current());
                state.cooldowns.put(abilityId, saturatingAdd(nowMs, secondsToMs(tickSeconds)));
                state.updatedAt = nowMs;
                speedBurstTriggered = natureHeal > 0.0D;
            }
        }
        if (config.getId().equals("lightning") && !passiveDisabled) {
            speedBurstTriggered = ownerAuras.consumeSpeedBurst(context.ownerUuid());
        }
        String speedBurstEffectId = speedBurstEffectId(
                config.getId(), speedBurstMultiplier, speedBurstDurationSeconds);
        Set<String> candidateOwnerEffects = ownerEffectCandidates(config);
        Set<String> activeOwnerEffects = ownerPassiveEffects(config, ownerAura);
        return new PassiveExecution(
                passiveSource,
                hasPassive,
                refreshPassive,
                cleanupDisabledPassive,
                natureHeal,
                speedBurstMultiplier,
                speedBurstDurationSeconds,
                speedBurstTriggered,
                speedBurstEffectId,
                candidateOwnerEffects,
                activeOwnerEffects,
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
            for (String effectId : passive.candidateOwnerEffects()) {
                world.removeEffect(context.ownerUuid(), passive.sourceKey(), effectId);
            }
            for (String effectId : new LinkedHashSet<>(config.getPassiveModifierEffects().values())) {
                world.removeEffect(context.ownerUuid(), passive.sourceKey(), effectId);
            }
            world.removeOwnerModifiers(context.ownerUuid(), passive.sourceKey());
            removeSpeedBurstEffects(context.ownerUuid(), context.profileId(), config.getId(), world);
        }
        if (passive.refreshPassive()) {
            double refreshSeconds = passiveRefreshSeconds(config);
            for (String effectId : passive.candidateOwnerEffects()) {
                if (!passive.activeOwnerEffects().contains(effectId)) {
                    world.removeEffect(context.ownerUuid(), passive.sourceKey(), effectId);
                }
            }
            for (String effectId : passive.activeOwnerEffects()) {
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
        if (passive.speedBurstTriggered()
                && passive.speedBurstMultiplier() > 0.0D
                && passive.speedBurstDurationSeconds() > 0.0D
                && passive.speedBurstEffectId() != null
                && world.applyOwnerEffect(context.ownerUuid(),
                        speedBurstSource(context.profileId(), config.getId()),
                        passive.speedBurstEffectId(),
                        passive.speedBurstDurationSeconds())) {
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
                for (String effectId : ownerEffectCandidates(config)) {
                    world.removeEffect(context.ownerUuid(), source, effectId);
                }
                for (String effectId : new LinkedHashSet<>(config.getPassiveModifierEffects().values())) {
                    world.removeEffect(context.ownerUuid(), source, effectId);
                }
            }
        }
        for (MiniwyvernArchetypeConfig config : archetypes.values()) {
            if (config.getId().equals("lightning") || config.getId().equals("nature")) {
                removeSpeedBurstEffects(context.ownerUuid(), context.profileId(), config.getId(), world);
            }
        }
    }

    private static double passiveRefreshSeconds(MiniwyvernArchetypeConfig config) {
        return config.getId().equals("nature") ? 10.0D : 8.0D;
    }

    private static Set<String> ownerEffectCandidates(MiniwyvernArchetypeConfig config) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>(config.getPassiveEffects());
        if (config.getId().equals("lightning") || config.getId().equals("nature")) {
            MiniwyvernArchetypeConfig.EssenceBondAura essence = config.getEssenceBondAura();
            if (essence != null) {
                for (MiniwyvernArchetypeConfig.Upgrade upgrade : essence.getUpgrades()) {
                    if (upgrade != null && upgrade.getTargetEffectId() != null) {
                        candidates.add(upgrade.getTargetEffectId());
                    }
                }
            }
        }
        return Set.copyOf(candidates);
    }

    private static Set<String> ownerPassiveEffects(
            MiniwyvernArchetypeConfig config,
            MiniwyvernOwnerAuraRegistry.Aura aura) {
        if (!config.getId().equals("lightning") && !config.getId().equals("nature")) {
            return Set.copyOf(config.getPassiveEffects());
        }
        if (aura == null || aura.ownerEffectId() == null || aura.ownerEffectId().isBlank()) {
            return Set.of();
        }
        return Set.of(aura.ownerEffectId());
    }

    private static double natureRegenerationFraction(String effectId, double fallback) {
        if (effectId == null || effectId.isBlank()) return fallback;
        Matcher matcher = NATURE_TIER_PATTERN.matcher(effectId.trim());
        if (!matcher.find()) return fallback;
        return Integer.parseInt(matcher.group(1)) / 1_000.0D;
    }

    private static boolean shouldRefreshPassiveLease(
            Long expiresAtMs, long nowMs, double durationSeconds) {
        long durationMs = secondsToMs(durationSeconds);
        long renewalLeadMs = Math.max(1_000L, durationMs / 2L);
        return expiresAtMs == null || expiresAtMs <= saturatingAdd(nowMs, renewalLeadMs);
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

    /** Captures source-owned Ward identity before scheduling world-thread cleanup. */
    WardCleanup captureWard(UUID ownerUuid) {
        MiniwyvernOwnerAuraRegistry.Aura aura = ownerAuras.activeFor(ownerUuid).orElse(null);
        if (aura == null || aura.wardEffectId() == null) return null;
        String source = wardSource(aura);
        return source == null ? null : new WardCleanup(ownerUuid, source, aura.wardEffectId());
    }

    private void synchronizeOwnerAttackAura(
            ProfileContext context,
            MiniwyvernArchetypeConfig config,
            MiniwyvernAbilityWorld world,
            boolean requiredTalentPurchased) {
        String formId = config.getId();
        MiniwyvernArchetypeConfig.OwnerAttackAura aura = config.getOwnerAttackAura();
        boolean playerOnlyForm = formId.equals("lightning") || formId.equals("nature");
        MiniwyvernOwnerAuraRegistry.Aura previous = ownerAuras.activeFor(context.ownerUuid()).orElse(null);
        if (!requiredTalentPurchased || (aura == null && !playerOnlyForm)) {
            removeWard(context.ownerUuid(), previous, world);
            removeSpeedBurstEffects(context.ownerUuid(), context.profileId(),
                    previous == null ? formId : previous.formId(), world);
            ownerAuras.clear(context.ownerUuid(), context.profileId(), context.npcUuid().toString());
            return;
        }

        String effectId = aura == null || aura.getEffectId() == null ? "" : aura.getEffectId();
        double durationSeconds = aura == null ? 0.0D : aura.getDurationSeconds();
        Double damageReductionFraction = aura == null ? null : aura.getDamageReductionFraction();
        // Void's retained root aura has always increased damage taken by 12%; its original
        // configuration predates the explicit field, so preserve that baseline when absent.
        double targetDamageTakenFraction = aura == null ? 0.0D : aura.getTargetDamageTakenFraction();
        if (aura != null && aura.getTargetDamageTakenFractionOverride() == null && "void".equals(formId)) {
            targetDamageTakenFraction = 0.12D;
        }
        double ownerDamageToAffectedFraction = 0.0D;
        String wardEffectId = null;
        double conditionalWardDamageReductionFraction = 0.0D;
        double siphonMaximumHealthFraction = 0.0D;
        long siphonCooldownMs = 0L;
        String ownerEffectId = playerOnlyForm
                ? config.getPassiveEffects().stream().findFirst().orElse(null) : null;
        double ownerRegenerationFraction = formId.equals("nature")
                ? config.getPassiveModifiers().getOrDefault("MaximumHealFractionPerTick", 0.0D) : 0.0D;
        double speedBurstMultiplier = 0.0D;
        double speedBurstDurationSeconds = 0.0D;

        MiniwyvernArchetypeConfig.EssenceBondAura essenceBondAura = config.getEssenceBondAura();
        if (essenceBondAura != null) {
            for (MiniwyvernArchetypeConfig.Upgrade upgrade : essenceBondAura.getUpgrades()) {
                if (upgrade == null || upgrade.getTalentId().isEmpty()
                        || !world.hasPurchasedTalent(upgrade.getTalentId())) continue;
                if (upgrade.getTargetEffectId() != null) {
                    if (playerOnlyForm) {
                        ownerEffectId = upgrade.getTargetEffectId();
                        if (formId.equals("nature")) {
                            ownerRegenerationFraction = natureRegenerationFraction(
                                    ownerEffectId, ownerRegenerationFraction);
                        }
                    } else {
                        effectId = upgrade.getTargetEffectId();
                    }
                }
                if (!playerOnlyForm && upgrade.getTargetDurationSecondsOverride() != null) {
                    durationSeconds = upgrade.getTargetDurationSecondsOverride();
                }
                if (!playerOnlyForm && upgrade.getTargetOutgoingDamageReductionFractionOverride() != null) {
                    damageReductionFraction = upgrade.getTargetOutgoingDamageReductionFractionOverride();
                }
                if (!playerOnlyForm && upgrade.getTargetDamageTakenFractionOverride() != null) {
                    targetDamageTakenFraction = upgrade.getTargetDamageTakenFractionOverride();
                }
                if (upgrade.getOwnerDamageToAffectedFractionOverride() != null) {
                    ownerDamageToAffectedFraction = upgrade.getOwnerDamageToAffectedFractionOverride();
                }
                if (upgrade.getWardEffectId() != null
                        && !"conditionalward".equals(normalizeOptional(upgrade.getSemantic()))) {
                    wardEffectId = upgrade.getWardEffectId();
                }
                if (upgrade.getConditionalWardDamageReductionFractionOverride() != null) {
                    conditionalWardDamageReductionFraction =
                            upgrade.getConditionalWardDamageReductionFractionOverride();
                }
                if (upgrade.getSiphonMaximumHealthFractionOverride() != null) {
                    siphonMaximumHealthFraction = upgrade.getSiphonMaximumHealthFractionOverride();
                    siphonCooldownMs = siphonMaximumHealthFraction > 0.0D
                            ? upgrade.getSiphonCooldownMs() : 0L;
                }
                if (upgrade.getSpeedBurstMultiplierOverride() != null) {
                    speedBurstMultiplier = upgrade.getSpeedBurstMultiplierOverride();
                }
                if (upgrade.getSpeedBurstDurationSecondsOverride() != null) {
                    speedBurstDurationSeconds = upgrade.getSpeedBurstDurationSecondsOverride();
                }
            }
        }
        if (!ownerAuras.update(
                context.ownerUuid(), context.profileId(), context.npcUuid().toString(), context.npcUuid(),
                formId, effectId, durationSeconds, damageReductionFraction,
                targetDamageTakenFraction, ownerDamageToAffectedFraction, wardEffectId,
                conditionalWardDamageReductionFraction, siphonMaximumHealthFraction, siphonCooldownMs,
                ownerEffectId, ownerRegenerationFraction,
                speedBurstMultiplier, speedBurstDurationSeconds)) {
            removeWard(context.ownerUuid(), previous, world);
            removeSpeedBurstEffects(context.ownerUuid(), context.profileId(),
                    previous == null ? formId : previous.formId(), world);
            ownerAuras.clear(context.ownerUuid(), context.profileId(), context.npcUuid().toString());
            return;
        }
        MiniwyvernOwnerAuraRegistry.Aura current = ownerAuras.activeFor(context.ownerUuid()).orElse(null);
        replaceWard(context.ownerUuid(), previous, current, world);
        replaceSpeedBurst(context.ownerUuid(), context.profileId(), previous, current, world);
    }

    private static void replaceWard(
            UUID ownerUuid,
            MiniwyvernOwnerAuraRegistry.Aura previous,
            MiniwyvernOwnerAuraRegistry.Aura current,
            MiniwyvernAbilityWorld world) {
        String previousEffect = previous == null ? null : previous.wardEffectId();
        String currentEffect = current == null ? null : current.wardEffectId();
        String previousSource = wardSource(previous);
        String currentSource = wardSource(current);
        if (previousEffect != null
                && (!Objects.equals(previousSource, currentSource)
                || !Objects.equals(previousEffect, currentEffect))) {
            world.removeOwnerEffect(ownerUuid, previousSource, previousEffect);
        }
        if (currentEffect != null && currentSource != null && current != null) {
            world.applyOwnerEffect(ownerUuid, currentSource, currentEffect, current.durationSeconds());
        }
    }

    private static void removeWard(
            UUID ownerUuid,
            MiniwyvernOwnerAuraRegistry.Aura aura,
            MiniwyvernAbilityWorld world) {
        if (aura == null || aura.wardEffectId() == null) return;
        String source = wardSource(aura);
        if (source != null) world.removeOwnerEffect(ownerUuid, source, aura.wardEffectId());
    }

    private static void replaceSpeedBurst(
            UUID ownerUuid,
            String profileId,
            MiniwyvernOwnerAuraRegistry.Aura previous,
            MiniwyvernOwnerAuraRegistry.Aura current,
            MiniwyvernAbilityWorld world) {
        if (previous == null) return;
        String previousEffect = speedBurstEffectId(
                previous.formId(), previous.speedBurstMultiplier(), previous.speedBurstDurationSeconds());
        String currentEffect = current == null ? null : speedBurstEffectId(
                current.formId(), current.speedBurstMultiplier(), current.speedBurstDurationSeconds());
        if (previousEffect != null && !Objects.equals(previousEffect, currentEffect)) {
            world.removeOwnerEffect(
                    ownerUuid, speedBurstSource(profileId, previous.formId()), previousEffect);
        }
    }

    private static void removeWard(WardCleanup wardCleanup, MiniwyvernAbilityWorld world) {
        if (wardCleanup == null) return;
        world.removeOwnerEffect(
                wardCleanup.ownerUuid(), wardCleanup.sourceKey(), wardCleanup.effectId());
    }

    private static String wardSource(MiniwyvernOwnerAuraRegistry.Aura aura) {
        return aura == null || aura.wardEffectId() == null
                ? null : sourceKey(aura.profileId(), aura.formId(), WARD_ABILITY_ID);
    }

    record WardCleanup(UUID ownerUuid, String sourceKey, String effectId) {
        WardCleanup {
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            sourceKey = requiredText(sourceKey, "sourceKey");
            effectId = requiredText(effectId, "effectId");
        }
    }

    private static boolean requiredTalentPurchased(
            MiniwyvernArchetypeConfig config,
            MiniwyvernAbilityWorld world) {
        String requiredTalentId = config.getRequiredTalentId();
        return requiredTalentId.isEmpty() || world.hasPurchasedTalent(requiredTalentId);
    }

    private static String sourceKey(String profileId, String formId, String abilityId) {
        return SOURCE_PREFIX + requiredText(profileId, "profileId") + ":"
                + normalize(formId) + ":" + requiredText(abilityId, "abilityId");
    }

    private static String speedBurstSource(String profileId, String formId) {
        return sourceKey(profileId, formId, SPEED_BURST_ABILITY_ID);
    }

    static String speedBurstEffectId(String formId, double multiplier, double durationSeconds) {
        if (formId == null || !Double.isFinite(multiplier) || !Double.isFinite(durationSeconds)) {
            return null;
        }
        String normalizedForm = normalizeOptional(formId);
        return SPEED_BURST_EFFECT_IDS.entrySet().stream()
                .filter(entry -> entry.getKey().formId().equals(normalizedForm))
                .filter(entry -> approximatelyEqual(entry.getKey().multiplier(), multiplier)
                        && approximatelyEqual(entry.getKey().durationSeconds(), durationSeconds))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static Set<String> speedBurstEffectCandidates(String formId) {
        if (formId == null) return Set.of();
        String normalizedForm = normalizeOptional(formId);
        return SPEED_BURST_EFFECT_IDS.entrySet().stream()
                .filter(entry -> entry.getKey().formId().equals(normalizedForm))
                .map(Map.Entry::getValue)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static void removeSpeedBurstEffects(
            UUID ownerUuid, String profileId, String formId, MiniwyvernAbilityWorld world) {
        String source = speedBurstSource(profileId, formId);
        for (String effectId : speedBurstEffectCandidates(formId)) {
            world.removeOwnerEffect(ownerUuid, source, effectId);
        }
    }

    private static String normalizeOptional(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean approximatelyEqual(double left, double right) {
        return Math.abs(left - right) <= 0.000_001D;
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
            boolean refreshPassive,
            boolean cleanupDisabledPassive,
            double natureHeal,
            double speedBurstMultiplier,
            double speedBurstDurationSeconds,
            boolean speedBurstTriggered,
            String speedBurstEffectId,
            Set<String> candidateOwnerEffects,
            Set<String> activeOwnerEffects,
            Map<String, String> effectModifiers,
            Map<String, Double> rawModifiers,
            List<String> diagnostics) {
    }

    private record SpeedBurstKey(String formId, double multiplier, double durationSeconds) {
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

        void discardRetiredCombatState() {
            cooldowns.keySet().removeIf(id -> !id.equals("nature_regeneration"));
            iceBuildup.clear();
            immunityUntil.clear();
            iceTargetUpdatedAt.clear();
            sources.removeIf(source -> !source.endsWith(":passive"));
            targetsBySource.keySet().retainAll(sources);
            sourceExpiresAt.keySet().retainAll(sources);
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
