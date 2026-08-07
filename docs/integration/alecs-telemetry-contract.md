# HyDragon - Alec's Telemetry contract

## Purpose

HyDragon ships a descriptor-only integration with Alec's Telemetry. The standalone telemetry runtime discovers HyDragon's packaged descriptor and can report anonymous usage stats, automatically captured crashes, and player-submitted issues or suggestions without HyDragon calling the Java runtime API.

## Version and dependency contract

- Alec's Telemetry is an optional dependency in the range `>=1.0.0 <2.0.0`.
- HyDragon remains fully functional when Alec's Telemetry is absent; telemetry reporting is simply unavailable.
- When installed, Alec's Telemetry owns discovery, consent, local queueing, upload, and the built-in player report UI.

## Exposed integration surface

`Server/Telemetry/project.json` identifies the portal project as `hydragon` and enables:

- anonymous server, player, version, and environment stats;
- uncaught exceptions, setup failures, start failures, and exceptional world removals;
- manual issue and suggestion reports through Alec's Telemetry commands and UI;
- server-owner-approved current and previous log attachments, contact information, and resolution updates.

Lifecycle events, performance timings, feature usage events, explicit non-fatal errors, and custom breadcrumbs remain unsupported until HyDragon adds corresponding Java runtime calls.

## Failure behavior and validation

Without Alec's Telemetry, the descriptor has no runtime effect and HyDragon continues normally. With it installed, validate discovery with `/telemetry project hydragon`, exercise crash ingest with `/telemetry test hydragon descriptor-check`, flush with `/telemetry flush hydragon`, submit a manual report through `/telemetry report hydragon`, and confirm the first stats heartbeat in the portal after its normal delay.
