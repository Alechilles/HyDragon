# Changelog

All notable player-facing changes to HyDragon are documented here.

## 1.0.1 - Dragon Tranquilizer and Spawn Hotfix - Unreleased

### Added

- Embedded Creditor 1.1.0 so `/credits` includes HyDragon and its authors
  without requiring a separate Creditor installation.

### Changed

- Increased Toxic Hydra encounter availability and removed the initial delay
  from dedicated Rock Drake beacon spawns.

### Fixed

- Fixed tranquilized dragons remaining suspended in midair; they now leave
  flight immediately and fall to the ground.
- Fixed tranquilized dragons leaving their sleep animation when hit again,
  including by zero-damage tranquilizer arrows.

## 1.0.0 - Unreleased

### Added

- Added a complete dragon content roster: Hydras, including the Toxic Hydra variant; Nordic Drakes; three Rock Drake tiers; and Miniwyverns.
- Added dragon world spawns and encounters, including volcanic-cave Rock Drakes, forest Nordic Drakes, volcanic Dark Green Moss Toxic Hydras, and lunar Hydra spawns.
- Added the Draconic Stone capture loop for eligible full dragons, with capture effects, energy tethers, homing motes, and Dragon Horn roster integration.
- Added the Dragon Horn as the shared companion interface for summoning, dismissing, commanding, and reviving captured dragons and Soul Bond Miniwyverns.
- Added an Aggressive Dragon Horn command for full dragons and Miniwyverns,
  including autonomous ground and aerial engagement of nearby hostile mobs.
- Added a confirmation warning before the Dragon Horn dismisses an active
  dragon.
- Added the once-per-player Wyvern Egg Soul Bond: it creates a persistent Miniwyvern companion that can be summoned and managed through the Dragon Horn.
- Added a six-form Miniwyvern attunement system: Fire, Ice, Lightning, Nature, Toxic, and Void. Each form has its own appearance, combat identity, talent tree, and Essence Bond effects.
- Added Miniwyvern talent progression, elemental projectile and swoop combat routes, owner auras, wards, debuffs, impact effects, and combat audio.
- Added dragon progression content, including Nordic Drake flight talents, Rock Drake progression, dragon stat growth, and summon-duration talents.
- Added tameable dragon mounts, Dragon Horn flight-mode controls, Nordic Drake Avatar Flight, Winged Toxic Hydra Avatar Flight, and mounted combat abilities.
- Added distinct combat kits for the dragon roster: Hydra ice and rain attacks, Toxic Hydra poison clouds and aerial attacks, Nordic Drake fireball and flame-breath attacks, and Rock Drake boulder attacks.
- Added the draconic crafting economy: Draconic Stones by tier, Draconic Essences, Revitalizing Essence, Draconic Altar recipes, Dragon Horn crafting, and Dragon-related drops.
- Added localized player-facing text for English, German, Spanish, French, and Brazilian Portuguese.

### Changed

- Finalized dragon companion lifecycle behavior around durable bonded rosters. Captured dragons and Soul Bond Miniwyverns retain their identity through storage, summoning, logout, transfer, and revival.
- Finalized full-dragon capture as a tranquilizer-only process and kept Miniwyverns exclusive to the Soul Bond system.
- Finalized Miniwyvern combat and progression around the current elemental forms; removed superseded attunement, controller-item, and backpack designs from the shipped feature set.
- Refined dragon spawning, follow behavior, cooldowns, active limits, session durations, health growth, drops, recipes, and companion balance for the final release.
- Moved Rock Drakes into volcanic caves, reduced wild Hydra and Nordic Drake
  damage, reduced Toxic Hydra poison stacking, and expanded each Hydra form
  with a themed talent tree.
- Moved Rock Drakes from vanilla cave spawn pools to rare, solitary HyDragon beacons so their encounters no longer consume or alter vanilla beacon population slots.
- Polished mount anchors, rider placement, flight animations, attack timing, hitboxes, sounds, visual effects, and Dragon Stone presentation across the dragon roster.
- Migrated dragon and Miniwyvern following, flying Hold, airborne transitions,
  and favorite-item pursuit to the shared Tamework 3.0 companion components
  while preserving species-specific tuning.
- Autonomous flying dragons now steer around nearby obstacles and return toward
  their owner or combat target when they wander beyond their configured range.
- Updated HyDragon to the Gradle workspace build and required Alec's Tamework `>=3.0.0 <4.0.0` for its bonded-companion and Patchwork integrations.

### Fixed

- Fixed flying dragon and Miniwyvern roles failing Hytale validation after the
  shared Tamework aerial-component migration.
- Fixed Nordic Drake grounded and aerial combat recovery, mounted flight transitions, landing behavior, Avatar Flight visuals, and attack feedback.
- Fixed Toxic Hydra and Hydra attack origins, mounted combat, animations, follow behavior, and ranged-combat presentation.
- Fixed oversized Toxic Hydra flight effects and rebalanced full-dragon revival
  costs across the progression tiers.
- Fixed Rock Drake tier assets, cave spawns, mount anchors, projectile presentation, drops, and targeting behavior.
- Fixed Miniwyvern summoning, flight-mode transitions, projectile aiming and impacts, swoop recovery, elemental aura cleanup, and localized combat guidance.
- Fixed Miniwyvern Aggressive mode failing to enter or remain in aerial combat,
  restored its intended follow speed and Essence Bond buffs, and preserved
  Defend or Aggressive after hard-leash recovery returns it to its owner.
- Fixed hostile mobs overlooking tamed dragons and broadened Aggressive-mode
  targeting to use the intended hostile creature groups.
- Fixed invalid animation and asset wiring that could cause visual, movement, or validation failures.
- Fixed the Toxic Hydra using the standard Hydra drop table; it now rewards Toxic Draconic Essence instead of Ice Draconic Essence.
