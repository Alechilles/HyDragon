# Toxic Hydra Lingering Poison Cloud Design

## Goal

Toxic Hydra projectile impacts create a visible poison cloud that lasts 30 seconds,
then disappears without requiring a game reload. While active, the cloud damages
entities inside its area once per second and reapplies `Poison_T1`.

## Approach

Create a Hydra-specific particle system that inherits the vanilla poison visual
spawners but declares `LifeSpan: 30.0`. Point the toxic direct and rain projectile
death/miss particles at that system, leaving launch and hit feedback unchanged.

Configure the existing `TameworkLaunchProjectile` lingering-hazard support on both
toxic launch interactions. The direct shot uses its existing 3 m impact radius;
the rain shot keeps its existing 4 m radius. Both use a 30-second duration, a
one-second tick interval, five damage per tick, exclude their source, and apply
`Poison_T1`.

## Boundaries

This does not change vanilla `Effect_Poison`, Ice Hydra behavior, projectile flight
feedback, or the status effect's own duration. It relies on the current Tamework
lingering-hazard system rather than the 0.6 encounter manager.

## Verification

Extend the Toxic Hydra asset contract to require the finite cloud system and both
hazard configurations. Run the focused Maven test and the asset validation script.
