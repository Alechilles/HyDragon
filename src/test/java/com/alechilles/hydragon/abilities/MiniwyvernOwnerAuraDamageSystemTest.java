package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MiniwyvernOwnerAuraDamageSystemTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void appliesToPositivePlayerDamageBeforeTheMiniwyvernFlagsTheTargetHostile() {
        MiniwyvernOwnerAuraRegistry registry = new MiniwyvernOwnerAuraRegistry();
        registry.update(OWNER, "profile", "lease", UUID.randomUUID(), "fire", "burn", 4.0D, null);
        MiniwyvernOwnerAuraDamageSystem system = new MiniwyvernOwnerAuraDamageSystem(registry);

        assertTrue(system.shouldApply(OWNER, true, false, 1.0F));
        assertFalse(system.shouldApply(OWNER, false, false, 1.0F));
        assertFalse(system.shouldApply(OWNER, true, true, 1.0F));
        assertFalse(system.shouldApply(OWNER, true, false, 0.0F));
    }

    @Test
    void rejectsInvalidEntitySourcesBeforeTheGlobalDamagePathReadsTheirComponents() {
        assertFalse(MiniwyvernOwnerAuraDamageSystem.isLiveRef(
                new Ref<EntityStore>(null, ComponentRegistry.UNASSIGNED_INDEX)));
        assertFalse(MiniwyvernOwnerAuraDamageSystem.isLiveRef(null));
    }

    @Test
    void routesOwnerHitEffectsThroughTheLiveStoreForEffectControllerReplication() throws IOException {
        String system = source();

        assertTrue(system.contains(
                "store.getComponent(target, EffectControllerComponent.getComponentType())"));
        assertTrue(system.contains("OverlapBehavior.OVERWRITE, store)"));
    }

    @Test
    void runsBeforeTheEntityEffectTrackerSoOwnerHitEffectsReplicateInTheSameTick() throws IOException {
        String system = source();

        assertTrue(system.contains("new SystemDependency<>(Order.BEFORE, EntityTrackerSystems.EffectControllerSystem.class)"));
    }

    private static String source() throws IOException {
        Path source = Path.of(System.getProperty("hydragon.project.basedir"),
                "src/main/java/com/alechilles/hydragon/abilities/MiniwyvernOwnerAuraDamageSystem.java");
        return Files.readString(source);
    }
}
