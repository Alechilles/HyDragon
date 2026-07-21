package com.alechilles.hydragon.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One immutable, atomically published generation of HyDragon's local durable state. */
public record HyDragonStateSnapshot(
        int storeSchemaVersion,
        boolean writable,
        Map<UUID, PlayerSoulBondRecord> playerSoulBonds,
        Map<UUID, ProfileExtensionRecord> profileExtensions,
        Map<String, EncounterRecord> encounters,
        List<QuarantinedRecord> quarantinedRecords) {
    public HyDragonStateSnapshot {
        playerSoulBonds = Map.copyOf(Objects.requireNonNull(playerSoulBonds, "playerSoulBonds"));
        profileExtensions = Map.copyOf(Objects.requireNonNull(profileExtensions, "profileExtensions"));
        encounters = Map.copyOf(Objects.requireNonNull(encounters, "encounters"));
        quarantinedRecords = List.copyOf(Objects.requireNonNull(quarantinedRecords, "quarantinedRecords"));
    }

    public Optional<PlayerSoulBondRecord> playerSoulBond(UUID playerUuid) {
        return Optional.ofNullable(playerSoulBonds.get(Objects.requireNonNull(playerUuid, "playerUuid")));
    }

    public Optional<ProfileExtensionRecord> profileExtension(UUID profileId) {
        return Optional.ofNullable(profileExtensions.get(Objects.requireNonNull(profileId, "profileId")));
    }

    public Optional<EncounterRecord> encounter(String encounterId) {
        return Optional.ofNullable(encounters.get(Objects.requireNonNull(encounterId, "encounterId")));
    }

    /** Builds deterministic reconciliation work lists from this snapshot. */
    public ReconciliationInventory reconciliationInventory() {
        Comparator<PlayerSoulBondRecord> byPlayer = Comparator.comparing(record -> record.playerUuid().toString());
        List<PlayerSoulBondRecord> needsReconciliation = playerSoulBonds.values().stream()
                .filter(record -> record.state() == SoulBondState.PENDING
                        || record.state() == SoulBondState.NEEDS_RECONCILIATION)
                .sorted(byPlayer)
                .toList();
        List<PlayerSoulBondRecord> claimed = playerSoulBonds.values().stream()
                .filter(record -> record.state() == SoulBondState.CLAIMED)
                .sorted(byPlayer)
                .toList();
        List<ProfileExtensionRecord> profiles = profileExtensions.values().stream()
                .sorted(Comparator.comparing(record -> record.profileId().toString()))
                .toList();
        List<EncounterRecord> persistedEncounters = encounters.values().stream()
                .sorted(Comparator.comparing(EncounterRecord::encounterId))
                .toList();
        List<QuarantinedRecord> quarantined = new ArrayList<>(quarantinedRecords);
        quarantined.sort(Comparator.comparing((QuarantinedRecord record) -> record.type().name())
                .thenComparing(QuarantinedRecord::persistentId));
        return new ReconciliationInventory(
                needsReconciliation,
                claimed,
                profiles,
                persistedEncounters,
                quarantined);
    }
}
