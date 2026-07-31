# MiniWyvern Elemental Projectile Impact Audio Design

## Goal

Give every MiniWyvern elemental projectile one appropriate vanilla impact sound on every resolved collision: entity contact, terrain/block contact, and any physical bounce. Keep the existing Shoot launch sound unchanged and never add a second element sound after damage is confirmed.

## Selected Approach

Use a distinct existing vanilla SoundEvent for each elemental family. Wire that event into all three projectile-physics routes:

- `ProjectileHit` for an entity collision;
- `ProjectileMiss` for a terminal terrain/block impact with no entity target; and
- `ProjectileBounce` for each non-terminal terrain/block collision that bounces and remains in flight.

The Hytale 0.5.7 standard projectile physics selects `ProjectileHit` when a target entity is present and `ProjectileMiss` when a terminal impact has no target entity. A block collision that bounces does not enter `ProjectileMiss`; it enters `ProjectileBounce`. Covering all three routes is therefore required for the selected “every collision” behavior.

Each route plays exactly one element sound from the projectile actor at its collision position. `ProjectileHit` plays the sound on physical entity contact, including a blocked or otherwise non-damaging contact; it is intentionally not attached to `DamageEntity` success. Projectile lifetime expiry and ordinary removal do not play an impact sound because they are not collisions.

## Element Sound Palette

| Element | Vanilla SoundEvent | Rationale |
| --- | --- | --- |
| Fire | `SFX_Staff_Flame_Fireball_Impact` | Compact fireball impact used by vanilla fire projectile content. |
| Ice | `SFX_Ice_Bolt_Death` | Short, crystalline ice-bolt resolution. |
| Lightning | `SFX_Spear_Projectile_Impact` | Tight impact cue; avoid the excessively loud ambient thunder events. |
| Nature | `SFX_Plant_Hit` | Organic plant/wood contact suited to nature shots. |
| Toxic | `SFX_Effect_Poison_World` | World-positioned poison burst rather than a player-local status effect. |
| Void | `SFX_Eye_Void_Attack_Blast` | Existing void-projectile blast cue. |
| Wild | `SFX_Rubble_Hit` | Brief, neutral earth/physical impact; no dedicated Wild event exists. |

The same sound is shared by all base, intermediate, pattern, mastery, guided, cadence, first, and echo variants for that element. Talent progression does not alter impact audio, volume, pitch, collision routing, damage, targeting, or projectile physics.

## Asset Wiring

Retain each existing elemental `ProjectileHit` root and its damage/effect behavior. Add a sound-only branch in parallel with that hit interaction so it is emitted once when the entity collision resolves, without changing damage sequencing. Do not place this sound in `DamageEntity.Next`, `Failed`, or `Blocked`, as that would omit valid collisions or make the audio outcome-dependent.

Replace each projectile configuration’s current remove-only `ProjectileMiss` chain with its element’s collision-sound action followed by the current projectile removal. Add an equivalent element-sound action to `ProjectileBounce` without removing the projectile, so a continuing bounce emits one cue for each additional block contact. All actions must preserve `User` as the projectile sound source; this places the event at the actual impact rather than at the MiniWyvern or target.

No new audio files or SoundEvents are needed. The implementation uses only registry-validated vanilla event IDs, so it neither expands the packaged JAR nor changes the custom non-repeating bite/spit sound pools.

## Alternatives Rejected

- **Entity hits only:** would leave terrain misses silent, contrary to the selected behavior.
- **Per-tier sound palettes:** adds noise and unrelated talent feedback without improving the elemental identity.
- **Collision sound plus a damage-confirmation cue:** can play two cues for a single hit and makes blocked contacts sound inconsistent.

## Validation and Testing

Use a red-green structural test cycle before modifying production assets. Add coverage that verifies:

- every current MiniWyvern projectile configuration has `ProjectileHit`, `ProjectileMiss`, and `ProjectileBounce` impact-audio coverage;
- the configured event for every path matches the owning elemental family and is one of the seven approved IDs;
- `ProjectileMiss` retains projectile removal after its sound action;
- `ProjectileBounce` does not remove the projectile;
- all hit interaction variants, including echoes, emit their sound before damage outcome branches and do not add another elemental sound to `DamageEntity` result branches;
- no MiniWyvern projectile impact path references a custom audio asset or SoundEvent.

Run the exact Hytale 0.5.7 project-profile checks, the repository asset validator, affected NPC/projectile wiring validation where supported, and the Maven verification suite. Package validation must confirm every changed projectile configuration and interaction is included in the JAR.

## Runtime Acceptance

After installing the verified JAR and restarting the game/server:

- an elemental shot hitting an NPC or player plays exactly one matching element sound at contact;
- a shot terminating on ground, walls, or other terrain plays exactly one matching element sound at contact;
- a projectile that bounces plays one matching sound for each bounce and continues normally;
- an expiring projectile that never collides produces no false impact cue;
- fire, ice, lightning, nature, toxic, void, and wild remain audibly distinct;
- existing Shoot-at-launch, damage, effects, echo behavior, cadence, and flight combat remain unchanged.
