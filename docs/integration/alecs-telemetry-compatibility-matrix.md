# Alec's Telemetry compatibility matrix

| HyDragon target | Alec's Telemetry range | Integration state | Static validation | Live validation | Failure behavior |
| --- | --- | --- | --- | --- | --- |
| `1.0.1` / Hytale `0.5.x` | `>=1.0.0 <2.0.0` | Installed | Descriptor syntax, manifest generation, and packaged resource checks | Run `/telemetry project hydragon`, crash test, manual report, and stats-heartbeat checks before release | Reporting is available subject to server-owner consent and runtime settings |
| `1.0.1` / Hytale `0.5.x` | Missing | Not installed | Optional manifest dependency permits normal loading | Start HyDragon without Alec's Telemetry before release | HyDragon gameplay remains available; telemetry is inactive |
