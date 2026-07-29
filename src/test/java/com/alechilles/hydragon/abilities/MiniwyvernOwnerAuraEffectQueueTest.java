package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MiniwyvernOwnerAuraEffectQueueTest {
    private static final String WORLD = "flat_world";
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void coalescesRepeatedHitsUntilTheDedicatedEffectPhaseDrainsThem() {
        MiniwyvernOwnerAuraEffectQueue queue = new MiniwyvernOwnerAuraEffectQueue();
        MiniwyvernOwnerAuraRegistry.Aura first = aura("void", "HyDragon_Miniwyvern_Void_Exposure", 6.0D);
        MiniwyvernOwnerAuraRegistry.Aura refreshed = aura("void", "HyDragon_Miniwyvern_Void_Exposure", 8.0D);

        queue.submit(WORLD, TARGET, first);
        queue.submit(WORLD, TARGET, refreshed);

        assertEquals(List.of(refreshed), queue.drain(WORLD, TARGET));
        assertTrue(queue.drain(WORLD, TARGET).isEmpty());
    }

    @Test
    void retainsDifferentEffectsQueuedForTheSameTarget() {
        MiniwyvernOwnerAuraEffectQueue queue = new MiniwyvernOwnerAuraEffectQueue();
        MiniwyvernOwnerAuraRegistry.Aura fire = aura("fire", "HyDragon_Miniwyvern_Fire_Burn", 4.0D);
        MiniwyvernOwnerAuraRegistry.Aura voidAura = aura("void", "HyDragon_Miniwyvern_Void_Exposure", 6.0D);

        queue.submit(WORLD, TARGET, voidAura);
        queue.submit(WORLD, TARGET, fire);

        assertEquals(List.of(fire, voidAura), queue.drain(WORLD, TARGET));
    }

    @Test
    void appliesQueuedEffectsAfterDamageCaptureAndBeforeEntityEffectReplication() {
        MiniwyvernOwnerAuraEffectSystem system = new MiniwyvernOwnerAuraEffectSystem(
                new MiniwyvernOwnerAuraEffectQueue(),
                new MiniwyvernOwnerAuraRegistry(),
                new MiniwyvernVoidEffectLifetimeSystem(),
                new MiniwyvernVoidEffectReplicationProbe());

        Map<Class<?>, Order> dependencies = system.getDependencies().stream()
                .filter(SystemDependency.class::isInstance)
                .map(SystemDependency.class::cast)
                .collect(Collectors.toMap(SystemDependency::getSystemClass, SystemDependency::getOrder));

        assertEquals(Order.AFTER, dependencies.get(MiniwyvernOwnerAuraDamageSystem.class));
        assertEquals(Order.BEFORE, dependencies.get(EntityTrackerSystems.EffectControllerSystem.class));
    }

    private static MiniwyvernOwnerAuraRegistry.Aura aura(String formId, String effectId, double durationSeconds) {
        return new MiniwyvernOwnerAuraRegistry.Aura(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "profile", "lease",
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                formId, effectId, durationSeconds, 0.0D);
    }
}
