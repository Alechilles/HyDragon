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
    void rejectsInvalidOrNonElementalOwnerAuraDefinitions() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();

        assertFalse(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "lightning", "boon", 4.0D, null));
        assertFalse(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "fire", "burn", 0.0D, null));
        assertFalse(registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "toxic", "weakness", 6.0D, 1.0D));
        assertTrue(registry.activeFor(OWNER).isEmpty());
    }

    @Test
    void retainsDataDefinedToxicWeaknessOnlyForItsAuthoredDuration() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();
        UUID target = UUID.randomUUID();

        registry.recordToxicWeakness(target, "weakness", 0.12D, 6.0D, 1_000L);

        assertEquals(0.12D, registry.activeToxicWeakness(target, 6_999L).orElseThrow().damageReductionFraction());
        assertTrue(registry.activeToxicWeakness(target, 7_000L).isEmpty());
    }
}
