# HyDragon Plugin Architecture Specification

Status: Draft implementation contract
Target HyDragon release: first plugin release compatible with Tamework `3.x`
Required Tamework range: `>=3.0.0 <4.0.0`

## 1. Purpose

HyDragon currently ships as a root-layout asset pack. This specification converts it into one combined Java plugin and asset pack without requiring an initial mass move of `Common/`, `Server/`, or `manifest.json`.

The Java layer owns HyDragon-specific state and behavior. Assets remain responsible for models, textures, items, recipes, NPC roles, static spawn data, effects, projectiles, audio, and localization. Generic companion mechanics remain in Tamework.

Related HyDragon specifications:

- [Suite index and traceability](README.md)
- [Capture, summoning, and maintenance](capture-summoning-maintenance.md)
- [Soul Bond and Miniwyvern](soul-bond-miniwyvern.md)
- [Dragon content and encounters](dragon-content-encounters.md)

Normative Tamework dependencies:

- [Tamework HyDragon integration suite](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/README.md)
- [Integration contract](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/integration-contract.md)
- [Capture policy](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/capture-policy.md)
- [Bonded vessels](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/bonded-vessels.md)
- [Population groups](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/population-groups.md)
- Deferred post-MVP: [Companion inventory](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/companion-inventory.md)

## 2. Architectural decisions

1. HyDragon becomes one distributable JAR containing Java classes and the existing asset pack.
2. The first conversion keeps `Common/`, `Server/`, and `manifest.json` at the repository root and configures Maven to package them at the JAR root.
3. A later move to `src/main/resources` is optional and must be a deliberate, separately tested migration. It is not a prerequisite for the plugin conversion.
4. HyDragon depends on Tamework `>=3.0.0 <4.0.0` and uses only Tamework's public API and documented asset contracts. The new integration surface targets public API `0.9.0`; individual capability checks remain authoritative.
5. Runtime features perform capability checks even when the manifest version range is satisfied.
6. Miniwyvern is Soul Bond-exclusive and cannot be captured by a Draconic Stone.
7. Tamework's Flightmaster's Talisman is the only flight-unlock item. HyDragon has no external flight-mod dependency or compatibility bridge in scope.

## 3. Requirements

### Packaging and identity

- **HYD-ARCH-001:** The build MUST produce one plugin JAR that contains compiled Java classes, `manifest.json`, and the complete `Common/` and `Server/` trees.
- **HYD-ARCH-002:** The initial Maven build MUST consume the current root-layout assets directly. It MUST NOT require moving those assets into `src/main/resources`.
- **HYD-ARCH-003:** `manifest.json` MUST declare `Main`, keep `IncludesAssetPack: true`, and declare `Alechilles:Alec's Tamework!` with range `>=3.0.0 <4.0.0`.
- **HYD-ARCH-004:** Public IDs already shipped by the asset pack MUST remain stable unless a migration rule is documented. New Java types, profile-data keys, config IDs, and commands MUST use the HyDragon namespace.

### Runtime boundary

- **HYD-ARCH-005:** HyDragon MUST integrate through the Tamework public API, documented asset families, registered interaction extensions, events, profile data, and declared capability identifiers. It MUST NOT import Tamework implementation packages or use reflection to reach internal services.
- **HYD-ARCH-006:** Startup MUST check all required Tamework capabilities and record the result in a queryable feature-status registry.
- **HYD-ARCH-007:** A missing required capability MUST fail the affected feature closed, leave unrelated HyDragon assets usable, and emit one actionable diagnostic naming the missing capability and required Tamework range.
- **HYD-ARCH-008:** Java services MUST be separated by domain: integration/capabilities, persistence, Draconic Stone lifecycle, Soul Bond, Miniwyvern archetypes, special encounters, migration, and diagnostics.
- **HYD-ARCH-009:** Balance and content choices MUST be data-driven under `Server/HyDragon/`; Java MUST enforce lifecycle, ownership, atomicity, and behavior that assets cannot safely express.

### Persistence and safety

- **HYD-ARCH-010:** HyDragon-owned player and profile records MUST contain a schema version and support forward, idempotent migrations without deleting an unknown or unsupported record.
- **HYD-ARCH-011:** Item consumption, profile creation/linking, archetype changes, repairs, and any later inventory mutations MUST be transactional or compensating. Retrying an event after interruption MUST NOT create a second companion or consume the same input twice.
- **HYD-ARCH-012:** World/entity access and delayed work MUST obey the game thread-affinity contract. Deferred tasks MUST retain stable identifiers and resolve live entities on the owning world thread rather than retaining component objects.
- **HYD-ARCH-013:** The plugin MUST expose structured startup diagnostics and an operator-readable status command covering version, capabilities, config load errors, disabled features, migration results, and orphaned links.
- **HYD-ARCH-014:** Automated release verification MUST prove that the JAR contains its entry point, manifest, assets, service metadata if used, and no duplicate or development-only resource trees.

## 4. Repository and package layout

### 4.1 Initial repository layout

```text
HyDragon/
  Common/                         # existing client/shared assets; unchanged initially
  Server/                         # existing server and Tamework assets; unchanged initially
  manifest.json                   # packaged at archive root
  pom.xml
  src/
    main/java/com/alechilles/hydragon/
    test/java/com/alechilles/hydragon/
  docs/specs/
```

Maven must configure the three asset inputs explicitly:

- `manifest.json` -> JAR root
- root `Common/` -> `Common/`
- root `Server/` -> `Server/`

Resource includes must be narrow enough that `HyDragon.zip`, generated output, documentation, IDE metadata, and repository files are not accidentally embedded. A future resource relocation may place the same paths beneath `src/main/resources`, but only after comparing archive contents and validating every asset reference.

### 4.2 Manifest target

The conversion must preserve the existing group and name while adding a Java entry point and narrowing the dependency contract:

```json
{
  "Group": "Alechilles",
  "Name": "HyDragon",
  "Main": "com.alechilles.hydragon.HyDragonPlugin",
  "Dependencies": {
    "Alechilles:Alec's Tamework!": ">=3.0.0 <4.0.0"
  },
  "IncludesAssetPack": true
}
```

Other existing manifest metadata remains unchanged unless release packaging requires an ordinary version/server-version update. `SubPlugins` is not used for the primary HyDragon entry point.

## 5. Java component model

| Component | Responsibility | Must not own |
| --- | --- | --- |
| `HyDragonPlugin` | Lifecycle orchestration, service construction, orderly shutdown | Domain logic |
| `TameworkBridge` | Public API acquisition, capability checks, event/extension registration | Tamework internals |
| `HyDragonConfigRepository` | Load and validate `Server/HyDragon/*` domain configs | NPC or item asset parsing already owned by Hytale/Tamework |
| `HyDragonDataStore` | Player ledger, encounter records, schema migrations | Tamework's canonical companion profile |
| `DraconicStoneService` | Capture outcomes, vessel maintenance policy, repair orchestration | Generic capture transaction or vessel identity |
| `SoulBondService` | Once-per-player claim and stable Miniwyvern link | Generic population admission |
| `MiniwyvernArchetypeService` | Attunement and domain-specific abilities | Generic attachment/inventory implementation |
| `DragonEncounterService` | Weather/player-gated and multi-stage encounters | Static weighted world spawning |
| `MigrationService` | Non-destructive conversion of legacy stone/Miniwyvern state | Silent deletion or arbitrary winner selection |
| `HyDragonDiagnostics` | Status snapshots, counters, validation errors, orphan reports | Player-facing gameplay state |

Implementations may use different class names, but these ownership boundaries are normative.

## 6. Integration capability model

Capability identifiers and exact API signatures are defined by the Tamework [integration contract](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/integration-contract.md). HyDragon must centralize them in one adapter rather than scattering version checks.

Feature gates:

| HyDragon feature | Required Tamework contract | Failure behavior |
| --- | --- | --- |
| Probabilistic/tiered capture | [Capture policy](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/capture-policy.md) | Reject capture before channel completion; keep item/NPC unchanged |
| Persistent Draconic Stone | [Bonded vessels](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/bonded-vessels.md) | Disable capture and summon interactions for bonded stones |
| One active full dragon / one Miniwyvern | [Population groups](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/population-groups.md) | Deny admission; never bypass the cap locally |
| Soul Bond creation | Tamework `COMPANION_PROVISIONING`, population groups, and the [integration contract](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/integration-contract.md) | Disable new claims; preserve existing entitlement/profile data |
| Soul Bond metadata and archetypes | Tamework profile data and event API in the [integration contract](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/integration-contract.md) | Disable mutation; preserve existing data |
| Deferred Miniwyvern backpack | Future [companion inventory](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/companion-inventory.md) | Not queried or registered in the initial release; capability-gate the later update |

Version acceptance is necessary but not sufficient. Capability checks protect development snapshots, selectively disabled modules, and compatible future releases that omit optional features.

## 7. Data ownership

### 7.1 Tamework-authoritative data

Tamework remains authoritative for:

- companion profile ID, ownership, lifecycle, live/dead/lost state, and active projection;
- provisioned-profile identity and `PROVISIONED_DORMANT` recovery state;
- bonded-vessel link and anti-duplication token;
- population-group admission;
- generic captured health, name, attachments, and progression; companion inventory contents join this authority only after the deferred system ships;
- capture roll result and committed capture transaction.

HyDragon reads or mutates these only through the public integration contract.

### 7.2 HyDragon player record

```text
HyDragonPlayerRecord
  schemaVersion
  playerUuid
  soulBond:
    state                 UNCLAIMED | PENDING | CLAIMED | NEEDS_RECONCILIATION
    miniwyvernProfileId   nullable
    claimedAt             nullable
    migrationStatus       NONE | MIGRATED | NEEDS_OPERATOR
  processedOperationIds   bounded/idempotency metadata
```

`PENDING` exists so a crash between entitlement reservation and profile creation can be reconciled. A pending operation must either finish linking the created profile or return to `UNCLAIMED`; it must never create a second Miniwyvern.

### 7.3 HyDragon profile extension data

Store namespaced profile data, not a second companion profile:

```text
HyDragonProfileData
  schemaVersion
  companionKind          FULL_DRAGON | SOULBOUND_MINIWYVERN
  speciesId
  archetypeId            nullable for full dragons
  lastAppliedOperationId nullable
```

The generic vessel/lifecycle record, rather than this extension, is authoritative for stored, active, dead/damaged, lost, and unavailable state. Unknown fields and unknown future enum values must be preserved during migration. If an unknown state cannot be interpreted safely, mutation is disabled for that record and diagnostics must identify it.

### 7.4 Encounter record

Only plugin-controlled multi-stage encounters need HyDragon persistence:

```text
DragonEncounterRecord
  schemaVersion
  encounterId
  definitionId
  worldId
  regionKey
  phase
  targetNpcId            nullable
  eligiblePlayerIds
  phaseStartedAt
  cooldownUntil
```

Ordinary `WorldNPCSpawn` and `BeaconNPCSpawn` assets do not create HyDragon records.

## 8. Runtime lifecycle

### Startup

1. Load and structurally validate HyDragon configs.
2. Acquire the Tamework public API and verify the manifest-supported version.
3. Query capabilities and populate feature gates.
4. Register namespaced interaction extensions and event listeners exactly once.
5. Open HyDragon persistence and run idempotent schema migrations.
6. Reconcile pending Soul Bond operations, vessel/profile references, and active encounter records.
7. Publish one structured readiness report.

### Reload

HyDragon domain configs may be reloadable only where an atomic snapshot can replace the old one. A failed reload keeps the last valid snapshot. Reload does not assume `/tw reloadconfig` refreshes role-scoped Tamework families; operators must use the lifecycle documented for the affected Tamework asset type.

### Shutdown

Stop new operations, cancel scheduled ability/encounter tasks, flush durable state, unregister listeners if supported, and release references. Shutdown must not despawn or duplicate a companion outside Tamework's lifecycle contract.

## 9. Failure-safety rules

- Validate ownership, eligibility, capacity, inputs, and capabilities before consuming an item.
- Treat callbacks and events as at-least-once. Use operation IDs or current-state comparisons to make handlers idempotent.
- If a Tamework transaction fails, HyDragon must not independently patch the item or NPC into the expected success state.
- If HyDragon persistence fails after a Tamework commit, record a recoverable pending reconciliation; do not issue a second capture/profile-create operation.
- Do not delete duplicate or orphaned legacy data automatically. Quarantine it from activation and surface an operator action.
- Never fall back from Soul Bond-exclusive Miniwyvern creation to ordinary capture.
- Never create a Miniwyvern directly when `COMPANION_PROVISIONING` is unavailable.
- Never bypass population admission because the population service is unavailable.

## 10. Configuration and asset map

| Path | Layer | Purpose |
| --- | --- | --- |
| `manifest.json` | Packaging | Entry point, combined asset-pack flag, dependency range |
| `pom.xml` | Packaging | Java build and explicit root resource mappings |
| `src/main/java/com/alechilles/hydragon/` | Plugin | Services and integration adapter |
| `src/test/java/com/alechilles/hydragon/` | Tests | Unit/contract/migration tests |
| `Server/HyDragon/DragonSpecies/*.json` | HyDragon config | Species difficulty, capture metadata, mount policy |
| `Server/HyDragon/StoneMaintenance/*.json` | HyDragon config | Death-repair policy and future extension flags; swap cooldown is authored in Tamework vessel config |
| `Server/HyDragon/MiniwyvernArchetypes/*.json` | HyDragon config | Attunement and ability definitions |
| `Server/HyDragon/Encounters/*.json` | HyDragon config | Plugin-controlled encounter definitions |
| `Server/Tamework/**` | Tamework assets | Generic companion, capture, vessel, population, and inventory declarations |
| `Common/**`, `Server/Item/**`, `Server/NPC/**` | Content assets | Models, items, recipes, roles, effects, projectiles, audio, localization |

Every domain-config codec must reject invalid identifiers, ranges, negative durations, mutually incompatible modes, and references to missing required content at load time. Validation errors must name the asset ID and field path.

## 11. Migration from the current asset pack

1. Add Maven and Java source directories without moving root assets.
2. Add `Main`, retain `IncludesAssetPack: true`, and update the dependency range from `>=2.17.0` to `>=3.0.0 <4.0.0`.
3. Package the unmodified asset trees and compare the JAR archive against the pre-conversion asset-pack ZIP.
4. Introduce the public API adapter and diagnostics before enabling domain runtime features.
5. Migrate current Draconic Stone and Miniwyvern behavior according to the dedicated specifications; never silently reinterpret a filled legacy stone.
6. Remove any third-party flight dependency or documentation. Keep the existing Nordic Drake `TameworkAvatarFlight` integration and gate it with Tamework's Flightmaster's Talisman.
7. Only after parity is proven may a separate change move assets into `src/main/resources`.

## 12. Acceptance criteria

- A clean Maven build produces exactly one loadable JAR with `HyDragonPlugin`, `manifest.json`, `Common/`, and `Server/` at the expected archive paths.
- The plugin loads with a compatible Tamework 3.x build and reports every required capability.
- Missing `COMPANION_PROVISIONING` disables only new Soul Bond claims; existing Miniwyverns and the full-dragon stone loop remain intact.
- A Tamework version below 3.0.0 or at/above 4.0.0 is rejected by the declared dependency.
- A test Tamework build missing one optional capability disables only the mapped HyDragon feature and preserves all data.
- No production Java class imports a Tamework internal package.
- Restarting during each multi-step mutation converges to one valid companion/profile/item outcome without duplicated items or companions.
- Config reload failure leaves the last valid configuration active.
- Packaging tests prove root-layout assets are included and `HyDragon.zip`, docs, build outputs, and IDE files are excluded.
- The Miniwyvern capture path remains unavailable even though companion inventory is not part of the initial release.
- Flying a configured dragon checks only Tamework's Flightmaster's Talisman.

## 13. Phased dependencies

| Phase | Deliverable | Entry dependency | Exit condition |
| --- | --- | --- | --- |
| A0 | Combined plugin packaging | Current asset pack | Loadable JAR with unchanged asset behavior |
| A1 | Public API adapter and diagnostics | Tamework 3.0.0+ with public API 0.9.0 integration capabilities | Capability matrix visible; features fail closed |
| A2 | Persistence and migration foundation | A1 | Versioned records and restart reconciliation tested |
| A3 | Capture/vessel integration | Tamework capture, vessel, and population specifications | [Capture specification](capture-summoning-maintenance.md) accepted |
| A4 | Soul Bond and archetypes | A2, A3, Tamework provisioning/population/profile contracts | [Soul Bond specification](soul-bond-miniwyvern.md) accepted |
| A5 | Dynamic encounters | A2, A4 | [Content specification](dragon-content-encounters.md) accepted |
| Post-MVP A6 | Miniwyvern backpack update | A4 plus the future inventory capability | Deferred [companion-inventory specification](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/companion-inventory.md) accepted |
