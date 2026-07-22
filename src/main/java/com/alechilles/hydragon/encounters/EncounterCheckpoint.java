package com.alechilles.hydragon.encounters;

import java.util.Locale;
import java.util.Objects;

/** Versioned encoding stored in EncounterRecord.phase without expanding the shared persistence schema. */
public record EncounterCheckpoint(
        EncounterPhase phase,
        double groundingBuildup,
        long eligibilityLostAtEpochMillis) {
    private static final String V1_SEPARATOR = ";v=1;b=";
    private static final String V2_SEPARATOR = ";v=2;b=";
    private static final String ELIGIBILITY_SEPARATOR = ";e=";

    public EncounterCheckpoint {
        Objects.requireNonNull(phase, "phase");
        if (!Double.isFinite(groundingBuildup) || groundingBuildup < 0.0D) {
            throw new IllegalArgumentException("groundingBuildup must be finite and non-negative");
        }
        if (phase != EncounterPhase.GROUNDING && groundingBuildup != 0.0D) {
            throw new IllegalArgumentException("Only GROUNDING may carry buildup");
        }
        if (eligibilityLostAtEpochMillis < 0L) {
            throw new IllegalArgumentException("eligibilityLostAtEpochMillis must not be negative");
        }
    }

    public EncounterCheckpoint(EncounterPhase phase, double groundingBuildup) {
        this(phase, groundingBuildup, 0L);
    }

    public static EncounterCheckpoint of(EncounterPhase phase) {
        return new EncounterCheckpoint(phase, 0.0D, 0L);
    }

    public EncounterCheckpoint withPhase(EncounterPhase nextPhase, double nextBuildup) {
        return new EncounterCheckpoint(nextPhase, nextBuildup, eligibilityLostAtEpochMillis);
    }

    public EncounterCheckpoint withEligibilityLostAt(long epochMillis) {
        return new EncounterCheckpoint(phase, groundingBuildup, epochMillis);
    }

    public String encode() {
        if (eligibilityLostAtEpochMillis == 0L) {
            if (phase != EncounterPhase.GROUNDING || groundingBuildup == 0.0D) return phase.name();
            return phase.name() + V1_SEPARATOR + Double.toString(groundingBuildup);
        }
        return phase.name() + V2_SEPARATOR + Double.toString(groundingBuildup)
                + ELIGIBILITY_SEPARATOR + eligibilityLostAtEpochMillis;
    }

    public static EncounterCheckpoint decode(String value) {
        String normalized = Objects.requireNonNull(value, "value").trim();
        int v2 = normalized.indexOf(V2_SEPARATOR);
        int v1 = normalized.indexOf(V1_SEPARATOR);
        int separator = v2 >= 0 ? v2 : v1;
        String rawPhase = separator < 0 ? normalized : normalized.substring(0, separator);
        EncounterPhase phase = EncounterPhase.valueOf(rawPhase.toUpperCase(Locale.ROOT));
        if (v2 >= 0) {
            int eligibility = normalized.indexOf(ELIGIBILITY_SEPARATOR, v2 + V2_SEPARATOR.length());
            if (eligibility < 0) throw new IllegalArgumentException("Missing eligibility checkpoint");
            double buildup = Double.parseDouble(normalized.substring(
                    v2 + V2_SEPARATOR.length(), eligibility));
            long eligibilityLostAt = Long.parseLong(normalized.substring(
                    eligibility + ELIGIBILITY_SEPARATOR.length()));
            return new EncounterCheckpoint(phase, buildup, eligibilityLostAt);
        }
        double buildup = v1 < 0 ? 0.0D
                : Double.parseDouble(normalized.substring(v1 + V1_SEPARATOR.length()));
        return new EncounterCheckpoint(phase, buildup, 0L);
    }
}
