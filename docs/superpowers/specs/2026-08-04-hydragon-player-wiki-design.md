# HyDragon Player Wiki Design

## Purpose

Create a complete HyDragon wiki for `wiki.hytalemodding.dev` that helps players understand the mod, decide what to do next, find every obtainable dragon, and use every major player-facing system. The wiki is an instructional companion, not promotional copy or a technical reference.

The initial wiki documents HyDragon `1.0.0`. It uses visible in-game names and explains concrete behavior without exposing asset IDs, configuration fields, code architecture, or implementation details.

## Audience and Writing Style

- Write for players who have installed the mod but may know nothing about its progression.
- Lead with practical answers: what an item or creature is, where to find it, what is required, and what to do next.
- Use short procedures, lookup tables, and cross-links instead of long narrative passages.
- Use **Dragon Command Flute** consistently because that is the current in-game item name.
- Describe locations with verified zone and biome wording, such as “Zone 2 volcanic caves.” Do not invent macro-region names.
- State limitations that affect player decisions, including the lifelong one-per-player Miniwyvern bond, active companion limits, capture consumption, summon windows, cooldowns, and revival costs.
- Add screenshot placeholders only where a creature, item, interface, or location image would materially help the player.

## Information Architecture

Use a guided-journey structure backed by a creature and item compendium. The homepage and Start Here section answer “what should I do next?” while the remaining sections answer focused lookup questions.

```text
wiki/
  index.md
  Start-Here/
    index.md
    Installation-and-Dependencies.md
    Getting-Started.md
    Progression-Roadmap.md
    Finding-Dragons.md
  Dragon-Compendium/
    index.md
    Hydra.md
    Toxic-Hydra.md
    Nordic-Drake.md
    Rock-Drakes.md
    Soulbound-Miniwyvern.md
    Miniwyvern-Forms.md
  Companion-Guides/
    index.md
    Capturing-Dragons.md
    Dragon-Command-Flute.md
    Soul-Bond-and-Attunement.md
    Leveling-and-Talents.md
    Mounts-and-Flight.md
    Summoning-and-Revival.md
  Items-and-Crafting/
    index.md
    Draconic-Altar.md
    Draconic-Stones.md
    Draconic-Essences-and-Scales.md
    Mysterious-Egg.md
    Revitalizing-Essence.md
    Recipe-Reference.md
  Help-and-Reference/
    index.md
    Quick-Reference.md
    FAQ-and-Troubleshooting.md
```

This produces 29 Markdown pages: one home page, five section indexes, and 23 focused content pages. Rock Drake tiers share one comparison page because their progression, attacks, drops, and habitats are easiest to understand together. Standard and Toxic Hydras remain separate because their habitats, attacks, and flight behavior differ materially.

## Page Responsibilities

### Home

The homepage introduces the mod in one short paragraph, provides a compact “first hour” route, and links directly to Getting Started, Progression Roadmap, Finding Dragons, Dragon Compendium, Capturing Dragons, Dragon Command Flute, Soul Bond, crafting, and troubleshooting.

### Start Here

- **Installation and Dependencies:** player-facing requirements, supported mod version context, and links to the required companion framework without build or configuration details.
- **Getting Started:** the shortest route from a fresh install to the first capture attempt.
- **Progression Roadmap:** Draconic Altar → Dragon Command Flute → tranquilizer and Draconic Stone → first full dragon → material gathering → Mysterious Egg and lifelong Soul Bond → attunement, leveling, mounts, flight, and revival.
- **Finding Dragons:** one location table covering Hydra, Toxic Hydra, Nordic Drake, and all three Rock Drake tiers, with links to their detailed pages. It explains that the Miniwyvern is crafted through a Soul Bond and does not spawn in the world.

### Dragon Compendium

Each creature page uses a consistent template:

1. Summary and role.
2. Where to find it.
3. How to obtain it.
4. Wild attacks and encounter notes.
5. Bonded combat abilities and commands.
6. Leveling and talent identity.
7. Mount or flight behavior.
8. Drops and their progression value.
9. Related guides and next steps.

Verified location wording:

- Hydra: Zone 3 glacial, ice-and-snow terrain; more common during its favored moon phase.
- Toxic Hydra: Zone 4 volcanic terrain on Dark Green Moss patches; do not mention the inactive swamp spawn.
- Nordic Drake: Zone 3 forests on non-snowy soil.
- Young Rock Drake: Zone 1 forest caves.
- Rock Drake: Zone 2 volcanic caves.
- Frost Rock Drake: deeper Zone 2 volcanic caves and Zone 3 glacial caves.
- Soulbound Miniwyvern: created from the crafted Mysterious Egg, not found as a wild spawn.

The Miniwyvern forms page documents Wild plus the six implemented attunements: Fire, Ice, Lightning, Nature, Toxic, and Void. Earth and Wind Essences are crafting materials, not Miniwyvern forms.

### Companion Guides

- **Capturing Dragons:** prepare the flute, tranquilize the dragon, hold the stone’s capture action through the channel, and understand interruption, failure, consumption, and success. Any stone can attempt an eligible full dragon; stronger stones improve the odds. The natural recommended progression is Iron for a Young Rock Drake, Thorium for a Rock Drake, Cobalt for a Frost Rock Drake or Hydra, Adamantium for a Nordic Drake, and Ancient for a guaranteed valid capture attempt. Make clear that these are recommendations, not hard minimums.
- **Dragon Command Flute:** roster access; summon, dismiss, follow, hold, recall, move, defend, attack, idle, locate, revive, and airborne-mode controls where supported.
- **Soul Bond and Attunement:** the once-per-player Mysterious Egg bond, the Wild starting form, the six elemental choices, re-attunement using eight matching essences, and returning to Wild with plain Draconic Essence.
- **Leveling and Talents:** how companions gain experience, spend talent points, and specialize. Explain branch identities and meaningful choices without copying every numerical field into prose. Use tables for unlocks where exact values affect decisions.
- **Mounts and Flight:** ground riding for Hydras and Rock Drakes, Nordic Drake avatar flight, Winged Toxic Hydra avatar flight, and how flight-related talents change the experience.
- **Summoning and Revival:** one active full dragon and one active Miniwyvern may accompany a player at once; full dragons have a five-minute summon window and five-minute resummon cooldown before talents; explain storage, dismissal, expiry, death, and role-specific revival costs. Do not publish unverified Miniwyvern timers.

### Items and Crafting

Every item page states purpose, recipe, where ingredients come from, how to use it, and the next item or system it unlocks.

The Recipe Reference consolidates:

- Draconic Altar and Dragon Command Flute.
- Iron, Thorium, Cobalt, Adamantium, and Ancient Draconic Stones.
- Earth, Fire, Ice, Lightning, Nature, Toxic, Void, and Wind essence conversions.
- Revitalizing Essence.
- Mysterious Egg.

The materials page distinguishes plain Draconic Essence, elemental essences, Draconic Scales, and the decorative Drake Egg. It must not confuse the decorative Drake Egg dropped by dragons with the crafted Mysterious Egg used for the Soul Bond.

### Help and Reference

- **Quick Reference:** compact tables for dragon locations, recommended capture stones, combat identity, mount/flight availability, drops, attunement effects, summon limits, and revival costs.
- **FAQ and Troubleshooting:** practical answers for failed captures, insufficient stones, missing tranquilization, interrupted channels, active roster limits, cooldowns, lost or stored companions, revival, an already-claimed Soul Bond, and why Wind or Earth cannot be selected as Miniwyvern forms.

## Navigation and Site Contract

- Use `wiki/index.md` as the lowercase homepage. Existing working wiki history shows the importer requires this route even though the older written contract names `Home.md`.
- The homepage has `title: "Home"`, `order: 1`, `published: true`, `draft: false`, and `is_index: true`.
- Every other page has the four required frontmatter keys: quoted title, integer order, `published: true`, and `draft: false`.
- Top-level section indexes use global order 2 through 6. Child orders start at 2 because their section index occupies the first slot.
- Use the inferred mod slug `hydragon` and absolute links in the form `/mod/hydragon/<title-derived-slug>`.
- Section index URLs are flat and title-derived, matching the current importer. Root links use `/mod/hydragon/`.
- Every section index lists its child pages and a suggested reading order. Every content page includes its section parent and Home links near the top.
- Validate frontmatter, sibling ordering, links, parent lines, filename/title alignment, and homepage discoverability before completion.

## Accuracy Decisions

The wiki follows active shipped behavior when older specs or metadata disagree:

1. Active capture policies allow every stone tier to attempt every eligible full dragon. Species-tier values are presented only as a recommended progression, not requirements.
2. The standard Hydra is a ground mount. The Toxic Hydra receives its separately implemented winged avatar flight.
3. Implemented Miniwyvern forms are Wild, Fire, Ice, Lightning, Nature, Toxic, and Void. Wind and Earth remain crafting essences and are not advertised as forms.
4. Current localized terminology wins over older promotional language: **Dragon Command Flute**, **Mysterious Egg**, and **Soulbound Miniwyvern**.
5. Location names stay at verified zone-and-biome precision because current Hytale `0.5.7` data does not expose dependable localized macro-region names for every relevant spawn.

## Validation

After authoring:

1. Confirm every planned page exists and every page has valid frontmatter.
2. Confirm no sibling pages share an `order` value.
3. Resolve every `/mod/hydragon/...` link against the title-derived page map.
4. Confirm every non-home page has a parent/back link near the top.
5. Search for stale terms and claims: Dragon Horn, Dragon Flute, wild Miniwyvern spawns, Wind Miniwyvern, Earth Miniwyvern, active swamp Toxic Hydra spawn, and mandatory minimum capture stones.
6. Cross-check recipes, drop sources, limits, cooldowns, talent summaries, and locations against the current `1.0.0` assets.
7. Review all pages for player-facing language and remove technical implementation details.

## Publishing Assumption

The repository wiki is the source of truth for content. Publishing or importer synchronization to `wiki.hytalemodding.dev` is a separate step. The inferred slug is `hydragon`; it should be confirmed against the live importer or admin view when the wiki is first published.
