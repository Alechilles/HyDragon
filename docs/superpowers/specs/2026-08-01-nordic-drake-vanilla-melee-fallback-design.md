# Nordic Drake Vanilla Melee Fallback Design

## Goal

Make a grounded, tamed Nordic Drake visibly use its existing chained swipe-left,
swipe-right, and stomp attacks between its bite and flame-breath specials while
continuing to attack the owner's target or defend the owner.

## Corrected Engine Model

The previous missing-component diagnosis was false. In Hytale 0.5.7,
`RoleSystems.RoleActivateSystem#onEntityAdd` supplies
`ChainingInteraction.Data` to every NPC. Vanilla Grizzly Bears inherit ordinary
JSON combat instructions from `Template_Predator` and execute
`Root_NPC_Bear_Grizzly_Attack`, a `Chaining` swipe/stomp root, through the normal
`ActionAttack` path without Combat Action Evaluator involvement.

Therefore HyDragon must not add a Java system for chaining data. Commit
`55b8d4a`'s `NordicDrakeChainingDataSystem`, its plugin registration, and its
unit test are redundant and will be removed.

## Grounded Attack Design

Retain the current target ownership, grounded/flying selection, head aiming,
distance maintenance, and special-attack definitions. Replace only the grounded
attack scheduler:

1. Bite and flame breath remain ordered, cooldown-gated special branches.
2. When both specials are ready, the existing weighted special selector chooses
   one. The direct special branches allow the other special to run while its
   counterpart is cooling down.
3. Place the basic melee branch after those special branches as the guaranteed
   fallback.
4. The fallback calls `Root_NPC_NordicDrake_Attack` directly with
   `ActionsBlocking: true`, the existing aim motion, a short vanilla-style
   pre/post delay, and `ActionAttack.AttackPauseRange` of 1.5-2.5 seconds.
5. Remove the separate `NordicDrake_Ground_Basic` timer, its stopped sensor, and
   its timer-start/restart actions. Rename the associated parameter to
   `GroundBasicAttackPauseRange` so its meaning matches the engine field.

This follows the working `Template_Predator` lifecycle for chained basic attacks
and the vanilla ordered-sibling pattern for special-versus-basic priority. It
also removes the custom timing layer that is unique to the failing basic branch.

## Non-goals

- Do not change the chained root or its swipe/stomp child interactions.
- Do not change bite, flame-breath, fireball, or aerial behavior.
- Do not add Java attack selection or interaction execution.
- Do not change owner targeting, defending, head look, or movement distances.

## Test Contract

The existing Nordic Drake combat asset test will be changed first and must fail
against the current assets. It will require:

- no `NordicDrake_Ground_Basic` timer sensor or timer actions;
- the basic branch to be ordered after the special branches;
- the basic `Attack` action to target `GroundBasicAttack` and carry
  `AttackPauseRange: { "Compute": "GroundBasicAttackPauseRange" }`;
- grounded state, target range, line of sight, walk controller, blocking actions,
  and head aim to remain present;
- the redundant Java system, plugin registration, and Java test to be absent.

After the asset change, run focused tests, exact-profile affected-scope NPC asset
validation, the repository asset validator, and the full Maven verification.

## Live Acceptance

Install an artifact built from the exact merged commit only while Hytale is
closed. After a full restart, a grounded tamed Nordic Drake defending/attacking
a target in melee range must visibly cycle through the chained swipe/stomp root
between occasional bite and flame-breath specials. Confirm that it continues to
aim its head at the target and maintains the configured 3.5-5 block range.

Static tests cannot prove the visible animation sequence, so live observation
remains a required acceptance check.
