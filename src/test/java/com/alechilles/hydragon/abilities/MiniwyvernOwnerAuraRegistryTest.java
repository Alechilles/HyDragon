package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MiniwyvernOwnerAuraRegistryTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void replacesThePreviousLeaseAndClearsOnlyItsMatchingLiveProjection() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();

        registry.update(OWNER, "profile", "lease-one", UUID.randomUUID(), "fire", "burn", 4.0D, null);
        registry.update(OWNER, "profile", "lease-two", UUID.randomUUID(), "toxic", "weakness", 6.0D, 0.12D);

        MiniwyvernOwnerAuraRegistry.Aura aura = registry.activeFor(OWNER).orElseThrow();
        assertEquals("lease-two", aura.leaseId());
        assertEquals("weakness", aura.effectId());
        assertEquals(0.12D, aura.damageReductionFraction());
        assertFalse(registry.clear(OWNER, "profile", "lease-one"));
        assertTrue(registry.activeFor(OWNER).isPresent());
        assertTrue(registry.clear(OWNER, "profile", "lease-two"));
        assertTrue(registry.activeFor(OWNER).isEmpty());
    }

    @Test
    void acceptsPlayerOnlyFormsWithoutTargetHitEffectsAndRejectsInvalidDefinitions() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();

        assertTrue(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "lightning", "", 0.0D, null));
        MiniwyvernOwnerAuraRegistry.Aura lightning = registry.activeFor(OWNER).orElseThrow();
        assertEquals("", lightning.effectId());
        assertEquals(0.0D, lightning.durationSeconds());
        assertTrue(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "nature", "", 0.0D, null));
        registry.clear();
        assertFalse(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "fire", "burn", 0.0D, null));
        assertFalse(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "toxic", "weakness", 6.0D, 1.0D));
        assertFalse(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "fire", "burn", 4.0D,
                null, 0.0D, 0.0D, null, 0.0D, 0.02D, 3_000L));
        assertFalse(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "void", "exposure", 6.0D,
                null, 0.0D, 0.0D, null, 0.0D, 0.01D, 2_999L));
        assertTrue(registry.activeFor(OWNER).isEmpty());
    }

    @Test
    void retainsExpandedAuraFields() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();
        assertTrue(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "void", "exposure", 6.0D,
                null, 0.22D, 0.10D, "RiftWard", 0.05D, 0.01D, 3_000L));

        MiniwyvernOwnerAuraRegistry.Aura aura = registry.activeFor(OWNER).orElseThrow();
        assertEquals(0.22D, aura.targetDamageTakenFraction());
        assertEquals(0.10D, aura.ownerDamageToAffectedFraction());
        assertEquals("RiftWard", aura.wardEffectId());
        assertEquals(0.05D, aura.conditionalWardDamageReductionFraction());
        assertEquals(0.01D, aura.siphonMaximumHealthFraction());
        assertEquals(3_000L, aura.siphonCooldownMs());
    }

    @Test
    void rejectsEveryInvalidExpandedFractionAndWardText() {
        assertFalse(new MiniwyvernOwnerAuraRegistry().update(OWNER, "profile", "lease", UUID.randomUUID(),
                "void", "exposure", 6.0D, null, 1.0D, 0.0D, null, 0.0D, 0.0D, 0L));
        assertFalse(new MiniwyvernOwnerAuraRegistry().update(OWNER, "profile", "lease", UUID.randomUUID(),
                "void", "exposure", 6.0D, null, 0.0D, Double.NaN, null, 0.0D, 0.0D, 0L));
        assertFalse(new MiniwyvernOwnerAuraRegistry().update(OWNER, "profile", "lease", UUID.randomUUID(),
                "void", "exposure", 6.0D, null, 0.0D, 0.0D, null, -0.01D, 0.0D, 0L));
        assertFalse(new MiniwyvernOwnerAuraRegistry().update(OWNER, "profile", "lease", UUID.randomUUID(),
                "void", "exposure", 6.0D, null, 0.0D, 0.0D, " ", 0.0D, 0.0D, 0L));
        assertTrue(new MiniwyvernOwnerAuraRegistry().update(OWNER, "profile", "lease", UUID.randomUUID(),
                "void", "exposure", 6.0D, null, 0.0D, 0.0D, null, 0.0D, 0.0D, 0L));
    }

    @Test
    void retainsDataDefinedToxicWeaknessOnlyForItsAuthoredDuration() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();
        UUID target = UUID.randomUUID();

        registry.recordToxicWeakness(target, "weakness", 0.12D, 6.0D, 1_000L);

        assertEquals(0.12D, registry.activeToxicWeakness(target, 6_999L).orElseThrow().damageReductionFraction());
        assertTrue(registry.activeToxicWeakness(target, 7_000L).isEmpty());
    }

    @Test
    void recordsExpandedTargetProjectionForItsAppliedEffectDuration() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();
        UUID target = UUID.randomUUID();
        assertTrue(registry.update(OWNER, "profile", "lease", UUID.randomUUID(),
                "void", "exposure", 10.0D, 0.20D, 0.22D, 0.10D,
                null, 0.0D, 0.0D, 0L));

        registry.recordTargetAura(target, registry.activeFor(OWNER).orElseThrow(), 6.0D, 1_000L);

        MiniwyvernOwnerAuraRegistry.TargetAura projection =
                registry.activeTargetAura(target, 6_999L).orElseThrow();
        assertEquals("exposure", projection.effectId());
        assertEquals(0.20D, projection.targetOutgoingDamageReductionFraction());
        assertEquals(0.22D, projection.targetDamageTakenFraction());
        assertEquals(0.10D, projection.ownerDamageToAffectedFraction());
        assertEquals(7_000L, projection.expiresAtMs());
        assertEquals(0.20D, registry.activeToxicWeakness(target, 6_999L).orElseThrow()
                .damageReductionFraction());
        assertTrue(registry.activeTargetAura(target, 7_000L).isEmpty());
    }
}
