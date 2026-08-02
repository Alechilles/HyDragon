# Miniwyvern Form-Specific Talent Trees Design

**Status:** Approved for implementation planning  
**Date:** 2026-08-02

## Purpose

Replace the Miniwyvern's single shared talent tree with role-specific trees so
that every visible tooltip accurately describes the currently summoned form.
An elemental attunement is a deliberate respec: changing the Miniwyvern's
current role refunds its spent talent points and presents the newly selected
form's tree.

This design also adds a reusable Tamework allocation-reconciliation rule. A
companion must never keep a talent allocation that is incompatible with the
tree selected by its current progression role, whether that mismatch came from
a role change or a later tree redesign.

## Scope

- Six elemental Miniwyvern roles receive separate 29-node, 52-point trees:
  Fire, Ice, Lightning, Nature, Toxic, and Void.
- Each elemental tree keeps the existing topology: nine Bond nodes, ten Combat
  nodes, and ten Vigor nodes, with the current tier, point-cost, and minimum
  level pacing.
- Wild receives a separate 20-node, 37-point tree containing only its existing
  ten Combat nodes and the redesigned ten-node Vigor branch. Wild has no Bond
  branch in this release.
- Every tree has its own stable, form-prefixed talent IDs, names, descriptions,
  icon choices, and effect values. Talent IDs may not be reused across form
  trees.
- Projectile and swoop mechanics remain mechanically unchanged. Their form
  trees only give them accurate themed names and exact descriptions of the
  already-wired projectile/root-interaction behavior.

## Tamework allocation reconciliation

`TameworkTalentsComponent` stores the allocation source identity and an
allocation revision in addition to its spent-point total and purchased IDs.
`TwTalentConfig` declares a corresponding allocation revision; authors bump it
only for a deliberately breaking tree redesign.

The reconciliation service resolves the enabled `TwTalentConfig` for the
companion's current effective progression role. It retains an allocation only
when all of the following are true:

1. the stored config identity equals the resolved config identity;
2. the stored allocation revision equals the resolved revision;
3. every purchased talent exists in the resolved tree;
4. every purchased talent's prerequisites are also purchased; and
5. the stored spent-point value equals the sum of the purchased nodes' current
   point costs.

If any condition fails, Tamework atomically replaces the stored config identity
and revision, clears purchased IDs and spent points, and reapplies progression
stat modifiers. Earned levels and their available points remain intact.

Reconciliation runs after a successful role change and during talent component
initialization/restoration. If a transient current role has no enabled talent
config, it must not erase the existing allocation; this protects temporary
mount or presentation roles that are outside progression ownership.

## Bond branch

Bond exists only on elemental forms. Its existing nine-node shape remains:

```text
Essence Bond
├─ Focus → Amplification → Efficiency → Mastery
└─ Attunement → Resonance → Harmony
                   └─ Ascendance (requires both paths)
```

The Focus path is the owner-facing progression of the form's existing passive.
It selects increasing configured values for the current passive, rather than
adding a new trigger type:

- Fire: burn damage and/or duration.
- Ice: movement slow strength and/or duration.
- Lightning: horizontal movement speed.
- Nature: regeneration amount and/or tick cadence.
- Toxic: outgoing-damage weakening strength and/or duration.
- Void: incoming-damage exposure strength and/or duration.

The Attunement path gives the Miniwyvern modest, form-themed benefits using
already supported progression effects: maximum health, outgoing damage,
incoming damage reduction, and movement speed. Ascendance combines the final
owner-passive tier with a modest companion benefit. It must not introduce
on-kill triggers, lifesteal, shields, chained attacks, health-threshold logic,
or other new combat semantics.

HyDragon receives one data-driven Bond-tier resolver. It determines the highest
owned Bond tier for the active elemental form and supplies its configured owner
passive values/effects. This replaces the current one-flag-only
`RequiredTalentId` behavior. Entity-effect assets define the static effect
values, while the resolver is responsible for selecting and refreshing the
correct tier on the owner. Nature's existing timed healing remains a supported
case of that resolver, not a separate new ability type.

## Combat branch

Combat preserves the current ten-node projectile and swoop topology and its
existing asset-driven attacks. Each form's nodes use its own talent IDs, so the
existing `TameworkHasTalent` gates and root-interaction selections are rewritten
to reference the active form's tree.

The content work is names and player-facing accuracy, not new combat behavior:

- projectile nodes state the exact projectile damage, aim delay, cadence,
  projectile pattern, and existing status effect for that form;
- swoop nodes state the exact existing damage/cadence/precision behavior; and
- Wild remains physical-only and applies no elemental projectile status.

## Vigor branch

Every form, including Wild, has a functional ten-node Vigor branch. It uses
only Tamework effects that already have runtime consumers:

- `MaxHealthMultiplier` for scale/health investments;
- `DamageTakenMultiplier` for explicit incoming-damage reduction; and
- `MoveSpeedMultiplier` for companion mobility/endurance investments.

The branch retains its three early health nodes. The former placeholder nodes
become real guard, endurance, and capstone investments using the same three
effects. Exact names are form-themed (for example, fire scales, ice carapace,
or nature bark), while descriptions state the actual percentage granted by that
node. The capstone grants a modest health and damage-reduction combination.

Vigor does not add healing, shielding, revive triggers, damage thresholds, or
other new runtime mechanics in this release.

## Assets and localization

- Replace `HyDragonMiniwyvern.json` with one talent config per Miniwyvern role.
- Keep the existing shared Miniwyvern leveling config; role swaps do not reset
  level, experience, or earned point capacity.
- Update each role's combat instruction and root-interaction gating to use the
  correct form-prefixed combat IDs.
- Add exact English localization for every form-specific talent name and
  description. Existing non-English keys must not claim a talent has no effect;
  they need matching localized text or an intentional safe fallback before
  release.
- Update the Miniwyvern archetype metadata to declare the Bond tier data for
  the appropriate elemental role. Wild declares no Bond passive.

## Verification

Automated tests and asset checks must prove that:

1. every Miniwyvern role resolves to its intended tree and no two form trees
   share talent IDs;
2. attuning from one elemental role to another immediately refunds the complete
   allocation and selects the destination config;
3. a compatible allocation survives ordinary store/summon/relog restoration;
4. a removed node, altered prerequisite, changed point cost, or allocation
   revision mismatch resets the allocation without reducing earned point
   capacity;
5. a role with no talent tree does not erase a previous allocation;
6. Combat talent sensors reference only their form's IDs and still choose the
   existing base, upgraded, pattern, and mastery projectile assets correctly;
7. every Vigor node modifies one or more supported effects and produces an
   observable health, damage-reduction, or movement result; and
8. each elemental Bond tier chooses only its declared existing passive effect
   and leaves all owner effects cleaned up on role swap, reset, storage, and
   death.

## Non-goals

- A shared Miniwyvern talent tree or role-dependent tooltip rendering.
- Any Wild Bond passive or empty Wild Bond placeholders.
- New projectile, swoop, chaining, shield, lifesteal, execution, kill-trigger,
  or health-threshold mechanics.
- An automatic reset merely because a harmless localization, icon, balance
  description, or additive-node update changes; allocation revisions exist for
  intentional breaking redesigns.
