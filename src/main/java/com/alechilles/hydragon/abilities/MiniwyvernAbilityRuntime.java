package com.alechilles.hydragon.abilities;

import com.alechilles.alecstamework.api.BondedCompanionChangedEvent;
import com.alechilles.alecstamework.api.BondedCompanionLeaseView;
import com.alechilles.alecstamework.api.BondedCompanionProfileView;
import com.alechilles.alecstamework.api.BondedCompanionResult;
import com.alechilles.alecstamework.api.BondedCompanionStateView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionCodec;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionDocument;
import com.alechilles.hydragon.bonded.BondedMiniwyvernExtensionStore;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.PlayerSoulBondRecord;
import com.alechilles.hydragon.persistence.SoulBondState;
import com.alechilles.hydragon.runtime.TameworkGameplayAdapter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Runs Miniwyvern abilities only for exact active bonded projection leases. */
public final class MiniwyvernAbilityRuntime implements AutoCloseable {
    public static final String CALLER_NAMESPACE = TameworkGameplayAdapter.CALLER_NAMESPACE;
    private static final String SPECIES_ID = "miniwyvern";
    private static final Logger LOGGER = Logger.getLogger(
            MiniwyvernAbilityRuntime.class.getName());

    private final TameworkGameplayAdapter tamework;
    private final BondedMiniwyvernExtensionStore extensions;
    private final HyDragonStateStore stateStore;
    private final Supplier<HyDragonConfigRepository.Snapshot> configs;
    private final Supplier<FeatureGate> featureGate;
    private final MiniwyvernAbilityWorldDispatcher worlds;
    private final MiniwyvernAbilityService service;
    private final Clock clock;
    private final Map<String, ActiveBinding> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> refreshing = new ConcurrentHashMap<>();
    private final java.util.Set<String> reportedDegradations =
            ConcurrentHashMap.newKeySet();
    private AutoCloseable subscription;
    private volatile boolean started;
    private String tickCursor;

    public MiniwyvernAbilityRuntime(
            TameworkApi api,
            HyDragonStateStore stateStore,
            Supplier<HyDragonConfigRepository.Snapshot> configs,
            Supplier<FeatureGate> featureGate,
            MiniwyvernAbilityWorldDispatcher worlds,
            MiniwyvernAbilityService service,
            Clock clock) {
        tamework = new TameworkGameplayAdapter(Objects.requireNonNull(api, "api"));
        extensions = new BondedMiniwyvernExtensionStore(
                tamework, new BondedMiniwyvernExtensionCodec());
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.featureGate = Objects.requireNonNull(featureGate, "featureGate");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.service = Objects.requireNonNull(service, "service");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Subscribes to the dedicated bonded lifecycle and bootstraps claimed owners. */
    public synchronized void start() {
        if (started) return;
        subscription = tamework.subscribeBondedChanges(this::onBondedChanged);
        started = true;
        refreshClaims();
    }

    /** Reconciles every currently active bonded Miniwyvern. */
    public void tickAll() {
        tickSome(Integer.MAX_VALUE);
    }

    /** Round-robin bounded polling entry point for the live server bridge. */
    public synchronized int tickSome(int maximumProfiles) {
        if (maximumProfiles <= 0) {
            throw new IllegalArgumentException("maximumProfiles must be positive");
        }
        refreshClaims();
        List<ActiveBinding> bindings = active.values().stream()
                .sorted(java.util.Comparator.comparing(ActiveBinding::profileId))
                .toList();
        if (bindings.isEmpty()) {
            tickCursor = null;
            return 0;
        }
        List<String> ids = bindings.stream().map(ActiveBinding::profileId).toList();
        int startIndex = tickCursor == null ? 0 : insertionPointAfter(ids, tickCursor);
        int count = Math.min(maximumProfiles, bindings.size());
        long nowMs = Math.max(0L, clock.millis());
        for (int offset = 0; offset < count; offset++) {
            ActiveBinding binding = bindings.get((startIndex + offset) % bindings.size());
            tick(binding, nowMs);
            tickCursor = binding.profileId();
        }
        return count;
    }

    /** Ticks a cached active profile and requests a refresh when it is not cached. */
    public void tickProfile(String profileId, long nowMs) {
        String profile = requiredText(profileId, "profileId");
        ActiveBinding binding = active.get(profile);
        if (binding != null) {
            tick(binding, Math.max(0L, nowMs));
            return;
        }
        claimedOwner(profile).ifPresent(this::scheduleRefresh);
    }

    private void tick(ActiveBinding binding, long nowMs) {
        FeatureGate gate = featureGate.get();
        MiniwyvernAbilityService.ProfileContext context = context(
                binding, true, gate != null && gate.available());
        worlds.dispatch(binding.ownerUuid(), binding.npcUuid(), world -> {
            MiniwyvernAbilityService.TickResult result = service.tick(
                    context, configs.get().archetypes(), world, nowMs);
            reportDegradation(binding, result);
        });
    }

    private void reportDegradation(
            ActiveBinding binding,
            MiniwyvernAbilityService.TickResult result) {
        if (!result.ready()
                || !result.reason().startsWith("ready-with-degraded-semantics:")) {
            return;
        }
        String diagnosticKey = binding.archetypeId() + ':' + result.reason();
        if (reportedDegradations.add(diagnosticKey)) {
            LOGGER.warning("Miniwyvern archetype '" + binding.archetypeId()
                    + "' remains active with unavailable optional semantics: "
                    + result.reason());
        }
    }

    private void refreshClaims() {
        if (!started) return;
        stateStore.snapshot().playerSoulBonds().values().stream()
                .filter(record -> record.state() == SoulBondState.CLAIMED)
                .map(PlayerSoulBondRecord::playerUuid)
                .forEach(this::scheduleRefresh);
    }

    private void scheduleRefresh(UUID ownerUuid) {
        if (!started) return;
        long generation = generations.getOrDefault(ownerUuid, 0L);
        if (refreshing.putIfAbsent(ownerUuid, generation) != null) return;
        try {
            tamework.listDragonHorn(ownerUuid).whenComplete((result, failure) -> {
                refreshing.remove(ownerUuid, generation);
                if (!started || failure != null
                        || generation != currentGeneration(ownerUuid)
                        || result == null || !result.successful()
                        || result.value() == null) {
                    return;
                }
                reconcile(ownerUuid, generation, result);
            });
        } catch (RuntimeException | LinkageError failure) {
            refreshing.remove(ownerUuid, generation);
        }
    }

    private void reconcile(
            UUID ownerUuid,
            long generation,
            BondedCompanionResult<List<BondedCompanionProfileView>> result) {
        String claimedProfile = claimedProfile(ownerUuid);
        Map<String, BondedCompanionProfileView> desired = new LinkedHashMap<>();
        if (claimedProfile != null) {
            for (BondedCompanionProfileView profile : result.value()) {
                if (activeMiniwyvern(ownerUuid, claimedProfile, profile)) {
                    desired.put(profile.profileId(), profile);
                }
            }
        }
        removeStale(ownerUuid, desired);
        desired.values().forEach(profile -> loadBinding(ownerUuid, generation, profile));
    }

    private void removeStale(
            UUID ownerUuid,
            Map<String, BondedCompanionProfileView> desired) {
        for (ActiveBinding binding : new ArrayList<>(active.values())) {
            if (!ownerUuid.equals(binding.ownerUuid())) continue;
            BondedCompanionProfileView profile = desired.get(binding.profileId());
            if (profile != null && sameProjection(binding, profile.activeLease())) continue;
            if (active.remove(binding.profileId(), binding)) {
                deactivate(binding, clock.millis());
            }
        }
    }

    private void loadBinding(
            UUID ownerUuid,
            long generation,
            BondedCompanionProfileView profile) {
        try {
            extensions.load(ownerUuid, profile.profileId()).whenComplete((read, failure) -> {
                if (!started || failure != null
                        || generation != currentGeneration(ownerUuid)
                        || !loadedMiniwyvern(read)) {
                    return;
                }
                BondedCompanionLeaseView lease = profile.activeLease();
                ActiveBinding candidate = new ActiveBinding(
                        profile.profileId(), ownerUuid, lease.liveNpcUuid(),
                        lease.leaseToken(), lease.worldKey(),
                        read.document().archetypeId());
                ActiveBinding previous = active.put(profile.profileId(), candidate);
                if (previous != null && !sameProjection(previous, lease)) {
                    deactivate(previous, clock.millis());
                }
            });
        } catch (RuntimeException failure) {
            // A later bounded refresh retries without using generic profile state.
        }
    }

    private void onBondedChanged(BondedCompanionChangedEvent event) {
        String claimed = event == null ? null : claimedProfile(event.ownerUuid());
        if (event == null
                || !TameworkGameplayAdapter.DRAGON_HORN_ROSTER.equals(event.rosterId())
                || !event.profileId().equals(claimed)) {
            return;
        }
        invalidate(event.ownerUuid());
        if (event.newState() != BondedCompanionStateView.ACTIVE) {
            ActiveBinding removed = active.remove(event.profileId());
            if (removed != null) deactivate(removed, clock.millis());
        }
        scheduleRefresh(event.ownerUuid());
    }

    private void deactivate(ActiveBinding binding, long nowMs) {
        worlds.dispatch(binding.ownerUuid(), binding.npcUuid(), world -> service.deactivate(
                context(binding, false, false), configs.get().archetypes(),
                world, Math.max(0L, nowMs)));
    }

    private MiniwyvernAbilityService.ProfileContext context(
            ActiveBinding binding,
            boolean activeState,
            boolean available) {
        return new MiniwyvernAbilityService.ProfileContext(
                binding.profileId(), binding.ownerUuid(), binding.npcUuid(),
                binding.archetypeId(), true, activeState, activeState, available);
    }

    private String claimedProfile(UUID ownerUuid) {
        PlayerSoulBondRecord record = stateStore.snapshot()
                .playerSoulBond(ownerUuid).orElse(null);
        return record != null && record.state() == SoulBondState.CLAIMED
                && record.profileId().isPresent()
                ? record.profileId().orElseThrow().toString() : null;
    }

    private java.util.Optional<UUID> claimedOwner(String profileId) {
        return stateStore.snapshot().playerSoulBonds().values().stream()
                .filter(record -> record.state() == SoulBondState.CLAIMED)
                .filter(record -> record.profileId().map(UUID::toString)
                        .filter(profileId::equals).isPresent())
                .map(PlayerSoulBondRecord::playerUuid)
                .findFirst();
    }

    private void invalidate(UUID ownerUuid) {
        generations.compute(ownerUuid, (ignored, value) ->
                value == null || value == Long.MAX_VALUE ? 1L : value + 1L);
        refreshing.remove(ownerUuid);
    }

    private long currentGeneration(UUID ownerUuid) {
        return generations.getOrDefault(ownerUuid, 0L);
    }

    private static boolean activeMiniwyvern(
            UUID ownerUuid,
            String claimedProfile,
            BondedCompanionProfileView profile) {
        BondedCompanionLeaseView lease = profile == null ? null : profile.activeLease();
        return profile != null
                && ownerUuid.equals(profile.ownerUuid())
                && claimedProfile.equals(profile.profileId())
                && TameworkGameplayAdapter.DRAGON_HORN_ROSTER.equals(profile.rosterId())
                && TameworkGameplayAdapter.MINIWYVERN_FAMILY.equals(profile.familyId())
                && TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE.equals(profile.roleId())
                && profile.state() == BondedCompanionStateView.ACTIVE
                && lease != null && lease.liveNpcUuid() != null;
    }

    private static boolean loadedMiniwyvern(
            BondedMiniwyvernExtensionStore.ReadResult read) {
        BondedMiniwyvernExtensionDocument document = read == null ? null : read.document();
        return read != null
                && read.status() == BondedMiniwyvernExtensionStore.ReadStatus.LOADED
                && document != null && SPECIES_ID.equals(document.speciesId());
    }

    private static boolean sameProjection(
            ActiveBinding binding,
            BondedCompanionLeaseView lease) {
        return lease != null
                && binding.npcUuid().equals(lease.liveNpcUuid())
                && binding.leaseToken().equals(lease.leaseToken())
                && binding.worldKey().equals(lease.worldKey());
    }

    @Override
    public synchronized void close() {
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // The runtime still clears all local handles after a close failure.
            }
        }
        subscription = null;
        started = false;
        active.clear();
        refreshing.clear();
        generations.clear();
        tickCursor = null;
        reportedDegradations.clear();
    }

    private static int insertionPointAfter(List<String> values, String cursor) {
        int low = 0;
        int high = values.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (values.get(middle).compareTo(cursor) <= 0) low = middle + 1;
            else high = middle - 1;
        }
        return low >= values.size() ? 0 : low;
    }

    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private record ActiveBinding(
            String profileId,
            UUID ownerUuid,
            UUID npcUuid,
            String leaseToken,
            String worldKey,
            String archetypeId) {
        private ActiveBinding {
            profileId = requiredText(profileId, "profileId");
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            Objects.requireNonNull(npcUuid, "npcUuid");
            leaseToken = requiredText(leaseToken, "leaseToken");
            worldKey = requiredText(worldKey, "worldKey");
            archetypeId = requiredText(archetypeId, "archetypeId");
        }
    }
}
