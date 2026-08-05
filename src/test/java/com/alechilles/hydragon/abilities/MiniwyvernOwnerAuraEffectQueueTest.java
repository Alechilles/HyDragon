package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MiniwyvernOwnerAuraEffectQueueTest {
    private static final String WORLD = "flat_world";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_TWO = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void coalescesRepeatedHitsUntilTheDedicatedEffectPhaseDrainsThem() {
        MiniwyvernOwnerAuraEffectQueue queue = immediateQueue();
        MiniwyvernOwnerAuraRegistry.Aura first = aura("void", "HyDragon_Miniwyvern_Void_Exposure", 6.0D);
        MiniwyvernOwnerAuraRegistry.Aura refreshed = aura("void", "HyDragon_Miniwyvern_Void_Exposure", 8.0D);

        queue.submit(WORLD, TARGET, first);
        queue.submit(WORLD, TARGET, refreshed);

        assertTrue(queue.drain(WORLD, TARGET).isEmpty());
        assertEquals(List.of(refreshed), queue.drain(WORLD, TARGET));
        assertTrue(queue.drain(WORLD, TARGET).isEmpty());
    }

    @Test
    void retainsDifferentEffectsQueuedForTheSameTarget() {
        MiniwyvernOwnerAuraEffectQueue queue = immediateQueue();
        MiniwyvernOwnerAuraRegistry.Aura fire = aura("fire", "HyDragon_Miniwyvern_Fire_Burn", 4.0D);
        MiniwyvernOwnerAuraRegistry.Aura voidAura = aura("void", "HyDragon_Miniwyvern_Void_Exposure", 6.0D);

        queue.submit(WORLD, TARGET, voidAura);
        queue.submit(WORLD, TARGET, fire);

        assertTrue(queue.drain(WORLD, TARGET).isEmpty());
        assertEquals(List.of(fire, voidAura), queue.drain(WORLD, TARGET));
    }

    @Test
    void retainsSameTierToxicOwnersUntilRegistryProjection() {
        MiniwyvernOwnerAuraEffectQueue queue = immediateQueue();
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();
        MiniwyvernOwnerAuraRegistry.Aura first = toxicAura(OWNER, "profile-one", "lease-one");
        MiniwyvernOwnerAuraRegistry.Aura second = toxicAura(OWNER_TWO, "profile-two", "lease-two");
        assertTrue(registry.update(OWNER, "profile-one", "lease-one", UUID.randomUUID(),
                "toxic", first.effectId(), 6.0D, first.damageReductionFraction()));
        assertTrue(registry.update(OWNER_TWO, "profile-two", "lease-two", UUID.randomUUID(),
                "toxic", second.effectId(), 6.0D, second.damageReductionFraction()));

        queue.submit(WORLD, TARGET, first);
        queue.submit(WORLD, TARGET, second);

        assertTrue(queue.drain(WORLD, TARGET).isEmpty());
        List<MiniwyvernOwnerAuraRegistry.Aura> drained = queue.drain(WORLD, TARGET);
        assertEquals(2, drained.size(), "owner/lease identity must prevent same-tier coalescing");
        drained.forEach(aura -> registry.recordTargetAura(TARGET, aura, 6.0D, 1_000L));

        assertEquals(2, registry.activeTargetAuras(TARGET, 6_999L).size());
        assertTrue(registry.hasActiveTargetAuraForOwner(OWNER, "toxic", 6_999L));
        assertTrue(registry.hasActiveTargetAuraForOwner(OWNER_TWO, "toxic", 6_999L));
    }

    @Test
    void waitsPastTheClientHitResponseWithoutPostponingRepeatedHits() {
        AtomicLong nowNanos = new AtomicLong();
        MiniwyvernOwnerAuraEffectQueue queue = new MiniwyvernOwnerAuraEffectQueue(
                nowNanos::get, MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS);
        MiniwyvernOwnerAuraRegistry.Aura first = aura(
                "void", "HyDragon_Miniwyvern_Void_Exposure", 6.0D);
        MiniwyvernOwnerAuraRegistry.Aura refreshed = aura(
                "void", "HyDragon_Miniwyvern_Void_Exposure", 8.0D);

        queue.submit(WORLD, TARGET, first);
        assertTrue(queue.drain(WORLD, TARGET).isEmpty());

        nowNanos.set(MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS - 1L);
        queue.submit(WORLD, TARGET, refreshed);
        assertTrue(queue.drain(WORLD, TARGET).isEmpty());

        nowNanos.set(MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS);
        assertEquals(List.of(refreshed), queue.drain(WORLD, TARGET));
    }

    @Test
    void requiresALaterEffectPhaseEvenWhenTheHitTickOutlastsTheDelay() {
        AtomicLong nowNanos = new AtomicLong();
        MiniwyvernOwnerAuraEffectQueue queue = new MiniwyvernOwnerAuraEffectQueue(
                nowNanos::get, MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS);
        MiniwyvernOwnerAuraRegistry.Aura aura = aura(
                "void", "HyDragon_Miniwyvern_Void_Exposure", 6.0D);

        queue.submit(WORLD, TARGET, aura);
        nowNanos.set(MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS + 1L);

        assertTrue(queue.drain(WORLD, TARGET).isEmpty());
        assertEquals(List.of(aura), queue.drain(WORLD, TARGET));
    }

    @Test
    void repeatedHitAtTheDeadlineAlsoRequiresALaterEffectPhase() {
        AtomicLong nowNanos = new AtomicLong();
        MiniwyvernOwnerAuraEffectQueue queue = new MiniwyvernOwnerAuraEffectQueue(
                nowNanos::get, MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS);
        MiniwyvernOwnerAuraRegistry.Aura first = aura(
                "void", "HyDragon_Miniwyvern_Void_Exposure", 6.0D);
        MiniwyvernOwnerAuraRegistry.Aura refreshed = aura(
                "void", "HyDragon_Miniwyvern_Void_Exposure", 8.0D);

        queue.submit(WORLD, TARGET, first);
        assertTrue(queue.drain(WORLD, TARGET).isEmpty());
        nowNanos.set(MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS);
        queue.submit(WORLD, TARGET, refreshed);

        assertTrue(queue.drain(WORLD, TARGET).isEmpty());
        assertEquals(List.of(refreshed), queue.drain(WORLD, TARGET));
    }

    @Test
    void aFreshHitReplacesAnExpiredUndrainableRequest() {
        AtomicLong nowNanos = new AtomicLong();
        MiniwyvernOwnerAuraEffectQueue queue = new MiniwyvernOwnerAuraEffectQueue(
                nowNanos::get, MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS);
        MiniwyvernOwnerAuraRegistry.Aura expired = aura(
                "void", "HyDragon_Miniwyvern_Void_Exposure", 6.0D);
        MiniwyvernOwnerAuraRegistry.Aura fresh = aura(
                "void", "HyDragon_Miniwyvern_Void_Exposure", 8.0D);

        queue.submit(WORLD, TARGET, expired);
        nowNanos.set(MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS
                + MiniwyvernOwnerAuraEffectQueue.STALE_REQUEST_NANOS + 1L);
        queue.submit(WORLD, TARGET, fresh);

        assertTrue(queue.drain(WORLD, TARGET).isEmpty());
        nowNanos.addAndGet(MiniwyvernOwnerAuraEffectQueue.HIT_RESPONSE_DELAY_NANOS);
        assertEquals(List.of(fresh), queue.drain(WORLD, TARGET));
    }

    @Test
    void reapplicationQueuedAfterRemovalIsReleasedInTheFollowingCycle() {
        MiniwyvernOwnerAuraEffectQueue queue = immediateQueue();
        MiniwyvernOwnerAuraRegistry.Aura aura = aura(
                "void", "HyDragon_Miniwyvern_Void_Exposure", 6.0D);

        MiniwyvernOwnerAuraEffectQueue.Cycle currentCycle = queue.drainCycle(WORLD, TARGET);
        queue.submitAfterRemoval(WORLD, TARGET, aura);

        assertTrue(currentCycle.reapplications().isEmpty());
        assertEquals(List.of(aura), queue.drainCycle(WORLD, TARGET).reapplications());
        assertTrue(queue.drainCycle(WORLD, TARGET).reapplications().isEmpty());
    }

    @Test
    void appliesQueuedEffectsAfterDamageCaptureAndBeforeEntityEffectReplication() {
        MiniwyvernOwnerAuraEffectSystem system = new MiniwyvernOwnerAuraEffectSystem(
                new MiniwyvernOwnerAuraEffectQueue(),
                new MiniwyvernOwnerAuraRegistry(),
                new MiniwyvernVoidEffectLifetimeSystem());

        Map<Class<?>, Order> dependencies = system.getDependencies().stream()
                .filter(SystemDependency.class::isInstance)
                .map(SystemDependency.class::cast)
                .collect(Collectors.toMap(SystemDependency::getSystemClass, SystemDependency::getOrder));

        assertEquals(Order.AFTER, dependencies.get(MiniwyvernOwnerAuraDamageSystem.class));
        assertEquals(Order.BEFORE, dependencies.get(EntityTrackerSystems.EffectControllerSystem.class));
    }

    private static MiniwyvernOwnerAuraRegistry.Aura aura(String formId, String effectId, double durationSeconds) {
        return aura(OWNER, "profile", "lease", formId, effectId, durationSeconds);
    }

    private static MiniwyvernOwnerAuraRegistry.Aura toxicAura(
            UUID ownerUuid, String profileId, String leaseId) {
        return aura(ownerUuid, profileId, leaseId,
                "toxic", "HyDragon_Miniwyvern_Toxic_Weakness", 6.0D);
    }

    private static MiniwyvernOwnerAuraRegistry.Aura aura(
            UUID ownerUuid, String profileId, String leaseId,
            String formId, String effectId, double durationSeconds) {
        return new MiniwyvernOwnerAuraRegistry.Aura(
                ownerUuid, profileId, leaseId,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                formId, effectId, durationSeconds, 0.0D);
    }

    private static MiniwyvernOwnerAuraEffectQueue immediateQueue() {
        return new MiniwyvernOwnerAuraEffectQueue(() -> 0L, 0L);
    }
}
