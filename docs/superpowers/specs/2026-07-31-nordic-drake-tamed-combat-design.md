# Tamed Nordic Drake Wild-Style Combat Design

## Goal

Give `Tamed_NordicDrake` the Nordic Drake's real grounded and aerial combat
behaviors while preserving Tamework companion commands, owner safety, leash
behavior, progression, and mounting. The Dragon Horn's existing
`AirborneMode` flag, rather than health percentage, selects grounded or flying
combat.

## Current Behavior

The wild `NordicDrake` declares `_CombatConfig: CAE_NordicDrake` and uses the
combat graph in `Template_HyDragon_Dragon`. Together, the config and graph
define the Drake's melee attack suite, ground positioning, fireballs, flying
flame-breath, ingress, pass, and recovery cycle. Its aerial phase is entered
and exited through `UseHealthPhaseFlight` health-range checks.

`Tamed_NordicDrake` instead inherits `Template_HyDragon_Dragon_Tamed`. Both of
that template's Defend movement branches delegate combat to
`Component_Tamework_Instruction_Defend`, which acquires owner threats correctly
but only performs generic chase, seek, and direct `Attack` actions. The tamed
template already uses the shared `Component_HyDragon_Instruction_Airborne_Mode_Transition`
for Dragon Horn takeoff and landing.

## Targeting Contract

The two commands retain distinct target semantics:

- **Defend** clears any prior explicit target, keeps the owner in
  `MasterTarget`, and automatically chooses a hostile that recently attacked or
  threatens the owner or the Drake. The selected hostile is stored in
  `LockedTarget`.
- **Attack Target** enters Defend with the player's valid crosshair target
  already stored in `LockedTarget`. Automatic threat selection must not replace
  this valid command target.

Both paths feed the same Nordic Drake combat execution graph after a valid
`LockedTarget` exists. Owner, self, friendly, and allied targets remain
ineligible. Target loss or a hard-leash violation releases `LockedTarget` and
returns to Defend's owner-relative follow and threat-search behavior.

## Architecture

Add a HyDragon-owned Nordic Drake combat component and place it before the
generic Defend fallback in the tamed dragon template. The new component becomes
eligible only while the outer state is Defend and a valid `LockedTarget`
exists. Because its matching combat branch is non-continuing, it takes priority
over the generic seek-and-attack subtree for that tick.

The existing Tamework Defend component remains responsible when no combat
target is locked. It continues to provide owner-threat acquisition, command
target acceptance, alerted feedback, hard-leash handling, target-loss cleanup,
and the grounded or flying owner-follow fallback. This avoids copying
Tamework's owner-defense rules into HyDragon.

Vanilla Hytale `CombatActionEvaluator` is not used by the tamed component.
Hytale 0.5.7 source shows that `SelectBasicAttackTarget` and combat options with
`Target: Hostile` enumerate the evaluator's hostile memory, choose their own
`primaryTarget`, and write that entity into the CAE marked-target slot. They do
not seed the evaluator from a pre-existing `LockedTarget`. Reusing CAE would
therefore allow a player-commanded target to be replaced. The component instead
reproduces the relevant attack choices and timing directly with target-slot-aware
NPC instructions whose sensors explicitly require `LockedTarget`.

The Nordic component contains two mutually exclusive execution branches:

1. A grounded branch requiring `AirborneMode=false` and the `Walk` motion
   controller.
2. An aerial branch requiring `AirborneMode=true` and the `Fly` motion
   controller.

Controller-transition intervals are deliberately not combat intervals. While
the flag and controller disagree, the existing shared transition component
owns takeoff or landing. The outer `Defend` state and `LockedTarget` remain
unchanged, and combat resumes through the newly matching branch once the
controller transition completes.

## Grounded Combat

The grounded branch reproduces the intended `CAE_NordicDrake` ground contract
rather than Tamework's generic attack loop. It uses the same basic melee,
ground bite, and ground flame-breath root interactions with their corresponding
distance bands, selection weights, cooldowns, desired attack distance, combat
behavior distance, backward speed, turn behavior, and recovery timing. Every
sensor and attack remains explicitly scoped to `LockedTarget`; no hostile
memory query may select or replace the target.

The branch operates only on `LockedTarget`. It must not run the wild template's
ambient hostile detection, flock target broadcasts, ReturnHome behavior, or
wild state transitions. Those are wild-role concerns, not companion combat.

## Aerial Combat

The aerial branch reproduces the wild Nordic Drake's target-relative flight
cycle with direct target-slot-aware instructions:

- ranged loiter and fireball attacks;
- flame-breath ingress;
- the flying flame-breath pass;
- post-pass recovery and return to ranged positioning;
- blockage and navigation recovery already required by the wild cycle.

It uses the wild role's fireball, fireball volley, and flying flame-breath root
interactions and the same selection weights, cooldowns, attack distances,
altitude bands, approach speeds, pass timing, wander geometry, and recovery
tuning. Every phase remains gated by a valid `LockedTarget`,
`AirborneMode=true`, and the `Fly` controller so a toggle or target loss cancels
stale aerial work immediately. No `CombatActionEvaluator`,
`CombatAbility`, or implicit hostile target is permitted in the tamed
component.

The tamed graph does not include the wild graph's health filters,
`AirPhaseHealthRange`, `GroundPhaseHealthRange`, or 50-percent phase entry and
exit behavior. `AirborneMode` is the sole combat-mode selector.

## Role and Template Wiring

`Tamed_NordicDrake` explicitly selects the Nordic-only component. The component
owns its melee, bite, ground breath, fireball, volley, flying breath, movement,
and timing parameters so the shared template does not expose Nordic tuning to
unrelated descendants. The role's current health, progression, interaction,
mount, avatar-flight, breeding, memory, and command capability values remain
unchanged.

The shared tamed dragon template remains usable by other full-dragon variants.
The Nordic combat component must be enabled through an explicit role parameter
or macro reference whose default is the null instruction. Ground-only Hydra and
Rock Drake templates must not acquire Nordic aerial combat or new flight
requirements.

## Safety and Recovery

- Owner and friendly/allied target rejection runs before Nordic combat.
- A missing, invalid, dead, or out-of-contract `LockedTarget` cannot keep an
  attack, breath pass, or flight motion alive.
- Hard-leash failure releases the combat target and returns to the existing
  Defend fallback.
- Changing `AirborneMode` never writes `State`, `MasterTarget`, or
  `LockedTarget`.
- Mounted `Ridden` behavior and avatar flight remain outside this graph.
- The change introduces no Java builder type or new stable Tamework API ID.
- The tamed component contains no CAE hostile selection and never writes a new
  entity into `LockedTarget`.

## Testing

Implementation follows test-first asset development. A focused JUnit contract
test will fail before production assets are edited and will assert:

- `Tamed_NordicDrake` wires the Nordic combat component and all required
  grounded and aerial attack parameters derived from the wild graph and
  `CAE_NordicDrake` tuning;
- the custom locked-target combat path has priority over generic Defend combat;
- Defend owner-threat acquisition and Attack Target command locking retain
  their distinct existing contracts;
- grounded and aerial combat branches are mutually exclusive on
  `AirborneMode` and motion controller;
- grounded combat exposes the Nordic basic melee, bite, and ground
  flame-breath choices without a CAE target selector;
- aerial combat contains the expected fireball, flame-breath, ingress, pass,
  and recovery phases with the Nordic roots;
- no health-stat phase sensor controls tamed combat;
- no `CombatActionEvaluator`, `CombatAbility`, `SelectBasicAttackTarget`, or
  implicit hostile target can replace `LockedTarget`;
- toggling mode preserves the outer Defend state and `LockedTarget`;
- target-loss, friendly-target, and leash recovery are bounded;
- non-Nordic companion roles do not consume the component.

After the focused test passes, run the repository asset validator, the complete
Maven test suite, and exact-profile affected-scope candidate validation. Static
validation uses HyDragon's locked release `0.5.7` project profile. In-game
verification, when available, should cover Defend acquisition, Attack Target
priority, all grounded attack choices, the complete aerial fireball/breath
cycle, mid-combat mode toggles in both directions, target loss, owner safety,
and hard-leash recovery.

## Non-Goals

- Changing wild Nordic Drake balance or behavior.
- Changing Dragon Horn commands or feedback.
- Rebalancing tamed health, damage, talents, leveling, mounting, or breeding.
- Giving Hydra, Rock Drakes, or Miniwyverns the Nordic combat graph.
- Introducing an automatic health-based combat mode for tamed creatures.
