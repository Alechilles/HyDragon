package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MiniwyvernAuraSiphonDamageSystemTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NPC = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void acceptsVoidSiphonAtExactThreeSecondCadence() {
        MiniwyvernAuraSiphonDamageSystem system =
                new MiniwyvernAuraSiphonDamageSystem(new MiniwyvernOwnerAuraRegistry());
        MiniwyvernOwnerAuraRegistry.Aura aura = voidAura(0.01D, 3_000L);

        assertTrue(system.trySiphon(OWNER, aura, 1_000L));
        assertFalse(system.trySiphon(OWNER, aura, 3_999L));
        assertTrue(system.trySiphon(OWNER, aura, 4_000L));
    }

    @Test
    void clearsCooldownWhenTheAuraRegistryIsCleared() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();
        MiniwyvernAuraSiphonDamageSystem system = new MiniwyvernAuraSiphonDamageSystem(registry);
        MiniwyvernOwnerAuraRegistry.Aura aura = voidAura(0.005D, 3_000L);

        assertTrue(system.trySiphon(OWNER, aura, 1_000L));
        registry.clear();
        assertTrue(system.trySiphon(OWNER, aura, 1_001L));
    }

    @Test
    void clearsOnlyTheOwnerCooldownWhenItsLeaseIsClearedAndReactivated() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();
        MiniwyvernAuraSiphonDamageSystem system = new MiniwyvernAuraSiphonDamageSystem(registry);
        assertTrue(registry.update(OWNER, "profile", "lease-one", NPC,
                "void", "exposure", 6.0D, null,
                0.0D, 0.0D, "RiftWard", 0.0D, 0.01D, 3_000L));
        MiniwyvernOwnerAuraRegistry.Aura first = registry.activeFor(OWNER).orElseThrow();

        assertTrue(system.trySiphon(OWNER, first, 1_000L));
        assertTrue(registry.clear(OWNER, "profile", "lease-one"));
        assertTrue(registry.update(OWNER, "profile", "lease-two", NPC,
                "void", "exposure", 6.0D, null,
                0.0D, 0.0D, "RiftWard", 0.0D, 0.01D, 3_000L));

        assertTrue(system.trySiphon(OWNER, registry.activeFor(OWNER).orElseThrow(), 1_001L));
    }

    @Test
    void fireAndToxicAurasHaveNoSiphonFraction() {
        MiniwyvernAuraSiphonDamageSystem system =
                new MiniwyvernAuraSiphonDamageSystem(new MiniwyvernOwnerAuraRegistry());

        assertFalse(system.trySiphon(OWNER, aura("fire", 0.0D), 1_000L));
        assertFalse(system.trySiphon(OWNER, aura("toxic", 0.0D), 1_000L));
        assertEquals(0.01D, voidAura(0.01D, 3_000L).siphonMaximumHealthFraction());
    }

    @Test
    void requiresAPlayerDamageHitAgainstTheMatchingActiveTargetStatus() {
        MiniwyvernOwnerAuraRegistry.Aura aura = voidAura(0.01D, 3_000L);

        assertFalse(MiniwyvernAuraSiphonDamageSystem.shouldSiphon(
                false, 10.0F, true, false, false, false, false, true, aura));
        assertFalse(MiniwyvernAuraSiphonDamageSystem.shouldSiphon(
                false, 10.0F, true, false, false, false, true, false, aura));
        assertTrue(MiniwyvernAuraSiphonDamageSystem.shouldSiphon(
                false, 10.0F, true, false, false, false, true, true, aura));
    }

    private static MiniwyvernOwnerAuraRegistry.Aura voidAura(double fraction, long cooldownMs) {
        return aura("void", fraction, cooldownMs);
    }

    private static MiniwyvernOwnerAuraRegistry.Aura aura(String formId, double fraction) {
        return aura(formId, fraction, 0L);
    }

    private static MiniwyvernOwnerAuraRegistry.Aura aura(
            String formId, double fraction, long cooldownMs) {
        return new MiniwyvernOwnerAuraRegistry.Aura(
                OWNER, "profile", "lease", NPC, formId, "exposure", 6.0D,
                0.0D, 0.0D, 0.0D, "RiftWard", 0.0D, fraction, cooldownMs);
    }
}
