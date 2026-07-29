package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MiniwyvernOwnerAuraEffectSchedulerTest {
    @Test
    void waitsForTheWorldTaskQueueBeforeApplyingAnOwnerHitEffect() {
        UUID targetUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        MiniwyvernOwnerAuraRegistry.Aura aura = new MiniwyvernOwnerAuraRegistry.Aura(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "profile", "lease",
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "void", "HyDragon_Miniwyvern_Void_Exposure", 3.0D, 0.0D);
        ArrayDeque<Runnable> worldTasks = new ArrayDeque<>();
        List<UUID> appliedTargets = new ArrayList<>();

        MiniwyvernOwnerAuraEffectScheduler.schedule(
                worldTasks::addLast, targetUuid, aura,
                (queuedTarget, queuedAura) -> appliedTargets.add(queuedTarget));

        assertTrue(appliedTargets.isEmpty());
        assertEquals(1, worldTasks.size());

        worldTasks.removeFirst().run();

        assertEquals(List.of(targetUuid), appliedTargets);
    }
}
