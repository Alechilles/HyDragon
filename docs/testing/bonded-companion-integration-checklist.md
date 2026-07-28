# Bonded Companion Fresh-World Integration Checklist

Status: Pending freshly packaged Tamework/HyDragon install and user-owned live pass
Scope: HyDragon `0.2.1` with Tamework `3.0.0`, fresh worlds only

## Operating rule

Run one bounded manual pass at a time. After installing a requested build, the coding/goal worker must stop or pause before the user tests. It must not remain running while waiting for a result.

Stop at the first unexpected result. Do not keep clicking, relogging, repairing records, or continue into dependent steps unless the failure instructions explicitly request it. Repeated actions can erase the earliest useful evidence.

## Build/install preflight

The implementation handoff must record:

- the exact clean Tamework test/build command and result;
- the exact clean HyDragon test/build command and result;
- absolute paths and SHA-256 hashes for both build-output JARs;
- absolute paths and matching hashes for the two installed runtime JARs;
- manifest versions and matching Tamework dependency ranges;
- confirmation that no second/older Tamework or HyDragon JAR remains in the active Mods target;
- confirmation that the packaged HyDragon JAR contains both bonded policy assets under `Server/Tamework/BondedCompanions/Rosters/` and does not contain the retired HyDragon population-group assets.

Do not perform release preparation or publishing for this checklist.

## Fresh-world setup

1. Create a new world. Do not restore, migrate, or repair a world used by the older command-roster/bonded-vessel builds.
2. Give the tester one `HyDragon_Dragon_Horn`, an Ancient Draconic Stone, a Wyvern Egg, the current full/mini revival materials, elemental essences, and Tamework's Flightmaster's Talisman.
3. Prepare at least two eligible full dragons. Use a normal dragon for the basic loop and a Nordic Drake for the avatar-flight encounter check.
4. Run `/hydragon status`. The required feature gates should be available and should not name a bonded-database blocker.
5. Run `/tw debugdb status`. Bonded readiness should be mutation-ready. Do not use generic replacement-persistence readiness as the bonded action gate.

## Failure evidence protocol

For any failed step:

1. Stop immediately after the first failure.
2. Capture a screenshot of the card/action/message and note the exact world, companion role, item tier, and action.
3. Run `/hydragon status` and capture all output.
4. Run `/tw debugdb status` and `/tw debugdb detail`.
5. Run `/tw debugdb export` once. Record the full bundle path printed in chat.
6. Preserve the relevant server-log window from before the action through export completion.
7. Report whether a source NPC, projection, item, profile card, state label, cost, timer, or cooldown changed despite the failure.

The export must remain bounded/redacted. Do not attach raw SQLite databases or manually edit the world data.

## Acceptance sequence

### 1. Capture a tranquilized full dragon

Action:

1. Hold the Dragon Horn in an accessible inventory location.
2. Apply `Tw_Status_Tranquilized` to an eligible wild full dragon.
3. Channel an Ancient Draconic Stone through completion once.

Expected visible result:

- the channel beam/aura/motes and dark-magic channel sound run during the channel;
- exactly one completion burst occurs (no second full-duration channel replay);
- one Ancient Stone is consumed;
- the source NPC is removed only after success;
- one `STORED` Horn card appears;
- the card immediately shows resolved name/species, gender, health, and the normal detailed/action presentation rather than only name/health or a raw role ID;
- no live captured projection remains and no generic persistence-evidence message appears.

Failure-specific evidence: note whether the channel sound started/stopped with the channel, whether the stone was consumed, whether the source remained, whether the card appeared, and whether the burst ran zero/one/multiple times.

### 2. Summon from the Horn

Action: Select the stored profile and press Summon once.

Expected visible result:

- the same card becomes `ACTIVE` immediately with complete details and correct buttons;
- exactly one live projection appears at safe placement;
- normal supported commands act on that projection;
- no `NPC is not linked to this tool`, replacement-persistence evidence, or relog-to-refresh error appears;
- reopening the Horn does not create another projection or lose card fields.

Failure-specific evidence: count visible copies, record the state label/button set before and after, and do not retry Summon if a projection appeared despite an error.

### 3. Dismiss/store

Action: Change an observable value when practical (for example health), then press Dismiss/store once.

Expected visible result:

- the exact projection disappears;
- the same card becomes `STORED` immediately;
- the complete snapshot, including the changed value, remains visible;
- the configured cooldown is shown/applied;
- no live copy remains.

Failure-specific evidence: note whether the card state, projection, snapshot value, and cooldown changed independently of one another.

### 4. Exercise full-dragon capacity and cooldown

Preparation: Capture a second eligible full dragon with the Step 1 flow so two stored full-dragon cards exist.

Action:

1. While the first profile is stored/cooling down, summon the other stored full dragon.
2. With that profile active, attempt to summon the first only when its own cooldown permits.
3. Dismiss the active profile, wait for the desired profile's displayed cooldown if necessary, then summon that profile.

Expected visible result:

- one full-dragon active slot is enforced for family `hydragon:full_dragons`;
- a capacity denial leaves both profiles/cards intact and creates no second projection;
- cooldown is per profile and matches the policy presentation;
- once capacity and cooldown permit, the different stored dragon summons normally.

Failure-specific evidence: record both card states, both cooldowns, active count/limit, and projection count.

### 5. Confirm death and paid revive

Action:

1. Kill the currently active bonded full dragon through a normal confirmed-death path.
2. Open its Horn card and inspect the revive quote.
3. Ensure the inventory contains `Revitalizing_Essence x2` and `Draconic_Essence x4`, then confirm Revive once.
4. After revive, press Summon once when policy permits.

Expected visible result:

- the card becomes `DEAD`, not `STORED`, `LOST`, or `UNLOADED`;
- the quote shows both required item components and quantities;
- missing either component would disable/deny payment without consuming the other;
- successful revive consumes both components once and changes the same card to `STORED`;
- revive does not create a live NPC or start a session;
- the later explicit Summon creates exactly one projection and changes the card to `ACTIVE`.

Failure-specific evidence: record exact inventory counts before/after, card revision/state, projection count, and whether revive partially consumed a recipe.

### 6. Test finite and disabled session policies

This is two bounded subpasses.

Finite production policy:

1. Summon a full dragon under the shipped 600-second session policy (or a Miniwyvern under the 900-second policy).
2. Do not dismiss it; wait through expiry.

Expected: one expiry stores the projection, leaves the full snapshot intact, releases the family active slot, and applies the configured cooldown. It never becomes lost/unloaded/dead.

Zero-duration test policy:

1. Use a separately identified test build whose one selected bonded family has `SessionDurationSeconds: 0`; do not silently alter the production artifact.
2. Summon and remain active longer than the finite test comparison window.

Expected: the profile remains `ACTIVE` until a real non-death store event; `0` never expires. If `SummonCooldownSeconds` is also `0`, storage permits immediate policy re-admission without a cooldown.

Failure-specific evidence: record the exact policy artifact/hash, signed timer/cooldown values shown by diagnostics, timestamps, and state transitions.

### 7. Logout and transfer worlds with an active projection

Action:

1. Summon a bonded companion and leave/rejoin the world/session.
2. Summon again when policy permits, then transfer to another real world.
3. Inspect the Horn after each boundary and return to the source world to confirm no copy remains.

Expected visible result:

- each non-death boundary converges to `STORED`;
- no `LOST` or `UNLOADED` state appears;
- no frozen/stuck projection follows incorrectly or remains in the old world;
- the complete card persists and can later summon normally.

Failure-specific evidence: record source/destination world keys, state before/after each boundary, and every visible copy/location.

### 8. Soul-bond and cycle a Miniwyvern

Action:

1. Use one Wyvern Egg once.
2. Open the same Dragon Horn used for full dragons.
3. Summon the new stored Miniwyvern.
4. Use a configured elemental essence's secondary interaction to attune the active Miniwyvern.
5. Exercise the relevant ability long enough to establish observable state, then dismiss/store, summon, and relog.
6. Confirm its death/revive loop with `Revitalizing_Essence x1` and `Draconic_Essence x2`.

Expected visible result:

- one Egg is consumed and one detailed `STORED` Miniwyvern card appears in the shared Horn;
- a second Egg claim is denied without consumption;
- the Miniwyvern uses its independent one-owned/one-active family policy;
- attunement, ability/progression state, appearance, and card details survive store/summon/relog;
- any non-death absence becomes `STORED`;
- confirmed death becomes `DEAD`; revive consumes both mini recipe components and returns to `STORED` without auto-summoning.

Failure-specific evidence: record Egg/essence counts, profile card state/details, selected archetype, ability behavior before/after cycling, and extension-related status reason.

### 9. Verify active full-dragon encounter eligibility

Positive control:

1. Summon a stored Nordic Drake so it has a confirmed `ACTIVE` full-dragon lease.
2. Carry `Tamework_Flightmasters_Talisman`.
3. Enter the configured Zone 3 glacial/mountain environment between Y 180 and 320 during heavy snow/storm conditions and allow the encounter evaluation interval.

Expected: eligibility can succeed only for that active avatar-flight full-dragon profile in the candidate world.

Negative controls:

- store the Nordic Drake and repeat: stored profile does not qualify;
- use a dead full-dragon profile: dead does not qualify;
- leave only an active Miniwyvern: wrong family does not qualify;
- use an active non-avatar-flight full dragon: wrong mount mode does not qualify;
- remove Talisman access: required item check denies;
- use an active lease in another world: wrong-world lease does not qualify.

The encounter is probabilistic and cooldown-controlled. Distinguish a denied eligibility reason from a valid admission that simply did not win the configured chance.

Failure-specific evidence: record `/hydragon status`, environment/weather/Y, Talisman access, active card family/role/world, encounter cooldown, and server-log admission reason.

### 10. Verify bounded diagnostics

Action:

```text
/tw debugdb status
/tw debugdb detail
/tw debugdb export
```

Expected visible result:

- bonded readiness and aggregate counts are reported independently of generic replacement-persistence readiness;
- detail includes bounded bonded profile/state/operation information without raw snapshots;
- export creates a bounded bundle and prints its exact path;
- the bundle includes a redacted bonded entry but no player UUIDs, profile IDs, full snapshot JSON, extension payloads, or raw database contents.

Also run `/hydragon status` and verify that no valid current Soul Bond is reported as a missing legacy local profile projection.

Failure-specific evidence: preserve the command output, printed bundle path, bundle entry names/sizes, and the server-log window. Do not share raw world databases.

## Symptom routing

| Symptom | Primary subsystem to inspect |
| --- | --- |
| Channel has no presentation or completion presentation repeats | HyDragon item interaction/effects assets and Tamework capture feedback dispatch |
| Stone/source/card disagree after capture | Tamework durable-before-removal capture transaction and exact capture evidence |
| Card has name/health only until relog | Tamework bonded profile-first panel source/presentation refresh |
| `NPC is not linked to this tool` | Bonded profile-keyed action routing or stale generic command routing |
| Generic persistence-evidence readiness message | Capability/readiness boundary; bonded action incorrectly reached replacement persistence |
| `LOST`/`UNLOADED` after non-death disappearance | Bonded disappearance classification or generic lifecycle leakage |
| Projection exists but card says stored/dead | Lease/projection marker and transition reconciliation |
| Duplicate live NPCs | Exact lease token marker, summon idempotency, or duplicate cleanup |
| Revive consumes partially or summons automatically | Bonded multi-item escrow/transition service |
| Miniwyvern data resets | `Alechilles:HyDragon` extension codec/CAS/event lifecycle |
| Encounter accepts stored/dead/mini/wrong-world profile | `ActiveBondedDragonResolver` / encounter eligibility |
| `/hydragon status` reports missing local profile after a valid claim | Superseded HyDragon local profile diagnostic still participating |

## Completion record

Record each step as `PASS`, `FAIL`, or `NOT RUN`, with world name, installed JAR hashes, and timestamp. The bonded implementation is not accepted until all ten steps pass on the intended packaged pair. After any install or requested manual pass, pause the implementation goal and wait visibly for the tester's report.
