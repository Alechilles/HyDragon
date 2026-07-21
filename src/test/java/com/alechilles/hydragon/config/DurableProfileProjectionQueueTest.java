package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alechilles.alecstamework.api.CaptureAttemptOutcome;
import com.alechilles.alecstamework.api.CaptureAttemptResolvedEvent;
import com.alechilles.hydragon.encounters.DurableProfileProjectionQueue;
import com.alechilles.hydragon.encounters.FullDragonProfileProjection;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DurableProfileProjectionQueueTest {
    @TempDir Path temporaryDirectory;

    @Test
    void unavailableCapturesSurviveRestartAndReplayInBoundedBatches() throws Exception {
        Path file = temporaryDirectory.resolve("restart.properties");
        HyDragonStateStore firstStore = new HyDragonStateStore(file);
        HyDragonConfigRepository.Snapshot unavailable = new HyDragonConfigRepository.Snapshot(
                Map.of(), Map.of(), Map.of(), Map.of(), List.of("assets-not-ready"));
        DurableProfileProjectionQueue first = queue(firstStore, unavailable);
        UUID firstProfile = UUID.randomUUID();
        UUID secondProfile = UUID.randomUUID();

        assertEquals(FullDragonProfileProjection.Result.UNAVAILABLE,
                first.accept(event(firstProfile, "NordicDrake")));
        assertEquals(FullDragonProfileProjection.Result.UNAVAILABLE,
                first.accept(event(secondProfile, "NordicDrake")));
        assertEquals(2, first.pendingCount());

        HyDragonStateStore restartedStore = new HyDragonStateStore(file);
        DurableProfileProjectionQueue restarted = queue(restartedStore, validSnapshot());
        assertEquals(2, restarted.pendingCount());
        assertEquals(1, restarted.retrySome(1));
        assertEquals(1, restarted.pendingCount());
        assertEquals(1, restartedStore.snapshot().profileExtensions().size());

        HyDragonStateStore restartedAgainStore = new HyDragonStateStore(file);
        DurableProfileProjectionQueue restartedAgain = queue(restartedAgainStore, validSnapshot());
        assertEquals(1, restartedAgain.pendingCount());
        assertEquals(1, restartedAgain.retrySome(1));
        assertEquals(0, restartedAgain.pendingCount());
        assertEquals(2, restartedAgainStore.snapshot().profileExtensions().size());
    }

    private static DurableProfileProjectionQueue queue(
            HyDragonStateStore store,
            HyDragonConfigRepository.Snapshot snapshot) {
        return new DurableProfileProjectionQueue(
                store,
                new FullDragonProfileProjection(store, () -> snapshot),
                Clock.systemUTC());
    }

    private static HyDragonConfigRepository.Snapshot validSnapshot() {
        DragonSpeciesConfig species = new DragonSpeciesConfig();
        species.id = "hydragon:nordic_drake";
        species.wildRoleIds = new String[]{"NordicDrake"};
        species.tamedRoleIdByWildRole = Map.of("NordicDrake", "Tamed_NordicDrake");
        species.difficultyId = "test";
        species.dropListId = "Drop_Test";
        species.mount.mode = "NONE";
        species.capture.minimumStoneTier = 1;
        species.presentation.localizationPrefix = "server.test";
        return new HyDragonConfigRepository.Snapshot(
                Map.of(species.getId(), species), Map.of(), Map.of(), Map.of(), List.of());
    }

    private static CaptureAttemptResolvedEvent event(UUID profileId, String roleId) {
        long now = System.currentTimeMillis();
        return new CaptureAttemptResolvedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), profileId.toString(),
                roleId, "Draconic_Stone", "HyDragonDraconicStone", 1L,
                "HyDragonPolicy", 1L, 5, 1, 10.0, 100.0, 0.9,
                0.0, 1.0, true, CaptureAttemptOutcome.CAPTURED, "captured", now, now);
    }
}
