package com.alechilles.hydragon.runtime;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Fail-closed attunement boundary until API 0.9 gains revision-fenced profile-data mutations. */
public final class MiniwyvernAttunementService {
    private static final Set<String> ARCHETYPES = Set.of(
            "lightning", "wind", "ice", "fire", "water", "nature", "void");
    private final TameworkGameplayAdapter tamework;

    public MiniwyvernAttunementService(TameworkGameplayAdapter tamework) {
        this.tamework = Objects.requireNonNull(tamework, "tamework");
    }

    public CompletionStage<GameplayResult> attune(UUID playerUuid,
                                                   String archetypeId,
                                                   ConsumableReservation essence) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        archetypeId = Objects.requireNonNull(archetypeId, "archetypeId").trim().toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(essence, "essence");
        if (!ARCHETYPES.contains(archetypeId)) {
            return essence.release().handle((ignored, failure) -> GameplayResult.denied("unsupported archetype"));
        }
        TameworkGameplayAdapter.Readiness readiness = tamework.attunementReadiness();
        return essence.release().handle((ignored, failure) -> GameplayResult.unavailable(readiness.reason()));
    }
}
