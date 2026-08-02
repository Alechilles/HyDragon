# Toxic Hydra Aerial Combat Design

## Goal

Give both wild and bonded Toxic Hydras Nordic Drake-style flight combat while
retaining Toxic Hydra attacks, poison status application, and poison-cloud
identity.

## Wild Combat

Wild Toxic Hydra preserves its existing ground combat. Its dedicated aerial
behavior uses Nordic Drake's health-phase lifecycle and movement sequence:
orbit the hostile target, fire a bounded toxic three-shot volley, make a short
poison-spit flyby, then recover and resume orbiting.

The ranged phase reuses the existing Toxic Hydra projectile interaction through
its role-resolved interaction variables. The flyby uses a new bounded poison
spit root, not the twenty-shot rain barrage, so one aerial pass cannot create a
dense field of thirty-second poison clouds.

## Bonded Combat and Mounted Flight

Bonded Toxic Hydra receives the same locked-target, airborne state cycle used
by the bonded Nordic Drake. It retains Nordic's hard-leash, owner, friendly,
lost-target, cancellation, and recovery exits, but binds every attack to Toxic
Hydra roots. The tamed role opts in through a Toxic-Hydra-specific parameter;
other tamed Hydras retain their existing behavior.

`HyDragonToxicHydra` exposes the same two mounted combat slots as Nordic Drake:
one for the toxic projectile volley and one for the bounded poison-spit flyby.
They use player-safe root interactions and toxic visuals/effects only.

## Architecture and Boundaries

Hydra's wild and tamed roles inherit different templates from Nordic Drake, so
the implementation adds dedicated opt-in wiring rather than copying Nordic
role fields. Nordic Drake, Ice Hydra, the existing Toxic Hydra rain barrage,
and vanilla poison assets remain unchanged. Existing thirty-second toxic-cloud
behavior is reused by the aerial projectiles.

## Verification

Asset contracts must prove each Toxic Hydra role selects its dedicated aerial
route; required target-safety exits are present; mounted slots resolve to toxic
roots; and no Nordic fireball or flame-breath ID is reachable from a Toxic Hydra
asset. Validate JSON assets and run the focused contracts; record any blocked
full-suite result separately.
