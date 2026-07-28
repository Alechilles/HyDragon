# HyDragon Implementation Specification Suite

Status: Bonded-companion source integration implemented; packaged and fresh-world acceptance pending
Required Tamework range: `>=3.0.0 <4.0.0`
Required Tamework public API: `0.9.0` with `BONDED_COMPANIONS`

## 1. Product direction

HyDragon is a Java plugin and root-layout asset pack built on Tamework 3.x. HyDragon owns dragon content, capture balance, the Draconic Altar and economy, the once-per-player Wyvern Egg entitlement, Miniwyvern archetypes/abilities, encounters, effects, and localization.

Tamework's dedicated bonded-companion runtime owns the durable roster profile, complete NPC snapshot, three-state lifecycle, ephemeral projection lease, capacity/timer policy, profile-first Dragon Horn card, atomic revival, and namespaced extension storage. It is deliberately separate from permanent-world animal persistence and Tamework's generic command-roster systems.

Draconic Stones are consumed capture attempts. Success stores a full dragon in the Dragon Horn after durable snapshot commit and removes the source NPC; failure spends the resolved attempt and leaves the wild target unchanged. The Wyvern Egg provisions one stored Soul Bond Miniwyvern. Both families use the same Horn from then on.

## 2. Locked decisions

- The shared roster ID is `hydragon:dragon_horn`.
- Full dragons use family `hydragon:full_dragons`; Miniwyverns use `hydragon:soulbound_mini`.
- The only bonded states are `STORED`, `ACTIVE`, and `DEAD`.
- Any non-death disappearance, including logout, transfer, expiration, missing projection, or duplicate cleanup, becomes `STORED`.
- Only confirmed death becomes `DEAD`; paid revive returns to `STORED` and never auto-summons.
- Cards and actions use stable bonded profile IDs, never live NPC UUIDs or item metadata.
- Complete snapshot details render from durable profile data immediately after capture, summon, store, revive, and relog.
- Session durations and summon cooldowns are family policy; `0` disables a timer.
- Full dragons and Miniwyverns share a panel but retain independent acquisition, ownership/active limits, timers, and revive recipes.
- `Wyvern_Egg` is consumed for the one lifelong Miniwyvern claim. No recurring `Soul_Bound_Wyvern` item exists.
- Miniwyvern attunement, ability, progression, and future fields use the owner/profile-scoped `Alechilles:HyDragon` bonded extension namespace.
- Flying dragons use Tamework's Flightmaster's Talisman only.
- HyDragon has not shipped, so pre-release generic-roster and bonded-vessel data are not migrated.

## 3. Documents

| Document | Authority |
| --- | --- |
| [Plugin architecture](plugin-architecture.md) | Packaging, public API boundary, capabilities, persistence ownership, recovery, and diagnostics |
| [Draconic capture, Dragon Horn, and revival](capture-summoning-maintenance.md) | Stone tiers/spending, stored capture, shared Horn, full-dragon family policy, leases, and revival |
| [Wyvern Egg, Soul Bond, and Miniwyvern](soul-bond-miniwyvern.md) | Lifelong entitlement, stored provisioning, extension data, abilities, and Miniwyvern family policy |
| [Dragon content and encounters](dragon-content-encounters.md) | Materials, Altar/recipes, species, drops, mounts, spawning, and special encounters |
| [Tamework bonded integration contract](../integration/tamework-bonded-companions-contract.md) | Cross-mod ownership and prohibited generic fallbacks |
| [Compatibility matrix](../integration/tamework-bonded-compatibility-matrix.md) | Version/capability behavior and remaining live-test gap |
| [Fresh-world integration checklist](../testing/bonded-companion-integration-checklist.md) | Packaged/manual sequence and failure evidence |

## 4. System boundary

| Layer | Owns | Does not own |
| --- | --- | --- |
| HyDragon assets/config | Items, recipes, roles, spawns, capture tier data, family policy values, effects, and localization | Durable bonded transitions or live-projection identity |
| HyDragon plugin | Entitlement saga, extension payload semantics, archetype abilities, special encounters, capability diagnostics, and bounded compensation | A second companion profile, roster state, lease, or revive transaction |
| Tamework bonded runtime | Stable profile/roster identity, full snapshot, `STORED`/`ACTIVE`/`DEAD`, projection lease, duplicate cleanup, panel actions, extension CAS, and atomic revive | Dragon lore, elemental combat, Altar economy, or encounter policy |
| Hytale runtime | Asset and ECS execution on the owning world thread | Cross-mod transaction authority |

## 5. Current declarative policies

| Family | Acquisition | Owned | Active | Session | Cooldown | Revive recipe |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| `hydragon:full_dragons` | Draconic Stone capture | Unlimited (`0`) | 1 | 600 s | 300 s | `Revitalizing_Essence x2` + `Draconic_Essence x4` |
| `hydragon:soulbound_mini` | Wyvern Egg provisioning | 1 | 1 | 900 s | 180 s | `Revitalizing_Essence x1` + `Draconic_Essence x2` |

These are current content values, not hard-coded Java limits. Both families may be active at once because capacity is resolved per bonded family.

## 6. Cross-cutting invariants

1. One stable bonded profile represents one companion across all projections and lifecycle states.
2. A live NPC is an ephemeral projection identified by a lease token, not the durable roster identity.
3. Every positive operation is revision-aware and idempotent; retry replays evidence instead of re-spending, re-rolling, re-provisioning, or re-spawning.
4. Capture is durable before source removal. Store snapshots before projection removal.
5. Item metadata can grant access but cannot establish ownership or fork a roster.
6. Positive mutations fail closed when their bonded, HyDragon journal, inventory, or world authority is unavailable.
7. World/entity work stays on the owning world thread; deferred work carries stable IDs.
8. HyDragon does not fall back to generic command-family, timed-summon, generic profile-data, population, paid-command-revival, or replacement-persistence APIs.

## 7. Implementation map

1. Tamework supplies the independent bonded database, public API, snapshot codec, lifecycle, projection manager, panel integration, diagnostics, and policy assets.
2. HyDragon routes full-dragon capture to `StoreBondedCompanion` and the shared Horn.
3. HyDragon provisions the one-lifetime Miniwyvern directly into the same roster and initializes its bonded extension document.
4. Miniwyvern attunement and abilities read/write the extension through compare-and-set and react to bonded state-change events.
5. Dynamic encounter eligibility queries a confirmed active full-dragon lease in the candidate world instead of population evidence.
6. HyDragon's old population assets and generic roster/timer/revive declarations are absent from the active asset path.
7. Contract, asset, and packaged-layout tests cover the source integration. Fresh-world gameplay remains the final acceptance gate.

## 8. Superseded historical designs

The following were unreleased development directions and are not supported behavior:

- filled or damageable bonded Draconic Stone vessels;
- `Soul_Bound_Wyvern` as a separate controller;
- `OwnerCommandFamily` / `TameAndCommandLink` for HyDragon companions;
- generic population groups for active dragon/Miniwyvern limits;
- generic timed-command-summon, dormant-profile, profile-data, and paid-command-revival paths;
- `LOST`, `UNLOADED`, `ROSTER_STORED`, or `DEAD_REVIVABLE` as bonded states.

No player migration or compatibility layer is required for those pre-release paths.

## 9. Definition of completion

The bonded redesign is complete only when:

- both families use one fully detailed, profile-keyed Horn panel;
- capture is durable-before-removal and never silently or partially succeeds;
- all non-death exits converge to `STORED`, death alone produces `DEAD`, and revive returns to `STORED`;
- finite and zero-duration policies behave as configured without negative-time bugs;
- Miniwyvern extension data survives every summon/store/relog cycle;
- active-dragon encounter eligibility uses one confirmed active full-dragon lease in the candidate world;
- ordinary Tamework animal/companion persistence remains unchanged under its full regression suite;
- clean Tamework and HyDragon suites, packaged asset/dependency checks, and the fresh-world checklist all pass.
