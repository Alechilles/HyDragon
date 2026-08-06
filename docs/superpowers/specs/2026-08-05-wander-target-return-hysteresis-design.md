# Wander-Target Return Hysteresis Design

## Goal

Keep autonomous flying dragons closer to their current movement target by making
`TameworkFlyingOrbit` return directly toward that target whenever a
`WANDER_TARGET` flight leaves its configured wander-radius envelope.

The behavior applies equally to following and combat:

- follow flight returns toward the owner/player target;
- combat flight returns toward the current combat target;
- no target slots, combat states, or engagement decisions are changed.

## Behavior

`WanderRadiusRange` supplies both transition thresholds. Horizontal distance is
used because that range already defines the horizontal wander radius; altitude
continues to use `DesiredAltitudeRange`.

1. While wandering normally, crossing beyond `WanderRadiusRange[1]` latches a
   return-to-target state.
2. While latched, the dragon ignores its current random wander waypoint and
   flies horizontally toward the current target using the existing
   `RelativeSpeed`.
3. The latch remains active while the dragon is between the maximum and minimum
   radii. This is the hysteresis that prevents rapid switching near the outer
   boundary.
4. Once horizontal distance is at or inside `WanderRadiusRange[0]`, the latch is
   cleared, the stale waypoint is discarded, and normal wandering selects a new
   target-relative waypoint.
5. If the target moves away again before the minimum radius is reached, return
   flight continues against the target's current position.

The existing target-relative climb/sink correction and autonomous obstacle
avoidance apply during return flight. Rider-control behavior remains unchanged.

## Architecture

The behavior belongs inside `BodyMotionTameworkFlyingOrbit` rather than NPC role
assets. Every follow and combat consumer already supplies the correct target to
the same `WANDER_TARGET` motion mode, so centralizing the transition avoids
duplicated sensors and guarantees consistent behavior.

`BodyMotionTameworkFlyingOrbit` will own one boolean return latch. Activation or
loss of required steering context resets it. A small package-private/static
decision helper will make the two-threshold transition directly testable.

No new builder property or asset parameter is needed. Existing modes, builder
IDs, role assets, combat sequencing, and target ownership remain unchanged.

## Data Flow

For each valid `WANDER_TARGET` steering update:

1. Measure current horizontal distance from the dragon to the provided target.
2. Update the return latch using the configured maximum and minimum radii.
3. If returning, compute target-directed horizontal translation at the existing
   `RelativeSpeed`, using the minimum radius as the stop distance.
4. Otherwise, run the existing random waypoint selection and traversal.
5. Apply target-relative altitude correction.
6. Pass the final translation through the existing obstacle-avoidance planner.
7. Derive yaw/pitch using the existing steering rules.

## Edge Cases

- Exact maximum radius does not start a return; crossing beyond it does.
- Exact minimum radius ends a return.
- Equal minimum/maximum radii remain valid and behave as a single boundary.
- A missing target, transform, or compatible flight controller clears the latch
  through the same early-exit reset path used by obstacle avoidance.
- Non-`WANDER_TARGET` modes are unaffected.
- A zero or invalid translation continues to use the existing safe steering and
  obstacle-avoidance guards.

## Testing

Tamework unit tests will cover:

- entering return state only outside the maximum radius;
- retaining return state between maximum and minimum radii;
- leaving return state at the minimum radius;
- selecting direct target approach while returning;
- discarding the stale wander waypoint when normal wandering resumes;
- leaving every non-wander flight mode unchanged;
- preserving altitude correction and obstacle-avoidance routing for return
  translation.

The focused movement tests and complete Tamework Gradle test suite must pass.
Hytale Workshop reference validation will be rerun for any engine-touching Java
file changed during implementation.

## Scope Boundaries

- No changes to combat targeting, aggression, state machines, or action timing.
- No changes to follow/teleport thresholds.
- No new seek-speed setting; return uses the current orbit `RelativeSpeed`, as
  requested.
- No HyDragon NPC asset edits are required because the shared motion mode covers
  both follow and combat consumers.
- Live multi-dragon observation remains the final gameplay-tuning check for how
  tightly the configured ranges feel in practice.
