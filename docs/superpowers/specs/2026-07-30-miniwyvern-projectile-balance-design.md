# MiniWyvern Projectile Balance Design

## Goal

Rebalance MiniWyvern projectiles around the companion's intended identity: a
hawk- or eagle-sized support combatant that contributes safe, reliable ranged
damage and form-specific utility without behaving like a primary damage
dealer.

This document is authoritative for MiniWyvern projectile damage, status
effects, firing patterns, and delivery architecture. The Combat-tree shape,
talent costs, prerequisites, and projectile/swoop arbitration remain defined
by `2026-07-30-miniwyvern-combat-routes-design.md`.

## Balance Baseline

Locked Hytale 0.5.7 NPC benchmarks place ordinary ranged attacks around 13–20
damage: a Skeleton Archer has 36 health and fires 13-damage arrows, while a
comparable Outlander spear deals 20 damage. Attacks above 30 damage read as
elite or boss-level output. MiniWyvern projectiles should sit below or near
that ordinary band because their short repeat cadence, airborne safety, and
status utility all add value beyond the direct hit.

The selected ordinary single-shot damage benchmarks are:

| Form class | Base | Intermediate | Apex |
| --- | ---: | ---: | ---: |
| Fire, Ice, Lightning, Nature, Toxic, Void | 8 | 12 | 16 |
| Wild | 10 | 15 | 20 |

Base and Intermediate are selectable single-shot profiles. Apex is the
would-be final single-shot benchmark used to balance the route, but it is not
a separately selectable attack: `ProjectilePattern` deliberately replaces it
with the exact two-shot profile below. Implementations therefore need Base,
Intermediate, and Pattern damage interactions, not an unreachable 16- or
20-damage single-shot interaction. Pattern's total damage modestly exceeds the
Apex benchmark because both projectiles must connect.

Elemental forms trade some direct damage for support effects. Wild has no
status effect and therefore receives the highest reliable raw damage. These
values contain no hidden splash, block damage, knockback, force, impact, or
stun damage.

## Problems in the Current Assets

The current form projectiles were assembled from unrelated adult-dragon and
vanilla assets. Their direct damage ranges from 14 to 70, several retain adult
species splash or block-damage behavior, some upgrade paths remove secondary
damage instead of progressing cleanly, and Void's nominal tiers move
backwards. The current deprecated `LaunchProjectile` interaction also exposes
only a projectile ID and does not provide a form-owned hit chain for applying
the approved status effects.

The replacement must not attempt to balance those inherited profiles in
place. MiniWyverns need dedicated projectile configs and hit interactions.

## Selected Delivery Architecture

Each of the seven MiniWyvern form launch interactions migrates from the
deprecated `Type: "LaunchProjectile"` interaction to the 0.5.7
`Type: "Projectile"` interaction with a dedicated `ProjectileConfig`.
MiniWyvern projectile configs live under a MiniWyvern-owned asset namespace
and define their own model, physics, launch force, spawn offset, and hit/miss
chains.

Every `ProjectileHit` chain executes at most once in this order:

1. apply the exact direct-damage interaction for the resolved profile;
2. apply the form status when this is a status-enabled shot;
3. play the form-specific hit presentation; and
4. despawn the projectile.

Every miss chain plays only appropriate miss presentation and despawns. A
miss deals no damage and applies no status. Hostile entity collision executes
the hit chain and terminates the projectile immediately. Terrain or block
collision executes the miss presentation and terminates without block damage.
Lifetime expiry terminates through the same harmless miss/despawn path.

Owner, party, allied, and otherwise friendly entities must never be damaged or
debuffed. Hytale 0.5.7 projectile physics already ignores the projectile's
creator, so the owner does not consume the shot. Other collidable allies cannot
be made pass-through through `ProjectileConfig`: the core physics tick marks a
projectile inactive before its hit interaction and exposes no safe
pre-collision veto hook. Those allied collisions therefore terminate through a
harmless no-damage/no-status path. Replacing core projectile physics solely to
let a shot pass through a party member is outside this balance pass.

The first terminal hostile, allied, or world collision owns an atomic
at-most-once outcome; the despawn in that outcome prevents later collision
callbacks from executing another chain.

Dedicated configs may share physics or presentation parents where their
observable behavior is identical, but they must not inherit adult-dragon
splash, block damage, oversized collision, or unrelated status behavior.

## Form Matrix

Status potency does not scale with projectile tier. Higher profiles improve
direct damage and delivery, while the form's utility remains predictable.

| Form | Direct damage | Status on a status-enabled hit |
| --- | --- | --- |
| Fire | 8 / 12 / 16 | Burn for 2 damage each second for 4 seconds. |
| Ice | 8 / 12 / 16 | Reduce horizontal movement speed by 20% for 4 seconds. |
| Lightning | 8 / 12 / 16 | Interrupt the current interruptible ability and prevent ability use for 0.5 seconds; movement remains available. |
| Nature | 8 / 12 / 16 | Root movement for 0.6 seconds; abilities remain available. |
| Toxic | 8 / 12 / 16 | Reduce outgoing damage by 10% for 5 seconds. |
| Void | 8 / 12 / 16 | Increase damage received by 10% for 5 seconds. |
| Wild | 10 / 15 / 20 | None. |

All statuses refresh their duration and never stack their magnitude, including
when several allied MiniWyverns apply the same status. Effects from repeated
MiniWyvern hits use one global effect identity per form and overwrite behavior,
not per-caster stacks. Fire and Ice may reuse
the existing `HyDragon_Miniwyvern_Fire_Burn` and
`HyDragon_Miniwyvern_Ice_Slow` assets because those already match this
contract.

An uninterrupted Fire application produces four 2-damage periodic ticks. A
refresh follows the engine's normal overwrite timing: it resets the remaining
duration and periodic schedule, creates no immediate bonus tick, and never
leaves two tick schedules active.

Lightning receives a dedicated `HyDragon_Miniwyvern_Lightning_Shock` effect.
It must not disable movement or use the existing owner speed boon. The hit
chain interrupts the target's current interruptible ability and the effect
briefly prevents a replacement ability; uninterruptible actions retain their
engine contract.

Nature receives a dedicated `HyDragon_Miniwyvern_Nature_Root` effect. It
disables horizontal movement for 0.6 seconds without disabling abilities and
must not reuse the owner regeneration effect.

Toxic receives a dedicated
`HyDragon_Miniwyvern_Toxic_Projectile_Weakness` marker and gameplay path. The
current owner-aura weakness is backed by Java registry state, so applying its
marker alone would not reduce outgoing damage. The projectile implementation
must make the damage system recognize the active projectile effect on the
damage source, or provide one equivalent authoritative path, so the stated
10% reduction is real. It must not change the Bond aura's separate 12% for 6
seconds contract.

Void receives a dedicated
`HyDragon_Miniwyvern_Void_Projectile_Exposure` effect providing 10% increased
damage received for 5 seconds. It must not change or reuse the Bond aura's
separate 12% for 6 seconds effect.

Toxic applies a 0.90 multiplier to damage attributed to the debuffed entity;
Void applies a 1.10 multiplier to damage received by the debuffed entity. Both
cover direct and periodic entity-caused health damage, regardless of physical
or elemental type, and exclude healing, self-damage, block damage, and
unattributed environment damage. The multipliers operate on the engine's
floating-point damage value after base damage and ordinary attacker bonuses
are assembled but before the engine's standard final clamp or rounding;
neither effect introduces custom integer rounding.

Matching Bond and projectile versions are the same modifier category and use
strongest-wins semantics rather than stacking: the Bond version's 12% modifier
wins while both are active, and the projectile version becomes effective only
for its own remaining duration after the Bond effect ends. Refreshing the
projectile status does not extend the Bond status. Unrelated modifier
categories continue to compose through the engine's existing rules; Toxic and
Void may coexist because one modifies outgoing damage and the other incoming
damage.

## Talent Resolution

Projectile talents compose rather than replacing unrelated benefits:

| Purchased milestone | Damage profile | Aim phase | Pattern | Cooldown |
| --- | --- | --- | --- | --- |
| `DraconicProjectile` only | Base | 0.4–0.7 seconds | One status-enabled shot | 5–7 seconds |
| `ProjectileRange` | Intermediate | Unchanged | One status-enabled shot | Unchanged |
| `ProjectileCadence` | Intermediate | Unchanged | One status-enabled shot | 4–6 seconds |
| `ProjectileGuidance` | Intermediate | 0.55–0.85 seconds | One status-enabled shot | Unchanged |
| `ProjectilePattern` | Pattern damage, Intermediate ballistics | Unchanged unless Guidance is owned | Two-shot sequence | 4–6 seconds |
| `ProjectileMastery` | Pattern damage, Apex ballistics | 0.55–0.85 seconds | Two-shot sequence | 3–5 seconds |

`ProjectileRange` owns the intermediate velocity, gravity, and lifetime
profile but does not alter cadence. `ProjectileCadence` owns cooldown changes
but does not silently improve guidance. `ProjectileGuidance` extends the
dedicated hover-and-aim phase and improves practical delivery without making
shots perfectly accurate.

The pattern sequence fires two shots exactly 0.30 seconds apart:

| Form class | First shot | Second shot | Total if both hit |
| --- | ---: | ---: | ---: |
| Elemental forms | 10, status enabled | 10, no status | 20 |
| Wild | 12 | 12 | 24 |

Only the first pattern shot can apply a status. The second shot uses a
damage-only config even when the first misses, preventing a second status
application and keeping the volley contract deterministic. Pattern and
Mastery retain the form's approved cooldown band rather than multiplying
cadence by two full-power apex shots.

Before the first pattern shot, the scheduler asserts a dedicated
`Miniwyvern_Projectile_Volley_Active` latch. It remains asserted through the
0.30-second interval and the second launch, then clears immediately. The
existing aim/attack-active signal also remains asserted for that entire
sequence. Swoop readiness may set `Miniwyvern_Swoop_Pending` during a volley,
which prevents any new projectile sequence, but the active volley completes
both shots before the swoop claims flight control. Command change, target
loss, death, despawn, or leaving eligible combat cancels the remainder of the
volley, clears both active signals, and launches no second shot; a pending
swoop alone never cancels it.

## Ballistics and Presentation

Each form retains its recognizable projectile model, model-owned trail,
sound, and trajectory character. Those differences may not make one form
unusable at the MiniWyvern's normal 8–20-block combat distance. The attack is
authored entirely through modern `ProjectileConfig` assets; no legacy
`Server/Projectiles` profile participates.

The exact `{LaunchForce, TerminalVelocityAir, Gravity, ProjectileSpawn
timeout}` profiles are Base `{28,32,6,4}`, Intermediate `{34,40,4,5}`, and
Apex `{40,48,3,6}`. Pattern uses Intermediate and Mastery uses Apex. Physics
uses inline `Type: Standard`; launch depth is `SpawnOffset.Z = 1`. Damage
exists only in the `DamageEntity` hit interaction.

Each config starts a 4/5/6-second `ProjectileSpawn` interaction that waits and
removes the projectile. Hytale 0.5.7 queues projectile-generated hit/miss
chains without applying interaction interruption rules, so a hit or miss
removes the proxy immediately while the short spawn timeout may remain queued.
Its later `RemoveEntity` against the already-removed proxy is an idempotent
no-op and cannot replay damage, status, presentation, or removal side effects.
An owner-scoped interrupt is forbidden because it could cancel a different
projectile in the same volley.
Shots are not intended to cross an entire battlefield: the existing combat
target limit remains authoritative, and profiles should not create useful
reach beyond that target-selection range.

The balance pass must not modify the dedicated aiming phase, hover behavior,
orbit behavior, or general flight-state arbitration except for the explicit
volley-active latch required to complete or cancel a two-shot pattern safely.

## Scope Boundaries

The implementation may change:

- the seven MiniWyvern form launch/root interactions;
- dedicated MiniWyvern projectile configs, damage interactions, and impact
  presentation assets;
- the approved MiniWyvern enemy status assets;
- the Toxic outgoing-damage filter and focused tests required to make its
  projectile weakness authoritative; and
- the projectile talent resolver and tests needed to select the exact profile,
  pattern shot, guidance, and cooldown.

It must not change Bond owner effects, `DraconicAssault`, swoop damage or
behavior, grounded combat, any other species' projectiles, or global vanilla
projectile assets.

## Verification Contract

Automated verification must prove:

- every single-shot talent combination resolves to the specified damage,
  aim, ballistics profile, and cooldown;
- Pattern and Mastery resolve to 10 + 10 elemental or 12 + 12 Wild damage,
  with only the first shot status-enabled;
- all seven form behaviors match the matrix, elemental statuses refresh
  instead of stacking across one or several casters, and status potency does
  not scale with projectile tier;
- Toxic actually reduces outgoing damage by exactly 10% while its projectile
  effect is active, then restores normal damage after 5 seconds;
- Void increases received damage by exactly 10% for 5 seconds without
  altering the Bond version;
- Lightning permits movement, interrupts only interruptible actions, and
  blocks ability use for only 0.5 seconds;
- Nature roots movement for only 0.6 seconds without disabling abilities;
- Wild applies no elemental status;
- hits cannot apply splash, block damage, force, impact, or knockback;
- misses and second pattern shots cannot apply a status;
- the two pattern launches are separated by 0.25–0.4 seconds, keep volley and
  attack activity asserted throughout, and complete before a pending swoop;
- command change, target loss, death, despawn, or combat exit cancels a pending
  second shot and clears volley activity;
- a hostile hit executes once and despawns atomically, terrain collision uses
  the harmless miss path, the explicit `ProjectileSpawn` timeout removes an
  expired shot, the creator is ignored by core physics, and other friendly
  collisions terminate without damage or status;
- owner and friendly targets are immune to both damage and statuses;
- Base, Intermediate, and Apex ballistics progress monotonically in practical
  reach, all reach a stationary target at 20 blocks, and none creates useful
  reach beyond the existing target-selection range;
- Toxic and Void retain floating-point precision through normal engine final
  resolution, use strongest-wins behavior with their matching Bond effect,
  and compose normally with unrelated modifier categories;
- an uninterrupted Fire application produces exactly four ticks, while a
  refresh produces no immediate bonus tick or concurrent tick schedule;
- MiniWyvern assets no longer use deprecated `LaunchProjectile` interactions,
  while no unrelated species is migrated as collateral work; and
- every new projectile config and interaction validates against locked Hytale
  0.5.7 schemas.

Focused tests, the full Maven suite, asset validation, packaging, and local
installation are required before in-game acceptance. Runtime acceptance uses
representative 36-, 54-, and 61-health ordinary enemies and confirms that no
ordinary single projectile one-shots them, statuses are visible and refresh
correctly, friendly entities remain unaffected, and existing aim and flight
behavior do not regress.
