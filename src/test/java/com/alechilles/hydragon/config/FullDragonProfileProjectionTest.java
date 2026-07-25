package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alechilles.alecstamework.api.CaptureAttemptOutcome;
import com.alechilles.alecstamework.api.CaptureAttemptResolvedEvent;
import com.alechilles.alecstamework.api.CaptureSourceConsumption;
import com.alechilles.alecstamework.api.CaptureSuccessDisposition;
import com.alechilles.hydragon.encounters.FullDragonProfileProjection;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ProfileKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FullDragonProfileProjectionTest {
    @TempDir Path temporaryDirectory;

    @Test
    void committedCaptureProjectsOneIdempotentFullDragonRecord() throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(temporaryDirectory.resolve("state.properties"));
        HyDragonConfigRepository.Snapshot configs = snapshot(species("hydragon:nordic_drake", "NordicDrake"));
        FullDragonProfileProjection projection = new FullDragonProfileProjection(store, () -> configs);
        UUID profileId = UUID.randomUUID();
        CaptureAttemptResolvedEvent event = event(profileId.toString(), "NordicDrake", CaptureAttemptOutcome.CAPTURED);

        assertEquals(FullDragonProfileProjection.Result.APPLIED, projection.project(event));
        assertEquals(FullDragonProfileProjection.Result.ALREADY_APPLIED, projection.project(event));
        assertEquals(ProfileKind.FULL_DRAGON,
                store.snapshot().profileExtensions().get(profileId).kind());
        assertEquals("hydragon:nordic_drake",
                store.snapshot().profileExtensions().get(profileId).speciesId());
    }

    @Test
    void nonCaptureMissingProfileAndAmbiguousRoleFailClosed() throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(temporaryDirectory.resolve("state.properties"));
        DragonSpeciesConfig first = species("hydragon:first", "SharedRole");
        DragonSpeciesConfig second = species("hydragon:second", "SharedRole");
        FullDragonProfileProjection projection = new FullDragonProfileProjection(
                store, () -> snapshot(first, second));

        assertEquals(FullDragonProfileProjection.Result.IGNORED,
                projection.project(event(UUID.randomUUID().toString(), "SharedRole", CaptureAttemptOutcome.FAILED_ROLL)));
        assertEquals(FullDragonProfileProjection.Result.INVALID,
                projection.project(event(null, "SharedRole", CaptureAttemptOutcome.CAPTURED)));
        assertEquals(FullDragonProfileProjection.Result.AMBIGUOUS,
                projection.project(event(UUID.randomUUID().toString(), "SharedRole", CaptureAttemptOutcome.CAPTURED)));
        assertEquals(0, store.snapshot().profileExtensions().size());
    }

    @Test
    void capturedProfileRequiresExactHyDragonTameAndLinkEvidence() throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(
                temporaryDirectory.resolve("capture-evidence.properties"));
        FullDragonProfileProjection projection = new FullDragonProfileProjection(
                store, () -> snapshot(species("hydragon:nordic_drake", "NordicDrake")));
        String profileId = UUID.randomUUID().toString();

        assertEquals(FullDragonProfileProjection.Result.INVALID, projection.project(
                CaptureEventFixtures.capture(
                        profileId,
                        "NordicDrake",
                        CaptureAttemptOutcome.CAPTURED,
                        "AnotherModsCaptureConfig",
                        CaptureSourceConsumption.RESOLVED_ATTEMPT,
                        CaptureSuccessDisposition.TAME_AND_COMMAND_LINK)));
        assertEquals(FullDragonProfileProjection.Result.INVALID, projection.project(
                CaptureEventFixtures.capture(
                        profileId,
                        "NordicDrake",
                        CaptureAttemptOutcome.CAPTURED,
                        "HyDragonDraconicStone",
                        CaptureSourceConsumption.SUCCESS_ONLY,
                        CaptureSuccessDisposition.TAME_AND_COMMAND_LINK)));
        assertEquals(FullDragonProfileProjection.Result.INVALID, projection.project(
                CaptureEventFixtures.capture(
                        profileId,
                        "NordicDrake",
                        CaptureAttemptOutcome.CAPTURED,
                        "HyDragonDraconicStone",
                        CaptureSourceConsumption.RESOLVED_ATTEMPT,
                        CaptureSuccessDisposition.CAPTURED_ITEM)));
        assertEquals(0, store.snapshot().profileExtensions().size());
    }

    private static HyDragonConfigRepository.Snapshot snapshot(DragonSpeciesConfig... species) {
        return new HyDragonConfigRepository.Snapshot(
                java.util.Arrays.stream(species).collect(java.util.stream.Collectors.toMap(
                        DragonSpeciesConfig::getId, value -> value)),
                Map.of(), Map.of(), List.of());
    }

    private static DragonSpeciesConfig species(String id, String roleId) {
        DragonSpeciesConfig species = new DragonSpeciesConfig();
        species.id = id;
        species.wildRoleIds = new String[]{roleId};
        species.tamedRoleIdByWildRole = Map.of(roleId, "Tamed_" + roleId);
        species.difficultyId = "test";
        species.dropListId = "Drop_Test";
        species.mount.mode = "NONE";
        species.capture.minimumStoneTier = 1;
        species.presentation.localizationPrefix = "server.test";
        return species;
    }

    private static CaptureAttemptResolvedEvent event(
            String profileId, String roleId, CaptureAttemptOutcome outcome) {
        return CaptureEventFixtures.capture(profileId, roleId, outcome);
    }
}
