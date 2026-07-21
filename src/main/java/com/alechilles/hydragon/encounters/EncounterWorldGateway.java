package com.alechilles.hydragon.encounters;

import com.alechilles.hydragon.config.DragonEncounterConfig;
import java.util.Optional;
import java.util.UUID;

/** World-thread-only spawn, projection, grounding, and cleanup boundary. */
public interface EncounterWorldGateway {
    boolean isWorldThread();

    SpawnResult spawn(SpawnRequest request);

    TargetLookup findTarget(String encounterId, String worldName, UUID expectedTargetUuid);

    boolean applyGroundedState(UUID targetNpcUuid, String groundedState, String groundedEffectId);

    /** Returns true only after the NPC's motion controller has authoritatively reached ground. */
    boolean isGrounded(UUID targetNpcUuid);

    boolean retireTarget(UUID targetNpcUuid, String reason);

    record SpawnRequest(
            String encounterId,
            String definitionId,
            String targetRoleId,
            EncounterCandidate candidate,
            DragonEncounterConfig definition) {
        public SpawnRequest {
            encounterId = requiredText(encounterId, "encounterId");
            definitionId = requiredText(definitionId, "definitionId");
            targetRoleId = requiredText(targetRoleId, "targetRoleId");
            java.util.Objects.requireNonNull(candidate, "candidate");
            java.util.Objects.requireNonNull(definition, "definition");
        }
    }

    record SpawnResult(boolean spawned, UUID targetNpcUuid, String reason) {
        public SpawnResult {
            reason = requiredText(reason, "reason");
            if (spawned && targetNpcUuid == null) throw new IllegalArgumentException("spawned result needs targetNpcUuid");
        }

        public static SpawnResult success(UUID targetNpcUuid) {
            return new SpawnResult(true, java.util.Objects.requireNonNull(targetNpcUuid), "spawned");
        }

        public static SpawnResult failure(String reason) { return new SpawnResult(false, null, reason); }
    }

    record TargetLookup(TargetPresence presence, Optional<UUID> targetNpcUuid) {
        public TargetLookup {
            java.util.Objects.requireNonNull(presence, "presence");
            targetNpcUuid = java.util.Objects.requireNonNull(targetNpcUuid, "targetNpcUuid");
            if (presence == TargetPresence.PRESENT && targetNpcUuid.isEmpty()) {
                throw new IllegalArgumentException("PRESENT target lookup needs UUID");
            }
        }

        public static TargetLookup present(UUID uuid) {
            return new TargetLookup(TargetPresence.PRESENT, Optional.of(uuid));
        }

        public static TargetLookup absent() { return new TargetLookup(TargetPresence.ABSENT, Optional.empty()); }
        public static TargetLookup unknown() { return new TargetLookup(TargetPresence.UNKNOWN, Optional.empty()); }
    }

    enum TargetPresence { PRESENT, ABSENT, UNKNOWN }

    private static String requiredText(String value, String field) {
        String normalized = java.util.Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
