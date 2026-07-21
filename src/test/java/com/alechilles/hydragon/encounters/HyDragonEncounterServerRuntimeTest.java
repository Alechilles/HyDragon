package com.alechilles.hydragon.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HyDragonEncounterServerRuntimeTest {
    @Test
    void sourceCandidatesPreferExactCompositeIdentity() {
        List<String> candidates = HyDragonEncounterServerRuntime.groundingSourceCandidates(
                "Ice_Ball", "Weapon_Staff_Frost", "Ice");

        assertEquals(
                "projectile:Ice_Ball+item:Weapon_Staff_Frost+damage_cause:Ice",
                candidates.getFirst());
        assertEquals(
                "projectile:Ice_Ball+item:Weapon_Staff_Frost",
                candidates.get(1));
        assertFalse(candidates.contains("hydragon:lure_hit"));
        assertFalse(candidates.contains("hydragon:stagger_hit"));
    }

    @Test
    void missingRuntimeIdentityDoesNotInventGroundingAliases() {
        assertEquals(List.of(),
                HyDragonEncounterServerRuntime.groundingSourceCandidates(null, null, null));
    }

    @Test
    void realProjectileDamageShapeRetainsShooterAndProjectileReferences() {
        Ref<EntityStore> shooter = new Ref<>(null, 10);
        Ref<EntityStore> projectile = new Ref<>(null, 11);
        Damage damage = new Damage(new Damage.ProjectileSource(shooter, projectile), 0, 12.0F);

        HyDragonEncounterServerRuntime.ProjectileDamageRefs refs =
                HyDragonEncounterServerRuntime.projectileDamageRefs(damage);

        assertSame(shooter, refs.shooterRef());
        assertSame(projectile, refs.projectileRef());
    }

    @Test
    void ordinaryEntityDamageIsNotAProjectileGroundingHit() {
        Ref<EntityStore> attacker = new Ref<>(null, 10);
        Damage damage = new Damage(new Damage.EntitySource(attacker), 0, 12.0F);

        assertNull(HyDragonEncounterServerRuntime.projectileDamageRefs(damage));
        assertNull(HyDragonEncounterServerRuntime.resolveGroundingSource(
                Set.of("projectile:Ice_Ball+item:Weapon_Staff_Frost"),
                new HyDragonEncounterServerRuntime.GroundingHitEvidence(
                        "Ice_Ball", "Weapon_Staff_Frost", "Projectile", false)));
    }

    @Test
    void configuredProjectileAndItemResolveAsExactGroundingSource() {
        String resolved = HyDragonEncounterServerRuntime.resolveGroundingSource(
                Set.of(
                        "projectile:Ice_Ball+item:Weapon_Staff_Frost",
                        "projectile:Spear_Thorium+item:Weapon_Spear_Thorium"),
                new HyDragonEncounterServerRuntime.GroundingHitEvidence(
                        "Ice_Ball", "Weapon_Staff_Frost", "Projectile", true));

        assertEquals("projectile:Ice_Ball+item:Weapon_Staff_Frost", resolved);
    }

    @Test
    void wrongOrSpoofedProjectileCannotResolveGroundingSource() {
        Set<String> configured = Set.of("projectile:Ice_Ball+item:Weapon_Staff_Frost");

        assertNull(HyDragonEncounterServerRuntime.resolveGroundingSource(
                configured,
                new HyDragonEncounterServerRuntime.GroundingHitEvidence(
                        "Fire_Ball", "Weapon_Staff_Frost", "Projectile", true)));
        assertNull(HyDragonEncounterServerRuntime.resolveGroundingSource(
                configured,
                new HyDragonEncounterServerRuntime.GroundingHitEvidence(
                        "Ice_Ball", "Weapon_Staff_Frost", "Projectile", false)));
    }

    @Test
    void wrongHeldItemCannotResolveConfiguredCompositeSource() {
        assertNull(HyDragonEncounterServerRuntime.resolveGroundingSource(
                Set.of("projectile:Ice_Ball+item:Weapon_Staff_Frost"),
                new HyDragonEncounterServerRuntime.GroundingHitEvidence(
                        "Ice_Ball", "Weapon_Staff_Fire", "Projectile", true)));
    }
}
