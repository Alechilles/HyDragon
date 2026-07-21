package com.alechilles.hydragon.encounters;

import com.alechilles.alecstamework.api.NpcProfileView;
import com.alechilles.alecstamework.api.PopulationGroupCountsView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.config.DragonEncounterConfig;
import com.alechilles.hydragon.config.DragonSpeciesConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ProfileExtensionRecord;
import com.alechilles.hydragon.persistence.ProfileKind;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Verifies weather, region, Talisman, population, ownership, and active avatar-flight profile gates. */
public final class EncounterEligibilityService {
    public static final String FLIGHTMASTERS_TALISMAN_ITEM_ID = "Tamework_Flightmasters_Talisman";
    private final TameworkApi api;
    private final HyDragonStateStore stateStore;

    public EncounterEligibilityService(TameworkApi api, HyDragonStateStore stateStore) {
        this.api = Objects.requireNonNull(api, "api");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
    }

    public Decision evaluate(
            DragonEncounterConfig definition,
            HyDragonConfigRepository.Snapshot configs,
            EncounterCandidate candidate,
            boolean featureAvailable) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(configs, "configs");
        Objects.requireNonNull(candidate, "candidate");
        if (!featureAvailable) return Decision.deny("feature-gated");
        if (!configs.isValid() || !definition.isEnabled() || !definition.validate().isEmpty()) {
            return Decision.deny("encounter-config-invalid");
        }
        DragonEncounterConfig.RegionSettings region = definition.getRegionsAndAltitude();
        if (!region.getEnvironmentIds().contains(candidate.environmentId())) return Decision.deny("wrong-environment");
        if (candidate.y() < region.getMinY() || candidate.y() > region.getMaxY()) return Decision.deny("wrong-altitude");
        if (!weatherMatches(definition.getWeatherPredicate(), candidate)) return Decision.deny("weather-gate");
        if (!FLIGHTMASTERS_TALISMAN_ITEM_ID.equals(definition.getPlayerEligibility().getRequiredItemId())) {
            return Decision.deny("unsupported-flight-item");
        }
        if (!candidate.accessibleItemIds().contains(FLIGHTMASTERS_TALISMAN_ITEM_ID)) {
            return Decision.deny("flightmasters-talisman-required");
        }

        String groupId = definition.getPlayerEligibility().getActiveCompanionGroup();
        try {
            PopulationGroupCountsView counts = api.policies().populationGroups()
                    .getCounts(candidate.playerUuid(), groupId, candidate.worldName()).orElse(null);
            if (counts == null || counts.committedActive() < 1L || counts.pendingActive() > 0L) {
                return Decision.deny("active-companion-group-unavailable");
            }
        } catch (RuntimeException failure) {
            return Decision.deny("population-query-failed");
        }

        for (ProfileExtensionRecord extension : stateStore.snapshot().profileExtensions().values()) {
            if (extension.kind() != ProfileKind.FULL_DRAGON) continue;
            DragonSpeciesConfig species = configs.species().get(extension.speciesId());
            if (species == null || species.getMount().getMode() != DragonSpeciesConfig.MountMode.AVATAR_FLIGHT) continue;
            try {
                NpcProfileView profile = api.profiles().getByProfileId(extension.profileId().toString()).orElse(null);
                if (profile != null && candidate.playerUuid().equals(profile.ownerUuid())
                        && profile.currentNpcUuid() != null
                        && api.policies().isOwner(profile.profileId(), candidate.playerUuid())) {
                    return Decision.allow(profile.profileId());
                }
            } catch (RuntimeException ignored) {
                // Continue scanning committed local extensions; any API failure ultimately denies.
            }
        }
        return Decision.deny("active-avatar-flight-dragon-required");
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
            reason = Objects.requireNonNull(reason, "reason").trim().toLowerCase(Locale.ROOT);
            if (reason.isEmpty()) throw new IllegalArgumentException("reason is required");
            creditedProfileId = creditedProfileId == null ? null : creditedProfileId.trim();
            if (allowed && (creditedProfileId == null || creditedProfileId.isEmpty())) {
                throw new IllegalArgumentException("Allowed eligibility needs creditedProfileId");
            }
        }

        static Decision allow(String profileId) { return new Decision(true, "allowed", profileId); }
        static Decision deny(String reason) { return new Decision(false, reason, null); }
    }
}
