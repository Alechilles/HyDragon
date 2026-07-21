package com.alechilles.hydragon;

import com.alechilles.hydragon.config.DragonEncounterConfig;
import com.alechilles.hydragon.config.DragonSpeciesConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.config.MiniwyvernArchetypeConfig;
import com.alechilles.hydragon.config.StoneMaintenanceConfig;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Main entry point for the combined HyDragon Java plugin and asset pack. */
public final class HyDragonPlugin extends JavaPlugin {
    private static HyDragonPlugin instance;
    private final HyDragonConfigRepository configRepository = new HyDragonConfigRepository();

    public HyDragonPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        registerConfigAssets();
        getLogger().at(Level.INFO).log("HyDragon plugin setup complete.");
    }

    @Override
    protected void start() {
        configRepository.refreshFromAssetRegistry();
        HyDragonConfigRepository.Snapshot config = configRepository.snapshot();
        Level level = config.isValid() ? Level.INFO : Level.WARNING;
        getLogger().at(level).log("HyDragon enabled with %d species, %d Miniwyvern archetypes, "
                        + "%d encounters, and %d config issue(s).",
                config.species().size(), config.archetypes().size(), config.encounters().size(), config.issues().size());
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("HyDragon disabled.");
        instance = null;
    }

    /** Returns the currently loaded plugin instance, if setup has begun. */
    @Nullable
    public static HyDragonPlugin getInstance() {
        return instance;
    }

    @Nonnull
    public HyDragonConfigRepository getConfigRepository() {
        return configRepository;
    }

    private void registerConfigAssets() {
        getAssetRegistry().register(
                HytaleAssetStore.builder(DragonSpeciesConfig.class, new DefaultAssetMap<>())
                        .setPath("HyDragon/DragonSpecies")
                        .setCodec(DragonSpeciesConfig.CODEC)
                        .setKeyFunction(DragonSpeciesConfig::getId)
                        .build());
        getAssetRegistry().register(
                HytaleAssetStore.builder(StoneMaintenanceConfig.class, new DefaultAssetMap<>())
                        .setPath("HyDragon/StoneMaintenance")
                        .setCodec(StoneMaintenanceConfig.CODEC)
                        .setKeyFunction(StoneMaintenanceConfig::getId)
                        .build());
        getAssetRegistry().register(
                HytaleAssetStore.builder(MiniwyvernArchetypeConfig.class, new DefaultAssetMap<>())
                        .setPath("HyDragon/MiniwyvernArchetypes")
                        .setCodec(MiniwyvernArchetypeConfig.CODEC)
                        .setKeyFunction(MiniwyvernArchetypeConfig::getId)
                        .build());
        getAssetRegistry().register(
                HytaleAssetStore.builder(DragonEncounterConfig.class, new DefaultAssetMap<>())
                        .setPath("HyDragon/Encounters")
                        .setCodec(DragonEncounterConfig.CODEC)
                        .setKeyFunction(DragonEncounterConfig::getId)
                        .build());

        getEventRegistry().register(LoadedAssetsEvent.class, DragonSpeciesConfig.class,
                configRepository::onSpeciesLoaded);
        getEventRegistry().register(RemovedAssetsEvent.class, DragonSpeciesConfig.class,
                configRepository::onSpeciesRemoved);
        getEventRegistry().register(LoadedAssetsEvent.class, StoneMaintenanceConfig.class,
                configRepository::onMaintenanceLoaded);
        getEventRegistry().register(RemovedAssetsEvent.class, StoneMaintenanceConfig.class,
                configRepository::onMaintenanceRemoved);
        getEventRegistry().register(LoadedAssetsEvent.class, MiniwyvernArchetypeConfig.class,
                configRepository::onArchetypeLoaded);
        getEventRegistry().register(RemovedAssetsEvent.class, MiniwyvernArchetypeConfig.class,
                configRepository::onArchetypeRemoved);
        getEventRegistry().register(LoadedAssetsEvent.class, DragonEncounterConfig.class,
                configRepository::onEncounterLoaded);
        getEventRegistry().register(RemovedAssetsEvent.class, DragonEncounterConfig.class,
                configRepository::onEncounterRemoved);
    }
}
