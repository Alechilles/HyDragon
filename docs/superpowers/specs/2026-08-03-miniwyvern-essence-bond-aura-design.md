# Miniwyvern Essence Bond Aura Design

## Purpose

Rework every non-Wild Miniwyvern Essence Bond talent so it modifies the aura provided to the bonded player while that Miniwyvern is summoned. It must not grant standalone companion combat statistics, health, or movement bonuses. The existing root aura for each form is retained unchanged.

Earth is included in the design because an Earth Miniwyvern variant is planned next. Wild deliberately has no Essence Bond branch.

## Talent-tree structure

Each elemental tree has nine Essence Bond nodes:

1. Tier 1 root: unlocks the existing form aura.
2. Tiers 2-4, pressure/control/support branch: strengthens the form's existing enemy effect or player support aura.
3. Tiers 2-4, Ward branch: grants a continuous defensive player aura and adds form-appropriate protection.
4. Tier 5 convergence: requires both tier-4 branch endpoints.
5. Tier 6 capstone: requires convergence.

All nodes cost the existing Essence Bond point costs and retain the tree's existing level gates. Tier 5 and tier 6 must require the preceding nodes described above, so reaching the capstone requires the entire Essence Bond tree.

Talent IDs remain stable where possible; their display names, descriptions, dependencies, and no-longer-applicable generic Tamework effects are replaced. Descriptions state only the current form's exact player-aura or enemy-aura result and values. No tooltip refers to other forms or says that an effect is unimplemented.

Every Miniwyvern talent uses localization keys rather than literal English text. The full Miniwyvern set (Fire, Ice, Lightning, Nature, Toxic, Void, and Wild; Essence Bond, Combat, and Vigor) has a name and description in every currently supported server language: `en-US`, `de-DE`, `es-ES`, `fr-FR`, and `pt-BR`. The six elemental Essence Bond trees receive newly authored form-specific translations; the unchanged Combat and Vigor effects retain their exact existing mechanics in localized copy.

## Form effects

### Fire — offensive

Base aura remains: the owner's hits burn for 2 damage each second for 4 seconds.

| Position | Aura upgrade |
|---|---|
| Pressure 1 | Burn lasts 5 seconds. |
| Pressure 2 | Burn deals 3 damage each second. |
| Pressure 3 | The owner deals 5% more damage to burning enemies. |
| Ward 1 | Flame Ward grants 8% Fire resistance. |
| Ward 2 | Flame Ward grants 12% Fire resistance. |
| Ward 3 | Applying Burn grants the owner 5% general damage reduction for 3 seconds. |
| Convergence | Burn lasts 6 seconds; Flame Ward grants 18% Fire resistance. |
| Capstone | Burn deals 4 damage each second for 6 seconds; the owner deals 10% more damage to burning enemies; Flame Ward grants 25% Fire resistance. |

Fire has no healing effect.

### Ice — control

Base aura remains: the owner's hits slow movement by 20% for 4 seconds.

| Position | Aura upgrade |
|---|---|
| Control 1 | Slow becomes 25% for 4 seconds. |
| Control 2 | Slow becomes 25% for 5 seconds. |
| Control 3 | Slowed enemies deal 8% less damage. |
| Ward 1 | Frost Ward grants 8% Ice resistance. |
| Ward 2 | Frost Ward grants 12% Ice resistance. |
| Ward 3 | The owner gains 5% movement speed while Frost Ward is active. |
| Convergence | Slow becomes 30% for 5 seconds; Frost Ward grants 18% Ice resistance. |
| Capstone | Slow becomes 35% for 6 seconds; slowed enemies deal 12% less damage; Frost Ward grants 25% Ice resistance. |

### Lightning — tempo and mobility

Base aura remains: the owner gains 15% horizontal movement speed.

| Position | Aura upgrade |
|---|---|
| Tempo 1 | Storm Boon grants 20% horizontal movement speed. |
| Tempo 2 | Storm Boon grants 25% horizontal movement speed. |
| Tempo 3 | Dealing damage grants an additional 10% horizontal movement speed for 3 seconds. |
| Ward 1 | Static Ward grants 5% general damage reduction. |
| Ward 2 | Static Ward grants 8% general damage reduction. |
| Ward 3 | The owner gains 20% knockback resistance while Static Ward is active. |
| Convergence | Storm Boon grants 30% horizontal movement speed; Static Ward grants 12% general damage reduction. |
| Capstone | Storm Boon grants 35% horizontal movement speed; the damage-triggered speed bonus becomes 15% for 4 seconds; Static Ward grants 15% general damage reduction. |

Lightning has no matching base-game elemental resistance target, so Static Ward is deliberately general defensive protection rather than a fictitious Lightning resistance.

### Nature — support and defense

Base aura remains: the owner heals 1% of maximum health every 2 seconds.

| Position | Aura upgrade |
|---|---|
| Support 1 | Regeneration heals 1.5% maximum health every 2 seconds. |
| Support 2 | Regeneration heals 2% maximum health every 2 seconds. |
| Support 3 | Regeneration also grants 5% movement speed for 2 seconds. |
| Ward 1 | Verdant Ward grants 8% Physical resistance. |
| Ward 2 | Verdant Ward grants 12% Physical resistance. |
| Ward 3 | While below 50% health, Verdant Ward grants an additional 5% general damage reduction. |
| Convergence | Regeneration heals 2.5% maximum health every 2 seconds; Verdant Ward grants 18% Physical resistance. |
| Capstone | Regeneration heals 3% maximum health every 2 seconds; its movement bonus becomes 10%; Verdant Ward grants 25% Physical resistance. |

### Toxic — debuff and defense

Base aura remains: the owner's hits apply Weakness, reducing the target's outgoing damage by 12% for 6 seconds.

| Position | Aura upgrade |
|---|---|
| Debilitation 1 | Weakness reduces outgoing damage by 15%. |
| Debilitation 2 | Weakness lasts 8 seconds. |
| Debilitation 3 | The owner deals 5% more damage to weakened enemies. |
| Ward 1 | Venom Ward grants 8% Poison resistance. |
| Ward 2 | Venom Ward grants 12% Poison resistance. |
| Ward 3 | The owner gains 5% general damage reduction while at least one enemy is weakened. |
| Convergence | Weakness reduces outgoing damage by 18% for 8 seconds; Venom Ward grants 18% Poison resistance. |
| Capstone | Weakness reduces outgoing damage by 22% for 10 seconds; the owner deals 10% more damage to weakened enemies; Venom Ward grants 25% Poison resistance. |

Toxic has no healing effect.

### Void — exposure and controlled sustain

Base aura remains: the owner's hits apply Exposure, increasing the target's damage taken by 12% for 6 seconds.

| Position | Aura upgrade |
|---|---|
| Exposure 1 | Exposure increases damage taken by 15%. |
| Exposure 2 | Exposure lasts 8 seconds. |
| Exposure 3 | The owner deals 5% more damage to exposed enemies. |
| Ward 1 | Rift Ward grants 8% Elemental resistance. |
| Ward 2 | Rift Ward grants 12% Elemental resistance. |
| Ward 3 | Damaging an exposed enemy heals the owner for 0.5% maximum health, at most once every 3 seconds. |
| Convergence | Exposure increases damage taken by 18% for 8 seconds; Rift Ward grants 18% Elemental resistance. |
| Capstone | Exposure increases damage taken by 22% for 10 seconds; the owner deals 10% more damage to exposed enemies; Rift Ward grants 25% Elemental resistance; the siphon heal becomes 1% maximum health, still at most once every 3 seconds. |

### Earth — support and defense

Base aura: Stone Ward grants 8% Physical resistance while the Earth Miniwyvern is summoned.

| Position | Aura upgrade |
|---|---|
| Control 1 | The owner's hits apply Tremor: 10% slow for 3 seconds. |
| Control 2 | Tremor becomes 20% slow for 4 seconds. |
| Control 3 | Tremored enemies deal 8% less damage. |
| Ward 1 | Stone Ward grants 12% Physical resistance. |
| Ward 2 | The owner gains 20% knockback resistance while Stone Ward is active. |
| Ward 3 | Taking damage grants 5% general damage reduction for 3 seconds. |
| Convergence | Tremored enemies deal 12% less damage; Stone Ward grants 18% Physical resistance. |
| Capstone | Tremor becomes 30% slow for 6 seconds; Stone Ward grants 25% Physical resistance. |

## Runtime architecture

`MiniwyvernAbilityService` remains the owner of Miniwyvern aura lifecycle. The existing base configuration fields continue to define the root auras. Form-specific aura-upgrade definitions are added alongside the Miniwyvern archetype data and are keyed by the relevant purchased talent IDs.

When a bonded Miniwyvern is summoned or its selected talents change, the service:

1. verifies that the root Essence Bond node is purchased;
2. reads purchased form-specific upgrade nodes;
3. derives one immutable active aura state containing the player ward, conditional player boons, enemy-debuff values, and optional special behavior;
4. registers that state with the existing owner-aura registry and refreshes player-owned EntityEffects; and
5. removes and rebuilds its own effects whenever the aura ends, the Miniwyvern changes form, or talent allocation is reconciled.

The owner-aura registry and its hit systems consume the derived state instead of hard-coded base values. Form-specific behavior stays within the existing aura systems where possible. A small, explicit extension handles conditional damage-to-marked-target bonuses, weakened/slowed enemy outgoing-damage reductions, conditional ward bonuses, and Void's cooldown-gated heal.

All player effects are marked with the existing Miniwyvern source key, so cleanup never removes effects from other systems.

## Safety and failure handling

- No purchased root talent means no Essence Bond aura, exactly as today.
- An absent or invalid optional upgrade definition is skipped; the unchanged base aura remains active.
- Unrecognized resistance/damage categories are skipped with a targeted warning and do not prevent summoning or aura refresh.
- Void's cooldown is keyed by owner and aura source, is enforced server-side, and is cleared when the aura is cleared.
- Despawning, form swapping, and talent resets clear cached derived state and player effects before a new state is created.

## Validation

- Unit tests cover derived state for Fire, Ice, Lightning, Nature, Toxic, Void, and planned Earth configurations.
- Tests assert the full-tree prerequisite chain and that no Essence Bond talent applies generic companion stat effects.
- Hit-aura tests cover upgraded duration/strength, target-state bonuses, and enemy outgoing-damage reductions.
- Player-effect tests cover resistance tiers, conditional Ward effects, source-key cleanup, and rebuild on reset/form change.
- Void tests assert no heal before the 3-second interval and the correct 0.5%/1% maximum-health values.
- In-game verification checks form-specific tooltip copy, summon/despawn cleanup, resistance effects, and one representative enemy-debuff interaction per form.

## Out of scope

- Wild Essence Bond.
- Reworking projectile, melee, or Vigor talent effects beyond their existing separate cleanup work.
- Adding new base-game damage causes for Lightning, Nature, Earth, or Void.
