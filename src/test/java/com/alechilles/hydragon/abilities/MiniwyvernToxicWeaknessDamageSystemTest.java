package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MiniwyvernToxicWeaknessDamageSystemTest {
    @Test
    void usesTheStrongestActiveToxicReductionWithoutStacking() {
        assertEquals(90.0F, MiniwyvernToxicWeaknessDamageSystem.reducedAmount(100.0F, false, true));
        assertEquals(88.0F, MiniwyvernToxicWeaknessDamageSystem.reducedAmount(100.0F, true, true));
        assertEquals(100.0F, MiniwyvernToxicWeaknessDamageSystem.reducedAmount(100.0F, false, false));
    }

    @Test
    void excludesCancelledInvalidSelfBlockedHealingAndEnvironmentDamage() {
        assertEquals(false, MiniwyvernToxicWeaknessDamageSystem.shouldModify(true, 100.0F, true, false, false, false));
        assertEquals(false, MiniwyvernToxicWeaknessDamageSystem.shouldModify(false, Float.NaN, true, false, false, false));
        assertEquals(false, MiniwyvernToxicWeaknessDamageSystem.shouldModify(false, 0.0F, true, false, false, false));
        assertEquals(false, MiniwyvernToxicWeaknessDamageSystem.shouldModify(false, 100.0F, false, false, false, false));
        assertEquals(false, MiniwyvernToxicWeaknessDamageSystem.shouldModify(false, 100.0F, true, true, false, false));
        assertEquals(false, MiniwyvernToxicWeaknessDamageSystem.shouldModify(false, 100.0F, true, false, true, false));
        assertEquals(false, MiniwyvernToxicWeaknessDamageSystem.shouldModify(false, 100.0F, true, false, false, true));
        assertEquals(true, MiniwyvernToxicWeaknessDamageSystem.shouldModify(false, 100.0F, true, false, false, false));
    }
}
