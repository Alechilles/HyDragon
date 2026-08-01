# Toxic Hydra Variant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a rare daytime swamp Toxic Hydra that inherits the Ice Hydra's behavior and timings while replacing its skin, melee poison application, projectile presentation, direct-hit status, and rain hazard with toxic variants.

**Architecture:** Keep the current Hydra as the behavioral source. Add four `_InteractionVars` seams to its two ranged choreography graphs and make their defaults small Ice leaf interactions. Define wild and tamed Toxic Hydra roles as variants of their Ice counterparts, overriding those four ranged variables plus the five existing melee damage variables. The toxic model inherits the Ice model, the projectiles copy all Ice numeric behavior, and only element-facing IDs differ.

**Tech Stack:** Hytale JSON assets, Tamework interaction assets, Java 21, Gson, JUnit 5, Maven Wrapper, HytaleNpcAssetTools, Hytale Workshop MCP, image generation/editing workflow.

**Approved design:** `docs/superpowers/specs/2026-07-31-toxic-hydra-variant-design.md`

**Validation baseline:** `ea5c3d36e087162706a1130a83fa7e0ce14b04a6`

## File structure

Create:

- `src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java` — focused contract covering inheritance, choreography, toxic substitutions, parity, spawn, registrations, localization, and texture dimensions/alpha.
- `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Ice_Ball_Charge_Effect.json`
- `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Ice_Ball_Launch.json`
- `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Ice_Charge_Effect.json`
- `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Ice_Launch.json` — Ice defaults extracted from existing choreography.
- `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Charge_Effect.json`
- `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Launch.json`
- `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Charge_Effect.json`
- `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Launch.json` — toxic replacements for the four seams.
- `Server/Models/HyDragon/Hydra/Hydra_Toxic.json` and `Common/NPC/HyDragon/Hydra/Model/Toxic.png` — inherited model and toxic skin.
- `Server/Models/Projectiles/HyDragon/Hydra_Toxic_Ball_Projectile.json` — Acid-based appearance shared by both toxic projectiles.
- `Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json`
- `Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Toxic_Ball.json` — mechanically identical toxic projectile copies.
- `Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra_Toxic.json`
- `Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra_Toxic.json` — inherited roles and complete interaction override maps.
- `Server/NPC/Spawn/World/Zone1/Spawns_Zone1_Swamps_HyDragon_Predator.json` — rare swamp spawn.

Modify:

- `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Ice_Ball.json` — replace repeated elemental blocks with variables without changing sequence or duration.
- `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_RainShoot_Barrage.json` — replace all 20 Ice charge/launch pairs with variables without changing order.
- `Server/HyDragon/DragonSpecies/Hydra.json` — register wild-to-tamed mapping, appearance, and spawn asset.
- `Server/Tamework/CapturePolicies/HyDragonHydra.json`
- `Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json`
- `Server/Tamework/Companion/HyDragonFullDragons.json`
- `Server/Tamework/Breeding/HyDragonBondedCompanions.json`
- `Server/Tamework/Items/Spawners/HyDragonDraconicStone.json`
- `Server/Tamework/Items/Commands/HyDragonDragonHorn.json` — include the new wild/tamed IDs in every Hydra integration surface.
- `Server/Languages/en-US/server.lang`, `de-DE/server.lang`, `es-ES/server.lang`, `fr-FR/server.lang`, and `pt-BR/server.lang` — add wild and bonded names.

## Task 1: Parameterize shared Hydra ranged choreography

**Files:**

- Create: `src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java`
- Create: the four Ice leaf interaction files listed above
- Modify: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Ice_Ball.json`
- Modify: `Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_RainShoot_Barrage.json`

- [ ] **Step 1: Write the failing choreography contract**

Create the test class with shared `ROOT`, `read`, `json`, and `occurrences` helpers, then add:

```java
@Test
void sharedRangedChoreographyUsesElementVariablesWithoutTimingDrift() throws Exception {
    String ball = read("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Ice_Ball.json");
    assertEquals(3, occurrences(ball, "\"Var\": \"Hydra_Ball_Charge_Effect\""));
    assertEquals(3, occurrences(ball, "\"Var\": \"Hydra_Ball_Launch\""));
    assertEquals(1, occurrences(ball, "\"RunTime\": 1"));
    assertEquals(3, occurrences(ball, "\"RunTime\": 0.5"));
    assertTrue(ball.contains("\"ItemAnimationId\": \"PrepareShoot\""));
    assertTrue(ball.contains("\"ItemAnimationId\": \"FinishShoot\""));

    String rain = read("Server/Item/RootInteractions/NPCs/Creature/HyDragon/"
            + "Root_NPC_Hydra_RainShoot_Barrage.json");
    assertEquals(20, occurrences(rain, "\"Var\": \"Hydra_Rain_Charge_Effect\""));
    assertEquals(20, occurrences(rain, "\"Var\": \"Hydra_Rain_Launch\""));
    assertEquals(20, occurrences(rain, "\"RunTime\": 0.3"));
}

@Test
void iceRolesProvideAllFourRangedDefaults() throws Exception {
    JsonObject vars = json("Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra.json")
            .getAsJsonObject("Modify").getAsJsonObject("_InteractionVars");
    assertVariableLeaf(vars, "Hydra_Ball_Charge_Effect", "Hydra_Ice_Ball_Charge_Effect");
    assertVariableLeaf(vars, "Hydra_Ball_Launch", "Hydra_Ice_Ball_Launch");
    assertVariableLeaf(vars, "Hydra_Rain_Charge_Effect", "Hydra_Rain_Ice_Charge_Effect");
    assertVariableLeaf(vars, "Hydra_Rain_Launch", "Hydra_Rain_Ice_Launch");
}
```

The helpers should parse UTF-8 files with Gson. `assertVariableLeaf` must require one entry under `Interactions` and compare its `Parent` value exactly.

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
./mvnw -Dtest=ToxicHydraVariantAssetTest test
```

Expected: FAIL because the variable references and Ice defaults do not exist.

- [ ] **Step 3: Extract the Ice leaves**

Create each leaf as a single reusable interaction:

- `Hydra_Ice_Ball_Charge_Effect`: the existing `Simple` charge block's effects, with no `RunTime` so the caller retains 1.0/0.5-second cadence.
- `Hydra_Ice_Ball_Launch`: the existing direct `TameworkLaunchProjectile` block, including `Hydra_Ice_Ball`, direct trajectory, `CAETargetSlot`, radius 3 `Chilled`, source exclusion, sounds, particle, and empty tags.
- `Hydra_Rain_Ice_Charge_Effect`: the existing rain charge block's effects, with no `RunTime`.
- `Hydra_Rain_Ice_Launch`: the existing rain launch block, including `Hydra_Rain_Ice_Ball`, random radius 6–15, offset `(0,-1,-2)`, radius 4, duration 6, tick 1, damage 5, `Chilled`, source `hydragon.rain_ice_hazard`, and all existing sounds/particles/tags.

- [ ] **Step 4: Replace repetition with the four seams**

In the direct graph, retain PrepareShoot and FinishShoot. Replace the three charge bodies with:

```json
{
  "Type": "Replace",
  "DefaultOk": true,
  "Var": "Hydra_Ball_Charge_Effect",
  "DefaultValue": {
    "Interactions": ["Hydra_Ice_Ball_Charge_Effect"]
  },
  "RunTime": 1.0
}
```

for the first charge and `RunTime: 0.5` for the next two. Replace each launch body with a zero-runtime `Replace` whose `Var` is `Hydra_Ball_Launch` and whose `DefaultValue.Interactions` contains `Hydra_Ice_Ball_Launch`.

In the rain graph, preserve every surrounding animation and ordering node. Replace exactly 20 charge bodies with `Var: Hydra_Rain_Charge_Effect`, defaulting to `Hydra_Rain_Ice_Charge_Effect`, at `RunTime: 0.3`. Replace exactly 20 launch bodies with zero-runtime `Var: Hydra_Rain_Launch`, defaulting to `Hydra_Rain_Ice_Launch`.

Append all four defaults to the wild Hydra `_InteractionVars`, each containing one interaction whose `Parent` is the matching Ice leaf. Add the identical four defaults to `Tamed_Hydra.json`; do not add ranged AI or change tamed combat behavior.

- [ ] **Step 5: Run the focused test and inspect the diff**

Run:

```bash
./mvnw -Dtest=ToxicHydraVariantAssetTest test
git diff --check
git diff --stat
```

Expected: PASS; three direct charge/launch pairs and twenty rain pairs remain, with unchanged cadence.

- [ ] **Step 6: Commit the parameterization**

```bash
git add src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java \
  Server/Item/Interactions/NPCs/HyDragon/Hydra \
  Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Hydra_RainShoot_Barrage.json \
  Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra.json \
  Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra.json
git commit -m "Refactor: parameterize Hydra ranged attacks"
```

## Task 2: Add toxic projectile presentation and effect leaves

**Files:**

- Modify: `src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java`
- Create: both toxic projectile files, the toxic projectile model, and all four toxic leaf interactions listed in the file structure

- [ ] **Step 1: Add failing toxic projectile and leaf contracts**

Add tests that require:

```java
@Test
void toxicProjectilesPreserveIceMechanicsAndReplaceOnlyElementPresentation() throws Exception {
    assertProjectileParity(
            "Server/Projectiles/HyDragon/Hydra/Hydra_Ice_Ball.json",
            "Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json");
    assertProjectileParity(
            "Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Ice_Ball.json",
            "Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Toxic_Ball.json");
}

@Test
void toxicLeavesUsePoisonT1AndCanonicalPoisonPresentation() throws Exception {
    String direct = read("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Launch.json");
    assertTrue(direct.contains("\"ProjectileId\": \"Hydra_Toxic_Ball\""));
    assertTrue(direct.contains("\"EffectId\": \"Poison_T1\""));
    assertTrue(direct.contains("\"Radius\": 3.0"));
    assertTrue(direct.contains("\"ExcludeSource\": true"));

    String rain = read("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Launch.json");
    for (String required : java.util.List.of(
            "\"ProjectileId\": \"Hydra_Rain_Toxic_Ball\"",
            "\"EffectId\": \"Poison_T1\"",
            "\"SourceId\": \"hydragon.rain_toxic_hazard\"",
            "\"Radius\": 4.0", "\"Duration\": 6.0",
            "\"TickInterval\": 1.0", "\"DamagePerTick\": 5.0")) {
        assertTrue(rain.contains(required), required);
    }
}
```

Implement `assertProjectileParity` by deep-copying both JSON objects and removing only `Appearance`, `DeathParticles`, `MissParticles`, `HitParticles`, and `DeathSoundEventId` before exact equality comparison. Separately assert both toxic projectiles select `Hydra_Toxic_Ball_Projectile`. Parse that model asset and require `Items/Projectiles/Acid.blockymodel`, `Items/Projectiles/Acid_Texture.png`, the exact Ice Ball hitbox, scale range 3–5, and one `Status_Poisoned` particle. Assert toxic effect leaves contain `Effect_Poison`, `Impact_Poison`, `SFX_Scarak_Spitball_Fire`, and `SFX_Scarak_Seeker_Spitball_Death` where the Ice equivalents currently occur.

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
./mvnw -Dtest=ToxicHydraVariantAssetTest test
```

Expected: FAIL because toxic projectile and leaf files are absent.

- [ ] **Step 3: Reconfirm canonical base-game IDs before authoring**

Using the Hytale Workshop MCP against release `0.5.7`, validate `Poison_T1`, `Effect_Poison`, `Impact_Poison`, `Status_Poisoned`, both Scarak sound IDs, and both Acid model/texture paths. Reconfirm that `Poison_T1` deals 6 Poison damage, has a 5-second damage cooldown, lasts 16 seconds, and uses `Extend` overlap behavior. If any ID or effect contract differs, stop and update the approved design and this plan before implementation.

- [ ] **Step 4: Create the toxic projectile appearance and projectile copies**

Create `Hydra_Toxic_Ball_Projectile.json` with the following complete content:

```json
{
  "Model": "Items/Projectiles/Acid.blockymodel",
  "Texture": "Items/Projectiles/Acid_Texture.png",
  "HitBox": {
    "Max": { "X": 0.1, "Y": 0.1, "Z": 0.1 },
    "Min": { "X": -0.1, "Y": -0.1, "Z": -0.1 }
  },
  "MinScale": 3,
  "MaxScale": 5,
  "Particles": [
    {
      "PositionOffset": { "X": 0, "Y": 0, "Z": 0 },
      "SystemId": "Status_Poisoned",
      "TargetNodeName": ""
    }
  ]
}
```

Copy each matching Ice projectile's JSON structure and every numeric/mechanical field. Change only the five presentation fields admitted by `assertProjectileParity`. Set `Appearance` to `Hydra_Toxic_Ball_Projectile`; use `Effect_Poison` for death/miss, `Impact_Poison` for hits, and `SFX_Scarak_Seeker_Spitball_Death` for death sound. Preserve `Scale: 2.0` on all three rain projectile particle objects.

- [ ] **Step 5: Create the four toxic leaves**

Mirror each Ice leaf's interaction type, trajectory, targeting, offsets, radii, durations, tick rate, damage, and tags. Replace Ice presentation IDs with the canonical poison IDs. The direct launch applies `Poison_T1` in radius 3 and excludes the source. The rain launch uses `Poison_T1` in its lingering hazard and source ID `hydragon.rain_toxic_hazard`.

- [ ] **Step 6: Verify and commit**

```bash
./mvnw -Dtest=ToxicHydraVariantAssetTest test
git diff --check
git add src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java \
  Server/Projectiles/HyDragon/Hydra \
  Server/Models/Projectiles/HyDragon/Hydra_Toxic_Ball_Projectile.json \
  Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Charge_Effect.json \
  Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Launch.json \
  Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Charge_Effect.json \
  Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Launch.json
git commit -m "Feat: add Toxic Hydra projectile leaves"
```

## Task 3: Add inherited Toxic Hydra roles, model, and skin

**Files:**

- Modify: `src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java`
- Create: `Server/Models/HyDragon/Hydra/Hydra_Toxic.json`
- Create: `Common/NPC/HyDragon/Hydra/Model/Toxic.png`
- Create: `Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra_Toxic.json`
- Create: `Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra_Toxic.json`

- [ ] **Step 1: Add failing inheritance and override contracts**

Add a parameterized helper invoked for both roles:

```java
@Test
void toxicRolesInheritIceRolesAndOverrideEveryElementSeam() throws Exception {
    assertToxicRole("Hydra_Toxic", "Hydra", "Tamed_Hydra_Toxic");
    assertToxicRole("Tamed_Hydra_Toxic", "Tamed_Hydra", null);
}
```

`assertToxicRole` must require:

- `Type` is `Variant` and `Reference` equals the supplied Ice role.
- `Modify.Appearance` is `Hydra_Toxic`.
- The wild role changes `TameRoleChange` to `Tamed_Hydra_Toxic`; the tamed role does not introduce a tame-role change.
- `_InteractionVars` has exactly nine keys: the five existing melee damage variables and the four ranged variables.
- Each ranged variable contains one `Parent` with its toxic leaf ID.
- Each melee variable contains one interaction with the matching `Hydra_*_Damage` parent and a `Next` interaction of type `ApplyEffect`, `Entity: Target`, and `EffectId: Poison_T1`.
- No copied movement, health, targeting, CAE, loot, or animation tuning appears in either child.

Add model/texture checks:

```java
@Test
void toxicModelInheritsHydraAndOnlySelectsToxicTexture() throws Exception {
    JsonObject model = json("Server/Models/HyDragon/Hydra/Hydra_Toxic.json");
    assertEquals("Hydra", model.get("Parent").getAsString());
    assertEquals("Common/NPC/HyDragon/Hydra/Model/Toxic.png",
            model.get("Texture").getAsString());
    assertEquals(java.util.Set.of("Parent", "Texture"), model.keySet());
}

@Test
void toxicTexturePreservesIceDimensionsAndPerPixelAlpha() throws Exception {
    BufferedImage ice = ImageIO.read(ROOT.resolve(
            "Common/NPC/HyDragon/Hydra/Model/Ice.png").toFile());
    BufferedImage toxic = ImageIO.read(ROOT.resolve(
            "Common/NPC/HyDragon/Hydra/Model/Toxic.png").toFile());
    assertNotNull(ice);
    assertNotNull(toxic);
    assertEquals(ice.getWidth(), toxic.getWidth());
    assertEquals(ice.getHeight(), toxic.getHeight());
    int alphaMismatches = 0;
    for (int y = 0; y < ice.getHeight(); y++) {
        for (int x = 0; x < ice.getWidth(); x++) {
            if ((ice.getRGB(x, y) >>> 24) != (toxic.getRGB(x, y) >>> 24)) alphaMismatches++;
        }
    }
    assertEquals(0, alphaMismatches, "Toxic recolor must preserve the complete UV alpha mask");
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
./mvnw -Dtest=ToxicHydraVariantAssetTest test
```

Expected: FAIL because roles, model, and skin are absent.

- [ ] **Step 3: Create the inherited model and toxic texture**

Create `Hydra_Toxic.json` with only:

```json
{
  "Parent": "Hydra",
  "Texture": "Common/NPC/HyDragon/Hydra/Model/Toxic.png"
}
```

Use the image-generation editing workflow with `Ice.png` as the referenced source. Recolor only existing opaque pixels into a violet/swamp-blue hide with chartreuse toxin accents. Do not move, resize, paint outside, or change alpha on any UV island. Save the result as `Toxic.png`, then rely on the per-pixel alpha test and visual inspection to catch topology damage.

- [ ] **Step 4: Create the wild Toxic Hydra variant**

Set `Reference: Hydra`. Keep `Modify` minimal: `Appearance`, `TameRoleChange`, `_InteractionVars`, and `NameTranslationKey`. Set `Parameters.NameTranslationKey.Value` to `server.npcRoles.Hydra_Toxic.name`. The four ranged parents are:

```text
Hydra_Ball_Charge_Effect -> Hydra_Toxic_Ball_Charge_Effect
Hydra_Ball_Launch -> Hydra_Toxic_Ball_Launch
Hydra_Rain_Charge_Effect -> Hydra_Rain_Toxic_Charge_Effect
Hydra_Rain_Launch -> Hydra_Rain_Toxic_Launch
```

For each melee variable, retain the inherited damage leaf through its matching parent and append poison:

```json
{
  "Parent": "Hydra_Bite_Damage",
  "Next": {
    "Type": "ApplyEffect",
    "EffectId": "Poison_T1",
    "Entity": "Target"
  }
}
```

Use the corresponding `Hydra_Swipe_Left_Damage`, `Hydra_Swipe_Right_Damage`, `Hydra_Stomp_Damage`, and `Hydra_Tail_Spin_Damage` parent in the other four variables. Do not copy or replace their damage calculators: the toxic child must inherit the Ice role's current melee damage while adding poison.

- [ ] **Step 5: Create the tamed Toxic Hydra variant**

Set `Reference: Tamed_Hydra`. Provide the toxic appearance, four ranged variable parents, and five poison-appending melee parents. Set `Parameters.NameTranslationKey.Value` to `server.npcRoles.Tamed_Hydra_Toxic.name`. Do not add ranged AI, taming settings, or copied Ice role fields; variant inheritance provides all tamed behavior.

- [ ] **Step 6: Verify and commit**

```bash
./mvnw -Dtest=ToxicHydraVariantAssetTest test
git diff --check
git add src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java \
  Server/Models/HyDragon/Hydra/Hydra_Toxic.json \
  Common/NPC/HyDragon/Hydra/Model/Toxic.png \
  Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra_Toxic.json \
  Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra_Toxic.json
git commit -m "Feat: add inherited Toxic Hydra roles"
```

## Task 4: Register the variant, spawn it rarely in swamps, and localize it

**Files:**

- Modify: `src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java`
- Create: `Server/NPC/Spawn/World/Zone1/Spawns_Zone1_Swamps_HyDragon_Predator.json`
- Modify: all species/Tamework/language files listed in the file structure

- [ ] **Step 1: Add failing spawn and registration contracts**

Add assertions for the exact spawn document:

```java
@Test
void toxicHydraIsAWeightOneDaytimeSwampPredator() throws Exception {
    JsonObject spawn = json("Server/NPC/Spawn/World/Zone1/"
            + "Spawns_Zone1_Swamps_HyDragon_Predator.json");
    assertEquals(java.util.List.of("Env_Zone1_Swamps"), strings(spawn.getAsJsonArray("Environments")));
    JsonObject npc = spawn.getAsJsonArray("NPCs").get(0).getAsJsonObject();
    assertEquals(1, spawn.getAsJsonArray("NPCs").size());
    assertEquals(1, npc.get("Weight").getAsInt());
    assertEquals("Mud", npc.get("SpawnBlockSet").getAsString());
    assertEquals("Hydra_Toxic", npc.get("Id").getAsString());
    assertEquals(java.util.List.of(6, 18), ints(spawn.getAsJsonArray("DayTimeRange")));
    assertEquals(java.util.List.of(0, 4), ints(spawn.getAsJsonArray("MoonPhaseRange")));
    assertEquals(java.util.List.of(0.7, 0.85, 1.0, 1.15, 1.3),
            doubles(spawn.getAsJsonArray("MoonPhaseWeightModifiers")));
}
```

Add one table-driven registration test. Parse JSON and assert each ID at its exact structural location; require one occurrence inside each listed array/map and preserve all existing Hydra entries:

```text
Server/HyDragon/DragonSpecies/Hydra.json:
  WildRoleIds contains Hydra and Hydra_Toxic
  TamedRoleIdByWildRole maps Hydra_Toxic to Tamed_Hydra_Toxic
  Spawn.OrdinarySpawnAssetIds contains Spawns_Zone1_Swamps_HyDragon_Predator
  Presentation.ModelIds contains Hydra and Hydra_Toxic
Server/Tamework/CapturePolicies/HyDragonHydra.json: RoleIds contains Hydra_Toxic
Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json: AllowedRoles contains Tamed_Hydra_Toxic
Server/Tamework/Companion/HyDragonFullDragons.json: RoleIds contains Tamed_Hydra_Toxic
Server/Tamework/Breeding/HyDragonBondedCompanions.json: RoleIds contains Tamed_Hydra_Toxic
Server/Tamework/Items/Spawners/HyDragonDraconicStone.json:
  AllowedRoles.Allowlist contains Hydra_Toxic
  Capture.TamedRoleOverrides maps Hydra_Toxic to Tamed_Hydra_Toxic
Server/Tamework/Items/Commands/HyDragonDragonHorn.json: AllowedRoles.Allowlist contains Tamed_Hydra_Toxic
```

Add locale assertions requiring `npcRoles.Hydra_Toxic.name` and `npcRoles.Tamed_Hydra_Toxic.name` exactly once with these values:

```text
en-US: Toxic Hydra | Bonded Toxic Hydra
de-DE: Toxische Hydra | Gebundene toxische Hydra
es-ES: Hidra tóxica | Hidra tóxica vinculada
fr-FR: Hydre toxique | Hydre toxique liée
pt-BR: Hidra tóxica | Hidra tóxica vinculada
```

Assert both keys appear exactly once per catalog.

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
./mvnw -Dtest=ToxicHydraVariantAssetTest test
```

Expected: FAIL because the spawn, registrations, and translations are absent.

- [ ] **Step 3: Create the rare swamp spawn**

Create the spawn asset with this complete structure:

```json
{
  "Environments": ["Env_Zone1_Swamps"],
  "NPCs": [
    {
      "Weight": 1,
      "SpawnBlockSet": "Mud",
      "Id": "Hydra_Toxic"
    }
  ],
  "DayTimeRange": [6, 18],
  "MoonPhaseRange": [0, 4],
  "MoonPhaseWeightModifiers": [0.7, 0.85, 1.0, 1.15, 1.3]
}
```

Do not add it to cave swamp spawns.

- [ ] **Step 4: Update species and Tamework integration**

Follow each file's existing Hydra entry shape and ordering. Add exactly one toxic entry beside the existing Ice Hydra entry. In the species registry, map `Hydra_Toxic` to `Tamed_Hydra_Toxic`, `Hydra_Toxic` appearance, and the new swamp spawn asset. In the Draconic Stone config, allow the wild role and add its explicit tamed-role override. Preserve all existing IDs and policy values.

- [ ] **Step 5: Add all five locale pairs**

Add the wild and bonded keys to each `server.lang` beside the current Hydra name. Preserve UTF-8 accents and the file's newline convention.

- [ ] **Step 6: Run focused integration tests and commit**

```bash
./mvnw -Dtest=ToxicHydraVariantAssetTest,DragonRosterAssetContractTest,BundledConfigAssetContractTest test
git diff --check
git add src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java \
  Server/NPC/Spawn/World/Zone1/Spawns_Zone1_Swamps_HyDragon_Predator.json \
  Server/HyDragon/DragonSpecies/Hydra.json \
  Server/Tamework/CapturePolicies/HyDragonHydra.json \
  Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json \
  Server/Tamework/Companion/HyDragonFullDragons.json \
  Server/Tamework/Breeding/HyDragonBondedCompanions.json \
  Server/Tamework/Items/Spawners/HyDragonDraconicStone.json \
  Server/Tamework/Items/Commands/HyDragonDragonHorn.json \
  Server/Languages/en-US/server.lang Server/Languages/de-DE/server.lang \
  Server/Languages/es-ES/server.lang Server/Languages/fr-FR/server.lang \
  Server/Languages/pt-BR/server.lang
git commit -m "Feat: spawn Toxic Hydras in swamps"
```

## Task 5: Prove the inheritance graph, package the mod, and review the result

**Files:**

- Verify: every file created or modified in Tasks 1–4
- Generate only ignored evidence under: `.asset-tools/reports/`
- Do not modify source unless a validation or review finding identifies a concrete defect

- [ ] **Step 1: Confirm the exact asset profile before using authoring evidence**

From the HytaleNpcAssetTools repository, run:

```bash
cd ../HytaleNpcAssetTools
hytale-assets profile check \
  --project-profile ../HyDragon/.hytale-npc-assets.json \
  --json
```

Require release `0.5.7`, source commit `dd07e6a837aaf6378e82ff81d6f520f913624c08`, and a warm/ready identity with no channel, plugin, knowledge, or runtime-overlay mismatch. Stop on any identity mismatch; do not substitute nightly evidence.

- [ ] **Step 2: Inspect both toxic roles with inheritance and reference views**

```bash
hytale-assets author inspect \
  --project-profile ../HyDragon/.hytale-npc-assets.json \
  --asset Hydra_Toxic --view both --provenance compact --references both \
  --format json --include-advisories actionable \
  --out ../HyDragon/.asset-tools/reports/toxic-hydra-wild-inspect.json

hytale-assets author inspect \
  --project-profile ../HyDragon/.hytale-npc-assets.json \
  --asset Tamed_Hydra_Toxic --view both --provenance compact --references both \
  --format json --include-advisories actionable \
  --out ../HyDragon/.asset-tools/reports/toxic-hydra-tamed-inspect.json
```

Review both authored and resolved views. Confirm the wild role resolves to the existing Hydra CAE and attack roots, the tamed role retains all Tamed Hydra behavior, the nine toxic interaction variables resolve, and every new leaf/projectile/model/spawn reference is present.

- [ ] **Step 3: Run the affected-closure check from the approved baseline**

```bash
hytale-assets author check \
  --project-profile ../HyDragon/.hytale-npc-assets.json \
  --changed --base ea5c3d36e087162706a1130a83fa7e0ce14b04a6 \
  --scope affected --format json \
  --out ../HyDragon/.asset-tools/reports/toxic-hydra-check.json
cd ../HyDragon
```

Treat unsupported or manual items as review evidence, not automatic passes. Resolve every blocking schema/reference/error finding. Record any remaining live-only checks in the final handoff.

- [ ] **Step 4: Validate new engine-facing JSON references against Workshop 0.5.7**

Use `validate_hytale_asset_references` on the raw contents of both toxic roles, both toxic projectile assets, all eight Ice/Toxic leaf interactions, both new model assets, and the swamp spawn. Classify unresolved IDs defined by this mod as local references; fix any unresolved vanilla ID. Use `validate_hytale_gamedata` with the appropriate asset type for each new role, projectile, interaction, model, and spawn document. Do not accept malformed JSON, unknown discriminators, or bad enum values.

- [ ] **Step 5: Run focused and full automated verification**

```bash
./mvnw -Dtest=ToxicHydraVariantAssetTest,DragonRosterAssetContractTest,BundledConfigAssetContractTest test
./mvnw clean verify
git diff --check ea5c3d36e087162706a1130a83fa7e0ce14b04a6..HEAD
jar tf "target/HyDragon v1.0.0.jar" | rg \
  'Hydra_Toxic|Tamed_Hydra_Toxic|Toxic.png|Spawns_Zone1_Swamps_HyDragon_Predator|Hydra_(Rain_)?Toxic'
```

Expected: all tests and Maven verification pass; the packaged JAR contains both roles, model/texture, projectile/interaction leaves, and swamp spawn.

- [ ] **Step 6: Request independent code and asset review**

Use `superpowers:requesting-code-review` with a read-only `reviewer`. Give it the approved design, this plan, baseline commit, current HEAD, and the asset-tool reports. Ask specifically for behavioral drift in the shared Ice graphs, incomplete toxic overrides, incorrect inheritance, registration omissions, spawn-rarity errors, and tests that could pass without proving the requirement. The main agent reconciles every finding and retains all edit/commit authority.

- [ ] **Step 7: Fix only concrete findings and rerun the owning verification**

For any valid finding, return to the task that owns the affected file, add or tighten a failing regression assertion, implement the smallest correction, rerun that task's focused command and `./mvnw clean verify`, then create a scoped `Fix:` commit. Do not create an empty validation commit.

- [ ] **Step 8: Report the live checklist without claiming unrun gameplay proof**

Unless the user separately authorizes a live Hytale server/client run, hand off these manual checks as pending:

- Observe a rare `Hydra_Toxic` spawn in `Env_Zone1_Swamps`, and verify it does not appear in cave swamps.
- Confirm visual UV alignment and chartreuse/violet toxic skin at gameplay distance.
- Confirm bite, both swipes, stomp, and tail spin apply `Poison_T1` once per hit while preserving Ice Hydra damage.
- Confirm the three-shot direct burst retains its 1.0/0.5/0.5 charge cadence and applies poison rather than chill.
- Confirm the 20-shot rain barrage preserves radius, offset, cadence, hazard duration/tick/damage, and applies poison rather than chill.
- Capture, bond, summon, command, breed, stone-spawn, and horn-control the toxic form; verify it maps to `Tamed_Hydra_Toxic` and keeps normal tamed Hydra behavior.

Final handoff must include the commit list, automated commands and outcomes, asset-profile identity, asset-tool report paths, independent-review result, and the explicit pending live checklist.
