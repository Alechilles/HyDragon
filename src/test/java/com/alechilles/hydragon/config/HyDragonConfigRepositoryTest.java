package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HyDragonConfigRepositoryTest {
    @Test
    void acceptsCompleteCrossReferencedConfiguration() {
        DragonSpeciesConfig species = validSpecies();
        DragonEncounterConfig encounter = validEncounter();

        HyDragonConfigRepository.Snapshot snapshot = HyDragonConfigRepository.buildSnapshot(
                List.of(species), List.of(validMaintenance()), allArchetypes(), List.of(encounter));

        assertTrue(snapshot.isValid(), () -> String.join("\n", snapshot.issues()));
        assertTrue(snapshot.defaultMaintenance() != null);
    }

    @Test
    void rejectsMiniwyvernInOrdinaryCaptureSpecies() {
        DragonSpeciesConfig species = validSpecies();
        species.wildRoleIds = new String[]{"Wyvern_Mini"};
        species.tamedRoleIdByWildRole = Map.of("Wyvern_Mini", "Tamed_Wyvern_Mini");

        assertTrue(species.validate().stream().anyMatch(issue -> issue.contains("Soul Bond-exclusive")));
    }

    @Test
    void rejectsDeferredMaintenanceExtensions() {
        StoneMaintenanceConfig maintenance = validMaintenance();
        maintenance.futureExtensions.energyEnabled = true;

        assertTrue(maintenance.validate().stream().anyMatch(issue -> issue.contains("deferred")));
    }

    @Test
    void rejectsUnboundedVoidAbility() {
        MiniwyvernArchetypeConfig archetype = validArchetype("void");
        MiniwyvernArchetypeConfig.Ability ability = validVoidAbility();
        ability.minimumDefenseMultiplier = null;
        ability.maximumReduction = null;
        ability.stackingPolicy = "ADDITIVE";
        archetype.activeAbilities = new MiniwyvernArchetypeConfig.Ability[]{ability};

        List<String> issues = archetype.validate();
        assertTrue(issues.stream().anyMatch(issue -> issue.contains("MinimumDefenseMultiplier")));
        assertTrue(issues.stream().anyMatch(issue -> issue.contains("MaximumReduction")));
        assertTrue(issues.stream().anyMatch(issue -> issue.contains("stacking")));
    }

    @Test
    void rejectsBrokenSpeciesEncounterCrossReference() {
        DragonSpeciesConfig species = validSpecies();
        DragonEncounterConfig encounter = validEncounter();
        encounter.targetSpeciesId = "hydragon:some_other_dragon";

        HyDragonConfigRepository.Snapshot snapshot = HyDragonConfigRepository.buildSnapshot(
                List.of(species), List.of(validMaintenance()), allArchetypes(), List.of(encounter));

        assertFalse(snapshot.isValid());
        assertTrue(snapshot.issues().stream().anyMatch(issue -> issue.contains("targeting")));
        assertTrue(snapshot.issues().stream().anyMatch(issue -> issue.contains("missing species")));
    }

    @Test
    void invalidReloadRetainsLastKnownGoodSnapshotAndReportsCandidateIssues() {
        HyDragonConfigRepository repository = new HyDragonConfigRepository();
        HyDragonConfigRepository.Snapshot valid = HyDragonConfigRepository.buildSnapshot(
                List.of(validSpecies()), List.of(validMaintenance()), allArchetypes(), List.of(validEncounter()));
        DragonEncounterConfig brokenEncounter = validEncounter();
        brokenEncounter.targetSpeciesId = "hydragon:missing";
        HyDragonConfigRepository.Snapshot invalid = HyDragonConfigRepository.buildSnapshot(
                List.of(validSpecies()), List.of(validMaintenance()), allArchetypes(), List.of(brokenEncounter));

        assertTrue(repository.publishCandidate(valid));
        assertFalse(repository.publishCandidate(invalid));

        assertSame(valid, repository.snapshot());
        assertEquals(invalid.issues(), repository.lastReloadIssues());
    }

    private static DragonSpeciesConfig validSpecies() {
        DragonSpeciesConfig species = new DragonSpeciesConfig();
        species.id = "hydragon:nordic_drake";
        species.wildRoleIds = new String[]{"NordicDrake"};
        species.tamedRoleIdByWildRole = Map.of("NordicDrake", "Tamed_NordicDrake");
        species.difficultyId = "legendary";
        species.statsAndBehaviorAssetIds = new String[]{"CAE_NordicDrake"};
        species.dropListId = "Drop_NordicDrake";
        species.mount.mode = "AVATAR_FLIGHT";
        species.mount.avatarFlightConfigId = "HyDragonNordicDrake";
        species.capture.resistance = 0.25;
        species.capture.minimumStoneTier = 4;
        species.capture.maxHealthPercentOverride = 20.0;
        species.capture.specialRequirementIds = new String[]{"hydragon:special_encounter_capture_ready"};
        species.spawn.pluginEncounterIds = new String[]{"hydragon:nordic_drake_high_altitude"};
        species.presentation.localizationPrefix = "server.npcRoles.NordicDrake";
        species.presentation.modelIds = new String[]{"NordicDrake"};
        return species;
    }

    private static StoneMaintenanceConfig validMaintenance() {
        StoneMaintenanceConfig maintenance = new StoneMaintenanceConfig();
        maintenance.assetKey = "Default";
        maintenance.repair.itemId = "Revitalizing_Essence";
        maintenance.repair.quantity = 1;
        return maintenance;
    }

    private static DragonEncounterConfig validEncounter() {
        DragonEncounterConfig encounter = new DragonEncounterConfig();
        encounter.id = "hydragon:nordic_drake_high_altitude";
        encounter.targetSpeciesId = "hydragon:nordic_drake";
        encounter.regionsAndAltitude.environmentIds = new String[]{"Env_Zone3_Glacial"};
        encounter.regionsAndAltitude.minY = 180;
        encounter.regionsAndAltitude.maxY = 320;
        encounter.weatherPredicate.mode = "AnyOf";
        encounter.weatherPredicate.weatherIds = new String[]{"Zone3_Snow_Storm"};
        encounter.playerEligibility.activeCompanionGroup = "hydragon:full_dragons";
        encounter.playerEligibility.requiredMountMode = "AVATAR_FLIGHT";
        encounter.playerEligibility.requiredItemId = "Tamework_Flightmasters_Talisman";
        encounter.admission.chance = 0.08;
        encounter.admission.evaluationCooldownSeconds = 300;
        encounter.admission.perRegionLimit = 1;
        encounter.admission.globalLimit = 2;
        encounter.phases = new String[]{"AERIAL", "GROUNDING", "GROUNDED_CAPTURE_WINDOW"};
        encounter.grounding.buildupSourceIds = new String[]{"hydragon:lure_hit"};
        encounter.grounding.threshold = 100;
        encounter.grounding.groundedState = "Grounded";
        encounter.grounding.groundedEffectId = "HyDragon_NordicDrake_Grounded";
        encounter.grounding.captureWindowSeconds = 45;
        encounter.cleanupAndCooldown.encounterTimeoutSeconds = 900;
        encounter.cleanupAndCooldown.retryCooldownSeconds = 1800;
        encounter.cleanupAndCooldown.eligibilityGraceSeconds = 30;
        return encounter;
    }

    private static List<MiniwyvernArchetypeConfig> allArchetypes() {
        List<MiniwyvernArchetypeConfig> archetypes = new ArrayList<>();
        for (String id : List.of("neutral", "lightning", "wind", "ice", "fire", "water", "nature", "void")) {
            MiniwyvernArchetypeConfig archetype = validArchetype(id);
            if (id.equals("void")) {
                archetype.activeAbilities = new MiniwyvernArchetypeConfig.Ability[]{validVoidAbility()};
            } else if (id.equals("ice")) {
                MiniwyvernArchetypeConfig.Ability ability = validAbility("ice_buildup");
                ability.buildupPerHit = 25.0;
                ability.buildupThreshold = 100.0;
                ability.buildupCap = 100.0;
                ability.controlEffectId = "HyDragon_Miniwyvern_Ice_Stun";
                ability.controlImmunitySeconds = 12.0;
                archetype.activeAbilities = new MiniwyvernArchetypeConfig.Ability[]{ability};
            } else if (id.equals("water")) {
                MiniwyvernArchetypeConfig.Ability ability = validAbility("restorative_surge");
                ability.ownerHealthThreshold = 0.6;
                ability.maximumHealFraction = 0.2;
                archetype.activeAbilities = new MiniwyvernArchetypeConfig.Ability[]{ability};
            } else if (id.equals("fire")) {
                archetype.activeAbilities = new MiniwyvernArchetypeConfig.Ability[]{validAbility("fireball")};
            }
            archetypes.add(archetype);
        }
        return archetypes;
    }

    private static MiniwyvernArchetypeConfig validArchetype(String id) {
        MiniwyvernArchetypeConfig archetype = new MiniwyvernArchetypeConfig();
        archetype.id = id;
        if (!id.equals("neutral")) {
            archetype.essenceSemanticId = id;
            archetype.essenceItemId = "Draconic_Essence_" + id;
        }
        archetype.appearanceId = "Wyvern_Mini_" + id;
        archetype.fallbackBehavior = "BASIC_BITE";
        if (id.equals("lightning")) {
            archetype.passiveModifiers = Map.of(
                    "MovementSpeedMultiplier", 1.15,
                    "ActionSpeedMultiplier", 1.10);
        } else if (id.equals("wind")) {
            archetype.passiveModifiers = Map.of(
                    "MovementSpeedMultiplier", 1.12,
                    "JumpMultiplier", 1.15,
                    "MobilityMultiplier", 1.10,
                    "MaximumMovementSpeedMultiplier", 1.20,
                    "MaximumJumpMultiplier", 1.25);
        } else if (id.equals("nature")) {
            archetype.passiveModifiers = Map.of(
                    "RegenerationTickSeconds", 2.0,
                    "MaximumHealFractionPerTick", 0.01);
        }
        return archetype;
    }

    private static MiniwyvernArchetypeConfig.Ability validAbility(String id) {
        MiniwyvernArchetypeConfig.Ability ability = new MiniwyvernArchetypeConfig.Ability();
        ability.id = id;
        ability.trigger = "COMBAT_INTERVAL";
        ability.targetPolicy = "OWNER_HOSTILE_ONLY";
        ability.range = 18.0;
        ability.cooldownSeconds = 4.0;
        ability.effectId = "HyDragon_Test_Effect";
        ability.magnitude = 1.0;
        ability.durationSeconds = 1.0;
        ability.stackingPolicy = "SOURCE_REFRESH";
        return ability;
    }

    private static MiniwyvernArchetypeConfig.Ability validVoidAbility() {
        MiniwyvernArchetypeConfig.Ability ability = new MiniwyvernArchetypeConfig.Ability();
        ability.id = "void_exposure";
        ability.trigger = "COMBAT_INTERVAL";
        ability.targetPolicy = "OWNER_HOSTILE_ONLY";
        ability.range = 18.0;
        ability.cooldownSeconds = 7.0;
        ability.effectId = "HyDragon_Miniwyvern_Void_Exposure";
        ability.projectileId = "Eye_Void_Blast";
        ability.magnitude = 0.12;
        ability.durationSeconds = 6.0;
        ability.stackingPolicy = "SOURCE_REFRESH";
        ability.minimumDefenseMultiplier = 0.5;
        ability.maximumReduction = 0.12;
        return ability;
    }
}
