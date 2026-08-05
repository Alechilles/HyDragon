# Dragon Horn Aggressive Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Dragon Horn flight/ground radial command with an
aggressive, hostility-only companion order that works in grounded and flying
paths.

**Architecture:** The command wheel enters Tamework's existing `Aggressive`
state. The ground-only template retains its established aggressive behavior;
the flying full-dragon and Miniwyvern templates select their normal ground or
flight motion branch before invoking that state behavior. The project asset
validator becomes the executable contract for the wheel, localization, and
ground/flying role wiring.

**Tech Stack:** Hytale NPC JSON assets, Alec's Tamework 5db09434e command
components, Python 3 asset validator, Gradle/JUnit verification.

## Global Constraints

- `Aggressive` attacks only targets hostile under the companion's attitude
  policy; it neither sets a crosshair target nor changes `MasterTarget`.
- Remove `ToggleAirborneMode` only from the Dragon Horn radial wheel.
  Companion-card `FlightToggle` controls remain unchanged.
- Full dragons and Miniwyverns must select explicit grounded (`Walk`) and
  flying (`Fly`) aggressive branches using `AirborneMode`; Hydras and Rock
  Drakes remain ground-only.
- Preserve the existing Defend and Attack Target behavior, including the
  Miniwyvern locked-target aerial talent path.

---

### Task 1: Make the asset validator enforce the aggressive command contract

**Files:**

- Modify: `scripts/validate_assets.py:1249-1310`
- Create: `scripts/tests/test_validate_assets.py`

**Interfaces:**

- Consumes: parsed asset map accepted by
  `validate_command_item(parsed, errors)`.
- Produces: an asset validator that rejects a Dragon Horn wheel retaining
  `ToggleAirborneMode`, omitting `Aggressive`, or using invalid aggressive
  feedback/state steps.

- [ ] **Step 1: Write the failing validator test**

  Create a `unittest` test that loads `scripts/validate_assets.py` with
  `importlib`, loads the Dragon Horn JSON into its parsed-map shape, replaces
  its `Aggressive` command with the current `ToggleAirborneMode` command, and
  calls `validate_command_item`. Assert that the produced errors include
  `Dragon Horn command wheel must contain Aggressive instead of ToggleAirborneMode`.

- [ ] **Step 2: Run the test to verify it fails**

  Run: `python -m unittest scripts.tests.test_validate_assets`

  Expected: FAIL because the validator currently accepts the obsolete command
  wheel.

- [ ] **Step 3: Implement the minimal validator contract**

  Extend `validate_command_item` with these executable checks:

  ```python
  expected_ids = {
      "Follow", "Hold", "Recall", "MoveToPing", "Defend", "Aggressive",
      "AttackTarget", "Idle",
  }
  if set(commands) != expected_ids:
      fail(errors, "Dragon Horn command wheel must contain Aggressive instead of ToggleAirborneMode")
  aggressive = commands.get("Aggressive", {})
  if aggressive.get("Steps") != [{"Type": "SetState", "State": "Aggressive"}]:
      fail(errors, "Dragon Horn Aggressive must enter the hostility-only Aggressive state")
  ```

  Add `Aggressive: SFX_HyDragon_Dragon_Flute_SE_05` to the existing feedback
  contract and require the two HyDragon localization keys through the existing
  locale catalog validation.

- [ ] **Step 4: Run the test and the validator**

  Run: `python -m unittest scripts.tests.test_validate_assets && python scripts/validate_assets.py`

  Expected: the unit test passes; asset validation fails against the still
  obsolete Horn configuration, proving the new contract detects the missing
  feature.

- [ ] **Step 5: Commit the validator test and contract**

  ```bash
  git add scripts/validate_assets.py scripts/tests/test_validate_assets.py
  git commit -m "Test: enforce Dragon Horn aggressive contract"
  ```

### Task 2: Replace the radial toggle with localized aggressive behavior

**Files:**

- Modify: `src/main/resources/Server/Tamework/Items/Commands/HyDragonDragonHorn.json:85-139`
- Modify: `src/main/resources/Server/Languages/en-US/server.lang:278-281`
- Modify: `src/main/resources/Server/Languages/de-DE/server.lang:278-281`
- Modify: `src/main/resources/Server/Languages/es-ES/server.lang:278-281`
- Modify: `src/main/resources/Server/Languages/fr-FR/server.lang:278-281`
- Modify: `src/main/resources/Server/Languages/pt-BR/server.lang:278-281`

**Interfaces:**

- Consumes: Tamework command-step `SetState` and the existing flute sound
  event `SFX_HyDragon_Dragon_Flute_SE_05`.
- Produces: an eight-command radial wheel containing `Aggressive` and no
  `ToggleAirborneMode` command.

- [ ] **Step 1: Run the validator to verify the production configuration is red**

  Run: `python scripts/validate_assets.py`

  Expected: FAIL with the aggressive-command-wheel error from Task 1.

- [ ] **Step 2: Replace the command**

  Replace the `ToggleAirborneMode` object with:

  ```json
  {
    "Id": "Aggressive",
    "DisplayName": "hydragon.commands.aggressive.name",
    "Feedback": {
      "HudMessage": "hydragon.commands.aggressive.hud",
      "SoundEvent": "SFX_HyDragon_Dragon_Flute_SE_05"
    },
    "Steps": [
      { "Type": "SetState", "State": "Aggressive" }
    ]
  }
  ```

  Add the two `hydragon.commands.aggressive.*` keys to every shipped locale.
  Remove the two obsolete Horn-toggle keys from every locale, leaving the
  companion-card flight toggle configuration untouched.

- [ ] **Step 3: Run the validator to verify the wheel is green**

  Run: `python scripts/validate_assets.py`

  Expected: the command-wheel and locale checks pass; any remaining failure
  identifies the unimplemented ground/flying aggressive wiring from Task 3.

- [ ] **Step 4: Commit the radial-menu change**

  ```bash
  git add src/main/resources/Server/Tamework/Items/Commands/HyDragonDragonHorn.json src/main/resources/Server/Languages
  git commit -m "Feat: add Dragon Horn aggressive command"
  ```

### Task 3: Wire grounded and flying aggressive paths

**Files:**

- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon_Tamed.json:1579-1665`
- Modify: `src/main/resources/Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json:518-650`
- Modify: `scripts/validate_assets.py:536-670,1249-1310`

**Interfaces:**

- Consumes: existing `CanAggressive`, `AirborneMode`, `Walk`, `Fly`, and
  `Component_Tamework_Instruction_Aggressive` contracts.
- Produces: one grounded and one flying aggressive instruction route for full
  dragons and Miniwyverns, while leaving ground-only templates and Defend's
  locked-target behavior unchanged.

- [ ] **Step 1: Extend the failing validator test for movement routes**

  Add a test fixture that removes the flying `Aggressive` branch from the
  Miniwyvern template and assert that the validator reports
  `Miniwyvern aggressive state must have grounded and flying paths`.

- [ ] **Step 2: Run the focused test to verify it fails**

  Run: `python -m unittest scripts.tests.test_validate_assets.ValidatorContractTest.test_rejects_missing_flying_aggressive_path`

  Expected: FAIL because the validator does not yet inspect aggressive path
  state/motion pairs.

- [ ] **Step 3: Implement the route validator and asset branches**

  Add a narrow recursive helper in `scripts/validate_assets.py` that recognizes
  an `Aggressive` state instruction containing both exact pairs:

  ```python
  ("AirborneMode", False, "Walk")
  ("AirborneMode", True, "Fly")
  ```

  Apply it to `Template_HyDragon_Dragon_Tamed.json` and
  `Template_Wyvern_Mini_Flying_Tamed.json`. In each template, split the
  aggressive behavior into two `AirborneMode` + motion-controller-gated
  branches that reference `Component_Tamework_Instruction_Aggressive`. Keep
  the established aggressive-component field set in each branch; the
  `AirborneMode` and controller gates select the matching path. Do not alter
  the ground-only
  `Template_HyDragon_Tamed.json` or any `Defend` state sensor/component.

- [ ] **Step 4: Run the focused test and static asset validation**

  Run: `python -m unittest scripts.tests.test_validate_assets && python scripts/validate_assets.py`

  Expected: PASS; all JSON assets parse, all locales resolve, the Horn has the
  aggressive command, and both flying templates contain grounded/flying
  aggressive paths.

- [ ] **Step 5: Commit the wiring**

  ```bash
  git add src/main/resources/Server/NPC/Roles/Creature/HyDragon/Templates scripts/validate_assets.py scripts/tests/test_validate_assets.py
  git commit -m "Feat: support aggressive flight paths"
  ```

### Task 4: Run the project verification suite

**Files:**

- Verify only: modified files from Tasks 1-3

**Interfaces:**

- Consumes: completed asset validator and Gradle project configuration.
- Produces: build evidence for the packaged HyDragon asset set.

- [ ] **Step 1: Run the focused asset test and validator**

  Run: `python -m unittest scripts.tests.test_validate_assets && python scripts/validate_assets.py`

  Expected: PASS with zero validator errors.

- [ ] **Step 2: Run Java tests and Gradle check**

  Run: `bash ./gradlew test check`

  Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect the final diff**

  Run: `git diff HEAD~3..HEAD --check && git status --short`

  Expected: no whitespace errors and no uncommitted files.
