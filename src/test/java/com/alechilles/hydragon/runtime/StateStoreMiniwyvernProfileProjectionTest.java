package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.MutationOutcome;
import com.alechilles.hydragon.persistence.ProfileExtensionRecord;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StateStoreMiniwyvernProfileProjectionTest {
    @TempDir Path temp;

    @Test
    void synchronizesAndReplaysTheCommittedArchetype() throws Exception {
        UUID profile = UUID.randomUUID();
        HyDragonStateStore store = new HyDragonStateStore(temp.resolve("projection.properties"));
        assertEquals(MutationOutcome.APPLIED, store.putProfileExtension(
                ProfileExtensionRecord.soulboundMiniwyvern(
                        profile, "miniwyvern", "neutral", Optional.of("soul-bond"))));
        StateStoreMiniwyvernProfileProjection projection =
                new StateStoreMiniwyvernProfileProjection(store);

        assertEquals(MiniwyvernAttunementService.ProfileProjection.Decision.APPLIED,
                projection.synchronize(profile, "fire", "attune-fire"));
        assertEquals(MiniwyvernAttunementService.ProfileProjection.Decision.ALREADY_APPLIED,
                projection.synchronize(profile, "fire", "attune-fire"));
        assertEquals(Optional.of("fire"),
                store.snapshot().profileExtension(profile).orElseThrow().archetypeId());
    }
}
