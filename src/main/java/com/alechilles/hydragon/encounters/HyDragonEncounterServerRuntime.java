package com.alechilles.hydragon.encounters;

import com.alechilles.hydragon.abilities.MiniwyvernAbilityRuntime;
import com.alechilles.hydragon.config.DragonEncounterConfig;
import com.alechilles.hydragon.config.HyDragonConfigRepository;
import com.alechilles.hydragon.persistence.EncounterRecord;
import com.alechilles.hydragon.runtime.ConsumableSagaRecoveryRuntime;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Live server bridge for bounded encounter admission/lifecycle polling and Miniwyvern ability ticks.
 * Candidate extraction and damage routing always execute on the owning Hytale world thread.
 */
public final class HyDragonEncounterServerRuntime implements AutoCloseable {
    static final int MAX_PLAYERS_PER_WORLD_SCAN = 32;
    static final int MAX_DEFINITIONS_PER_PLAYER = 8;
    static final int MAX_ENCOUNTERS_PER_TICK = 64;
    static final int MAX_MINIWYVERNS_PER_TICK = 64;
    static final int MAX_SAGAS_PER_TICK = 16;
    private static final long TICK_PERIOD_SECONDS = 1L;
    private static final int ADMISSION_SCAN_INTERVAL_TICKS = 5;
    private static final int REGION_SIZE_BLOCKS = 512;

    private final HytaleEncounterWorldDispatcher worlds;
    private final ComponentType<EntityStore, HyDragonEncounterComponent> markerType;
    private final Map<String, AtomicBoolean> queuedWorldScans = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduled;
    private volatile Bindings bindings;
    private int cycle;

    HyDragonEncounterServerRuntime(ComponentType<EntityStore, HyDragonEncounterComponent> markerType) {
        this.markerType = Objects.requireNonNull(markerType, "markerType");
        this.worlds = new HytaleEncounterWorldDispatcher(markerType);
    }

    public HytaleEncounterWorldDispatcher worlds() { return worlds; }

    /** Binds installed feature runtimes and starts one daemon, non-overlapping bounded poller. */
    public void start(
            DynamicEncounterRuntime encounters,
            MiniwyvernAbilityRuntime abilities,
            ConsumableSagaRecoveryRuntime sagaRecovery,
            Supplier<HyDragonConfigRepository.Snapshot> configs) {
        Objects.requireNonNull(encounters, "encounters");
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(sagaRecovery, "sagaRecovery");
        Objects.requireNonNull(configs, "configs");
        synchronized (lifecycleLock) {
            if (scheduled != null) return;
            bindings = new Bindings(encounters, abilities, sagaRecovery, configs);
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "HyDragon-live-runtime");
                thread.setDaemon(true);
                return thread;
            });
            scheduled = executor.scheduleWithFixedDelay(
                    this::safeCycle, 0L, TICK_PERIOD_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void safeCycle() {
        try {
            Bindings active = bindings;
            if (active == null) return;
            active.encounters().tickSome(MAX_ENCOUNTERS_PER_TICK);
            active.abilities().tickSome(MAX_MINIWYVERNS_PER_TICK);
            active.sagaRecovery().tickSome(MAX_SAGAS_PER_TICK);
            cycle++;
            if (cycle % ADMISSION_SCAN_INTERVAL_TICKS == 0) queueAdmissionScans(active);
        } catch (RuntimeException ignored) {
            // One failed poll must not terminate all future lifecycle reconciliation.
        }
    }

    private void queueAdmissionScans(Bindings active) {
        Universe universe = Universe.get();
        if (universe == null) return;
        for (World world : universe.getWorlds().values()) {
            AtomicBoolean queued = queuedWorldScans.computeIfAbsent(world.getName(), ignored -> new AtomicBoolean());
            if (!queued.compareAndSet(false, true)) continue;
            try {
                world.execute(() -> {
                    try {
                        scanWorld(world, active);
                    } finally {
                        queued.set(false);
                    }
                });
            } catch (RejectedExecutionException failure) {
                queued.set(false);
            }
        }
    }

    private void scanWorld(World world, Bindings active) {
        if (!world.isInThread()) return;
        HyDragonConfigRepository.Snapshot snapshot = active.configs().get();
        if (snapshot == null || !snapshot.isValid()) return;
        List<DragonEncounterConfig> definitions = snapshot.encounters().values().stream()
                .filter(DragonEncounterConfig::isEnabled)
                .sorted(java.util.Comparator.comparing(DragonEncounterConfig::getId))
                .limit(MAX_DEFINITIONS_PER_PLAYER)
                .toList();
        if (definitions.isEmpty()) return;

        Store<EntityStore> store = world.getEntityStore().getStore();
        int[] visited = {0};
        store.forEachChunk(PlayerRef.getComponentType(), (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size() && visited[0] < MAX_PLAYERS_PER_WORLD_SCAN; index++) {
                PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
                TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
                WeatherTracker tracker = chunk.getComponent(index, WeatherTracker.getComponentType());
                if (player == null || transform == null || tracker == null || !player.isValid()) continue;
                visited[0]++;
                EncounterCandidate candidate = candidate(world, chunk.getReferenceTo(index), player, transform, tracker, store);
                if (candidate == null) continue;
                for (DragonEncounterConfig definition : definitions) {
                    worlds.dispatch(world.getName(), null,
                            gateway -> active.encounters().admit(definition.getId(), candidate, gateway));
                }
            }
        });
    }

    private static EncounterCandidate candidate(
            World world,
            Ref<EntityStore> playerRef,
            PlayerRef player,
            TransformComponent transform,
            WeatherTracker tracker,
            Store<EntityStore> store) {
        Environment environment = Environment.getAssetMap().getAsset(tracker.getEnvironmentId());
        Weather weather = Weather.getAssetMap().getAsset(tracker.getWeatherIndex());
        if (environment == null || weather == null) return null;
        var position = transform.getPosition();
        long regionX = Math.floorDiv((long) Math.floor(position.x()), REGION_SIZE_BLOCKS);
        long regionZ = Math.floorDiv((long) Math.floor(position.z()), REGION_SIZE_BLOCKS);
        String regionKey = world.getName() + ':' + regionX + ':' + regionZ;
        Set<String> items = accessibleItems(playerRef, store);
        return new EncounterCandidate(
                player.getUuid(),
                world.getName(),
                regionKey,
                environment.getId(),
                position.x(), position.y(), position.z(),
                Set.of(weather.getId()),
                items);
    }

    private static Set<String> accessibleItems(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        var inventory = InventoryComponent.getCombined(store, playerRef, InventoryComponent.EVERYTHING);
        if (inventory == null) return Set.of();
        Set<String> items = new LinkedHashSet<>();
        inventory.forEach((slot, stack) -> {
            if (!ItemStack.isEmpty(stack) && stack.getItemId() != null && !stack.getItemId().isBlank()) {
                items.add(stack.getItemId());
            }
        });
        return Set.copyOf(items);
    }

    void onEncounterDamage(
            String encounterId,
            Ref<EntityStore> targetRef,
            Damage damage,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        Bindings active = bindings;
        if (active == null || !(damage.getSource() instanceof Damage.EntitySource entitySource)) return;
        Ref<EntityStore> sourceRef = entitySource.getRef();
        if (sourceRef == null || !sourceRef.isValid()
                || commandBuffer.getComponent(sourceRef, PlayerRef.getComponentType()) == null) {
            return;
        }
        UUIDComponent targetIdentity = store.getComponent(targetRef, UUIDComponent.getComponentType());
        if (targetIdentity == null || targetIdentity.getUuid() == null) return;
        EncounterRecord record = active.encounters().find(encounterId);
        if (record == null) return;
        DragonEncounterConfig definition = active.configs().get().encounters().get(record.definitionId());
        if (definition == null) return;
        String sourceId = resolveGroundingSource(definition, damage, sourceRef, store);
        if (sourceId == null) return;
        float amount = damage.getAmount();
        if (!Float.isFinite(amount) || amount <= 0.0F) return;
        World world = store.getExternalData().getWorld();
        worlds.dispatch(world.getName(), targetIdentity.getUuid(), gateway -> active.encounters().groundingHit(
                encounterId, targetIdentity.getUuid(), sourceId, amount, gateway));
    }

    static String resolveGroundingSource(
            DragonEncounterConfig definition,
            Damage damage,
            Ref<EntityStore> sourceRef,
            Store<EntityStore> store) {
        Set<String> allowed = Set.copyOf(definition.getGrounding().getBuildupSourceIds());
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String projectileId = null;
        if (damage.getSource() instanceof Damage.ProjectileSource projectileSource) {
            Ref<EntityStore> projectileRef = projectileSource.getProjectile();
            ProjectileComponent projectile = projectileRef != null && projectileRef.isValid()
                    ? store.getComponent(projectileRef, ProjectileComponent.getComponentType()) : null;
            if (projectile != null && projectile.getProjectileAssetName() != null) {
                projectileId = projectile.getProjectileAssetName();
            }
        }
        ItemStack held = InventoryComponent.getItemInHand(store, sourceRef);
        String itemId = ItemStack.isEmpty(held) ? null : held.getItemId();
        String damageCauseId = damage.getCause() == null ? null : damage.getCause().getId();
        candidates.addAll(groundingSourceCandidates(projectileId, itemId, damageCauseId));
        return candidates.stream().filter(allowed::contains).findFirst().orElse(null);
    }

    static List<String> groundingSourceCandidates(String projectileId, String itemId, String damageCauseId) {
        String projectile = sourcePart("projectile", projectileId);
        String item = sourcePart("item", itemId);
        String cause = sourcePart("damage_cause", damageCauseId);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCombination(candidates, projectile, item, cause);
        addCombination(candidates, projectile, item);
        addCombination(candidates, projectile, cause);
        addCombination(candidates, item, cause);
        addCombination(candidates, projectile);
        addCombination(candidates, item);
        addCombination(candidates, cause);
        return List.copyOf(candidates);
    }

    private static String sourcePart(String kind, String id) {
        return id == null || id.isBlank() ? null : kind + ':' + id.trim();
    }

    private static void addCombination(Set<String> candidates, String... parts) {
        for (String part : parts) if (part == null) return;
        candidates.add(String.join("+", parts));
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            bindings = null;
            if (scheduled != null) scheduled.cancel(false);
            scheduled = null;
            if (executor != null) executor.shutdownNow();
            executor = null;
            queuedWorldScans.clear();
            cycle = 0;
        }
    }

    private record Bindings(
            DynamicEncounterRuntime encounters,
            MiniwyvernAbilityRuntime abilities,
            ConsumableSagaRecoveryRuntime sagaRecovery,
            Supplier<HyDragonConfigRepository.Snapshot> configs) {
    }
}
