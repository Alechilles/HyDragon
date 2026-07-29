package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MiniwyvernOwnerAuraDamageSystemTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void appliesOnlyPositivePlayerDamageAgainstTheLiveMiniwyvernsHostileTarget() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();
        registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "fire", "burn", 4.0D, null);
        MiniwyvernOwnerAuraDamageSystem system = new MiniwyvernOwnerAuraDamageSystem(registry);

        assertTrue(system.shouldApply(OWNER, true, true, false, 1.0F));
        assertFalse(system.shouldApply(OWNER, false, true, false, 1.0F));
        assertFalse(system.shouldApply(OWNER, true, false, false, 1.0F));
        assertFalse(system.shouldApply(OWNER, true, true, true, 1.0F));
        assertFalse(system.shouldApply(OWNER, true, true, false, 0.0F));
    }
}
