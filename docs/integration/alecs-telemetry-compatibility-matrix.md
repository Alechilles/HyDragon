# Beacon compatibility matrix

| HyDragon target | Beacon range | Integration state | Static validation | Live validation | Failure behavior |
| --- | --- | --- | --- | --- | --- |
| `1.1.6` / Hytale `>=0.5.0 <0.7.0` | `>=2.0.0 <3.0.0` | Installed | Descriptor syntax, consent icon, manifest generation, and packaged resource checks | Run `/beacon project hydragon`, crash test, manual report, and stats-heartbeat checks before release | Reporting is available subject to server-owner consent and runtime settings |
| `1.1.6` / Hytale `>=0.5.0 <0.7.0` | Missing | Not installed | Optional manifest dependency permits normal loading | Start HyDragon without Beacon before release | HyDragon gameplay remains available; telemetry is inactive |
