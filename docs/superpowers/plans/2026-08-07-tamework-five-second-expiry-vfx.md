# Tamework Five-Second Expiry VFX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Start configured bonded-companion expiry model effects at five seconds remaining instead of thirty seconds.

**Architecture:** Keep the existing warning schedule and effect-duration safety behavior. Change only the warning selected by `modelEffectId`, protected by the existing deterministic schedule test.

**Tech Stack:** Java 25, JUnit 5, Gradle, Tamework bonded-companion runtime.

## Global Constraints

- Work only in `C:/Users/22ale/AppData/Roaming/Hytale/Modding/alecstamework/.worktrees/hydragon-expiry-fade`.
- Preserve all notification thresholds and cleanup grace behavior.
- Do not change owner/avatar effect-target selection.

---

### Task 1: Select the configured model effect at five seconds

**Files:**
- Modify: `src/test/java/com/alechilles/alecstamework/companion/bonded/BondedCompanionExpiryWarningScheduleTest.java`
- Modify: `src/main/java/com/alechilles/alecstamework/companion/bonded/BondedCompanionExpiryWarningSchedule.java`

**Interfaces:**
- Consumes: `Warning(int secondsRemaining, NotificationStyle style)`.
- Produces: `modelEffectId(Warning, String)` returning the configured ID only when `secondsRemaining() == 5`.

- [ ] **Step 1: Change the behavior test first**

Rename the test to `selects_a_configured_model_effect_only_for_the_five_second_warning`, create five- and ten-second warnings, assert the configured effect is returned for five seconds, and assert it is empty for ten seconds and blank configuration.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew test --tests '*BondedCompanionExpiryWarningScheduleTest' --no-daemon`

Expected: the five-second selection assertion fails because production still checks for 30.

- [ ] **Step 3: Implement the minimal production change**

Change the `modelEffectId` guard from `warning.secondsRemaining() != 30` to `warning.secondsRemaining() != 5` and update its Javadoc to say five-second visual warning.

- [ ] **Step 4: Run focused and full verification**

Run the focused command again, then `./gradlew test --no-daemon`.

Expected: both commands exit 0.

- [ ] **Step 5: Commit**

Stage only the schedule source and test, then commit with `Fix: start expiry VFX at five seconds`.

