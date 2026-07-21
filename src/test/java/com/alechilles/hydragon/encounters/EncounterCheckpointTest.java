package com.alechilles.hydragon.encounters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EncounterCheckpointTest {
    @Test
    void groundingBuildupRoundTrips() {
        EncounterCheckpoint expected = new EncounterCheckpoint(EncounterPhase.GROUNDING, 37.5D);
        assertEquals(expected, EncounterCheckpoint.decode(expected.encode()));
    }

    @Test
    void nonGroundingPhaseCannotCarryBuildup() {
        assertThrows(IllegalArgumentException.class,
                () -> new EncounterCheckpoint(EncounterPhase.AERIAL, 1.0D));
    }
}
