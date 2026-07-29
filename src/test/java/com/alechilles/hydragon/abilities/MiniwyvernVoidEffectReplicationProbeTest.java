package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.EffectOp;
import com.hypixel.hytale.protocol.EntityEffectUpdate;
import com.hypixel.hytale.protocol.EntityEffectsUpdate;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.server.core.modules.entity.livingentity.LivingEntityEffectClearChangesSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MiniwyvernVoidEffectReplicationProbeTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000017");

    @Test
    void reportsOnlyTheRequestedEffectsQueuedAddAndRemoveOperations() {
        EntityEffectsUpdate effects = new EntityEffectsUpdate(new EntityEffectUpdate[] {
                new EntityEffectUpdate(EffectOp.Add, 17, 6.0F, false, true, "void"),
                new EntityEffectUpdate(EffectOp.Add, 99, 4.0F, false, true, "other"),
                new EntityEffectUpdate(EffectOp.Remove, 17, 0.0F, false, true, "void")
        });

        MiniwyvernVoidEffectReplicationProbe.PacketEvidence evidence =
                MiniwyvernVoidEffectReplicationProbe.inspectQueuedUpdates(
                        17, new ComponentUpdate[] {effects});

        assertEquals(1, evidence.adds());
        assertEquals(1, evidence.removes());
        assertEquals(6.0F, evidence.latestAddRemainingSeconds());
    }

    @Test
    void consumesEachObservedTargetExactlyOnceAfterReplication() {
        MiniwyvernVoidEffectReplicationProbe probe = new MiniwyvernVoidEffectReplicationProbe();

        probe.observe(TARGET, 17);

        assertEquals(new MiniwyvernVoidEffectReplicationProbe.Observation(17), probe.consume(TARGET));
        assertNull(probe.consume(TARGET));
    }

    @Test
    void observesOnlySuccessfulVoidEffectApplications() {
        MiniwyvernVoidEffectReplicationProbe probe = new MiniwyvernVoidEffectReplicationProbe();

        probe.observeApplication(TARGET, 17, "fire", true);
        assertNull(probe.consume(TARGET));
        probe.observeApplication(TARGET, 17, "void", false);
        assertNull(probe.consume(TARGET));
        probe.observeApplication(TARGET, 17, "void", true);

        assertEquals(new MiniwyvernVoidEffectReplicationProbe.Observation(17), probe.consume(TARGET));
    }

    @Test
    void inspectsPacketsAfterEffectReplicationAndBeforeChangesAreCleared() {
        MiniwyvernVoidEffectReplicationSystem system =
                new MiniwyvernVoidEffectReplicationSystem(new MiniwyvernVoidEffectReplicationProbe());

        Map<Class<?>, Order> dependencies = system.getDependencies().stream()
                .filter(SystemDependency.class::isInstance)
                .map(SystemDependency.class::cast)
                .collect(Collectors.toMap(SystemDependency::getSystemClass, SystemDependency::getOrder));

        assertEquals(EntityTrackerSystems.QUEUE_UPDATE_GROUP, system.getGroup());
        assertEquals(Order.AFTER, dependencies.get(EntityTrackerSystems.EffectControllerSystem.class));
        assertEquals(Order.BEFORE, dependencies.get(LivingEntityEffectClearChangesSystem.class));
    }
}
