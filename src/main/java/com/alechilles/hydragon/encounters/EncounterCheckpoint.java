package com.alechilles.hydragon.encounters;

import java.util.Locale;
import java.util.Objects;

/** Versioned encoding stored in EncounterRecord.phase without expanding the shared persistence schema. */
public record EncounterCheckpoint(EncounterPhase phase, double groundingBuildup) {
    private static final String SEPARATOR = ";v=1;b=";

    public EncounterCheckpoint {
        Objects.requireNonNull(phase, "phase");
        if (!Double.isFinite(groundingBuildup) || groundingBuildup < 0.0D) {
            throw new IllegalArgumentException("groundingBuildup must be finite and non-negative");
        }
        if (phase != EncounterPhase.GROUNDING && groundingBuildup != 0.0D) {
            throw new IllegalArgumentException("Only GROUNDING may carry buildup");
        }
    }

    public static EncounterCheckpoint of(EncounterPhase phase) {
        return new EncounterCheckpoint(phase, 0.0D);
    }

    public String encode() {
        if (phase != EncounterPhase.GROUNDING || groundingBuildup == 0.0D) return phase.name();
        return phase.name() + SEPARATOR + Double.toString(groundingBuildup);
    }

    public static EncounterCheckpoint decode(String value) {
        String normalized = Objects.requireNonNull(value, "value").trim();
        int separator = normalized.indexOf(SEPARATOR);
        String rawPhase = separator < 0 ? normalized : normalized.substring(0, separator);
        EncounterPhase phase = EncounterPhase.valueOf(rawPhase.toUpperCase(Locale.ROOT));
        double buildup = separator < 0 ? 0.0D : Double.parseDouble(normalized.substring(separator + SEPARATOR.length()));
        return new EncounterCheckpoint(phase, buildup);
    }
}
