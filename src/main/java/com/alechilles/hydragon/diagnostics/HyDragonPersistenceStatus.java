package com.alechilles.hydragon.diagnostics;

import com.alechilles.hydragon.persistence.HyDragonStateSnapshot;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ReconciliationInventory;
import com.alechilles.hydragon.persistence.PlayerSoulBondRecord;
import com.alechilles.hydragon.persistence.ProfileExtensionRecord;
import com.alechilles.hydragon.persistence.ProfileKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
        List<OrphanedLink> orphanedLinks,
        @Nullable String reason) {
    public HyDragonPersistenceStatus {
        orphanedLinks = List.copyOf(orphanedLinks);
    }

    public static HyDragonPersistenceStatus from(@Nullable HyDragonStateStore store, @Nullable String failure) {
        if (store == null) {
            return new HyDragonPersistenceStatus(false, false, 0, 0, 0, 0, 0, 0,
                    List.of(),
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
                findOrphanedLinks(snapshot),
                snapshot.writable() ? null : "unsupported store schema or quarantined store"
        );
    }

    private static List<OrphanedLink> findOrphanedLinks(HyDragonStateSnapshot snapshot) {
        List<OrphanedLink> links = new ArrayList<>();
        Set<UUID> claimedProfiles = new HashSet<>();
        for (PlayerSoulBondRecord bond : snapshot.playerSoulBonds().values()) {
            bond.profileId().ifPresent(profileId -> {
                claimedProfiles.add(profileId);
                if (!snapshot.profileExtensions().containsKey(profileId)) {
                    links.add(new OrphanedLink(
                            "SOUL_BOND_PROFILE_MISSING",
                            "player=" + bond.playerUuid() + ", profile=" + profileId,
                            "restore the matching profile extension or quarantine the Soul Bond record"));
                }
            });
        }
        for (ProfileExtensionRecord extension : snapshot.profileExtensions().values()) {
            if (extension.kind() == ProfileKind.SOULBOUND_MINIWYVERN
                    && !claimedProfiles.contains(extension.profileId())) {
                links.add(new OrphanedLink(
                        "MINIWYVERN_BOND_MISSING",
                        "profile=" + extension.profileId(),
                        "verify Tamework ownership, then reconcile or quarantine the profile extension"));
            }
        }
        links.sort(java.util.Comparator.comparing(OrphanedLink::kind).thenComparing(OrphanedLink::identity));
        return List.copyOf(links);
    }

    /** Identifiable, bounded-at-render-time evidence with a concrete operator response. */
    public record OrphanedLink(String kind, String identity, String operatorAction) {
        public OrphanedLink {
            kind = required(kind, "kind");
            identity = required(identity, "identity");
            operatorAction = required(operatorAction, "operatorAction");
        }

        private static String required(String value, String field) {
            String normalized = java.util.Objects.requireNonNull(value, field).trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
            return normalized;
        }
    }
}
