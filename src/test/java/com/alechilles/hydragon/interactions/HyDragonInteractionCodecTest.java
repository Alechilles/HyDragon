package com.alechilles.hydragon.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class HyDragonInteractionCodecTest {
    @Test
    void exposesOnlyTheSoulBondItemInteraction() {
        assertEquals("HyDragonSoulBond", HyDragonSoulBondInteraction.TYPE_ID);
        assertEquals(java.util.Optional.of("HyDragon_Dragon_Horn"),
                new HyDragonSoulBondInteraction("test").requiredAccessItemId());
    }
}
