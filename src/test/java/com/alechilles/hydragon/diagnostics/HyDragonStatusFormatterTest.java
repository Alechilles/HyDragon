package com.alechilles.hydragon.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonDiagnosticText;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.integration.TameworkRuntimeDiagnostics;
import com.hypixel.hytale.server.core.Message;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HyDragonStatusFormatterTest {
    @Test
    void reportsEachFeatureAndItsStableDisableReason() {
        HyDragonConfigRepository.Snapshot config = new HyDragonConfigRepository.Snapshot(
                Map.of(), Map.of(), Map.of(), List.of("missing bundled assets"));
        Map<HyDragonFeature, FeatureGate> gates = new EnumMap<>(HyDragonFeature.class);
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            boolean diagnostics = feature == HyDragonFeature.TAMEWORK_DIAGNOSTICS;
            gates.put(feature, new FeatureGate(
                    feature,
                    diagnostics,
                    diagnostics ? Set.of("DIAGNOSTICS") : Set.of("COMMAND_FAMILY_ROSTERS"),
                    diagnostics ? Set.of() : Set.of("COMMAND_FAMILY_ROSTERS"),
                    List.of()));
        }
        TameworkBridge.Snapshot bridge = new TameworkBridge.Snapshot("0.8.0", Set.of("DIAGNOSTICS"), gates, null);
        TameworkRuntimeDiagnostics.Snapshot diagnostics = new TameworkRuntimeDiagnostics.Snapshot(
                true, "HEALTHY", null, 0, "READY", "COMPLETE", "READ_WRITE", null);

        HyDragonPersistenceStatus localPersistence = new HyDragonPersistenceStatus(
                true, true, 1, 1, 0, 3, 0, 5,
                List.of(new HyDragonPersistenceStatus.OrphanedLink(
                        "SOUL_BOND_PROFILE_MISSING",
                        "player=11111111-1111-1111-1111-111111111111, profile=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        "restore the matching profile extension or quarantine the Soul Bond record")),
                null);
        List<String> lines = HyDragonStatusFormatter.format(
                "1.0.0", config, config.issues(), bridge, diagnostics, localPersistence);

        assertTrue(lines.stream().anyMatch(line -> line.equals("HyDragon 1.0.0 status")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("required=>=4.0.0")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Config: INVALID")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("missing bundled assets")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("CAPTURE_AND_ROSTER: DISABLED")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("TAMEWORK_DIAGNOSTICS: READY")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Tamework persistence: HEALTHY")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("HyDragon persistence: READ_WRITE")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("pendingProfileProjections=3")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("orphan[SOUL_BOND_PROFILE_MISSING]")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("action=restore the matching profile extension")));
    }

    @Test
    void reportsRejectedReloadIssuesWhileRetainingValidSnapshot() {
        HyDragonConfigRepository.Snapshot config = new HyDragonConfigRepository.Snapshot(
                Map.of(), Map.of(), Map.of(), List.of());
        Map<HyDragonFeature, FeatureGate> gates = new EnumMap<>(HyDragonFeature.class);
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            gates.put(feature, new FeatureGate(
                    feature, true, feature.requiredCapabilities(), Set.of(), List.of()));
        }
        TameworkBridge.Snapshot bridge = new TameworkBridge.Snapshot("0.9.0", Set.of(), gates, null);
        TameworkRuntimeDiagnostics.Snapshot diagnostics = new TameworkRuntimeDiagnostics.Snapshot(
                true, "HEALTHY", null, 0, "READY", "COMPLETE", "READ_WRITE", null);
        HyDragonPersistenceStatus persistence = new HyDragonPersistenceStatus(
                true, true, 0, 0, 0, 0, 0, 0, List.of(), null);
        List<String> rejectedIssues = List.of("Encounter[storm]: TargetSpeciesId is missing");

        List<String> lines = HyDragonStatusFormatter.format(
                "1.0.0", config, rejectedIssues, bridge, diagnostics, persistence);
        List<Message> messages = HyDragonStatusFormatter.formatMessages(
                "1.0.0", config, rejectedIssues, bridge, diagnostics, persistence);

        assertTrue(lines.stream().anyMatch(line -> line.contains("Last config reload: REJECTED")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("TargetSpeciesId is missing")));
        assertTrue(messages.stream().anyMatch(message ->
                "server.messages.status.rejectedReload".equals(message.getMessageId())));
        assertTrue(messages.stream().anyMatch(message ->
                "server.messages.status.configIssue".equals(message.getMessageId())));
        Message configIssue = messages.stream()
                .filter(message -> "server.messages.status.configIssue".equals(message.getMessageId()))
                .findFirst()
                .orElseThrow();
        assertEquals("server.messages.status.diagnostic.config.scoped",
                configIssue.getFormattedMessage().messageParams.get("issue").messageId);
        assertFalse(messages.stream().anyMatch(message -> message.getMessageId() == null));
    }

    @Test
    void translatesKnownNestedConfigDiagnostics() {
        for (ConfigDiagnosticCase testCase : knownConfigDiagnostics()) {
            Message translated = HyDragonDiagnosticText.configIssue(testCase.issue());

            assertEquals(testCase.expectedMessageId(), translated.getMessageId(), testCase.issue());
            if (testCase.nestedMessageId() != null) {
                Message nested = new Message(translated.getFormattedMessage().messageParams.get("reason"));
                assertEquals(testCase.nestedMessageId(), nested.getMessageId(), testCase.issue());
            }
        }

        Message between = HyDragonDiagnosticText.configIssue("Capture.Resistance must be between 0 and 1");
        assertEquals("0", ((com.hypixel.hytale.protocol.StringParamValue)
                between.getFormattedMessage().params.get("minimum")).value);
        assertEquals("1", ((com.hypixel.hytale.protocol.StringParamValue)
                between.getFormattedMessage().params.get("maximum")).value);
    }

    private static List<ConfigDiagnosticCase> knownConfigDiagnostics() {
        return List.of(
                new ConfigDiagnosticCase("Encounter[storm]: Unknown encounter phase: DAYBREAK",
                        "server.messages.status.diagnostic.config.scoped",
                        "server.messages.status.diagnostic.config.unknownEncounterPhase"),
                new ConfigDiagnosticCase("EssenceBondAura.Upgrades[2].Semantic is unknown: whorl",
                        "server.messages.status.diagnostic.config.scoped",
                        "server.messages.status.diagnostic.config.unknownSemantic"),
                new ConfigDiagnosticCase("EssenceBondAura.Upgrades[2].SiphonCooldownMs must be at least 3000 for a siphon",
                        "server.messages.status.diagnostic.config.scoped",
                        "server.messages.status.diagnostic.config.siphonCooldownMinimum"),
                new ConfigDiagnosticCase("Grounding.BuildupSourceIds contains unsupported source: Unknown_Source",
                        "server.messages.status.diagnostic.config.unsupportedValue", null),
                new ConfigDiagnosticCase("DragonSpecies[ember] references missing encounter storm",
                        "server.messages.status.diagnostic.config.referencesMissingEncounter", null),
                new ConfigDiagnosticCase("DragonSpecies[ember] references encounter storm targeting frost",
                        "server.messages.status.diagnostic.config.referencesEncounterTarget", null),
                new ConfigDiagnosticCase("MiniwyvernArchetypes has duplicate RoleId Tamed_Wyvern_Mini_Fire for fire and toxic",
                        "server.messages.status.diagnostic.config.duplicateRoleId", null),
                new ConfigDiagnosticCase("Grounding.BuildupSourceIds contains duplicate source: Lure",
                        "server.messages.status.diagnostic.config.duplicateSource", null),
                new ConfigDiagnosticCase("WildRoleIds contains a blank role",
                        "server.messages.status.diagnostic.config.blankRole", null),
                new ConfigDiagnosticCase("ParticleAndSoundIds cannot contain blank values",
                        "server.messages.status.diagnostic.config.blankValues", null),
                new ConfigDiagnosticCase("RegionsAndAltitude requires finite MinY <= MaxY",
                        "server.messages.status.diagnostic.config.finiteAltitudeRange", null),
                new ConfigDiagnosticCase("Capture.Resistance must be between 0 and 1",
                        "server.messages.status.diagnostic.config.between", null),
                new ConfigDiagnosticCase("SiphonCooldownMs must be at least 3000 for a siphon",
                        "server.messages.status.diagnostic.config.siphonCooldownMinimum", null),
                new ConfigDiagnosticCase("Mount.Mode must be NONE, GROUND, or AVATAR_FLIGHT",
                        "server.messages.status.diagnostic.config.mountModeOptions", null),
                new ConfigDiagnosticCase("WeatherPredicate.Mode must be AnyOf or AllOf",
                        "server.messages.status.diagnostic.config.weatherModeOptions", null));
    }

    private record ConfigDiagnosticCase(String issue, String expectedMessageId, String nestedMessageId) {
    }
}
