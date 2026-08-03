package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.hydragon.config.MiniwyvernArchetypeConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract for retained Java owner-passive handling after combat moves to role assets. */
class MiniwyvernAbilityServiceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NPC = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ENEMY = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void leavesCombatExecutionToRoleAssetsWhileRefreshingOwnerPassives() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context(), Map.of("fire", fireConfig()), world, 1_000L);

        assertTrue(result.ready());
        assertEquals(0, result.abilitiesExecuted());
        assertEquals(1, world.effects, "the retained owner passive should refresh");
        assertEquals(0, world.projectiles);
        assertEquals(0, world.damageApplications);
        assertEquals(0, world.enemyEffects);
    }

    @Test
    void natureRegenerationRemainsJavaOwnedAndCooldownGated() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.roleId = "Tamed_Wyvern_Mini_Nature";
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);

        assertTrue(service.tick(context(), Map.of("nature", natureConfig()), world, 1_000L).ready());
        assertTrue(service.tick(context(), Map.of("nature", natureConfig()), world, 2_000L).ready());

        assertEquals(1, world.heals);
        assertEquals(0, world.projectiles);
        assertEquals(0, world.damageApplications);
        assertEquals(3_000L, states.current.cooldownUntilByAbility().get("nature_regeneration"));
    }

    @Test
    void doesNotRewriteAnActivePassiveLeaseBeforeItNeedsRenewal() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);

        assertTrue(service.tick(context(), Map.of("fire", fireConfig()), world, 1_000L).ready());
        assertTrue(service.tick(context(), Map.of("fire", fireConfig()), world, 2_000L).ready());

        assertEquals(1, states.saves,
                "an already-valid passive lease must not synchronously rewrite bonded extension state every second");
        assertEquals(1, world.effects,
                "the existing effect duration keeps the passive active until its renewal window");
    }

    @Test
    void keepsOwnerPassivesAndAttackAuraLockedUntilEssenceBondIsPurchased() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.essenceBondPurchased = false;
        MiniwyvernOwnerAuraRegistry auras = new MiniwyvernOwnerAuraRegistry();

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states, auras).tick(
                context(), Map.of("fire", fireConfig()), world, 1_000L);

        assertTrue(result.ready());
        assertEquals(0, world.effects, "a locked bond talent must not apply its owner passive");
        assertTrue(auras.activeFor(OWNER).isEmpty(),
                "a locked bond talent must not register an owner-hit aura");
    }

    @Test
    void deactivationCleansOwnerPassiveSourcesWithoutTargetedCombatCleanup() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        MiniwyvernOwnerAuraRegistry auras = new MiniwyvernOwnerAuraRegistry();
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states, auras);
        Map<String, MiniwyvernArchetypeConfig> archetypes = Map.of("fire", fireConfig());
        service.tick(context(), archetypes, world, 1_000L);
        assertEquals("test-fire-owner-aura", auras.activeFor(OWNER).orElseThrow().effectId());

        MiniwyvernAbilityService.TickResult result = service.deactivate(context(), archetypes, world, 2_000L);

        assertFalse(result.ready());
        assertTrue(world.removedEffects > 0);
        assertEquals(1, world.ownerModifierRemovals);
        assertTrue(auras.activeFor(OWNER).isEmpty());
        assertTrue(states.current.appliedSourceKeys().isEmpty());
        assertEquals(0, world.projectiles);
        assertEquals(0, world.damageApplications);
    }

    @Test
    void legacyCombatSchedulerStateConvergesWithoutTouchingAssetOwnedTargets() throws Exception {
        MemoryRepository states = new MemoryRepository();
        states.current = new MiniwyvernAbilityState(
                MiniwyvernAbilityState.SCHEMA_VERSION, "fire", Map.of("legacy_fireball", 10_000L),
                Map.of(ENEMY, 25.0D), Map.of(), Map.of(ENEMY, 1_000L),
                java.util.Set.of("hydragon:mini:profile-1:fire:legacy_fireball"),
                Map.of("hydragon:mini:profile-1:fire:legacy_fireball", ENEMY),
                Map.of("hydragon:mini:profile-1:fire:legacy_fireball", 10_000L), 1_000L);
        FakeWorld world = new FakeWorld(states);

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context(), Map.of("fire", fireConfig()), world, 2_000L);

        assertTrue(result.ready());
        assertFalse(states.current.cooldownUntilByAbility().containsKey("legacy_fireball"));
        assertTrue(states.current.iceBuildupByTarget().isEmpty());
        assertTrue(states.current.controlImmunityUntilByTarget().isEmpty());
        assertFalse(states.current.appliedSourceKeys().stream().anyMatch(key -> key.contains("legacy_fireball")));
        assertEquals(0, world.enemyEffects);
        assertEquals(0, world.projectiles);
        assertEquals(0, world.damageApplications);
    }

    @Test
    void unavailableStateFailsClosedBeforeAnyOwnerOrCombatMutation() throws Exception {
        MemoryRepository states = new MemoryRepository();
        states.unavailable = true;
        FakeWorld world = new FakeWorld(states);

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context(), Map.of("fire", fireConfig()), world, 1_000L);

        assertFalse(result.ready());
        assertEquals("ability-state-unavailable", result.reason());
        assertEquals(0, world.effects);
        assertEquals(0, world.projectiles);
        assertEquals(0, world.damageApplications);
    }

    @Test
    void derivesPurchasedEssenceBondUpgradesInAssetOrder() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.purchasedTalents.add("EssenceBond");
        world.purchasedTalents.add("FireCapstone");
        MiniwyvernArchetypeConfig config = fireConfigWithUpgrades();
        MiniwyvernOwnerAuraRegistry auras = new MiniwyvernOwnerAuraRegistry();

        assertTrue(new MiniwyvernAbilityService(states, auras).tick(
                context(), Map.of("fire", config), world, 1_000L).ready());

        MiniwyvernOwnerAuraRegistry.Aura aura = auras.activeFor(OWNER).orElseThrow();
        assertEquals(6.0D, aura.durationSeconds());
        assertEquals(0.10D, aura.ownerDamageToAffectedFraction());
        assertEquals("FlameWard", aura.wardEffectId());
    }

    @Test
    void derivesVoidCapstoneSiphonValues() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.roleId = "Tamed_Wyvern_Mini_Void";
        world.purchasedTalents.add("EssenceBond");
        world.purchasedTalents.add("VoidCapstone");
        MiniwyvernArchetypeConfig config = voidConfigWithUpgrades();
        MiniwyvernOwnerAuraRegistry auras = new MiniwyvernOwnerAuraRegistry();

        assertTrue(new MiniwyvernAbilityService(states, auras).tick(
                context(), Map.of("void", config), world, 1_000L).ready());

        MiniwyvernOwnerAuraRegistry.Aura aura = auras.activeFor(OWNER).orElseThrow();
        assertEquals(6.0D, aura.durationSeconds());
        assertEquals(0.10D, aura.ownerDamageToAffectedFraction());
        assertEquals(0.01D, aura.siphonMaximumHealthFraction());
        assertEquals(3_000L, aura.siphonCooldownMs());
    }

    @Test
    void invalidEssenceBondUpgradeDefinitionsFailValidation() throws Exception {
        MiniwyvernArchetypeConfig config = fireConfig();
        MiniwyvernArchetypeConfig.EssenceBondAura essenceBondAura = construct(
                MiniwyvernArchetypeConfig.EssenceBondAura.class);
        MiniwyvernArchetypeConfig.Upgrade first = construct(MiniwyvernArchetypeConfig.Upgrade.class);
        MiniwyvernArchetypeConfig.Upgrade duplicate = construct(MiniwyvernArchetypeConfig.Upgrade.class);
        set(first, "talentId", "FirePressure");
        set(duplicate, "talentId", " FirePressure ");
        set(duplicate, "ownerDamageToAffectedFraction", Double.NaN);
        set(essenceBondAura, "upgrades", new MiniwyvernArchetypeConfig.Upgrade[] { first, duplicate });
        set(config, "essenceBondAura", essenceBondAura);

        List<String> errors = config.validate();
        assertTrue(errors.stream().anyMatch(error -> error.contains("duplicate")), errors.toString());
        assertTrue(errors.stream().anyMatch(error -> error.contains("finite")), errors.toString());

        set(duplicate, "ownerDamageToAffectedFraction", 1.0D);
        errors = config.validate();
        assertTrue(errors.stream().anyMatch(error -> error.contains("fraction")), errors.toString());
    }

    private static MiniwyvernAbilityService.ProfileContext context() {
        return new MiniwyvernAbilityService.ProfileContext("profile-1", OWNER, NPC, true, true, true, true);
    }

    private static MiniwyvernArchetypeConfig fireConfig() throws Exception {
        MiniwyvernArchetypeConfig config = base("fire", "Tamed_Wyvern_Mini_Fire");
        set(config, "requiredTalentId", "EssenceBond");
        set(config, "passiveEffects", new String[] { "test-fire-aura" });
        set(config, "passiveModifiers", Map.of("JumpMultiplier", 1.10D));
        MiniwyvernArchetypeConfig.OwnerAttackAura aura = construct(MiniwyvernArchetypeConfig.OwnerAttackAura.class);
        set(aura, "effectId", "test-fire-owner-aura");
        set(aura, "durationSeconds", 4.0D);
        set(config, "ownerAttackAura", aura);
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
    }

    private static MiniwyvernArchetypeConfig natureConfig() throws Exception {
        MiniwyvernArchetypeConfig config = base("nature", "Tamed_Wyvern_Mini_Nature");
        set(config, "passiveEffects", new String[] { "test-nature-regeneration" });
        set(config, "passiveModifiers", Map.of(
                "RegenerationTickSeconds", 2.0D, "MaximumHealFractionPerTick", 0.01D));
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
    }

    private static MiniwyvernArchetypeConfig fireConfigWithUpgrades() throws Exception {
        MiniwyvernArchetypeConfig config = fireConfig();
        MiniwyvernArchetypeConfig.EssenceBondAura aura = construct(
                MiniwyvernArchetypeConfig.EssenceBondAura.class);
        MiniwyvernArchetypeConfig.Upgrade pressure = construct(MiniwyvernArchetypeConfig.Upgrade.class);
        MiniwyvernArchetypeConfig.Upgrade capstone = construct(MiniwyvernArchetypeConfig.Upgrade.class);
        set(pressure, "talentId", "FirePressure");
        set(pressure, "targetEffectId", "FireBurnPlus");
        set(pressure, "targetDurationSeconds", 5.0D);
        set(capstone, "talentId", "FireCapstone");
        set(capstone, "targetEffectId", "FireBurnCapstone");
        set(capstone, "targetDurationSeconds", 6.0D);
        set(capstone, "ownerDamageToAffectedFraction", 0.10D);
        set(capstone, "wardEffectId", "FlameWard");
        set(aura, "upgrades", new MiniwyvernArchetypeConfig.Upgrade[] { pressure, capstone });
        set(config, "essenceBondAura", aura);
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
    }

    private static MiniwyvernArchetypeConfig voidConfigWithUpgrades() throws Exception {
        MiniwyvernArchetypeConfig config = base("void", "Tamed_Wyvern_Mini_Void");
        set(config, "requiredTalentId", "EssenceBond");
        MiniwyvernArchetypeConfig.OwnerAttackAura root = construct(
                MiniwyvernArchetypeConfig.OwnerAttackAura.class);
        set(root, "effectId", "test-void-owner-aura");
        set(root, "durationSeconds", 6.0D);
        set(config, "ownerAttackAura", root);
        MiniwyvernArchetypeConfig.EssenceBondAura aura = construct(
                MiniwyvernArchetypeConfig.EssenceBondAura.class);
        MiniwyvernArchetypeConfig.Upgrade capstone = construct(MiniwyvernArchetypeConfig.Upgrade.class);
        set(capstone, "talentId", "VoidCapstone");
        set(capstone, "targetDamageTakenFraction", 0.22D);
        set(capstone, "ownerDamageToAffectedFraction", 0.10D);
        set(capstone, "siphonMaximumHealthFraction", 0.01D);
        set(capstone, "siphonCooldownMs", 3_000L);
        set(aura, "upgrades", new MiniwyvernArchetypeConfig.Upgrade[] { capstone });
        set(config, "essenceBondAura", aura);
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
    }

    private static MiniwyvernArchetypeConfig base(String id, String roleId) throws Exception {
        MiniwyvernArchetypeConfig config = construct(MiniwyvernArchetypeConfig.class);
        set(config, "id", id);
        set(config, "roleId", roleId);
        set(config, "particleAndSoundIds", new String[0]);
        set(config, "passiveEffects", new String[0]);
        set(config, "passiveModifiers", Map.of());
        set(config, "fallbackBehavior", "BASIC_BITE");
        return config;
    }

    private static <T> T construct(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class MemoryRepository implements MiniwyvernAbilityStateRepository {
        MiniwyvernAbilityState current;
        boolean unavailable;
        int saves;

        @Override public LoadResult load(UUID ownerUuid, String profileId) {
            return unavailable ? LoadResult.unavailable()
                    : current == null ? LoadResult.missing() : LoadResult.loaded(current);
        }

        @Override public boolean save(UUID ownerUuid, String profileId, MiniwyvernAbilityState state) {
            if (unavailable) return false;
            saves++;
            current = state;
            return true;
        }
    }

    private static final class FakeWorld implements MiniwyvernAbilityWorld {
        private final MemoryRepository states;
        int effects;
        int enemyEffects;
        int removedEffects;
        int ownerModifierRemovals;
        int projectiles;
        int damageApplications;
        int heals;
        String roleId = "Tamed_Wyvern_Mini_Fire";
        boolean essenceBondPurchased = true;
        final java.util.Set<String> purchasedTalents = new HashSet<>();

        private FakeWorld(MemoryRepository states) { this.states = states; }
        @Override public boolean isWorldThread() { return true; }
        @Override public String worldName() { return "world"; }
        @Override public Optional<Target> owner() { return Optional.of(target(OWNER)); }
        @Override public Optional<Target> companion() { return Optional.of(target(NPC)); }
        @Override public Optional<Target> hostileTarget(double maximumRange) { return Optional.of(target(ENEMY)); }
        @Override public Optional<String> companionRoleId() { return Optional.of(roleId); }
        @Override public Health health(UUID entityUuid) { return new Health(50.0D, 100.0D); }
        @Override public boolean applyEffect(UUID entityUuid, String sourceKey, String effectId, double durationSeconds) {
            effects++;
            if (ENEMY.equals(entityUuid)) enemyEffects++;
            return true;
        }
        @Override public boolean removeEffect(UUID entityUuid, String sourceKey, String effectId) {
            removedEffects++;
            return true;
        }
        @Override public boolean supportsOwnerModifiers(Map<String, Double> modifiers) { return true; }
        @Override public boolean applyOwnerModifiers(UUID ownerUuid, String sourceKey, Map<String, Double> modifiers, double durationSeconds) { return true; }
        @Override public boolean removeOwnerModifiers(UUID ownerUuid, String sourceKey) {
            ownerModifierRemovals++;
            return true;
        }
        @Override public boolean launchProjectile(UUID sourceUuid, UUID targetUuid, String projectileId) {
            projectiles++;
            return true;
        }
        @Override public boolean dealDamage(UUID sourceUuid, UUID targetUuid, double amount) {
            damageApplications++;
            return true;
        }
        @Override public boolean heal(UUID entityUuid, double amount) {
            heals++;
            return true;
        }
        @Override public boolean areAllies(UUID ownerUuid, UUID targetUuid) { return false; }
        @Override public boolean hasPurchasedTalent(String talentId) {
            return ("EssenceBond".equals(talentId) && essenceBondPurchased)
                    || purchasedTalents.contains(talentId);
        }
        private static Target target(UUID id) { return new Target(id, null, "world", 0.0D, true); }
    }
}
