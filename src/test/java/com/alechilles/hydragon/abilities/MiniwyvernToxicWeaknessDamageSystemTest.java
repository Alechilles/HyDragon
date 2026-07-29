package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MiniwyvernToxicWeaknessDamageSystemTest {
    @Test
    void reducesOutgoingDamageByTheDataDefinedToxicFractionBeforeApplication() {
        assertEquals(88.0F, MiniwyvernToxicWeaknessDamageSystem.reducedAmount(100.0F, 0.12D));
    }
}
