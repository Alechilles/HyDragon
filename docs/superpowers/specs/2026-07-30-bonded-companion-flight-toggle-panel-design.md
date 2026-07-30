# Bonded Companion Flight Toggle Panel Design

## Goal

Add a small flight-mode button to active bonded-companion cards in the Dragon
Horn panel. The button must show the live companion's current locomotion mode
and toggle only companions that explicitly opt into the capability.

## Scope and ownership

The card renderer, panel event routing, live projection lookup, and reusable
capability belong to Alec's Tamework. HyDragon opts its flight-capable bonded
profiles into the capability. HyDragon's existing `AirborneMode` flag and
locomotion implementation remain the source of behavior; the panel does not
create a second movement controller.

Hydras and Rock Drakes remain ground-only. They receive no capability flag and
no panel button.

## Capability contract

Tamework adds a default-false, inherited `FlightToggle` bonded-companion
capability. A card is eligible only when all of these conditions hold:

1. Its resolved profile enables `FlightToggle`.
2. The profile currently has a matching active in-world projection.
3. The active projection exposes the existing locomotion-mode state.

The card never infers availability from a species name or an NPC motion
controller. Stored, dead, stale, and ground-only cards render without the
control. `AirborneMode` remains projection-local, so dismissing and re-summoning
starts from the existing grounded default.

## Presentation

Place a compact image button in the active card's upper-right chrome, adjacent
to the lifecycle state as indicated in the approved reference image. It uses:

- a transparent standing dragon/bird icon while the projection is grounded;
- a transparent flying dragon/bird icon while the projection is airborne;
- existing panel hover and pressed treatment; and
- localized tooltips that describe the resulting action: `Switch to flight` or
  `Switch to ground`.

Generate the two source icons as matching, readable, game-style silhouettes.
Package their final transparent PNG versions under Tamework's custom panel UI
assets and reference them from the card UI. The icon always communicates the
state currently read from the live projection, not the most recent click.

## Interaction and refresh flow

1. The panel's active-card snapshot carries the current `AirborneMode` value.
2. The card binds the matching icon and a profile-specific flight-toggle event.
3. A click resolves the profile's active projection on the authoritative
   server/world thread.
4. The handler delegates to the established locomotion-toggle path, preserving
   the companion's current target and order.
5. The panel refreshes from a new live snapshot. It changes the icon only when
   the authoritative projection state changed.

If a projection is missing, no longer active, no longer configured, or rejects
the state change, the handler performs no speculative UI mutation. The normal
refresh removes or corrects the control. This keeps panel state safe across
dismissal, death, transfer, and concurrent refreshes.

## Configuration rollout

Enable `FlightToggle` only on HyDragon bonded profiles that are already allowed
to use the established airborne locomotion feature. Do not add it to Hydra or
Rock Drake profiles. New companion packs can opt in by configuration, without a
Tamework Java species allowlist.

## Tests and verification

Use test-first coverage in Tamework for:

1. capability inheritance and its default-disabled behavior;
2. active-only rendering and absence for a disabled/ground-only profile;
3. standing versus flying icon binding from the actual snapshot value;
4. profile-scoped event routing to the existing authoritative toggle path;
5. stale or rejected actions producing a refreshed, non-speculative card; and
6. localization and final packaged icon/UI asset availability.

Use HyDragon tests for the opt-in profile assets and confirm that no
ground-only profile gains `FlightToggle`. Build both source repositories, then
install their exact committed artifacts and run an in-game check with one
grounded and one airborne active companion.

## Non-goals

- No stored-card preference or next-summon mode setting.
- No change to hydra or Rock Drake movement.
- No persistence of `AirborneMode` across dismissal, death, or re-summon.
- No duplicated locomotion, target, or order-management implementation in the
  panel.
