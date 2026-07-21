package com.alechilles.hydragon.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class HyDragonEncounterServerRuntimeTest {
    @Test
    void sourceCandidatesPreferExactCompositeIdentity() {
        List<String> candidates = HyDragonEncounterServerRuntime.groundingSourceCandidates(
                "Projectile_Config_Ice_Ball", "Weapon_Staff_Frost", "Ice");

        assertEquals(
                "projectile:Projectile_Config_Ice_Ball+item:Weapon_Staff_Frost+damage_cause:Ice",
                candidates.getFirst());
        assertEquals(
                "projectile:Projectile_Config_Ice_Ball+item:Weapon_Staff_Frost",
                candidates.get(1));
        assertFalse(candidates.contains("hydragon:lure_hit"));
        assertFalse(candidates.contains("hydragon:stagger_hit"));
    }

    @Test
    void missingRuntimeIdentityDoesNotInventGroundingAliases() {
        assertEquals(List.of(),
                HyDragonEncounterServerRuntime.groundingSourceCandidates(null, null, null));
    }
}
