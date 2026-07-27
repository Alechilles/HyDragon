# HyDragon - Tamework Bonded Compatibility Matrix

Status: Source/contract coverage present; packaged and live compatibility pending

| HyDragon | Tamework / capability state | Result | Automated evidence | Live evidence | Notes |
| --- | --- | --- | --- | --- | --- |
| `0.2.1` | `3.0.x`, public API `0.9.0`, `BONDED_COMPANIONS` advertised and available | Bonded features on | Contract coverage present | Pending | Full behavior. Capture and dynamic encounters additionally require capture policy, resolved-attempt consumption, interaction extensions, and events. |
| `0.2.1` | Compatible `3.x`, bonded capability becomes ready after startup recovery | Bonded features recover | Refresh behavior covered | Pending | `TameworkBridge` refreshes capabilities and bonded availability on request; no generic fallback or bridge reconstruction is required. |
| `0.2.1` | Compatible `3.x`, no `BONDED_COMPANIONS` | Bonded features off | Missing-capability behavior covered | Pending | Feature gate reports the missing capability and does not mutate generic persistence. |
| `0.2.1` | Compatible `3.x`, capability advertised but bonded authority unavailable | Bonded features off | Availability-blocker behavior covered | Pending | Feature gate reports Tamework's bonded readiness reason. |
| `0.2.1` | Compatible `3.x`, legacy generic capabilities only | Unsupported companion path | No-fallback contracts present | Pending | Command-family, timed-summon, paid-command-revival, profile-data, population, and replacement-persistence APIs are not substitutes. |
| `0.2.1` | Tamework missing, `<3.0.0`, or `>=4.0.0` | Required dependency failure | Manifest/range contracts present | Pending | Plugin dependency should reject the combination before HyDragon gameplay starts. |

## Alignment sources

The required range must match in all three locations:

- `manifest.json`: `Alechilles:Alec's Tamework!` -> `>=3.0.0 <4.0.0`
- `pom.xml`: compile target `tamework.version` -> `3.0.0`
- `TameworkBridge.REQUIRED_TAMEWORK_RANGE` -> `>=3.0.0 <4.0.0`

The HyDragon manifest targets Hytale `>=0.5.6 <0.6.0`. The local schema catalog used during this documentation pass did not expose an exact public `0.5.6` Workshop profile, so exact-profile and live-load compatibility are still explicit acceptance gaps rather than completed claims.

## Feature-off diagnostics

When a row is feature-off:

1. Run `/hydragon status` and record the affected feature plus missing capability/bonded reason.
2. Run `/tw debugdb status` and `/tw debugdb detail`.
3. If the behavior contradicts the matrix, run `/tw debugdb export` and retain its printed path plus the relevant server-log window.
4. Do not retry with an old generic roster/profile path or edit persistence records manually.

`Live evidence: Pending` remains intentional until both freshly packaged jars complete the bounded fresh-world checklist.
