# Draconic Capture, Dragon Horn, and Revival Specification

Status: Bonded-companion implementation is present in source; packaged and live acceptance remain pending
Scope: Full-sized dragons, the shared Dragon Horn, bonded sessions, storage, and paid revival
Target dependency: Tamework `>=3.0.0 <4.0.0`

## 1. Purpose

Draconic Stones are consumable capture attempts, not storage containers. A player tranquilizes an eligible wild dragon, channels a tiered stone, and spends that stone when Tamework resolves the capture roll. A successful roll commits a complete bonded profile, removes the source NPC only after that profile is durable, and shows the companion as `STORED` in the Dragon Horn. A failed roll spends the stone but otherwise leaves the wild dragon unchanged.

The Dragon Horn is the one recurring roster interface for full dragons and the Soul Bond Miniwyvern. The physical Horn grants access to the acting player's roster; it is not the durable save and does not carry transferable ownership metadata.

Related documents:

- [Wyvern Egg, Soul Bond, and Miniwyvern](soul-bond-miniwyvern.md)
- [Plugin architecture](plugin-architecture.md)
- [Dragon content and encounters](dragon-content-encounters.md)
- [HyDragon - Tamework bonded integration contract](../integration/tamework-bonded-companions-contract.md)

## 2. Implemented boundary

| Concern | HyDragon owns | Tamework bonded runtime owns |
| --- | --- | --- |
| Capture content | Stone items, tiers, role allowlists, tranquilizer and encounter requirements, channel presentation, and balance | Preflight, one authoritative roll, resolved-attempt consumption, durable-before-removal capture, and idempotent result evidence |
| Roster | The Dragon Horn item and the `hydragon:dragon_horn` roster/family assets | Stable bonded profile IDs, `STORED`/`ACTIVE`/`DEAD`, full snapshots, leases, action validation, and profile-first cards |
| World NPC | Dragon roles, commands, movement, combat, mounts, and normal effects | One ephemeral projection per active profile, exact projection markers, cleanup, and store-on-nondeath convergence |
| Revival | Item IDs, quantities, and player-facing HyDragon content | Atomic cost preflight/escrow, `DEAD -> STORED`, idempotency, and recovery |

This path does not use Tamework's generic command-family roster, generic dormant profile, population-group evidence, timed-command-summon records, paid-command-revival API, generic lifecycle aliases, or replacement-persistence outbox.

## 3. Locked gameplay decisions

- Every eligible resolved roll consumes exactly one Draconic Stone, whether the roll succeeds or fails.
- Invalid targeting, failed preflight, lost eligibility, or interrupted channeling consumes nothing and obtains no random result.
- Capture requires `Tw_Status_Tranquilized`. There is no health-percentage eligibility threshold; missing health may still contribute to configured probability.
- Ancient/Mithril power guarantees capture only after every deterministic requirement passes.
- Success maps the wild role to its tamed role, snapshots the complete result, commits one stored profile, and then removes the source NPC.
- Success does not produce a filled stone and does not leave the captured source active in the world.
- The Dragon Horn uses one roster ID with separate full-dragon and Miniwyvern family policies.
- A bonded companion has exactly three player-visible states: `STORED`, `ACTIVE`, and `DEAD`.
- Any non-death disappearance resolves to `STORED`; only a positively confirmed death resolves to `DEAD`.
- Paid revive restores `DEAD -> STORED`. It never summons automatically.
- Session duration and summon cooldown are policy values. `0` disables the corresponding timer.
- HyDragon has not shipped this pre-release persistence design, so no migration or compatibility reader is required.

## 4. Capture contract

### 4.1 Stone tiers

| Tier | Power | Quality | Item ID |
| --- | ---: | --- | --- |
| Iron | 1 | Common | `Draconic_Stone` |
| Thorium | 2 | Uncommon | `Draconic_Stone_Thorium` |
| Cobalt | 3 | Rare | `Draconic_Stone_Cobalt` |
| Adamantium | 4 | Epic | `Draconic_Stone_Adamantium` |
| Ancient | 5 | Legendary | `Draconic_Stone_Ancient` |

All tiers inherit `HyDragonDraconicStone`; only the item ID and power change. Species policies own minimum power, resistance, chance modifiers, and `GuaranteedAtPower`. Every current full-dragon capture policy uses power 5 as its guarantee threshold.

### 4.2 Eligibility

The shared spawner policy accepts these wild roles and maps them to their tamed roles:

- `NordicDrake` -> `Tamed_NordicDrake`
- `Hydra` -> `Tamed_Hydra`
- `RockDrakeT1` -> `Tamed_RockDrakeT1`
- `RockDrakeT2` -> `Tamed_RockDrakeT2`
- `RockDrakeT3` -> `Tamed_RockDrakeT3`

Miniwyvern roles are deliberately absent. Capture also requires a registered `HyDragon_Dragon_Horn`, valid range/world context, the tranquilized effect, and policy capacity. Nordic Drake uses the same ordinary capture path as every other full dragon.

### 4.3 State and transaction

```text
READY
  -> CHANNELING                 preflight succeeds
  -> READY                      channel canceled or eligibility lost; no spend
  -> RESOLVING                  terminal requirements still pass
  -> FAILED_ROLL                one stone spent; target unchanged
  -> STORED bonded profile      one stone spent; snapshot durable; source removed
```

For a successful attempt, Tamework must:

1. Revalidate the player, source stack, Horn access, target, role, tranquilizer, policy, and world context without consuming the item.
2. Resolve one authoritative result under a stable operation/idempotency key.
3. Reserve exactly one source stone for either terminal result.
4. On success, read the complete NPC snapshot before removing or rewriting the source.
5. Commit one `hydragon:dragon_horn` / `hydragon:full_dragons` profile in `STORED` state.
6. Remove the exact source NPC only after durable profile evidence exists.
7. Finalize the item spend and publish one bounded capture result/evidence record.

Duplicate callbacks replay the recorded result. They must not roll again, spend another stone, create another profile, or remove another NPC. If success becomes ambiguous, encounter cleanup treats it as unresolved until exact bonded capture evidence proves capture or absence.

### 4.4 Channel and completion presentation

`Draconic_Stone.json` owns the channel beam, homing motes, progress bar, and complete/cancel interaction phases. `HyDragonDraconicStone.json` applies the target aura and plays `SFX_Tamework_Capture_Channel_Dark_Magic` while channeling. The item declares exactly one `CaptureBurstParticleSystem` on the completion phase; the channel sound is distinct from that burst, and capture persistence code must not start a second copy of the completion effect.

## 5. Dragon Horn contract

The canonical command asset is:

```text
ItemId: HyDragon_Dragon_Horn
CommandConfigId: HyDragonDragonHorn
RosterStorage: BondedCompanions
BondedRosterId: hydragon:dragon_horn
MembershipMode: LinkedOnly
LinkEnabled: false
LinkUseTogglesMembership: false
```

Its allowlist contains all five tamed full-dragon roles and `Tamed_Wyvern_Mini`. Membership is created only by bonded capture or bonded provisioning, so ordinary link/unlink interactions cannot create a profile or move one between owners.

Each card is keyed by stable bonded profile ID and renders from the durable snapshot before consulting a live entity. Capture, summon, store, revive, and relog must all show the same resolved display name, species, gender, health, needs, happiness, breeding, attachments, progression, traits, talents, life stage, command settings, and HyDragon extension presentation applicable to that profile. A matching active projection may refresh volatile data, but it is not the card identity.

The allowed actions are state-driven:

| State | Primary lifecycle action | Result |
| --- | --- | --- |
| `STORED` | Summon | Creates one exact ephemeral projection and changes the profile to `ACTIVE` when policy permits |
| `ACTIVE` | Dismiss/store | Snapshots and removes the exact projection, then changes the profile to `STORED` |
| `DEAD` | Revive | Atomically pays the configured recipe and changes the profile to `STORED`; the player summons separately |

## 6. Full-dragon family policy

`Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json` is authoritative for the current full-dragon values:

| Field | Current value |
| --- | --- |
| Roster ID | `hydragon:dragon_horn` |
| Family ID | `hydragon:full_dragons` |
| Maximum owned | `0` (unlimited) |
| Maximum active | `1` |
| Session duration | `600` seconds |
| Summon cooldown | `300` seconds |
| Revive cost | `Revitalizing_Essence x2` and `Draconic_Essence x4` |
| Features | Capture, summon, dismiss, revive enabled; direct provision disabled |

Capacity is a bonded family rule, not a generic population group. A Miniwyvern uses its own family capacity, so one full dragon and one Miniwyvern may be active together. Raising `MaximumActive` changes the family limit declaratively. Setting either timer to `0` disables that timer without changing the storage model.

## 7. Lease and disappearance semantics

An `ACTIVE` profile owns one exact lease token and one exact projection. A second projection is never an alternate authority.

These events store the companion instead of marking it lost or unloaded:

- manual Dismiss;
- session expiration;
- owner logout;
- world transfer;
- missing projection reconciliation;
- stale or duplicate projection cleanup;
- any other disappearance without confirmed death.

Storage captures the latest complete state, removes the exact live projection when present, releases the family active slot, and starts the configured summon cooldown. If the operation is interrupted, the same operation must converge to one `ACTIVE` projection or one `STORED` profile. The bonded UI never exposes `UNLOADED`, `LOST`, `STORING`, `RESTORING`, or generic lifecycle aliases.

## 8. Death and paid revival

A confirmed active-projection death records the final snapshot and changes the same profile to `DEAD`. Despawn, chunk unload, logout, travel, or missing-entity detection cannot infer death.

The Horn quotes the complete configured recipe before payment. All components form one AND recipe, so missing any component denies the operation without consuming anything. The current full-dragon recipe is two Revitalizing Essences plus four Draconic Essences.

A successful revive:

1. revalidates owner, roster, family, profile ID, expected revision, `DEAD` state, and the complete current recipe;
2. atomically reserves/consumes every component once;
3. restores the same profile to `STORED` with no live projection;
4. publishes the new revision so the same Horn card refreshes immediately.

Summoning after revive is a separate policy-gated action. A restart or duplicate click must converge to one charge and one `STORED` profile, never a duplicate projection, double charge, or free revive.

## 9. Capability and failure behavior

Capture and special encounters require `BONDED_COMPANIONS`, `CAPTURE_POLICY`, `CAPTURE_RESOLVED_ATTEMPT_CONSUMPTION`, `INTERACTION_EXTENSIONS`, and `EVENTS`. Horn lifecycle actions require a ready `BONDED_COMPANIONS` authority.

If Tamework is missing or outside `>=3.0.0 <4.0.0`, the manifest dependency rejects startup. If the compatible plugin lacks the bonded capability or its bonded database is unavailable, HyDragon disables the affected feature and reports the specific reason through `/hydragon status`; it never falls back to generic command-roster or persistence APIs.

For a failed live operation, record the first visible failure, run `/hydragon status`, then run `/tw debugdb status`, `/tw debugdb detail`, and `/tw debugdb export`. The Tamework export is bounded and redacted; include its printed path and the relevant server-log window in the report.

## 10. Superseded pre-release designs

The following designs are historical only and are not supported runtime paths:

- filled/active/damaged/lost/unavailable Draconic Stone items;
- stone durability, repair, summon, store, and re-capture flows;
- `Soul_Bound_Wyvern` as a separate recurring controller;
- `RosterStorage: OwnerCommandFamily` and `TameAndCommandLink` for HyDragon;
- HyDragon population-group assets for full dragons or Miniwyverns;
- generic timed-command-summon and generic paid-command-revival bindings.

Because none of these HyDragon persistence paths shipped, the first bonded release contains no migration reader, aliases, or compatibility conversion.

## 11. Acceptance criteria

- Invalid preflight and canceled channels consume nothing and obtain no roll.
- A resolved failed roll spends one stone and leaves the target unchanged.
- A successful capture spends one stone, persists a full `STORED` card, removes the source after durability, and dispatches one completion burst.
- Capture, summon, store, revive, relog, logout, and world transfer preserve one stable profile and complete card details.
- One full dragon and one Miniwyvern can be active concurrently; a second active member of either family follows that family's own policy.
- Finite sessions expire to `STORED`; a zero-duration policy never expires.
- Every non-death disappearance becomes `STORED`. Confirmed death alone becomes `DEAD`.
- Full-dragon revive consumes both configured cost components once, returns to `STORED`, and does not auto-summon.
- Exact bonded capture evidence prevents encounter cleanup from deleting or replacing a committed capture.
- No HyDragon bonded path uses a generic command-family roster, population record, timed-summon record, generic profile-data API, paid-command-revival API, or replacement-persistence evidence gate.
- Automated, packaged, and fresh-world checks in [the bonded integration checklist](../testing/bonded-companion-integration-checklist.md) pass before release.
