package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MiniwyvernVoidExposureDamageSystemTest {
    @Test
    void usesTheStrongestActiveVoidExposureWithoutStacking() {
        assertEquals(110.0F, MiniwyvernVoidExposureDamageSystem.increasedAmount(100.0F, false, true));
        assertEquals(112.0F, MiniwyvernVoidExposureDamageSystem.increasedAmount(100.0F, true, true));
        assertEquals(122.0F, MiniwyvernVoidExposureDamageSystem.increasedAmount(100.0F, 0.22D));
        assertEquals(115.0F, MiniwyvernVoidExposureDamageSystem.increasedAmount(100.0F, 0.15D));
        assertEquals(110.0F, MiniwyvernVoidExposureDamageSystem.increasedAmount(100.0F, 0.0D, true));
        assertEquals(100.0F, MiniwyvernVoidExposureDamageSystem.increasedAmount(100.0F, false, false));
    }

    @Test
    void excludesCancelledInvalidSelfBlockedHealingAndEnvironmentDamage() {
        assertEquals(false, MiniwyvernVoidExposureDamageSystem.shouldModify(true, 100.0F, true, false, false, false));
        assertEquals(false, MiniwyvernVoidExposureDamageSystem.shouldModify(false, Float.NEGATIVE_INFINITY, true, false, false, false));
        assertEquals(false, MiniwyvernVoidExposureDamageSystem.shouldModify(false, -1.0F, true, false, false, false));
        assertEquals(false, MiniwyvernVoidExposureDamageSystem.shouldModify(false, 100.0F, false, false, false, false));
        assertEquals(false, MiniwyvernVoidExposureDamageSystem.shouldModify(false, 100.0F, true, true, false, false));
        assertEquals(false, MiniwyvernVoidExposureDamageSystem.shouldModify(false, 100.0F, true, false, true, false));
        assertEquals(false, MiniwyvernVoidExposureDamageSystem.shouldModify(false, 100.0F, true, false, false, true));
        assertEquals(true, MiniwyvernVoidExposureDamageSystem.shouldModify(false, 100.0F, true, false, false, false));
    }
}
