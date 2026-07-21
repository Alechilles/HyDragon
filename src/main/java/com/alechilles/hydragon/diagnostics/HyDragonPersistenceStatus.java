package com.alechilles.hydragon.diagnostics;

import com.alechilles.hydragon.persistence.HyDragonStateSnapshot;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ReconciliationInventory;
import javax.annotation.Nullable;

/** Sanitized HyDragon-owned persistence and current reconciliation counters. */
public record HyDragonPersistenceStatus(
        boolean available,
        boolean writable,
        int players,
        int profiles,
        int encounters,
        int pendingProfileProjections,
        int quarantined,
        int pendingReconciliation,
        @Nullable String reason) {
    public static HyDragonPersistenceStatus from(@Nullable HyDragonStateStore store, @Nullable String failure) {
        if (store == null) {
            return new HyDragonPersistenceStatus(false, false, 0, 0, 0, 0, 0, 0,
                    failure == null ? "state store not initialized" : failure);
        }
        HyDragonStateSnapshot snapshot = store.snapshot();
        ReconciliationInventory inventory = snapshot.reconciliationInventory();
        return new HyDragonPersistenceStatus(
                true,
                snapshot.writable(),
                snapshot.playerSoulBonds().size(),
                snapshot.profileExtensions().size(),
                snapshot.encounters().size(),
                snapshot.pendingProfileProjections().size(),
                snapshot.quarantinedRecords().size(),
                inventory.soulBondsNeedingReconciliation().size()
                        + inventory.claimedSoulBondsToVerify().size()
                        + inventory.profileExtensionsToVerify().size()
                        + inventory.encountersToResumeOrCleanUp().size()
                        + inventory.profileProjectionsToRetry().size()
                        + inventory.consumableTransactionsToReconcile().size(),
                snapshot.writable() ? null : "unsupported store schema or quarantined store"
        );
    }
}
