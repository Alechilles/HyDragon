# Dragon Economy Specification

Status: approved for implementation

## Goal

HyDragon progression must let a player enter the mod through ordinary base-game gathering, while dragon hunting supplies the consumable capture, attunement, revival, and Soul Bond progression loops.

## Economy rules

### Entry items

The Draconic Altar and Dragon Horn use no dragon materials.

| Item | Recipe | Bench |
| --- | --- | --- |
| Draconic Altar | 8 Thorium Bars, 12 Rubble Stone | Workbench tier 1 |
| Dragon Horn | 3 Thorium Bars, 10 Azure Wood | Draconic Altar |

### Essence availability

Every Draconic Essence item belongs to resource type `HyDragon_DraconicEssences`: plain, Earth, Fire, Ice, Lightning, Nature, Toxic, Void, and Wind. Recipes using `ResourceTypeId: HyDragon_DraconicEssences` accept any member of that group.

Until each elemental dragon is available, the altar supplies one-output conversion recipes. Each uses two plain Draconic Essences plus the following base-game catalyst:

| Output | Catalyst |
| --- | --- |
| Nature | 1 Greater Life Essence |
| Fire | 10 Fire Essence |
| Ice | 10 Ice Essence |
| Lightning | 3 Lightning Essence |
| Void | 8 Void Essence |
| Earth | 12 Green Crystal |
| Toxic | 10 Venom Sac |
| Wind | 16 Blue Feathers |

### Drops

Every drop table has guaranteed scales, plain essence, and a primary elemental essence. Optional secondary essence drops are independent. Decorative `Drake_Egg` drops are not part of progression balance.

| Dragon | Scales | Plain | Primary | Secondary |
| --- | --- | --- | --- | --- |
| Rock Drake T1 | 2–3 | 2–3 | 1–2 | 40%: 1–2 |
| Rock Drake T2 | 3–4 | 3–4 | 2–3 | 45%: 1–2 |
| Rock Drake T3 | 4–5 | 4–5 | 3–4 | 50%: 1–2 |
| Hydra | 5–6 | 5–6 | 3–4 | guaranteed 2–3 |
| Nordic Drake | 6–8 | 6–8 | 4–5 | guaranteed 2–3 |

The exact primary/secondary affinity is declared in the drop and revive-policy assets; current full-dragon revival mappings are Rock Drake → Nature, Hydra → Ice, Toxic Hydra → Toxic, and Nordic Drake → Ice.

### Capture Stones

Every Stone can attempt every eligible dragon. There is no minimum-power denial. Ancient (power 5) remains guaranteed after deterministic encounter requirements pass.

| Stone | Recipe |
| --- | --- |
| Iron | 4 Iron Bars, 3 Scales, 3 plain Essence |
| Thorium | 4 Thorium Bars, 5 Scales, 5 plain Essence |
| Cobalt | 4 Cobalt Bars, 7 Scales, 7 plain Essence |
| Adamantium | 4 Adamantite Bars, 10 Scales, 10 plain Essence |
| Ancient | 12 Adamantite Bars, 20 Scales, 20 plain Essence, 6 Void Essence |

Capture chance is 3% base plus 7 percentage points per Stone power, before species resistance, multiplier, and missing-health bonus. The chance floor is 1%; the ceiling remains 95%. At final resolution, an active `Tw_Status_Tranquilized` effect grants an additional flat 25 percentage points. The effect must still be active at completion. While the target is in `Sleep.Tranquilized`, positive health recovery is suppressed; incoming damage remains normal.

### Attunement and Soul Bond

Every first attunement and re-attunement costs eight matching elemental essences. Re-attuning to the current form consumes nothing.

The permanent Soul Bond Egg costs three of every Draconic Essence type (plain plus all eight elemental types) and ten scales.

### Revival

Revitalizing Essence is crafted at the altar from five essences of any `HyDragon_DraconicEssences` member plus one Greater Life Essence.

| Companion | Revival cost |
| --- | --- |
| Miniwyvern | 1 Revitalizing Essence + 1 essence matching its current role; Wild uses plain Essence |
| Full dragon | 2 Revitalizing Essences + 2 essences matching the captured role |

The Horn must quote and escrow the exact role-specific recipe. Tamework owns the atomic payment and state transition; HyDragon supplies declarative role-to-cost mappings.

## Integration contract

- Primary mod: HyDragon
- Dependency: Alec's Tamework `>=3.0.0 <4.0.0`
- Required additions: effect-aware capture bonus, tranquilized-sleep health recovery suppression, and roster `RevivePriceByRole` support.
- Failure behavior: existing capability checks continue to disable affected bonded actions rather than falling back to generic companion systems.

