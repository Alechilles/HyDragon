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
    void rejectsCancelledHealingBlockedAndNonpositiveDamage() {
        assertFalse(MiniwyvernConditionalWardDamageSystem.shouldModify(true, 100.0F, false, false));
        assertFalse(MiniwyvernConditionalWardDamageSystem.shouldModify(false, 0.0F, false, false));
        assertFalse(MiniwyvernConditionalWardDamageSystem.shouldModify(false, 100.0F, true, false));
        assertFalse(MiniwyvernConditionalWardDamageSystem.shouldModify(false, 100.0F, false, true));
        assertTrue(MiniwyvernConditionalWardDamageSystem.shouldModify(false, 100.0F, false, false));
    }
}
