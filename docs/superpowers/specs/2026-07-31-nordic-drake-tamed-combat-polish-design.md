# Tamed Nordic Drake Combat Polish Design

## Goal

Correct four problems observed in the installed tamed Nordic Drake combat
graph:

- grounded combat crowds its target;
- bite and flame breath displace the normal swipe/stomp attack chain;
- the Drake seeks a grounded target without continuously looking at it; and
- autonomous fireball and flame-breath abilities omit the sound cues used by
  the mounted versions.

The change must preserve the existing `LockedTarget` contract, owner-defense
behavior, Dragon Horn ground/flight toggle, and aerial combat cycle.

## Grounded Spacing and Tracking

Change `DesiredAttackDistanceRange` from `[0.5, 5]` to `[3.5, 5]`. This keeps
the target inside the normal melee root's 5.25-block maximum range while
matching the wild evaluator's 3.5-block post-bite recovery distance and
preventing the Drake from intentionally closing to near-overlap range.

The grounded `MaintainDistance` motion will use the wild graph's `MoveThreshold`
of `0.2`, avoiding unnecessary micro-adjustments once the Drake reaches its
combat band.

Add a continuing grounded target-tracking instruction before movement and
attack selection. Its sensor will explicitly provide `LockedTarget` while
`.GroundCombat`, `AirborneMode=false`, and the `Walk` controller are active.
Its `HeadMotion` will use `Aim` with the existing combat turn-speed parameter.
Because the branch continues, chase or `MaintainDistance` can still own body
motion and the attack branches can still execute. Head tracking therefore
remains active while seeking, repositioning, waiting on cooldowns, and
attacking.

## Grounded Attack Cadence

Keep the manual, target-slot-aware instruction graph. Do not restore
`CombatActionEvaluator`: its hostile-memory actions can choose their own target
instead of honoring the player/Defend-selected `LockedTarget`.

Move the `Root_NPC_NordicDrake_Attack` branch ahead of the bite and flame-breath
selectors. It remains the regular basic cadence with the existing 5.25-block
range and 1.5-2.5 second cooldown. While that cooldown is running, evaluation
falls through to the existing weighted bite/breath selection and their direct
availability branches. Their current ranges, weights, blocking execution, and
10-20 second cooldowns remain unchanged.

This recreates the wild evaluator's relationship between `BasicAttacks` and
special utility actions without allowing the evaluator to acquire or replace a
target. The normal root continues to provide its existing left swipe, right
swipe, and stomp chain.

## Ability Audio

Reuse the mounted Nordic Drake sound-event IDs; do not duplicate audio assets
or rename events.

- Add `SFX_HyDragon_NordicDrake_Avatar_Fireball_Roar` to the normal fireball's
  `ChargeShoot` effects.
- Add a zero-duration sound step immediately after projectile launch using
  `SFX_HyDragon_NordicDrake_Avatar_Fireball_Launch`.
- Add `SFX_HyDragon_NordicDrake_Avatar_Flame_Breath_Roar` to the `ChargeShoot`
  effects in both grounded and flying NPC flame-breath interactions.

The fireball volley roots repeat the normal fireball interaction, so they
inherit both cues automatically. Sound placement in the interaction chains
keeps audio aligned with the visible charge, launch, and breath start rather
than with NPC decision timing.

## Safety and Scope

- Every grounded combat sensor continues to consume only `LockedTarget`.
- The Nordic component remains exclusive to `Tamed_NordicDrake`.
- Ground/air selection remains controlled solely by `AirborneMode` plus the
  matching walk/fly controller.
- No health-based phase logic, target acquisition, damage tuning, mounted
  ability behavior, or unrelated creature role changes are introduced.
- Existing hard-leash, owner/friendly rejection, target-loss, and aerial reset
  behavior remains intact.

## Verification

No new permanent regression tests will be added. Verification will use:

- maintenance updates to existing assertions that encode the old spacing and
  branch order, without introducing new test cases;
- the existing focused Nordic Drake combat and interaction tests;
- the repository asset validator and full Maven test suite;
- exact-profile affected-scope asset validation; and
- an installed in-game check confirming 3.5-5 block spacing, visible continuous
  head tracking, regular swipe/stomp use between specials, and synchronized
  fireball/breath sounds in grounded and flying combat.
