package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MiniwyvernConditionalWardDamageSystemTest {
    @Test
    void reducesValidDamageByExactlyTheConditionalFraction() {
        assertEquals(95.0F, MiniwyvernConditionalWardDamageSystem.reducedAmount(100.0F, 0.05D));
        assertEquals(100.0F, MiniwyvernConditionalWardDamageSystem.reducedAmount(100.0F, 0.0D));
    }

    @Test
    void selectsLightningStaticWardReductionForEveryAuthoredTier() {
        assertEquals(0.05D, MiniwyvernConditionalWardDamageSystem.reductionFraction(lightningAura(0.05D)));
        assertEquals(0.08D, MiniwyvernConditionalWardDamageSystem.reductionFraction(lightningAura(0.08D)));
        assertEquals(0.12D, MiniwyvernConditionalWardDamageSystem.reductionFraction(lightningAura(0.12D)));
        assertEquals(0.15D, MiniwyvernConditionalWardDamageSystem.reductionFraction(lightningAura(0.15D)));
        assertEquals(95.0F, MiniwyvernConditionalWardDamageSystem.reducedAmount(
                100.0F, MiniwyvernConditionalWardDamageSystem.reductionFraction(lightningAura(0.05D))));
    }

    @Test
    void rejectsCancelledHealingBlockedAndNonpositiveDamage() {
        assertFalse(MiniwyvernConditionalWardDamageSystem.shouldModify(true, 100.0F, false, false));
        assertFalse(MiniwyvernConditionalWardDamageSystem.shouldModify(false, 0.0F, false, false));
        assertFalse(MiniwyvernConditionalWardDamageSystem.shouldModify(false, 100.0F, true, false));
        assertFalse(MiniwyvernConditionalWardDamageSystem.shouldModify(false, 100.0F, false, true));
        assertTrue(MiniwyvernConditionalWardDamageSystem.shouldModify(false, 100.0F, false, false));
    }

    private static MiniwyvernOwnerAuraRegistry.Aura lightningAura(double fraction) {
        return new MiniwyvernOwnerAuraRegistry.Aura(
                java.util.UUID.randomUUID(), "profile", "lease", java.util.UUID.randomUUID(),
                "lightning", "", 0.0D, 0.0D, 0.0D, 0.0D, "StaticWard", 0.0D,
                0.0D, 0L, "StormBoon", 0.0D, 0.0D, 0.0D, fraction);
    }
}
