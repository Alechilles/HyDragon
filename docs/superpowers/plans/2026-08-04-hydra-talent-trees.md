# Hydra Talent Trees Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the shared Hydra talent tree with a 22-node Toxic Hydra tree that retains Avatar Flight talents and a 16-node Ice Hydra tree with no flight talents, fully translated in every supported language.

**Architecture:** Keep the change declarative and role-scoped. Two independent `TwTalentConfig` assets reuse the Nordic Drake's approved progression values, while one focused Java contract test parses the real JSON and localization catalogs to protect role isolation, node graphs, effects, and translation completeness.

**Tech Stack:** Tamework JSON assets, Hytale `.lang` catalogs, Java 25, Gson, JUnit 5, Gradle, HytaleNpcAssetTools release-`0.5.7` profile.

## Global Constraints

- `Tamed_Hydra_Toxic` receives exactly 22 Toxic-themed nodes, including the six-node Avatar Flight branch.
- `Tamed_Hydra` receives exactly 16 Ice-themed nodes and no `AvatarFlight*` effect.
- Levels, tiers, costs, prerequisites, icons, effect keys, and multipliers mirror the corresponding Nordic Drake nodes.
- Both configs use `Enabled: true`, `Priority: 100`, `AllocationRevision: 1`, and exactly one role ID.
- Translate every branch, name, and description into `en-US`, `de-DE`, `es-ES`, `fr-FR`, and `pt-BR`; do not use English fallback text in non-English catalogs.
- Do not change leveling, NPC roles, combat actions, Avatar Flight configuration, projectiles, mounts, models, animation, or VFX.
- Preserve unrelated working-tree edits and the untracked `README.md`.

## Batch Contract

- Project profile: `.asset-tools/project-profile.json`, profile `release-0.5.7`, identity `e97773a1d98a0b23f8ffd88406df93c65c48eadcf61864006917a5a91e3513dd`.
- Knowledge: absent; empty knowledge hash.
- Immutable graph snapshot: `8aa7c9537c188642ef85ddf830f68c624d7dbb84fb816e3058c02ab769bf1782`.
- Representative species: `Tamed_Hydra_Toxic`; outlier: `Tamed_Hydra`.
- Archetype: none; the approved Nordic tree and current Tamework `TwTalentConfig` source are the structural references.
- Include: the two Hydra talent configs, five locale catalogs, focused Hydra progression test, and packaged-asset assertion.
- Exclude: all leveling configs, NPC roles/templates, combat/projectile assets, models, textures, and Tamework source.
- Batch size: two role-scoped configs.
- Advisory policy: deterministic schema/reference failures block; inherited defaults and catch-all review heuristics remain unchanged because no NPC role is edited.
- Rollback: revert the feature commits; no migration or external state mutation is involved.

---

### Task 1: Role-scoped Hydra talent assets

**Files:**
- Create: `src/test/java/com/alechilles/hydragon/config/HydraTalentProgressionAssetTest.java`
- Modify: `Server/Tamework/Talents/HyDragonHydra.json`
- Create: `Server/Tamework/Talents/HyDragonToxicHydra.json`

**Interfaces:**
- Consumes: Tamework `TwTalentConfig` JSON shape and existing passive effect keys.
- Produces: `IceHydra_*` and `ToxicHydra_*` talent graphs referenced by localization and packaging verification.

- [ ] **Step 1: Write the failing role/tree contract test**

Create `HydraTalentProgressionAssetTest` with these literal expectations:

```java
private static final Path ICE = Path.of("Server", "Tamework", "Talents", "HyDragonHydra.json");
private static final Path TOXIC = Path.of("Server", "Tamework", "Talents", "HyDragonToxicHydra.json");

private static final List<String> ICE_IDS = List.of(
        "IceHydra_FrostDiscipline", "IceHydra_FrozenCore", "IceHydra_RimeboundMomentum",
        "IceHydra_ShatteringBreath", "IceHydra_IcecladTalons", "IceHydra_ThreefoldWinter",
        "IceHydra_GlacialHide", "IceHydra_PermafrostScales", "IceHydra_LongHibernation",
        "IceHydra_UnyieldingWinter", "IceHydra_Frostscar", "IceHydra_FrozenBulwark",
        "IceHydra_FrostboundPact", "IceHydra_SwiftRecall", "IceHydra_EndlessWinter",
        "IceHydra_BroodmastersCall");

private static final List<String> TOXIC_IDS = List.of(
        "ToxicHydra_VirulentLift", "ToxicHydra_CausticCurrent", "ToxicHydra_Venomwake",
        "ToxicHydra_MiasmaMantle", "ToxicHydra_BlightSurge", "ToxicHydra_PlagueSovereign",
        "ToxicHydra_VenomDiscipline", "ToxicHydra_CausticHeart", "ToxicHydra_ToxicMomentum",
        "ToxicHydra_RuinousMiasma", "ToxicHydra_BarbedTalons", "ToxicHydra_ThreefoldBlight",
        "ToxicHydra_BogscaleHide", "ToxicHydra_AcidHardenedScales", "ToxicHydra_PatientStalker",
        "ToxicHydra_DeathlessBrood", "ToxicHydra_Blightscar", "ToxicHydra_PestilentBulwark",
        "ToxicHydra_BlightboundPact", "ToxicHydra_SwiftRecall",
        "ToxicHydra_LingeringPresence", "ToxicHydra_BroodmastersCall");
```

The test must parse both real assets and assert:

```java
assertConfig(ice, List.of("Tamed_Hydra"), 16, ICE_IDS);
assertConfig(toxic, List.of("Tamed_Hydra_Toxic"), 22, TOXIC_IDS);
assertTrue(effectKeys(toxic).containsAll(Set.of(
        "AvatarFlightVigourCapacityMultiplier",
        "AvatarFlightVigourRechargeRateMultiplier",
        "AvatarFlightForwardBoostCostMultiplier",
        "AvatarFlightForwardBoostImpulseMultiplier",
        "AvatarFlightGlideSinkMultiplier",
        "AvatarFlightClimbLiftMultiplier")));
assertTrue(effectKeys(ice).stream().noneMatch(key -> key.startsWith("AvatarFlight")));
```

Define literal `TalentExpectation` maps for every ID using the exact branch,
cost, level, prerequisites, and multipliers from the approved design. Use
`1.0 / 0.97`, `1.0 / 0.96`, and `1.0 / 0.94` as the hand-derived toughness
values and compare doubles with epsilon `0.000_001`.

- [ ] **Step 2: Run the focused test and verify the expected failure**

Run:

```bash
bash ./gradlew test --tests com.alechilles.hydragon.config.HydraTalentProgressionAssetTest
```

Expected: FAIL because `HyDragonToxicHydra.json` does not exist and the shared
Hydra config does not match the Ice-only contract.

- [ ] **Step 3: Implement the two minimal talent configs**

Rewrite `HyDragonHydra.json` with the 16 Ice rows from the design and create
`HyDragonToxicHydra.json` with its 22 Toxic rows. Every row uses this exact
shape, omitting `RequiresTalentIds` only for roots:

```json
{
  "Id": "IceHydra_FrostDiscipline",
  "DisplayName": "hydragon.talents.ice_hydra.frost_discipline.name",
  "Description": "hydragon.talents.ice_hydra.frost_discipline.description",
  "IconPath": "Tamework/LinkedPanelIcons/Trait_Strength.png",
  "Tier": 1,
  "Branch": "hydragon.talents.ice_hydra.branch.winters_wrath",
  "PointCost": 2,
  "MinLevel": 1,
  "Effects": [{"EffectKey": "DamageDealtMultiplier", "Multiplier": 1.02}]
}
```

Use tiers `1,2,2,3,3,4` for each six-node split branch and `1,2,3,4` for each
summon branch. Use semantic icons: strength for damage and offensive flight,
health for health, toughness for damage reduction/glide defense, and
swiftness for Vigour/recharge/summon timing.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit the role-scoped trees and contract**

```bash
git add Server/Tamework/Talents/HyDragonHydra.json \
  Server/Tamework/Talents/HyDragonToxicHydra.json \
  src/test/java/com/alechilles/hydragon/config/HydraTalentProgressionAssetTest.java
git commit -m "Feat: add themed Hydra talent trees"
```

### Task 2: Complete five-language localization

**Files:**
- Modify: `src/test/java/com/alechilles/hydragon/config/HydraTalentProgressionAssetTest.java`
- Modify: `Server/Languages/en-US/server.lang`
- Modify: `Server/Languages/de-DE/server.lang`
- Modify: `Server/Languages/es-ES/server.lang`
- Modify: `Server/Languages/fr-FR/server.lang`
- Modify: `Server/Languages/pt-BR/server.lang`

**Interfaces:**
- Consumes: every `Branch`, `DisplayName`, and `Description` key emitted by Task 1.
- Produces: complete player-facing translations in all supported catalogs.

- [ ] **Step 1: Add the failing localization completeness test**

Add a test that gathers the real `Branch`, `DisplayName`, and `Description`
values from both configs, asserts the required key count is `83` (`48` Toxic
plus `35` Ice), and requires every key exactly once in each supported catalog:

```java
private static final List<String> LOCALES =
        List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR");

assertEquals(83, requiredKeys.size());
for (String locale : LOCALES) {
    List<String> lines = Files.readAllLines(
            Path.of("Server", "Languages", locale, "server.lang"));
    for (String key : requiredKeys) {
        assertEquals(1L, lines.stream().filter(line -> line.startsWith(key + "=")).count(),
                locale + " must define " + key + " exactly once");
    }
}
```

Also compare `{placeholder}` tokens to English and assert that every
non-English value differs from English for the 38 talent names and seven
branch names. Numeric descriptions may share digits but must be translated.

- [ ] **Step 2: Run the localization test and verify it fails**

Run the focused test command from Task 1. Expected: FAIL because the new Ice
and Toxic localization namespaces do not exist.

- [ ] **Step 3: Replace the legacy shared Hydra localization block**

In each catalog, remove all `hydragon.talents.hydra.*` entries and insert the
83 exact keys referenced by the two configs. English descriptions state the
generic effect accurately; translations preserve the same percentages and
meaning:

```text
hydragon.talents.toxic_hydra.branch.plaguewing=Plaguewing
hydragon.talents.toxic_hydra.virulent_lift.name=Virulent Lift
hydragon.talents.toxic_hydra.virulent_lift.description=Increases maximum mounted flight Vigour by 15%.
hydragon.talents.ice_hydra.branch.winters_wrath=Winter's Wrath
hydragon.talents.ice_hydra.frost_discipline.name=Frost Discipline
hydragon.talents.ice_hydra.frost_discipline.description=Increases all damage dealt by 2%.
```

Use natural German, Spanish, French, and Brazilian Portuguese phrasing. Keep
the decimal conventions already used by each catalog (`3,5%`, `4,5%`, and
`37,5%` outside English). Do not copy English names into another locale.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the focused test command. Expected: PASS.

- [ ] **Step 5: Commit all translations**

```bash
git add src/test/java/com/alechilles/hydragon/config/HydraTalentProgressionAssetTest.java \
  Server/Languages/en-US/server.lang Server/Languages/de-DE/server.lang \
  Server/Languages/es-ES/server.lang Server/Languages/fr-FR/server.lang \
  Server/Languages/pt-BR/server.lang
git commit -m "Feat: translate Hydra talent trees"
```

### Task 3: Packaging and static verification

**Files:**
- Modify: `src/test/java/com/alechilles/hydragon/integration/PackagedHyDragonRosterIT.java`
- Verify only: `scripts/validate_assets.py`

**Interfaces:**
- Consumes: both finalized configs and every supported locale catalog.
- Produces: packaged-JAR and repository-validation evidence.

- [ ] **Step 1: Add packaged asset assertions**

Extend `packagedToxicHydraAssetsAreCompleteAndTextureResolves` to require:

```java
assertNotNull(zip.getEntry("Server/Tamework/Talents/HyDragonHydra.json"));
assertNotNull(zip.getEntry("Server/Tamework/Talents/HyDragonToxicHydra.json"));
for (String locale : List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR")) {
    assertNotNull(zip.getEntry("Server/Languages/" + locale + "/server.lang"));
}
```

- [ ] **Step 2: Run focused unit and packaging verification**

```bash
bash ./gradlew test --tests com.alechilles.hydragon.config.HydraTalentProgressionAssetTest
bash ./gradlew packagingTest --tests com.alechilles.hydragon.integration.PackagedHyDragonRosterIT
```

Expected: both commands PASS.

- [ ] **Step 3: Run repository asset validation and consistency checks**

```bash
python scripts/validate_assets.py
git diff --check
```

Then compare both JSON files for unique IDs, valid prerequisites, exact role
isolation, supported effect keys, and Ice's complete absence of
`AvatarFlight*`. Expected: validator exits `0` and diff check is clean.

- [ ] **Step 4: Run exact-profile affected-scope checks**

Re-run profile check, then use HytaleNpcAssetTools `author check --changed` on
the materialized source. The profile does not catalog Tamework's custom talent
assets as first-class NPC assets, so report any `unsupported` custom-config
validation distinctly; still require the unchanged `Tamed_Hydra` and
`Tamed_Hydra_Toxic` role inspections to remain finding-free on snapshot
`8aa7c9537c188642ef85ddf830f68c624d7dbb84fb816e3058c02ab769bf1782`
or its refreshed post-edit successor.

- [ ] **Step 5: Commit packaging coverage**

```bash
git add src/test/java/com/alechilles/hydragon/integration/PackagedHyDragonRosterIT.java
git commit -m "Test: verify packaged Hydra talent trees"
```

- [ ] **Step 6: Review final scope**

Confirm `git status --short` shows only pre-existing unrelated changes plus no
uncommitted Hydra talent work. Confirm the commits contain no leveling, NPC
role, combat, model, texture, animation, VFX, or Tamework source changes.
