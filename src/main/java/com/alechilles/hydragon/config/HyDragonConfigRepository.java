package com.alechilles.hydragon.config;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.JsonAsset;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builds an immutable, cross-validated view of HyDragon's asset-backed runtime configuration.
 * Asset events rebuild the whole view under one lock so consumers never observe a partial reload.
 */
public final class HyDragonConfigRepository {
    private final Object reloadLock = new Object();
    private Map<String, DragonSpeciesConfig> speciesAssets = Map.of();
    private Map<String, StoneMaintenanceConfig> maintenanceAssets = Map.of();
    private Map<String, MiniwyvernArchetypeConfig> archetypeAssets = Map.of();
    private Map<String, DragonEncounterConfig> encounterAssets = Map.of();
    private volatile Snapshot snapshot = Snapshot.empty();

    /** Reads all registered asset maps. Safe to call at start and during reconciliation. */
    public void refreshFromAssetRegistry() {
        synchronized (reloadLock) {
            speciesAssets = copyRegisteredAssets(DragonSpeciesConfig.class);
            maintenanceAssets = copyRegisteredAssets(StoneMaintenanceConfig.class);
            archetypeAssets = copyRegisteredAssets(MiniwyvernArchetypeConfig.class);
            encounterAssets = copyRegisteredAssets(DragonEncounterConfig.class);
            rebuild();
        }
    }

    public void onSpeciesLoaded(
            LoadedAssetsEvent<String, DragonSpeciesConfig, DefaultAssetMap<String, DragonSpeciesConfig>> event) {
        replaceSpecies(event.getAssetMap());
    }

    public void onSpeciesRemoved(
            RemovedAssetsEvent<String, DragonSpeciesConfig, DefaultAssetMap<String, DragonSpeciesConfig>> event) {
        replaceSpecies(event.getAssetMap());
    }

    public void onMaintenanceLoaded(
            LoadedAssetsEvent<String, StoneMaintenanceConfig, DefaultAssetMap<String, StoneMaintenanceConfig>> event) {
        replaceMaintenance(event.getAssetMap());
    }

    public void onMaintenanceRemoved(
            RemovedAssetsEvent<String, StoneMaintenanceConfig, DefaultAssetMap<String, StoneMaintenanceConfig>> event) {
        replaceMaintenance(event.getAssetMap());
    }

    public void onArchetypeLoaded(
            LoadedAssetsEvent<String, MiniwyvernArchetypeConfig, DefaultAssetMap<String, MiniwyvernArchetypeConfig>> event) {
        replaceArchetypes(event.getAssetMap());
    }

    public void onArchetypeRemoved(
            RemovedAssetsEvent<String, MiniwyvernArchetypeConfig, DefaultAssetMap<String, MiniwyvernArchetypeConfig>> event) {
        replaceArchetypes(event.getAssetMap());
    }

    public void onEncounterLoaded(
            LoadedAssetsEvent<String, DragonEncounterConfig, DefaultAssetMap<String, DragonEncounterConfig>> event) {
        replaceEncounters(event.getAssetMap());
    }

    public void onEncounterRemoved(
            RemovedAssetsEvent<String, DragonEncounterConfig, DefaultAssetMap<String, DragonEncounterConfig>> event) {
        replaceEncounters(event.getAssetMap());
    }

    @Nonnull
    public Snapshot snapshot() {
        return snapshot;
    }

    private void replaceSpecies(@Nullable DefaultAssetMap<String, DragonSpeciesConfig> map) {
        synchronized (reloadLock) {
            speciesAssets = copy(map);
            rebuild();
        }
    }

    private void replaceMaintenance(@Nullable DefaultAssetMap<String, StoneMaintenanceConfig> map) {
        synchronized (reloadLock) {
            maintenanceAssets = copy(map);
            rebuild();
        }
    }

    private void replaceArchetypes(@Nullable DefaultAssetMap<String, MiniwyvernArchetypeConfig> map) {
        synchronized (reloadLock) {
            archetypeAssets = copy(map);
            rebuild();
        }
    }

    private void replaceEncounters(@Nullable DefaultAssetMap<String, DragonEncounterConfig> map) {
        synchronized (reloadLock) {
            encounterAssets = copy(map);
            rebuild();
        }
    }

    private void rebuild() {
        snapshot = buildSnapshot(
                speciesAssets.values(),
                maintenanceAssets.values(),
                archetypeAssets.values(),
                encounterAssets.values()
        );
    }

    /** Pure snapshot builder exposed for deterministic validation tests. */
    static Snapshot buildSnapshot(
            Collection<DragonSpeciesConfig> species,
            Collection<StoneMaintenanceConfig> maintenance,
            Collection<MiniwyvernArchetypeConfig> archetypes,
            Collection<DragonEncounterConfig> encounters) {
        List<String> issues = new ArrayList<>();
        Map<String, DragonSpeciesConfig> speciesById = index(
                "DragonSpecies", species, DragonSpeciesConfig::getId, DragonSpeciesConfig::validate, issues);
        Map<String, StoneMaintenanceConfig> maintenanceById = index(
                "StoneMaintenance", maintenance, StoneMaintenanceConfig::getId,
                StoneMaintenanceConfig::validate, issues);
        Map<String, MiniwyvernArchetypeConfig> archetypesById = index(
                "MiniwyvernArchetypes", archetypes, MiniwyvernArchetypeConfig::getId,
                MiniwyvernArchetypeConfig::validate, issues);
        Map<String, DragonEncounterConfig> encountersById = index(
                "Encounters", encounters, DragonEncounterConfig::getId,
                DragonEncounterConfig::validate, issues);

        if (!maintenanceById.containsKey("Default")) {
            issues.add("StoneMaintenance requires an asset with key Default");
        }

        for (DragonSpeciesConfig speciesConfig : speciesById.values()) {
            for (String encounterId : speciesConfig.getSpawn().getPluginEncounterIds()) {
                DragonEncounterConfig encounter = encountersById.get(encounterId);
                if (encounter == null) {
                    issues.add("DragonSpecies[" + speciesConfig.getId()
                            + "] references missing encounter " + encounterId);
                } else if (!speciesConfig.getId().equals(encounter.getTargetSpeciesId())) {
                    issues.add("DragonSpecies[" + speciesConfig.getId() + "] references encounter "
                            + encounterId + " targeting " + encounter.getTargetSpeciesId());
                }
            }
        }
        for (DragonEncounterConfig encounter : encountersById.values()) {
            if (!speciesById.containsKey(encounter.getTargetSpeciesId())) {
                issues.add("Encounter[" + encounter.getId() + "] references missing species "
                        + encounter.getTargetSpeciesId());
            }
        }

        Set<String> expectedArchetypes = Set.of(
                "neutral", "lightning", "wind", "ice", "fire", "water", "nature", "void");
        Set<String> missingArchetypes = new TreeSet<>(expectedArchetypes);
        missingArchetypes.removeAll(archetypesById.keySet());
        if (!archetypesById.isEmpty() && !missingArchetypes.isEmpty()) {
            issues.add("MiniwyvernArchetypes is missing " + missingArchetypes);
        }

        return new Snapshot(
                Map.copyOf(speciesById),
                Map.copyOf(maintenanceById),
                Map.copyOf(archetypesById),
                Map.copyOf(encountersById),
                List.copyOf(issues)
        );
    }

    private static <T> Map<String, T> index(
            String family,
            Collection<T> assets,
            Function<T, String> idFunction,
            Function<T, List<String>> validation,
            List<String> issues) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (T asset : assets) {
            if (asset == null) {
                issues.add(family + " contains a null asset");
                continue;
            }
            String id = idFunction.apply(asset);
            if (id == null || id.isBlank()) {
                issues.add(family + " contains an asset with a blank id");
                continue;
            }
            T duplicate = indexed.putIfAbsent(id, asset);
            if (duplicate != null) {
                issues.add(family + " has duplicate id " + id);
            }
            for (String issue : validation.apply(asset)) {
                issues.add(family + "[" + id + "]: " + issue);
            }
        }
        return indexed;
    }

    private static <T extends com.hypixel.hytale.assetstore.map.JsonAssetWithMap<String, DefaultAssetMap<String, T>>>
            Map<String, T> copyRegisteredAssets(Class<T> type) {
        AssetStore<String, T, DefaultAssetMap<String, T>> store = AssetRegistry.getAssetStore(type);
        return store == null ? Map.of() : copy(store.getAssetMap());
    }

    private static <T extends JsonAsset<String>> Map<String, T> copy(@Nullable DefaultAssetMap<String, T> map) {
        if (map == null || map.getAssetMap() == null) {
            return Map.of();
        }
        return Map.copyOf(map.getAssetMap());
    }

    /** One atomic configuration generation. */
    public record Snapshot(
            Map<String, DragonSpeciesConfig> species,
            Map<String, StoneMaintenanceConfig> maintenance,
            Map<String, MiniwyvernArchetypeConfig> archetypes,
            Map<String, DragonEncounterConfig> encounters,
            List<String> issues) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), List.of("Assets not loaded"));
        }

        public boolean isValid() {
            return issues.isEmpty();
        }

        @Nullable
        public StoneMaintenanceConfig defaultMaintenance() {
            return maintenance.get("Default");
        }
    }
}
