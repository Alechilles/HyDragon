package com.alechilles.hydragon.encounters;

import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.config.DragonEncounterConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.runtime.TameworkGameplayAdapter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Verifies encounter gates against one confirmed active bonded full dragon. */
public final class EncounterEligibilityService {
    public static final String FLIGHTMASTERS_TALISMAN_ITEM_ID =
            "Tamework_Flightmasters_Talisman";

    private final TameworkGameplayAdapter tamework;
    private final ActiveBondedDragonResolver dragons;

    public EncounterEligibilityService(TameworkApi api) {
        tamework = new TameworkGameplayAdapter(Objects.requireNonNull(api, "api"));
        dragons = new ActiveBondedDragonResolver();
    }

    /** Source-compatible bridge; local HyDragon profile state is intentionally ignored. */
    @Deprecated
    public EncounterEligibilityService(
            TameworkApi api,
            HyDragonStateStore ignoredStateStore) {
        this(api);
        Objects.requireNonNull(ignoredStateStore, "ignoredStateStore");
    }

    public Decision evaluate(
            DragonEncounterConfig definition,
            HyDragonConfigRepository.Snapshot configs,
            EncounterCandidate candidate,
            boolean featureAvailable) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(configs, "configs");
        Objects.requireNonNull(candidate, "candidate");
        Decision staticGate = staticGate(
                definition, configs, candidate, featureAvailable);
        if (staticGate != null) return staticGate;

        BondedCompanionResult<List<BondedCompanionProfileView>> result;
        try {
            result = tamework.listDragonHorn(candidate.playerUuid())
                    .toCompletableFuture().getNow(null);
        } catch (RuntimeException | LinkageError failure) {
            return Decision.deny("bonded-roster-query-failed");
        }
        if (result == null) return Decision.deny("bonded-roster-query-pending");
        if (!result.successful() || result.value() == null) {
            return Decision.deny("bonded-roster-query-failed");
        }
        return dragons.resolve(
                        configs, candidate.playerUuid(), candidate.worldName(),
                        result.value())
                .map(Decision::allow)
                .orElseGet(() -> Decision.deny(
                        "active-avatar-flight-dragon-required"));
    }

    private static Decision staticGate(
            DragonEncounterConfig definition,
            HyDragonConfigRepository.Snapshot configs,
            EncounterCandidate candidate,
            boolean featureAvailable) {
        if (!featureAvailable) return Decision.deny("feature-gated");
        if (!configs.isValid() || !definition.isEnabled()
                || !definition.validate().isEmpty()) {
            return Decision.deny("encounter-config-invalid");
        }
        DragonEncounterConfig.RegionSettings region =
                definition.getRegionsAndAltitude();
        if (!region.getEnvironmentIds().contains(candidate.environmentId())) {
            return Decision.deny("wrong-environment");
        }
        if (candidate.y() < region.getMinY() || candidate.y() > region.getMaxY()) {
            return Decision.deny("wrong-altitude");
        }
        if (!weatherMatches(definition.getWeatherPredicate(), candidate)) {
            return Decision.deny("weather-gate");
        }
        if (!FLIGHTMASTERS_TALISMAN_ITEM_ID.equals(
                definition.getPlayerEligibility().getRequiredItemId())) {
            return Decision.deny("unsupported-flight-item");
        }
        return candidate.accessibleItemIds().contains(
                FLIGHTMASTERS_TALISMAN_ITEM_ID)
                ? null : Decision.deny("flightmasters-talisman-required");
    }

    private static boolean weatherMatches(
            DragonEncounterConfig.WeatherSettings weather,
            EncounterCandidate candidate) {
        List<String> required = weather.getWeatherIds();
        if (weather.getMode().equalsIgnoreCase("AllOf")) {
            return candidate.activeWeatherIds().containsAll(required);
        }
        return required.stream().anyMatch(candidate.activeWeatherIds()::contains);
    }

    public record Decision(boolean allowed, String reason, String creditedProfileId) {
        public Decision {
            reason = Objects.requireNonNull(reason, "reason")
                    .trim().toLowerCase(Locale.ROOT);
            if (reason.isEmpty()) throw new IllegalArgumentException("reason is required");
            creditedProfileId = creditedProfileId == null
                    ? null : creditedProfileId.trim();
            if (allowed && (creditedProfileId == null
                    || creditedProfileId.isEmpty())) {
                throw new IllegalArgumentException(
                        "Allowed eligibility needs creditedProfileId");
            }
        }

        static Decision allow(String profileId) {
            return new Decision(true, "allowed", profileId);
        }

        static Decision deny(String reason) {
            return new Decision(false, reason, null);
        }
    }
}
