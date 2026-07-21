package com.alechilles.hydragon.encounters;

import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/** Small plugin-facing registration facade for public capture hooks and lifecycle events. */
public final class HyDragonEncounterRegistrationFacade {
    private HyDragonEncounterRegistrationFacade() {
    }

    /** Must be called from plugin setup before any world is loaded. */
    public static ComponentType<EntityStore, HyDragonEncounterComponent> registerComponents(JavaPlugin plugin) {
        return HyDragonEncounterComponent.register(plugin);
    }

    /**
     * Setup-time registration for the persistent marker and post-filter damage system.
     * Bind the returned runtime after both feature runtimes are installed during plugin start.
     */
    public static HyDragonEncounterServerRuntime registerServerRuntime(JavaPlugin plugin) {
        ComponentType<EntityStore, HyDragonEncounterComponent> marker = registerComponents(plugin);
        HyDragonEncounterServerRuntime runtime = new HyDragonEncounterServerRuntime(marker);
        plugin.getEntityStoreRegistry().registerSystem(new HyDragonEncounterDamageSystem(runtime, marker));
        return runtime;
    }

    public static DynamicEncounterRuntime install(
            TameworkApi api,
            HyDragonStateStore stateStore,
            Supplier<HyDragonConfigRepository.Snapshot> configs,
            Supplier<FeatureGate> featureGate,
            EncounterWorldDispatcher worlds) {
        Objects.requireNonNull(api, "api");
        EncounterEligibilityService eligibility = new EncounterEligibilityService(api, stateStore);
        DynamicEncounterCoordinator coordinator = new DynamicEncounterCoordinator(api, stateStore, eligibility);
        DynamicEncounterRuntime runtime = new DynamicEncounterRuntime(
                api, stateStore, configs, featureGate, worlds, coordinator, Clock.systemUTC());
        try {
            runtime.start();
            runtime.reconcileAll();
            return runtime;
        } catch (RuntimeException | Error failure) {
            runtime.close();
            throw failure;
        }
    }
}
