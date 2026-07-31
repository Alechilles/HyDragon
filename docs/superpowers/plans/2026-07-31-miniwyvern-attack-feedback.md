# MiniWyvern Attack Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Play the user-authored Bite and Shoot animations with every matching MiniWyvern attack and add tightly trimmed, non-repeating randomized sound pools for both attack families.

**Architecture:** Keep the behavior asset-driven. Two SoundEvents own random clip selection, while the MiniWyvern aerial combat component sequences `PlayAnimation`, `Attack`, and `PlaySound` so animation wind-up and launch/strike audio align with Hytale 0.5.7 `ActionAttack` semantics. Structural tests protect every projectile profile, echo, swoop profile, audio encoding, and packaged resource.

**Tech Stack:** Hytale 0.5.7 NPC JSON, Hytale SoundEvent JSON, Blocky animation assets, Ogg Vorbis via FFmpeg, Java 25, Gson, JUnit 5, Maven Wrapper.

## Global Constraints

- Preserve all current user edits in `Server/Models/HyDragon/Wyvern_Mini/Wyvern_Mini.json`: `EyeHeight: 0.75`, flight blending changes, and the Bite/Shoot animation sets.
- Work on a named feature branch in the current worktree so the intentional uncommitted model edit is retained; do not stash, revert, or overwrite it.
- Use Hytale release 0.5.7 contracts only.
- Use the `Action` animation slot and the existing model animation-set IDs `Bite` and `Shoot`.
- Every projectile attack, including pattern/mastery echoes, gets Shoot plus projectile audio.
- Every swoop strike profile gets Bite plus bite audio.
- Preserve combat timing, cooldown, damage, targeting, talent arbitration, movement, and recovery values.
- Convert all seven supplied clips to mono 48 kHz Ogg Vorbis and remove only measured trailing silence.
- Both SoundEvents inherit `SFX_Attn_Quiet`, use one layer, and set `RoundRobinHistorySize` to `1` without pitch or volume randomization.
- Make production edits only after the relevant test has failed for the expected missing-feature reason.
- Stage files explicitly; do not include unrelated working-tree changes.

---

### Task 1: Add trimmed randomized attack sound pools

**Files:**
- Create: `src/test/java/com/alechilles/hydragon/config/MiniwyvernAttackFeedbackAssetTest.java`
- Modify: `src/test/java/com/alechilles/hydragon/build/PackagedJarContractIT.java`
- Create: `Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_01.ogg`
- Create: `Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_02.ogg`
- Create: `Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_03.ogg`
- Create: `Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_04.ogg`
- Create: `Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_01.ogg`
- Create: `Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_02.ogg`
- Create: `Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_03.ogg`
- Create: `Server/Audio/SoundEvents/SFX/HyDragon/Wyvern_Mini/SFX_HyDragon_Miniwyvern_Projectile.json`
- Create: `Server/Audio/SoundEvents/SFX/HyDragon/Wyvern_Mini/SFX_HyDragon_Miniwyvern_Bite.json`

**Interfaces:**
- Consumes: the seven MP3 files in `C:/Users/22ale/Downloads` named in the approved design.
- Produces: SoundEvent IDs `SFX_HyDragon_Miniwyvern_Projectile` and `SFX_HyDragon_Miniwyvern_Bite`, each resolving to a repository-local random clip pool.

- [ ] **Step 1: Create the feature branch without disturbing the model edit**

Run:

```bash
git switch -c feat/miniwyvern-attack-feedback
git status --short
```

Expected: the new branch is active and only `Server/Models/HyDragon/Wyvern_Mini/Wyvern_Mini.json` remains modified.

- [ ] **Step 2: Write the failing source-asset sound-pool test**

Create `MiniwyvernAttackFeedbackAssetTest` with a test that loads each SoundEvent, asserts the exact parent, exact ordered file list, `RoundRobinHistorySize: 1`, file existence, and mono Vorbis encoding. The core contract is:

```java
@Test
void attackSoundPoolsUseTrimmedMonoVorbisClipsWithoutImmediateRepeats() throws IOException {
    assertPool("Projectile", List.of(
            "Projectile_01.ogg", "Projectile_02.ogg",
            "Projectile_03.ogg", "Projectile_04.ogg"));
    assertPool("Bite", List.of("Bite_01.ogg", "Bite_02.ogg", "Bite_03.ogg"));
}

private static void assertPool(String family, List<String> files) throws IOException {
    Path eventPath = ROOT.resolve(Path.of("Server", "Audio", "SoundEvents", "SFX", "HyDragon",
            "Wyvern_Mini", "SFX_HyDragon_Miniwyvern_" + family + ".json"));
    JsonObject event = JsonParser.parseString(Files.readString(eventPath)).getAsJsonObject();
    assertEquals("SFX_Attn_Quiet", event.get("Parent").getAsString());
    JsonObject layer = event.getAsJsonArray("Layers").get(0).getAsJsonObject();
    assertEquals(1, layer.get("RoundRobinHistorySize").getAsInt());
    assertEquals(files.stream().map(name -> "Sounds/HyDragon/Wyvern_Mini/Attack/" + name).toList(),
            layer.getAsJsonArray("Files").asList().stream().map(JsonElement::getAsString).toList());
    for (String file : files) {
        Path audio = ROOT.resolve(Path.of("Common", "Sounds", "HyDragon", "Wyvern_Mini", "Attack", file));
        assertTrue(Files.isRegularFile(audio));
        assertMonoVorbis(audio);
    }
}
```

Copy the proven Vorbis identification-header parser from `NordicDrakeInteractionAssetTest.assertMonoVorbis`, retaining its `channels == 1` assertion.

- [ ] **Step 3: Extend packaged-JAR coverage before creating resources**

Add these exact entries to the `entries` assertions in `PackagedJarContractIT`:

```java
for (String entry : Set.of(
        "Server/Audio/SoundEvents/SFX/HyDragon/Wyvern_Mini/SFX_HyDragon_Miniwyvern_Projectile.json",
        "Server/Audio/SoundEvents/SFX/HyDragon/Wyvern_Mini/SFX_HyDragon_Miniwyvern_Bite.json",
        "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_01.ogg",
        "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_02.ogg",
        "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_03.ogg",
        "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_04.ogg",
        "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_01.ogg",
        "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_02.ogg",
        "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_03.ogg")) {
    assertTrue(entries.contains(entry), "missing MiniWyvern attack-feedback resource " + entry);
}
```

- [ ] **Step 4: Run the source-asset test and verify RED**

Run:

```bash
./mvnw -Dtest=MiniwyvernAttackFeedbackAssetTest test
```

Expected: FAIL because `SFX_HyDragon_Miniwyvern_Projectile.json` does not exist. A compilation error or failure in an unrelated test is not the required RED state.

- [ ] **Step 5: Convert and trim the projectile clips**

Create the target directory, then run these exact FFmpeg conversions:

```bash
mkdir -p Common/Sounds/HyDragon/Wyvern_Mini/Attack
ffmpeg -y -i "/c/Users/22ale/Downloads/young_dragon_spittin_#2-1785514869461.mp3" -t 0.70 -af "afade=t=out:st=0.69:d=0.01" -ac 1 -ar 48000 -c:a libvorbis -q:a 5 Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_01.ogg
ffmpeg -y -i "/c/Users/22ale/Downloads/young_dragon_spittin_#3-1785514905738.mp3" -t 0.67 -af "afade=t=out:st=0.66:d=0.01" -ac 1 -ar 48000 -c:a libvorbis -q:a 5 Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_02.ogg
ffmpeg -y -i "/c/Users/22ale/Downloads/young_dragon_spittin_#4-1785514880439.mp3" -t 0.64 -af "afade=t=out:st=0.63:d=0.01" -ac 1 -ar 48000 -c:a libvorbis -q:a 5 Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_03.ogg
ffmpeg -y -i "/c/Users/22ale/Downloads/young_dragon_spittin_#4-1785514900126.mp3" -t 0.72 -af "afade=t=out:st=0.71:d=0.01" -ac 1 -ar 48000 -c:a libvorbis -q:a 5 Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_04.ogg
```

- [ ] **Step 6: Convert and trim the bite clips**

Run:

```bash
ffmpeg -y -i "/c/Users/22ale/Downloads/Sharp,_snapping_bite_#2-1785515094449.mp3" -t 0.47 -af "afade=t=out:st=0.46:d=0.01" -ac 1 -ar 48000 -c:a libvorbis -q:a 5 Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_01.ogg
ffmpeg -y -i "/c/Users/22ale/Downloads/Sharp,_snapping_bite_#3-1785515100047.mp3" -t 0.47 -af "afade=t=out:st=0.46:d=0.01" -ac 1 -ar 48000 -c:a libvorbis -q:a 5 Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_02.ogg
ffmpeg -y -i "/c/Users/22ale/Downloads/Sharp,_snapping_bite_#4-1785515102095.mp3" -t 0.37 -af "afade=t=out:st=0.36:d=0.01" -ac 1 -ar 48000 -c:a libvorbis -q:a 5 Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_03.ogg
```

- [ ] **Step 7: Create the two SoundEvents**

Create `SFX_HyDragon_Miniwyvern_Projectile.json`:

```json
{
  "Parent": "SFX_Attn_Quiet",
  "Layers": [
    {
      "Files": [
        "Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_01.ogg",
        "Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_02.ogg",
        "Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_03.ogg",
        "Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_04.ogg"
      ],
      "RoundRobinHistorySize": 1
    }
  ]
}
```

Create `SFX_HyDragon_Miniwyvern_Bite.json` with the same structure and the three `Bite_01.ogg` through `Bite_03.ogg` paths.

- [ ] **Step 8: Verify GREEN for source and packaged resources**

Run:

```bash
./mvnw -Dtest=MiniwyvernAttackFeedbackAssetTest -Dit.test=PackagedJarContractIT verify
```

Expected: the command exits 0; the unit test sees two valid pools and the integration test sees every new file in the built JAR.

- [ ] **Step 9: Commit the sound-pool deliverable**

Stage only the new test, the packaged-JAR test change, two SoundEvents, and seven Ogg files. Do not stage `Wyvern_Mini.json` yet.

```bash
git add src/test/java/com/alechilles/hydragon/config/MiniwyvernAttackFeedbackAssetTest.java \
  src/test/java/com/alechilles/hydragon/build/PackagedJarContractIT.java \
  Server/Audio/SoundEvents/SFX/HyDragon/Wyvern_Mini \
  Common/Sounds/HyDragon/Wyvern_Mini/Attack
git diff --cached --check
git commit -m "Feat: add MiniWyvern attack sound pools"
```

---

### Task 2: Synchronize Bite and Shoot feedback with every attack

**Files:**
- Modify: `src/test/java/com/alechilles/hydragon/config/MiniwyvernAttackFeedbackAssetTest.java`
- Modify: `Server/Models/HyDragon/Wyvern_Mini/Wyvern_Mini.json`
- Modify: `Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json`

**Interfaces:**
- Consumes: model animation-set IDs `Bite` and `Shoot`; SoundEvent IDs from Task 1.
- Produces: exact adjacent action triples for 11 projectile attacks and four swoop bite attacks.

- [ ] **Step 1: Write the failing attack-sequencing test**

Add a test that recursively visits every `Actions` array in the component. For each action whose type is `Attack`, read `Attack.Compute`, classify `TalentProjectile*` as projectile and `SwoopAttack*` as bite, and assert its immediate neighbors:

```java
@Test
void everyAttackPlaysItsAnimationBeforeAndItsSoundAtDispatch() throws IOException {
    JsonObject component = load("Server/NPC/Roles/Creature/HyDragon/Components/"
            + "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json");
    List<JsonArray> sequences = new ArrayList<>();
    collectActionSequences(component, sequences);
    int projectileCount = 0;
    int biteCount = 0;
    for (JsonArray actions : sequences) {
        for (int index = 0; index < actions.size(); index++) {
            JsonObject action = actions.get(index).getAsJsonObject();
            if (!"Attack".equals(type(action))) continue;
            String attack = action.getAsJsonObject("Attack").get("Compute").getAsString();
            if (attack.startsWith("TalentProjectile")) {
                assertFeedback(actions, index, "Shoot", "SFX_HyDragon_Miniwyvern_Projectile");
                projectileCount++;
            } else if (attack.startsWith("SwoopAttack")) {
                assertFeedback(actions, index, "Bite", "SFX_HyDragon_Miniwyvern_Bite");
                biteCount++;
            }
        }
    }
    assertEquals(11, projectileCount);
    assertEquals(4, biteCount);
}

private static void assertFeedback(JsonArray actions, int attackIndex,
        String animation, String soundEvent) {
    assertTrue(attackIndex > 0 && attackIndex + 1 < actions.size());
    JsonObject before = actions.get(attackIndex - 1).getAsJsonObject();
    assertEquals("PlayAnimation", type(before));
    assertEquals("Action", before.get("Slot").getAsString());
    assertEquals(animation, before.get("Animation").getAsString());
    JsonObject after = actions.get(attackIndex + 1).getAsJsonObject();
    assertEquals("PlaySound", type(after));
    assertEquals(soundEvent, after.get("SoundEventId").getAsString());
}
```

Also assert the user-authored model contract:

```java
@Test
void miniwyvernModelExposesTheUserAuthoredAttackAnimations() throws IOException {
    JsonObject sets = load("Server/Models/HyDragon/Wyvern_Mini/Wyvern_Mini.json")
            .getAsJsonObject("AnimationSets");
    assertEquals("NPC/HyDragon/Wyvern_Mini/Animations/Bite.blockyanim",
            sets.getAsJsonObject("Bite").getAsJsonArray("Animations").get(0)
                    .getAsJsonObject().get("Animation").getAsString());
    assertEquals("NPC/HyDragon/Wyvern_Mini/Animations/Shoot.blockyanim",
            sets.getAsJsonObject("Shoot").getAsJsonArray("Animations").get(0)
                    .getAsJsonObject().get("Animation").getAsString());
}
```

Implement `collectActionSequences` by recursively visiting every object value and array element, adding any object-owned `Actions` array exactly once.

- [ ] **Step 2: Run the sequencing test and verify RED**

Run:

```bash
./mvnw -Dtest=MiniwyvernAttackFeedbackAssetTest test
```

Expected: FAIL at the first attack because its preceding action is not `PlayAnimation`. The model assertion may already pass because it protects the user's pre-existing edit.

- [ ] **Step 3: Add projectile animation and sound actions**

For each of the 11 actions whose attack compute is one of `TalentProjectileBase`, `TalentProjectileIntermediate`, `TalentProjectilePattern`, `TalentProjectilePatternEcho`, `TalentProjectileMastery`, or `TalentProjectileMasteryEcho`, transform the existing Attack in place. For example:

```json
{ "Type": "Attack", "Attack": { "Compute": "TalentProjectileBase" }, "AimingTimeRange": [0.1, 0.2], "AttackPauseRange": [0, 0] }
```

into this adjacent sequence without changing the Attack object:

```json
{ "Type": "PlayAnimation", "Slot": "Action", "Animation": "Shoot" },
{ "Type": "Attack", "Attack": { "Compute": "TalentProjectileBase" }, "AimingTimeRange": [0.1, 0.2], "AttackPauseRange": [0, 0] },
{ "Type": "PlaySound", "SoundEventId": "SFX_HyDragon_Miniwyvern_Projectile" }
```

Include all three echo action sites after their existing 0.3-second Timeout.

- [ ] **Step 4: Add swoop bite animation and sound actions**

For each of the four compute values `SwoopAttack`, `SwoopAttackFerocity`, `SwoopAttackRending`, and `SwoopAttackMastery`, preserve the committed-strike flag immediately before feedback and the recovery transition immediately after feedback. The default sequence is:

```json
{ "Type": "SetFlag", "Name": "Miniwyvern_Swoop_Strike_Committed", "SetTo": true },
{ "Type": "PlayAnimation", "Slot": "Action", "Animation": "Bite" },
{ "Type": "Attack", "Attack": { "Compute": "SwoopAttack" }, "AimingTimeRange": [0, 0], "AttackPauseRange": [0, 0] },
{ "Type": "PlaySound", "SoundEventId": "SFX_HyDragon_Miniwyvern_Bite" },
{ "Type": "State", "State": ".Recovery" }
```

- [ ] **Step 5: Verify GREEN for attack wiring**

Run:

```bash
./mvnw -Dtest=MiniwyvernAttackFeedbackAssetTest,MiniwyvernSwoopAssetContractTest,MiniwyvernTalentAssetWiringTest test
```

Expected: all targeted tests pass, with exact counts of 11 projectile attacks and four swoop attacks.

- [ ] **Step 6: Commit the synchronized attack feedback**

Stage the component, the expanded test, and the user's model changes explicitly:

```bash
git add Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json \
  Server/Models/HyDragon/Wyvern_Mini/Wyvern_Mini.json \
  src/test/java/com/alechilles/hydragon/config/MiniwyvernAttackFeedbackAssetTest.java
git diff --cached --check
git commit -m "Feat: synchronize MiniWyvern attack feedback"
```

---

### Task 3: Validate exact-profile wiring and the final artifact

**Files:**
- Inspect: `.hytale-npc-assets.json`
- Generate ignored reports under: `.asset-tools/reports/`
- Verify: all files committed in Tasks 1 and 2

**Interfaces:**
- Consumes: the complete feature branch from Tasks 1 and 2.
- Produces: exact-profile/static validation evidence, a clean Maven artifact, and an independent review verdict.

- [ ] **Step 1: Run the exact release profile check**

From `C:/Users/22ale/AppData/Roaming/Hytale/Modding/HytaleNpcAssetTools`, run:

```bash
hytale-assets profile check \
  --project-profile "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.hytale-npc-assets.json" \
  --json
```

Expected: release `0.5.7`, expected source commit `dd07e6a837aaf6378e82ff81d6f520f913624c08`, and ready profile identity. Stop if the profile is stale or mismatched.

- [ ] **Step 2: Run affected NPC authoring validation**

Use the canonical author check against the changed component and model, writing only ignored reports:

```bash
hytale-assets author check \
  --project-profile "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.hytale-npc-assets.json" \
  --changed \
  --format json \
  --out "C:/Users/22ale/AppData/Roaming/Hytale/Modding/HyDragon/.asset-tools/reports/miniwyvern-attack-feedback-check.json"
```

Expected: no new blocker on the changed assets. If the known component-as-role or unavailable-plugin limitation appears, record it as unsupported rather than treating it as a pass; rely on the repo validator, direct 0.5.7 source contracts, and structural tests for that surface.

- [ ] **Step 3: Run fresh full verification**

Run:

```bash
git diff --check
./mvnw clean verify
```

Expected: asset validation passes, all unit tests pass, all integration tests pass, and Maven reports `BUILD SUCCESS`.

- [ ] **Step 4: Inspect the packaged JAR directly**

Run:

```bash
unzip -l "target/HyDragon v1.0.0.jar" | rg "Miniwyvern_(Projectile|Bite)|Wyvern_Mini/Attack|Wyvern_Mini.json|Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend"
unzip -p "target/HyDragon v1.0.0.jar" Server/NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json | rg -c '"Type": "PlayAnimation"|"Type": "PlaySound"'
```

Expected: both SoundEvents, all seven Ogg files, the model, and the component are present; the component contains 15 PlayAnimation and 15 PlaySound actions.

- [ ] **Step 5: Request an independent runtime/schema review**

Ask a read-only reviewer to inspect only the committed feature diff for action ordering, echo overlap, animation-slot behavior, SoundEvent randomness, audio encoding, and unintended combat timing changes. Resolve every actionable finding and rerun Step 3 after any edit.

- [ ] **Step 6: Confirm the branch is clean and hand off integration**

Run:

```bash
git status --short
git log --oneline --decorate -4
```

Expected: clean feature branch containing the documentation commit plus the two scoped implementation commits. Use `superpowers:finishing-a-development-branch` to offer local merge, PR, or branch preservation. Install only the verified merged artifact or the exact verified branch artifact explicitly selected by the user.
