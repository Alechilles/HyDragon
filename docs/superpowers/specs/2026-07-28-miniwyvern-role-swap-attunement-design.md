# Miniwyvern Role-Swap Attunement Design

**Status:** Approved for beta planning  
**Date:** 2026-07-28

## 1. Purpose

Miniwyverns may change form whenever their owner chooses, provided the owner
pays a meaningful, predictable resource cost. The beta implementation uses
Tamework NPC role swaps as the complete form authority. It does not retain a
second Java or extension-document representation of the selected form.

This is a beta balance baseline. Essence availability and exact combat numbers
may change after playtesting without changing the interaction, role, or
persistence model described here.

## 2. Player flow

1. The player summons their Soul Bond Miniwyvern.
2. The player holds at least eight of the essence that represents the desired
   form.
3. The player interacts directly with their Miniwyvern.
4. Tamework validates the interaction, consumes exactly eight matching items,
   and role-swaps the active Miniwyvern to that form.
5. The former role's owner aura and owner-attack infusion end before the new
   role's effects begin.

The interaction lives in a Tamework NPC interaction configuration referenced
by the Miniwyvern NPC template/roles. Elemental essence items do not contain
the transformation interaction themselves.

## 3. Costs and form mapping

All transformations cost eight items. The cost is intentionally uniform for
the beta, even if particular essence sources are later found to be uneven.

| Desired role | Held item | Quantity |
| --- | --- | ---: |
| Wild | `Draconic_Essence` | 8 |
| Venom | `Draconic_Essence_Nature` | 8 |
| Fire | `Draconic_Essence_Fire` | 8 |
| Void | `Draconic_Essence_Void` | 8 |
| Lightning | `Draconic_Essence_Lightning` | 8 |
| Ice | `Draconic_Essence_Ice` | 8 |
| Wind | `Draconic_Essence_Wind` | 8 |
| Water | `Draconic_Essence_Water` | 8 |

Wild is the default, bite-focused baseline form. Venom is the gameplay and
resource successor to the previous Nature concept.

## 4. Source of truth and persistence

Every form is a dedicated bonded Miniwyvern role:

- `Tamed_Wyvern_Mini_Wild`
- `Tamed_Wyvern_Mini_Venom`
- `Tamed_Wyvern_Mini_Fire`
- `Tamed_Wyvern_Mini_Void`
- `Tamed_Wyvern_Mini_Lightning`
- `Tamed_Wyvern_Mini_Ice`
- `Tamed_Wyvern_Mini_Wind`
- `Tamed_Wyvern_Mini_Water`

Each role owns its appearance, combat behavior, and owner aura. The
Miniwyvern bonded roster accepts all eight roles. Tamework's ordinary bonded
snapshot is therefore the only durable record of the selected form and
preserves the role through storage, summoning, relog, death, and revival.

The current custom Java `MiniwyvernAttunementService`, archetype extension
fields, and custom ability scheduler do not participate in the beta
transformation path. They must be retired from that path so no duplicate
attunement authority remains.

No migration is required because the feature has not been released.

## 5. Role gameplay

Exactly one owner aura is active while the owner's Miniwyvern is summoned. It
does not depend on a distance check; storing, confirmed death, or another
despawn ends it. A role swap removes all effects belonging to the prior role
before applying the destination role's aura and attack behavior.

| Role | Miniwyvern combat behavior | Owner aura |
| --- | --- | --- |
| Wild | Bite only | None |
| Venom | Venom projectile | Healing aura |
| Fire | Fire projectile | Owner attacks apply burn |
| Void | Bite plus void projectile | Owner attacks reduce enemy defense |
| Lightning | Lightning projectile | Movement-speed aura |
| Ice | Ice projectile | Owner attacks apply slow |
| Wind | Wind projectile | Jump-height aura |
| Water | Water projectile | Percentage damage reduction |

Projectile damage, projectile cadence, effect duration, aura strength, and
Water's damage-reduction percentage must be role/config balance data. They
are not encoded as persistence or interaction rules.

## 6. Interaction rules

The Tamework configuration must make all eight form entries available for
every Miniwyvern role so a player can change directly between any two forms.
For each entry it must:

1. require the interacting player to own the bonded Miniwyvern;
2. require at least eight of the listed held item;
3. consume exactly eight only after the complete request is valid;
4. perform the destination role swap; and
5. leave inventory and role unchanged when the destination is already active.

The role-swap effect and its item requirement must be confirmed against the
installed Tamework schema before implementation. This design requires the
NPC-side interaction to be the transaction boundary; it must not reintroduce
an item-side custom attunement transaction.

## 7. Verification

Automated and in-game beta checks must establish that:

1. every form accepts only its configured stack of eight items;
2. success consumes exactly eight items once and role-swaps once;
3. insufficient items, a non-owner interaction, and a request for the current
   role consume nothing and leave the NPC unchanged;
4. the old role's aura and owner-attack effects are removed before the new
   role's effects are active;
5. every role exposes its intended projectile/bite behavior and owner aura;
6. Wild reversion consumes eight normal `Draconic_Essence`;
7. storing, summoning, relogging, confirmed death, and revival preserve the
   selected role; and
8. no custom archetype extension or custom attunement runtime is active in the
   transformation path.

## 8. Non-goals

- Equalizing elemental essence acquisition for the beta.
- Altering the once-per-player Soul Bond Miniwyvern entitlement.
- Adding a transformation altar, channel, cooldown, or range gate.
- Adding a second Miniwyvern inventory, profile, or form-persistence store.
