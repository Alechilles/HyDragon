# Capture Energy Tether Design

## Goal

Make a successful HyDragon capture read as smooth magical energy flowing from
the dragon to the player, while the existing capture sweep dissolves the dragon
out of existence.

## Scope

- Rework only the capture beam and homing-mote presentation assets under
  `Server/Particles/HyDragon/DragonStone/`.
- Keep the capture channel duration, target selection, success/failure logic,
  item IDs, and Tamework interaction wiring unchanged.
- Keep the current bottom-up model-VFX dissolve as the capture endpoint.

## Visual Design

The effect has three layers:

1. **Core tether** — a soft cyan beam continuously connecting the targeted
   dragon and player for the channel duration.
2. **Flowing ribbons** — two or three elongated, translucent energy wisps
   moving toward the player along the tether. Their overlapping lifetimes make
   the flow appear continuous rather than like separate projectiles.
3. **Endpoint accents** — a sparse, short-lived sparkle near the dragon and
   player only. There will be no dense dot cloud along the entire path.

At capture completion, the tether intensifies briefly while the existing
`HyDragon_DragonStone_Capture` model VFX sweeps bottom-up and removes the
dragon. The result should look like the dragon's essence is being drawn through
the tether as it fades away.

## Asset Plan

- Replace the single, burst-style beam treatment with a continuous,
  directionally stretched core.
- Retune the current `CaptureMote` particle system from several very
  short-lived circular/spark particles into a small number of longer-lived,
  elongated `Glow_Direction`-style ribbons.
- Preserve the existing capture-particle-system IDs and item interaction
  references where possible, so no gameplay or trigger wiring changes are
  needed.
- Retain only a reduced endpoint sparkle spawner as an accent layer.

## Validation

- Validate each changed particle-system and particle-spawner JSON asset.
- Confirm every spawner ID resolves from its particle system and every system
  reference still resolves from the capture interaction/model.
- Run the HyDragon asset validator and full Maven verification.
- In game, verify a full capture channel: the tether remains continuous during
  movement, energy visibly travels dragon-to-player, the endpoint accents stay
  restrained, and the dissolve still completes at capture resolution.

## Acceptance Criteria

- The capture reads as one flowing energy transfer, not a cloud of individual
  particles.
- The player can clearly see directionality from dragon to player.
- The beam does not obscure the dragon or overpower the dissolve edge.
- No capture behavior, duration, item, roster, or failure-path semantics
  change.
