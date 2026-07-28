# HyDragon - Tamework Bonded Companions Integration Contract

Status: Source and asset integration implemented; packaged/live acceptance pending

- Primary mod: HyDragon `0.2.1`
- Required dependency: Alec's Tamework `>=3.0.0 <4.0.0`
- Tamework public API: `0.9.0`
- Primary capability: `BONDED_COMPANIONS`
- Shared roster ID: `hydragon:dragon_horn`
- Family IDs: `hydragon:full_dragons`, `hydragon:soulbound_mini`
- Extension namespace: `Alechilles:HyDragon`

## Ownership boundary

Tamework owns the durable bonded profile, its `STORED`/`ACTIVE`/`DEAD` state, complete NPC snapshot, policy, lease, cooldown/revive summary, exact projection cleanup, idempotent operations, and namespaced extension row/revision. These records live in Tamework's independent `bonded-companions.sqlite` store and do not depend on replacement-persistence readiness or outbox evidence.

HyDragon owns dragon species/content, Draconic Stone balance and encounter requirements, the one-lifetime Miniwyvern entitlement and Egg saga, Miniwyvern extension-document semantics, elemental abilities, and dynamic encounter policy.

The stable bonded profile ID crosses this boundary. A live NPC UUID appears only as exact lease/capture/cleanup evidence and is never a roster, entitlement, or UI identity.

## Shared Horn and family policies

Both families appear in the one `HyDragon_Dragon_Horn` panel. `HyDragonDragonHorn.json` uses:

```text
RosterStorage: BondedCompanions
BondedRosterId: hydragon:dragon_horn
LinkEnabled: false
LinkUseTogglesMembership: false
```

Family policy remains independent:

| Family | Acquisition | Maximum owned | Maximum active | Session | Cooldown | Revive recipe |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| `hydragon:full_dragons` | `StoreBondedCompanion` capture | `0` (unlimited) | 1 | 600 s | 300 s | `Revitalizing_Essence x2` + `Draconic_Essence x4` |
| `hydragon:soulbound_mini` | bonded `provision` after Wyvern Egg spend | 1 | 1 | 900 s | 180 s | `Revitalizing_Essence x1` + `Draconic_Essence x2` |

`0` is the disabled sentinel for session/cooldown values. Capacity is resolved by bonded family, so one full dragon and one Miniwyvern may be active simultaneously.

## Public hooks

HyDragon uses only public Tamework contracts:

- `TameworkApiCapability.BONDED_COMPANIONS`;
- `TameworkApi.bondedCompanions()` and its current `BondedCompanionAvailability`;
- owner/shared-roster profile list;
- stored Miniwyvern provision;
- owner/profile/namespace-scoped extension read and compare-and-set;
- bonded profile-change subscription for summon, store, death, revive, and cleanup;
- durable bonded-capture resolved events;
- exact capture-evidence lookup by owner, shared roster, and source NPC UUID;
- existing capture-policy, resolved-attempt, interaction-extension, event, and diagnostics capabilities where the feature needs them.

Profile lifecycle actions are routed by Tamework's Horn panel through stable profile ID and expected revision. HyDragon does not ask callers to discover a live NPC UUID for summon, store, or revive.

## Runtime semantics

### Full-dragon capture

1. Draconic Stone preflight requires an allowed wild role, `Tw_Status_Tranquilized`, Horn access, world/range validity, family policy, and any special encounter requirement.
2. One resolved attempt spends one stone on success or failure; preflight/channel denial spends nothing.
3. Success role-maps and snapshots the source, commits a `STORED` profile in the full-dragon family, then removes the exact source NPC.
4. Source removal and item finalization cannot precede durable bonded evidence.
5. Dynamic encounters listen for the bonded capture event and fall back to exact bounded evidence lookup when event delivery is missed or ambiguous.

### Miniwyvern Soul Bond

1. HyDragon reserves the one-lifetime entitlement and exact Egg operation.
2. After durable Egg consumption, it calls bonded `provision` for `Tamed_Wyvern_Mini`.
3. Tamework returns the canonical profile in `STORED` with no active lease.
4. HyDragon initializes or verifies one neutral extension document through compare-and-set.
5. Only then does HyDragon close the entitlement against that profile ID.
6. The player summons through the same Horn; the Egg is not a recurring controller.

### Lifecycle

```text
STORED --summon--> ACTIVE
ACTIVE --any non-death exit--> STORED
ACTIVE --confirmed death--> DEAD
DEAD --paid revive--> STORED
```

Non-death exits include dismissal, session expiration, logout, world transfer, missing projection reconciliation, and duplicate/stale cleanup. Revive never auto-summons. A profile card is built from its durable snapshot in every state and may only enrich volatile values from a matching active lease.

### Miniwyvern extension and abilities

The extension is one versioned, unknown-field-preserving document containing companion kind/species, archetype and attunement evidence, ability scheduler/source state, progression, and future fields. Attunement and ability writers merge their owned fields with revision-fenced CAS rather than replacing the document.

`MiniwyvernAbilityRuntime` subscribes to bonded changes. It initializes against a confirmed `ACTIVE` Miniwyvern lease, detaches on store/death, and preserves the extension across every cycle.

### Active-dragon encounter eligibility

The high-altitude encounter lists the owner's shared Horn roster and selects a profile only when all of these are true:

- owner matches the candidate player;
- roster is `hydragon:dragon_horn`;
- family is `hydragon:full_dragons`;
- state is `ACTIVE`;
- an exact live lease exists in the candidate world;
- the tamed role maps to a species whose mount mode is `AVATAR_FLIGHT`;
- the player can access `Tamework_Flightmasters_Talisman`.

Stored/dead profiles, Miniwyverns, wrong-world leases, missing live UUIDs, and non-avatar-flight roles do not qualify.

## Prohibited fallbacks

Bonded dragons and Miniwyverns must not call or mutate Tamework's generic:

- companion provisioning or command-family roster APIs;
- timed-command-summon or paid-command-revival APIs;
- generic profile/profile-data APIs;
- population-group ownership/evidence APIs;
- lifecycle aliases or dormant profiles;
- replacement-persistence database, evidence gate, incident, or outbox.

HyDragon-local legacy profile projection/queue records are also not an alternate companion authority. The only HyDragon-local durable companion-related authority is the one-lifetime entitlement and its consumable saga evidence.

## Capability and dependency behavior

`TameworkBridge` reads the current capability set and then checks bonded availability. It refreshes that snapshot for requests so a bonded authority that becomes ready after startup can enable features without reconstructing the bridge.

- Tamework missing or outside `>=3.0.0 <4.0.0`: required manifest dependency fails before gameplay.
- Compatible Tamework without `BONDED_COMPANIONS`: affected feature gate names the missing capability.
- Capability advertised but bonded authority unavailable: affected gate includes the authority's specific reason.
- Capture/encounter contracts incomplete: capture is denied before roll/spend and encounter work fails closed.
- Extension CAS conflict: reload/merge/retry is bounded; another writer is never overwritten silently.
- HyDragon entitlement journal unavailable: new claim/attunement sagas are disabled without rewriting bonded profiles.
- Unrelated HyDragon content remains independently gated where runtime composition permits.

## Diagnostics

Use `/hydragon status` for HyDragon version, config, capability, feature-gate, bonded-availability, and HyDragon-owned persistence information.

Use these Tamework commands for bounded persistence evidence:

```text
/tw debugdb status
/tw debugdb detail
/tw debugdb export
```

The export reports aggregate bonded readiness/counts and includes a redacted bonded bundle entry. It must not include player UUIDs, profile IDs, full snapshots, extension payloads, or raw database contents.

## Validation cases

- bonded capability and authority available;
- capability present but bonded authority unavailable;
- compatible Tamework with legacy generic capabilities but no bonded capability;
- Tamework missing or outside the required range;
- full dragon and Miniwyvern sharing one panel with independent family limits;
- capture, summon, store, death, revive, expiration, logout, transfer, restart, and duplicate cleanup;
- concurrent attunement and ability extension writers;
- immediate full-detail cards after every transition;
- dynamic encounter eligibility across active/stored/dead, wrong-family, wrong-world, and wrong-mount profiles;
- exact capture evidence at encounter cleanup boundaries;
- bounded/redacted diagnostics.

Automated source and contract tests cover the static integration. Packaged and live behavior remains pending until the explicit fresh-world acceptance handoff.
