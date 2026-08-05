# Autonomous Dragon Flight Obstacle Avoidance Design

## Goal

Prevent wild and tamed dragons under NPC control from routinely flying into
trees, terrain, and structures. Avoidance must act before contact, preserve the
existing target-relative flight behaviors, and remain cheap enough for several
dragons to fly at once.

Rider-controlled flight is explicitly outside this change. A rider remains the
authority over the dragon's route, and the existing mounted collision behavior
is not modified.

## Current Problem

HyDragon's autonomous follow, loiter, orbit, combat-ingress, pass-through, and
recovery branches commonly use Tamework's `TameworkFlyingOrbit` body motion.
That motion computes a desired translation and sends it directly to
`MotionControllerFly`; it does not inspect the blocks along the intended route.

`MotionControllerFly` resolves collisions for the current movement step and
reports obstruction after contact. HyDragon's blocked-state routines can then
recover a dragon, but neither mechanism prevents the initial collision.

Hytale 0.5.7 exposes the missing preflight operation through
`MotionControllerFly.probeMove` and `ProbeMoveData`. The probe sweeps the NPC's
collision volume along a candidate move. Vanilla find, seek, wander, and path
following motions already use that facility. Hytale's separate
`SteeringForceAvoidCollision` predicts collisions with other entities; it does
not avoid static blocks and is not a substitute for route probing.

## Selected Approach

Add a bounded, geometry-aware avoidance layer to Tamework's existing
`TameworkFlyingOrbit` motion.

After the motion calculates its normal translation, the layer probes a short
lookahead corridor in that direction. A clear corridor leaves the original
steering untouched. A blocked corridor triggers a small fixed fan of alternate
directions: climb, left, right, climb-left, and climb-right. The clearest
candidate that still makes useful progress toward the original route replaces
the translation temporarily.

This is preferred over a global flight-controller interceptor because vanilla
seek/wander motions already perform their own path or movement probes. A global
interceptor could duplicate work or fight those motions. It is also preferred
over full A* flight paths because these dragons continuously orbit moving
targets; a local predictive correction is cheaper and better matched to that
behavior.

The existing blocked recovery remains the last-resort fallback for enclosed
spaces, momentum, unloaded or changing world geometry, and cases where every
candidate is obstructed.

## Scope and Ownership

The implementation belongs in the Tamework repository because
`TameworkFlyingOrbit` owns the steering that lacks preflight:

- `BuilderBodyMotionTameworkFlyingOrbit` gains a backward-compatible
  `AvoidObstacles` option, enabled by default.
- `BodyMotionTameworkFlyingOrbit` invokes the avoidance layer after the normal
  3D translation and altitude correction are known, but before yaw and pitch
  are finalized.
- A package-private `FlyingObstacleAvoidance` helper owns probe cadence,
  candidate generation and scoring, scratch buffers, and hysteresis state.

The `TameworkFlyingOrbit` builder ID and every existing mode remain unchanged.
Because obstacle avoidance defaults on, HyDragon does not need repetitive JSON
edits across every wild/tamed consumer. Existing HyDragon tuning remains the
source of target, radius, altitude, and speed intent.

## Autonomous-Only Guard

Avoidance runs only when all of the following are true:

- `AvoidObstacles` is enabled;
- the active controller is a compatible `MotionControllerFly`;
- the computed translation is non-zero; and
- the NPC is not currently rider-controlled.

The last condition is an explicit runtime guard, not just an asset convention.
The motion must skip avoidance if either Tamework's
`TameworkRideMountComponent` or Hytale's native `NPCMountComponent` is present.
This covers normal ridden states plus transitional or misconfigured ticks where
a flying-orbit motion and a mounted controller could briefly overlap.

No changes are made to `MotionControllerTameworkFly`,
`TameworkMountedGlide`, avatar flight, mounted input, or mounted collision
recovery.

## Lookahead and Probe Cadence

The probe distance adapts to the motion rather than using a one-block ray:

```text
lookahead = clamp(
    current turn radius
      + maximum fly speed * min(2, desired translation magnitude) * 0.75 seconds,
    4 blocks,
    12 blocks)
```

The cap prevents unusually fast or aggressively tuned motions from producing
unbounded collision sweeps. The minimum provides useful notice for slow-moving
large dragons. `probeMove` still uses the dragon's own collider, so the formula
does not need a species-specific width table.

Normal corridor checks run no more often than once every 0.10 seconds per
moving dragon. Between checks, clear flight uses the newly computed original
translation, while an active avoidance maneuver keeps its cached direction.
Activation resets the cadence so a new motion gets an immediate check.

The final 3D translation is probed, including pure climbs or sinks. A
`FACE_TARGET` motion therefore costs nothing while stationary, but a vertical
altitude correction still detects a canopy, ceiling, or ground approach.

## Avoidance Fan and Scoring

When the primary route is blocked, probe the following fixed alternatives:

1. climb by 35 degrees;
2. turn left by 45 degrees;
3. turn right by 45 degrees;
4. turn left by 45 degrees and climb by 25 degrees; and
5. turn right by 45 degrees and climb by 25 degrees.

There are deliberately no downward escape candidates. Descending toward an
obstacle should level out or climb rather than trade a wall collision for
terrain contact, so alternate candidates clamp their vertical component to
level or upward flight. If the original direction has no horizontal component,
the target-facing direction supplies the fan's horizontal reference axis; if
that is also degenerate, positive Z is the deterministic fallback axis.

Candidate scoring is deterministic and ordered by:

1. fully clear candidates before partial candidates;
2. greater probed travel distance;
3. stronger alignment with the original translation; and
4. the remembered avoidance side as a tie-breaker.

Side memory must never beat materially greater clearance. It exists only to
stop left/right oscillation when both sides are similarly viable.

If no candidate is fully clear, use the candidate with the greatest positive
clearance and scale its translation by its clearance fraction. A candidate
that cannot complete at least 25 percent of the lookahead is not meaningful;
if every candidate falls below that threshold, clear translational steering
for that update and leave contact handling and blocked recovery as the final
fallback.

## Hysteresis

After selecting an escape direction, retain its side and normalized direction
for at least 0.35 seconds. During that hold, cadence-limited probes validate the
held route without rebuilding the full fan. When the hold expires, probe the
original target-biased route again:

- if the original route is clear, resume it;
- if it is blocked but the held route remains clear, extend the hold; or
- if both are blocked, run a new bounded fan.

This keeps flight smooth around trunks and wall edges while still allowing the
dragon to abandon an escape route that closes unexpectedly.

## Wander Destination Preflight

`WANDER_TARGET` should avoid committing to an obviously bad random waypoint.
When choosing a destination, test only the same capped initial lookahead
corridor rather than sweeping the entire potentially 36-block route. Try at
most three random destinations and accept the first whose initial corridor is
clear. If none is clear, keep the candidate with the greatest clearance and
let continuous local avoidance handle the remaining route.

Waypoint selection happens only every few seconds, but its probes still share
the per-update probe budget with steering avoidance. It cannot create an
unbounded retry loop.

## Performance Budget

The implementation has explicit upper bounds:

- stationary or rider-controlled: zero probes;
- ordinary clear autonomous flight: one probe per 0.10 seconds, at most ten
  probes per second per moving dragon;
- one blocked-route decision: one primary plus five alternate probes;
- full fan selection: no more often than once per 0.35-second hold window;
- wander retarget: at most three candidate-corridor probes; and
- any single steering update: at most six total probes, shared across waypoint
  selection and avoidance.

`ProbeMoveData`, candidate vectors, probe vectors, and scoring scratch storage
are persistent fields. The steering loop must not allocate arrays, vectors,
lists, streams, or lambdas per tick. It must not scan blocks manually, search
nearby entities, build a path, or log each probe.

The helper exposes a package-private probe seam for deterministic tests, but no
new public runtime API is introduced.

## State and Failure Handling

Activation resets the probe timer, held direction, remembered side, and
waypoint-probe state. Deactivation requires no world mutation.

Invalid vectors or an unavailable controller/component cause avoidance to
leave the normal motion result alone. An invalid rider component type is
treated as absent, while a present rider component always disables avoidance.
World geometry can still change after a successful probe, so normal
`MotionControllerFly` collision resolution and HyDragon's obstruction recovery
remain enabled and authoritative.

## Testing and Validation

Focused Tamework unit tests cover:

- lookahead scaling and the 4/12-block clamps;
- clear primary movement using exactly one probe;
- blocked primary movement using no more than six total probes;
- climb, left, right, and diagonal candidate selection;
- clearance outranking target alignment and side memory;
- the 0.35-second hold preventing left/right oscillation;
- cadence suppressing redundant probes;
- partial-clearance speed reduction and the fully trapped stop result;
- vertical-only movement being probed;
- both mounted-component guards producing zero probes; and
- waypoint selection accepting a clear candidate and stopping after three
  blocked candidates.

Existing `BodyMotionTameworkFlyingOrbitTest` behavior remains covered for all
orbit modes, altitude correction, wander waypoints, and pass-through targets.
The builder-registration architecture test remains unchanged except for any
focused assertion needed for the new default.

Run the focused movement tests and the full Tamework test/build suite. Because
the selected design changes no HyDragon JSON, HyDragon's current asset contract
tests should pass without fixture updates. If implementation later requires an
asset override, validate that edit with HyDragon's exact locked Hytale 0.5.7
asset-tools profile before the normal project verification.

Performance validation uses two repeatable 60-second, post-warmup server runs
with 20 autonomously flying dragons:

- open air, to compare baseline and changed mean/p95 tick time; and
- a tree/wall course, to confirm bounded fan frequency under obstruction.

The open-air p95 tick-time regression target is no more than five percent. The
probe counts must also respect the code-level limits above. Profiling is done
without committing per-tick debug logging.

## Gameplay Acceptance Criteria

- Wild and tamed dragons using `TameworkFlyingOrbit` begin steering around
  trunks, walls, ceilings, and terrain before contact.
- Avoidance preserves the original target, altitude band, flight mode, and
  combat/follow state; it changes only the short-term translation.
- Dragons do not rapidly alternate left and right at the same obstacle.
- Dense or enclosed spaces can still fall through to the existing blocked
  recovery instead of entering an unbounded search.
- Rider-controlled flight receives no predictive steering or input changes.
- Open-air probe rate and measured tick cost stay within the stated
  performance budget.
