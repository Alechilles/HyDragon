# Dragon Horn Aggressive Command Design

## Goal

Replace the Dragon Horn radial menu's obsolete flight/ground toggle with an
`Aggressive` command. Flight mode remains controlled by the companion-card
buttons. While aggressive, every bonded dragon automatically engages nearby
targets that are hostile under its configured attitude policy, matching Alec's
Animal Husbandry Predator behavior.

## Command Wheel

`HyDragonDragonHorn` keeps its explicit command list. `ToggleAirborneMode` is
removed and `Aggressive` takes its slot. The final wheel contains `Follow`,
`Hold`, `Recall`, `MoveToPing`, `Defend`, `Aggressive`, `AttackTarget`, and
`Idle`.

The new command sets the existing `Aggressive` state and uses HyDragon-local
name and HUD localization. It does not set a crosshair target, alter the owner
target, or introduce a new targeting policy.

## Behavior

The shared tamed templates already expose `CanAggressive` and permit the
`Aggressive` state. They will explicitly choose their grounded or flying
aggressive branch using the same `AirborneMode` and motion-controller checks as
`Defend`.

- Grounded companions retain the grounded follow/combat path.
- Flying companions retain the flying follow/combat path.
- Miniwyverns use the corresponding grounded or aerial combat path without
  changing the established Defend/Attack Target targeted-combat contract.
- Hydras and Rock Drakes remain grounded because they do not consume
  `AirborneMode`.

No Tamework API, new component type, or card-control behavior changes are
needed.

## Verification

Tests and asset validation will show that the wheel replaces the toggle with
`Aggressive`, all eligible roles accept the state, and the shared templates
have valid grounded and flying aggressive paths. Existing Defend, Attack Target,
and card-driven flight-mode behavior must remain unchanged.
