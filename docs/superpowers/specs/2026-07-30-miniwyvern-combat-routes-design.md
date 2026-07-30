# MiniWyvern Expanded Combat Routes Design

## Goal

Expand the MiniWyvern Combat talent tree into two meaningful routes that meet
at the form-specific special ability:

- projectiles remain the MiniWyvern's primary, safe, consistent source of
  sustained damage; and
- melee talents provide larger relative improvements to the default swoop,
  turning it into dangerous supplemental burst without overtaking projectile
  DPS.

The tree structure and shared contracts are defined now. The actual Fire,
Ice, Lightning, Nature, Toxic, Void, and Wild special abilities unlocked by
`DraconicAssault` remain TBD and require a later design before that portion of
the tree is implemented.

## Relationship to the Previous Talent Design

This document supersedes only the Combat branch in
`2026-07-29-miniwyvern-talent-progression-design.md` and its implementation
plan. The previous Bond and Vigor designs remain authoritative. Any future
implementation plan must use this document for Combat node IDs, prerequisites,
costs, and behavior and must not restore the old eleven-node Combat layout.

## Combat Identity and Balance

The two routes are deliberately asymmetrical. They are specialization routes,
not equal-DPS alternatives.

The projectile route improves uptime, reach, delivery reliability, and attack
patterns. Its upgrades should produce most of the MiniWyvern's damage over a
long fight and should be relatively insensitive to enemy movement or exposure
windows.

The melee route starts from the default reliable swoop described in
`2026-07-30-miniwyvern-reliable-swoop-design.md`. Its individual talents make
larger percentage changes because the attack begins at a much lower frequency.
The MiniWyvern must descend into enemy range, complete the attack, and recover
before it can resume ranged combat. Talents improve damage, realized hit rate,
and cadence, but do not grant invulnerability or remove that commitment.

The numerical target is:

| State | Swoop damage | Swoop cooldown |
| --- | ---: | --- |
| Default, no melee talents | 16 | 25–35 seconds |
| Fully mastered melee route | 28 | 18–24 seconds |

At mastery, the swoop deals 75 percent more damage. Its average cooldown is 30
percent shorter, making it approximately 43 percent more frequent than the
default version. Together those upgrades produce approximately 2.5 times the
default swoop's sustained contribution without turning an individual strike
into a powerhouse attack. Its isolated sustained DPS remains below the
existing 3–7-second projectile cadence. Swoop damage is physical and adds no
force, impact, launch, stun, or knockback mechanic.

## Selected Tree Shape

The expanded Combat branch contains sixteen nodes: six projectile nodes, six
melee nodes, and four shared special-ability nodes. A player may eventually
purchase both routes; they are not mutually exclusive.

One complete route plus the shared special-ability route costs 19 points,
preserving the current cost of a complete Combat progression. Purchasing both
masteries and every shared node costs 29 points, making dual specialization a
near-total max-level commitment that competes directly with Bond and Vigor.

### Projectile route

| Tier | Talent ID | Purpose | Cost | Minimum level | Prerequisite |
| --- | --- | --- | ---: | ---: | --- |
| 1 | `DraconicProjectile` | Unlock the form's baseline projectile. | 1 | 3 | None |
| 2 | `ProjectileRange` | Improve practical projectile reach. | 1 | 5 | `DraconicProjectile` |
| 2 | `ProjectileCadence` | Reduce time between projectile attacks. | 1 | 5 | `DraconicProjectile` |
| 3 | `ProjectileGuidance` | Improve delivery reliability without perfect accuracy. | 2 | 9 | `ProjectileRange` |
| 3 | `ProjectilePattern` | Improve the form's firing pattern or projectile count. | 2 | 11 | `ProjectileCadence` |
| 4 | `ProjectileMastery` | Combine the route's strongest projectile profile and cadence. | 3 | 14 | `ProjectileGuidance` and `ProjectilePattern` |

`ProjectileForce` and `ProjectileImpact` are removed. They are not replaced by
renamed versions of the same concepts. Damage progression comes from the
concrete base, intermediate, and apex projectile profiles selected by the
remaining talents. Pattern behavior must be visible, such as a form-appropriate
volley or sequence, rather than an invisible generic impact statistic.

Guidance improves the chance that a correctly aimed shot arrives near its
target, but it must not make every projectile perfectly accurate. The existing
dedicated hover-and-aim phase remains shared by every projectile tier.

Projectile cadence retains three deliberate bands:

| Highest cadence milestone | Cooldown |
| --- | --- |
| `DraconicProjectile` without cadence upgrades | 5–7 seconds |
| `ProjectileCadence` or `ProjectilePattern` | 4–6 seconds |
| `ProjectileMastery` | 3–5 seconds |

Range and guidance talents must not accidentally reduce the cooldown on their
own. The base, intermediate, and apex projectile profiles remain the concrete
damage progression, with `ProjectileMastery` selecting the strongest profile.
Exact per-form projectile values remain subject to later in-game balance
testing, but every form must preserve the ranged route's sustained-DPS lead
over its mastered swoop.

Projectile features compose deterministically when several talents are owned:

| Purchased milestone | Projectile profile | Aim phase | Firing pattern | Cooldown |
| --- | --- | --- | --- | --- |
| `DraconicProjectile` only | Base | Existing 0.4–0.7 seconds | One shot | 5–7 seconds |
| `ProjectileRange` | Intermediate velocity, gravity, and lifetime profile | Unchanged | One shot | Unchanged |
| `ProjectileCadence` | Intermediate profile | Unchanged | One shot | 4–6 seconds |
| `ProjectileGuidance` | Intermediate profile | 0.55–0.85 seconds | One shot | Unchanged |
| `ProjectilePattern` | Pattern-tuned apex profile | Unchanged unless guidance is also owned | Two-shot sequence | 4–6 seconds |
| `ProjectileMastery` | Pattern-tuned apex profile | 0.55–0.85 seconds | Two-shot sequence | 3–5 seconds |

The two pattern shots are separated by 0.25–0.4 seconds. Their per-shot damage
is tuned to approximately 65 percent of that form's ordinary apex projectile,
for approximately 130 percent total volley damage if both hit. Pattern assets
retain each form's visual identity and status-effect rules. Wild remains raw
physical damage and cannot acquire an elemental effect.

These features compose rather than overwrite one another. For example, owning
both `ProjectileRange` and `ProjectileCadence` produces the intermediate
profile at 4–6 seconds; owning `ProjectilePattern` and `ProjectileGuidance`
produces the guided two-shot sequence at 4–6 seconds. `ProjectileMastery`
requires both subpaths and selects the complete row regardless of purchase
order.

### Melee route

The default swoop is always available. `SwoopFerocity` begins its improvement
route; it does not unlock the behavior.

| Tier | Talent ID | Purpose | Cost | Minimum level | Prerequisite |
| --- | --- | --- | ---: | ---: | --- |
| 1 | `SwoopFerocity` | First substantial swoop-damage increase. | 1 | 3 | None |
| 2 | `SwoopCadence` | First swoop-cooldown reduction. | 1 | 5 | `SwoopFerocity` |
| 2 | `SwoopPrecision` | Improve pursuit and hit reliability during descent. | 1 | 5 | `SwoopFerocity` |
| 3 | `RelentlessSwoop` | Second swoop-cooldown reduction. | 2 | 9 | `SwoopCadence` |
| 3 | `RendingDive` | Second substantial swoop-damage increase. | 2 | 11 | `SwoopPrecision` |
| 4 | `SwoopMastery` | Reach the route's final damage and cadence profile. | 3 | 14 | `RelentlessSwoop` and `RendingDive` |

The implementation may use a small set of mutually exclusive swoop profiles,
but the externally observable progression must be monotonic:

- damage progresses 16 → 20 → 24 → 28 across `SwoopFerocity`, `RendingDive`,
  and `SwoopMastery`;
- cooldown progresses 25–35 → 22–30 → 20–26 → 18–24 seconds across
  `SwoopCadence`, `RelentlessSwoop`, and `SwoopMastery`; and
- `SwoopPrecision` raises swoop approach speed from 55 to 70 percent of flight
  speed without changing damage, attack distance, the six-second timeout,
  adding crowd control, or eliminating recovery.

Technical recovery must remain reliable at every tier so the MiniWyvern never
gets stuck near the ground. That safety invariant is not a talent benefit.
Combat risk comes from entering melee range and spending time in approach and
recovery, not from intentionally unreliable flight control.

### Shared special-ability route

| Tier | Talent ID | Purpose | Cost | Minimum level | Prerequisite |
| --- | --- | --- | ---: | ---: | --- |
| 5 | `DraconicAssault` | Unlock the MiniWyvern form's unique third combat ability. | 2 | 17 | Either `ProjectileMastery` or `SwoopMastery` |
| 5 | `AssaultUtility` | Improve the special ability's form-specific utility. | 1 | 18 | `DraconicAssault` |
| 5 | `AssaultMastery` | Improve the special ability's primary combat output. | 2 | 21 | `DraconicAssault` |
| 6 | `DraconicApex` | Complete the form-specific special ability. | 4 | 27 | `AssaultUtility` and `AssaultMastery` |

`DraconicAssault` and every node above it are reserved for the third ability.
They must not silently act as another projectile tier or swoop-stat upgrade.
Their exact effects, cadence, presentation, and per-form assets remain blocked
on the later special-ability design.

## Any-Of Prerequisite Contract

Tamework's existing `RequiresTalentIds` list retains its all-of meaning. A new
optional `RequiresAnyTalentIds` list supplies the merge semantics.

Purchase eligibility is:

1. every nonblank ID in `RequiresTalentIds` is purchased; and
2. when `RequiresAnyTalentIds` contains at least one nonblank ID, at least one
   of those IDs is purchased.

For `DraconicAssault`, the asset uses:

```json
"RequiresAnyTalentIds": ["ProjectileMastery", "SwoopMastery"]
```

The contract must be implemented consistently in:

- the `TwTalentConfig` codec and public definition accessors;
- live NPC purchase validation;
- bonded or persisted companion mutation validation;
- command and linked-panel talent pages;
- API and presentation projections that expose prerequisites;
- tree layout connectors and prerequisite labels; and
- unit, integration, codec, and UI contract tests.

The UI should present the merge as “Requires one of” and render connectors from
both route endpoints. Old configs without `RequiresAnyTalentIds` decode to an
empty list and retain their current behavior.

A shared `TalentPrerequisiteEvaluator` must own both all-of and any-of
decisions. Live NPC purchase, bonded/persisted purchase, command UI, linked
panel UI, and API eligibility projections must delegate to it instead of
reimplementing the rules independently.

Config validation rejects blank entries, duplicate IDs within either list,
IDs present in both lists, unknown talent IDs, direct self-references, and
cycles containing all-of or any-of edges. An absent or empty any-of list is
valid. Tamework's example config and asset documentation must describe the new
field, and “Requires one of” must use the same localization system as existing
prerequisite text rather than a hard-coded English UI string.

## Projectile and Swoop Arbitration

The reliable-swoop state machine remains authoritative while a swoop or its
recovery is active. Projectile scheduling and execution require
`Miniwyvern_Swoop_Pending` and `Miniwyvern_Swooping` to be false. Swoop
cooldown expiry sets pending through an instruction ordered before the
projectile scheduler. An already active aim-and-fire sequence may complete,
but pending prevents another sequence from starting; the swoop claims control
as soon as the aim flag clears and continues blocking projectiles until
recovery ends.

Talent changes alter the next eligible attack profile or cooldown. They do not
cancel an attack already in progress, reset the other attack's cooldown, or
allow the projectile and swoop routines to execute simultaneously.

The future special ability must join this same arbitration contract rather
than adding an independent attack loop that can interrupt either routine.

## Data Compatibility

The revised MiniWyvern tree has never been released. No save migration, legacy
ID alias, or point refund is required for removing `ProjectileForce` and
`ProjectileImpact`. The implementation should replace the unreleased config
directly and must not add generic migration machinery solely for this change.

## Implementation Boundaries

The reliable default swoop may be implemented and shipped independently from
the talent-tree redesign.

The expanded tree should be implemented in two dependency-ordered stages:

1. add and verify Tamework's `RequiresAnyTalentIds` contract, then add the
   projectile and melee route assets and behavior; and
2. after the seven form-specific abilities are designed, activate
   `DraconicAssault`, its upgrades, and `DraconicApex`.

Until stage two is designed, production assets must not expose purchasable
special-ability nodes that have no behavior. Documentation may show the
planned shared route, but shippable configs either stop at both tier-four
masteries or arrive together with the completed special abilities.

Grounded MiniWyvern combat, Bond and Vigor branches, owner passives, and every
Nordic Drake, Rock Drake, Hydra, and full-dragon role remain outside this
design.

## Verification Contract

Stage-one implementation plans must include tests proving:

- the config contains both six-node routes and no `ProjectileForce` or
  `ProjectileImpact` talent;
- the default swoop requires no melee talent;
- projectile-only, melee-only, and dual-route purchases behave independently;
- ordinary all-of prerequisites retain their existing semantics;
- a Tamework fixture talent with `RequiresAnyTalentIds` accepts either fixture
  prerequisite and rejects a companion with neither;
- the live and persisted purchase paths make identical prerequisite decisions;
- UI prerequisite text and connectors represent the fixture OR merge;
- malformed any-of configs fail validation under the rules above;
- every projectile talent combination resolves to the specified profile,
  aiming, pattern, and cooldown outputs;
- swoop damage and cooldown profiles progress monotonically to 28 and 18–24;
- projectile cooldowns remain independent from swoop cooldowns;
- simultaneous projectile and swoop readiness gives the swoop a starvation-
  free pending handoff;
- no talent removes swoop recovery or grants force, impact, knockback, stun,
  or invulnerability; and
- no non-MiniWyvern species or locomotion state changes.

Stage-two special-ability plans must additionally prove that production
`DraconicAssault` accepts either `ProjectileMastery` or `SwoopMastery`, rejects
a companion with neither, and that all four shared nodes execute their defined
per-form behavior. Those production-tree assertions do not gate the stage-one
endpoint while the special abilities remain TBD.

All affected Hytale NPC assets require exact-profile validation against the
locked 0.5.7 release, focused Maven tests, full verification in both changed
repositories, packaging, local installation, and in-game acceptance testing.
