# Nordic Drake Flight Progression Design

## Goal

Add reusable Tamework support for Avatar Flight progression, then configure
HyDragon's `Tamed_NordicDrake` as a level-30 legendary companion. Progression
uses a mix of normal companion combat XP and server-authoritative time spent in
fast Avatar Flight. The first release deliberately excludes breeding, needs,
and happiness systems.

## Scope

Tamework owns generic flight XP, flight talent effect semantics, runtime
application of those effects, public XP event reporting, configuration
inheritance, and tests. HyDragon owns the Nordic Drake leveling and talent
assets, player-facing localization, asset validation, and integration tests.

No Tamework Java class, API identifier, or default config may mention
`NordicDrake` or HyDragon. The capability must be usable by any NPC-backed
Avatar Flight profile.

## Flight XP

`TwLevelingConfig.XpSources` gains a nested `Flight` object. It controls:

- `Enabled`
- `XpPerQualifiedSecond`
- `AwardIntervalSeconds`
- `MaxXpPerMinute`

The normal parent-child behavior applies: omitting `Flight` inherits the
complete parent object, while an explicit `Flight` object inherits only its
omitted nested keys. Codec documentation and inheritance tests must state and
exercise this behavior.

Avatar Flight earns XP only when all of the following are true:

1. The transformed player has an active, NPC-backed Avatar Flight mount
   session in the same world.
2. The session's source NPC UUID resolves to a valid companion.
3. Avatar Flight is applying its custom velocity this tick.
4. The controller classifies the output as fast flight using the profile's
   existing relative fast-flight threshold.
5. The source NPC resolves to an enabled leveling config with `Flight` XP
   enabled.

Qualified server tick time is accumulated and paid in batches through
`CompanionLevelingService` to the parked source NPC. It uses the new public
`CompanionXpSource.AVATAR_FLIGHT` value, preserving ordinary level-up,
persistence, diagnostics, and event behavior. The tracker stores only the
current session's qualified-time and rate-limit state on the existing
`AvatarFlightComponent`.

The system measures elapsed server tick time, not client packets, raw travel
distance, or player-reported speed. Each tick contributes at most a bounded
duration before it enters the accumulator. The configured XP-per-minute cap is
also enforced. This prevents lag spikes, clock jumps, faster companion
profiles, idle hovering, falling, swimming, vertical-only movement, and
input-loop farming from granting excess progress.

When the player falls below the fast-flight threshold, is grounded, swims,
falls, loses a valid session/source, enters another world, or Avatar Flight
tears down, qualification stops and no unpaid XP is granted. Session-local
progression state is cleared during teardown; a stale persisted session is
already recovered by Avatar Flight's normal epoch cleanup.

## Flight Talent Effects

Tamework adds these generic passive effect keys, consumed only by the Avatar
Flight runtime when a valid source companion is mounted:

| Effect key | Semantics |
| --- | --- |
| `AvatarFlightVigourCapacityMultiplier` | Higher is better; multiplies maximum Vigour charges. |
| `AvatarFlightVigourRechargeRateMultiplier` | Higher is better; multiplies charge recovery rate. |
| `AvatarFlightForwardBoostCostMultiplier` | Lower is better; multiplies Vigour spent by a forward boost. |
| `AvatarFlightForwardBoostImpulseMultiplier` | Higher is better; multiplies forward-boost impulse. |
| `AvatarFlightGlideSinkMultiplier` | Lower is better; multiplies unpowered glide sink speed. |
| `AvatarFlightClimbLiftMultiplier` | Higher is better; multiplies pitch-up lift. |

`AvatarFlightMovementSystem` resolves the source companion from the mount
session and derives an immutable, clamped effective-flight tuning before
running the controller and Vigour logic. The effective-tuning layer is the
only place that interprets the effect keys and applies documented bounds; JSON
talent configs remain declarative. A missing source, missing progression
component, disabled talent config, or unknown effect is equivalent to a
multiplier of `1.0`.

## Nordic Drake Leveling

`Tamed_NordicDrake` receives dedicated level and talent configs under the
existing `Server/Tamework/Leveling` and `Server/Tamework/Talents` trees.

| Setting | Value |
| --- | --- |
| Maximum level | `30` |
| Base XP | `155` |
| Growth factor | `1.09` |
| Level 1 to 30 total XP | `19,241` |
| Talent points | `1` for each level after level 1 (`29` total) |
| Health per level after level 1 | `0.4%` |
| Damage dealt per level after level 1 | `0.2%` |
| Flight XP rate | `0.15 XP` per qualified second |
| Flight XP batch interval | `10` qualified seconds |
| Flight XP cap | `9 XP` per minute |

Flight-only progression reaches level 30 in about 35.6 hours. Normal combat
XP remains enabled as a second path and reduces that time for ordinary mixed
use. Feed, harvest, and breeding XP are disabled for this configuration.

## Nordic Drake Talent Tree

All talent names and descriptions use HyDragon localization keys. Branches
offer more total cost than 29 points, so a player can specialize but cannot
purchase every capstone.

### Aerial Mastery

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Northwind Resolve | 1 | 1 | None | `AvatarFlightVigourCapacityMultiplier` `1.15` |
| Sustained Current | 6 | 2 | Northwind Resolve | `AvatarFlightVigourRechargeRateMultiplier` `1.15` |
| Flamewake | 6 | 2 | Northwind Resolve | `AvatarFlightForwardBoostCostMultiplier` `0.88` |
| Thermal Guard | 14 | 3 | Sustained Current | `AvatarFlightGlideSinkMultiplier` `0.86` |
| Skyrend | 14 | 3 | Flamewake | `AvatarFlightForwardBoostImpulseMultiplier` `1.15` |
| Storm Sovereign | 24 | 4 | Thermal Guard and Skyrend | Vigour capacity `1.10`; climb lift `1.12` |

### War Drake

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Ember Discipline | 1 | 2 | None | `DamageDealtMultiplier` `1.02` |
| Furnace Heart | 8 | 2 | Ember Discipline | 3% less incoming damage (`DamageTakenMultiplier` toughness `1 / 0.97`) |
| Scorching Momentum | 8 | 3 | Ember Discipline | `DamageDealtMultiplier` `1.035` |
| Ruinous Breath | 16 | 4 | Scorching Momentum | `DamageDealtMultiplier` `1.045` |
| Iron Talons | 16 | 3 | Furnace Heart | `MaxHealthMultiplier` `1.05` |
| Jarl's Bane | 24 | 4 | Ruinous Breath and Iron Talons | Damage dealt `1.04`; 3% less incoming damage (toughness `1 / 0.97`) |

### Wyrmguard

| Talent | Level | Cost | Requirement | Effect |
| --- | ---: | ---: | --- | --- |
| Runestone Hide | 1 | 1 | None | `MaxHealthMultiplier` `1.04` |
| Glacier Scales | 7 | 2 | Runestone Hide | 4% less incoming damage (`DamageTakenMultiplier` toughness `1 / 0.96`) |
| Long Vigil | 7 | 2 | Runestone Hide | `MaxHealthMultiplier` `1.04` |
| Unyielding | 15 | 3 | Long Vigil | `MaxHealthMultiplier` `1.06` |
| Sagascar | 15 | 3 | Glacier Scales | 6% less incoming damage (toughness `1 / 0.94`) |
| Northern Bulwark | 24 | 4 | Unyielding and Sagascar | Max health `1.05`; 4% less incoming damage (toughness `1 / 0.96`) |

`DamageTakenMultiplier` is consumed by current Tamework as a toughness value:
runtime incoming damage is divided by the composed multiplier. The reciprocal
values above preserve the intended 3%, 4%, and 6% reductions instead of
accidentally increasing incoming damage.

## Validation

Tamework automated coverage must verify:

1. `Flight` config codec parsing, nested inheritance, and default-safe
   behavior.
2. XP only accrues during qualified fast flight and remains fair across
   profiles with different raw travel speeds.
3. Unqualified modes, invalid/missing/wrong-world source sessions, teardown,
   and stale sessions grant no flight XP.
4. Tick-duration bounds, XP batching, and per-minute caps prevent oversized
   awards.
5. Flight XP reaches the source companion and emits `AVATAR_FLIGHT` through
   the public event and debug paths.
6. Each flight effect applies only through the mounted source companion and
   obeys its effective-tuning bound.
7. Existing Avatar Flight behavior is unchanged at `1.0` multipliers.

HyDragon automated coverage must verify that its two configs target only
`Tamed_NordicDrake`, reference every intended talent/effect/localization key,
parse with Tamework installed, and package into the released JAR. Exact-profile
asset validation must inspect declared and effective config values and validate
the affected scope before live testing.

## Non-Goals

- Flight XP from travel distance, client packets, raw input events, idle time,
  or combat performed by the transformed player.
- Breeding, needs, happiness, harvesting, or feed-based Nordic Drake
  progression.
- Species-specific behavior in Tamework.
- Any change to Nordic Drake's capture gate, roster policy, Avatar Flight model,
  or existing combat-action definitions.
