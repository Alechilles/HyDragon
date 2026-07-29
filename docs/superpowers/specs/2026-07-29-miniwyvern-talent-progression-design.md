# Miniwyvern Talent Progression Design

**Status:** Approved for implementation planning
**Date:** 2026-07-29

## 1. Purpose

Soul-Bond Miniwyverns gain a persistent, shared talent tree that gives their
owner meaningful long-term progression without creating a separate build for
each elemental form. The player develops the Miniwyvern itself, then chooses
which form to summon and which parts of the larger tree to specialize in.

The system must be asset-first. NPC role instructions, root interactions,
entity effects, and Tamework talent assets own every ability that can be
expressed through them. Java is permitted only for reusable, engine-level
Tamework primitives that assets cannot otherwise express.

## 2. Player experience

1. A player raises their single Soul-Bond Miniwyvern to level 30.
2. The Miniwyvern earns experience from real combat and a slow, capped trickle
   while actively summoned.
3. The player spends earned points in one persistent tree through Tamework's
   companion-talent UI.
4. Talent nodes unlock generic capability flags, not form-specific permanent
   builds.
5. The currently active form's role assets interpret each owned flag as that
   form's passive, projectile, upgrade, or advanced attack.
6. Changing form never removes XP, spent points, or purchased talents.
7. The player may reset the complete tree freely and immediately.

At level 30, a Miniwyvern has 29 points but the tree costs 51 points in total.
It can deeply develop one branch and take selected cross-branch investments,
but cannot purchase every capstone.

## 3. Authority and persistence

There is one `TwLevelingConfig` and one `TwTalentConfig` that apply to every
bonded Miniwyvern role. The Tamework leveling and talents components are the
only progression authority; their normal bonded-companion persistence carries
the tree through storage, summoning, death, revival, relog, and role changes.

The talent config must list every supported Miniwyvern role. Each role must
resolve to that same config so a role swap retains the component's configured
tree and purchased IDs.

HyDragon must not introduce a second talent store, a form-specific XP ledger,
or a custom Java representation of purchased flags.

## 4. Generic flag contract

Every node has a stable, generic talent ID. Form assets consume those IDs via
the Tamework talent sensor. They do not encode a form name in a shared flag.

The primary capability families are:

- `EssenceBond`, `EssenceFocus`, and deeper Bond flags: unlock and amplify the
  active form's owner passive.
- `DraconicProjectile`, `ProjectileRange`, `ProjectileCadence`,
  `ProjectileForce`, and advanced Combat flags: unlock and amplify the active
  form's projectile and advanced attack.
- `VitalScales`, `HardenedScales`, recovery, and survival flags: improve the
  Miniwyvern's own durability across every form.

The Wild form is a first-class consumer of every relevant flag. Its Bond path
provides stamina regeneration and maximum stamina, and its Combat path remains
a straightforward raw-damage projectile with no elemental status effect.

## 5. Tree topology and node budget

All nodes use `RequiresTalentIds` to draw the tree and enforce prerequisites.
Foundational nodes cost one point; deeper improvements cost two or three; each
branch capstone costs four. Minimum levels pace access from early companion
identity to late specialization.

### Bond: 9 nodes, 15 points

| Tier | Node purpose | Cost | Minimum level |
| --- | --- | ---: | ---: |
| 1 | Unlock the current form's baseline passive (`EssenceBond`). | 1 | 2 |
| 2 | First passive-potency improvement. | 1 | 4 |
| 2 | First passive-property improvement. | 1 | 6 |
| 3 | Second passive-potency improvement. | 2 | 9 |
| 3 | Secondary passive option. | 2 | 11 |
| 4 | Passive-efficiency improvement. | 1 | 14 |
| 4 | Secondary-passive improvement. | 1 | 17 |
| 5 | Highest non-capstone passive improvement. | 2 | 20 |
| 6 | Form-specific Bond capstone; requires both deeper Bond paths. | 4 | 26 |

The role assets decide what the unlocked passive property means for that form.
Wild maps these tiers to stamina regeneration and maximum stamina; an elemental
role maps them to the matching elemental owner effect.

### Combat: 11 nodes, 19 points

| Tier | Node purpose | Cost | Minimum level |
| --- | --- | ---: | ---: |
| 1 | Unlock the form's projectile (`DraconicProjectile`). | 1 | 3 |
| 2 | Projectile-range improvement. | 1 | 5 |
| 2 | Projectile-cadence improvement. | 1 | 5 |
| 3 | First projectile-damage improvement. | 2 | 8 |
| 3 | Targeting or delivery improvement. | 1 | 9 |
| 4 | Second projectile-damage improvement. | 2 | 12 |
| 4 | Projectile-pattern improvement. | 2 | 14 |
| 5 | Unlock the form's advanced attack. | 2 | 17 |
| 5 | Advanced-attack utility improvement. | 1 | 18 |
| 5 | Advanced-attack mastery. | 2 | 21 |
| 6 | Combat capstone; requires the advanced-attack paths. | 4 | 27 |

Wild's projectile-pattern and advanced-attack nodes may add only raw projectile
damage, cadence, range, or additional projectiles. They may not add elemental
status effects.

### Vigor: 10 nodes, 17 points

| Tier | Node purpose | Cost | Minimum level |
| --- | --- | ---: | ---: |
| 1 | First `VitalScales` health increase. | 1 | 2 |
| 2 | Second health increase. | 1 | 4 |
| 3 | Third health increase. | 2 | 7 |
| 3 | First damage-resistance improvement. | 1 | 8 |
| 4 | Second damage-resistance improvement. | 2 | 11 |
| 4 | First recovery improvement. | 1 | 12 |
| 5 | Second recovery improvement. | 2 | 15 |
| 5 | First companion-survival utility improvement. | 1 | 17 |
| 5 | Second companion-survival utility improvement. | 2 | 20 |
| 6 | Vigor capstone. | 4 | 25 |

The three health nodes initially use Tamework's existing
`MaxHealthMultiplier` effect keys: +5%, +5%, and +8%, respectively. Their
composed result is approximately +19% maximum health. The remaining exact
values are balance data in the role and effect assets and may be tuned without
changing this progression contract.

## 6. Talent-aware NPC asset wiring

Tamework gains a reusable self-targeting sensor:

```json
{
  "Type": "TameworkHasTalent",
  "TalentId": "DraconicProjectile"
}
```

The sensor reads the NPC's `TameworkTalentsComponent` and returns true only
when the supplied purchased talent ID is present. It checks the NPC itself; it
does not inspect the owner or an interaction target. Asset authors combine it
with the native `And`, `Or`, and `Not` sensors.

Every talent-gated role instruction must have a complete execution path:

1. a `TameworkHasTalent` sensor gates the instruction;
2. the instruction invokes the correct root interaction or effect asset; and
3. mutually exclusive variants use `Not` around higher-tier flags so a base
   and upgraded passive or attack cannot both run.

Projectile launches, damage calculations, status effects, cadence, targeting,
and presentation belong in the NPC/root-interaction/entity-effect assets.
Existing Miniwyvern Java attack scheduling must be retired as an equivalent
asset path becomes available. The current release retains the Java owner-passive
application path (including Void effects) because applying an entity effect to
the NPC's marked owner target lacks a source-backed asset action contract. That
owner-target primitive is deferred; it must not be guessed in role JSON.

## 7. Experience and point progression

Miniwyverns use a level-30 Tamework leveling config with one talent point per
level after level 1.

### Combat XP

Combat XP is enabled for both final damage dealt and final damage taken.
Damage-taken XP is deliberately tuned lower than damage-dealt XP. Player and
same-owner ally damage remain excluded. Only real final damage events award XP.

### Active-summon XP

Tamework gains a reusable `Summoned` XP source in `TwLevelingConfig` with:

- `Enabled`
- `XpPerActiveSecond`
- `AwardIntervalSeconds`
- `MaxXpPerHour`

It awards only while the bonded companion has a live active projection. It
stops immediately when that projection is dismissed, stored, dead, or no
longer valid. The hourly cap prevents an unattended summoned companion from
becoming an unlimited AFK XP source. Values are balancing data in the
Miniwyvern leveling asset.

Tamework `main` already contains the separate Avatar Flight XP implementation
and its `Flight` config source. The implementation work must begin from a
Tamework revision that includes that merged feature; the unrelated, currently
checked-out `feat/capture-alert` worktree is not that authority.

## 8. Reset policy

The existing Tamework reset action refunds all points with no item, currency,
cooldown, or penalty. Resetting recomputes the current role's asset-gated
behavior immediately and removes any no-longer-authorized effects before
applying the remaining valid ones.

## 9. Verification

Automated and in-game checks must prove that:

1. all Miniwyvern roles resolve to the same leveling and talent config;
2. a purchased flag survives every supported role swap and bonded lifecycle
   transition;
3. `TameworkHasTalent` is false for missing IDs and true only for IDs purchased
   by the NPC itself;
4. every visible gated instruction has an executable asset action path;
5. base and upgraded variants never stack when only the stronger variant should
   be active;
6. Wild receives stamina passives and raw projectiles only;
7. elemental forms interpret the shared flags through their own declared
   assets;
8. combat XP awards damage dealt and lower damage taken XP only for eligible
   encounters;
9. active-summon XP stops on every inactive lifecycle transition and never
   exceeds its hourly cap;
10. a free reset refunds all points and immediately removes disabled behavior;
11. the legacy HyDragon Java Miniwyvern ability runtime has no remaining
   gameplay authority once equivalent asset paths exist; and
12. affected NPC assets validate against the exact Hytale/Tamework profile.

## 10. Non-goals

- A separate talent tree per elemental form.
- Form-specific persistent talent data.
- Paid, delayed, or limited respecs.
- Uncapped passive XP while a Miniwyvern is summoned.
- Reintroducing a HyDragon-owned talent or ability scheduler when an
  asset-driven or generic Tamework solution exists.
