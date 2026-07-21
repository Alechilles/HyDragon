package com.alechilles.hydragon.integration;

import com.alechilles.alecstamework.Tamework;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Supported HyDragon bootstrap into Tamework's experimental public API.
 *
 * <p>This class deliberately uses only {@link Tamework#getInstance()}, {@link Tamework#getApi()},
 * and {@code com.alechilles.alecstamework.api.*}. Capability names are compared as strings so a
 * HyDragon binary compiled against a newer enum remains safe when the manifest-compatible runtime
 * exposes an older optional capability set.</p>
 */
public final class TameworkBridge {
    private static final String API_UNAVAILABLE = "Tamework public API is unavailable";
    private final TameworkApi api;
    private final Snapshot snapshot;

    private TameworkBridge(@Nullable TameworkApi api, Snapshot snapshot) {
        this.api = api;
        this.snapshot = snapshot;
    }

    /** Acquires Tamework through its sanctioned nullable plugin accessor. */
    @Nonnull
    public static TameworkBridge connect() {
        try {
            Tamework plugin = Tamework.getInstance();
            TameworkApi api = plugin == null ? null : plugin.getApi();
            if (api == null) {
                return new TameworkBridge(null, evaluate(null, Set.of(), API_UNAVAILABLE));
            }
            Set<String> capabilities = new TreeSet<>();
            for (TameworkApiCapability capability : api.getCapabilities()) {
                capabilities.add(capability.name());
            }
            return new TameworkBridge(api, evaluate(api.getApiVersion(), capabilities, null));
        } catch (RuntimeException | LinkageError failure) {
            String reason = "Tamework API bootstrap failed: " + failure.getClass().getSimpleName();
            return new TameworkBridge(null, evaluate(null, Set.of(), reason));
        }
    }

    /** Pure gate evaluator used by unit tests and readiness tooling. */
    static Snapshot evaluate(@Nullable String apiVersion, Set<String> capabilities, @Nullable String bootstrapIssue) {
        Set<String> present = Set.copyOf(capabilities);
        Map<HyDragonFeature, FeatureGate> gates = new EnumMap<>(HyDragonFeature.class);
        gates.put(HyDragonFeature.CAPTURE_AND_BOND, gate(
                HyDragonFeature.CAPTURE_AND_BOND, present,
                Set.of("CAPTURE_POLICY", "BONDED_VESSELS", "POPULATION_GROUPS"), List.of(), bootstrapIssue));
        gates.put(HyDragonFeature.BONDED_STONE_TRANSITIONS, gate(
                HyDragonFeature.BONDED_STONE_TRANSITIONS, present,
                Set.of("BONDED_VESSELS", "POPULATION_GROUPS"),
                List.of("Bonded SUMMON/STORE needs a documented item-projection claim/apply/commit protocol"),
                bootstrapIssue));
        gates.put(HyDragonFeature.BONDED_STONE_REPAIR, gate(
                HyDragonFeature.BONDED_STONE_REPAIR, present,
                Set.of("BONDED_VESSELS", "POPULATION_GROUPS"), List.of(), bootstrapIssue));
        gates.put(HyDragonFeature.SOUL_BOND_CLAIM, gate(
                HyDragonFeature.SOUL_BOND_CLAIM, present,
                Set.of("COMPANION_PROVISIONING", "POPULATION_GROUPS", "PROFILE_DATA"),
                List.of("Provisioning needs query-by-profile revision/origin and restart-safe activate/revive transitions"),
                bootstrapIssue));
        gates.put(HyDragonFeature.MINIWYVERN_ATTUNEMENT, gate(
                HyDragonFeature.MINIWYVERN_ATTUNEMENT, present,
                Set.of("PROFILE_DATA", "PROGRESSION_MUTATIONS"),
                List.of("Profile data queue acceptance is not a durable CAS/item-consumption transaction"),
                bootstrapIssue));
        gates.put(HyDragonFeature.MINIWYVERN_ABILITIES, gate(
                HyDragonFeature.MINIWYVERN_ABILITIES, present,
                Set.of("EVENTS", "PROFILE_DATA", "TRAIT_EFFECTS"),
                List.of("Provisioned companion events need origin namespace and command-link-independent lifecycle coverage"),
                bootstrapIssue));
        gates.put(HyDragonFeature.DYNAMIC_ENCOUNTERS, gate(
                HyDragonFeature.DYNAMIC_ENCOUNTERS, present,
                Set.of("CAPTURE_POLICY", "POPULATION_GROUPS"), List.of(), bootstrapIssue));
        gates.put(HyDragonFeature.TAMEWORK_DIAGNOSTICS, gate(
                HyDragonFeature.TAMEWORK_DIAGNOSTICS, present,
                Set.of("DIAGNOSTICS"), List.of(), bootstrapIssue));
        return new Snapshot(
                apiVersion == null ? "unavailable" : apiVersion,
                present,
                Map.copyOf(gates),
                bootstrapIssue
        );
    }

    private static FeatureGate gate(
            HyDragonFeature feature,
            Set<String> present,
            Set<String> required,
            List<String> blockers,
            @Nullable String bootstrapIssue) {
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(present);
        List<String> effectiveBlockers = bootstrapIssue == null ? blockers : List.of(bootstrapIssue);
        boolean available = bootstrapIssue == null && missing.isEmpty() && effectiveBlockers.isEmpty();
        return new FeatureGate(feature, available, required, missing, effectiveBlockers);
    }

    @Nonnull
    public Snapshot snapshot() {
        return snapshot;
    }

    /** Returns the public API only for adapters that have already checked the matching feature gate. */
    @Nullable
    public TameworkApi api() {
        return api;
    }

    public record Snapshot(
            String apiVersion,
            Set<String> capabilities,
            Map<HyDragonFeature, FeatureGate> features,
            @Nullable String bootstrapIssue) {
        public Snapshot {
            capabilities = Set.copyOf(capabilities);
            features = Map.copyOf(features);
        }

        public boolean apiAvailable() {
            return bootstrapIssue == null;
        }

        @Nonnull
        public FeatureGate feature(HyDragonFeature feature) {
            return features.get(feature);
        }
    }
}
