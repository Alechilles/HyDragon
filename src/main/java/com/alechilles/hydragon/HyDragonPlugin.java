package com.alechilles.hydragon;

import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.abilities.HyDragonAbilityRegistrationFacade;
import com.alechilles.hydragon.abilities.HytaleMiniwyvernAbilityWorldDispatcher;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityRuntime;
import com.alechilles.hydragon.config.DragonEncounterConfig;
import com.alechilles.hydragon.config.DragonSpeciesConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.config.MiniwyvernArchetypeConfig;
import com.alechilles.hydragon.config.StoneMaintenanceConfig;
import com.alechilles.hydragon.diagnostics.HyDragonStatusCommand;
import com.alechilles.hydragon.diagnostics.HyDragonPersistenceStatus;
import com.alechilles.hydragon.diagnostics.HyDragonRefundClaimCommand;
import com.alechilles.hydragon.encounters.DynamicEncounterRuntime;
import com.alechilles.hydragon.encounters.HyDragonEncounterRegistrationFacade;
import com.alechilles.hydragon.encounters.HyDragonEncounterServerRuntime;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.interactions.HyDragonMiniwyvernAttuneInteraction;
import com.alechilles.hydragon.interactions.HyDragonInteractionRuntime;
import com.alechilles.hydragon.interactions.HyDragonRepairBondedStoneInteraction;
import com.alechilles.hydragon.interactions.HyDragonSoulBondInteraction;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.runtime.BondedStoneRepairService;
import com.alechilles.hydragon.runtime.ConsumableRefundClaimService;
import com.alechilles.hydragon.runtime.ConsumableSagaRecoveryRuntime;
import com.alechilles.hydragon.runtime.HyDragonGameplayRuntime;
import com.alechilles.hydragon.runtime.MiniwyvernAttunementService;
import com.alechilles.hydragon.runtime.SoulBondLedger;
import com.alechilles.hydragon.runtime.SoulBondService;
import com.alechilles.hydragon.runtime.StateStoreMiniwyvernProfileProjection;
import com.alechilles.hydragon.runtime.StateStoreOperationJournal;
import com.alechilles.hydragon.runtime.StateStoreSoulBondLedger;
import com.alechilles.hydragon.runtime.TameworkBondedRepairRequestResolver;
import com.alechilles.hydragon.runtime.TameworkGameplayAdapter;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.io.IOException;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Main entry point for the combined HyDragon Java plugin and asset pack. */
public final class HyDragonPlugin extends JavaPlugin {
    private static HyDragonPlugin instance;
    private final HyDragonConfigRepository configRepository = new HyDragonConfigRepository();
    private TameworkBridge tameworkBridge;
    private HyDragonStateStore stateStore;
    private String persistenceFailure;
    private HyDragonEncounterServerRuntime serverRuntime;
    private HyDragonGameplayRuntime gameplayRuntime;
    private DynamicEncounterRuntime encounterRuntime;
    private MiniwyvernAbilityRuntime abilityRuntime;
    private ConsumableSagaRecoveryRuntime sagaRecoveryRuntime;
    private ConsumableRefundClaimService refundClaims;

    public HyDragonPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        registerInteractionCodecs();
        // The persistent encounter marker and damage system must exist before any world loads.
        serverRuntime = HyDragonEncounterRegistrationFacade.registerServerRuntime(this);
        tameworkBridge = TameworkBridge.connect();
        registerConfigAssets();
        getCommandRegistry().registerCommand(new HyDragonStatusCommand(
                getManifest().getVersion().toString(),
                configRepository::snapshot,
                () -> tameworkBridge,
                this::getPersistenceStatus));
        getCommandRegistry().registerCommand(new HyDragonRefundClaimCommand(() -> refundClaims));
        getLogger().at(Level.INFO).log("HyDragon plugin setup complete.");
    }

    @Override
    protected void start() {
        tameworkBridge = TameworkBridge.connect();
        configRepository.refreshFromAssetRegistry();
        openStateStore();
        startRuntimes();
        HyDragonConfigRepository.Snapshot config = configRepository.snapshot();
        Level level = config.isValid() ? Level.INFO : Level.WARNING;
        getLogger().at(level).log("HyDragon enabled with %d species, %d Miniwyvern archetypes, "
                        + "%d encounters, %d config issue(s), and Tamework Public API %s.",
                config.species().size(), config.archetypes().size(), config.encounters().size(), config.issues().size(),
                tameworkBridge.snapshot().apiVersion());
        HyDragonPersistenceStatus persistence = getPersistenceStatus();
        getLogger().at(persistence.writable() ? Level.INFO : Level.WARNING).log(
                "HyDragon persistence %s: players=%d, profiles=%d, encounters=%d, "
                        + "pendingProfileProjections=%d, quarantined=%d, reconcile=%d.",
                persistence.writable() ? "ready" : "unavailable/read-only",
                persistence.players(), persistence.profiles(), persistence.encounters(),
                persistence.pendingProfileProjections(), persistence.quarantined(),
                persistence.pendingReconciliation());
    }

    @Override
    protected void shutdown() {
        stopRuntimes();
        getLogger().at(Level.INFO).log("HyDragon disabled.");
        stateStore = null;
        persistenceFailure = null;
        tameworkBridge = null;
        serverRuntime = null;
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

    @Nullable
    public TameworkBridge getTameworkBridge() {
        return tameworkBridge;
    }

    @Nullable
    public HyDragonStateStore getStateStore() {
        return stateStore;
    }

    @Nonnull
    public HyDragonPersistenceStatus getPersistenceStatus() {
        return HyDragonPersistenceStatus.from(stateStore, persistenceFailure);
    }

    private void openStateStore() {
        try {
            stateStore = new HyDragonStateStore(getDataDirectory().resolve("hydragon-state.properties"));
            persistenceFailure = null;
        } catch (IOException | RuntimeException failure) {
            stateStore = null;
            persistenceFailure = "state store open failed: " + failure.getClass().getSimpleName();
            getLogger().at(Level.SEVERE).withCause(failure).log("Unable to open HyDragon persistence; mutations disabled.");
        }
    }

    private void startRuntimes() {
        TameworkBridge bridge = tameworkBridge;
        HyDragonStateStore store = stateStore;
        TameworkApi api = bridge == null ? null : bridge.api();
        if (api == null || store == null || !store.snapshot().writable()) {
            getLogger().at(Level.WARNING).log(
                    "HyDragon gameplay runtimes remain disabled until Tamework and writable persistence are available.");
            return;
        }
        if (serverRuntime == null) {
            getLogger().at(Level.SEVERE).log(
                    "HyDragon live runtime was not registered during setup; gameplay remains disabled.");
            return;
        }

        try {
            TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(api);
            SoulBondLedger soulBonds = new StateStoreSoulBondLedger(store);
            StateStoreOperationJournal journal = new StateStoreOperationJournal(store, System::currentTimeMillis);
            SoulBondService soulBondService = new SoulBondService(
                    adapter, soulBonds, journal, System::currentTimeMillis);
            MiniwyvernAttunementService attunementService = new MiniwyvernAttunementService(
                    adapter,
                    soulBonds,
                    journal,
                    new StateStoreMiniwyvernProfileProjection(store));
            BondedStoneRepairService repairService = new BondedStoneRepairService(adapter, journal);
            sagaRecoveryRuntime = new ConsumableSagaRecoveryRuntime(
                    journal, soulBondService, repairService);
            refundClaims = new ConsumableRefundClaimService(journal);

            gameplayRuntime = new HyDragonGameplayRuntime(
                    soulBondService,
                    attunementService,
                    repairService,
                    new TameworkBondedRepairRequestResolver(api));
            HyDragonInteractionRuntime.install(gameplayRuntime, () -> bridge.snapshot());

            encounterRuntime = HyDragonEncounterRegistrationFacade.install(
                    api,
                    store,
                    configRepository::snapshot,
                    () -> bridge.snapshot().feature(HyDragonFeature.DYNAMIC_ENCOUNTERS),
                    serverRuntime.worlds());
            abilityRuntime = HyDragonAbilityRegistrationFacade.install(
                    api,
                    store,
                    configRepository::snapshot,
                    () -> bridge.snapshot().feature(HyDragonFeature.MINIWYVERN_ABILITIES),
                    new HytaleMiniwyvernAbilityWorldDispatcher(api));
            serverRuntime.start(
                    encounterRuntime, abilityRuntime, sagaRecoveryRuntime, configRepository::snapshot);
            getLogger().at(Level.INFO).log("HyDragon gameplay, encounter, and Miniwyvern runtimes are active.");
        } catch (RuntimeException | LinkageError failure) {
            stopRuntimes();
            getLogger().at(Level.SEVERE).withCause(failure).log(
                    "HyDragon runtime startup failed; gameplay interactions are disabled.");
        }
    }

    private void stopRuntimes() {
        closeRuntime("live server", serverRuntime);
        closeRuntime("Miniwyvern ability", abilityRuntime);
        closeRuntime("dynamic encounter", encounterRuntime);
        if (gameplayRuntime != null) {
            HyDragonInteractionRuntime.uninstall(gameplayRuntime);
        }
        gameplayRuntime = null;
        encounterRuntime = null;
        abilityRuntime = null;
        sagaRecoveryRuntime = null;
        refundClaims = null;
    }

    private void closeRuntime(String label, AutoCloseable runtime) {
        if (runtime == null) return;
        try {
            runtime.close();
        } catch (Exception failure) {
            getLogger().at(Level.WARNING).withCause(failure).log(
                    "Unable to close HyDragon %s runtime cleanly.", label);
        }
    }

    private void registerInteractionCodecs() {
        getCodecRegistry(Interaction.CODEC).register(
                HyDragonSoulBondInteraction.TYPE_ID,
                HyDragonSoulBondInteraction.class,
                HyDragonSoulBondInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(
                HyDragonMiniwyvernAttuneInteraction.TYPE_ID,
                HyDragonMiniwyvernAttuneInteraction.class,
                HyDragonMiniwyvernAttuneInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(
                HyDragonRepairBondedStoneInteraction.TYPE_ID,
                HyDragonRepairBondedStoneInteraction.class,
                HyDragonRepairBondedStoneInteraction.CODEC);
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
