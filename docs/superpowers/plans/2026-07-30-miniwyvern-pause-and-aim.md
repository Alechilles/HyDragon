# MiniWyvern Pause-and-Aim Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every talent-driven MiniWyvern projectile attack pause movement, turn toward the locked target, fire after the existing 0.4–0.7-second aiming window, and then resume its prior combat movement.

**Architecture:** Keep the behavior asset-owned in the existing tamed MiniWyvern template. Each mutually exclusive projectile instruction will temporarily select `BodyMotion: Nothing` and `HeadMotion: Aim`; the surrounding grounded or aerial combat instruction automatically resumes after the blocking attack completes.

**Tech Stack:** Hytale NPC role JSON, Gson/JUnit 5 asset contract tests, Maven Wrapper, HyDragon asset validator.

## Global Constraints

- Apply the firing stance to all 11 projectile talents: `DraconicProjectile`, `ProjectileRange`, `ProjectileCadence`, `ProjectileForce`, `ProjectileGuidance`, `ProjectileImpact`, `ProjectilePattern`, `DraconicAssault`, `AssaultUtility`, `AssaultMastery`, and `DraconicApex`.
- Preserve `AimingTimeRange: [0.4, 0.7]` and every existing `AttackPauseRange`.
- Use `BodyMotion: { "Type": "Nothing" }` and `HeadMotion: { "Type": "Aim", "Spread": 0, "HitProbability": 1, "Deflection": true }` on each projectile instruction.
- Do not change projectile assets, orbit tuning, dive/bite behavior, talent selection, command states, or any Rockdrake/Hydra locomotion.
- Preserve unrelated user changes and build the installed jar from an exact committed revision.

---

### Task 1: Add the MiniWyvern projectile firing stance

**Files:**
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json`

**Interfaces:**
- Consumes: `COMBAT_TALENTS`, `instructionForTalent(JsonArray, String)`, and the existing 11 mutually exclusive projectile instruction branches.
- Produces: a JSON contract in which every projectile branch owns the same stationary ballistic-aiming stance during its blocking attack action.

- [ ] **Step 1: Confirm the exact project state and profile**

Run:

```bash
git status --short
git branch --show-current
python -m hytale_npc_assets.cli profile check --project-profile .hytale-npc-assets.json --json
```

Expected: branch `main`; preserve any unrelated changes. If the known descriptor hash mismatch remains, record the profile-backed validator as unavailable and continue with HyDragon's project validator rather than refreshing profile identity without authorization.

- [ ] **Step 2: Write the failing asset contract**

Add this test to `MiniwyvernTalentAssetWiringTest`:

```java
@Test
void everyProjectileVariantPausesMovementWhileAimingAtItsTarget() throws IOException {
    JsonArray instructions = load(TEMPLATE).getAsJsonArray("Instructions");
    JsonElement expectedBodyMotion = JsonParser.parseString("{\"Type\":\"Nothing\"}");
    JsonElement expectedHeadMotion = JsonParser.parseString(
            "{\"Type\":\"Aim\",\"Spread\":0,\"HitProbability\":1,\"Deflection\":true}");

    for (String talentId : COMBAT_TALENTS) {
        JsonObject instruction = instructionForTalent(instructions, talentId);
        assertEquals(expectedBodyMotion, instruction.get("BodyMotion"),
                talentId + " must pause movement while aiming");
        assertEquals(expectedHeadMotion, instruction.get("HeadMotion"),
                talentId + " must use deterministic ballistic aiming");
    }
}
```

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```bash
./mvnw -Dtest=MiniwyvernTalentAssetWiringTest test
```

Expected: `everyProjectileVariantPausesMovementWhileAimingAtItsTarget` fails because the first talent instruction's `BodyMotion` is `null`. All pre-existing tests remain green.

- [ ] **Step 4: Add the minimal firing stance to all projectile branches**

For each of the 11 talent instructions in `Template_Wyvern_Mini_Flying_Tamed.json`, retain its existing sensor, action, `AimingTimeRange`, and `AttackPauseRange`, then add these siblings beside `Actions`:

```json
"BodyMotion": { "Type": "Nothing" },
"HeadMotion": { "Type": "Aim", "Spread": 0, "HitProbability": 1, "Deflection": true }
```

Preserve these exact attack mappings and pause bands:

| Talent | Attack compute parameter | Attack pause range |
| --- | --- | --- |
| `DraconicProjectile` | `TalentProjectileBase` | `[5, 7]` |
| `ProjectileRange` | `TalentProjectileIntermediate` | `[5, 7]` |
| `ProjectileCadence` | `TalentProjectileIntermediate` | `[4, 6]` |
| `ProjectileForce` | `TalentProjectileIntermediate` | `[5, 7]` |
| `ProjectileGuidance` | `TalentProjectileIntermediate` | `[4, 6]` |
| `ProjectileImpact` | `TalentProjectileApex` | `[5, 7]` |
| `ProjectilePattern` | `TalentProjectileApex` | `[4, 6]` |
| `DraconicAssault` | `TalentProjectileApex` | `[3, 5]` |
| `AssaultUtility` | `TalentProjectileApex` | `[3, 5]` |
| `AssaultMastery` | `TalentProjectileApex` | `[3, 5]` |
| `DraconicApex` | `TalentProjectileApex` | `[3, 5]` |

Do not edit the separate aerial dive/bite `HeadMotion: Aim` instruction.

- [ ] **Step 5: Run focused verification and verify GREEN**

Run:

```bash
./mvnw -Dtest=MiniwyvernTalentAssetWiringTest,DragonHornLocomotionAssetContractTest test
```

Expected: all MiniWyvern talent and locomotion contract tests pass; HyDragon asset validation reports success before the tests.

- [ ] **Step 6: Run the full project verification**

Run:

```bash
./mvnw clean verify
git diff --check
```

Expected: 208 or more unit tests pass, 12 integration tests pass, every current HyDragon JSON asset validates, and `git diff --check` produces no errors.

- [ ] **Step 7: Commit the behavioral change**

Run:

```bash
git add -- Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json src/test/java/com/alechilles/hydragon/config/MiniwyvernTalentAssetWiringTest.java
git commit -m "Fix: pause MiniWyvern while aiming projectiles"
```

Expected: the commit contains only the template and regression-test changes.

- [ ] **Step 8: Install an exact committed build**

First confirm no Hytale server or Maven build is running. Build and install from a detached temporary worktree at the new commit:

```bash
set -e
if ps -W -f | rg -q '[H]ytaleServer|[M]avenWrapperMain'; then exit 9; fi
root="$PWD"
commit=$(git rev-parse HEAD)
wt=$(mktemp -d "${TMPDIR:-/tmp}/hydragon-pause-aim-install.XXXXXX")
git worktree add --detach "$wt" "$commit"
(cd "$wt" && ./mvnw package -DskipTests -Pinstall-plugin)
git worktree remove --force "$wt"
```

Verify both installed jars are byte-identical and the packaged template contains exactly 11 projectile instructions with the firing stance:

```bash
user_jar='C:/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar'
server_jar='C:/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods/HyDragon v1.0.0.jar'
sha256sum "$user_jar" "$server_jar"
cmp -s "$user_jar" "$server_jar"
python -c 'import json,zipfile; p="Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json"; z=zipfile.ZipFile(r"C:/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar"); d=json.loads(z.read(p)); xs=[i for i in d["Instructions"] if isinstance(i,dict) and any(isinstance(a,dict) and "AimingTimeRange" in a for a in i.get("Actions",[]))]; assert len(xs)==11; assert all(i.get("BodyMotion")=={"Type":"Nothing"} and i.get("HeadMotion")=={"Type":"Aim","Spread":0,"HitProbability":1,"Deflection":True} for i in xs); print("11 packaged firing stances verified")'
if ps -W -f | rg -i '[H]ytaleServer|[M]avenWrapperMain'; then exit 7; fi
git worktree list
```

Install destinations:

```text
C:/Users/22ale/AppData/Roaming/Hytale/UserData/Mods/HyDragon v1.0.0.jar
C:/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods/HyDragon v1.0.0.jar
```

Expected: both SHA-256 values match; no temporary worktree or Maven/Hytale server process remains.

- [ ] **Step 9: Runtime acceptance check**

In game, command a projectile-capable MiniWyvern to defend and observe multiple shots from different orbit headings. During each shot it must stop translating, rotate toward the locked target without exceeding its model's head limits, fire after 0.4–0.7 seconds, and return to its prior grounded movement or aerial wander. Record runtime observation as pending when no live harness/player session is available; do not infer it from static tests.
