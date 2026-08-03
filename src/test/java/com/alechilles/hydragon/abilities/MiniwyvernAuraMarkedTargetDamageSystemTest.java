package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MiniwyvernAuraMarkedTargetDamageSystemTest {
    @Test
    void appliesTheLiveOwnerBonus() {
        assertEquals(110.0F,
                MiniwyvernAuraMarkedTargetDamageSystem.increasedOwnerDamage(100.0F, 0.10D));
        assertEquals(105.0F,
                MiniwyvernAuraMarkedTargetDamageSystem.increasedOwnerDamage(100.0F, 0.05D));
    }

    @Test
    void excludesCancelledInvalidSelfBlockedHealingAndUnmarkedDamage() {
        assertFalse(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                true, 100.0F, true, false, false, false, true, true));
        assertFalse(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                false, Float.NaN, true, false, false, false, true, true));
        assertFalse(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                false, 0.0F, true, false, false, false, true, true));
        assertFalse(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                false, 100.0F, false, false, false, false, true, true));
        assertFalse(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                false, 100.0F, true, true, false, false, true, true));
        assertFalse(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                false, 100.0F, true, false, true, false, true, true));
        assertFalse(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                false, 100.0F, true, false, false, true, true, true));
        assertFalse(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                false, 100.0F, true, false, false, false, false, true));
        assertFalse(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                false, 100.0F, true, false, false, false, true, false));
        assertTrue(MiniwyvernAuraMarkedTargetDamageSystem.shouldModify(
                false, 100.0F, true, false, false, false, true, true));
    }
}
