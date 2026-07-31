# MiniWyvern Elemental Projectile Impact Audio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Play one appropriate vanilla elemental sound whenever a MiniWyvern projectile collides with an entity, terminates on terrain, or bounces from terrain.

**Architecture:** Keep the feature in existing Hytale 0.5.7 projectile JSON. Each element retains its damage/effect interaction and gains a zero-runtime sound branch in ProjectileHit; each of the 42 projectile configurations gains the same element's sound in ProjectileMiss and ProjectileBounce. One Java asset-contract test owns the seven-event mapping and proves all variants have the required coverage.

**Tech Stack:** Hytale 0.5.7 item-interaction JSON, vanilla SoundEvent IDs, Java 25, Gson, JUnit 5, Maven Wrapper, Hytale NPC Asset Tools.

## Global Constraints

- Use the release 0.5.7 project profile identity 8f917b67e62db471460d6e72d9c512e290c313611493f67a483b28d818ebc65d.
- Use exactly: Fire SFX_Staff_Flame_Fireball_Impact; Ice SFX_Ice_Bolt_Death; Lightning SFX_Spear_Projectile_Impact; Nature SFX_Plant_Hit; Toxic SFX_Effect_Poison_World; Void SFX_Eye_Void_Attack_Blast; Wild SFX_Rubble_Hit.
- Cover ProjectileHit, ProjectileMiss, and ProjectileBounce; they mean entity contact, terminal no-entity impact, and continuing terrain contacts respectively.
- Emit exactly one elemental impact sound per route activation from the projectile at its collision position.
- Do not emit elemental sound from DamageEntity success, failure, or blocked branches.
- Preserve damage, effects, models, physics, launch values, cooldowns, custom launch audio, and removal behavior.
- Add no custom audio assets or SoundEvents.
- Make production asset edits only after the relevant test has failed for the expected missing-coverage reason.
- Stage only feature files.

---

### Task 1: Add the executable elemental-impact contract

**Files:**

- Create: src/test/java/com/alechilles/hydragon/config/MiniwyvernProjectileImpactAudioAssetTest.java

**Interfaces:**

- Consumes: all Projectile_Config_HyDragon_Miniwyvern_<Element>_*.json files, each ProjectileHit root, and its matching item interaction.
- Produces: the immutable Map<String, String> of seven element-to-SoundEvent mappings and regression coverage for 42 configurations plus their hit interactions.

- [ ] **Step 1: Write the failing asset contract**

Create MiniwyvernProjectileImpactAudioAssetTest with project-relative paths, matching the existing Miniwyvern asset tests.

~~~
private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));
private static final Path PROJECTILES = ROOT.resolve(
        Path.of("Server", "ProjectileConfigs", "HyDragon", "Wyvern_Mini"));
private static final Map<String, String> IMPACT_EVENTS = Map.of(
        "Fire", "SFX_Staff_Flame_Fireball_Impact", "Ice", "SFX_Ice_Bolt_Death",
        "Lightning", "SFX_Spear_Projectile_Impact", "Nature", "SFX_Plant_Hit",
        "Toxic", "SFX_Effect_Poison_World", "Void", "SFX_Eye_Void_Attack_Blast",
        "Wild", "SFX_Rubble_Hit");

@Test
void everyElementalProjectilePlaysItsMappedImpactSoundForEveryCollisionRoute()
        throws IOException {
    List<Path> configs;
    try (Stream<Path> paths = Files.list(PROJECTILES)) {
        configs = paths.filter(path -> path.getFileName().toString()
                        .startsWith("Projectile_Config_HyDragon_Miniwyvern_"))
                .sorted().toList();
    }
    assertEquals(42, configs.size(), "seven elements must retain six projectile variants each");
    for (Path configPath : configs) {
        String element = configPath.getFileName().toString().split("_")[4];
        String event = IMPACT_EVENTS.get(element);
        assertNotNull(event, "unknown Miniwyvern projectile element: " + configPath);
        JsonObject interactions = load(configPath).getAsJsonObject("Interactions");
        assertMissRoute(interactions.getAsJsonObject("ProjectileMiss"), event, configPath);
        assertBounceRoute(interactions.getAsJsonObject("ProjectileBounce"), event, configPath);
        assertHitRoute(interactions.get("ProjectileHit").getAsString(), event, configPath);
    }
}
~~~

Implement the miss and bounce assertions as follows.

~~~
private static void assertMissRoute(JsonObject route, String event, Path configPath) {
    JsonArray actions = route.getAsJsonArray("Interactions");
    assertSimpleWorldSound(actions.get(0).getAsJsonObject(), event, configPath);
    assertEquals("RemoveEntity", actions.get(1).getAsJsonObject().get("Type").getAsString());
    assertEquals("User", actions.get(1).getAsJsonObject().get("Entity").getAsString());
}
private static void assertBounceRoute(JsonObject route, String event, Path configPath) {
    JsonArray actions = route.getAsJsonArray("Interactions");
    assertEquals(1, actions.size(), "a bounce must only emit sound and continue: " + configPath);
    assertSimpleWorldSound(actions.get(0).getAsJsonObject(), event, configPath);
}
private static void assertSimpleWorldSound(JsonObject action, String event, Path path) {
    assertEquals("Simple", action.get("Type").getAsString(), path.toString());
    assertEquals(0.0, action.get("RunTime").getAsDouble(), 0.0, path.toString());
    assertEquals(event, action.getAsJsonObject("Effects")
            .get("WorldSoundEventId").getAsString(), path.toString());
}
~~~

assertHitRoute loads the root file named by the configuration's ProjectileHit ID from Server/Item/RootInteractions/NPCs/HyDragon/Wyvern_Mini/ProjectileHits/, resolves its one referenced ID under Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/ProjectileHits/, and asserts a top-level Parallel with exactly two branches: the mapped Simple sound branch and the preserved DamageEntity branch. Assert that the DamageEntity subtree has no element sound event ID. Import assertNotNull, JsonElement, Map, and Stream alongside the existing Gson/JUnit utilities.

- [ ] **Step 2: Run the targeted test and verify RED**

Run:

~~~
./mvnw -Dtest=MiniwyvernProjectileImpactAudioAssetTest test
~~~

Expected: a contract failure because the current configurations have no ProjectileBounce and begin ProjectileMiss with RemoveEntity, not a compilation or unrelated failure.

- [ ] **Step 3: Commit the failing contract**

~~~
git add src/test/java/com/alechilles/hydragon/config/MiniwyvernProjectileImpactAudioAssetTest.java
git diff --cached --check
git commit -m "Test: define MiniWyvern impact audio contract"
~~~

---

### Task 2: Wire all projectile collision routes

**Files:**

- Modify: all 42 files under Server/ProjectileConfigs/HyDragon/Wyvern_Mini/Projectile_Config_HyDragon_Miniwyvern_{Fire,Ice,Lightning,Nature,Toxic,Void,Wild}_*.json
- Modify: all 35 files under Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/ProjectileHits/HyDragon_Miniwyvern_{Fire,Ice,Lightning,Nature,Toxic,Void,Wild}_ProjectileHit_*.json

**Interfaces:**

- Consumes: the mapping from Task 1 and each configuration's ProjectileHit root ID.
- Produces: a two-child Parallel hit interaction, a sound-then-remove miss interaction, and a sound-only bounce interaction for every variant.

- [ ] **Step 1: Transform each entity-hit interaction**

Wrap each full original DamageEntity object as the second child of a new top-level Parallel. First child is the matching sound action; for Fire:

~~~
{
  "Type": "Parallel",
  "Interactions": [
    { "Type": "Simple", "RunTime": 0,
      "Effects": { "WorldSoundEventId": "SFX_Staff_Flame_Fireball_Impact" } },
    { "Type": "DamageEntity", "DamageCalculator": {
      "Type": "Absolute", "BaseDamage": { "Physical": 8 },
      "RandomPercentageModifier": 0 },
      "Next": { "Type": "Serial", "Interactions": [
        { "Type": "ApplyEffect", "Entity": "Target", "EffectId": "HyDragon_Miniwyvern_Fire_Burn" },
        { "Type": "RemoveEntity", "Entity": "User" }
      ] },
      "Failed": { "Type": "RemoveEntity", "Entity": "User" },
      "Blocked": { "Type": "RemoveEntity", "Entity": "User" } }
  ]
}
~~~

The Fire JSON above is the exact current base hit shape. For each of the other 34 files, retain that file's complete original DamageEntity object byte-for-byte as the second child; only its new first sound child changes according to the global mapping. Every Parallel has two children, never one.

- [ ] **Step 2: Transform every miss and bounce route**

Prepend the element's zero-runtime sound action to the existing remove action in ProjectileMiss, retaining its cooldown. Add this sibling route to every configuration:

~~~
"ProjectileBounce": {
  "Cooldown": { "Cooldown": 0 },
  "Interactions": [
    { "Type": "Simple", "RunTime": 0,
      "Effects": { "WorldSoundEventId": "SFX_Staff_Flame_Fireball_Impact" } }
  ]
}
~~~

The Fire example uses SFX_Staff_Flame_Fireball_Impact; use each other element's exact global-mapping ID in its six configurations. Do not put RemoveEntity in ProjectileBounce; continuing physics owns the projectile lifetime.

- [ ] **Step 3: Run the targeted test and verify GREEN**

~~~
./mvnw -Dtest=MiniwyvernProjectileImpactAudioAssetTest test
~~~

Expected: PASS after inspecting 42 configurations, every hit root, and all three collision routes.

- [ ] **Step 4: Validate changed assets against the exact profile**

From C:/Users/22ale/AppData/Roaming/Hytale/Modding/HytaleNpcAssetTools run:

~~~
PYTHONPATH=src python -m hytale_assets.cli profile check --project-profile ../HyDragon/.hytale-npc-assets.json --json
PYTHONPATH=src python -m hytale_assets.cli author check --project-profile ../HyDragon/.hytale-npc-assets.json --changed --format json
~~~

Expected: profile status ready; no invalid/error finding in changed projectile configurations, roots, or interactions. Record unsupported static checks separately.

- [ ] **Step 5: Run repository verification and package**

~~~
./mvnw verify
./mvnw -Pinstall-plugin -DskipTests package
~~~

Expected: both commands exit 0; the first proves tests and asset/package checks, the second produces the installable JAR only after verification.

- [ ] **Step 6: Commit the asset wiring**

~~~
git add Server/ProjectileConfigs/HyDragon/Wyvern_Mini Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/ProjectileHits
git diff --cached --check
git commit -m "Feat: add MiniWyvern elemental impact audio"
~~~

---

### Task 3: Install and perform runtime acceptance

**Files:**

- No source changes.

**Interfaces:**

- Consumes: the verified JAR from Task 2.
- Produces: installed mod copies and user-observed live evidence.

- [ ] **Step 1: Install the verified JAR**

Run the repository's standard plugin-install profile. Verify the generated HyDragon JAR checksum matches each installed target before asking for live testing.

- [ ] **Step 2: Restart and test every collision route in-game**

After server restart, use Fire, Ice, Lightning, Nature, Toxic, Void, and Wild projectiles to confirm entity hit, terrain termination, and bounce audio; an uncollided expiry must remain silent. Confirm launch spitting sounds and all previous damage/effect behavior remain unchanged.

- [ ] **Step 3: Record result without overclaiming**

Report static verification separately from the user's live observations. If any route plays twice, fails to play, or uses the wrong event, capture the element, projectile variant, collision type, and server log before changing assets again.
