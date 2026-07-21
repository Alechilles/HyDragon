package com.alechilles.hydragon.integration;

import com.alechilles.alecstamework.api.DiagnosticsApi;
import com.alechilles.alecstamework.api.PersistenceDiagnosticsView;
import com.alechilles.alecstamework.api.PersistenceResilienceView;
import com.alechilles.alecstamework.api.PopulationDiagnosticsView;
import com.alechilles.alecstamework.api.TameworkApi;
import javax.annotation.Nonnull;

/** Reads bounded Tamework diagnostics outside world-tick callbacks. */
public final class TameworkRuntimeDiagnostics {
    private TameworkRuntimeDiagnostics() {
    }

    @Nonnull
    public static Snapshot read(TameworkBridge bridge) {
        FeatureGate gate = bridge.snapshot().feature(HyDragonFeature.TAMEWORK_DIAGNOSTICS);
        TameworkApi api = bridge.api();
        if (!gate.available() || api == null) {
            return Snapshot.unavailable(gate.reason());
        }
        try {
            DiagnosticsApi diagnostics = api.diagnostics();
            PersistenceDiagnosticsView persistence = diagnostics.getPersistenceDiagnostics();
            PopulationDiagnosticsView population = diagnostics.getPopulationDiagnostics();
            PersistenceResilienceView resilience = diagnostics.getPersistenceResilience();
            return new Snapshot(
                    true,
                    persistence.health().status(),
                    persistence.health().reason(),
                    persistence.queueMetrics().queueDepth(),
                    population.readiness().ownerGlobal(),
                    population.reconciliation().state(),
                    resilience.storageState(),
                    resilience.storageReason()
            );
        } catch (RuntimeException | LinkageError failure) {
            return Snapshot.unavailable("diagnostics read failed: " + failure.getClass().getSimpleName());
        }
    }

    public record Snapshot(
            boolean available,
            String persistenceStatus,
            String persistenceReason,
            int queueDepth,
            String populationReadiness,
            String populationReconciliation,
            String resilienceState,
            String resilienceReason) {
        private static Snapshot unavailable(String reason) {
            return new Snapshot(false, "UNAVAILABLE", reason, -1, "UNAVAILABLE", "UNKNOWN", "READ_ONLY", reason);
        }
    }
}
