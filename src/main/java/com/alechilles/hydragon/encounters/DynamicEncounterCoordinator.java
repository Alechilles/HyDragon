package com.alechilles.hydragon.encounters;

import com.alechilles.alecstamework.api.CaptureAttemptOutcome;
import com.alechilles.alecstamework.api.CaptureAttemptResolvedEvent;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.config.DragonEncounterConfig;
import com.alechilles.hydragon.config.DragonSpeciesConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.persistence.EncounterDefinitionSnapshot;
import com.alechilles.hydragon.persistence.EncounterRecord;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.MutationOutcome;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Crash-safe dynamic encounter admission and phase coordinator. */
public final class DynamicEncounterCoordinator {
    private final Object lock = new Object();
    private final TameworkApi api;
    private final HyDragonStateStore stateStore;
    private final EncounterEligibilityService eligibility;

    public DynamicEncounterCoordinator(
            TameworkApi api,
            HyDragonStateStore stateStore,
            EncounterEligibilityService eligibility) {
        this.api = Objects.requireNonNull(api, "api");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
    }

    public AdmissionResult admit(
            DragonEncounterConfig definition,
            HyDragonConfigRepository.Snapshot configs,
            EncounterCandidate candidate,
            EncounterWorldGateway world,
            boolean featureAvailable,
            long nowMs) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(configs, "configs");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(world, "world");
        if (nowMs < 0L) throw new IllegalArgumentException("nowMs must not be negative");
        if (!world.isWorldThread()) return AdmissionResult.denied("not-world-thread");
        EncounterEligibilityService.Decision decision = eligibility.evaluate(
                definition, configs, candidate, featureAvailable);
        if (!decision.allowed()) return AdmissionResult.denied(decision.reason());

        synchronized (lock) {
            if (!stateStore.snapshot().writable()) return AdmissionResult.denied("state-store-read-only");
            long interval = Math.max(1L, definition.getAdmission().getEvaluationCooldownMs());
            long bucket = nowMs / interval;
            String encounterId = deterministicEncounterId(definition.getId(), candidate, bucket);
            EncounterRecord replay = stateStore.snapshot().encounters().get(encounterId);
            if (replay != null) {
                return new AdmissionResult(true, "already-admitted", encounterId, replay.targetNpcUuid().orElse(null));
            }
            if (hasCooldown(definition.getId(), candidate.worldName(), candidate.regionKey(), nowMs)) {
                return AdmissionResult.denied("encounter-cooldown");
            }
            if (!withinConcurrency(definition, candidate.regionKey())) {
                return AdmissionResult.denied("encounter-concurrency-limit");
            }
            if (deterministicRoll(definition.getId(), candidate, bucket) >= definition.getAdmission().getChance()) {
                return AdmissionResult.denied("admission-roll-failed");
            }
            // Ownership, active-population and item state may change while waiting for the
            // serialized admission boundary. Recheck immediately before the durable claim.
            EncounterEligibilityService.Decision finalDecision = eligibility.evaluate(
                    definition, configs, candidate, featureAvailable);
            if (!finalDecision.allowed()) return AdmissionResult.denied(finalDecision.reason());
            DragonSpeciesConfig species = configs.species().get(definition.getTargetSpeciesId());
            if (species == null || species.getWildRoleIds().isEmpty()) {
                return AdmissionResult.denied("target-species-unavailable");
            }

            EncounterRecord admitted = record(
                    encounterId, definition.getId(), candidate, EncounterCheckpoint.of(EncounterPhase.ADMITTED),
                    EncounterDefinitionSnapshot.capture(definition), Optional.empty(),
                    Set.of(candidate.playerUuid()), nowMs, nowMs, 0L);
            if (!put(admitted)) return AdmissionResult.denied("admission-checkpoint-failed");

            EncounterWorldGateway.SpawnResult spawn;
            try {
                spawn = world.spawn(new EncounterWorldGateway.SpawnRequest(
                        encounterId, definition.getId(), species.getWildRoleIds().getFirst(), candidate, definition));
            } catch (RuntimeException failure) {
                spawn = EncounterWorldGateway.SpawnResult.failure("spawn-gateway-failed");
            }
            if (!spawn.spawned()) {
                return enterCooldown(admitted, admitted.definitionSnapshot(), nowMs)
                        ? AdmissionResult.denied(spawn.reason())
                        : new AdmissionResult(false, "spawn-failed-cooldown-checkpoint-pending", encounterId, null);
            }
            EncounterRecord aerial = update(
                    admitted, EncounterCheckpoint.of(EncounterPhase.AERIAL), Optional.of(spawn.targetNpcUuid()),
                    nowMs, nowMs, 0L);
            if (!put(aerial)) {
                // The spawn is now ambiguous. Preserve ADMITTED so restart reconciliation searches the marker.
                return new AdmissionResult(false, "aerial-checkpoint-pending", encounterId, spawn.targetNpcUuid());
            }
            return new AdmissionResult(true, "spawned", encounterId, spawn.targetNpcUuid());
        }
    }

    public TransitionResult groundingHit(
            String encounterId,
            UUID targetNpcUuid,
            String sourceId,
            double buildup,
            EncounterWorldGateway world,
            long nowMs) {
        Objects.requireNonNull(targetNpcUuid, "targetNpcUuid");
        sourceId = requiredText(sourceId, "sourceId");
        if (!Double.isFinite(buildup) || buildup <= 0.0D) return TransitionResult.denied("invalid-buildup");
        if (!world.isWorldThread()) return TransitionResult.denied("not-world-thread");
        synchronized (lock) {
            EncounterRecord current = stateStore.snapshot().encounter(requiredText(encounterId, "encounterId")).orElse(null);
            if (current == null || current.targetNpcUuid().filter(targetNpcUuid::equals).isEmpty()) {
                return TransitionResult.denied("encounter-target-mismatch");
            }
            EncounterDefinitionSnapshot definition = current.definitionSnapshot();
            EncounterCheckpoint checkpoint = decode(current);
            if (checkpoint.phase() != EncounterPhase.AERIAL && checkpoint.phase() != EncounterPhase.GROUNDING) {
                return TransitionResult.denied("encounter-not-airborne");
            }
            if (!definition.buildupSourceIds().contains(sourceId)) {
                return TransitionResult.denied("grounding-source-not-allowed");
            }
            if (checkpoint.phase() == EncounterPhase.AERIAL
                    && !definition.lureSourceId().equals(sourceId)) {
                return TransitionResult.denied("grounding-lure-required");
            }
            if (checkpoint.phase() == EncounterPhase.GROUNDING
                    && !definition.staggerSourceIds().contains(sourceId)) {
                return TransitionResult.denied("grounding-stagger-required");
            }
            double total = Math.min(definition.groundingThreshold(), checkpoint.groundingBuildup() + buildup);
            if (checkpoint.phase() == EncounterPhase.AERIAL
                    || total < definition.groundingThreshold()) {
                EncounterRecord grounding = update(current,
                        new EncounterCheckpoint(EncounterPhase.GROUNDING, total), current.targetNpcUuid(),
                        checkpoint.phase() == EncounterPhase.GROUNDING ? current.phaseStartedAtEpochMillis() : nowMs,
                        nowMs, 0L);
                return put(grounding)
                        ? new TransitionResult(true, "grounding-progress", EncounterPhase.GROUNDING, total)
                        : TransitionResult.denied("grounding-checkpoint-failed");
            }
            EncounterRecord thresholdReached = update(current,
                    new EncounterCheckpoint(EncounterPhase.GROUNDING, total), current.targetNpcUuid(),
                    checkpoint.phase() == EncounterPhase.GROUNDING ? current.phaseStartedAtEpochMillis() : nowMs,
                    nowMs, 0L);
            if (!put(thresholdReached)) {
                return TransitionResult.denied("grounding-threshold-checkpoint-failed");
            }
            return advanceGrounding(thresholdReached, definition, world, nowMs);
        }
    }

    public void onCaptureResolved(
            CaptureAttemptResolvedEvent event,
            EncounterWorldGateway world) {
        if (event == null || event.outcome() != CaptureAttemptOutcome.CAPTURED || !world.isWorldThread()) return;
        synchronized (lock) {
            findByTarget(event.targetNpcUuid()).ifPresent(record -> enterCooldown(
                    record, record.definitionSnapshot(), Math.max(0L, event.resolvedAtMs())));
        }
    }

    /** Times out active phases while honoring a last-moment committed Tamework capture. */
    public TransitionResult tick(
            String encounterId,
            EncounterWorldGateway world,
            long nowMs) {
        if (!world.isWorldThread()) return TransitionResult.denied("not-world-thread");
        synchronized (lock) {
            EncounterRecord current = stateStore.snapshot().encounter(requiredText(encounterId, "encounterId")).orElse(null);
            if (current == null) return TransitionResult.denied("encounter-missing");
            EncounterDefinitionSnapshot definition = current.definitionSnapshot();
            EncounterCheckpoint checkpoint = decode(current);
            if (checkpoint.phase() == EncounterPhase.COOLDOWN) {
                if (current.cooldownUntilEpochMillis() <= nowMs) {
                    return remove(current.encounterId())
                            ? new TransitionResult(true, "cooldown-complete", EncounterPhase.COOLDOWN, 0.0D)
                            : TransitionResult.denied("cooldown-remove-failed");
                }
                return TransitionResult.denied("cooldown-active");
            }
            if (checkpoint.phase() == EncounterPhase.GROUNDING
                    && checkpoint.groundingBuildup() >= definition.groundingThreshold()) {
                return advanceGrounding(current, definition, world, nowMs);
            }
            boolean expired = nowMs >= saturatingAdd(current.createdAtEpochMillis(),
                    definition.encounterTimeoutMs());
            if (checkpoint.phase() == EncounterPhase.GROUNDED_CAPTURE_WINDOW) {
                expired |= nowMs >= current.cooldownUntilEpochMillis();
            }
            if (!expired) return new TransitionResult(true, "active", checkpoint.phase(), checkpoint.groundingBuildup());
            UUID target = current.targetNpcUuid().orElse(null);
            CaptureStatus captureStatus = target == null ? CaptureStatus.NOT_CAPTURED : captureStatus(target);
            if (captureStatus == CaptureStatus.CAPTURED) {
                return enterCooldown(current, definition, nowMs)
                        ? new TransitionResult(true, "captured-at-cleanup-boundary", EncounterPhase.COOLDOWN, 0.0D)
                        : TransitionResult.denied("captured-cooldown-checkpoint-failed");
            }
            if (captureStatus == CaptureStatus.UNKNOWN) {
                return TransitionResult.denied("capture-state-ambiguous");
            }
            if (target != null && !world.retireTarget(target, "encounter-timeout")) {
                return TransitionResult.denied("target-cleanup-pending");
            }
            return enterCooldown(current, definition, nowMs)
                    ? new TransitionResult(true, "timed-out", EncounterPhase.COOLDOWN, 0.0D)
                    : TransitionResult.denied("timeout-cooldown-checkpoint-failed");
        }
    }

    /** Reattaches persisted targets, blocks ambiguous admissions, and never assumes absence. */
    public TransitionResult reconcile(
            String encounterId,
            EncounterWorldGateway world,
            long nowMs) {
        if (!world.isWorldThread()) return TransitionResult.denied("not-world-thread");
        synchronized (lock) {
            EncounterRecord current = stateStore.snapshot().encounter(requiredText(encounterId, "encounterId")).orElse(null);
            if (current == null) return TransitionResult.denied("encounter-missing");
            EncounterDefinitionSnapshot definition = current.definitionSnapshot();
            EncounterCheckpoint currentCheckpoint = decode(current);
            if (currentCheckpoint.phase() == EncounterPhase.COOLDOWN) {
                return new TransitionResult(true, "cooldown-preserved", EncounterPhase.COOLDOWN, 0.0D);
            }
            EncounterWorldGateway.TargetLookup lookup = world.findTarget(
                    current.encounterId(), current.worldName(), current.targetNpcUuid().orElse(null));
            if (lookup.presence() == EncounterWorldGateway.TargetPresence.PRESENT) {
                EncounterCheckpoint checkpoint = currentCheckpoint;
                EncounterPhase recoveredPhase = checkpoint.phase() == EncounterPhase.ADMITTED
                        || checkpoint.phase() == EncounterPhase.RECONCILING
                        ? EncounterPhase.AERIAL : checkpoint.phase();
                EncounterCheckpoint recoveredCheckpoint = recoveredPhase == checkpoint.phase()
                        ? checkpoint : EncounterCheckpoint.of(recoveredPhase);
                EncounterRecord recovered = update(current, recoveredCheckpoint,
                        lookup.targetNpcUuid(), current.phaseStartedAtEpochMillis(), nowMs,
                        recoveredPhase == EncounterPhase.GROUNDED_CAPTURE_WINDOW
                                ? current.cooldownUntilEpochMillis() : 0L);
                return put(recovered)
                        ? new TransitionResult(true, "target-reattached", recoveredPhase,
                        recoveredCheckpoint.groundingBuildup())
                        : TransitionResult.denied("recovery-checkpoint-failed");
            }
            if (lookup.presence() == EncounterWorldGateway.TargetPresence.ABSENT) {
                return enterCooldown(current, definition, nowMs)
                        ? new TransitionResult(true, "target-authoritatively-absent", EncounterPhase.COOLDOWN, 0.0D)
                        : TransitionResult.denied("absence-cooldown-checkpoint-failed");
            }
            EncounterRecord reconciling = update(current, EncounterCheckpoint.of(EncounterPhase.RECONCILING),
                    current.targetNpcUuid(), current.phaseStartedAtEpochMillis(), nowMs, 0L);
            return put(reconciling)
                    ? new TransitionResult(false, "target-presence-ambiguous", EncounterPhase.RECONCILING, 0.0D)
                    : TransitionResult.denied("reconciliation-checkpoint-failed");
        }
    }

    public boolean captureAllowed(UUID targetNpcUuid) {
        return findByTarget(Objects.requireNonNull(targetNpcUuid, "targetNpcUuid"))
                .map(this::decode)
                .map(EncounterCheckpoint::phase)
                .filter(EncounterPhase.GROUNDED_CAPTURE_WINDOW::equals)
                .isPresent();
    }

    private TransitionResult advanceGrounding(
            EncounterRecord current,
            EncounterDefinitionSnapshot definition,
            EncounterWorldGateway world,
            long nowMs) {
        UUID target = current.targetNpcUuid().orElse(null);
        if (target == null) return TransitionResult.denied("encounter-target-missing");
        if (!world.applyGroundedState(target,
                definition.groundedState(), definition.groundedEffectId())) {
            return TransitionResult.denied("grounding-sequence-apply-failed");
        }
        double buildup = decode(current).groundingBuildup();
        if (!world.isGrounded(target)) {
            return new TransitionResult(true, "grounding-descent-active", EncounterPhase.GROUNDING, buildup);
        }
        EncounterRecord grounded = update(current,
                EncounterCheckpoint.of(EncounterPhase.GROUNDED_CAPTURE_WINDOW), current.targetNpcUuid(),
                nowMs, nowMs, saturatingAdd(nowMs, definition.captureWindowMs()));
        return put(grounded)
                ? new TransitionResult(true, "capture-window-open", EncounterPhase.GROUNDED_CAPTURE_WINDOW, buildup)
                : TransitionResult.denied("grounded-checkpoint-failed");
    }

    private CaptureStatus captureStatus(UUID targetNpcUuid) {
        try {
            return api.profiles().resolveProfileId(targetNpcUuid).isPresent()
                    ? CaptureStatus.CAPTURED : CaptureStatus.NOT_CAPTURED;
        } catch (RuntimeException failure) {
            return CaptureStatus.UNKNOWN;
        }
    }

    private Optional<EncounterRecord> findByTarget(UUID targetNpcUuid) {
        return stateStore.snapshot().encounters().values().stream()
                .filter(record -> record.targetNpcUuid().filter(targetNpcUuid::equals).isPresent())
                .min(Comparator.comparing(EncounterRecord::encounterId));
    }

    private boolean hasCooldown(String definitionId, String worldName, String regionKey, long nowMs) {
        return stateStore.snapshot().encounters().values().stream().anyMatch(record ->
                record.definitionId().equals(definitionId)
                        && record.worldName().equals(worldName)
                        && record.regionKey().equals(regionKey)
                        && decode(record).phase() == EncounterPhase.COOLDOWN
                        && record.cooldownUntilEpochMillis() > nowMs);
    }

    private boolean withinConcurrency(DragonEncounterConfig definition, String regionKey) {
        List<EncounterRecord> active = stateStore.snapshot().encounters().values().stream()
                .filter(record -> record.definitionId().equals(definition.getId()))
                .filter(record -> decode(record).phase() != EncounterPhase.COOLDOWN)
                .toList();
        long region = active.stream().filter(record -> record.regionKey().equals(regionKey)).count();
        return active.size() < definition.getAdmission().getGlobalLimit()
                && region < definition.getAdmission().getPerRegionLimit();
    }

    private boolean enterCooldown(EncounterRecord current, EncounterDefinitionSnapshot definition, long nowMs) {
        return put(update(current, EncounterCheckpoint.of(EncounterPhase.COOLDOWN), Optional.empty(),
                nowMs, nowMs, saturatingAdd(nowMs, definition.retryCooldownMs())));
    }

    private boolean put(EncounterRecord record) {
        try {
            MutationOutcome outcome = stateStore.putEncounter(record);
            return outcome == MutationOutcome.APPLIED || outcome == MutationOutcome.ALREADY_APPLIED;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private boolean remove(String encounterId) {
        try {
            MutationOutcome outcome = stateStore.removeEncounter(encounterId);
            return outcome == MutationOutcome.APPLIED || outcome == MutationOutcome.ALREADY_APPLIED;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static EncounterRecord record(
            String id, String definitionId, EncounterCandidate candidate, EncounterCheckpoint phase,
            EncounterDefinitionSnapshot definitionSnapshot, Optional<UUID> target, Set<UUID> players,
            long created, long phaseStarted, long cooldownUntil) {
        return new EncounterRecord(
                EncounterRecord.SCHEMA_VERSION, id, definitionId, candidate.worldName(), candidate.regionKey(),
                phase.encode(), definitionSnapshot, target, players,
                created, phaseStarted, phaseStarted, cooldownUntil);
    }

    private static EncounterRecord update(
            EncounterRecord current, EncounterCheckpoint phase, Optional<UUID> target,
            long phaseStarted, long updated, long cooldownUntil) {
        return new EncounterRecord(
                current.schemaVersion(), current.encounterId(), current.definitionId(), current.worldName(),
                current.regionKey(), phase.encode(), current.definitionSnapshot(), target,
                current.eligiblePlayerUuids(),
                current.createdAtEpochMillis(), Math.max(current.createdAtEpochMillis(), phaseStarted),
                Math.max(Math.max(current.createdAtEpochMillis(), phaseStarted), updated), cooldownUntil);
    }

    private EncounterCheckpoint decode(EncounterRecord record) {
        try {
            return EncounterCheckpoint.decode(record.phase());
        } catch (RuntimeException failure) {
            return EncounterCheckpoint.of(EncounterPhase.RECONCILING);
        }
    }

    private static double deterministicRoll(String definitionId, EncounterCandidate candidate, long bucket) {
        byte[] digest = sha256(definitionId + '|' + candidate.worldName() + '|' + candidate.regionKey()
                + '|' + candidate.playerUuid() + '|' + bucket);
        long bits = ByteBuffer.wrap(digest).getLong();
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static String deterministicEncounterId(String definitionId, EncounterCandidate candidate, long bucket) {
        String seed = definitionId + '|' + candidate.worldName() + '|' + candidate.regionKey()
                + '|' + candidate.playerUuid() + '|' + bucket;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private enum CaptureStatus { CAPTURED, NOT_CAPTURED, UNKNOWN }

    public record AdmissionResult(boolean admitted, String reason, String encounterId, UUID targetNpcUuid) {
        public AdmissionResult {
            reason = requiredText(reason, "reason");
            encounterId = encounterId == null ? null : encounterId.trim();
            if (admitted && (encounterId == null || encounterId.isEmpty())) {
                throw new IllegalArgumentException("admitted result requires encounterId");
            }
        }
        static AdmissionResult denied(String reason) { return new AdmissionResult(false, reason, null, null); }
    }

    public record TransitionResult(boolean transitioned, String reason, EncounterPhase phase, double buildup) {
        public TransitionResult {
            reason = requiredText(reason, "reason");
            Objects.requireNonNull(phase, "phase");
            if (!Double.isFinite(buildup) || buildup < 0.0D) throw new IllegalArgumentException("invalid buildup");
        }
        static TransitionResult denied(String reason) {
            return new TransitionResult(false, reason, EncounterPhase.RECONCILING, 0.0D);
        }
    }
}
