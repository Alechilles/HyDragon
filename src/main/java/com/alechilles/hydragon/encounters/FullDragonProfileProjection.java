package com.alechilles.hydragon.encounters;

import com.alechilles.alecstamework.api.CaptureAttemptOutcome;
import com.alechilles.alecstamework.api.CaptureAttemptResolvedEvent;
import com.alechilles.hydragon.config.DragonSpeciesConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.MutationOutcome;
import com.alechilles.hydragon.persistence.ProfileExtensionRecord;
import com.alechilles.hydragon.persistence.ProfileKind;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Projects a committed Tamework full-dragon capture into HyDragon's namespaced metadata. */
public final class FullDragonProfileProjection {
    private final HyDragonStateStore stateStore;
    private final Supplier<HyDragonConfigRepository.Snapshot> configs;

    public FullDragonProfileProjection(
            HyDragonStateStore stateStore,
            Supplier<HyDragonConfigRepository.Snapshot> configs) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.configs = Objects.requireNonNull(configs, "configs");
    }

    /** Safe for at-least-once event delivery; ambiguous or malformed capture evidence fails closed. */
    public Result project(CaptureAttemptResolvedEvent event) {
        if (event == null || event.outcome() != CaptureAttemptOutcome.CAPTURED) return Result.IGNORED;
        if (event.profileId() == null || event.profileId().isBlank()) return Result.INVALID;

        UUID profileId;
        try {
            profileId = UUID.fromString(event.profileId());
        } catch (IllegalArgumentException failure) {
            return Result.INVALID;
        }

        HyDragonConfigRepository.Snapshot snapshot = configs.get();
        if (snapshot == null || !snapshot.isValid()) return Result.UNAVAILABLE;
        List<DragonSpeciesConfig> matches = snapshot.species().values().stream()
                .filter(species -> species.getWildRoleIds().contains(event.roleId()))
                .toList();
        if (matches.size() != 1) return Result.AMBIGUOUS;

        String speciesId = matches.getFirst().getId();
        String operationId = event.operationId().toString();
        ProfileExtensionRecord current = stateStore.snapshot().profileExtensions().get(profileId);
        if (current != null) {
            boolean exactDomainIdentity = current.kind() == ProfileKind.FULL_DRAGON
                    && current.speciesId().equals(speciesId);
            if (!exactDomainIdentity) return Result.CONFLICT;
            return current.lastOperationId().filter(operationId::equals).isPresent()
                    ? Result.ALREADY_APPLIED : Result.CONFLICT;
        }

        try {
            MutationOutcome outcome = stateStore.putProfileExtension(ProfileExtensionRecord.fullDragon(
                    profileId, speciesId, Optional.of(operationId)));
            return switch (outcome) {
                case APPLIED -> Result.APPLIED;
                case ALREADY_APPLIED -> Result.ALREADY_APPLIED;
                case CONFLICT, QUARANTINED -> Result.CONFLICT;
                case STORE_READ_ONLY -> Result.UNAVAILABLE;
            };
        } catch (IOException | RuntimeException failure) {
            return Result.UNAVAILABLE;
        }
    }

    public enum Result {
        APPLIED,
        ALREADY_APPLIED,
        IGNORED,
        INVALID,
        AMBIGUOUS,
        CONFLICT,
        UNAVAILABLE
    }
}
