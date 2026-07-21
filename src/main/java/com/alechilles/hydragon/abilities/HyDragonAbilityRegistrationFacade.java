package com.alechilles.hydragon.abilities;

import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/** Small plugin-facing facade; HyDragonPlugin only needs to install, tick, and close this runtime. */
public final class HyDragonAbilityRegistrationFacade {
    private HyDragonAbilityRegistrationFacade() {
    }

    public static MiniwyvernAbilityRuntime install(
            TameworkApi api,
            HyDragonStateStore stateStore,
            Supplier<HyDragonConfigRepository.Snapshot> configs,
            Supplier<FeatureGate> featureGate,
            MiniwyvernAbilityWorldDispatcher worlds) {
        Objects.requireNonNull(api, "api");
        MiniwyvernAbilityService service = new MiniwyvernAbilityService(
                new TameworkMiniwyvernAbilityStateRepository(api.profileData()));
        MiniwyvernAbilityRuntime runtime = new MiniwyvernAbilityRuntime(
                api, stateStore, configs, featureGate, worlds, service, Clock.systemUTC());
        try {
            runtime.start();
            return runtime;
        } catch (RuntimeException | Error failure) {
            runtime.close();
            throw failure;
        }
    }
}
