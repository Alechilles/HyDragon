package com.alechilles.hydragon.npc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AirborneTargetTrackerTest {
    private final AirborneTargetTracker tracker = new AirborneTargetTracker();

    @Test
    void shortJumpsDoNotAccumulateIntoATakeoff() {
        assertFalse(tracker.update("target", true, false, 0.5));
        assertFalse(tracker.update("target", false, false, 0.1));
        assertFalse(tracker.update("target", true, false, 0.5));
        assertTrue(tracker.update("target", true, false, 0.4));
    }

    @Test
    void flyingDragonKeepsFlyingWhenItAcquiresAnAirborneTarget() {
        assertTrue(tracker.update("target", true, true, 0.05));
    }

    @Test
    void touchdownMustPersistBeforeLanding() {
        assertTrue(tracker.update("target", true, true, 0.1));
        assertTrue(tracker.update("target", false, true, 0.6));
        assertTrue(tracker.update("target", true, true, 0.1));
        assertTrue(tracker.update("target", false, true, 0.6));
        assertFalse(tracker.update("target", false, true, 0.5));
    }

    @Test
    void changingOrLosingTargetDiscardsPendingTakeoff() {
        assertFalse(tracker.update("first", true, false, 0.5));
        assertFalse(tracker.update("second", true, false, 0.5));
        assertFalse(tracker.update(null, false, false, 0.1));
        assertFalse(tracker.update("second", true, false, 0.5));
    }

    @Test
    void leavingCombatDiscardsPendingTakeoff() {
        assertFalse(tracker.update("target", true, false, 0.5));
        tracker.reset();
        assertFalse(tracker.update("target", true, false, 0.5));
    }
}
