# MiniWyvern Reliable Default Swoop Design

## Goal

Make every airborne tamed MiniWyvern perform a reliable, meaningfully damaging
bite swoop roughly once every thirty seconds while continuing to use its
existing talent-driven projectile cadence between swoops.

The cycle runs only in Tamework's owner-safe `Defend` combat state with a valid
locked target. The explicit Attack Target command shares that same state and is
therefore included. Idle, Follow, and Hold do not initiate swoops.

This change restores the default melee routine. The linked
`2026-07-30-miniwyvern-combat-routes-design.md` specifies how the expanded
Combat tree will improve it without changing the MiniWyvern's ranged-first
identity. Form-specific special abilities remain intentionally undefined.

## Confirmed Root Cause

The existing aerial combat component contains a weighted dive branch, but the
dive normally cannot reach a ground target. The MiniWyvern Fly controller uses
`MinHeightOverGround = 4`, while the bite sensor requires the target to be
within `AttackDistance = 2.75`.

Hytale 0.5.7 evaluates that target range as three-dimensional Euclidean
distance. Its Fly controller also steers `Seek` motion back into the configured
ground-relative altitude band. Consequently, the ground-clearance constraint
keeps the MiniWyvern outside the bite gate even when its horizontal approach
looks correct.

The current `9:1` loiter-to-dive random selection also averages one selected
dive per approximately fifty seconds because each weighted choice remains
active for three to seven seconds. That selection is probabilistic and can be
visually interrupted by the independent projectile aiming phase.

## Selected Architecture

Replace the weighted dive opportunity with a timer-driven internal swoop
cycle inside
`Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend`.

The component owns four internal phases:

1. `.Default` retains the current target acquisition, owner safety, leash, and
   follow behavior.
2. `.Combat` performs the existing slow irregular aerial loiter and allows
   talent projectiles.
3. `.Swoop` temporarily owns movement, descends toward the locked target, and
   attempts the dedicated MiniWyvern swoop bite.
4. `.Recovery` retreats to the ranged combat area before returning to
   `.Combat`.

Entering combat starts `Miniwyvern_Swoop_Cooldown` with a randomized
twenty-five-to-thirty-five-second duration. A completed bite attempt or timed-
out approach restarts that cooldown. This gives the requested roughly thirty-
second rhythm without tying melee frequency to projectile progression.

When the cooldown expires, a priority instruction atomically sets
`Miniwyvern_Swoop_Pending` before either attack scheduler can claim the next
turn. If projectile aiming is already active, that one aim-and-fire sequence
may finish. Pending blocks every new projectile schedule, so the component
enters `.Swoop` immediately after the current aim flag clears. Claiming the
swoop clears pending and sets `Miniwyvern_Swooping`.

## Swoop Reachability

While `.Swoop` is active, a continuing instruction applies Hytale's native
`OverrideAltitude` action every behavior tick and seeks the locked target. The
override uses a `[0, 2]` ground-relative desired altitude range that permits
the MiniWyvern to enter its 2.75-block bite radius. It exists only in the
active swoop phase.

Hytale clears Fly altitude overrides during post-behavior processing, so the
override disappears automatically when the `.Swoop` instruction stops
refreshing it. Normal loiter altitude and recovery movement then become
authoritative again.

The approach has a bounded timeout. If pathing, collision, target height, or
line of sight prevents a bite, the MiniWyvern abandons that attempt, clears
swoop ownership, restarts the cooldown, and returns to normal combat instead of
remaining in a permanent dive. The approach timeout is six seconds from entry
into `.Swoop`.

## Swoop Damage

The aerial swoop uses a dedicated physical-damage interaction instead of the
ordinary grounded bite interaction. Its default target is the same 16 damage.
The dedicated path exists to remove the ordinary bite's knockback and support
melee talent profiles without changing grounded combat.

The implementation adds these dedicated assets:

- `Root_NPC_Wyvern_Mini_Swoop_Bite` as the aerial attack root;
- `Wyvern_Mini_Swoop_Bite` as the bite animation and selector interaction; and
- `Wyvern_Mini_Swoop_Bite_Damage` as the 16-damage physical hit.

The aerial component exposes a `SwoopAttack` parameter whose default is the
new root and uses it only from the `.Swoop` attack action. Existing role
variants continue supplying `Attack = Root_NPC_Wyvern_Mini_Bite`, so grounded
combat and every nonswoop caller remain on the ordinary interaction.

Each cycle permits exactly one swoop attack invocation. When the range and
line-of-sight gate first passes, the component sets
`Miniwyvern_Swoop_Strike_Committed` before invoking `SwoopAttack`. That latch
prevents a second invocation while the target remains in range. Completion of
the one attack attempt transitions atomically to `.Recovery`, even if the
selector did not connect with a target. Timeout reaches recovery without
invoking the attack. A new cycle or full cancellation clears the latch.

Grounded MiniWyvern combat continues using the ordinary bite interaction and
its existing damage. The dedicated swoop interaction adds no knockback,
launch, force, impact, stun, or invulnerability effect.

## Projectile and Melee Ownership

Projectile cadence remains controlled by the existing mutually exclusive
talent branches:

| Projectile progression | Existing cooldown |
| --- | --- |
| Base projectile | 5–7 seconds |
| Cadence and intermediate upgrades | 4–6 seconds |
| Advanced and apex branches | 3–5 seconds |

A shared `Miniwyvern_Swooping` flag coordinates the two routines:

- a swoop may begin only after any active projectile aiming phase completes;
- `Miniwyvern_Swoop_Pending` reserves the next attack turn as soon as the
  swoop cooldown expires;
- projectile scheduling and projectile execution require the swoop flag to be
  false and the pending flag to be false;
- the flag remains true through the bite approach and recovery retreat; and
- projectile cooldown state is not restarted by a swoop.

Faster projectile talents therefore produce more ranged attacks between
swoops, but they cannot interrupt or starve the melee routine. If a projectile
becomes ready during a swoop, it may line up after recovery.

## Cancellation and Recovery

The swoop must cancel safely when any of these conditions becomes true:

- the outer command state is no longer `Defend`;
- the locked target is released or invalid;
- airborne mode is disabled;
- the active motion controller is no longer `Fly`;
- the owner leash or friendly-target safeguards reject the target; or
- the approach timeout expires before a bite.

Cancellation caused by command change, target loss, airborne-mode change,
motion-controller change, leash rejection, or friendly-target rejection
clears `Miniwyvern_Swoop_Pending`, `Miniwyvern_Swooping`, and the strike latch;
stops both the approach and swoop-cooldown timers; and restores an appropriate
default phase. Re-entering valid airborne Defend combat starts a fresh
25–35-second cooldown; a stale cooldown never runs while the MiniWyvern is
outside valid combat.

A completed bite attempt or six-second approach timeout restarts the
25–35-second cooldown immediately while combat remains valid. Recovery time
therefore counts toward the next interval. Existing command state and target
cleanup remain authoritative; the swoop routine must not invent a new outer
command or retain an invalid target.

After a successful bite, the existing combat backoff behavior owns `.Recovery`
for two to four seconds and moves the MiniWyvern back toward its eight-to-
fourteen-block loiter space. Projectile scheduling resumes only after recovery
finishes.

## Linked Talent-Tree Contract

The default swoop is unlocked for every tamed MiniWyvern. No talent is required
to use it in this change.

The expanded Combat-tree design is documented separately and preserves these
contracts:

- one route unlocks and improves projectiles as the primary sustained-damage
  source;
- one route improves the default melee swoop as supplemental, higher-risk
  burst damage;
- full melee mastery targets 28 swoop damage and an 18–24-second cooldown;
- the routes merge into a future form-specific special ability; and
- that merge requires either route endpoint, not both.

Tamework currently treats `RequiresTalentIds` as an all-of prerequisite list.
The redesign therefore requires an explicit any-of prerequisite contract,
`RequiresAnyTalentIds`, across Tamework's config codec,
purchase services, persisted mutation path, UI presentation, API projections,
and tests. The reliable-swoop implementation remains independently shippable
and does not require that schema change.

## Scope

Included:

- the tamed MiniWyvern aerial defend component;
- the tamed MiniWyvern template only where shared swoop/projectile ownership
  must be gated;
- the dedicated swoop root, selector interaction, and damage interaction;
- regression tests for cadence independence, reachability, cancellation,
  recovery, and species isolation; and
- exact-profile asset validation, full Maven verification, packaging, and
  local installation.

Excluded:

- grounded MiniWyvern combat;
- projectile assets, damage, accuracy, and cadence values;
- the Combat talent-tree layout and prerequisite schema;
- future form-specific special abilities;
- MiniWyvern model or animation edits; and
- all Nordic Drake, Rock Drake, Hydra, and full-dragon behavior.

## Test and Validation Contract

Test-driven implementation must first add a failing asset contract proving:

- the aerial component has a 25–35-second swoop cooldown;
- the normal loiter phase remains the default combat movement;
- the swoop phase continuously applies the `[0, 2]` altitude override and
  seek;
- the bite remains inside the swoop phase only;
- one cycle can invoke the swoop attack at most once and immediately advances
  to recovery after that attempt;
- the aerial swoop uses its dedicated knockback-free 16-damage interaction
  while grounded combat retains the ordinary 16-damage bite;
- the approach has a finite timeout and safe abort path;
- the approach timeout is six seconds;
- recovery retains the existing two-to-four-second backoff;
- invalid-combat cancellation stops the swoop cooldown, while success or
  timeout restarts it immediately;
- simultaneous projectile and swoop readiness sets pending first, permits at
  most one already-active projectile sequence to finish, and then begins the
  swoop without another projectile schedule;
- all projectile schedulers and executable projectile branches reject an
  active swoop;
- projectile cooldown ranges remain exactly 5–7, 4–6, and 3–5 seconds;
- grounded combat remains unchanged; and
- no non-MiniWyvern species asset is modified.

After the red-green cycle, validate the affected NPC assets under the locked
Hytale release `0.5.7` profile, generate and run the available static
verification plan, run the focused Maven tests, and run `./mvnw clean verify`.
Runtime acceptance remains an in-game check because static validation cannot
prove flight geometry or animation timing.

## Runtime Acceptance

Against a stationary ground target for at least two minutes, an airborne tamed
MiniWyvern should:

- continue its irregular eight-to-fourteen-block aerial loiter;
- fire projectiles at the cadence selected by its talents;
- begin approximately three to four swoop attempts over two minutes;
- descend far enough to execute the dedicated swoop bite when pathing and line
  of sight permit;
- retreat after the bite before resuming projectile attacks; and
- recover cleanly from an obstructed or timed-out approach.

Ground mode must retain its existing bite routine. Follow, Hold, Defend,
Attack Target, and airborne-mode switching must continue to preserve their
existing command and target semantics.
