package com.alechilles.hydragon.encounters;

import com.alechilles.alecstamework.api.CaptureAttemptResolvedEvent;
import com.alechilles.alecstamework.api.CaptureRequirementDecision;
import com.alechilles.alecstamework.api.CaptureRequirementSpec;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.hydragon.config.DragonEncounterConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.integration.FeatureGate;
import com.alechilles.hydragon.persistence.EncounterRecord;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Capture-policy registration, event dispatch, restart reconciliation, and timeout ticking facade. */
public final class DynamicEncounterRuntime implements AutoCloseable {
    public static final String CAPTURE_REQUIREMENT_ID = "hydragon:special_encounter_capture_ready";
    private static final System.Logger LOGGER = System.getLogger(DynamicEncounterRuntime.class.getName());

    private final TameworkApi api;
    private final HyDragonStateStore stateStore;
    private final Supplier<HyDragonConfigRepository.Snapshot> configs;
    private final Supplier<FeatureGate> gate;
    private final EncounterWorldDispatcher worlds;
    private final DynamicEncounterCoordinator coordinator;
    private final FullDragonProfileProjection fullDragonProfiles;
    private final DurableProfileProjectionQueue profileProjections;
    private final Clock clock;
    private final List<AutoCloseable> handles = new ArrayList<>();
    private boolean started;
    private String tickCursor;

    public DynamicEncounterRuntime(
            TameworkApi api,
            HyDragonStateStore stateStore,
            Supplier<HyDragonConfigRepository.Snapshot> configs,
            Supplier<FeatureGate> gate,
            EncounterWorldDispatcher worlds,
            DynamicEncounterCoordinator coordinator,
            Clock clock) {
        this.api = Objects.requireNonNull(api, "api");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.configs = Objects.requireNonNull(configs, "configs");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.fullDragonProfiles = new FullDragonProfileProjection(stateStore, configs);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.profileProjections = new DurableProfileProjectionQueue(stateStore, fullDragonProfiles, clock);
    }

    public synchronized void start() {
        if (started) return;
        handles.add(api.interactionExtensions().registerCaptureRequirement(
                CAPTURE_REQUIREMENT_ID,
                (context, spec) -> captureRequirement(context.targetNpcUuid(), spec)));
        handles.add(api.events().subscribe(CaptureAttemptResolvedEvent.class, this::onCaptureResolved));
        started = true;
    }

    public DynamicEncounterCoordinator.AdmissionResult admit(
            String definitionId,
            EncounterCandidate candidate,
            EncounterWorldGateway world) {
        HyDragonConfigRepository.Snapshot snapshot = configs.get();
        DragonEncounterConfig definition = snapshot.encounters().get(definitionId);
        if (definition == null) {
            return new DynamicEncounterCoordinator.AdmissionResult(
                    false, "encounter-definition-missing", null, null);
        }
        FeatureGate featureGate = gate.get();
        return coordinator.admit(definition, snapshot, candidate, world,
                featureGate != null && featureGate.available(), clock.millis());
    }

    public DynamicEncounterCoordinator.TransitionResult groundingHit(
            String encounterId,
            UUID targetNpcUuid,
            String sourceId,
            double buildup,
            EncounterWorldGateway world) {
        EncounterRecord record = stateStore.snapshot().encounters().get(encounterId);
        DragonEncounterConfig definition = record == null ? null : configs.get().encounters().get(record.definitionId());
        if (definition == null) {
            return new DynamicEncounterCoordinator.TransitionResult(
                    false, "encounter-definition-missing", EncounterPhase.RECONCILING, 0.0D);
        }
        return coordinator.groundingHit(
                encounterId, targetNpcUuid, sourceId, buildup, definition, world, clock.millis());
    }

    /** Read-only lookup used by the registered Hytale damage bridge. */
    public EncounterRecord find(String encounterId) {
        if (encounterId == null || encounterId.isBlank()) return null;
        return stateStore.snapshot().encounters().get(encounterId);
    }

    public void reconcileAll() {
        long now = clock.millis();
        for (EncounterRecord record : stateStore.snapshot().encounters().values()) {
            DragonEncounterConfig definition = configs.get().encounters().get(record.definitionId());
            if (definition == null) continue;
            worlds.dispatch(record.worldName(), record.targetNpcUuid().orElse(null),
                    world -> coordinator.reconcile(record.encounterId(), definition, world, now));
        }
    }

    public void tickAll() {
        tickSome(Integer.MAX_VALUE);
    }

    /** Round-robin bounded polling entry point for the live server bridge. */
    public synchronized int tickSome(int maximumRecords) {
        if (maximumRecords <= 0) throw new IllegalArgumentException("maximumRecords must be positive");
        retryProfileProjections(Math.min(16, maximumRecords));
        long now = clock.millis();
        List<EncounterRecord> records = stateStore.snapshot().encounters().values().stream()
                .sorted(java.util.Comparator.comparing(EncounterRecord::encounterId))
                .toList();
        if (records.isEmpty()) {
            tickCursor = null;
            return 0;
        }
        int start = insertionPointAfter(records, tickCursor);
        int count = Math.min(maximumRecords, records.size());
        for (int offset = 0; offset < count; offset++) {
            EncounterRecord record = records.get((start + offset) % records.size());
            DragonEncounterConfig definition = configs.get().encounters().get(record.definitionId());
            if (definition != null) {
                worlds.dispatch(record.worldName(), record.targetNpcUuid().orElse(null),
                        world -> coordinator.tick(record.encounterId(), definition, world, now));
            }
            tickCursor = record.encounterId();
        }
        return count;
    }

    private CaptureRequirementDecision captureRequirement(UUID targetNpcUuid, CaptureRequirementSpec spec) {
        FeatureGate featureGate = gate.get();
        if (featureGate == null || !featureGate.available()) {
            return CaptureRequirementDecision.deny("hydragon-dynamic-encounters-unavailable");
        }
        if (spec == null || !CAPTURE_REQUIREMENT_ID.equals(spec.id())) {
            return CaptureRequirementDecision.deny("hydragon-capture-requirement-mismatch");
        }
        return coordinator.captureAllowed(targetNpcUuid)
                ? CaptureRequirementDecision.allow()
                : CaptureRequirementDecision.deny("hydragon-target-not-grounded");
    }

    private void onCaptureResolved(CaptureAttemptResolvedEvent event) {
        if (event == null) return;
        FullDragonProfileProjection.Result projection = profileProjections.accept(event);
        if (projection == FullDragonProfileProjection.Result.INVALID
                || projection == FullDragonProfileProjection.Result.AMBIGUOUS
                || projection == FullDragonProfileProjection.Result.CONFLICT) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "HyDragon could not project captured full-dragon profile for operation {0}: {1}",
                    event.operationId(), projection);
        }
        EncounterRecord record = stateStore.snapshot().encounters().values().stream()
                .filter(candidate -> candidate.targetNpcUuid().filter(event.targetNpcUuid()::equals).isPresent())
                .findFirst().orElse(null);
        if (record == null) return;
        DragonEncounterConfig definition = configs.get().encounters().get(record.definitionId());
        if (definition == null) return;
        worlds.dispatch(record.worldName(), event.targetNpcUuid(),
                world -> coordinator.onCaptureResolved(event, definition, world));
    }

    private void retryProfileProjections(int maximum) {
        profileProjections.retrySome(maximum);
    }

    public int pendingProfileProjectionCount() {
        return profileProjections.pendingCount();
    }

    @Override
    public synchronized void close() {
        for (int index = handles.size() - 1; index >= 0; index--) {
            try {
                handles.get(index).close();
            } catch (Exception ignored) {
                // Continue releasing the remaining public API handles.
            }
        }
        handles.clear();
        started = false;
        tickCursor = null;
    }

    private static int insertionPointAfter(List<EncounterRecord> records, String cursor) {
        if (cursor == null) return 0;
        int low = 0;
        int high = records.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int comparison = records.get(middle).encounterId().compareTo(cursor);
            if (comparison <= 0) low = middle + 1;
            else high = middle - 1;
        }
        return low >= records.size() ? 0 : low;
    }
}
