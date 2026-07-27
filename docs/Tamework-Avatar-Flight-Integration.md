# Tamework Avatar Flight Integration

## Contract

- Primary mod: Alec's Tamework `>=3.0.0 <4.0.0`
- External mod: HyDragon `0.2.1`
- Dependency: HyDragon requires the same Tamework range declared by its manifest, Maven build, and `TameworkBridge`.
- Exposed hooks: `MountMode=TameworkAvatarFlight`, `AvatarFlightConfig=HyDragonNordicDrake`, and `HyDragonIntDragon`'s ordinary `Mount` entry.
- Failure behavior: Tamework hides the prompt and rejects activation when the config/model or required participant state is unavailable. Other HyDragon dragons retain their existing mounted-glide defaults.
- Validation cases: Nordic Drake assets parse with the integration enabled; the source and avatar-only models map all grounded crouch states to their matching crouch clips; Tamework without HyDragon continues to load because no HyDragon id is referenced by Tamework assets or Java.

## Compatibility matrix

| Tamework version | HyDragon version | Status | Notes |
| --- | --- | --- | --- |
| `3.0.x` | `0.2.1` | Source implemented; packaged/live validation pending | `Tamed_NordicDrake` uses `HyDragonNordicDrake` through the standard mount interaction. The high-altitude encounter additionally requires a confirmed active bonded full-dragon lease and the Flightmaster's Talisman. |
