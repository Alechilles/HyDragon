# HyDragon Implementation Specification Suite

Status: Draft for implementation planning
Source: collaborator-provided `HydragonBaseMod.docx`, reconciled with the current HyDragon asset pack and Tamework 3.0.0 source
Required Tamework range: `>=3.0.0 <4.0.0`

## 1. Outcome

HyDragon will become a combined Java plugin and asset pack. Existing content stays in the current root `Common/` and `Server/` layout during the first conversion; Maven packages it beside the Java entry point. HyDragon owns dragon-specific content, economy, Soul Bond, elemental abilities, stone maintenance, and special encounters. Tamework owns reusable capture, profile, bonded-vessel, population, and command mechanics; generic companion inventory is reserved for a later update.

Locked product decisions:

- Miniwyvern is Soul Bond-exclusive and is removed from ordinary Draconic Stone capture.
- Flying dragons use Tamework's Flightmaster's Talisman only.
- HyDragon targets Tamework `>=3.0.0 <4.0.0` and performs capability checks.
- MVP stone maintenance is a short configurable summon/store swap cooldown plus death damage/Revitalizing Essence repair. Duration and energy budgets are deferred optional extensions.
- A player may own multiple full dragons but have only one active full dragon; their one Soul Bond Miniwyvern uses a separate active group.
- The Miniwyvern backpack and Tamework companion-inventory capability are deferred to a post-MVP update.
- Canonical HyDragon asset IDs use English terminology. Every player-facing key ships in default English plus Brazilian Portuguese, German, French, and Spanish `server.lang` catalogs.

## 2. Documents

| Document | Authority |
| --- | --- |
| [Plugin architecture](plugin-architecture.md) | Packaging, module boundary, dependency/capability handling, persistence, recovery, and safety |
| [Capture, summoning, and maintenance](capture-summoning-maintenance.md) | Stone tiers/chance, bonded-vessel lifecycle, one-active rule, commands/mounts, cooldown, death and repair |
| [Soul Bond and Miniwyvern](soul-bond-miniwyvern.md) | One-time entitlement, unique Miniwyvern, seven archetypes, abilities, clean first-release provisioning, and deferred backpack contract |
| [Dragon content and encounters](dragon-content-encounters.md) | Materials, Altar/recipes, species, roles/drops/mounts, ordinary spawning, special encounter controller |

Normative Tamework companion specifications:

- [Tamework HyDragon integration suite](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/README.md)
- [Capture policy](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/capture-policy.md)
- [Bonded vessels](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/bonded-vessels.md)
- [Population groups](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/population-groups.md)
- Deferred post-MVP: [Companion inventory](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/companion-inventory.md)
- [Integration contract](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/integration-contract.md)

## 3. System boundary

| Layer | Owns | Does not own |
| --- | --- | --- |
| HyDragon assets/config | Models, textures, items, Altar/recipes, roles, static spawns, drops, VFX/audio, balance values | Atomic companion/profile transitions |
| HyDragon Java plugin | Soul Bond ledger, archetype abilities, repair policy, special encounter controller, capability/diagnostics | Generic profiles, capture transaction, vessel identity/cooldown authority, population admission, inventory engine |
| Tamework | Public companion API, capture policy engine, bonded vessels and durable transition cooldown, profile lifecycle, commands, population groups, and later generic companion inventory | HyDragon lore, species balance, elemental effects, Altar/economy |
| Base Hytale assets/runtime | `CraftingRecipe`, `WorldNPCSpawn`, `BeaconNPCSpawn`, NPC/item/effect/projectile execution | Weather/owned-companion-gated multi-stage HyDragon encounter policy |

## 4. Delivery sequence

1. **Foundation:** Convert packaging without moving root assets; set manifest `Main`, `IncludesAssetPack: true`, and the Tamework range; add capability diagnostics and versioned storage.
2. **Tamework primitives:** Land capture policy, bonded vessel, population-group, and companion-provisioning contracts before enabling their dependent HyDragon behavior.
3. **Full-dragon vertical slice:** Draconic Altar, Iron/Ancient stone paths, one completed species, bonded summon/store, cooldown, death damage, and repair.
4. **Roster completion:** Remaining stone tiers, Rock Drake tamed roles, commands, mounts, drops, and static spawns.
5. **Soul Bond vertical slice:** Once-only claim, neutral Miniwyvern, Fire and Nature archetypes, and unique active-group enforcement.
6. **Companion completion:** Remaining archetypes and their full ability safety matrix.
7. **Special encounters:** Weather/player-gated high-altitude encounter, grounding sequence, persistence, and tuning.

The Tamework-backed nine-slot Miniwyvern backpack is a separate post-MVP update after phase 7. Each numbered phase is releasable only after the linked MVP acceptance criteria pass. A missing Tamework capability blocks its dependent feature; it does not authorize a private HyDragon reimplementation.

## 5. Requirements traceability matrix

Every normative `HYD-*` requirement in this suite appears below. “Layer” identifies the implementation owner; “Tamework dependency” names the generic contract HyDragon relies on. Acceptance links point to the testable criteria for that requirement's specification.

### 5.1 Architecture

| ID | Source/design intent | Layer | Tamework dependency | Acceptance |
| --- | --- | --- | --- | --- |
| `HYD-ARCH-001` | HyDragon becomes a plugin without losing its asset pack | Build | None | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-002` | Avoid disruptive first-pass asset relocation | Build | None | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-003` | Load Java and assets with a supported Tamework 3.x | Manifest | Integration suite | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-004` | Use canonical English names without unreleased-ID compatibility machinery | Assets + plugin | None | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-005` | Keep HyDragon outside Tamework internals | Plugin | [Integration contract](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/integration-contract.md) | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-006` | Verify runtime feature availability | Plugin | Integration contract capabilities | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-007` | Fail safely on partial/incompatible runtime | Plugin | Integration contract capabilities | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-008` | Maintain explicit domain ownership | Plugin | None | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-009` | Keep balance/content data-driven | Assets + plugin | Config/API contracts | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-010` | Version and validate first-release durable records | Plugin persistence | Profile Data API | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-011` | Prevent duplicate companions/item loss on interrupted operations | Plugin + Tamework | Integration transaction contract | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-012` | Keep entity mutations thread-safe | Plugin | Integration event/thread contract | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-013` | Make disabled/orphaned states operable | Plugin | Diagnostics/capability contract | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |
| `HYD-ARCH-014` | Ship a complete, clean artifact | Build/test | None | [Architecture §12](plugin-architecture.md#12-acceptance-criteria) |

### 5.2 Capture, summoning, and maintenance

| ID | Source/design intent | Layer | Tamework dependency | Acceptance |
| --- | --- | --- | --- | --- |
| `HYD-CAP-001` | Full dragons use stones; Miniwyvern is Soul Bond-only | Assets + plugin gate | Capture policy | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-002` | Weaken, tranquilize, target, and channel before capture | Tamework config | [Capture policy](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/capture-policy.md) | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-003` | Five ore-progressed stone tiers | Assets/config | Capture policy tier input | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-004` | Species difficulty/rarity/condition affects capture | HyDragon species config | Capture policy evaluator | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-005` | Ancient stone guarantees eligible capture | Config | Capture policy guarantee | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-006` | Perform one authoritative capture roll | Tamework | Capture transaction | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-007` | Failed attempts are explicit and item-safe | Config + Tamework | Capture result/transaction | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-008` | Successful stone capture creates/keeps one profile | Tamework + plugin metadata | Capture transaction/profile API | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-009` | Stone remains permanently bonded to the same dragon | Tamework | [Bonded vessels](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/bonded-vessels.md) | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-010` | Stored/active/damaged/unavailable states are coherent | Assets + plugin + Tamework | Bonded-vessel state API | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-011` | Own many but summon one full dragon | Tamework config | [Population groups](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/population-groups.md) | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-012` | Summon/store/recall/command the bonded dragon | Assets + Tamework config | Vessel/command API | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-013` | Never substitute a different dragon | Tamework | Stable vessel/profile identity | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-014` | Flight uses Flightmaster's Talisman only | Assets + Tamework | Avatar flight | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-015` | MVP has short configurable swap cooldown | HyDragon-authored Tamework config | Bonded-vessel transition cooldown | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-016` | Defer duration/energy while leaving safe extension hooks | Plugin integration | Vessel lifecycle extension points | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-017` | Death damages rather than loses the bonded stone/dragon | Assets + plugin | Death/vessel events | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-018` | Revitalizing Essence repairs the exact profile once | Plugin | Vessel recovery/revive transaction | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-019` | Lifecycle and cooldown persist across ordinary unload/restart | Plugin + Tamework | Profile/vessel persistence | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |
| `HYD-CAP-020` | Ship only the canonical first-release bonded-stone model | Assets + validation | Bonded-vessel contract | [Capture §12](capture-summoning-maintenance.md#12-acceptance-criteria) |

### 5.3 Soul Bond and Miniwyvern

| ID | Source/design intent | Layer | Tamework dependency | Acceptance |
| --- | --- | --- | --- | --- |
| `HYD-SOUL-001` | Craft/use the Draconic Soul Bond | Assets + plugin interaction | Interaction extension API | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-002` | First use creates one permanent Miniwyvern | Plugin | `COMPANION_PROVISIONING` + population admission | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-003` | Soul Bond is once per player | Plugin persistence | Population/profile query | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-004` | Miniwyvern is never ordinarily spawned/captured | Assets + plugin gate | Capture policy deny | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-005` | One Miniwyvern, separate from full-dragon cap | Tamework config | [Population groups](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/population-groups.md) | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-006` | Preserve the same companion and all attached state | Plugin metadata + Tamework | Profile/data APIs | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-007` | Death cannot reset entitlement/create a replacement | Plugin | Death/recovery events | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-008` | Small follower with bite and combat assistance | NPC assets/config | Tamework commands/actions | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-009` | Miniwyvern is not a mount | NPC/Tamework config | None | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-010` | **Deferred:** small persistent backpack | Future Tamework config | [Deferred companion inventory](https://github.com/Alechilles/AlecsTamework/blob/main/docs/specs/hydragon/companion-inventory.md) | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-011` | **Deferred:** backpack access is owner-safe and failure-safe | Future Tamework | Deferred companion inventory access/transaction | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-012` | Seven defined archetypes plus neutral | HyDragon config/assets | Profile Data API | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-013` | Essence re-attunes the same Miniwyvern | Plugin interaction | Profile/attachment API | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-014` | Appearance and runtime behavior both communicate archetype | Assets + plugin | Attachment sync/events | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-015` | Lightning/Wind/Ice roles | Plugin + assets | Generic effects/projectiles only | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-016` | Fire/Water/Nature roles | Plugin + assets | Generic effects/projectiles only | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-017` | Void defense-reduction role | Plugin + assets | Generic effects/projectiles only | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-018` | Abilities are bounded, data-driven, and clean up safely | Plugin | Events/profile lifecycle | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-019` | Ship no pre-release Miniwyvern adoption compatibility path | Plugin + validation | Provisioning API | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |
| `HYD-SOUL-020` | All Miniwyvern behavior has a release test gate | Test/build | All linked contracts | [Soul Bond §13](soul-bond-miniwyvern.md#13-acceptance-criteria) |

### 5.4 Content and encounters

| ID | Source/design intent | Layer | Tamework dependency | Acceptance |
| --- | --- | --- | --- | --- |
| `HYD-CONT-001` | Complete draconic/elemental material set | Assets | None | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-002` | Rename untranslated source IDs directly to canonical English IDs | Assets + validation | None | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-003` | Dragons source crafting materials | Drop assets/config | None | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-004` | Dedicated Draconic Altar replaces Arcanebench recipes | Bench/recipe assets | None | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-005` | Altar crafts stones, repair essence, and Soul Bond | Recipe assets | None | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-006` | Complete current full-dragon roster; separate Miniwyvern | NPC assets | None | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-007` | Species/difficulty controls stats, behavior, rarity, spawn, capture | HyDragon config | Capture policy inputs | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-008` | Every capturable wild dragon has a valid tamed role | NPC/Tamework config | Commands/profile capture | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-009` | Species explicitly define ground/flight/no mount | NPC/Tamework config | Mount/avatar-flight contract | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-010` | One authoritative flight unlock item | Assets/config | Tamework avatar flight | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-011` | Use base spawn assets for supported conditions | Spawn assets | None | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-012` | Use plugin only for unsupported dynamic conditions | Plugin encounters | Integration/profile query | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-013` | Require another flying dragon for high-altitude encounter | Plugin encounters | Population/profile/flight query | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-014` | Aerial fight must be lured to a capture-ready ground phase | Plugin + NPC/effect assets | Capture special-requirement hook | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-015` | Rare encounters are concurrency/restart safe | Plugin persistence | Event/profile integration | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-016` | Content/reference/test gate prevents partial species | Validation/test | Linked contracts | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |
| `HYD-CONT-017` | Complete English, Brazilian Portuguese, German, French, and Spanish localization | Language assets + validation | None | [Content §12](dragon-content-encounters.md#12-acceptance-criteria) |

## 6. Requirement and change-control rules

- `MUST`/`MUST NOT` are release requirements; `SHOULD` identifies the preferred default unless asset or playtest evidence justifies another choice; `MAY` is optional.
- A requirement ID is stable. Changed semantics require a documented revision note rather than silently reusing the ID.
- The HyDragon document is authoritative for domain behavior; the linked Tamework document is authoritative for generic API/config mechanics. If they conflict, implementation pauses until both documents are reconciled.
- Config/file paths described as illustrative must be replaced by the final path from the Tamework integration contract before implementation.
- Deferred energy/duration maintenance is not an MVP acceptance requirement. Adding either requires a new or revised requirement, an explicit data-version contract, and dedicated tests.
- The two deferred backpack requirement IDs are retained as stable post-MVP requirements but are explicitly excluded from the initial plugin release gate. Enabling them requires the deferred Tamework companion-inventory contract and its own release acceptance pass.
- Traceability is checked by ensuring every `HYD-*` ID occurs in exactly one normative requirements section and at least once in this matrix.
