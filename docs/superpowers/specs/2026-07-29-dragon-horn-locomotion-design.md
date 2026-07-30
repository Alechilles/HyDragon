# Dragon Horn Locomotion Design

## Goal

Extend the Dragon Horn command wheel so bonded Miniwyverns and full dragons can
defend independently, explicitly attack a selected target, and switch their
locomotion between grounded and airborne behavior without changing their
current order or target.

## Command Wheel

`HyDragonDragonHorn` explicitly replaces the inherited `TwCommandExample`
`CommandList` with these eight commands:

1. `Follow`
2. `Hold`
3. `Recall`
4. `MoveToPing`
5. `Defend`
6. `AttackTarget`
7. `Idle`
8. `ToggleAirborneMode`

`SetHome` and `ReturnHome` are removed from this command item only. They remain
available to other Tamework command configurations that inherit the example
command list.

`Defend` clears `LockedTarget`, assigns the owner to `MasterTarget`, and enters
the existing `Defend` state. It therefore lets companions independently protect
their owner. `AttackTarget` continues to set `LockedTarget` from the crosshair
target and enters `Defend`, which preserves the existing combat and
Miniwyvern-talent execution path.

## Airborne Mode Contract

`AirborneMode` is a vanilla NPC role flag. It is intentionally transient: a
newly summoned companion has the flag unset and therefore begins in grounded
mode. The flag is not saved in bonded-companion data and does not survive
dismissal or resummoning.

Tamework gains a generic, one-shot command request for toggling a named,
declared vanilla role flag. The command executor must not invent a second flag
store. It raises a request for `AirborneMode`; the matching NPC template
consumes that request and uses vanilla `Flag` sensors and `SetFlag` actions to
flip the compiled role flag. The request is consumed exactly once per recipient
so one Horn use cannot oscillate a companion across instruction ticks.

The request does not call `SetState`, clear `LockedTarget`, or overwrite
`MasterTarget`. It only changes the locomotion branch selected by the companion
template. This guarantees that Follow, Hold, Defend, and Idle retain their
current order and combat target while their movement style changes.

## Species Behavior

Both `Template_Wyvern_Mini_Flying_Tamed` and
`Template_HyDragon_Dragon_Tamed` declare and consume `AirborneMode`.

- Grounded Follow, Defend, Hold, and Idle use their grounded movement branches.
- Airborne Follow, Defend, Hold, and Idle use their respective flight, hover,
  or aerial pursuit branches.
- Switching modes must never release the active attack target or master target.
- The Miniwyvern Defend branch remains the sole state that enables
  talent-gated projectile attacks, so `AttackTarget` continues to drive those
  attacks as it does today.

The shared Dragon Horn command applies the same request to every eligible role,
while each template owns its own concrete movement behavior. No species-specific
condition is added to Tamework.

## Verification

Automated coverage must prove:

- the Horn command array replaces the inherited array and contains the eight
  commands above, with neither home command;
- Defend and AttackTarget have distinct target semantics;
- the command request is one-shot and only addresses declared vanilla flags;
- `AirborneMode` defaults to unset after spawning;
- Miniwyvern and full-dragon templates each contain grounded and airborne
  branches for Follow, Hold, Defend, and Idle; and
- the existing Miniwyvern Defend plus locked-target talent projectile contract
  remains intact.

Validate the affected assets with the locked HyDragon release-0.5.7 profile,
then run the focused HyDragon and Tamework test suites before packaging both
mods for manual in-game testing.
