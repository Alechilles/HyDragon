# MiniWyvern Attack Feedback Design

## Goal

Give every MiniWyvern bite and projectile launch synchronized animation and audio feedback. Preserve the current eye-height, flight-blending, Bite animation-set, and Shoot animation-set edits in `Wyvern_Mini.json`.

## Selected Approach

Sequence audiovisual NPC actions directly around each existing combat `Attack` action:

- Projectile: `PlayAnimation(Shoot)` -> `Attack` -> `PlaySound(projectile)`
- Swoop bite: `PlayAnimation(Bite)` -> `Attack` -> `PlaySound(bite)`

Hytale 0.5.7 `ActionAttack` remains active during its configured aiming interval and returns only after it queues the interaction chain. Placing Shoot before `Attack` starts the animation during the existing 0.1-0.2 second aim; placing the sound after `Attack` aligns it with the queued projectile. The swoop attacks have no aiming delay, so Bite and its sound execute around the strike in the same update.

The actions use the model's `Action` animation slot. This is the stable model-specific slot accepted by Hytale 0.5.7 `ActionPlayAnimation` and does not replace the MiniWyvern's movement animation.

## Coverage

Apply projectile feedback to every current projectile attack action, including:

- base, intermediate, pattern, and mastery profiles;
- guided and cadence combinations;
- pattern and mastery echo shots.

Apply bite feedback to all four current swoop strike selections:

- default;
- Swoop Ferocity;
- Rending Dive;
- Swoop Mastery.

Every echo replays Shoot and selects another projectile sound. No combat timing, cooldown, damage, targeting, talent arbitration, or movement values change.

## Audio Assets

Convert the seven supplied MP3 files to mono, 48 kHz Ogg Vorbis assets under the MiniWyvern sound namespace:

- four projectile/spit variations;
- three bite variations.

Remove trailing silence using the measured per-clip endpoints: projectile clips at 0.70, 0.67, 0.64, and 0.72 seconds; bite clips at 0.47, 0.47, and 0.37 seconds. Preserve the attack transient, apply a 0.01-second fade ending at each new endpoint to avoid clicks, and retain the original leading edge because no material leading silence was detected.

Define two spatial SoundEvents, one per attack family, inheriting `SFX_Attn_Quiet`. Each event has one layer containing its complete variation pool and `RoundRobinHistorySize: 1`. Hytale selects a random file from the layer while the history setting prevents immediate repetition. Do not add pitch or volume randomization during this pass.

## Asset Wiring

The MiniWyvern aerial defend component references the two new SoundEvent IDs and the existing `Bite` and `Shoot` model animation-set IDs. The SoundEvents reference normalized repository-local `.ogg` paths rather than the original Downloads filenames.

The user-authored `Wyvern_Mini.json` edits remain intact and become part of the implementation commit because the new `Bite` and `Shoot` action references depend on those animation sets.

## Validation and Testing

Add structural contract coverage that verifies:

- the model exposes Bite and Shoot animation sets with their existing clips;
- every projectile `Attack` is immediately preceded by Shoot and followed by the projectile SoundEvent, including echoes;
- every swoop `Attack` is immediately preceded by Bite and followed by the bite SoundEvent;
- the SoundEvents contain exactly four projectile files and three bite files;
- both pools set `RoundRobinHistorySize` to 1;
- every referenced audio file exists and is mono Ogg Vorbis;
- the packaged JAR contains the model, component, SoundEvents, and all seven audio files.

Use a red-green test cycle before changing production assets. Finish with the exact Hytale 0.5.7 project profile checks, affected NPC wiring validation where supported, the repository asset validator, and the full Maven verification suite.

## Runtime Acceptance

After installing the verified JAR and restarting the game/server:

- each bite visibly plays Bite and emits one bite variation;
- each projectile, including an echo, visibly plays Shoot and emits one projectile variation at launch;
- the same variation never plays twice consecutively within its pool;
- clips end tightly without audible trailing silence or cut-off transients;
- existing flight, aiming, swoop recovery, cooldown, and damage behavior remains unchanged.
