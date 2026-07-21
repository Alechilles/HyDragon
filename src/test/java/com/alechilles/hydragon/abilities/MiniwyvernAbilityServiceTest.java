package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.hydragon.config.MiniwyvernArchetypeConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MiniwyvernAbilityServiceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NPC = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ENEMY = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ENEMY_TWO = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ENEMY_THREE = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void commitsCooldownBeforeMutationAndDoesNotDuplicateCast() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);
        MiniwyvernAbilityService.ProfileContext context = context();

        MiniwyvernAbilityService.TickResult first = service.tick(
                context, Map.of("fire", fireConfig()), world, 1_000L);
        assertTrue(first.ready());
        assertEquals(1, first.abilitiesExecuted());
        assertEquals(1, world.projectiles);
        assertEquals(1, world.effects);
        assertTrue(world.sawCommittedCooldownBeforeMutation);
        assertEquals("Wyvern_Mini_Fire", world.appearanceId);

        MiniwyvernAbilityService.TickResult replay = service.tick(
                context, Map.of("fire", fireConfig()), world, 1_000L);
        assertTrue(replay.ready());
        assertEquals(0, replay.abilitiesExecuted());
        assertEquals(1, world.projectiles);
        assertEquals(1, world.effects);
    }

    @Test
    void rejectsSameOwnerTargetWithoutConsumingCooldown() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.targetOwner = OWNER;
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);

        MiniwyvernAbilityService.TickResult result = service.tick(
                context(), Map.of("fire", fireConfig()), world, 1_000L);

        assertTrue(result.ready());
        assertEquals(0, result.abilitiesExecuted());
        assertEquals(0, world.projectiles);
        assertFalse(states.current.cooldownUntilByAbility().containsKey("fireball"));
    }

    @Test
    void unavailableStateFailsClosedWithoutWorldMutation() throws Exception {
        MemoryRepository states = new MemoryRepository();
        states.unavailable = true;
        FakeWorld world = new FakeWorld(states);

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context(), Map.of("fire", fireConfig()), world, 1_000L);

        assertFalse(result.ready());
        assertEquals("ability-state-unavailable", result.reason());
        assertEquals(0, world.projectiles);
    }

    @Test
    void iceAreaAbilityAffectsMultipleTargetsButHonorsConfiguredMaximum() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.areaTargets = List.of(
                new MiniwyvernAbilityWorld.Target(ENEMY, null, "world", 3.0D, true),
                new MiniwyvernAbilityWorld.Target(ENEMY_TWO, null, "world", 4.0D, true),
                new MiniwyvernAbilityWorld.Target(ENEMY_THREE, null, "world", 5.0D, true));

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context("ice"), Map.of("ice", iceConfig(2)), world, 1_000L);

        assertTrue(result.ready());
        assertEquals(1, result.abilitiesExecuted(), "one area cast consumed one shared cooldown");
        assertEquals(List.of(ENEMY, ENEMY_TWO), world.projectileTargets);
        assertEquals(List.of(ENEMY, ENEMY_TWO), world.effectTargets);
        assertEquals(2, states.current.iceBuildupByTarget().size());
        assertTrue(states.current.appliedSourceKeys().stream().anyMatch(key -> key.endsWith(":" + ENEMY)));
        assertTrue(states.current.appliedSourceKeys().stream().anyMatch(key -> key.endsWith(":" + ENEMY_TWO)));
        assertFalse(states.current.appliedSourceKeys().stream().anyMatch(key -> key.endsWith(":" + ENEMY_THREE)));
    }

    @Test
    void unsupportedRequiredOwnerModifiersFailClosedBeforeAppearanceOrAbilities() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.ownerModifiersSupported = false;

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context("lightning"), Map.of("lightning", lightningConfig()), world, 1_000L);

        assertFalse(result.ready());
        assertTrue(result.reason().startsWith("owner-modifier-capability-unavailable:"));
        assertNull(world.appearanceId);
        assertEquals(0, world.projectiles);
        assertEquals(0, world.effects);
        assertEquals(0, world.ownerModifierApplications);
    }

    private static MiniwyvernAbilityService.ProfileContext context() {
        return context("fire");
    }

    private static MiniwyvernAbilityService.ProfileContext context(String archetypeId) {
        return new MiniwyvernAbilityService.ProfileContext(
                "profile-1", OWNER, NPC, archetypeId, true, true, true, true);
    }

    private static MiniwyvernArchetypeConfig fireConfig() throws Exception {
        MiniwyvernArchetypeConfig config = construct(MiniwyvernArchetypeConfig.class);
        set(config, "id", "fire");
        set(config, "essenceSemanticId", "fire");
        set(config, "essenceItemId", "Draconic_Essence_Fire");
        set(config, "appearanceId", "Wyvern_Mini_Fire");
        set(config, "particleAndSoundIds", new String[0]);
        set(config, "passiveEffects", new String[0]);
        set(config, "passiveModifiers", Map.of());
        set(config, "fallbackBehavior", "BASIC_BITE");

        MiniwyvernArchetypeConfig.Ability ability = construct(MiniwyvernArchetypeConfig.Ability.class);
        set(ability, "id", "fireball");
        set(ability, "trigger", "COMBAT_INTERVAL");
        set(ability, "targetPolicy", "OWNER_HOSTILE_ONLY");
        set(ability, "range", 18.0D);
        set(ability, "cooldownSeconds", 2.5D);
        set(ability, "effectId", "test-burn");
        set(ability, "projectileId", "test-projectile");
        set(ability, "magnitude", 10.0D);
        set(ability, "maximumStacks", 1);
        set(ability, "durationSeconds", 4.0D);
        set(ability, "stackingPolicy", "SOURCE_REFRESH");
        set(config, "activeAbilities", new MiniwyvernArchetypeConfig.Ability[] { ability });
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
    }

    private static MiniwyvernArchetypeConfig iceConfig(int maximumTargets) throws Exception {
        MiniwyvernArchetypeConfig config = construct(MiniwyvernArchetypeConfig.class);
        set(config, "id", "ice");
        set(config, "essenceSemanticId", "ice");
        set(config, "essenceItemId", "Draconic_Essence_Ice");
        set(config, "appearanceId", "Wyvern_Mini_Ice");
        set(config, "particleAndSoundIds", new String[0]);
        set(config, "passiveEffects", new String[0]);
        set(config, "passiveModifiers", Map.of());
        set(config, "fallbackBehavior", "BASIC_BITE");

        MiniwyvernArchetypeConfig.Ability ability = construct(MiniwyvernArchetypeConfig.Ability.class);
        set(ability, "id", "ice_buildup");
        set(ability, "trigger", "COMBAT_INTERVAL");
        set(ability, "targetPolicy", "OWNER_HOSTILE_AREA");
        set(ability, "range", 10.0D);
        set(ability, "maximumTargets", maximumTargets);
        set(ability, "cooldownSeconds", 6.0D);
        set(ability, "effectId", "test-ice-slow");
        set(ability, "projectileId", "test-ice-projectile");
        set(ability, "magnitude", 0.0D);
        set(ability, "buildupPerHit", 25.0D);
        set(ability, "buildupThreshold", 100.0D);
        set(ability, "buildupCap", 100.0D);
        set(ability, "controlEffectId", "test-ice-stun");
        set(ability, "controlImmunitySeconds", 12.0D);
        set(ability, "durationSeconds", 4.0D);
        set(ability, "stackingPolicy", "SOURCE_REFRESH");
        set(config, "activeAbilities", new MiniwyvernArchetypeConfig.Ability[] { ability });
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
    }

    private static MiniwyvernArchetypeConfig lightningConfig() throws Exception {
        MiniwyvernArchetypeConfig config = construct(MiniwyvernArchetypeConfig.class);
        set(config, "id", "lightning");
        set(config, "essenceSemanticId", "lightning");
        set(config, "essenceItemId", "Draconic_Essence_Lightning");
        set(config, "appearanceId", "Wyvern_Mini_Lightning");
        set(config, "particleAndSoundIds", new String[0]);
        set(config, "passiveEffects", new String[0]);
        set(config, "passiveModifiers", Map.of(
                "MovementSpeedMultiplier", 1.15D,
                "ActionSpeedMultiplier", 1.10D));
        set(config, "activeAbilities", new MiniwyvernArchetypeConfig.Ability[0]);
        set(config, "fallbackBehavior", "BASIC_BITE");
        assertTrue(config.validate().isEmpty(), config.validate().toString());
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

        @Override public LoadResult load(String profileId) {
            if (unavailable) return LoadResult.unavailable();
            return current == null ? LoadResult.missing() : LoadResult.loaded(current);
        }

        @Override public boolean save(String profileId, MiniwyvernAbilityState state) {
            if (unavailable) return false;
            current = state;
            return true;
        }
    }

    private static final class FakeWorld implements MiniwyvernAbilityWorld {
        private final MemoryRepository states;
        UUID targetOwner;
        List<Target> areaTargets = List.of();
        int effects;
        int projectiles;
        int ownerModifierApplications;
        String appearanceId;
        boolean sawCommittedCooldownBeforeMutation;
        boolean ownerModifiersSupported = true;
        final List<UUID> projectileTargets = new ArrayList<>();
        final List<UUID> effectTargets = new ArrayList<>();

        private FakeWorld(MemoryRepository states) { this.states = states; }

        @Override public boolean isWorldThread() { return true; }
        @Override public String worldName() { return "world"; }
        @Override public Optional<Target> owner() {
            return Optional.of(new Target(OWNER, OWNER, "world", 2.0D, true));
        }
        @Override public Optional<Target> companion() {
            return Optional.of(new Target(NPC, OWNER, "world", 0.0D, true));
        }
        @Override public Optional<Target> hostileTarget(double maximumRange) {
            return Optional.of(new Target(ENEMY, targetOwner, "world", 5.0D, true));
        }
        @Override public List<Target> hostileTargets(double maximumRange, int maximumTargets) {
            return areaTargets;
        }
        @Override public boolean synchronizeAppearance(UUID entityUuid, String requestedAppearanceId) {
            if (!NPC.equals(entityUuid)) return false;
            appearanceId = requestedAppearanceId;
            return true;
        }
        @Override public Health health(UUID entityUuid) { return new Health(50.0D, 100.0D); }
        @Override public boolean applyEffect(UUID entityUuid, String sourceKey, String effectId, double durationSeconds) {
            assertCooldownCommitted();
            effects++;
            effectTargets.add(entityUuid);
            return true;
        }
        @Override public boolean removeEffect(UUID entityUuid, String sourceKey, String effectId) { return true; }
        @Override public boolean supportsOwnerModifiers(Map<String, Double> modifiers) {
            return ownerModifiersSupported;
        }
        @Override public boolean applyOwnerModifiers(UUID ownerUuid, String sourceKey, Map<String, Double> modifiers,
                                                     double durationSeconds) {
            ownerModifierApplications++;
            return true;
        }
        @Override public boolean removeOwnerModifiers(UUID ownerUuid, String sourceKey) { return true; }
        @Override public boolean launchProjectile(UUID sourceUuid, UUID targetUuid, String projectileId) {
            assertCooldownCommitted();
            projectiles++;
            projectileTargets.add(targetUuid);
            return true;
        }
        @Override public boolean dealDamage(UUID sourceUuid, UUID targetUuid, double amount) { return true; }
        @Override public boolean heal(UUID entityUuid, double amount) { return true; }
        @Override public boolean areAllies(UUID ownerUuid, UUID targetUuid) { return OWNER.equals(targetOwner); }

        private void assertCooldownCommitted() {
            sawCommittedCooldownBeforeMutation |= states.current != null
                    && states.current.cooldownUntilByAbility().getOrDefault("fireball", 0L) > 1_000L;
        }
    }
}
