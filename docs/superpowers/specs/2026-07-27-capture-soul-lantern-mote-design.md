# Capture Soul Lantern Mote Design

## Goal

Make the dragon-capture projectile use the exact Soul Lantern particle presentation so the energy between the captured dragon and player is a soft, cyan, flow-mapped soul swirl.

## Confirmed runtime path

`Draconic_Stone.json` enables Tamework's homing visual projectile with model ID `HyDragon_DragonStone_Capture_Mote`. That model exists at `Server/Models/Projectiles/HyDragon/HyDragon_DragonStone_Capture_Mote.json` and attaches the `HyDragon_DragonStone_CaptureMote` particle system at its `mote_anchor` node. The model is moved from the dragon body to the player by Tamework.

## Design

Replace the content of the HyDragon capture-mote particle system with the exact two-spawner structure and values used by Tamework's Soul Lantern system:

- A continuously emitting `Fireflies` spawner: erosion rendering, billboard-velocity rotation, `Glow.png`, `FlowMap4.png`, the existing dual-attractor orbit, and all original timing, opacity, scale, and offset values.
- A two-particle `Fireflies_Start` burst using the same source values.

The copied assets receive HyDragon-prefixed IDs, but their particle behavior and values are otherwise unchanged. The existing model attachment, homing settings, capture timing, beam fallback, and capture-complete burst remain unchanged.

## Scope and safety

- Keep HyDragon self-contained; do not reference Tamework particle asset IDs at runtime.
- Remove the no-longer-referenced third capture-mote spawner so no orphan particle asset remains.
- Update the focused capture-presentation contract test to describe the Soul Lantern configuration instead of directional ribbons.
- Validate JSON, run the focused test, then run the normal project verification suite before deployment.

## Evidence

- Tamework source: `src/main/resources/Server/Particles/Item/Tamework/Tamework_Soul_Lantern.particlesystem` and its two `Lantern/Spawners` assets.
- Hytale Workshop MCP v0.5.6 `ModelAsset` schema confirms models support `Particles`; the capture mote model uses that supported attachment path.
