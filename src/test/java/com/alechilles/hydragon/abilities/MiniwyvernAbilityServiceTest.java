package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(1, world.presentations);
        assertTrue(world.sawCommittedCooldownBeforeMutation);

        MiniwyvernAbilityService.TickResult replay = service.tick(
                context, Map.of("fire", fireConfig()), world, 1_000L);
        assertTrue(replay.ready());
        assertEquals(0, replay.abilitiesExecuted());
        assertEquals(1, world.projectiles);
        assertEquals(1, world.effects);
        assertEquals(1, world.presentations);
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
    void unsupportedLightningModifierDisablesWholePassiveButKeepsCombatActive() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.ownerModifiersSupported = false;

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context("lightning"), Map.of("lightning", lightningConfig()), world, 1_000L);

        assertTrue(result.ready());
        assertEquals(
                "ready-with-degraded-semantics:passive-ability-disabled:ActionSpeedMultiplier",
                result.reason());
        assertEquals(0, world.effects, "a partial movement-only substitute is forbidden");
        assertEquals(1, world.damageApplications, "the archetype's combat ability remains active");
        assertEquals(0, world.ownerModifierApplications);
    }

    @Test
    void unavailableMovementEffectDoesNotDisableLiveRoleCombat() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.ownerModifiersSupported = false;
        world.passiveModifierEffectSupported = false;

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context("lightning"), Map.of("lightning", lightningConfig()), world, 1_000L);

        assertTrue(result.ready());
        assertEquals(
                "ready-with-degraded-semantics:passive-ability-disabled:"
                        + "ActionSpeedMultiplier+MovementSpeedMultiplier",
                result.reason());
        assertEquals(0, world.effects);
        assertEquals(1, world.damageApplications);
    }

    @Test
    void disablingPassiveRemovesPreviouslyAppliedSourceAndEffect() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);
        MiniwyvernArchetypeConfig lightning = lightningConfig();

        MiniwyvernAbilityService.TickResult supported = service.tick(
                context("lightning"), Map.of("lightning", lightning), world, 1_000L);
        assertEquals("ready", supported.reason());
        assertEquals(1, world.effects);
        assertEquals(1, world.ownerModifierApplications);
        assertTrue(states.current.appliedSourceKeys().stream().anyMatch(key -> key.endsWith(":passive")));

        world.ownerModifiersSupported = false;
        MiniwyvernAbilityService.TickResult disabled = service.tick(
                context("lightning"), Map.of("lightning", lightning), world, 2_000L);

        assertEquals(
                "ready-with-degraded-semantics:passive-ability-disabled:ActionSpeedMultiplier",
                disabled.reason());
        assertEquals(1, world.removedEffects);
        assertEquals(1, world.ownerModifierRemovals);
        assertFalse(states.current.appliedSourceKeys().stream().anyMatch(key -> key.endsWith(":passive")));
        assertFalse(states.current.targetBySourceKey().keySet().stream().anyMatch(key -> key.endsWith(":passive")));
    }

    @Test
    void natureRegenerationHealsAndPresentsOnItsCappedSchedule() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.ownerHealth = new MiniwyvernAbilityWorld.Health(50.0D, 100.0D);
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);

        MiniwyvernAbilityService.TickResult first = service.tick(
                context("nature"), Map.of("nature", natureConfig()), world, 1_000L);
        MiniwyvernAbilityService.TickResult early = service.tick(
                context("nature"), Map.of("nature", natureConfig()), world, 2_000L);

        assertTrue(first.ready());
        assertTrue(early.ready());
        assertEquals(1, world.healApplications);
        assertEquals(1, world.presentations);
        assertEquals(3_000L, states.current.cooldownUntilByAbility().get("nature_regeneration"));
    }

    @Test
    void iceFourthHitStunsThenImmunitySuppressesTheNextCast() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.areaTargets = List.of(
                new MiniwyvernAbilityWorld.Target(ENEMY, null, "world", 3.0D, true));
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);
        MiniwyvernArchetypeConfig config = iceConfig(1);

        for (long now : List.of(1_000L, 7_000L, 13_000L, 19_000L)) {
            assertEquals(1, service.tick(
                    context("ice"), Map.of("ice", config), world, now).abilitiesExecuted());
        }

        assertEquals(4, world.projectiles);
        assertEquals(5, world.effects, "four slows plus the threshold stun");
        assertFalse(states.current.iceBuildupByTarget().containsKey(ENEMY));
        assertEquals(31_000L, states.current.controlImmunityUntilByTarget().get(ENEMY));

        assertEquals(0, service.tick(
                context("ice"), Map.of("ice", config), world, 25_000L).abilitiesExecuted());
        assertEquals(4, world.projectiles, "control immunity must suppress the next eligible cast");
    }

    @Test
    void iceAreaTrackingPrunesExpiredSourcesAndStaleTargets() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);
        MiniwyvernArchetypeConfig config = iceConfig(1);

        for (int index = 0; index < 140; index++) {
            UUID target = UUID.nameUUIDFromBytes(("ice-target-" + index)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            world.areaTargets = List.of(
                    new MiniwyvernAbilityWorld.Target(target, null, "world", 3.0D, true));
            assertEquals(1, service.tick(
                    context("ice"), Map.of("ice", config), world, 1_000L + index * 6_001L)
                    .abilitiesExecuted());
            assertTrue(states.current.iceBuildupByTarget().size()
                    <= MiniwyvernAbilityState.MAX_TRACKED_ICE_TARGETS);
            assertTrue(states.current.appliedSourceKeys().size()
                    <= MiniwyvernAbilityState.MAX_TRACKED_SOURCE_KEYS);
        }

        assertTrue(states.current.iceBuildupByTarget().size() <= 11,
                "one-hit buildup older than the retention window must be pruned");
        assertEquals(1, states.current.appliedSourceKeys().size(),
                "expired per-target source keys must not accumulate across area casts");
        assertEquals(states.current.appliedSourceKeys(),
                states.current.sourceExpiresAtBySourceKey().keySet());
        assertEquals(states.current.iceBuildupByTarget().keySet(),
                states.current.iceTargetUpdatedAtByTarget().keySet());
    }

    @Test
    void unsupportedEffectStackingFailsOnlyTheEffectChannel() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.effectStackingSupported = false;

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context(), Map.of("fire", fireConfig()), world, 1_000L);

        assertTrue(result.ready());
        assertTrue(result.reason().contains("effect-stacking-unavailable:fireball"));
        assertEquals(1, result.abilitiesExecuted(), "the projectile channel still executes");
        assertEquals(1, world.projectiles);
        assertEquals(0, world.effects);
        assertEquals(1, world.presentations);
    }

    @Test
    void voidExecutionVerifiesConfiguredDefenseFloorAndReductionCap() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.boundedDefenseSupported = true;

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context("void"), Map.of("void", voidConfig()), world, 1_000L);

        assertTrue(result.ready());
        assertEquals("ready", result.reason());
        assertEquals(1, world.effects);
        assertEquals(0.12D, world.requestedReduction);
        assertEquals(0.50D, world.minimumDefenseMultiplier);
        assertEquals(0.12D, world.maximumReduction);
    }

    @Test
    void unavailableVoidBoundsSkipOnlyDebuffWhileProjectileRemainsFunctional() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context("void"), Map.of("void", voidConfig()), world, 1_000L);

        assertTrue(result.ready());
        assertTrue(result.reason().contains("void-defense-bounds-unavailable:void_exposure"));
        assertEquals(0, world.effects);
        assertEquals(1, world.projectiles);
        assertEquals(1, result.abilitiesExecuted());
    }

    @Test
    void usesTheLiveCompanionRoleInsteadOfPersistedFormAuthority() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        world.companionRoleId = "Tamed_Wyvern_Mini_Lightning";

        MiniwyvernAbilityService.TickResult result = new MiniwyvernAbilityService(states).tick(
                context(), Map.of("fire", fireConfig(), "lightning", lightningConfig()), world, 1_000L);

        assertTrue(result.ready());
        assertEquals(1, world.damageApplications);
        assertEquals(0, world.projectiles);
        assertEquals("lightning", states.current.formId());
    }

    @Test
    void liveRoleChangeCleansPriorSourcesAndResetsSchedulerState() throws Exception {
        MemoryRepository states = new MemoryRepository();
        FakeWorld world = new FakeWorld(states);
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(states);
        Map<String, MiniwyvernArchetypeConfig> configs = Map.of(
                "fire", fireConfig(), "lightning", lightningConfig());

        assertEquals(1, service.tick(context(), configs, world, 1_000L).abilitiesExecuted());
        world.companionRoleId = "Tamed_Wyvern_Mini_Lightning";

        MiniwyvernAbilityService.TickResult changed = service.tick(context(), configs, world, 2_000L);

        assertTrue(changed.ready());
        assertEquals("lightning", states.current.formId());
        assertTrue(world.removedEffects >= 1, "role swap must clean previous form sources");
        assertFalse(states.current.cooldownUntilByAbility().containsKey("fireball"));
    }

    private static MiniwyvernAbilityService.ProfileContext context() {
        return context("fire");
    }

    private static MiniwyvernAbilityService.ProfileContext context(String ignoredFormId) {
        return new MiniwyvernAbilityService.ProfileContext(
                "profile-1", OWNER, NPC, true, true, true, true);
    }

    private static MiniwyvernArchetypeConfig fireConfig() throws Exception {
        MiniwyvernArchetypeConfig config = construct(MiniwyvernArchetypeConfig.class);
        set(config, "id", "fire");
        set(config, "roleId", "Tamed_Wyvern_Mini_Fire");
        set(config, "particleAndSoundIds", new String[] { "test-presentation" });
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
        set(config, "roleId", "Tamed_Wyvern_Mini_Ice");
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
        set(config, "roleId", "Tamed_Wyvern_Mini_Lightning");
        set(config, "particleAndSoundIds", new String[0]);
        set(config, "passiveEffects", new String[0]);
        set(config, "passiveModifiers", Map.of(
                "MovementSpeedMultiplier", 1.15D,
                "ActionSpeedMultiplier", 1.10D));
        set(config, "passiveModifierEffects", Map.of(
                "MovementSpeedMultiplier", "test-lightning-boon"));
        MiniwyvernArchetypeConfig.Ability ability = construct(MiniwyvernArchetypeConfig.Ability.class);
        set(ability, "id", "lightning_strike");
        set(ability, "trigger", "COMBAT_INTERVAL");
        set(ability, "targetPolicy", "OWNER_HOSTILE_ONLY");
        set(ability, "range", 18.0D);
        set(ability, "cooldownSeconds", 4.0D);
        set(ability, "magnitude", 12.0D);
        set(ability, "durationSeconds", 0.0D);
        set(ability, "stackingPolicy", "SOURCE_REFRESH");
        set(config, "activeAbilities", new MiniwyvernArchetypeConfig.Ability[] { ability });
        set(config, "fallbackBehavior", "BASIC_BITE");
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
    }

    private static MiniwyvernArchetypeConfig natureConfig() throws Exception {
        MiniwyvernArchetypeConfig config = construct(MiniwyvernArchetypeConfig.class);
        set(config, "id", "nature");
        set(config, "roleId", "Tamed_Wyvern_Mini_Nature");
        set(config, "particleAndSoundIds", new String[] { "test-nature-presentation" });
        set(config, "passiveEffects", new String[] { "test-nature-regeneration" });
        set(config, "passiveModifiers", Map.of(
                "RegenerationTickSeconds", 2.0D,
                "MaximumHealFractionPerTick", 0.01D));
        set(config, "fallbackBehavior", "BASIC_BITE");
        set(config, "activeAbilities", new MiniwyvernArchetypeConfig.Ability[0]);
        assertTrue(config.validate().isEmpty(), config.validate().toString());
        return config;
    }

    private static MiniwyvernArchetypeConfig voidConfig() throws Exception {
        MiniwyvernArchetypeConfig config = construct(MiniwyvernArchetypeConfig.class);
        set(config, "id", "void");
        set(config, "roleId", "Tamed_Wyvern_Mini_Void");
        set(config, "particleAndSoundIds", new String[] { "test-void-presentation" });
        set(config, "passiveEffects", new String[0]);
        set(config, "passiveModifiers", Map.of());
        set(config, "fallbackBehavior", "BASIC_BITE");

        MiniwyvernArchetypeConfig.Ability ability = construct(MiniwyvernArchetypeConfig.Ability.class);
        set(ability, "id", "void_exposure");
        set(ability, "trigger", "COMBAT_INTERVAL");
        set(ability, "targetPolicy", "OWNER_HOSTILE_ONLY");
        set(ability, "range", 18.0D);
        set(ability, "cooldownSeconds", 7.0D);
        set(ability, "effectId", "test-void-effect");
        set(ability, "projectileId", "test-void-projectile");
        set(ability, "magnitude", 0.12D);
        set(ability, "maximumStacks", 1);
        set(ability, "minimumDefenseMultiplier", 0.50D);
        set(ability, "maximumReduction", 0.12D);
        set(ability, "durationSeconds", 6.0D);
        set(ability, "stackingPolicy", "SOURCE_REFRESH");
        set(config, "activeAbilities", new MiniwyvernArchetypeConfig.Ability[] { ability });
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

        @Override public LoadResult load(UUID ownerUuid, String profileId) {
            if (unavailable) return LoadResult.unavailable();
            return current == null ? LoadResult.missing() : LoadResult.loaded(current);
        }

        @Override public boolean save(
                UUID ownerUuid,
                String profileId,
                MiniwyvernAbilityState state) {
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
        int ownerModifierRemovals;
        int removedEffects;
        int damageApplications;
        int healApplications;
        int presentations;
        String companionRoleId = "Tamed_Wyvern_Mini_Fire";
        boolean sawCommittedCooldownBeforeMutation;
        boolean ownerModifiersSupported = true;
        boolean passiveModifierEffectSupported = true;
        boolean effectStackingSupported = true;
        boolean boundedDefenseSupported;
        double requestedReduction;
        double minimumDefenseMultiplier;
        double maximumReduction;
        Health ownerHealth = new Health(50.0D, 100.0D);
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
        @Override public Optional<String> companionRoleId() { return Optional.of(companionRoleId); }
        @Override public Health health(UUID entityUuid) {
            return OWNER.equals(entityUuid) ? ownerHealth : new Health(50.0D, 100.0D);
        }
        @Override public boolean applyEffect(UUID entityUuid, String sourceKey, String effectId, double durationSeconds) {
            assertCooldownCommitted();
            effects++;
            effectTargets.add(entityUuid);
            return true;
        }
        @Override public boolean removeEffect(UUID entityUuid, String sourceKey, String effectId) {
            removedEffects++;
            return true;
        }
        @Override public boolean supportsOwnerModifiers(Map<String, Double> modifiers) {
            return ownerModifiersSupported;
        }
        @Override public boolean supportsPassiveModifierEffect(
                String modifierId, double requestedValue, double configuredMaximum, String effectId) {
            return "MovementSpeedMultiplier".equals(modifierId)
                    && passiveModifierEffectSupported
                    && requestedValue <= configuredMaximum && effectId.startsWith("test-");
        }
        @Override public boolean supportsEffectStacking(String effectId, String stackingPolicy, int maximumStacks) {
            return effectStackingSupported && maximumStacks == 1;
        }
        @Override public boolean supportsBoundedDefenseReduction(
                String effectId, double requested, double minimum, double maximum) {
            requestedReduction = requested;
            minimumDefenseMultiplier = minimum;
            maximumReduction = maximum;
            return boundedDefenseSupported;
        }
        @Override public boolean applyOwnerModifiers(UUID ownerUuid, String sourceKey, Map<String, Double> modifiers,
                                                     double durationSeconds) {
            ownerModifierApplications++;
            return true;
        }
        @Override public boolean removeOwnerModifiers(UUID ownerUuid, String sourceKey) {
            ownerModifierRemovals++;
            return true;
        }
        @Override public int emitPresentation(UUID entityUuid, List<String> particleAndSoundIds) {
            presentations += particleAndSoundIds.size();
            return particleAndSoundIds.size();
        }
        @Override public boolean launchProjectile(UUID sourceUuid, UUID targetUuid, String projectileId) {
            assertCooldownCommitted();
            projectiles++;
            projectileTargets.add(targetUuid);
            return true;
        }
        @Override public boolean dealDamage(UUID sourceUuid, UUID targetUuid, double amount) {
            damageApplications++;
            return true;
        }
        @Override public boolean heal(UUID entityUuid, double amount) {
            healApplications++;
            return true;
        }
        @Override public boolean areAllies(UUID ownerUuid, UUID targetUuid) { return OWNER.equals(targetOwner); }

        private void assertCooldownCommitted() {
            sawCommittedCooldownBeforeMutation |= states.current != null
                    && states.current.cooldownUntilByAbility().getOrDefault("fireball", 0L) > 1_000L;
        }
    }
}
