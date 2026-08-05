# HyDragon Player Wiki Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete, practical, player-facing HyDragon 1.0.0 wiki for `wiki.hytalemodding.dev` that explains progression, dragon locations, creatures, companions, flight, items, crafting, and troubleshooting.

**Architecture:** Create a lowercase `wiki/index.md` homepage plus five navigable section directories. Each section has a short index and focused leaf pages with absolute `/mod/hydragon/...` links, consistent frontmatter, parent links, and facts reconciled against shipped assets.

**Tech Stack:** Markdown, YAML frontmatter, HytaleModding wiki importer conventions, Git Bash validation.

## Global Constraints

- Document HyDragon `1.0.0` for players; do not include code, asset IDs, configuration fields, or implementation explanations.
- Use **Dragon Command Flute**, **Mysterious Egg**, and **Soulbound Miniwyvern** consistently.
- Use verified zone-and-biome location wording; do not invent macro-region names.
- Use `wiki/index.md` for the homepage and infer the site slug as `hydragon`.
- Every page has `title`, integer `order`, `published: true`, and `draft: false`; the homepage also has `is_index: true`.
- Use absolute internal links in the form `/mod/hydragon/<title-derived-slug>`; home is `/mod/hydragon/`.
- Every non-home page has a parent/back line near the top.
- Section indexes are globally ordered 2 through 6. Child pages start at order 2 with no sibling duplicates.
- Screenshot callouts use `> [Screenshot Placeholder: <what should be shown>]` only when an image materially helps.
- Preserve the untracked `README.md` and all unrelated worktree changes.
- Source-file edit custody belongs to the assigned task owner. The main agent alone stages and commits.

---

### Task 1: Homepage and Start Here Journey

**Files:**
- Create: `wiki/index.md`
- Create: `wiki/Start-Here/index.md`
- Create: `wiki/Start-Here/Installation-and-Dependencies.md`
- Create: `wiki/Start-Here/Getting-Started.md`
- Create: `wiki/Start-Here/Progression-Roadmap.md`
- Create: `wiki/Start-Here/Finding-Dragons.md`

**Interfaces:**
- Produces the homepage route `/mod/hydragon/` and Start Here index route `/mod/hydragon/start-here-index` used by every later section.
- Links to the planned title slugs `dragon-compendium-index`, `companion-guides-index`, `items-and-crafting-index`, `help-and-reference-index`, `capturing-dragons`, `dragon-command-flute`, `soul-bond-and-attunement`, and `recipe-reference`.

- [ ] **Step 1: Create the homepage and section index**

Use homepage frontmatter `title: "Home"`, `order: 1`, `published: true`, `draft: false`, `is_index: true`. Give `Start-Here/index.md` the title `Start Here Index`, global order `2`, a Home parent link, a `## Child Pages` list, and a four-step suggested reading path.

- [ ] **Step 2: Write installation and getting-started pages**

State the player-facing requirements: HyDragon 1.0.0, Hytale server line 0.5.x, and Alec's Tamework `>=3.0.0 <4.0.0`. Keep installation concise. The getting-started sequence is: craft a Draconic Altar at a Workbench, craft the Dragon Command Flute, prepare a tranquilizer and Draconic Stone, find an eligible full dragon, tranquilize it, channel the stone, then use the flute roster.

- [ ] **Step 3: Write the progression roadmap**

Cover these stages in order: altar and flute; first Young Rock Drake; stronger stones and harder full dragons; material gathering; Mysterious Egg and the one-per-player Soul Bond; six Miniwyvern attunements; companion leveling and talents; ground mounts, Nordic Drake flight, Toxic Hydra flight; storage, summon windows, cooldowns, and revival. Link each stage to its detailed guide.

- [ ] **Step 4: Write the finding guide**

Use this table exactly as the factual basis:

| Creature | Where to search | Acquisition |
|---|---|---|
| Hydra | Zone 3 glacial ice-and-snow terrain; favored moon phase increases sightings | Tranquilize and capture |
| Toxic Hydra | Zone 4 volcanic terrain on Dark Green Moss patches | Tranquilize and capture |
| Nordic Drake | Zone 3 forests on non-snowy soil | Tranquilize and capture |
| Young Rock Drake | Zone 1 forest caves | Tranquilize and capture |
| Rock Drake | Zone 2 volcanic caves | Tranquilize and capture |
| Frost Rock Drake | Deeper Zone 2 volcanic caves and Zone 3 glacial caves | Tranquilize and capture |
| Soulbound Miniwyvern | Does not spawn in the world | Craft and hatch a Mysterious Egg |

Do not mention the inactive Toxic Hydra swamp asset.

- [ ] **Step 5: Validate and return custody**

Run `git diff --check -- wiki/index.md wiki/Start-Here`. Confirm all six files have frontmatter, correct parent links, unique child orders 2–5, and only absolute `/mod/hydragon/` internal links. Report the file list and checks to the main agent; do not stage or commit.

---

### Task 2: Dragon Compendium

**Files:**
- Create: `wiki/Dragon-Compendium/index.md`
- Create: `wiki/Dragon-Compendium/Hydra.md`
- Create: `wiki/Dragon-Compendium/Toxic-Hydra.md`
- Create: `wiki/Dragon-Compendium/Nordic-Drake.md`
- Create: `wiki/Dragon-Compendium/Rock-Drakes.md`
- Create: `wiki/Dragon-Compendium/Soulbound-Miniwyvern.md`
- Create: `wiki/Dragon-Compendium/Miniwyvern-Forms.md`

**Interfaces:**
- Produces `/mod/hydragon/dragon-compendium-index` and the creature slugs used by the finding, capture, mount, material, and quick-reference pages.
- Consumes the location wording and terminology from Task 1.

- [ ] **Step 1: Create the compendium index and page shells**

Use global order `3` for the section index and child orders 2–7 in the listed file order. Every leaf page includes `Parent: [Dragon Compendium](/mod/hydragon/dragon-compendium-index) | [Home](/mod/hydragon/)`.

- [ ] **Step 2: Write Hydra and Toxic Hydra pages**

Hydra: Zone 3 glacial terrain; ice ball and ice-rain combat; ground mount; Ice and Earth materials; Scaleguard, Survivalist, combat, and Summoner's Pact talent identities. Toxic Hydra: Zone 4 volcanic Dark Green Moss; toxic bolts, poison breath/clouds, aerial attacks; dedicated Winged Toxic Hydra flight; guaranteed Toxic and Earth materials, plus rare Void essence and a decorative Drake Egg. Explain capture, leveling, drops, and next steps without exposing internal probabilities.

- [ ] **Step 3: Write the Nordic Drake page**

Cover Zone 3 forest habitat, fireballs and flame breath, high capture difficulty, avatar flight, flight Vigour, Aerial Mastery, War Drake, Wyrmguard, and Summoner's Pact branches. Drops are 6–8 scales, 6–8 plain essence, 4–5 Ice essence, 2–3 Wind essence, with a rare Void essence chance. Phrase quantities as drop ranges, not guarantees for optional drops.

- [ ] **Step 4: Write the Rock Drake comparison page**

Compare Young Rock Drake, Rock Drake, and Frost Rock Drake in one table. All are ground mounts and use boulder attacks. Locations are Zone 1 forest caves; Zone 2 volcanic caves; and deeper Zone 2 volcanic plus Zone 3 glacial caves. Explain the natural Iron/Thorium/Cobalt stone progression as a recommendation. Drops progress from 2–3 to 3–4 to 4–5 scales/plain essence; Nature essence rises from 1–2 to 2–3 to 3–4; Young and standard variants may drop Fire essence, while Frost may drop Ice essence.

- [ ] **Step 5: Write the Soulbound Miniwyvern page**

Explain that it is created once per player by hatching the crafted Mysterious Egg, begins Wild, lives in the Dragon Command Flute roster, may be active alongside one full dragon, levels to 30, earns one talent point per level, fights with projectiles and swoops, can be re-attuned, and can be revived. Do not advertise a wild Miniwyvern spawn.

- [ ] **Step 6: Write the Miniwyvern forms page**

Document Wild plus six implemented forms only:

| Form | Player-facing identity |
|---|---|
| Wild | Strong neutral damage; no Essence Bond passive |
| Fire | Burning attacks and frequent fireballs |
| Ice | Slows enemies and can freeze fully chilled targets |
| Lightning | Improves owner movement and disrupts enemies |
| Nature | Sustained healing and brief roots |
| Toxic | Reduces enemy outgoing damage |
| Void | Makes enemies take more damage |

State explicitly that Earth and Wind Essences are crafting materials, not selectable forms. Link to Soul Bond and Attunement and Leveling and Talents.

- [ ] **Step 7: Validate and return custody**

Run `git diff --check -- wiki/Dragon-Compendium`. Confirm seven pages, one section index, child orders 2–7, consistent creature template coverage, and no claims of wild Miniwyvern, swamp Toxic Hydra, Earth form, or Wind form. Report to the main agent; do not stage or commit.

---

### Task 3: Companion Guides

**Files:**
- Create: `wiki/Companion-Guides/index.md`
- Create: `wiki/Companion-Guides/Capturing-Dragons.md`
- Create: `wiki/Companion-Guides/Dragon-Command-Flute.md`
- Create: `wiki/Companion-Guides/Soul-Bond-and-Attunement.md`
- Create: `wiki/Companion-Guides/Leveling-and-Talents.md`
- Create: `wiki/Companion-Guides/Mounts-and-Flight.md`
- Create: `wiki/Companion-Guides/Summoning-and-Revival.md`

**Interfaces:**
- Produces `/mod/hydragon/companion-guides-index` and six guide slugs consumed by Home, Start Here, creature pages, item pages, and Help.
- Consumes creature identities and location language from Tasks 1–2.

- [ ] **Step 1: Create the section navigation**

Use global order `4` for the section index and child orders 2–7. Include a suggested order: capture → flute → Soul Bond → leveling → mounts/flight → summoning/revival.

- [ ] **Step 2: Write the capture guide**

Prerequisites: carry the Dragon Command Flute, have roster capacity, tranquilize an eligible full dragon, stay within range, and use a Draconic Stone. Explain the three-second hold/channel in player terms. Interrupted or invalid attempts consume no stone; a resolved success or failure consumes one. Lower health improves the chance. Any tier can attempt any eligible full dragon; stronger stones improve the chance. Ancient guarantees a valid attempt after all prerequisites remain satisfied. Present Iron/Thorium/Cobalt/Adamantium as suggested progression, not hard gates.

- [ ] **Step 3: Write the Dragon Command Flute guide**

Explain roster use plus summon, dismiss, follow, hold, recall, move to a pointed location, defend, attack the targeted creature, idle/wander, locate, revive, and airborne-mode controls where supported. Separate roster actions from commands issued to an active companion.

- [ ] **Step 4: Write Soul Bond and attunement**

Explain the one-per-player bond, hatching the Mysterious Egg, Wild starting form, and re-attunement by interacting with the owned Miniwyvern while holding eight matching Fire, Ice, Lightning, Nature, Toxic, or Void Draconic Essences. Eight plain Draconic Essences return it to Wild. Existing level, ownership, and roster identity persist through form changes.

- [ ] **Step 5: Write leveling and talents**

Explain level 30, one point per level, combat and active-companion experience, and the practical purpose of each tree. Full dragons use combat/durability/survival branches plus Summoner's Pact for longer summons and shorter cooldowns. Nordic Drake adds a detailed flight branch. Elemental Miniwyverns use Essence Bond, Draconic Combat, and Vigor; Wild uses Combat and Vigor until attuned. Avoid dumping every hidden number; include exact unlock levels only for Summoner's Pact: 1, 8, 16, and 24.

- [ ] **Step 6: Write mounts, flight, summoning, and revival**

Mounts and Flight distinguishes ground-mounted Hydra/Rock Drakes, Nordic Drake avatar flight, and Winged Toxic Hydra avatar flight. Summoning and Revival states: unlimited owned full dragons, one active full dragon, one active Miniwyvern, and both categories can be active together. Full dragons have a five-minute base summon window and five-minute cooldown. Dismissal, expiry, logout, or transfer stores a companion; confirmed death requires revival. Full dragon revival costs two Revitalizing Essences plus two role essences: Ice for Nordic/Hydra, Toxic for Toxic Hydra, Nature for Rock Drakes. Miniwyvern revival costs one Revitalizing Essence plus one matching essence, or plain essence for Wild. Do not publish unverified Miniwyvern timers.

- [ ] **Step 7: Validate and return custody**

Run `git diff --check -- wiki/Companion-Guides`. Confirm player-visible terminology, no hard minimum-stone claim, no internal configuration language, correct section orders, and working cross-links to planned titles. Report to the main agent; do not stage or commit.

---

### Task 4: Items and Crafting

**Files:**
- Create: `wiki/Items-and-Crafting/index.md`
- Create: `wiki/Items-and-Crafting/Draconic-Altar.md`
- Create: `wiki/Items-and-Crafting/Draconic-Stones.md`
- Create: `wiki/Items-and-Crafting/Draconic-Essences-and-Scales.md`
- Create: `wiki/Items-and-Crafting/Mysterious-Egg.md`
- Create: `wiki/Items-and-Crafting/Revitalizing-Essence.md`
- Create: `wiki/Items-and-Crafting/Recipe-Reference.md`

**Interfaces:**
- Produces `/mod/hydragon/items-and-crafting-index` and six item/reference slugs consumed throughout the wiki.
- Consumes the capture, Soul Bond, drop, and revival behavior from Tasks 2–3.

- [ ] **Step 1: Create the item navigation**

Use global order `5` for the section index and child orders 2–7. The suggested path is altar → stones/materials → Mysterious Egg → Revitalizing Essence → recipe lookup.

- [ ] **Step 2: Write the Draconic Altar and Stones pages**

Altar recipe: 8 Thorium Bars and 12 Rubble Stone at a Workbench. Dragon Command Flute recipe: 3 Thorium Bars and 10 Azure Wood at the altar. Stone recipes:

| Stone | Recipe |
|---|---|
| Iron | 4 Iron Bars, 3 Draconic Scales, 3 plain Draconic Essences |
| Thorium | 4 Thorium Bars, 5 scales, 5 plain essences |
| Cobalt | 4 Cobalt Bars, 7 scales, 7 plain essences |
| Adamantium | 4 Adamantite Bars, 10 scales, 10 plain essences |
| Ancient | 12 Adamantite Bars, 20 scales, 20 plain essences, 6 Void Draconic Essences |

Explain strength and recommended targets without hard restrictions.

- [ ] **Step 3: Write materials and essence conversions**

Explain plain essence and scales as dragon drops, elemental essences as drops or altar conversions, and the decorative Drake Egg as separate from the Mysterious Egg. Conversion recipes all consume two plain Draconic Essences plus: 12 Green Crystals for Earth; 10 Fire Essences; 10 Ice Essences; 3 Lightning Essences; 1 Greater Life Essence for Nature; 10 Venom Sacs for Toxic; 8 Void Essences; or 16 Blue Feathers for Wind.

- [ ] **Step 4: Write Mysterious Egg and Revitalizing Essence pages**

Mysterious Egg recipe: 3 each of plain, Earth, Fire, Ice, Lightning, Nature, Toxic, Void, and Wind Draconic Essences plus 10 Draconic Scales. Explain that hatching claims the single lifelong Soul Bond. Revitalizing Essence recipe: 5 Draconic Essences of any accepted type plus 1 Greater Life Essence. Link exact revival costs from the companion guide.

- [ ] **Step 5: Write the Recipe Reference**

Consolidate all recipes from Steps 2–4 into scannable tables grouped by Setup, Capture, Soul Bond, Attunement Materials, and Restoration. Repeat quantities intentionally for lookup convenience, but link to detailed item pages for use instructions.

- [ ] **Step 6: Add material-source guidance**

Use these verified drop identities: the standard Hydra provides Ice/Earth materials; the Toxic Hydra provides Toxic/Earth materials; both may provide Void; Nordic Drake provides Ice/Wind and may provide Void; Rock Drakes provide Nature, with Fire on Young/standard variants and Ice on Frost. Plain essence and scales come from all full dragon families. Avoid presenting optional drops as guaranteed.

- [ ] **Step 7: Validate and return custody**

Run `git diff --check -- wiki/Items-and-Crafting`. Cross-check every quantity against current item assets, confirm Mysterious Egg and decorative Drake Egg remain distinct, confirm Earth/Wind are never called Miniwyvern forms, and report to the main agent without staging or committing.

---

### Task 5: Help, Quick Reference, and Cross-Section Cohesion

**Files:**
- Create: `wiki/Help-and-Reference/index.md`
- Create: `wiki/Help-and-Reference/Quick-Reference.md`
- Create: `wiki/Help-and-Reference/FAQ-and-Troubleshooting.md`
- Modify: `wiki/index.md`
- Modify: `wiki/Start-Here/*.md`
- Modify: `wiki/Dragon-Compendium/*.md`
- Modify: `wiki/Companion-Guides/*.md`
- Modify: `wiki/Items-and-Crafting/*.md`

**Interfaces:**
- Consumes every title slug and verified behavior produced by Tasks 1–4.
- Produces the final cross-linked 30-page content set before mechanical validation.

- [ ] **Step 1: Create Help and Reference navigation**

Use global order `6` for the section index and child orders `2` and `3`. Link Quick Reference first and FAQ second.

- [ ] **Step 2: Write Quick Reference**

Create compact tables for locations, suggested stones, combat identity, ground/flight availability, primary drops, Miniwyvern form identity, active limits, full-dragon summon/cooldown timing, and revival costs. Link every row to the detailed page instead of expanding into duplicate prose.

- [ ] **Step 3: Write FAQ and Troubleshooting**

Answer: why capture will not start; why a stone failed or disappeared; why interruption did not consume it; why a dragon is stored; why summon is on cooldown; why only one full dragon or Miniwyvern is active; how to find/recall/revive; why the Soul Bond cannot be claimed twice; why Miniwyverns cannot be captured; why Earth/Wind cannot be selected; and where the Toxic Hydra actually spawns.

- [ ] **Step 4: Normalize cross-links and next steps**

Read all 30 pages. Add missing contextual links, remove duplicated long explanations when a dedicated page exists, ensure every leaf page ends with useful Related Guides or Next Steps, and ensure Home exposes every top-level section plus the most important player journeys.

- [ ] **Step 5: Validate and return custody**

Run `git diff --check -- wiki`. Confirm the Help section orders, parent links, title slugs, and that cross-section links are absolute. Report all modified files to the main agent; do not stage or commit.

---

### Task 6: Repository-Wide Wiki Validation and Commit

**Files:**
- Verify: `wiki/**/*.md`
- Verify: `manifest.json`
- Verify: `CHANGELOG.md`
- Verify: `Server/Languages/en-US/server.lang`
- Verify: relevant recipes, drops, spawn assets, roster policies, and talent files under `Server/`

**Interfaces:**
- Consumes the complete 30-page wiki from Tasks 1–5.
- Produces the committed wiki source ready for importer synchronization.

- [ ] **Step 1: Verify page inventory**

Run `find wiki -type f -name '*.md' | sort` and confirm exactly 30 Markdown pages matching the design map.

- [ ] **Step 2: Verify metadata and ordering**

Check every page contains `title`, `order`, `published: true`, and `draft: false`; confirm only the homepage requires `is_index: true`; confirm section orders 2–6 and unique child orders within each folder.

- [ ] **Step 3: Verify navigation**

Extract every `/mod/hydragon/...` link, build the expected slug list from page titles, and confirm each target resolves. Confirm every non-home page has a Parent or Back line near the top and every section index lists all of its children.

- [ ] **Step 4: Verify content guardrails**

Search for stale or misleading claims: `Dragon Horn`, unqualified `Dragon Flute`, wild Miniwyvern spawning, a Wind or Earth Miniwyvern form, Toxic Hydra swamp spawning, hard minimum capture-stone requirements, unverified Miniwyvern timers, and technical asset/configuration terminology. Inspect and correct every hit rather than relying only on the search result.

- [ ] **Step 5: Cross-check player facts**

Compare the final wiki tables against the current recipes, drop tables, spawn files, capture policies, rosters, localization, and talent assets. Re-run `git diff --check -- wiki docs/superpowers/plans/2026-08-04-hydragon-player-wiki.md`.

- [ ] **Step 6: Stage and commit**

Stage only `wiki/` after confirming no unrelated worktree files are included. Commit from the main agent with `Docs: add HyDragon player wiki`.
