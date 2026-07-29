package com.alechilles.hydragon.abilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class MiniwyvernOwnerAuraEffectQueue {
    /** Keeps effect replication clear of the client's transient damage-hit visual response. */
    static final long HIT_RESPONSE_DELAY_NANOS = 250_000_000L;
    static final long STALE_REQUEST_NANOS = 30_000_000_000L;

    private final ConcurrentHashMap<RequestKey, PendingAura> pending =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RequestKey, PendingAura> reapplications =
            new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;
    private final long delayNanos;

    public MiniwyvernOwnerAuraEffectQueue() {
        this(System::nanoTime, HIT_RESPONSE_DELAY_NANOS);
    }

    MiniwyvernOwnerAuraEffectQueue(LongSupplier nanoTime, long delayNanos) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (delayNanos < 0L) throw new IllegalArgumentException("delayNanos must not be negative");
        this.delayNanos = delayNanos;
    }

    void submit(String worldName, UUID targetUuid, MiniwyvernOwnerAuraRegistry.Aura aura) {
        Objects.requireNonNull(aura, "aura");
        RequestKey key = new RequestKey(worldName, targetUuid, aura.effectId());
        long nowNanos = nanoTime.getAsLong();
        long readyAtNanos = nowNanos + delayNanos;
        pending.compute(key, (ignored, current) -> {
            if (current == null
                    || hasReached(nowNanos, current.readyAtNanos() + STALE_REQUEST_NANOS)) {
                return new PendingAura(aura, readyAtNanos, false);
            }
            return new PendingAura(aura, current.readyAtNanos(), false);
        });
    }

    /** Queues a fresh Add for the cycle after a COMPLETE removal was replicated. */
    void submitAfterRemoval(String worldName, UUID targetUuid, MiniwyvernOwnerAuraRegistry.Aura aura) {
        Objects.requireNonNull(aura, "aura");
        RequestKey key = new RequestKey(worldName, targetUuid, aura.effectId());
        reapplications.put(key, new PendingAura(aura, nanoTime.getAsLong(), true));
    }

    Cycle drainCycle(String worldName, UUID targetUuid) {
        return new Cycle(
                drainReapplications(worldName, targetUuid),
                drain(worldName, targetUuid));
    }

    List<MiniwyvernOwnerAuraRegistry.Aura> drain(String worldName, UUID targetUuid) {
        String requiredWorld = requireText(worldName, "worldName");
        Objects.requireNonNull(targetUuid, "targetUuid");
        long nowNanos = nanoTime.getAsLong();
        List<QueuedAura> drained = new ArrayList<>();
        for (var entry : pending.entrySet()) {
            RequestKey key = entry.getKey();
            PendingAura queued = entry.getValue();
            if (hasReached(nowNanos, queued.readyAtNanos() + STALE_REQUEST_NANOS)) {
                pending.remove(key, queued);
                continue;
            }
            if (!key.worldName().equals(requiredWorld) || !key.targetUuid().equals(targetUuid)) {
                continue;
            }
            if (!queued.effectPhaseObserved()) {
                pending.replace(key, queued, queued.afterEffectPhase());
                continue;
            }
            if (hasReached(nowNanos, queued.readyAtNanos()) && pending.remove(key, queued)) {
                drained.add(new QueuedAura(key.effectId(), queued.aura()));
            }
        }
        drained.sort(Comparator.comparing(QueuedAura::effectId));
        return drained.stream().map(QueuedAura::aura).toList();
    }

    private List<MiniwyvernOwnerAuraRegistry.Aura> drainReapplications(
            String worldName, UUID targetUuid) {
        String requiredWorld = requireText(worldName, "worldName");
        Objects.requireNonNull(targetUuid, "targetUuid");
        long nowNanos = nanoTime.getAsLong();
        List<QueuedAura> drained = new ArrayList<>();
        for (var entry : reapplications.entrySet()) {
            RequestKey key = entry.getKey();
            PendingAura queued = entry.getValue();
            if (hasReached(nowNanos, queued.readyAtNanos() + STALE_REQUEST_NANOS)) {
                reapplications.remove(key, queued);
                continue;
            }
            if (key.worldName().equals(requiredWorld) && key.targetUuid().equals(targetUuid)
                    && reapplications.remove(key, queued)) {
                drained.add(new QueuedAura(key.effectId(), queued.aura()));
            }
        }
        drained.sort(Comparator.comparing(QueuedAura::effectId));
        return drained.stream().map(QueuedAura::aura).toList();
    }

    private static boolean hasReached(long nowNanos, long deadlineNanos) {
        return nowNanos - deadlineNanos >= 0L;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private record RequestKey(String worldName, UUID targetUuid, String effectId) {
        private RequestKey {
            worldName = requireText(worldName, "worldName");
            Objects.requireNonNull(targetUuid, "targetUuid");
            effectId = requireText(effectId, "effectId");
        }
    }

    private record QueuedAura(String effectId, MiniwyvernOwnerAuraRegistry.Aura aura) {
    }

    record Cycle(
            List<MiniwyvernOwnerAuraRegistry.Aura> reapplications,
            List<MiniwyvernOwnerAuraRegistry.Aura> applications) {
        Cycle {
            reapplications = List.copyOf(reapplications);
            applications = List.copyOf(applications);
        }
    }

    private record PendingAura(
            MiniwyvernOwnerAuraRegistry.Aura aura,
            long readyAtNanos,
            boolean effectPhaseObserved) {
        private PendingAura {
            Objects.requireNonNull(aura, "aura");
        }

        private PendingAura afterEffectPhase() {
            return new PendingAura(aura, readyAtNanos, true);
        }
    }
}
