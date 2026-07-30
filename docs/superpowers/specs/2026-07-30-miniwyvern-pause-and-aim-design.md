# MiniWyvern Pause-and-Aim Design

## Goal

Make every talent-driven MiniWyvern projectile attack briefly interrupt combat movement, line up a ballistic shot at the locked target, fire, and then return to the existing grounded or airborne combat routine.

## Selected Behavior

Each of the 11 mutually exclusive projectile talent instructions in `Template_Wyvern_Mini_Flying_Tamed` will retain its current `AimingTimeRange` of 0.4–0.7 seconds and gain both:

- `BodyMotion: { "Type": "Nothing" }`, which temporarily releases the normal walk/orbit steering while the blocking attack action runs. In flight this produces a hover; on the ground it produces a stationary firing stance.
- `HeadMotion: { "Type": "Aim", "Spread": 0, "HitProbability": 1, "Deflection": true }`, which supplies `ActionAttack` with target and ballistic aiming data and turns the NPC into the shot.

The MiniWyvern model limits visible head rotation to 35 degrees left or right and -25/+45 degrees vertically. Once the head reaches its allowed yaw while body steering is released, Hytale's motion controller can rotate the body toward the target rather than allowing an extreme head/body mismatch.

The explicit zero aiming spread is diagnostic and removes the engine's inaccurate `Aim` defaults from this orientation fix. It does not change the projectile assets themselves, and projectile motion can still miss due to target movement, collision, or Hytale's known omission of shooter velocity from deflection. Controlled inaccuracy can be added separately after firing orientation is reliable.

After the blocking attack completes, the surrounding combat instructions resume the existing behavior: airborne MiniWyverns return to `TameworkFlyingOrbit`, grounded MiniWyverns return to their current defend movement, and the rare dive/bite behavior remains unchanged.

## Alternatives Considered

1. `HeadMotion: Aim` without pausing body motion. This is the smallest edit, but `TameworkFlyingOrbit` continues controlling body yaw. Hytale then clamps the head within the model's limits instead of rotating the body, so targets behind the flight direction can remain outside the firing direction.
2. Pause/hover plus `HeadMotion: Aim` (selected). This gives aiming temporary ownership of body orientation and produces a readable line-up-and-fire beat.
3. Rewrite the orbit controller to face the target continuously. This would couple general locomotion to projectile behavior and remove the wandering flight character, so it is outside scope.

## Scope and Non-Goals

- Apply the firing stance to all 11 talent projectile variants, in both grounded and airborne modes.
- Do not change the 0.4–0.7-second aiming window, attack cadence, projectile assets, orbit tuning, dive/bite behavior, talent selection, or command states.
- Do not add any flying state to Rockdrakes or Hydras.

## Verification

- Extend `MiniwyvernTalentAssetWiringTest` first so every talent projectile attack must define the exact body and head motion contract.
- Observe the focused test fail before changing the asset, then pass afterward.
- Run the focused MiniWyvern and locomotion contract tests.
- Run HyDragon asset validation and the full Maven verification suite.
- Build and install an exact committed revision into both local Hytale mod directories, then verify matching checksums and packaged JSON.
- Runtime success criterion: while preparing a projectile, the MiniWyvern stops translating, turns toward the locked target without an extreme neck twist, fires after 0.4–0.7 seconds, and resumes its previous combat movement.
