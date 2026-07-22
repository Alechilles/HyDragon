# Tamework Avatar Flight Integration

## Contract

- Primary mod: Alec's Tamework 2.16.1+
- External mod: HyDragon
- Dependency: HyDragon requires a compatible Tamework installation for its existing interaction and mount assets.
- Exposed hooks: `MountMode=TameworkAvatarFlight`, `AvatarFlightConfig=HyDragonNordicDrake`, and `HyDragonIntDragon`'s ordinary `Mount` entry.
- Failure behavior: Tamework hides the prompt and rejects activation when the config/model or required participant state is unavailable. Other HyDragon dragons retain their existing mounted-glide defaults.
- Validation cases: Nordic Drake assets parse with the integration enabled; the source and avatar-only models map all grounded crouch states to their matching crouch clips; Tamework without HyDragon continues to load because no HyDragon id is referenced by Tamework assets or Java.

## Compatibility matrix

| Tamework version | HyDragon version | Status | Notes |
| --- | --- | --- | --- |
| 2.16.1+ | current development tree | Implemented; live validation pending | `Tamed_NordicDrake` uses its existing `HyDragonNordicDrake` config through the standard mount interaction. |
