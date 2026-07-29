package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MiniwyvernOwnerAuraEffectSystemTest {
    @Test
    void activeModelVfxEffectRequiresAFreshClientLifecycle() {
        assertTrue(MiniwyvernOwnerAuraEffectSystem.requiresModelVfxRestart(
                true, "HyDragon_Void_Debuff"));
        assertFalse(MiniwyvernOwnerAuraEffectSystem.requiresModelVfxRestart(
                false, "HyDragon_Void_Debuff"));
        assertFalse(MiniwyvernOwnerAuraEffectSystem.requiresModelVfxRestart(true, null));
        assertFalse(MiniwyvernOwnerAuraEffectSystem.requiresModelVfxRestart(true, "  "));
    }
}
