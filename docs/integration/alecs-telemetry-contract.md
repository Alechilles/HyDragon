# HyDragon - Beacon integration contract

## Purpose

HyDragon ships a descriptor-only integration with Beacon. The Beacon runtime discovers HyDragon's packaged descriptor and can report anonymous usage stats, automatically captured crashes, and player-submitted issues or suggestions without HyDragon calling the Java runtime API.

## Version and dependency contract

- Beacon is an optional dependency in the range `>=2.0.0 <3.0.0`.
- HyDragon remains fully functional when Beacon is absent; telemetry reporting is simply unavailable.
- When installed, Beacon owns discovery, consent, local queueing, upload, and the built-in player report UI.

## Exposed integration surface

`Server/Beacon/project.json` identifies the portal project as `hydragon` and enables:

- anonymous server, player, version, and environment stats;
- uncaught exceptions, setup failures, start failures, and exceptional world removals;
- manual issue and suggestion reports through Beacon commands and UI;
- server-owner-approved current and previous log attachments, contact information, and resolution updates.

Lifecycle events, performance timings, feature usage events, explicit non-fatal errors, and custom breadcrumbs remain unsupported until HyDragon adds corresponding Java runtime calls.

## Failure behavior and validation

Without Beacon, the descriptor has no runtime effect and HyDragon continues normally. With it installed, validate discovery with `/beacon project hydragon`, exercise crash ingest with `/beacon test hydragon descriptor-check`, flush with `/beacon flush hydragon`, submit a manual report through `/beacon report hydragon`, and confirm the first stats heartbeat in the portal after its normal delay.
