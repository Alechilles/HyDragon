package com.alechilles.hydragon.config;

import com.alechilles.alecstamework.api.CaptureAttemptOutcome;
import com.alechilles.alecstamework.api.CaptureAttemptReplayEvidence;
import com.alechilles.alecstamework.api.CaptureAttemptResolvedEvent;
import com.alechilles.alecstamework.api.CaptureChanceMode;
import com.alechilles.alecstamework.api.CaptureSourceConsumption;
import com.alechilles.alecstamework.api.CaptureSuccessDisposition;
import java.util.UUID;

/** Deterministic replay-complete Tamework capture events for HyDragon contract tests. */
final class CaptureEventFixtures {
    private CaptureEventFixtures() {
    }

    static CaptureAttemptResolvedEvent capture(
            String profileId,
            String roleId,
            CaptureAttemptOutcome outcome) {
        return capture(
                profileId,
                roleId,
                outcome,
                "HyDragonDraconicStone",
                CaptureSourceConsumption.RESOLVED_ATTEMPT,
                CaptureSuccessDisposition.TAME_AND_COMMAND_LINK);
    }

    static CaptureAttemptResolvedEvent capture(
            String profileId,
            String roleId,
            CaptureAttemptOutcome outcome,
            String spawnerConfigId,
            CaptureSourceConsumption sourceConsumption,
            CaptureSuccessDisposition successDisposition) {
        long now = 1_000L;
        UUID actorUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        CaptureAttemptReplayEvidence replay = new CaptureAttemptReplayEvidence(
                UUID.randomUUID().toString(),
                actorUuid,
                null,
                null,
                "default",
                roleId,
                1L,
                new CaptureAttemptReplayEvidence.Lifecycle(
                        "ACTIVE", "LIVE_ENTITY", targetUuid.toString(), "default", 2L),
                new CaptureAttemptReplayEvidence.Formula(
                        CaptureChanceMode.PROBABILITY,
                        0.42D,
                        0.1D,
                        0.05D,
                        0.95D,
                        0.0D,
                        1.0D,
                        0.0D,
                        5,
                        "test-requirements",
                        1L,
                        0.1D,
                        null,
                        sourceConsumption,
                        successDisposition,
                        10.0D,
                        100.0D),
                new CaptureAttemptReplayEvidence.Source(
                        0, 1, "before-fingerprint", 0, null, "test-receipt"),
                null,
                now);
        return new CaptureAttemptResolvedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                actorUuid,
                targetUuid,
                profileId,
                roleId,
                "Draconic_Stone",
                spawnerConfigId,
                1L,
                "HyDragonPolicy",
                1L,
                5,
                1,
                10.0D,
                100.0D,
                0.9D,
                0.0D,
                outcome == CaptureAttemptOutcome.CAPTURED ? 1.0D : 0.5D,
                outcome == CaptureAttemptOutcome.CAPTURED,
                outcome,
                outcome == CaptureAttemptOutcome.CAPTURED ? "captured" : "failed-roll",
                now,
                now,
                replay);
    }
}
