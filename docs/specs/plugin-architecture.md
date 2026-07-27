# HyDragon Plugin Architecture Specification

Status: Bonded-companion source integration implemented; packaged and live acceptance pending
Target HyDragon version: `0.2.1`
Required Tamework range: `>=3.0.0 <4.0.0`
Target Tamework public API: `0.9.0`

## 1. Purpose

HyDragon is one Java plugin and root-layout asset pack. Maven packages compiled classes, `manifest.json`, `Common/`, and `Server/` into a single JAR without relocating the existing asset trees.

The Java layer owns HyDragon-specific transactions and behavior that assets cannot safely express. Assets remain responsible for models, textures, items, recipes, NPC roles, ordinary spawn data, effects, projectiles, audio references, and localization. Tamework owns reusable companion mechanics.

The shared Dragon Horn uses Tamework's dedicated bonded-companion runtime. That runtime models durable profile state plus temporary world projections; it is not the generic command-family roster and is not the replacement persistence used by permanent world animals.

Related documents:

- [Specification suite index](README.md)
- [Draconic capture, Dragon Horn, and revival](capture-summoning-maintenance.md)
- [Wyvern Egg, Soul Bond, and Miniwyvern](soul-bond-miniwyvern.md)
- [Dragon content and encounters](dragon-content-encounters.md)
- [HyDragon - Tamework bonded integration contract](../integration/tamework-bonded-companions-contract.md)
- [Compatibility matrix](../integration/tamework-bonded-compatibility-matrix.md)

## 2. Architectural decisions

1. The distributable remains one JAR containing Java classes and the complete asset pack.
2. The initial build consumes root `Common/`, root `Server/`, and root `manifest.json` directly.
3. HyDragon requires Tamework `>=3.0.0 <4.0.0` and integrates through the public API plus registered custom asset/interaction contracts.
4. Manifest version compatibility is necessary but not sufficient; each runtime feature checks required capability names and current bonded availability.
5. Full dragons and the Soul Bond Miniwyvern share roster `hydragon:dragon_horn` but use families `hydragon:full_dragons` and `hydragon:soulbound_mini`.
6. The bonded lifecycle exposes only `STORED`, `ACTIVE`, and `DEAD`; a world NPC is a temporary projection of a stable profile.
7. HyDragon never uses generic command-family, population, timed-command-summon, generic profile-data, or paid-command-revival APIs as a bonded fallback.
8. Miniwyvern is Soul Bond-exclusive and cannot be captured by a Draconic Stone.
9. Tamework's Flightmaster's Talisman is the only flight-unlock item.
10. Canonical asset IDs and filenames use English terminology. Player-facing localization ships in `en-US`, `pt-BR`, `de-DE`, `fr-FR`, and `es-ES`.
11. The unreleased bonded-vessel and generic command-roster approaches receive no migration or compatibility layer.

## 3. Requirements

### Packaging and identity

- **HYD-ARCH-001:** A clean build must produce one JAR containing `HyDragonPlugin`, `manifest.json`, and the expected `Common/` and `Server/` trees.
- **HYD-ARCH-002:** Resource includes must exclude repository metadata, docs, source models, generated output, and development archives.
- **HYD-ARCH-003:** `manifest.json` declares `Main`, keeps `IncludesAssetPack: true`, and requires `Alechilles:Alec's Tamework!` in range `>=3.0.0 <4.0.0`.
- **HYD-ARCH-004:** `pom.xml`, `manifest.json`, and `TameworkBridge.REQUIRED_TAMEWORK_RANGE` must agree.

### Runtime boundary

- **HYD-ARCH-005:** HyDragon may acquire Tamework through its sanctioned plugin accessor and use only `com.alechilles.alecstamework.api.*` for runtime integration. It must not import Tamework implementation services or reflect into them.
- **HYD-ARCH-006:** `TameworkBridge` centralizes capability evaluation. Domain services do not scatter their own version assumptions.
- **HYD-ARCH-007:** The bridge refreshes capability and `BondedCompanionAvailability` state on demand so normal post-startup recovery can enable a feature without a server restart.
- **HYD-ARCH-008:** Missing capability or unavailable bonded authority fails only the affected feature closed and records a concrete blocker.
- **HYD-ARCH-009:** Java services remain separated by integration, entitlement, extension data, abilities, encounters, diagnostics, and world dispatch.
- **HYD-ARCH-010:** World/entity work runs on the owning world thread. Deferred work carries stable owner/profile/entity IDs and resolves current components inside that context.

### Persistence and recovery

- **HYD-ARCH-011:** Tamework's independent bonded store is authoritative for the roster profile, complete NPC snapshot, lease, state, cooldown/revive summary, cleanup intent, operation idempotency, and extension rows.
- **HYD-ARCH-012:** HyDragon's local store is authoritative only for HyDragon-owned concerns: the once-per-player entitlement, consumable saga/refund evidence, and special encounter records.
- **HYD-ARCH-013:** Miniwyvern domain data is a namespaced bonded extension document, not a local or generic second profile.
- **HYD-ARCH-014:** Capture, Egg spend, provisioning, attunement, and revival must be idempotent or compensating. A lost response cannot create a second companion or consume the same input twice.
- **HYD-ARCH-015:** Unsupported or conflicting records fail closed and surface bounded reconciliation diagnostics instead of being overwritten.

### Diagnostics and verification

- **HYD-ARCH-016:** Startup and `/hydragon status` report plugin/API version, capabilities, per-feature blockers, bonded availability, config issues, HyDragon persistence readiness, and bounded reconciliation counts.
- **HYD-ARCH-017:** `/tw debugdb status`, `/tw debugdb detail`, and `/tw debugdb export` remain the bounded Tamework evidence path. Exported bonded data is aggregate/redacted rather than raw profiles or player identifiers.
- **HYD-ARCH-018:** Automated verification covers public-API boundaries, capability-off behavior, assets, extension concurrency, restart recovery, packaged layout, dependency alignment, and fresh-world acceptance prerequisites.

## 4. Repository and package layout

```text
HyDragon/
  Common/                         shared/client assets packaged at JAR root
  Server/                         server, HyDragon, and Tamework assets
  manifest.json                   packaged at JAR root
  pom.xml
  src/main/java/com/alechilles/hydragon/
    abilities/                    Miniwyvern ability behavior and bonded events
    bonded/                       extension document, codec, and CAS gateway
    config/                       species, archetype, and encounter configs
    diagnostics/                  feature/persistence status
    encounters/                   active bonded eligibility and encounter runtime
    integration/                  Tamework bridge, gates, and messages
    persistence/                  HyDragon entitlement/saga/encounter data
    runtime/                      gameplay orchestration
  src/test/java/com/alechilles/hydragon/
  docs/
```

Moving assets to `src/main/resources` is optional future work. It is not part of this integration because the current root-layout packaging is intentional and tested.

## 5. Runtime component model

| Component | Responsibility | Must not own |
| --- | --- | --- |
| `HyDragonPlugin` | Lifecycle orchestration, codec/command registration, service composition, orderly shutdown | Companion domain transitions |
| `TameworkBridge` | Public API acquisition, capability snapshots, bonded availability, feature blockers | Tamework implementation details |
| `TameworkGameplayAdapter` | Narrow bonded list/provision/capture-evidence/extension/event calls | Generic profile, population, command-family, timed, or revival APIs |
| `HyDragonConfigRepository` | Immutable species/archetype/encounter config snapshots and validation | Tamework asset decoding |
| `SoulBondService` | Once-per-player entitlement and Egg spend/recovery saga | Projection lifecycle or a second Miniwyvern profile |
| `BondedMiniwyvernProvisioningService` | Canonical stored provision evidence and extension initialization | Live NPC UUID or automatic summon |
| `BondedMiniwyvernExtensionStore` | Typed, unknown-field-preserving extension read/CAS | Roster state |
| `MiniwyvernAttunementService` | Essence transaction and attunement merge | Generic profile-data mutation |
| `MiniwyvernAbilityRuntime` | Attach/detach behavior from bonded state events | Durable roster ownership |
| `ActiveBondedDragonResolver` | Confirm active full-dragon lease in the candidate world | Generic population evidence |
| `DynamicEncounterCoordinator` | Encounter phases, cleanup, and exact bonded capture evidence | Capture transaction |
| `HyDragonPersistenceStatus` | Sanitized HyDragon-owned entitlement/saga/encounter status | Raw bonded profiles or extension payloads |

Legacy local profile-projection and generic capture-event queue types, if still present during source cleanup, are superseded artifacts and are not an authority for the bonded runtime. Documentation and runtime composition must not route new bonded data through them.

## 6. Capability model

HyDragon compares capability names as strings so a binary remains safe when a manifest-compatible Tamework exposes a different optional enum set. Current gates are:

| Feature | Required capabilities | Failure behavior |
| --- | --- | --- |
| Full-dragon capture and roster | `BONDED_COMPANIONS`, `CAPTURE_POLICY`, `CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION`, `INTERACTION_EXTENSIONS`, `EVENTS` | Deny before a roll/spend; do not mutate generic persistence |
| Dragon Horn lifecycle | `BONDED_COMPANIONS` | Keep cards durable; disable mutation with bonded reason |
| Bonded sessions/cooldowns | `BONDED_COMPANIONS` | Do not create a projection or invent a local timer |
| Bonded paid revival | `BONDED_COMPANIONS` | Keep the profile `DEAD`; consume nothing |
| Soul Bond claim | `BONDED_COMPANIONS` | Keep/release the exact Egg reservation according to saga phase |
| Miniwyvern attunement | `BONDED_COMPANIONS` | Preserve current extension and essence |
| Miniwyvern abilities | `BONDED_COMPANIONS` | Detach behavior; preserve extension state |
| Dynamic encounters | Capture capability set above | Fail encounter admission/cleanup evidence closed |
| Tamework diagnostics | `DIAGNOSTICS` | Report diagnostic capability unavailable |

For every feature requiring `BONDED_COMPANIONS`, the advertised capability is followed by `api.bondedCompanions().availability()`. A capability bit with an unavailable authority is still feature-off, and its reason is included in the gate.

`TIMED_SUMMONING` and `PAID_REVIVAL` remain internal HyDragon feature labels for status compatibility, but their implementation is the bonded API/policy. They do not call Tamework's generic timed-summon or paid-command-revival services.

## 7. Data ownership

### 7.1 Tamework bonded authority

Tamework stores `bonded-companions.sqlite` separately from replacement persistence. It owns:

- stable profile ID, owner, roster, family, role, and revision;
- only `STORED`, `ACTIVE`, and `DEAD` state;
- complete snapshot and profile-first presentation data;
- one exact lease token/live projection while active;
- session expiry and summon cooldown policy state;
- death/revive state and atomic multi-item revive recipe;
- exact capture evidence and bounded cleanup/operation records;
- profile-keyed `Alechilles:HyDragon` extension data with CAS revision.

HyDragon never writes this data directly and never needs a live NPC UUID to identify a roster entry.

### 7.2 HyDragon entitlement and saga data

```text
PlayerSoulBondRecord
  schemaVersion
  playerUuid
  state: UNCLAIMED | PENDING | CLAIMED | NEEDS_RECONCILIATION
  operationId
  miniwyvernProfileId
  claimedAt

ConsumableOperation
  operation/source evidence
  phase
  bonded authority evidence
  refund/reconciliation evidence
```

This data proves one-lifetime eligibility and exactly-once Egg handling. It does not duplicate bonded state, health, appearance, lease, or extension contents.

### 7.3 Miniwyvern extension

The `Alechilles:HyDragon` document contains schema/kind identity, species, archetype and attunement evidence, ability scheduler/source evidence, progression, and preserved unknown fields. Tamework owns the row/revision; HyDragon owns the JSON meaning and codec.

### 7.4 Encounter records

Only plugin-controlled multi-stage encounters need HyDragon records: encounter/definition identity, world and region, phase, exact target UUID while present, credited players, timing, cooldown, and immutable definition snapshot. Ordinary asset spawns do not create records.

## 8. Runtime lifecycle

### Setup and start

1. Register HyDragon interaction/config codecs, command handlers, and world marker systems.
2. Connect to Tamework through the public accessor.
3. Load and validate HyDragon configs.
4. Open `hydragon-state.properties` and validate supported HyDragon-owned schemas.
5. Refresh capabilities and bonded availability.
6. Install gameplay, ability, and encounter runtimes only when their complete gates pass.
7. Register bonded change/capture listeners and interaction extensions exactly once.
8. Start bounded saga and encounter reconciliation.
9. Emit one structured status summary.

### Reload

Config reload replaces immutable repository snapshots only after complete validation. A failed reload keeps the last valid snapshot. Bonded roster policy reload follows Tamework's registered custom-asset lifecycle rather than being copied into HyDragon Java state.

### Shutdown

Stop new work, close subscriptions/runtimes, and release references. The bonded runtime, not HyDragon shutdown, classifies and stores active projections. Shutdown code must not independently despawn, duplicate, or rewrite a companion.

## 9. Failure-safety rules

- Validate capability, authority, owner/profile/revision, policy, item, and world context before positive mutation.
- Treat callbacks/events as at-least-once and reuse operation IDs.
- Never reconstruct success from item metadata or a live NPC alone.
- If Tamework returns unavailable/unknown, retain the operation for exact evidence recovery instead of retrying under a new ID.
- If a bonded extension CAS conflicts, reload and merge only the owned fields with a bounded retry.
- If HyDragon's entitlement store is unavailable, new Egg/attunement/saga operations remain disabled; existing Tamework bonded profiles are not rewritten.
- Invalid family, owner, role, lease, extension, or capture evidence fails closed.
- Missing or out-of-range Tamework fails the required dependency. Missing bonded capability never falls back to old generic services.
- Diagnostics are bounded and redacted. Player/profile IDs and full snapshots do not belong in exported aggregate status.

## 10. Configuration and asset map

| Path | Purpose |
| --- | --- |
| `manifest.json` | Entry point, Hytale version, required Tamework range, asset-pack flag |
| `pom.xml` | Java build, explicit Tamework JAR, root-layout resource mapping, tests |
| `Server/Tamework/Items/Commands/HyDragonDragonHorn.json` | Shared `BondedCompanions` Horn roster |
| `Server/Tamework/Items/Spawners/HyDragonDraconicStone*.json` | Resolved-attempt capture into the full-dragon family |
| `Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json` | Full-dragon roles, limits, timers, revive recipe, feature toggles |
| `Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json` | Miniwyvern role, limits, timers, revive recipe, feature toggles |
| `Server/Tamework/Companion/HyDragon*.json` | Movement, placement, and normal command behavior only |
| `Server/HyDragon/DragonSpecies/*.json` | Species roles, capture difficulty, spawn/mount metadata |
| `Server/HyDragon/MiniwyvernArchetypes/*.json` | Archetype and ability definitions |
| `Server/HyDragon/Encounters/*.json` | Plugin-controlled encounter definitions; no population-group field |
| `Server/Languages/{en-US,pt-BR,de-DE,fr-FR,es-ES}/server.lang` | Matching player-facing catalogs |

The old HyDragon population-group assets are intentionally absent because bonded family policies own capacity. The two `TwCompanion` assets intentionally do not declare generic `Travel`, `Summon`, or `Revive` persistence blocks.

## 11. Acceptance criteria

- The packaged JAR contains the entry point, manifest, current assets, both bonded roster policies, and no obsolete HyDragon population groups.
- Manifest, Maven, and bridge dependency versions agree.
- Every bonded feature requires `BONDED_COMPANIONS`; capture/encounters require their additional capture/event contracts.
- A compatible but bonded-unavailable Tamework produces feature-specific diagnostics and no generic mutation.
- Full dragons and Miniwyverns use the same roster ID but independent family policy.
- HyDragon local persistence contains entitlement/saga/encounter authority only; Miniwyvern domain data uses bonded extension CAS.
- Dynamic encounter eligibility accepts only a confirmed `ACTIVE` full-dragon profile with an exact lease in the candidate world and an avatar-flight role.
- No active bonded runtime path uses generic command-family, population, timed-summon, profile-data, paid-command-revival, or replacement-persistence evidence.
- Clean tests, packaged verification, and [fresh-world acceptance](../testing/bonded-companion-integration-checklist.md) pass before release.
