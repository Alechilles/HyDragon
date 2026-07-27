# Wyvern Egg, Soul Bond, and Miniwyvern Specification

Status: Bonded-companion implementation is present in source; packaged and live acceptance remain pending
Target dependency: Tamework `>=3.0.0 <4.0.0`

## 1. Purpose

The Wyvern Egg is a once-per-player ritual item that creates the player's lifelong Soul Bond Miniwyvern. Successful use consumes the Egg, provisions exactly one canonical bonded profile in `STORED` state, initializes HyDragon's namespaced Miniwyvern data, and places the profile in the same Dragon Horn roster as full dragons.

The Egg is an acquisition item, not a recurring controller. The Dragon Horn is the one summon, dismiss, command, and revive interface for both companion families. No `Soul_Bound_Wyvern` item exists.

Related documents:

- [Draconic capture, Dragon Horn, and revival](capture-summoning-maintenance.md)
- [Plugin architecture](plugin-architecture.md)
- [Dragon content and encounters](dragon-content-encounters.md)
- [HyDragon - Tamework bonded integration contract](../integration/tamework-bonded-companions-contract.md)

## 2. Locked decisions

- Miniwyverns are Soul Bond-exclusive and cannot be captured with a Draconic Stone.
- Each player can claim one Miniwyvern for life.
- A successful claim creates a `Tamed_Wyvern_Mini` profile in roster `hydragon:dragon_horn` and family `hydragon:soulbound_mini`.
- Provisioning returns the new profile as `STORED`; the player uses the Dragon Horn to summon it.
- Full dragons and Miniwyverns share one Horn panel but have independent family limits, session durations, cooldowns, acquisition permissions, and revival recipes.
- Any Miniwyvern non-death disappearance becomes `STORED`. Only confirmed death becomes `DEAD`.
- Paid revive returns the same profile to `STORED` and never auto-summons.
- Tamework owns the complete NPC snapshot and bonded lifecycle. HyDragon owns the one-lifetime entitlement and Miniwyvern domain data.
- Miniwyvern domain data uses Tamework's profile-keyed, namespaced bonded extension store rather than HyDragon's old generic/local profile projection.
- The nine-slot backpack remains deferred. Preserving unknown extension fields keeps a future addition possible without changing lifecycle ownership.
- HyDragon has not shipped this feature, so no Egg, controller-item, generic-profile, or command-roster migration is required.

## 3. Ownership boundary

| Concern | Authority |
| --- | --- |
| Lifelong claim eligibility | HyDragon player Soul Bond ledger |
| Egg spend/recovery | HyDragon idempotent consumable operation journal |
| Profile ID, owner, roster/family, state, revision, complete NPC snapshot, lease, cooldown, and revive | Tamework bonded-companion runtime |
| Archetype, attunement evidence, ability scheduler state, progression, and future HyDragon fields | Tamework bonded extension namespace `Alechilles:HyDragon`, with HyDragon defining the payload |
| Appearance, role behavior, commands, combat, and archetype content | HyDragon assets and runtime |

The bonded profile ID is the shared key across these records. A live Miniwyvern NPC UUID is temporary projection evidence only and is never the entitlement or roster identity.

## 4. Claim requirements

- **HYD-SOUL-001:** `Wyvern_Egg` remains a craftable, non-placeable ritual item using `HyDragonSoulBond` as its primary interaction.
- **HYD-SOUL-002:** Claim preflight requires a valid player/world context, a writable HyDragon entitlement/journal, `BONDED_COMPANIONS` readiness, and no existing or reconcilable claim.
- **HYD-SOUL-003:** A successful claim consumes exactly one Egg, provisions exactly one `STORED` bonded profile in the shared Horn, initializes one neutral extension document, and closes the entitlement against that profile ID.
- **HYD-SOUL-004:** `PENDING`, `CLAIMED`, or `NEEDS_RECONCILIATION` denies another independent claim. `STORED`, `ACTIVE`, and `DEAD` are all still the same claimed companion.
- **HYD-SOUL-005:** Duplicate callbacks and restart recovery reuse the same operation and authority evidence. They cannot consume two Eggs, provision two profiles, or initialize two divergent extension documents.
- **HYD-SOUL-006:** If the Egg is consumed and bonded provisioning is terminally denied, HyDragon records one Egg recovery claim and releases the entitlement only through that compensation path.
- **HYD-SOUL-007:** A missing projection, logout, transfer, restart, or dismissal cannot release the entitlement or create a replacement claim.
- **HYD-SOUL-008:** `Wyvern_Mini` and `Tamed_Wyvern_Mini` remain absent from all Draconic Stone allowlists and production wild-spawn assets.

## 5. Entitlement state machine

```text
UNCLAIMED
  -> PENDING                 one operation reserves the entitlement
  -> CLAIMED                 stored bonded profile + extension are proven
  -> NEEDS_RECONCILIATION    durable evidence is temporarily incomplete

PENDING
  -> UNCLAIMED               terminal provisioning denial creates Egg recovery
```

Ordinary gameplay has no `CLAIMED -> UNCLAIMED` transition. The bonded profile's `STORED`, `ACTIVE`, or `DEAD` state does not change entitlement ownership.

## 6. Claim transaction

The implemented claim saga is intentionally separate from ordinary projection lifecycle:

1. Resolve the acting player, exact Egg stack, world context, bonded capability, and writable HyDragon journal.
2. Fence the player's entitlement and reuse any existing matching operation.
3. Persist `PENDING` plus the exact source-item evidence.
4. Consume the Egg through its exact reservation and record `MATERIAL_CONSUMED`.
5. Call bonded `provision` with the same stable operation identity for roster `hydragon:dragon_horn`, family `hydragon:soulbound_mini`, and role `Tamed_Wyvern_Mini`.
6. Require canonical authority evidence: matching owner, roster, family, role, `STORED` state, nonnegative revision, and no active lease.
7. Create or verify the neutral `Alechilles:HyDragon` extension with compare-and-set semantics.
8. Link the HyDragon entitlement to the stable profile ID and close the operation.
9. Report that the Miniwyvern is stored in the Dragon Horn. Summoning is a separate Horn action.

If a response is lost, recovery reads the operation, bonded profile, and extension evidence. It does not invent a new operation. Invalid or conflicting authority evidence is quarantined for operator review instead of being overwritten.

## 7. Bonded family policy

`Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json` defines the current policy:

| Field | Current value |
| --- | --- |
| Roster ID | `hydragon:dragon_horn` |
| Family ID | `hydragon:soulbound_mini` |
| Allowed role | `Tamed_Wyvern_Mini` |
| Maximum owned | `1` |
| Maximum active | `1` |
| Session duration | `900` seconds |
| Summon cooldown | `180` seconds |
| Revive cost | `Revitalizing_Essence x1` and `Draconic_Essence x2` |
| Features | Provision, summon, dismiss, revive enabled; capture disabled |

The limits are bonded family policy, not population-group membership. The full-dragon family has its own active slot, so one Miniwyvern and one full dragon may be active together. `SessionDurationSeconds: 0` disables expiration and `SummonCooldownSeconds: 0` disables the cooldown without adding another lifecycle state.

## 8. Bonded lifecycle

```text
STORED --Summon--> ACTIVE
ACTIVE --Dismiss / expiry / logout / transfer / missing or duplicate cleanup--> STORED
ACTIVE --confirmed death--> DEAD
DEAD --paid revive--> STORED
```

The Dragon Horn card remains profile-keyed and fully detailed in every state. Summon creates one exact live projection. Store snapshots the projection before removal. Ability runtime attaches only to a confirmed active Miniwyvern lease and detaches on store or death; detaching never deletes extension data.

There are no Miniwyvern `LOST`, `UNLOADED`, dormant-profile, generic timed-summon, or generic command-roster states. Any non-death absence is recoverable storage. A second live copy is stale projection data and is cleaned up without creating a second companion.

## 9. Extension-data contract

HyDragon stores one versioned, unknown-field-preserving document under namespace `Alechilles:HyDragon`:

```text
schemaVersion: 1
companionKind: SOULBOUND_MINIWYVERN
speciesId: miniwyvern
archetypeId: neutral | lightning | wind | ice | fire | water | nature | void
archetypeRevision
lastAttunementOperationId
abilityState:
  archetypeId
  scheduler/cooldown state
  source-keyed effect evidence
progression: object
unknown future fields: preserved
```

Reads and writes are owner/profile/namespace scoped. Attunement and ability writers use compare-and-set revisions plus bounded retry/reload behavior so one writer cannot silently overwrite another. An attunement change preserves progression and unknown fields; ability updates preserve attunement and progression.

Tamework's full NPC snapshot separately preserves gameplay state such as name, appearance, health, needs, happiness, breeding, attachments, progression exposed by the NPC snapshot, traits, talents, life stage, and command settings. The extension supplements that snapshot with HyDragon-specific data; it is not a second companion profile.

## 10. Elemental attunement

| Archetype | Intended behavior |
| --- | --- |
| Neutral | Baseline Miniwyvern behavior |
| Lightning | Movement/action speed and fast lightning attacks |
| Wind | Movement, jump, mobility, and gust utility |
| Ice | Bounded slow/freeze buildup and control |
| Fire | Fireball pressure and bounded damage-over-time |
| Water | Burst combat healing |
| Nature | Lower-intensity periodic regeneration |
| Void | Bounded defense reduction |

Attunement consumes the configured essence only after the active claim/profile and extension revision are valid. Re-attuning to the current archetype is denied without consumption. Source-keyed effects from the prior archetype are cleaned before the new ability state is published.

## 11. Death and revival

Confirmed death keeps the entitlement and the same Horn card, records `DEAD`, and removes the active lease. The Miniwyvern recipe requires one Revitalizing Essence and two Draconic Essences.

Successful revival consumes the complete recipe once and changes the same profile to `STORED`. It does not create a replacement Miniwyvern, clear the one-lifetime claim, start a session, or summon a projection. The player explicitly selects Summon afterward.

## 12. Deferred backpack and historical designs

The Miniwyvern backpack remains a later feature. The initial bonded release does not register backpack interactions, inventory UI, or a separate inventory persistence system. Future data should extend the existing profile/extension boundary rather than introduce another roster identity.

The following pre-release designs are superseded and must not be described as current behavior:

- a recurring `Soul_Bound_Wyvern` item;
- owner-command-family membership for the Miniwyvern;
- `hydragon:soulbound_mini` as a generic population group;
- generic `COMPANION_PROVISIONING`, `ProfileDataApi`, timed-command-summon, or paid-command-revival calls;
- `DEAD_REVIVABLE`, `ROSTER_STORED`, `LOST`, or `UNLOADED` as player-visible Miniwyvern states;
- initial automatic projection as part of Egg provisioning.

## 13. Acceptance criteria

- First valid Egg use consumes one Egg and creates one neutral `STORED` profile in the shared Horn.
- Duplicate, simultaneous, or recovered callbacks cannot consume twice or create a second profile/extension.
- Every existing or reconcilable lifelong claim denies a new Egg without independent provisioning.
- Miniwyverns cannot be captured or spawned through production wild-spawn assets.
- The Horn shows full Miniwyvern details immediately after claim, summon, store, revive, and relog.
- Summon, dismissal, session expiry, logout, world transfer, and duplicate cleanup preserve the same profile and extension data.
- Only confirmed death produces `DEAD`; paid revive consumes both configured components and returns to `STORED` without summoning.
- Attunement, ability state, progression, and unknown extension fields survive summon/store/relog cycles.
- One active Miniwyvern does not consume the full-dragon family's active slot.
- Missing `BONDED_COMPANIONS` or unavailable bonded authority disables claim/attunement/ability operations with a specific reason and never invokes a generic fallback.
- The packaged initial release contains no `Soul_Bound_Wyvern` asset and no backpack implementation.
