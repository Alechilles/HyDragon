package com.alechilles.hydragon.npc;

import java.util.Objects;

/** Keeps brief jumps and touchdown flicker from repeatedly switching combat movement. */
final class AirborneTargetTracker {
    private Object target;
    private boolean airborne;
    private double pendingSeconds;

    void reset() {
        target = null;
        airborne = false;
        pendingSeconds = 0;
    }

    boolean update(Object nextTarget, boolean observedAirborne, boolean alreadyFlying, double dt) {
        if (!Objects.equals(target, nextTarget)) {
            target = nextTarget;
            airborne = nextTarget != null && observedAirborne && alreadyFlying;
            pendingSeconds = 0;
        }
        if (nextTarget == null) {
            airborne = false;
            pendingSeconds = 0;
        } else if (observedAirborne == airborne) {
            pendingSeconds = 0;
        } else {
            pendingSeconds += Math.max(0, dt);
            if (pendingSeconds >= (observedAirborne ? 0.8 : 1.0)) {
                airborne = observedAirborne;
                pendingSeconds = 0;
            }
        }
        return airborne;
    }
}
