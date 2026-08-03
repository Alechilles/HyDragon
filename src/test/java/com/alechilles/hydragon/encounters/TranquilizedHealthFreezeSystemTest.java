package com.alechilles.hydragon.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TranquilizedHealthFreezeSystemTest {
    @Test
    void runsAfterTheRegisteredNpcRegenerationSystem() {
        Map<Class<?>, Order> dependencies = new TranquilizedHealthFreezeSystem().getDependencies().stream()
                .filter(SystemDependency.class::isInstance)
                .map(SystemDependency.class::cast)
                .collect(Collectors.toMap(SystemDependency::getSystemClass, SystemDependency::getOrder));

        assertEquals(Order.AFTER, dependencies.get(NPCPlugin.NPCEntityRegenerateStatsSystem.class));
    }
}
