# MiniWyvern Dedicated Projectile Aim Phase Design

## Goal

Make an airborne MiniWyvern visibly stop, turn toward its locked target, fire an accurate talent projectile, and then resume its existing wandering combat flight. Make the airborne Hold command actually hover. Preserve the current projectile variants, cadence, orbit character, and dive/bite behavior.

This design supersedes the motion-order assumptions in `2026-07-30-miniwyvern-pause-and-aim-design.md`.

## Confirmed Root Causes

Hytale 0.5.7's `BodyMotionNothing` clears desired steering, but `MotionControllerFly.computeMove` clamps that zero steering to at least `MinAirSpeed`. The MiniWyvern Fly controller currently specifies `MinAirSpeed: 3`, so it is forced to keep translating in both Hold and the projectile instruction. Vanilla `Template_Eye` demonstrates true native-flight pauses with `MinAirSpeed: 0` and a timed `BodyMotion: Nothing`.

Hytale also accepts only the first matching body-motion instruction and the first matching head-motion instruction in a behavior tick. The MiniWyvern's aerial Defend component is evaluated before the talent projectile instructions, so its orbit/seek body motion and `Watch` head motion win. The later projectile action can still execute, but the later `Nothing` and `Aim` motions are ignored.

## Selected Design

### Hover-capable Fly controller

Change only the tamed MiniWyvern's native Fly controller:

- `MinAirSpeed: 0`, allowing cleared steering to decelerate all the way to a stationary hover.
- `Deceleration: 12`, matching the established HyDragon flight tuning and stopping the normal 0.28-relative-speed combat loiter in roughly 0.28 seconds. Acceleration and all other flight limits remain unchanged.

Grounded Hold continues to use `BodyMotion: Nothing`. Airborne Hold also keeps `BodyMotion: Nothing`; with the corrected Fly controller, that instruction now decelerates to zero and holds altitude in the controller's steerable in-air path.

### Explicit projectile phase

Add one shared projectile phase flag and two timers to the tamed MiniWyvern behavior:

- `Miniwyvern_Projectile_Aiming` flag identifies the brief line-up phase.
- `Miniwyvern_Projectile_Aim` timer runs for 0.4–0.7 seconds.
- `Miniwyvern_Projectile_Cooldown` timer preserves the selected talent variant's existing cadence range.

The instructions that own projectile-phase body and head motion are ordered before the ordinary Idle/Follow/Defend/Hold movement selector. This is required by Hytale's first-match-wins motion arbitration.

When the MiniWyvern is in Defend, has a locked target, owns `DraconicProjectile`, is not already aiming, and its projectile cooldown is stopped, a scheduler instruction sets the aiming flag and starts or restarts the aim timer.

While the aiming flag is set, the priority instruction uses:

- `BodyMotion: { "Type": "MatchLook" }` so the body follows the aimed head while translation decelerates to zero.
- `HeadMotion: { "Type": "Aim", "Spread": 0, "HitProbability": 1, "Deflection": true }` so the locked target and ballistic solution drive orientation.

The phase remains active after the aim timer stops until the selected projectile action completes. This prevents ordinary orbit/seek motion from reclaiming the shot tick.

Each of the 11 mutually exclusive talent variant instructions fires only when the aiming flag is set and the aim timer is stopped. The attack uses a small 0.1–0.2-second engine aiming allowance: an already aligned shot fires immediately, while a marginally misaligned shot gets one final correction instead of firing away from the target. After the attack completes, the instruction clears the aiming flag and starts or restarts the shared cooldown using that variant's existing cadence range. The previous `AttackPauseRange` is moved to this explicit cooldown timer so ordinary orbiting owns the interval between shots rather than the firing stance.

On the following tick, no projectile-phase motion matches, so the existing grounded combat or aerial `TameworkFlyingOrbit`/dive behavior resumes.

### Cancellation and recovery

If the locked target disappears or the NPC leaves Defend while the aiming flag is set, a higher-priority recovery instruction clears the flag, stops the aim timer, and executes `ResetInstructions`. Resetting is required because Hytale blocking action lists retain their current action index when their sensor stops matching; without it, a partially activated projectile action could resume against a later target. The normal state behavior then owns movement in the same tick, and a future Defend target begins a fresh aim phase.

## Alternatives Considered

1. Only set `MinAirSpeed` to zero. This fixes Hold and allows `Nothing` to hover, but it does not fix projectile facing because the earlier aerial Defend motion still wins.
2. Move the existing projectile instructions before aerial Defend. This gives them motion priority, but their talent/target sensors also match throughout the entire attack cooldown, freezing the MiniWyvern instead of restoring orbit flight between shots.
3. Switch to the Walk controller's visual hover system. That controller can rest in place, but switching an actively flying companion to Walk for each shot would couple aiming to landing, collision, animation, and controller-transition behavior.
4. Use an explicit, timer-driven aim phase with native Fly hover (selected). This gives aiming priority only for its intended window and makes cadence, recovery, and motion ownership observable in the asset.

## Scope and Non-goals

- Apply only to `Template_Wyvern_Mini_Flying_Tamed` and its seven tamed MiniWyvern variants.
- Preserve all 11 projectile interaction selections and their current cooldown ranges.
- Preserve the 0.4–0.7-second visible line-up, zero diagnostic spread, target deflection, orbit tuning, rare dive/bite routine, command names, and talent gates.
- Do not change wild MiniWyverns, Nordic Drakes, Rockdrakes, Hydras, or any other species.
- Do not make projectile attacks perfectly accurate against obstruction or rapidly changing target motion.

## Verification

Update `MiniwyvernTalentAssetWiringTest` before the asset change so it fails unless:

- the native Fly controller permits zero airspeed and has deterministic deceleration;
- projectile scheduling, priority motion, firing, cooldown, and cancellation are ordered ahead of ordinary state motion;
- the priority phase uses exact `MatchLook` and deterministic `Aim` settings;
- all 11 variants fire only after the aim timer stops, clear the phase, and preserve their prior cadence through the cooldown timer;
- recovery clears the phase, stops its timer, and resets any partially active blocking projectile action;
- Hold remains `Nothing` in both movement modes; and
- no wild or non-MiniWyvern role is modified.

Then run focused MiniWyvern and locomotion contracts, exact-profile NPC candidate validation and static verification, and the full Maven suite. Build and install an exact committed revision into both Hytale mod directories and confirm matching hashes plus the packaged JSON contract.

Runtime acceptance requires:

- airborne Hold decelerates to a stationary altitude hold;
- between projectile shots, the MiniWyvern retains its slow wandering orbit;
- for each shot, it stops, turns its body toward the target for 0.4–0.7 seconds, fires, and resumes flight; and
- target or command loss cancels the line-up without leaving the companion frozen.
