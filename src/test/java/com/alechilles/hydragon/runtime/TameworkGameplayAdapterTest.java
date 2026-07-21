package com.alechilles.hydragon.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.*;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TameworkGameplayAdapterTest {
    @Test
    void attunementNamesTheExactMissingAtomicProfileDataCall() {
        TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(api(
                EnumSet.of(TameworkApiCapability.PROFILE_DATA)));

        TameworkGameplayAdapter.Readiness readiness = adapter.attunementReadiness();

        assertFalse(readiness.ready());
        assertTrue(readiness.reason().contains("compareAndSet"));
        assertTrue(readiness.reason().contains("findOperation"));
    }

    @Test
    void onlyTerminalDeniedProvesRepairRefundIsSafe() {
        BondedVesselOperationView terminal = operation(BondedVesselDurableOperationStatus.TERMINAL_DENIED);
        BondedVesselOperationView applied = operation(BondedVesselDurableOperationStatus.APPLIED);

        assertTrue(TameworkGameplayAdapter.provesTerminalPreApplyDenial(Optional.of(terminal)));
        assertFalse(TameworkGameplayAdapter.provesTerminalPreApplyDenial(Optional.of(applied)));
        assertFalse(TameworkGameplayAdapter.provesTerminalPreApplyDenial(Optional.empty()));
    }

    private static BondedVesselOperationView operation(BondedVesselDurableOperationStatus status) {
        return new BondedVesselOperationView(
                UUID.randomUUID(), "Alechilles:HyDragon", "repair-key", status, "reason",
                UUID.randomUUID(), UUID.randomUUID().toString(), BondedVesselTransition.REPAIR_DEAD_TO_STORED,
                1L, 2L, 4L, null, false, 10L);
    }

    private static TameworkApi api(EnumSet<TameworkApiCapability> capabilities) {
        return new TameworkApi() {
            public String getApiVersion() { return "0.9.0"; }
            public EnumSet<TameworkApiCapability> getCapabilities() { return capabilities.clone(); }
            public NpcProfilesApi profiles() { return null; }
            public CommandLinksApi commandLinks() { return null; }
            public ProgressionApi progression() { return null; }
            public PolicyApi policies() { return null; }
            public InteractionExtensionApi interactionExtensions() { return null; }
            public TraitEffectApi traitEffects() { return null; }
            public ProfileDataApi profileData() { return null; }
            public TameworkEventsApi events() { return null; }
            public TameworkConfigReadApi configs() { return null; }
            public DiagnosticsApi diagnostics() { return null; }
        };
    }
}
