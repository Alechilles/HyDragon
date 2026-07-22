package com.alechilles.hydragon.diagnostics;

import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.HyDragonMessages;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.integration.TameworkRuntimeDiagnostics;
import com.hypixel.hytale.server.core.Message;
import java.util.ArrayList;
import java.util.List;

/** Stable, compact operator-facing readiness report. */
public final class HyDragonStatusFormatter {
    private HyDragonStatusFormatter() {
    }

    public static List<String> format(
            String pluginVersion,
            HyDragonConfigRepository.Snapshot config,
            List<String> lastReloadIssues,
            TameworkBridge.Snapshot tamework,
            TameworkRuntimeDiagnostics.Snapshot diagnostics,
            HyDragonPersistenceStatus localPersistence) {
        List<String> lines = new ArrayList<>();
        lines.add("HyDragon " + pluginVersion + " status");
        lines.add("Config: " + (config.isValid() ? "READY" : "INVALID")
                + "; species=" + config.species().size()
                + ", archetypes=" + config.archetypes().size()
                + ", encounters=" + config.encounters().size()
                + ", issues=" + config.issues().size());
        for (String issue : config.issues().stream().limit(5).toList()) {
            lines.add("  config: " + issue);
        }
        if (config.issues().size() > 5) {
            lines.add("  config: ... and " + (config.issues().size() - 5) + " more");
        }
        if (config.isValid() && !lastReloadIssues.isEmpty()) {
            lines.add("Last config reload: REJECTED; retained=READY; issues=" + lastReloadIssues.size());
            for (String issue : lastReloadIssues.stream().limit(5).toList()) {
                lines.add("  rejected reload: " + issue);
            }
            if (lastReloadIssues.size() > 5) {
                lines.add("  rejected reload: ... and " + (lastReloadIssues.size() - 5) + " more");
            }
        }

        lines.add("Tamework: required=" + TameworkBridge.REQUIRED_TAMEWORK_RANGE
                + "; Public API=" + tamework.apiVersion()
                + "; capabilities=" + tamework.capabilities().size());
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            FeatureGate gate = tamework.feature(feature);
            lines.add("  " + feature + ": " + (gate.available() ? "READY" : "DISABLED — " + gate.reason()));
        }
        lines.add("Tamework persistence: " + diagnostics.persistenceStatus()
                + "; queue=" + diagnostics.queueDepth()
                + "; population=" + diagnostics.populationReadiness()
                + "; resilience=" + diagnostics.resilienceState());
        if (!diagnostics.available() && diagnostics.persistenceReason() != null) {
            lines.add("  diagnostics: " + diagnostics.persistenceReason());
        }
        lines.add("HyDragon persistence: "
                + (!localPersistence.available() ? "UNAVAILABLE"
                : localPersistence.writable() ? "READ_WRITE" : "READ_ONLY")
                + "; players=" + localPersistence.players()
                + ", profiles=" + localPersistence.profiles()
                + ", encounters=" + localPersistence.encounters()
                + ", pendingProfileProjections=" + localPersistence.pendingProfileProjections()
                + ", quarantined=" + localPersistence.quarantined()
                + ", reconcile=" + localPersistence.pendingReconciliation()
                + ", orphanedLinks=" + localPersistence.orphanedLinks().size());
        for (HyDragonPersistenceStatus.OrphanedLink orphan : localPersistence.orphanedLinks().stream().limit(5).toList()) {
            lines.add("  orphan[" + orphan.kind() + "]: " + orphan.identity()
                    + "; action=" + orphan.operatorAction());
        }
        if (localPersistence.orphanedLinks().size() > 5) {
            lines.add("  orphan: ... and " + (localPersistence.orphanedLinks().size() - 5) + " more");
        }
        if (localPersistence.reason() != null) {
            lines.add("  local persistence: " + localPersistence.reason());
        }
        return List.copyOf(lines);
    }

    /** Localized player-facing equivalent of the structured operator report. */
    public static List<Message> formatMessages(
            String pluginVersion,
            HyDragonConfigRepository.Snapshot config,
            List<String> lastReloadIssues,
            TameworkBridge.Snapshot tamework,
            TameworkRuntimeDiagnostics.Snapshot diagnostics,
            HyDragonPersistenceStatus localPersistence) {
        List<Message> messages = new ArrayList<>();
        messages.add(HyDragonMessages.statusTitle(pluginVersion));
        messages.add(HyDragonMessages.statusConfig(
                config.isValid() ? HyDragonMessages.statusStateReady() : HyDragonMessages.statusStateInvalid(),
                config.species().size(), config.archetypes().size(), config.encounters().size(), config.issues().size()));
        appendIssues(messages, config.issues());
        if (config.isValid() && !lastReloadIssues.isEmpty()) {
            messages.add(HyDragonMessages.statusRejectedReload(lastReloadIssues.size()));
            appendIssues(messages, lastReloadIssues);
        }

        messages.add(HyDragonMessages.statusTamework(
                TameworkBridge.REQUIRED_TAMEWORK_RANGE, tamework.apiVersion(), tamework.capabilities().size()));
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            FeatureGate gate = tamework.feature(feature);
            messages.add(HyDragonMessages.statusFeature(
                    feature.name(),
                    gate.available() ? HyDragonMessages.statusStateReady() : HyDragonMessages.statusStateDisabled(),
                    gate.reason()));
        }
        messages.add(HyDragonMessages.statusTameworkPersistence(
                diagnostics.persistenceStatus(), diagnostics.queueDepth(),
                diagnostics.populationReadiness(), diagnostics.resilienceState()));
        if (!diagnostics.available() && diagnostics.persistenceReason() != null) {
            messages.add(HyDragonMessages.statusDiagnosticsIssue(diagnostics.persistenceReason()));
        }

        Message localState = !localPersistence.available()
                ? HyDragonMessages.statusStateUnavailable()
                : localPersistence.writable()
                        ? HyDragonMessages.statusStateReadWrite()
                        : HyDragonMessages.statusStateReadOnly();
        messages.add(HyDragonMessages.statusLocalPersistence(
                localState,
                localPersistence.players(),
                localPersistence.profiles(),
                localPersistence.encounters(),
                localPersistence.pendingProfileProjections(),
                localPersistence.quarantined(),
                localPersistence.pendingReconciliation(),
                localPersistence.orphanedLinks().size()));
        for (HyDragonPersistenceStatus.OrphanedLink orphan
                : localPersistence.orphanedLinks().stream().limit(5).toList()) {
            messages.add(HyDragonMessages.statusOrphan(
                    orphan.kind(), orphan.identity(), orphan.operatorAction()));
        }
        if (localPersistence.orphanedLinks().size() > 5) {
            messages.add(HyDragonMessages.statusOrphanMore(localPersistence.orphanedLinks().size() - 5));
        }
        if (localPersistence.reason() != null) {
            messages.add(HyDragonMessages.statusLocalPersistenceIssue(localPersistence.reason()));
        }
        return List.copyOf(messages);
    }

    private static void appendIssues(List<Message> messages, List<String> issues) {
        for (String issue : issues.stream().limit(5).toList()) {
            messages.add(HyDragonMessages.statusConfigIssue(issue));
        }
        if (issues.size() > 5) {
            messages.add(HyDragonMessages.statusConfigMore(issues.size() - 5));
        }
    }
}
