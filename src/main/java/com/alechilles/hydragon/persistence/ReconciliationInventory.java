package com.alechilles.hydragon.persistence;

import java.util.List;
import java.util.Objects;

/** Immutable startup work list for reconciling local state with authoritative runtime systems. */
public record ReconciliationInventory(
        List<PlayerSoulBondRecord> soulBondsNeedingReconciliation,
        List<PlayerSoulBondRecord> claimedSoulBondsToVerify,
        List<ProfileExtensionRecord> profileExtensionsToVerify,
        List<EncounterRecord> encountersToResumeOrCleanUp,
        List<PendingProfileProjectionRecord> profileProjectionsToRetry,
        List<ConsumableTransactionRecord> consumableTransactionsToReconcile,
        List<QuarantinedRecord> quarantinedRecords) {
    public ReconciliationInventory {
        soulBondsNeedingReconciliation = List.copyOf(
                Objects.requireNonNull(soulBondsNeedingReconciliation, "soulBondsNeedingReconciliation"));
        claimedSoulBondsToVerify = List.copyOf(
                Objects.requireNonNull(claimedSoulBondsToVerify, "claimedSoulBondsToVerify"));
        profileExtensionsToVerify = List.copyOf(
                Objects.requireNonNull(profileExtensionsToVerify, "profileExtensionsToVerify"));
        encountersToResumeOrCleanUp = List.copyOf(
                Objects.requireNonNull(encountersToResumeOrCleanUp, "encountersToResumeOrCleanUp"));
        profileProjectionsToRetry = List.copyOf(
                Objects.requireNonNull(profileProjectionsToRetry, "profileProjectionsToRetry"));
        consumableTransactionsToReconcile = List.copyOf(
                Objects.requireNonNull(consumableTransactionsToReconcile, "consumableTransactionsToReconcile"));
        quarantinedRecords = List.copyOf(Objects.requireNonNull(quarantinedRecords, "quarantinedRecords"));
    }
}
