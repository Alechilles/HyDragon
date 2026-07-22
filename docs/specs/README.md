# HyDragon Implementation Specification Suite

Status: Redesign specified; implementation pending
Source: collaborator feature outline plus in-game iteration
Required Tamework range: `>=3.0.0 <4.0.0`

## 1. Product direction

HyDragon is a Java plugin and asset pack built on Tamework 3.x. It owns dragon content, capture balance, the Draconic Altar and economy, the Wyvern Egg entitlement, elemental abilities, encounters, effects, and localization. Tamework owns reusable companion identity, capture resolution, command-family rosters, population admission, placement, commands, and paid revival.

The original unreleased bonded-stone design is withdrawn. Draconic Stones are consumed capture attempts. Success tames the existing dragon and adds it to the Dragon Horn; failure consumes the attempted stone and leaves the wild dragon available for another attempt after cooldown. The Dragon Horn is the recurring interface for full dragons and the Soul Bond Miniwyvern.

## 2. Locked decisions

- HyDragon targets Tamework `>=3.0.0 <4.0.0` and checks required capabilities.
- Every eligible resolved capture roll consumes one stone, on success or failure.
- Invalid preflight and interrupted channels consume nothing and do not roll.
- Success tames in place and adds one canonical profile to `hydragon:dragon_horn`; no filled stone is created.
- Full dragons and the one Soul Bond Miniwyvern share the Dragon Horn UI but retain separate population limits.
- `Wyvern_Egg` is consumed on the one lifelong Miniwyvern claim. No `Soul_Bound_Wyvern` item exists.
- Dead companions remain in the Horn and require configured `Revitalizing_Essence` costs to revive.
- Recall, initial Egg projection, and revival place companions safely in front of the player.
- Flying dragons use Tamework's Flightmaster's Talisman only.
- The backpack is deferred to a later update.
- Canonical asset IDs use English. Player-facing text ships in English, Brazilian Portuguese, German, French, and Spanish.
- HyDragon has never been released. There are no HyDragon migration or compatibility requirements.

## 3. Documents

| Document | Authority |
| --- | --- |
| [Plugin architecture](plugin-architecture.md) | Packaging, dependency/capability handling, persistence, recovery, and safety |
| [Draconic capture, Dragon Horn, and revival](capture-summoning-maintenance.md) | Stone tiers and spending, tame-and-link capture, Horn roster, commands, placement, death, revival, and bonded-system removal |
| [Wyvern Egg, Soul Bond, and Miniwyvern](soul-bond-miniwyvern.md) | Lifelong claim, Egg consumption, Horn membership, Miniwyvern behavior, archetypes, and deferred backpack |
| [Dragon content and encounters](dragon-content-encounters.md) | Materials, Altar/recipes, species, drops, mounts, spawning, and special encounters |

Normative Tamework companion specifications:

- [Tamework HyDragon integration suite](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/README.md)
- [Capture policy](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/capture-policy.md)
- [Command-roster capture and revival](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/command-roster-capture-revival.md)
- [Population groups](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/population-groups.md)
- [Integration contract](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/integration-contract.md)
- Deferred: [Companion inventory](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/companion-inventory.md)

## 4. System boundary

| Layer | Owns | Does not own |
| --- | --- | --- |
| HyDragon assets/config | Items, models, textures, Altar/recipes, roles, spawns, drops, VFX/audio, balance, multilingual catalogs | Atomic profile/roster transitions |
| HyDragon plugin | Egg entitlement, archetype abilities, special encounters, capability checks, domain diagnostics | Generic profiles, command roster, population admission, revival inventory transaction |
| Tamework | Capture transaction, profile lifecycle, command-family roster, commands/UI, population groups, provisioning, paid revival, recovery | Dragon lore, ore/essence balance, elemental combat, Altar, encounter policy |
| Hytale runtime | Asset and ECS execution | Cross-mod domain transaction policy |

## 5. Delivery map

1. **Tamework redesign:** replace bonded vessels with command-family roster capture and paid revival.
2. **HyDragon capture:** convert five stone tiers to consume-on-roll tame-and-link behavior.
3. **Dragon Horn:** rename and configure the command item as the durable roster access point.
4. **Soul Bond:** consume the Wyvern Egg, provision one profile, and add it to the Horn.
5. **Removal:** delete bonded stone states, summon/store/repair code, Soul Bound Wyvern, and all unreleased compatibility artifacts.
6. **Content:** retain species, mounts, Altar, essence, elemental abilities, and encounters under the new lifecycle.
7. **Verification:** cross-repository, packaged-asset, recovery, localization, and in-game tests.

The Miniwyvern backpack is a later release and does not block these phases.

## 6. Core requirement traceability

| Area | HyDragon authority | Tamework authority |
| --- | --- | --- |
| Capture eligibility and chance | [Capture §§4-5](capture-summoning-maintenance.md#4-functional-requirements) | [Capture policy](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/capture-policy.md) |
| Spend on every resolved attempt | [Capture §§4, 7](capture-summoning-maintenance.md#stone-consumption-boundary) | [Command-roster capture §5](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/command-roster-capture-revival.md#5-capture-transaction) |
| Tame in place and add to Horn | [Capture §§4, 8](capture-summoning-maintenance.md#successful-capture-and-dragon-horn-membership) | [Command-roster capture §§4-6](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/command-roster-capture-revival.md) |
| Paid death recovery | [Capture §4](capture-summoning-maintenance.md#death-and-paid-revival) | [Command-roster capture §8](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/command-roster-capture-revival.md#8-paid-revival-transaction) |
| Miniwyvern lifelong claim | [Miniwyvern §§3-5](soul-bond-miniwyvern.md#3-requirements) | Tamework provisioning, roster, and population contracts |
| One active full dragon / one Miniwyvern | Capture and Miniwyvern specs | [Population groups](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/population-groups.md) |
| English IDs and five locales | [Content localization](dragon-content-encounters.md#53-localization-catalogs) | None |
| Deferred backpack | [Miniwyvern §3](soul-bond-miniwyvern.md#deferred-backpack) | [Companion inventory](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/companion-inventory.md) |

## 7. Cross-cutting invariants

1. One stable Tamework profile represents one companion across all projections and lifecycle states.
2. One namespaced operation ID spans each cross-plugin capture, claim, or revival transaction.
3. Exactly-once means retries return the recorded result; they do not re-roll, re-consume, re-provision, or re-spawn.
4. Item metadata is a cache/projection. It cannot establish ownership, Horn membership, or entitlement.
5. Inventory, profile, roster, population, and world mutations either converge together or produce one durable recovery claim.
6. Positive mutations fail closed when persistence or capability authority is unavailable.
7. World/ECS work runs on the owning world thread; durable I/O never blocks it.
8. Missing presentation assets may degrade visuals, not transaction correctness.
9. No implementation may retain the bonded-vessel design as a hidden alternate path.

## 8. Definition of completion

The redesign is complete only when:

- all five stones consume exactly once for every resolved roll and never for pre-roll denial;
- capture success preserves the same dragon/profile in the world and adds one Horn row;
- Horn replacement reconstructs the same owner roster and never transfers it;
- the Wyvern Egg creates one lifelong profile and no separate summon item;
- death and paid revival preserve identity and charge exactly once;
- population caps and in-front placement apply to every projection path;
- all bonded-vessel and Soul Bound Wyvern code/assets/config/docs are removed;
- there are no HyDragon migration readers or aliases;
- Maven tests, packaged-asset validation, localization parity, restart recovery, and live-server acceptance all pass against Tamework 3.0.0.
