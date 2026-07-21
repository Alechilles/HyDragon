package com.alechilles.hydragon.abilities;

import com.alechilles.alecstamework.api.NpcProfileChangedEvent;
import com.alechilles.alecstamework.api.NpcProfileView;
import com.alechilles.alecstamework.api.ProvisionedCompanionDeathRecordedEvent;
import com.alechilles.alecstamework.api.ProvisionedCompanionRevivedEvent;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ProfileExtensionRecord;
import com.alechilles.hydragon.persistence.ProfileKind;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Tamework-event-aware runtime facade around {@link MiniwyvernAbilityService}. */
public final class MiniwyvernAbilityRuntime implements AutoCloseable {
    public static final String CALLER_NAMESPACE = "hydragon";

    private final TameworkApi api;
    private final HyDragonStateStore stateStore;
    private final Supplier<HyDragonConfigRepository.Snapshot> configs;
    private final Supplier<FeatureGate> featureGate;
    private final MiniwyvernAbilityWorldDispatcher worlds;
    private final MiniwyvernAbilityService service;
    private final Clock clock;
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private boolean started;
    private String tickCursor;

    public MiniwyvernAbilityRuntime(
            TameworkApi api,
            HyDragonStateStore stateStore,
            Supplier<HyDragonConfigRepository.Snapshot> configs,
            Supplier<FeatureGate> featureGate,
            MiniwyvernAbilityWorldDispatcher worlds,
            MiniwyvernAbilityService service,
            Clock clock) {
        this.api = Objects.requireNonNull(api, "api");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.featureGate = Objects.requireNonNull(featureGate, "featureGate");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.service = Objects.requireNonNull(service, "service");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Registers public lifecycle events. Safe to replay; no duplicate subscriptions are created. */
    public synchronized void start() {
        if (started) return;
        subscriptions.add(api.events().subscribe(NpcProfileChangedEvent.class, this::onProfileChanged));
        subscriptions.add(api.events().subscribe(
                ProvisionedCompanionDeathRecordedEvent.class, this::onProvisionedDeath));
        subscriptions.add(api.events().subscribe(
                ProvisionedCompanionRevivedEvent.class, this::onProvisionedRevived));
        started = true;
    }

    /** Reconciles every persisted Soul Bond Miniwyvern; intended for a bounded periodic scheduler. */
    public void tickAll() {
        tickSome(Integer.MAX_VALUE);
    }

    /** Round-robin bounded polling entry point for the live server bridge. */
    public synchronized int tickSome(int maximumProfiles) {
        if (maximumProfiles <= 0) throw new IllegalArgumentException("maximumProfiles must be positive");
        List<String> profileIds = stateStore.snapshot().profileExtensions().values().stream()
                .filter(extension -> extension.kind() == ProfileKind.SOULBOUND_MINIWYVERN)
                .map(extension -> extension.profileId().toString())
                .sorted()
                .toList();
        if (profileIds.isEmpty()) {
            tickCursor = null;
            return 0;
        }
        int start = tickCursor == null ? 0 : insertionPointAfter(profileIds, tickCursor);
        int count = Math.min(maximumProfiles, profileIds.size());
        long nowMs = clock.millis();
        for (int offset = 0; offset < count; offset++) {
            String profileId = profileIds.get((start + offset) % profileIds.size());
            tickProfile(profileId, nowMs);
            tickCursor = profileId;
        }
        return count;
    }

    public void tickProfile(String profileId, long nowMs) {
        ProfileExtensionRecord extension = extension(profileId).orElse(null);
        if (extension == null || extension.kind() != ProfileKind.SOULBOUND_MINIWYVERN
                || extension.archetypeId().isEmpty()) {
            return;
        }
        NpcProfileView profile;
        try {
            profile = api.profiles().getByProfileId(profileId).orElse(null);
        } catch (RuntimeException failure) {
            return;
        }
        if (profile == null || profile.ownerUuid() == null || profile.currentNpcUuid() == null) return;
        boolean owned;
        try {
            owned = api.policies().isOwner(profileId, profile.ownerUuid());
        } catch (RuntimeException failure) {
            owned = false;
        }
        FeatureGate gate = featureGate.get();
        MiniwyvernAbilityService.ProfileContext context = new MiniwyvernAbilityService.ProfileContext(
                profileId,
                profile.ownerUuid(),
                profile.currentNpcUuid(),
                extension.archetypeId().orElseThrow(),
                owned,
                true,
                true,
                gate != null && gate.available());
        worlds.dispatch(profile.ownerUuid(), profile.currentNpcUuid(), world -> service.tick(
                context, configs.get().archetypes(), world, nowMs));
    }

    private void onProfileChanged(NpcProfileChangedEvent event) {
        if (event == null) return;
        tickProfile(event.profileId(), Math.max(0L, event.emittedAtMs()));
    }

    private void onProvisionedDeath(ProvisionedCompanionDeathRecordedEvent event) {
        if (event == null || !CALLER_NAMESPACE.equals(event.callerNamespace()) || event.lastNpcUuid() == null) return;
        deactivate(event.profileId(), event.ownerUuid(), event.lastNpcUuid(), event.emittedAtMs());
    }

    private void onProvisionedRevived(ProvisionedCompanionRevivedEvent event) {
        if (event == null || !CALLER_NAMESPACE.equals(event.callerNamespace()) || event.newNpcUuid() == null) return;
        tickProfile(event.profileId(), Math.max(0L, event.emittedAtMs()));
    }

    private void deactivate(String profileId, UUID ownerUuid, UUID npcUuid, long nowMs) {
        ProfileExtensionRecord extension = extension(profileId).orElse(null);
        if (extension == null || extension.archetypeId().isEmpty()) return;
        MiniwyvernAbilityService.ProfileContext context = new MiniwyvernAbilityService.ProfileContext(
                profileId, ownerUuid, npcUuid, extension.archetypeId().orElseThrow(),
                true, false, false, false);
        worlds.dispatch(ownerUuid, npcUuid, world -> service.deactivate(
                context, configs.get().archetypes(), world, Math.max(0L, nowMs)));
    }

    private Optional<ProfileExtensionRecord> extension(String profileId) {
        try {
            return stateStore.snapshot().profileExtension(UUID.fromString(profileId));
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized void close() {
        for (int index = subscriptions.size() - 1; index >= 0; index--) {
            try {
                subscriptions.get(index).close();
            } catch (Exception ignored) {
                // Every handle is attempted so shutdown cannot strand later subscriptions.
            }
        }
        subscriptions.clear();
        started = false;
        tickCursor = null;
    }

    private static int insertionPointAfter(List<String> values, String cursor) {
        int index = java.util.Collections.binarySearch(values, cursor);
        return index >= 0 ? (index + 1) % values.size() : Math.min(values.size() - 1, -index - 1);
    }
}
