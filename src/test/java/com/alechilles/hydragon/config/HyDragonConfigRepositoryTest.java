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
                List.of(species), allArchetypes(), List.of(encounter));

        assertTrue(snapshot.isValid(), () -> String.join("\n", snapshot.issues()));
    }

    @Test
    void rejectsMiniwyvernInOrdinaryCaptureSpecies() {
        DragonSpeciesConfig species = validSpecies();
        species.wildRoleIds = new String[]{"Wyvern_Mini"};
        species.tamedRoleIdByWildRole = Map.of("Wyvern_Mini", "Tamed_Wyvern_Mini");

        assertTrue(species.validate().stream().anyMatch(issue -> issue.contains("Soul Bond-exclusive")));
    }

    @Test
    void rejectsModifierEffectMappingWithoutMatchingSemantic() {
        MiniwyvernArchetypeConfig lightning = validArchetype("lightning");
        lightning.passiveModifierEffects = Map.of("JumpMultiplier", "Some_Effect");

        List<String> issues = lightning.validate();
        assertTrue(issues.stream().anyMatch(issue -> issue.contains("does not name a configured")));
        assertTrue(issues.stream().anyMatch(issue -> issue.contains("MovementSpeedMultiplier is required")));
    }

    @Test
    void acceptsLightningSpeedAsItsOwnerPassiveEntityEffect() {
        MiniwyvernArchetypeConfig lightning = validArchetype("lightning");
        lightning.passiveModifiers = Map.of();
        lightning.passiveModifierEffects = Map.of();
        lightning.passiveEffects = new String[] { "HyDragon_Miniwyvern_Lightning_Boon" };
        assertTrue(lightning.validate().isEmpty());
    }

    @Test
    void rejectsBrokenSpeciesEncounterCrossReference() {
        DragonSpeciesConfig species = validSpecies();
        DragonEncounterConfig encounter = validEncounter();
        encounter.targetSpeciesId = "hydragon:some_other_dragon";

        HyDragonConfigRepository.Snapshot snapshot = HyDragonConfigRepository.buildSnapshot(
                List.of(species), allArchetypes(), List.of(encounter));

        assertFalse(snapshot.isValid());
        assertTrue(snapshot.issues().stream().anyMatch(issue -> issue.contains("targeting")));
        assertTrue(snapshot.issues().stream().anyMatch(issue -> issue.contains("missing species")));
    }

    @Test
    void invalidReloadRetainsLastKnownGoodSnapshotAndReportsCandidateIssues() {
        HyDragonConfigRepository repository = new HyDragonConfigRepository();
        HyDragonConfigRepository.Snapshot valid = HyDragonConfigRepository.buildSnapshot(
                List.of(validSpecies()), allArchetypes(), List.of(validEncounter()));
        DragonEncounterConfig brokenEncounter = validEncounter();
        brokenEncounter.targetSpeciesId = "hydragon:missing";
        HyDragonConfigRepository.Snapshot invalid = HyDragonConfigRepository.buildSnapshot(
                List.of(validSpecies()), allArchetypes(), List.of(brokenEncounter));

        assertTrue(repository.publishCandidate(valid));
        assertFalse(repository.publishCandidate(invalid));

        assertSame(valid, repository.snapshot());
        assertEquals(invalid.issues(), repository.lastReloadIssues());
        HyDragonConfigRepository.Snapshot reloaded = HyDragonConfigRepository.buildSnapshot(
                List.of(validSpecies()), allArchetypes(), List.of(validEncounter()));

        assertTrue(repository.publishCandidate(reloaded));
        assertSame(reloaded, repository.snapshot());
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
        species.capture.specialRequirementIds = new String[]{"hydragon:special_encounter_capture_ready"};
        species.spawn.pluginEncounterIds = new String[]{"hydragon:nordic_drake_high_altitude"};
        species.presentation.localizationPrefix = "server.npcRoles.NordicDrake";
        species.presentation.modelIds = new String[]{"NordicDrake"};
        return species;
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
        encounter.playerEligibility.requiredMountMode = "AVATAR_FLIGHT";
        encounter.playerEligibility.requiredItemId = "Tamework_Flightmasters_Talisman";
        encounter.admission.chance = 0.08;
        encounter.admission.evaluationCooldownSeconds = 300;
        encounter.admission.perRegionLimit = 1;
        encounter.admission.globalLimit = 2;
        encounter.phases = new String[]{"AERIAL", "GROUNDING", "GROUNDED_CAPTURE_WINDOW"};
        encounter.grounding.buildupSourceIds = new String[]{
                "projectile:Ice_Ball+item:Weapon_Staff_Frost",
                "projectile:Spear_Thorium+item:Weapon_Spear_Thorium"
        };
        encounter.grounding.threshold = 100;
        encounter.grounding.groundedState = "Combat.AirLand";
        encounter.grounding.groundedEffectId = "HyDragon_NordicDrake_Grounded";
        encounter.grounding.captureWindowSeconds = 45;
        encounter.cleanupAndCooldown.encounterTimeoutSeconds = 900;
        encounter.cleanupAndCooldown.retryCooldownSeconds = 1800;
        encounter.cleanupAndCooldown.eligibilityGraceSeconds = 30;
        return encounter;
    }

    private static List<MiniwyvernArchetypeConfig> allArchetypes() {
        List<MiniwyvernArchetypeConfig> archetypes = new ArrayList<>();
        for (String id : List.of("wild", "nature", "toxic", "fire", "void", "lightning", "ice")) {
            archetypes.add(validArchetype(id));
        }
        return archetypes;
    }

    private static MiniwyvernArchetypeConfig validArchetype(String id) {
        MiniwyvernArchetypeConfig archetype = new MiniwyvernArchetypeConfig();
        archetype.id = id;
        archetype.roleId = "Tamed_Wyvern_Mini_" + Character.toUpperCase(id.charAt(0)) + id.substring(1);
        archetype.fallbackBehavior = "BASIC_BITE";
        if (id.equals("lightning")) {
            archetype.passiveModifiers = Map.of(
                    "MovementSpeedMultiplier", 1.15);
            archetype.passiveModifierEffects = Map.of(
                    "MovementSpeedMultiplier", "HyDragon_Miniwyvern_Lightning_Boon");
        } else if (id.equals("nature")) {
            archetype.passiveModifiers = Map.of(
                    "RegenerationTickSeconds", 2.0,
                    "MaximumHealFractionPerTick", 0.01);
        }
        return archetype;
    }

}
