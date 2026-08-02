# Toxic Hydra Lingering Cloud Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Toxic Hydra impact clouds disappear after 30 seconds while damaging and reapplying poison to entities inside them.

**Architecture:** A new Hydra-only particle system inherits the vanilla poison system and supplies its missing `LifeSpan`. Both toxic projectile launch interactions use Tamework's existing `LingeringHazard` setting for independent server-authoritative damage and `Poison_T1` application.

**Tech Stack:** Hytale JSON particle/projectile assets, Tamework 3.0.0 custom projectile interaction, Java 25, Gson, JUnit 5, Maven.

## Global Constraints

- Do not modify vanilla `Effect_Poison` or any Ice Hydra asset.
- Use `HyDragon_Hydra_Toxic_Cloud` with `LifeSpan: 30.0` for only Toxic Hydra impact/miss clouds.
- Both hazards tick every 1.0 second, deal 5.0 damage per tick, exclude their source, and apply `Poison_T1`.
- The direct projectile hazard radius is 3.0; the rain projectile hazard radius is 4.0.

---

### Task 1: Cover the intended cloud and hazard contracts

**Files:**
- Modify: `src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java`
- Modify: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Launch.json`
- Modify: `Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Launch.json`
- Create: `Server/Particles/HyDragon/Hydra/HyDragon_Hydra_Toxic_Cloud.particlesystem`
- Modify: `Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json`
- Modify: `Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Toxic_Ball.json`

**Interfaces:**
- Consumes: `TameworkLaunchProjectile.LingeringHazard` properties `Radius`, `DurationSeconds`, `TickIntervalSeconds`, `DamagePerTick`, `ExcludeSource`, `EffectId`, and `SourceTypeId`.
- Produces: two 30-second, server-authoritative poison hazards and finite impact/miss cloud visuals.

- [ ] **Step 1: Write the failing test**

Add a focused `toxicLingeringCloudsExpireAndApplyPoison()` test which loads the cloud system and both launch assets. It must assert:

```java
assertEquals("Effect_Poison", cloud.get("Parent").getAsString());
assertEquals(30.0, cloud.get("LifeSpan").getAsDouble());
assertLingeringHazard(direct, 3.0, "hydragon.toxic_hydra_hazard");
assertLingeringHazard(rain, 4.0, "hydragon.rain_toxic_hazard");
```

`assertLingeringHazard` must also require duration `30.0`, tick interval `1.0`, damage `5.0`, `ExcludeSource: true`, and `EffectId: "Poison_T1"`. Extend `assertProjectilePresentation` to require `HyDragon_Hydra_Toxic_Cloud` for `DeathParticles` and `MissParticles`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dexec.skip=true -Dtest=ToxicHydraVariantAssetTest test`

Expected: the new test fails because the particle asset and/or 30-second direct hazard configuration is absent.

- [ ] **Step 3: Write minimal asset implementation**

Create the cloud asset:

```json
{
  "Parent": "Effect_Poison",
  "LifeSpan": 30.0
}
```

Replace only the toxic projectiles' `DeathParticles.SystemId` and `MissParticles.SystemId` with `HyDragon_Hydra_Toxic_Cloud`. Add a direct-shot `LingeringHazard` and update the rain hazard duration to 30 seconds:

```json
{
  "Radius": 3.0,
  "DurationSeconds": 30.0,
  "TickIntervalSeconds": 1.0,
  "DamagePerTick": 5.0,
  "ExcludeSource": true,
  "EffectId": "Poison_T1",
  "SourceTypeId": "hydragon.toxic_hydra_hazard"
}
```

Use radius `4.0` and `SourceTypeId` `hydragon.rain_toxic_hazard` for rain.

- [ ] **Step 4: Run focused verification**

Run: `./mvnw -q -Dexec.skip=true -Dtest=ToxicHydraVariantAssetTest test`

Expected: PASS.

- [ ] **Step 5: Run asset verification**

Run: `python scripts/validate_assets.py`

Expected: no failures caused by the new cloud or toxic-hydra assets; report pre-existing unrelated failures exactly if present.

- [ ] **Step 6: Commit**

```bash
git add Server/Particles/HyDragon/Hydra/HyDragon_Hydra_Toxic_Cloud.particlesystem \
  Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json \
  Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Toxic_Ball.json \
  Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Launch.json \
  Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Launch.json \
  src/test/java/com/alechilles/hydragon/config/ToxicHydraVariantAssetTest.java
git commit -m "Feat: add toxic hydra lingering poison clouds"
```
