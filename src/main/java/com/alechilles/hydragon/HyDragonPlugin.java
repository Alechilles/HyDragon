package com.alechilles.hydragon;

import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.abilities.HyDragonAbilityRegistrationFacade;
import com.alechilles.hydragon.abilities.HytaleMiniwyvernAbilityWorldDispatcher;
import com.alechilles.hydragon.abilities.MiniwyvernAbilityRuntime;
import com.alechilles.hydragon.abilities.MiniwyvernAuraMarkedTargetDamageSystem;
import com.alechilles.hydragon.abilities.MiniwyvernAuraSiphonDamageSystem;
import com.alechilles.hydragon.abilities.MiniwyvernConditionalWardDamageSystem;
import com.alechilles.hydragon.abilities.MiniwyvernOwnerAuraDamageSystem;
import com.alechilles.hydragon.abilities.MiniwyvernOwnerAuraEffectQueue;
import com.alechilles.hydragon.abilities.MiniwyvernOwnerAuraEffectSystem;
import com.alechilles.hydragon.abilities.MiniwyvernOwnerAuraRegistry;
import com.alechilles.hydragon.abilities.MiniwyvernVoidEffectReplicationProbe;
import com.alechilles.hydragon.abilities.MiniwyvernVoidEffectReplicationSystem;
import com.alechilles.hydragon.abilities.MiniwyvernVoidEffectLifetimeSystem;
import com.alechilles.hydragon.abilities.MiniwyvernToxicWeaknessDamageSystem;
import com.alechilles.hydragon.abilities.MiniwyvernVoidExposureDamageSystem;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionCodec;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionStore;
import com.alechilles.hydragon.config.DragonEncounterConfig;
import com.alechilles.hydragon.config.DragonSpeciesConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.config.MiniwyvernArchetypeConfig;
import com.alechilles.hydragon.diagnostics.HyDragonStatusCommand;
import com.alechilles.hydragon.diagnostics.HyDragonPersistenceStatus;
import com.alechilles.hydragon.diagnostics.HyDragonRefundClaimCommand;
import com.alechilles.hydragon.encounters.DynamicEncounterRuntime;
import com.alechilles.hydragon.encounters.HyDragonEncounterRegistrationFacade;
import com.alechilles.hydragon.encounters.HyDragonEncounterServerRuntime;
import com.alechilles.hydragon.encounters.TranquilizedHealthFreezeSystem;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.integration.HyDragonFeature;
import com.alechilles.hydragon.integration.TameworkBridge;
import com.alechilles.hydragon.integration.TameworkCapabilityDiagnostics;
import com.alechilles.hydragon.interactions.HyDragonInteractionRuntime;
import com.alechilles.hydragon.interactions.HyDragonSoulBondInteraction;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.runtime.ConsumableRefundClaimService;
import com.alechilles.hydragon.runtime.ConsumableSagaRecoveryRuntime;
import com.alechilles.hydragon.runtime.HyDragonGameplayRuntime;
import com.alechilles.hydragon.runtime.HyDragonRuntimeComposition;
import com.alechilles.hydragon.runtime.SoulBondAbandonmentHandler;
import com.alechilles.hydragon.runtime.SoulBondLedger;
import com.alechilles.hydragon.runtime.SoulBondService;
import com.alechilles.hydragon.runtime.StateStoreOperationJournal;
import com.alechilles.hydragon.runtime.StateStoreSoulBondLedger;
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
    private final MiniwyvernOwnerAuraRegistry miniwyvernOwnerAuras = new MiniwyvernOwnerAuraRegistry();
    private MiniwyvernAuraSiphonDamageSystem miniwyvernAuraSiphon;
    private ConsumableSagaRecoveryRuntime sagaRecoveryRuntime;
    private ConsumableRefundClaimService refundClaims;
    private HyDragonRuntimeComposition runtimeComposition;

    public HyDragonPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        registerInteractionCodecs();
        // The persistent encounter marker and damage system must exist before any world loads.
        serverRuntime = HyDragonEncounterRegistrationFacade.registerServerRuntime(this);
        getEntityStoreRegistry().registerSystem(new MiniwyvernToxicWeaknessDamageSystem(miniwyvernOwnerAuras));
        getEntityStoreRegistry().registerSystem(new TranquilizedHealthFreezeSystem());
        getEntityStoreRegistry().registerSystem(new MiniwyvernVoidExposureDamageSystem(miniwyvernOwnerAuras));
        getEntityStoreRegistry().registerSystem(
                new MiniwyvernConditionalWardDamageSystem(miniwyvernOwnerAuras));
        getEntityStoreRegistry().registerSystem(
                new MiniwyvernAuraMarkedTargetDamageSystem(miniwyvernOwnerAuras));
        miniwyvernAuraSiphon = new MiniwyvernAuraSiphonDamageSystem(miniwyvernOwnerAuras);
        getEntityStoreRegistry().registerSystem(miniwyvernAuraSiphon);
        MiniwyvernVoidEffectLifetimeSystem voidLifetime = new MiniwyvernVoidEffectLifetimeSystem();
        MiniwyvernOwnerAuraEffectQueue ownerAuraEffects = new MiniwyvernOwnerAuraEffectQueue();
        MiniwyvernVoidEffectReplicationProbe voidReplication = new MiniwyvernVoidEffectReplicationProbe();
        getEntityStoreRegistry().registerSystem(voidLifetime);
        getEntityStoreRegistry().registerSystem(
                new MiniwyvernOwnerAuraDamageSystem(miniwyvernOwnerAuras, ownerAuraEffects));
        getEntityStoreRegistry().registerSystem(
                new MiniwyvernOwnerAuraEffectSystem(
                        ownerAuraEffects, miniwyvernOwnerAuras, voidLifetime, voidReplication));
        getEntityStoreRegistry().registerSystem(new MiniwyvernVoidEffectReplicationSystem(voidReplication));
        tameworkBridge = TameworkBridge.connect();
        registerConfigAssets();
        getCommandRegistry().registerCommand(new HyDragonStatusCommand(
                getManifest().getVersion().toString(),
                configRepository::snapshot,
                configRepository::lastReloadIssues,
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
        emitCapabilityDiagnostics();
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

    private void emitCapabilityDiagnostics() {
        TameworkBridge bridge = tameworkBridge;
        if (bridge == null) {
            getLogger().at(Level.SEVERE).log(
                    "Tamework capability diagnostics unavailable; install Tamework %s and restart the server.",
                    TameworkBridge.REQUIRED_TAMEWORK_RANGE);
            return;
        }
        for (TameworkCapabilityDiagnostics.Entry entry
                : TameworkCapabilityDiagnostics.evaluate(bridge.snapshot())) {
            getLogger().at(entry.present() ? Level.INFO : Level.WARNING).log(entry.format());
        }
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

        TameworkBridge.Snapshot gates = bridge.snapshot();
        runtimeComposition = new HyDragonRuntimeComposition(this::logRuntimeFailure);
        installGameplay(api, store, bridge, gates);
        installAbilities(api, store, bridge, gates);
        installEncounters(api, store, bridge, gates);
        startSharedRuntime();
    }

    private void stopRuntimes() {
        closeRuntime("live server", serverRuntime);
        closeRuntime("feature composition", runtimeComposition);
        runtimeComposition = null;
        gameplayRuntime = null;
        encounterRuntime = null;
        abilityRuntime = null;
        if (miniwyvernAuraSiphon != null) {
            miniwyvernAuraSiphon.close();
            miniwyvernAuraSiphon = null;
        }
        miniwyvernOwnerAuras.clear();
        sagaRecoveryRuntime = null;
        refundClaims = null;
    }

    private void installGameplay(
            TameworkApi api,
            HyDragonStateStore store,
            TameworkBridge bridge,
            TameworkBridge.Snapshot gates) {
        GameplayInstallation installed = runtimeComposition.install(
                HyDragonRuntimeComposition.Slot.BONDED_GAMEPLAY,
                gameplayGate(gates),
                () -> createGameplay(api, store, bridge));
        if (installed == null) return;
        gameplayRuntime = installed.gameplay();
        sagaRecoveryRuntime = installed.sagaRecovery();
        refundClaims = installed.refundClaims();
    }

    private GameplayInstallation createGameplay(
            TameworkApi api,
            HyDragonStateStore store,
            TameworkBridge bridge) {
        TameworkGameplayAdapter adapter = new TameworkGameplayAdapter(api);
        SoulBondLedger soulBonds = new StateStoreSoulBondLedger(store);
        StateStoreOperationJournal journal = new StateStoreOperationJournal(
                store, System::currentTimeMillis);
        BondedMiniwyvernExtensionStore extensions =
                new BondedMiniwyvernExtensionStore(
                        adapter, new BondedMiniwyvernExtensionCodec());
        SoulBondService soulBondService = new SoulBondService(
                adapter, extensions, soulBonds, journal, System::currentTimeMillis);
        HyDragonGameplayRuntime gameplay = new HyDragonGameplayRuntime(
                soulBondService, new SoulBondAbandonmentHandler(soulBonds));
        gameplay.start(adapter);
        HyDragonInteractionRuntime.install(gameplay, bridge::snapshot);
        return new GameplayInstallation(
                gameplay,
                new ConsumableSagaRecoveryRuntime(journal, soulBondService),
                new ConsumableRefundClaimService(journal));
    }

    private void installAbilities(
            TameworkApi api,
            HyDragonStateStore store,
            TameworkBridge bridge,
            TameworkBridge.Snapshot gates) {
        abilityRuntime = runtimeComposition.install(
                HyDragonRuntimeComposition.Slot.MINIWYVERN_ABILITIES,
                gates.feature(HyDragonFeature.MINIWYVERN_ABILITIES),
                () -> HyDragonAbilityRegistrationFacade.install(
                        api, store, configRepository::snapshot,
                        () -> bridge.snapshot().feature(
                                HyDragonFeature.MINIWYVERN_ABILITIES),
                        new HytaleMiniwyvernAbilityWorldDispatcher(api), miniwyvernOwnerAuras));
    }

    private void installEncounters(
            TameworkApi api,
            HyDragonStateStore store,
            TameworkBridge bridge,
            TameworkBridge.Snapshot gates) {
        encounterRuntime = runtimeComposition.install(
                HyDragonRuntimeComposition.Slot.DYNAMIC_ENCOUNTERS,
                gates.feature(HyDragonFeature.DYNAMIC_ENCOUNTERS),
                () -> HyDragonEncounterRegistrationFacade.install(
                        api, store, configRepository::snapshot,
                        () -> bridge.snapshot().feature(
                                HyDragonFeature.DYNAMIC_ENCOUNTERS),
                        serverRuntime.worlds()));
    }

    private void startSharedRuntime() {
        if (runtimeComposition.startedSlots().isEmpty()) {
            getLogger().at(Level.WARNING).log(
                    "No HyDragon gameplay feature runtime is currently available.");
            return;
        }
        try {
            serverRuntime.start(encounterRuntime, abilityRuntime,
                    sagaRecoveryRuntime, configRepository::snapshot);
            getLogger().at(Level.INFO).log(
                    "HyDragon feature runtimes active: %s.",
                    runtimeComposition.startedSlots());
        } catch (RuntimeException | LinkageError failure) {
            getLogger().at(Level.SEVERE).withCause(failure).log(
                    "HyDragon shared poller startup failed; installed interactions remain active.");
        }
    }

    private FeatureGate gameplayGate(TameworkBridge.Snapshot gates) {
        return gates.feature(HyDragonFeature.SOUL_BOND_CLAIM);
    }

    private void logRuntimeFailure(HyDragonRuntimeComposition.Failure failure) {
        getLogger().at(failure.phase() == HyDragonRuntimeComposition.Phase.INSTALL
                        ? Level.SEVERE : Level.WARNING)
                .withCause(failure.cause())
                .log("HyDragon %s runtime %s failed; other feature runtimes remain active.",
                        failure.slot(), failure.phase().name().toLowerCase());
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
    }

    private void registerConfigAssets() {
        getAssetRegistry().register(
                HytaleAssetStore.builder(DragonSpeciesConfig.class, new DefaultAssetMap<>())
                        .setPath("HyDragon/DragonSpecies")
                        .setCodec(DragonSpeciesConfig.CODEC)
                        .setKeyFunction(DragonSpeciesConfig::getId)
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
        getEventRegistry().register(LoadedAssetsEvent.class, MiniwyvernArchetypeConfig.class,
                configRepository::onArchetypeLoaded);
        getEventRegistry().register(RemovedAssetsEvent.class, MiniwyvernArchetypeConfig.class,
                configRepository::onArchetypeRemoved);
        getEventRegistry().register(LoadedAssetsEvent.class, DragonEncounterConfig.class,
                configRepository::onEncounterLoaded);
        getEventRegistry().register(RemovedAssetsEvent.class, DragonEncounterConfig.class,
                configRepository::onEncounterRemoved);
    }

    /** Owns the shared interaction installation and its gameplay polling collaborators. */
    private record GameplayInstallation(
            HyDragonGameplayRuntime gameplay,
            ConsumableSagaRecoveryRuntime sagaRecovery,
            ConsumableRefundClaimService refundClaims) implements AutoCloseable {
        private GameplayInstallation {
            java.util.Objects.requireNonNull(gameplay, "gameplay");
            java.util.Objects.requireNonNull(sagaRecovery, "sagaRecovery");
            java.util.Objects.requireNonNull(refundClaims, "refundClaims");
        }

        @Override
        public void close() {
            HyDragonInteractionRuntime.uninstall(gameplay);
            gameplay.close();
        }
    }
}
