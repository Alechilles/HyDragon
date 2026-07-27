package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioral coverage for the shared poller with independently optional work. */
final class HyDragonRuntimePollCycleTest {

    @Test
    void bondedOnlyCycleRunsSagaRecoveryWithoutAbsentEncounterOrAbilityWork() {
        List<String> ran = new ArrayList<>();
        List<HyDragonRuntimePollCycle.Failure> failures = new ArrayList<>();
        HyDragonRuntimePollCycle cycle = new HyDragonRuntimePollCycle(
                null, null, () -> ran.add("saga"), failures::add);

        cycle.run();

        assertEquals(List.of("saga"), ran);
        assertTrue(cycle.hasWork());
        assertFalse(cycle.encountersEnabled());
        assertTrue(failures.isEmpty());
    }

    @Test
    void onePollFailureDoesNotPreventOtherInstalledFeatureWork() {
        List<String> ran = new ArrayList<>();
        List<HyDragonRuntimePollCycle.Failure> failures = new ArrayList<>();
        HyDragonRuntimePollCycle cycle = new HyDragonRuntimePollCycle(
                () -> { throw new IllegalStateException("encounter tick failed"); },
                () -> ran.add("abilities"),
                () -> ran.add("saga"),
                failures::add);

        cycle.run();

        assertEquals(List.of("abilities", "saga"), ran);
        assertEquals(List.of(HyDragonRuntimePollCycle.Work.ENCOUNTERS),
                failures.stream().map(HyDragonRuntimePollCycle.Failure::work).toList());
        assertTrue(cycle.encountersEnabled());
    }

    @Test
    void emptyCycleReportsNoWork() {
        HyDragonRuntimePollCycle cycle = new HyDragonRuntimePollCycle(
                null, null, null, ignored -> { });

        cycle.run();

        assertFalse(cycle.hasWork());
        assertFalse(cycle.encountersEnabled());
    }
}
