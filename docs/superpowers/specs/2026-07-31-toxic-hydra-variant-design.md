# Toxic Hydra Variant Design

## Goal

Add a rare Toxic Hydra to overground Zone 1 swamps. It must preserve the Ice
Hydra's combat selection, timing, damage, movement, taming, bonded-companion,
mount, drop, and animation behavior while replacing its appearance and every
combat impact with toxic equivalents. Toxic impacts use poison damage over
time rather than the Ice Hydra's chilled effect.

The implementation targets the locked `release-0.5.7` HyDragon asset profile.

## Design Principles

- Parameterize elemental differences instead of copying the full Hydra role,
  combat evaluator, or attack choreography.
- Preserve the existing `Hydra`, `Tamed_Hydra`, and ice-facing interaction IDs
  so the feature does not break existing saves or external references.
- Treat the Ice Hydra as the behavioral baseline. Numeric differences are not
  permitted except where the approved poison effect inherently adds damage
  over time.
- Keep the change asset-driven. No Java runtime changes are required.
- Keep the Toxic Hydra in the existing `hydragon:hydra` species and full-dragon
  bonded family.

## Role Inheritance

Hytale 0.5.7 supports variants that reference other variants. The new wild role
`Hydra_Toxic` therefore references `Hydra`, and `Tamed_Hydra_Toxic` references
`Tamed_Hydra`.

`Hydra_Toxic` overrides only:

- `Appearance` with `Hydra_Toxic`;
- `FlockArray` with only `Hydra_Toxic`;
- `TameRoleChange` with `Tamed_Hydra_Toxic`;
- `MemoriesNameOverride` with `Toxic Hydra`;
- the elemental interaction-variable map described below; and
- the localized name parameter.

`Tamed_Hydra_Toxic` overrides only:

- `Appearance` with `Hydra_Toxic`;
- `FlockArray` with only `Tamed_Hydra_Toxic`;
- `MemoriesNameOverride` with `Toxic Hydra`;
- the same elemental interaction-variable map; and
- the localized bonded name parameter.

All other declared and effective values come through the existing variant
chain. The Toxic Hydra must not restate health, movement, combat ranges,
cooldowns, command capabilities, mount values, or other shared behavior.

The child roles declare the complete `_InteractionVars` map they need. This is
intentional: a parameter value is replaced as a unit, so the design does not
assume that map values merge with the parent.

## Shared Melee Choreography

The Ice Hydra's melee selectors already expose replaceable damage interactions:

- `Bite_Damage`;
- `Swipe_Left_Damage`;
- `Swipe_Right_Damage`;
- `Stomp_Damage`; and
- `Tail_Spin_Damage`.

Both toxic roles continue using the existing melee roots, selectors,
animations, hit volumes, sounds, knockback, base damage, and random modifiers.
For each variable, the toxic role supplies a damage interaction that inherits
the corresponding Hydra damage interaction and appends `ApplyEffect` for
`Poison_T1` to the successfully damaged target.

Blocked, missed, or otherwise unsuccessful melee attacks do not apply poison.
The Ice Hydra's current variable replacements remain unchanged.

## Parameterized Ranged Choreography

The existing ball and rain sequences retain their public roots and total
timing, but their elemental work becomes replaceable interaction leaves. This
keeps `CAE_Hydra` shared by both variants and avoids duplicating the evaluator.

The shared choreography exposes four variables:

| Variable | Responsibility | Ice default | Toxic override |
| --- | --- | --- | --- |
| `Hydra_Ball_Charge_Effect` | Mouth charge particles only | Existing ice charge particles | Poison charge particles |
| `Hydra_Ball_Launch` | One aimed projectile launch and its launch feedback | Existing Ice Ball launch | Toxic Ball launch |
| `Hydra_Rain_Charge_Effect` | One barrage charge pulse | Existing ice charge pulse | Poison charge pulse |
| `Hydra_Rain_Launch` | One randomized barrage projectile and lingering hazard | Existing rain Ice Ball launch | Toxic rain launch |

Animation and delay nodes remain outside these replacement leaves. Therefore,
element overrides cannot accidentally change prepare, charge, inter-shot, or
finish timing.

The shared three-shot attack retains:

- one `PrepareShoot` phase;
- charge durations of `1.0`, `0.5`, and `0.5` seconds;
- three direct shots using `CAETargetSlot`;
- `FinishShoot` for `0.5` seconds; and
- the existing attack cooldown and range selected by `CAE_Hydra`.

The shared rain barrage retains every existing charge/launch repetition,
random source radius, projectile physics, hazard radius, hazard duration,
tick interval, and damage per tick. Only the projectile, feedback, status
effect, and source type ID differ for Toxic.

## Toxic Projectiles and Poison

Create `Hydra_Toxic_Ball` and `Hydra_Rain_Toxic_Ball` projectile assets by
copying the respective Ice Hydra physics and damage values exactly.

The toxic projectile appearance uses a mod-local model asset backed by Hytale
0.5.7's canonical `Items/Projectiles/Acid.blockymodel` and
`Items/Projectiles/Acid_Texture.png`. It uses poison/acid particles rather than
ice particles. Use these release-0.5.7 feedback IDs:

- `Effect_Poison` for charge or emission feedback;
- `Impact_Poison` and the Acid projectile particle family for impact/death
  feedback;
- `SFX_Scarak_Spitball_Fire` for launch feedback; and
- `SFX_Scarak_Seeker_Spitball_Death` for projectile death feedback.

Exact-profile schema and reference validation must accept each ID in its
assigned field before materialization. A validation failure blocks the design
for review instead of permitting an unreviewed substitute.

Each aimed Toxic Ball replaces the Ice Ball's `Chilled` impact effect with
`Poison_T1`, keeping the same `3.0` radius and source exclusion. The toxic rain
hazard replaces `Chilled` with `Poison_T1`, retains its `4.0` radius, `6.0`
second duration, `1.0` second tick interval, and `5.0` damage per tick, and uses
the distinct source type `hydragon.rain_toxic_hazard`.

In Hytale 0.5.7, effective `Poison_T1` behavior is:

- `6` Poison damage per tick;
- `5` seconds between damage calculations;
- `16` seconds duration;
- overlap behavior `Extend`; and
- canonical poison audio, particles, tint, screen effect, and status icon.

Direct projectile damage, explosion damage, explosion radius, knockback,
block-damage behavior, velocity, gravity, and lifetime remain identical to the
corresponding Ice Hydra projectile. The poison damage over time is the approved
elemental difference and is not subtracted from direct damage.

## Appearance and Texture

Add model asset `Hydra_Toxic` as a child of `Hydra`, overriding only `Texture`
with `NPC/HyDragon/Hydra/Model/Toxic.png`.

Create `Toxic.png` from the existing `Ice.png` atlas with these constraints:

- preserve the exact pixel dimensions, UV layout, and transparent regions;
- do not move, redraw, expand, crop, or rotate atlas regions;
- use dark violet and swamp-dark blue for armored scales;
- use luminous chartreuse for toxin-bearing crystal, mouth, eye, and accent
  regions; and
- visually match HyDragon's existing Acid Nordic Drake and Toxic Miniwyvern
  palette without copying their unrelated UV layouts.

Texture verification compares dimensions and alpha occupancy against
`Ice.png`, then visually inspects the rendered atlas and in-game model when a
live check is available.

## Swamp Spawn

Add one world-spawn asset under `Server/NPC/Spawn/World/Zone1` for
`Hydra_Toxic` with:

- `Environments: ["Env_Zone1_Swamps"]`;
- `Weight: 1`;
- `SpawnBlockSet: "Mud"`;
- the Ice Hydra's `[6, 18]` day-time range;
- the Ice Hydra's `[0, 4]` moon-phase range; and
- the Ice Hydra's five moon-phase weight modifiers.

The Toxic Hydra does not spawn in `Env_Zone1_Caves_Swamps` or any non-swamp
environment. Its environment-and-role combination is registered only once.
Rarity comes from the role weight, not from changing the swamp environment's
global spawn density.

## HyDragon and Tamework Integration

Update the existing Hydra species definition rather than adding a second
species:

- add `Hydra_Toxic` to `WildRoleIds`;
- map it to `Tamed_Hydra_Toxic` in `TamedRoleIdByWildRole`;
- add the toxic appearance to `Presentation.ModelIds`; and
- add the toxic swamp spawn asset to `Spawn.OrdinarySpawnAssetIds`.

Extend all role allowlists and mappings required for parity with the Ice Hydra:

- Hydra capture policy;
- Draconic Stone capture allowlist and tamed-role override;
- bonded full-dragon roster;
- full-dragon companion configuration;
- Dragon Horn command allowlist; and
- bonded-companion breeding role list.

The toxic wild role uses the existing Hydra drop list, capture resistance,
minimum stone tier, bonded roster/family, revive price, session limit, summon
cooldown, and ground-mount behavior.

Capturing or taming a Toxic Hydra must always produce `Tamed_Hydra_Toxic` so
its texture and poison-bearing melee replacements survive the role change.
The design does not add ranged attacks to the tamed Toxic Hydra because the
existing `Tamed_Hydra` does not have them.

## Localization

Add names to every bundled language file:

| Locale | Wild | Tamed |
| --- | --- | --- |
| `en-US` | Toxic Hydra | Bonded Toxic Hydra |
| `de-DE` | Toxische Hydra | Gebundene toxische Hydra |
| `es-ES` | Hidra tóxica | Hidra tóxica vinculada |
| `fr-FR` | Hydre toxique | Hydre toxique liée |
| `pt-BR` | Hidra tóxica | Hidra tóxica vinculada |

The role parameters reference distinct Toxic Hydra localization keys. Existing
Hydra strings remain unchanged.

## Validation and Tests

Automated asset-contract coverage must prove:

1. `Hydra_Toxic` resolves through `Hydra`, and `Tamed_Hydra_Toxic` resolves
   through `Tamed_Hydra`.
2. Shared stats, ranges, cooldowns, timings, animation IDs, selector geometry,
   direct damage, projectile physics, drops, and mount values match Ice.
3. The shared evaluator and ranged choreography resolve to ice leaves for
   `Hydra` and toxic leaves for `Hydra_Toxic`.
4. All five successful toxic melee damage paths apply `Poison_T1` after damage.
5. Toxic ranged and rain paths select toxic projectiles, poison/acid feedback,
   and `Poison_T1`. Shared choreography may retain legacy ice-named public IDs
   and declared ice defaults for compatibility, but role-resolved toxic
   replacement selection must never execute a `Chilled`, ice projectile, or
   ice particle leaf.
6. Existing Ice Hydra effective behavior remains unchanged after the shared
   choreography refactor.
7. The Toxic Hydra spawns only in `Env_Zone1_Swamps` at weight `1` and has no
   duplicate environment-role registration.
8. Capture, role change, roster, commands, companion behavior, breeding, and
   species metadata include both toxic roles where appropriate.
9. `Toxic.png` matches `Ice.png` dimensions and alpha occupancy and is the
   texture resolved by `Hydra_Toxic`.
10. All five locale files contain both Toxic Hydra name keys.

Static validation uses the exact `release-0.5.7` profile and includes affected
inheritance and reverse-reference closure. Run Hytale asset reference/schema
validation, the repository's focused asset-contract tests, packaging checks,
and the full Maven verification suite. A live spawn/combat/capture pass is
reported separately if the configured runtime harness is unavailable.

## Out of Scope

- Changing the Ice Hydra's balance or presentation.
- Adding a separate Toxic Hydra species, drop table, capture difficulty, or
  bonded family.
- Adding ranged attacks to tamed Hydras.
- Spawning Toxic Hydras in swamp caves or other zones.
- Adding Java-only poison systems when `Poison_T1` already provides the
  required release-supported behavior.
- Generalizing non-Hydra dragons or unrelated interaction assets.
