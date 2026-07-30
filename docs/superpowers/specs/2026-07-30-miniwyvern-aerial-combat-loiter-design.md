# Miniwyvern Aerial Combat Loiter Design

## Goal

Replace the Miniwyvern's tight, rapid-fire aerial orbit with a slower and less
mechanical combat pattern. An airborne Miniwyvern must loosely loiter above
and around its hostile target, attack periodically with projectiles, and make
only occasional bite dives before returning to range.

Grounded combat behavior is outside this change and must remain unchanged.

## Current Problem

The flying `Defend` branch uses the same close-range combat behavior as the
grounded branch. Its combat radius is 8 blocks, its direct and always-moving
weights are high, and its chase speed is 0.9. This makes the Miniwyvern circle
very close to the target and repeatedly drive toward it.

Projectile talent variants independently execute attacks with fixed pauses
between 0.75 and 2.5 seconds. Those intervals are substantially faster than
the intended periodic supporting-fire role and make high-tier variants fire
almost continuously.

## Selected Approach

Retune the existing asset-driven combat path rather than adding another Java
flight controller or a Java combat scheduler.

Tamework's generic `Defend` instruction cannot express this geometry by tuning
weights alone because it always runs an unconditional chase until the NPC is in
bite range. The airborne branch therefore uses a focused HyDragon instruction
component that preserves Tamework's target acquisition, owner safety, leash,
follow, and target cleanup behavior while replacing only its combat movement.
The grounded branch continues using the generic instruction unchanged.

The airborne component uses Tamework's existing `TameworkFlyingOrbit` motion in
`WanderTarget` mode. That motion chooses randomized target-relative waypoints
within explicit horizontal-radius and altitude ranges, which creates the loose
wandering pattern without new Java movement code. A low-weight asset choice
still permits occasional bite dives and then invokes the existing combat
backoff component.

## Airborne Movement Behavior

When all of the following are true, the flying combat tuning applies:

- the Miniwyvern is in `Defend`;
- `AirborneMode` is set;
- the active motion controller is `Fly`; and
- a valid hostile `LockedTarget` is present through the existing Tamework
  defend routine.

The preferred combat space is:

- 8–14 blocks horizontally from the target; and
- 5–9 blocks above the target.

The asset must heavily favor slow `WanderTarget` movement within that space.
Waypoint distance and retarget timing use non-degenerate ranges so the
Miniwyvern changes direction, distance, and timing instead of tracing a perfect
orbit. Direct movement must have a much lower weight than loiter movement. This
makes close approaches uncommon rather than removing them entirely.

When a direct approach produces a bite, the existing combat backoff behavior
must return the Miniwyvern toward its loiter distance. The Miniwyvern must not
remain within bite range as its normal aerial combat position.

The grounded `Defend` branch retains its current radius, speed, weights, bite
timing, and follow behavior.

## Projectile Cadence

Every projectile talent variant uses a randomized pause range rather than a
fixed value. The progression bands are:

| Progression band | Attack pause range |
| --- | --- |
| Base projectile | 5–7 seconds |
| Intermediate/cadence upgrades | 4–6 seconds |
| Apex and highest upgrades | 3–5 seconds |

No projectile-capable talent variant may have a minimum pause below 3 seconds.
Upgrades may improve cadence only within these bands; they must not restore the
current sub-second or near-continuous firing behavior.

Projectile attacks remain talent-gated and mutually exclusive. This tuning
must not allow multiple projectile instructions to execute for the same
Miniwyvern at the same time.

## Combat Flow

1. Tamework's existing defend routine acquires or retains the hostile target.
2. The airborne branch moves the Miniwyvern into the loose overhead loiter
   space.
3. Weighted movement normally selects a randomized target-relative waypoint at
   reduced speed.
4. The selected mutually exclusive projectile instruction fires when its
   randomized pause permits.
5. A low-probability direct movement choice can produce a bite dive.
6. Backoff movement returns the Miniwyvern to its ranged loiter space.
7. Existing owner leash, friendly-target rejection, command state, and target
   cleanup behavior remain authoritative.

## Validation and Tests

Focused asset contract tests must prove:

- the grounded combat branch retains its existing tuning;
- the airborne branch uses a combat distance compatible with the 8–14 block
  horizontal loiter zone;
- airborne combat movement is slower than the current 0.9 chase behavior;
- wandering/backoff behavior outweighs direct approaches;
- waypoint distance and retarget timing use actual ranges rather than fixed
  values;
- airborne backoff returns the Miniwyvern beyond close bite range;
- every projectile variant has a randomized pause range within its assigned
  progression band;
- no projectile variant has a minimum pause below 3 seconds; and
- talent gates remain mutually exclusive.

The edited NPC asset must be validated with the exact locked Hytale 0.5.7
project profile using affected-scope validation and the generated static
verification plan. The normal HyDragon asset validator, focused Maven tests,
and full Maven verification must pass before installation.

## Acceptance Criteria

- An airborne Miniwyvern normally remains 8–14 blocks horizontally from and
  5–9 blocks above its hostile target.
- Its path varies in direction, duration, and distance instead of forming a
  continuous perfect circle.
- Base projectile attacks occur every 5–7 seconds, with talent progression
  improving the cadence no further than the 3–5 second apex band.
- Bite dives are occasional and followed by a return to ranged loitering.
- Grounded combat, command handling, owner safety, and talent exclusivity do
  not regress.
