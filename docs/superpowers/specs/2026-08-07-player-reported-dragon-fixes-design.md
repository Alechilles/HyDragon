# Player-Reported Dragon Fixes Design

## Scope

Address the August 7 player reports for the Toxic Hydra, Rock Drake, Nordic
Drake, and Miniwyvern texture set. Preserve the Fire Miniwyvern contract in
which only its owner applies Burn after purchasing `Miniwyvern_Fire_EmberBond`.

## Toxic Hydra

- Replace the wild Toxic Hydra's ice projectile references with
  `Root_NPC_Hydra_Toxic_Aerial_Spit` in both the role and aerial combat balance
  asset.
- Add the same aerial movement overrides already proven on the Nordic Drake:
  climb `1`, sink `0.8`, wander speed `0.9`, wander radius `18-36`, retarget
  interval `4-8`, recovery speed `1.1`, recovery climb `1.5`, minimum move `8`,
  stop distance `5`, and eight wander tests per tick.
- Replace the Toxic Hydra winged texture with the downloaded `Acid.png`, stored
  under the repo's `Toxic.png` naming convention and referenced by both normal
  and avatar-flight winged model assets.

## Desummon Fade

Tamework currently applies a configured expiry model effect at 30 seconds and
extends it through lease cleanup. Change the generic trigger to the five-second
warning and change HyDragon's VFX animation/status duration to five seconds.
HyDragon is the only checked-out roster that configures this hook.

## Rock Drake

- Introduce `SleepHearingRange` on `Template_Hydra_Intelligent`, defaulting to
  the existing `HearingRange` so other consumers do not change.
- Use the sleep-specific range for sleeping proximity detection and sleeping
  flock alerts. Override it to `2` on all wild Rock Drake tiers.
- Run the existing wake action when Sleep exits into Combat or Search, including
  the damage-driven `Combat.Message` and `Search.Confused` substates. This clears
  the looping sleep animation before pursuit and addresses the associated
  apparent fall/underground presentation without changing the collider.
- Reduce only tamed Rock Drake tier speeds from `5.5` to `3.5`; wild combat
  movement remains unchanged.
- Add `EntityEffect: Immunity_Fire` to `CAE_RockDrake`. Hytale 0.5.7's
  `Lava_Burn` explicitly refuses to apply while `Immunity_Fire` is active, so
  the one effect covers both direct fire and lava burn while preserving CAE.

## Texture Mapping

- Nordic archive: replace `Black`, `Cobalt`, `GreenBalls`, `OldOrange`, and
  `Acid`; leave `Green` and `Red` unchanged because the archive omits them.
- Miniwyvern archive:
  - `Wyvern Normal.png` -> `Miniwyvern_Normal.png`
  - `Wyvern Texture ByYasmim.png` -> `Miniwyvern_Nature.png`
  - `WyverToxic.png` -> `Miniwyvern_Toxic.png`
  - `WyvernIgneo.png` -> `Miniwyvern_Fire.png`
  - `WyvernVoid.png` -> `Miniwyvern_Void.png`
  - `WyverThunder.png` -> `Miniwyvern_Lightning.png`
  - `Wyvern Ground.png` -> `Miniwyvern_Ground.png`
- Leave Ice unchanged. Ignore `WyverNature.png`, `WyverStorm.png`, and
  `Wyvern Sakura.png`; they are not the selected Nature artwork or implemented
  HyDragon variants.

## Verification

- Use a focused Tamework schedule regression test with a red-green cycle.
- Do not add source-shape or texture-presence tests. Validate JSON with the
  existing asset validator and exact release-0.5.7 Hytale asset profile.
- Run both repository test suites and HyDragon's build/asset validation.
- Treat live flight, sleep/wake physics, and Fire passive observation as
  gameplay verification gaps unless a user-controlled test server is supplied.

## Repository Contract

- HyDragon branch: `fix/player-reported-dragon-issues`, based on clean
  `origin/main`.
- Tamework branch: `fix/hydragon-expiry-fade`, based on the clean local `main`
  at `23ded8d2` because it contains nine post-v3.0.2 integration commits used by
  the current local development environment.
- Preserve the original HyDragon checkout's uncommitted manifest and Rock Drake
  drop-table edits.
