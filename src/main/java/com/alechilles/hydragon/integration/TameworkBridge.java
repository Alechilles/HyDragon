package com.alechilles.hydragon.integration;

import com.alechilles.alecstamework.Tamework;
import com.alechilles.alecstamework.api.BondedCompanionApi;
import com.alechilles.alecstamework.api.BondedCompanionAvailability;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import java.util.EnumMap;
import java.util.LinkedHashSet;
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
    public static final String REQUIRED_TAMEWORK_RANGE = ">=3.1.0 <4.0.0";
    private static final String API_UNAVAILABLE = "Tamework public API is unavailable";
    private final TameworkApi api;
    private final Snapshot bootstrapSnapshot;

    private TameworkBridge(@Nullable TameworkApi api, Snapshot snapshot) {
        this.api = api;
        this.bootstrapSnapshot = snapshot;
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
            return connect(api);
        } catch (RuntimeException | LinkageError failure) {
            String reason = "Tamework API bootstrap failed: " + failure.getClass().getSimpleName();
            return new TameworkBridge(null, evaluate(null, Set.of(), reason));
        }
    }

    /** Package-visible seam for exercising capability activation after plugin startup. */
    static TameworkBridge connect(TameworkApi api) {
        Snapshot initial = readSnapshot(api, "bootstrap");
        return new TameworkBridge(api, initial);
    }

    private static Snapshot readSnapshot(TameworkApi api, String phase) {
        try {
            if (api == null) {
                return evaluate(null, Set.of(), API_UNAVAILABLE);
            }
            Set<String> capabilities = new TreeSet<>();
            Set<TameworkApiCapability> advertised = api.getCapabilities();
            if (advertised == null) {
                throw new IllegalStateException("capability set is null");
            }
            for (TameworkApiCapability capability : advertised) {
                if (capability != null) capabilities.add(capability.name());
            }
            Snapshot snapshot = evaluate(api.getApiVersion(), capabilities, null);
            return capabilities.contains("BONDED_COMPANIONS")
                    ? withBondedAvailability(snapshot, bondedAvailability(api))
                    : snapshot;
        } catch (RuntimeException | LinkageError failure) {
            String reason = "Tamework API capability " + phase + " failed: "
                    + failure.getClass().getSimpleName();
            return evaluate(null, Set.of(), reason);
        }
    }

    private static BondedCompanionAvailability bondedAvailability(TameworkApi api) {
        try {
            BondedCompanionApi bonded = api.bondedCompanions();
            if (bonded == null) {
                return BondedCompanionAvailability.unavailable(
                        "Tamework bonded-companion API is null");
            }
            BondedCompanionAvailability availability = bonded.availability();
            return availability == null
                    ? BondedCompanionAvailability.unavailable(
                    "Tamework bonded-companion availability is null")
                    : availability;
        } catch (RuntimeException | LinkageError failure) {
            return BondedCompanionAvailability.unavailable(
                    "Tamework bonded-companion availability refresh failed: "
                            + failure.getClass().getSimpleName());
        }
    }

    private static Snapshot withBondedAvailability(
            Snapshot snapshot,
            BondedCompanionAvailability availability) {
        if (availability.available()) return snapshot;
        String reason = availability.reason();
        Map<HyDragonFeature, FeatureGate> gates = new EnumMap<>(HyDragonFeature.class);
        snapshot.features().forEach((feature, gate) -> {
            if (!gate.requiredCapabilities().contains("BONDED_COMPANIONS")) {
                gates.put(feature, gate);
                return;
            }
            java.util.List<String> blockers = new java.util.ArrayList<>(
                    gate.contractBlockers());
            blockers.add(reason);
            gates.put(feature, new FeatureGate(
                    feature, false, gate.requiredCapabilities(),
                    gate.missingCapabilities(), blockers));
        });
        return new Snapshot(snapshot.apiVersion(), snapshot.capabilities(),
                Map.copyOf(gates), snapshot.bootstrapIssue());
    }

    /** Pure gate evaluator used by unit tests and readiness tooling. */
    static Snapshot evaluate(@Nullable String apiVersion, Set<String> capabilities, @Nullable String bootstrapIssue) {
        Set<String> present = Set.copyOf(capabilities);
        Map<HyDragonFeature, FeatureGate> gates = new EnumMap<>(HyDragonFeature.class);
        for (HyDragonFeature feature : HyDragonFeature.values()) {
            gates.put(feature, gate(feature, present, feature.requiredCapabilities(), bootstrapIssue));
        }
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
            @Nullable String bootstrapIssue) {
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(present);
        java.util.List<String> effectiveBlockers = bootstrapIssue == null
                ? java.util.List.of() : java.util.List.of(bootstrapIssue);
        boolean available = bootstrapIssue == null && missing.isEmpty() && effectiveBlockers.isEmpty();
        return new FeatureGate(feature, available, required, missing, effectiveBlockers);
    }

    @Nonnull
    public Snapshot snapshot() {
        // Tamework deliberately advertises recovery-dependent capabilities only after its
        // persistence runtimes become authoritative. Read them at request time so a normal
        // post-startup recovery can enable HyDragon without requiring another server restart.
        return api == null ? bootstrapSnapshot : readSnapshot(api, "refresh");
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
