# Hydra Talent Trees Design

## Goal

Replace the small talent tree shared by the Ice Hydra (`Tamed_Hydra`) and
Toxic Hydra (`Tamed_Hydra_Toxic`) with separate, fully themed trees based on
the Nordic Drake's proven progression structure. The Toxic Hydra keeps the
Nordic Drake-style Avatar Flight branch. The Ice Hydra has no flight talents.

The new trees reuse the Nordic Drake's levels, point costs, prerequisites,
passive effect keys, and numeric multipliers. The work does not add new poison
or freeze combat behavior.

## Asset Structure

- `Server/Tamework/Talents/HyDragonHydra.json` becomes the Ice-Hydra-only
  config and targets exactly `Tamed_Hydra`.
- `Server/Tamework/Talents/HyDragonToxicHydra.json` is a new config targeting
  exactly `Tamed_Hydra_Toxic`.
- Both configs use priority `100` and allocation revision `1`.
- Ice talent IDs use the `IceHydra_` prefix and localization keys use
  `hydragon.talents.ice_hydra.*`.
- Toxic talent IDs use the `ToxicHydra_` prefix and localization keys use
  `hydragon.talents.toxic_hydra.*`.
- Talent IDs append the table name in PascalCase (for example,
  `ToxicHydra_VirulentLift` and `IceHydra_ThreefoldWinter`). Talent text keys
  append the snake-case table name (for example,
  `hydragon.talents.toxic_hydra.virulent_lift.name`).
- Branch keys are exactly `plaguewing`, `venomous_onslaught`, `blightguard`,
  and `broodcallers_pact` under the Toxic prefix, and `winters_wrath`,
  `glacierguard`, and `broodcallers_pact` under the Ice prefix.
- Icons continue to use the existing Tamework strength, health, toughness, and
  swiftness icons according to the effect represented by each node.

The existing shared `Hydra_*` IDs and `hydragon.talents.hydra.*` localization
entries will be removed. The tree has not been released, and Tamework's
allocation revision/config reconciliation provides a clean reset if an
existing development save contains an old allocation.

## Toxic Hydra Tree

The Toxic Hydra has 22 nodes across four branches. Its total tree cost remains
greater than the 29 points available by level 30, preserving specialization.

### Plaguewing

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Virulent Lift | 1 | 1 | None | `AvatarFlightVigourCapacityMultiplier` `1.15` |
| Caustic Current | 6 | 2 | Virulent Lift | `AvatarFlightVigourRechargeRateMultiplier` `1.15` |
| Venomwake | 6 | 2 | Virulent Lift | `AvatarFlightForwardBoostCostMultiplier` `0.88` |
| Miasma Mantle | 14 | 3 | Caustic Current | `AvatarFlightGlideSinkMultiplier` `0.86` |
| Blight Surge | 14 | 3 | Venomwake | `AvatarFlightForwardBoostImpulseMultiplier` `1.15` |
| Plague Sovereign | 24 | 4 | Miasma Mantle and Blight Surge | Vigour capacity `1.10`; climb lift `1.12` |

### Venomous Onslaught

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Venom Discipline | 1 | 2 | None | `DamageDealtMultiplier` `1.02` |
| Caustic Heart | 8 | 2 | Venom Discipline | 3% less incoming damage (`DamageTakenMultiplier` `1 / 0.97`) |
| Toxic Momentum | 8 | 3 | Venom Discipline | `DamageDealtMultiplier` `1.035` |
| Ruinous Miasma | 16 | 4 | Toxic Momentum | `DamageDealtMultiplier` `1.045` |
| Barbed Talons | 16 | 3 | Caustic Heart | `MaxHealthMultiplier` `1.05` |
| Threefold Blight | 24 | 4 | Ruinous Miasma and Barbed Talons | Damage dealt `1.04`; 3% less incoming damage (`1 / 0.97`) |

### Blightguard

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Bogscale Hide | 1 | 1 | None | `MaxHealthMultiplier` `1.04` |
| Acid-Hardened Scales | 7 | 2 | Bogscale Hide | 4% less incoming damage (`DamageTakenMultiplier` `1 / 0.96`) |
| Patient Stalker | 7 | 2 | Bogscale Hide | `MaxHealthMultiplier` `1.04` |
| Deathless Brood | 15 | 3 | Patient Stalker | `MaxHealthMultiplier` `1.06` |
| Blightscar | 15 | 3 | Acid-Hardened Scales | 6% less incoming damage (`DamageTakenMultiplier` `1 / 0.94`) |
| Pestilent Bulwark | 24 | 4 | Deathless Brood and Blightscar | Max health `1.05`; 4% less incoming damage (`1 / 0.96`) |

### Broodcaller's Pact

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Blightbound Pact | 1 | 1 | None | `SummonSessionDurationMultiplier` `1.25` |
| Swift Recall | 8 | 2 | Blightbound Pact | `SummonCooldownMultiplier` `0.8` |
| Lingering Presence | 16 | 3 | Swift Recall | `SummonSessionDurationMultiplier` `1.6` |
| Broodmaster's Call | 24 | 4 | Lingering Presence | `SummonCooldownMultiplier` `0.625` |

## Ice Hydra Tree

The Ice Hydra has 16 nodes across three branches. It mirrors the Nordic
Drake's combat, durability, and summon lines, but the entire six-node Avatar
Flight line and every `AvatarFlight*` effect are absent.

### Winter's Wrath

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Frost Discipline | 1 | 2 | None | `DamageDealtMultiplier` `1.02` |
| Frozen Core | 8 | 2 | Frost Discipline | 3% less incoming damage (`DamageTakenMultiplier` `1 / 0.97`) |
| Rimebound Momentum | 8 | 3 | Frost Discipline | `DamageDealtMultiplier` `1.035` |
| Shattering Breath | 16 | 4 | Rimebound Momentum | `DamageDealtMultiplier` `1.045` |
| Iceclad Talons | 16 | 3 | Frozen Core | `MaxHealthMultiplier` `1.05` |
| Threefold Winter | 24 | 4 | Shattering Breath and Iceclad Talons | Damage dealt `1.04`; 3% less incoming damage (`1 / 0.97`) |

### Glacierguard

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Glacial Hide | 1 | 1 | None | `MaxHealthMultiplier` `1.04` |
| Permafrost Scales | 7 | 2 | Glacial Hide | 4% less incoming damage (`DamageTakenMultiplier` `1 / 0.96`) |
| Long Hibernation | 7 | 2 | Glacial Hide | `MaxHealthMultiplier` `1.04` |
| Unyielding Winter | 15 | 3 | Long Hibernation | `MaxHealthMultiplier` `1.06` |
| Frostscar | 15 | 3 | Permafrost Scales | 6% less incoming damage (`DamageTakenMultiplier` `1 / 0.94`) |
| Frozen Bulwark | 24 | 4 | Unyielding Winter and Frostscar | Max health `1.05`; 4% less incoming damage (`1 / 0.96`) |

### Broodcaller's Pact

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Frostbound Pact | 1 | 1 | None | `SummonSessionDurationMultiplier` `1.25` |
| Swift Recall | 8 | 2 | Frostbound Pact | `SummonCooldownMultiplier` `0.8` |
| Endless Winter | 16 | 3 | Swift Recall | `SummonSessionDurationMultiplier` `1.6` |
| Broodmaster's Call | 24 | 4 | Endless Winter | `SummonCooldownMultiplier` `0.625` |

## Localization

Every branch, talent name, and description receives entries in the existing
`en-US`, `de-DE`, `es-ES`, `fr-FR`, and `pt-BR` server catalogs. Descriptions
state the actual generic multiplier applied and do not imply that a bonus is
limited to poison clouds, ice projectiles, breath attacks, or another specific
attack when the effect applies to all damage.

## Unchanged Behavior

- `Server/Tamework/Leveling/HyDragonHydra.json` remains shared by both Hydra
  roles. Its level curve, combat/summoned XP sources, stat growth, and one
  talent point per level are unchanged.
- Toxic Hydra Avatar Flight configuration, role patching, mounted abilities,
  combat actions, projectiles, lingering poison cloud, and visuals are
  unchanged.
- Ice Hydra combat actions, projectiles, mounted behavior, and visuals are
  unchanged.
- No Tamework Java or default assets change.

## Validation

Focused HyDragon regression coverage will verify:

1. The Ice and Toxic configs are enabled, priority `100`, allocation revision
   `1`, and target exactly one distinct role each.
2. The Toxic tree has the approved 22 IDs and the Ice tree has the approved 16
   IDs, with exact branches, levels, costs, prerequisites, effect keys, and
   multipliers.
3. Toxic exposes all six Nordic Avatar Flight effect keys, while Ice exposes no
   `AvatarFlight*` effect.
4. Each referenced localization key exists in all five catalogs with matching
   placeholders.
5. The old shared role targeting and legacy shared talent IDs are absent.
6. Both configs are packaged in the release JAR.

After the edits, run the focused Gradle test, the repository asset validator,
the relevant packaged-asset test, and exact release-`0.5.7` affected-scope
validation through HytaleNpcAssetTools. A live game run is not required for
these declarative passive effects unless static validation exposes a runtime
gap.

## Non-Goals

- New poison, freeze, slow, lingering-cloud, projectile, or attack-selection
  mechanics.
- A flight branch or any Avatar Flight bonus for the Ice Hydra.
- Changes to leveling speed, level cap, available talent points, capture,
  summoning policy, mounts, combat balance, models, animations, or VFX.
- Migration support for a publicly released Hydra talent allocation.
