package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.BondedVesselOperationResult;
import com.alechilles.alecstamework.api.BondedVesselProjectionStatus;
import com.alechilles.alecstamework.api.BondedVesselProjectionValidationRequest;
import com.alechilles.alecstamework.api.BondedVesselProjectionValidationStatus;
import com.alechilles.alecstamework.api.BondedVesselProjectionValidationView;
import com.alechilles.alecstamework.api.BondedVesselState;
import com.alechilles.alecstamework.api.BondedVesselTransition;
import com.alechilles.alecstamework.api.BondedVesselTransitionContext;
import com.alechilles.alecstamework.api.BondedVesselTransitionRequest;
import com.alechilles.alecstamework.api.CaptureChanceMode;
import com.alechilles.alecstamework.api.CapturePolicyConfigView;
import com.alechilles.alecstamework.api.CaptureRequirementContext;
import com.alechilles.alecstamework.api.CaptureRequirementDecision;
import com.alechilles.alecstamework.api.CaptureRequirementPhase;
import com.alechilles.alecstamework.api.CommandLinksApi;
import com.alechilles.alecstamework.api.CompanionProvisioningApi;
import com.alechilles.alecstamework.api.CompanionProvisioningOperationView;
import com.alechilles.alecstamework.api.CompanionProvisioningProjectionStatus;
import com.alechilles.alecstamework.api.CompanionProvisioningRequest;
import com.alechilles.alecstamework.api.CompanionProvisioningResult;
import com.alechilles.alecstamework.api.DiagnosticsApi;
import com.alechilles.alecstamework.api.InteractionExtensionApi;
import com.alechilles.alecstamework.api.NpcProfilesApi;
import com.alechilles.alecstamework.api.PolicyApi;
import com.alechilles.alecstamework.api.PopulationAdmissionForcePolicy;
import com.alechilles.alecstamework.api.PopulationAdmissionLocation;
import com.alechilles.alecstamework.api.PopulationCompanionLifecycle;
import com.alechilles.alecstamework.api.PopulationGroupDefinitionView;
import com.alechilles.alecstamework.api.PopulationGroupScope;
import com.alechilles.alecstamework.api.ProfileDataApi;
import com.alechilles.alecstamework.api.ProgressionApi;
import com.alechilles.alecstamework.api.ProvisionedCompanionTransitionRequest;
import com.alechilles.alecstamework.api.ProvisionedCompanionView;
import com.alechilles.alecstamework.api.TameworkApi;
import com.alechilles.alecstamework.api.TameworkApiCapability;
import com.alechilles.alecstamework.api.TameworkConfigReadApi;
import com.alechilles.alecstamework.api.TameworkEventsApi;
import com.alechilles.alecstamework.api.TraitEffectApi;
import com.alechilles.alecstamework.api.internal.CaptureRequirementRuntime;
import com.alechilles.alecstamework.config.ItemFeatureConfig;
import com.alechilles.alecstamework.items.CommandLinkedNpcCaptureService;
import com.alechilles.alecstamework.items.capturepolicy.SpawnerCaptureChanceService;
import com.alechilles.alecstamework.ownership.CompanionLifecycleState;
import com.alechilles.alecstamework.ownership.groups.PopulationGroupAdmissionPolicy;
import com.alechilles.alecstamework.ownership.groups.PopulationGroupBucket;
import com.alechilles.alecstamework.ownership.groups.PopulationGroupCountDelta;
import com.alechilles.alecstamework.ownership.groups.PopulationGroupCounts;
import com.alechilles.alecstamework.ownership.groups.PopulationGroupIndex;
import com.alechilles.alecstamework.persistence.sqlite.BondedVesselBindingRecord;
import com.alechilles.alecstamework.persistence.sqlite.BondedVesselOperationRecord;
import com.alechilles.alecstamework.persistence.sqlite.NpcProfileRepository;
import com.alechilles.alecstamework.persistence.sqlite.TameworkPersistenceRuntime;
import com.alechilles.alecstamework.selftest.ApiSelfTestAssertion;
import com.alechilles.alecstamework.vessels.BondedVesselCoordinator;
import com.alechilles.alecstamework.vessels.BondedVesselEvidenceAuthority;
import com.alechilles.alecstamework.vessels.SqliteBondedVesselJournal;
import com.alechilles.alecstamework.vessels.runtime.BondedVesselInitialBindingService;
import com.alechilles.alecstamework.vessels.runtime.ProductionBondedVesselMutationAuthority;
import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.runtime.ConsumableReservation;
import com.alechilles.hydragon.runtime.GameplayResult;
import com.alechilles.hydragon.runtime.OperationJournal;
import com.alechilles.hydragon.runtime.SoulBondService;
import com.alechilles.hydragon.runtime.StateStoreOperationJournal;
import com.alechilles.hydragon.runtime.StateStoreSoulBondLedger;
import com.alechilles.hydragon.runtime.TameworkGameplayAdapter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verify-stage release gate spanning the packaged Tamework runtime and HyDragon's production
 * orchestration. Tamework deliberately owns capture and vessel mutations, so this test invokes
 * those packaged production coordinators rather than fabricating a downstream capture endpoint.
 */
class PackagedHyDragonTameworkLoopIT {
    private static final UUID OWNER = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final String DRAGON_ROLE = "HyDragon_NordicDrake";
    private static final String WORLD = "default";

    @TempDir
    Path tempDir;

    @Test
    void packagedPluginsCompleteTheCaptureVesselAndSoulBondLoop() throws Exception {
        assertPackagedTameworkDependency();
        assertPackagedBehavioralReleaseGate();
        assertTierQualificationUsesTheProductionCapturePolicy();

        try (VesselHarness vessels = new VesselHarness(tempDir.resolve("vessels"))) {
            VesselIdentity first = vessels.bindCapturedDragon("first");
            VesselIdentity second = vessels.bindCapturedDragon("second");
            assertEquals(1L, vessels.binding(first).generation(),
                    "A successful capture must create generation-one authority.");

            BondedVesselOperationResult summoned = vessels.transition(first, BondedVesselTransition.SUMMON);
            assertEquals(BondedVesselOperationResult.Status.COMMITTED, summoned.status());
            BondedVesselBindingRecord active = vessels.binding(first);
            assertEquals(BondedVesselBindingRecord.LifecycleState.ACTIVE, active.lifecycleState());
            UUID firstProjection = active.activeNpcUuid();
            assertNotNull(firstProjection);

            BondedVesselOperationResult denied = vessels.transition(second, BondedVesselTransition.SUMMON);
            assertEquals(BondedVesselOperationResult.Status.DENIED, denied.status(),
                    "The second full dragon must lose the same atomic active-group admission.");
            assertEquals(1L, vessels.binding(second).generation());

            BondedVesselOperationResult stored = vessels.transition(first, BondedVesselTransition.STORE);
            assertEquals(BondedVesselOperationResult.Status.COMMITTED, stored.status());
            BondedVesselBindingRecord afterStore = vessels.binding(first);
            assertEquals(BondedVesselBindingRecord.LifecycleState.STORED, afterStore.lifecycleState());
            assertEquals(first.profileId(), afterStore.profileId());
            assertEquals(first.bindingId().toString(), afterStore.bindingId());

            BondedVesselOperationResult recalled = vessels.transition(first, BondedVesselTransition.SUMMON);
            assertEquals(BondedVesselOperationResult.Status.COMMITTED, recalled.status());
            BondedVesselBindingRecord afterRecall = vessels.binding(first);
            assertEquals(first.profileId(), afterRecall.profileId());
            assertEquals(first.bindingId().toString(), afterRecall.bindingId());
            assertNotEquals(firstProjection, afterRecall.activeNpcUuid(),
                    "Recall may replace the live UUID but must preserve canonical identity.");

            long currentGeneration = afterRecall.generation();
            assertStaleVesselFailsClosed(vessels, first, currentGeneration);
        }

        assertSoulBondIsIdempotentAcrossPartialDormantRecovery();
    }

    private void assertPackagedTameworkDependency() throws Exception {
        Path hydragonJar = Path.of(System.getProperty("hydragon.packaged.jar"))
                .toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(hydragonJar),
                () -> "missing packaged HyDragon JAR: " + hydragonJar);
        Path configured = Path.of(System.getProperty("hydragon.tamework.jar"))
                .toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(configured), () -> "missing Tamework JAR: " + configured);
        assertPackagedArtifact(hydragonJar, System.getProperty("hydragon.plugin.version"), List.of(
                "manifest.json",
                "com/alechilles/hydragon/HyDragonPlugin.class",
                "com/alechilles/hydragon/runtime/SoulBondService.class",
                "Server/Languages/en-US/server.lang"));
        assertPackagedArtifact(configured, System.getProperty("hydragon.tamework.version"), List.of(
                "com/alechilles/alecstamework/api/TameworkApi.class",
                "com/alechilles/alecstamework/selftest/HyDragonBehavioralSelfTestFixtures.class",
                "com/alechilles/alecstamework/vessels/runtime/ProductionBondedVesselMutationAuthority.class",
                "com/alechilles/alecstamework/provisioning/CompanionProvisioningCoordinator.class"));
        URI source = TameworkApi.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        assertEquals(configured, Path.of(source).toAbsolutePath().normalize(),
                "The integration gate must execute the configured packaged Tamework 3.0.0 artifact.");
    }

    private static void assertPackagedArtifact(
            Path artifact, String expectedVersion, List<String> requiredEntries) throws Exception {
        try (JarFile jar = new JarFile(artifact.toFile())) {
            assertNotNull(jar.getManifest(), () -> "missing JAR manifest: " + artifact);
            assertEquals(expectedVersion,
                    jar.getManifest().getMainAttributes().getValue("Plugin-Version"),
                    () -> "unexpected packaged plugin version: " + artifact);
            for (String entry : requiredEntries) {
                assertNotNull(jar.getJarEntry(entry),
                        () -> "missing packaged entry " + entry + " in " + artifact);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void assertPackagedBehavioralReleaseGate() throws Exception {
        Class<?> fixture = Class.forName(
                "com.alechilles.alecstamework.selftest.HyDragonBehavioralSelfTestFixtures");
        Method run = fixture.getDeclaredMethod("run");
        run.setAccessible(true);
        List<ApiSelfTestAssertion> assertions = (List<ApiSelfTestAssertion>) run.invoke(null);
        List<String> requiredAssertions = List.of(
                "isolated guaranteed capture commits without entropy",
                "isolated failed capture is immutable and duplicate-safe",
                "isolated capture restart cancels an expired prepared checkpoint",
                "isolated capture restart quarantines ambiguous apply and reuses its outcome",
                "isolated bonded vessel rejects stale generation",
                "isolated bonded vessel bind recovers one generation-one authority",
                "isolated bonded vessel summons and stores through one journal",
                "isolated bonded vessel death is duplicate-safe with offline item",
                "isolated bonded vessel restart closes applied transition without reapplying",
                "isolated bonded vessel lost transition survives sealed item absence",
                "isolated population group rejects boundary overflow",
                "isolated population reservations serialize and cancel exactly once",
                "isolated population role change is evaluated all-or-none",
                "isolated unavailable population config fails closed without reservation",
                "isolated provisioning commits dormant profile",
                "isolated provisioning projects active profile",
                "isolated failed projection stays durable and recoverable",
                "isolated restart reacquires lost active projection token");
        List<String> packagedAssertions = assertions.stream().map(ApiSelfTestAssertion::name).toList();
        assertTrue(packagedAssertions.containsAll(requiredAssertions),
                () -> "packaged Tamework JAR is missing HyDragon release fixtures: required="
                        + requiredAssertions + " actual=" + packagedAssertions);
        assertTrue(assertions.stream().allMatch(ApiSelfTestAssertion::passed), () -> assertions.stream()
                .filter(assertion -> !assertion.passed())
                .map(assertion -> assertion.name() + ": " + assertion.detail())
                .reduce((left, right) -> left + "; " + right)
                .orElse("Tamework HyDragon release fixture failed"));
    }

    private void assertTierQualificationUsesTheProductionCapturePolicy() {
        AtomicInteger entropy = new AtomicInteger();
        SpawnerCaptureChanceService service = new SpawnerCaptureChanceService(allowRequirements());
        CapturePolicyConfigView dragonPolicy = new CapturePolicyConfigView(
                "HyDragon_Full_Dragon", 3L, 0, Set.of(DRAGON_ROLE),
                3, 0.0D, 1.0D, 0.0D, 3, List.of());
        CaptureRequirementContext context = new CaptureRequirementContext(
                UUID.randomUUID(), CaptureRequirementPhase.FINAL_REVALIDATION,
                OWNER, UUID.randomUUID(), null, DRAGON_ROLE, WORLD,
                "Draconic_Stone_Iron", 0.25D,
                CaptureRequirementContext.UNKNOWN_PROFILE_REVISION);
        ItemFeatureConfig.CaptureItemMechanics underTier = new ItemFeatureConfig.CaptureItemMechanics(
                CaptureChanceMode.PROBABILITY, 2, 0.5D, 0.1D, 0.0D, 1.0D,
                0, null, null);
        ItemFeatureConfig.CaptureItemMechanics qualified = new ItemFeatureConfig.CaptureItemMechanics(
                CaptureChanceMode.PROBABILITY, 3, 0.5D, 0.1D, 0.0D, 1.0D,
                0, null, null);

        assertEquals("capture-power-below-minimum", service.evaluate(
                underTier, dragonPolicy, 25.0D, 100.0D, context, 0L,
                () -> { entropy.incrementAndGet(); return 0.0D; }).reason());
        SpawnerCaptureChanceService.Evaluation accepted = service.evaluate(
                qualified, dragonPolicy, 25.0D, 100.0D, context, 0L,
                () -> { entropy.incrementAndGet(); return 0.0D; });
        assertEquals(SpawnerCaptureChanceService.Outcome.SUCCESS, accepted.outcome());
        assertTrue(accepted.guaranteed());
        assertEquals(0, entropy.get(), "Guaranteed-at-tier capture must not sample entropy.");
    }

    private void assertStaleVesselFailsClosed(
            VesselHarness vessels, VesselIdentity identity, long currentGeneration) throws Exception {
        BondedVesselProjectionValidationView stale = vessels.coordinator.validateProjection(
                new BondedVesselProjectionValidationRequest(
                        identity.bindingId(), 1L,
                        BondedVesselProjectionValidationRequest.ProjectionKind.ITEM,
                        "copied-generation-one"));
        assertEquals(BondedVesselProjectionValidationStatus.STALE_GENERATION, stale.status());
        assertEquals(currentGeneration, stale.canonicalGeneration());
        assertTrue(stale.authoritative());

        for (BondedVesselTransition transition : BondedVesselTransition.values()) {
            BondedVesselOperationResult result = vessels.prepareStale(identity, transition, 1L);
            assertEquals(BondedVesselOperationResult.Status.DENIED, result.status(),
                    () -> "A copied generation-one vessel must not perform " + transition);
            assertEquals(currentGeneration, vessels.binding(identity).generation());
        }
    }

    private void assertSoulBondIsIdempotentAcrossPartialDormantRecovery() throws Exception {
        UUID owner = UUID.fromString("22000000-0000-0000-0000-000000000002");
        RecoverableProvisioningApi provisioning = new RecoverableProvisioningApi(owner);
        HyDragonStateStore store = new HyDragonStateStore(tempDir.resolve("soul-bond.properties"));
        StateStoreOperationJournal journal = new StateStoreOperationJournal(store, () -> 10_000L);
        SoulBondService service = new SoulBondService(
                new TameworkGameplayAdapter(soulBondApi(provisioning)),
                new StateStoreSoulBondLedger(store), journal, () -> 10_000L);
        String operationId = "hydragon:soul-bond:" + owner;

        CountingReservation first = new CountingReservation(operationId);
        GameplayResult partial = service.claim(owner, WORLD,
                new PopulationAdmissionLocation(WORLD, 4, -2), first).toCompletableFuture().join();
        assertEquals(GameplayResult.Status.RECONCILIATION_REQUIRED, partial.status());
        assertEquals(1, provisioning.provisionCalls.get());
        assertEquals(1, provisioning.activationCalls.get());
        assertEquals(1, first.consumeCalls.get());
        assertEquals(OperationJournal.Phase.MATERIAL_CONSUMED,
                journal.find(operationId).orElseThrow().phase());
        assertEquals(1, store.snapshot().playerSoulBonds().size());

        CountingReservation recovery = new CountingReservation(operationId);
        GameplayResult recovered = service.claim(owner, WORLD,
                new PopulationAdmissionLocation(WORLD, 4, -2), recovery).toCompletableFuture().join();
        assertEquals(GameplayResult.Status.APPLIED, recovered.status());
        assertEquals(1, provisioning.provisionCalls.get(),
                "Recovery must reuse the one dormant canonical profile.");
        assertEquals(2, provisioning.activationCalls.get());
        assertEquals(0, recovery.consumeCalls.get());
        assertEquals(OperationJournal.Phase.COMMITTED,
                journal.find(operationId).orElseThrow().phase());
        assertEquals(1, store.snapshot().playerSoulBonds().size());

        CountingReservation duplicate = new CountingReservation(operationId);
        GameplayResult denied = service.claim(owner, WORLD, duplicate).toCompletableFuture().join();
        assertEquals(GameplayResult.Status.DENIED, denied.status());
        assertEquals(1, provisioning.provisionCalls.get());
        assertEquals(0, duplicate.consumeCalls.get());
    }

    private static CaptureRequirementRuntime allowRequirements() {
        return new CaptureRequirementRuntime() {
            @Override public long captureRequirementGeneration() { return 0L; }
            @Override public CaptureRequirementDecision evaluateCaptureRequirement(
                    com.alechilles.alecstamework.api.CaptureRequirementSpec spec,
                    CaptureRequirementContext context, long expectedGeneration) {
                return CaptureRequirementDecision.allow();
            }
        };
    }

    private static TameworkApi soulBondApi(CompanionProvisioningApi provisioning) {
        return new TameworkApi() {
            @Override public String getApiVersion() { return "0.9.0"; }
            @Override public EnumSet<TameworkApiCapability> getCapabilities() {
                return EnumSet.of(
                        TameworkApiCapability.PROFILES,
                        TameworkApiCapability.POLICY,
                        TameworkApiCapability.PERSISTENCE_RESILIENCE,
                        TameworkApiCapability.POPULATION_GROUPS,
                        TameworkApiCapability.COMPANION_PROVISIONING,
                        TameworkApiCapability.INTERACTION_EXTENSIONS);
            }
            @Override public NpcProfilesApi profiles() { return null; }
            @Override public CommandLinksApi commandLinks() { return null; }
            @Override public ProgressionApi progression() { return null; }
            @Override public PolicyApi policies() { return null; }
            @Override public InteractionExtensionApi interactionExtensions() { return null; }
            @Override public TraitEffectApi traitEffects() { return null; }
            @Override public ProfileDataApi profileData() { return null; }
            @Override public TameworkEventsApi events() { return null; }
            @Override public TameworkConfigReadApi configs() { return null; }
            @Override public DiagnosticsApi diagnostics() { return null; }
            @Override public CompanionProvisioningApi companionProvisioning() { return provisioning; }
        };
    }

    private static final class VesselHarness implements AutoCloseable {
        private final TameworkPersistenceRuntime persistence;
        private final Map<String, ProductionBondedVesselMutationAuthority.CanonicalProfileSnapshot> profiles =
                new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong(20_000L);
        private final GroupPopulationPort population = new GroupPopulationPort(profiles);
        private final ExactEvidence evidence = new ExactEvidence();
        private final BondedVesselCoordinator coordinator;

        private VesselHarness(Path directory) {
            persistence = TameworkPersistenceRuntime.initialize(directory, null);
            ProductionBondedVesselMutationAuthority mutation = new ProductionBondedVesselMutationAuthority(
                    new ProfilePort(profiles), population, new DeterministicWorldPort(), Runnable::run);
            assertTrue(mutation.isCapabilityReady());
            coordinator = new BondedVesselCoordinator(
                    new SqliteBondedVesselJournal(persistence.getBondedVesselRepository()),
                    (binding, request, nowMs) -> plan(binding, request.transition()),
                    evidence,
                    mutation,
                    null,
                    Runnable::run,
                    sequence::getAndIncrement,
                    sequence::getAndIncrement,
                    30_000L,
                    64);
        }

        private VesselIdentity bindCapturedDragon(String suffix) throws Exception {
            UUID bindingId = UUID.nameUUIDFromBytes(
                    ("binding:" + suffix).getBytes(StandardCharsets.UTF_8));
            UUID capturedNpcId = UUID.nameUUIDFromBytes(
                    ("captured-npc:" + suffix).getBytes(StandardCharsets.UTF_8));
            NpcProfileRepository profilesRepository = persistence.getNpcProfileRepository();
            assertTrue(persistence.getCaptureRepository().upsertAsync(
                    new CommandLinkedNpcCaptureService.CapturedLinkedNpcSnapshot(
                            capturedNpcId, OWNER, new String[0], DRAGON_ROLE, suffix,
                            null, null, sequence.getAndIncrement())));
            assertTrue(persistence.awaitWriteQueueIdle(5_000L),
                    "canonical captured profile write did not drain");
            String profileId = profilesRepository.resolveProfileId(capturedNpcId);
            assertNotNull(profileId, "capture persistence did not create a canonical profile");
            profiles.put(profileId, new ProductionBondedVesselMutationAuthority.CanonicalProfileSnapshot(
                    profileId, OWNER, DRAGON_ROLE, 0L, CompanionLifecycleState.CAPTURED, null));
            UUID operationId = UUID.nameUUIDFromBytes(
                    ("initial-bind:" + suffix).getBytes(StandardCharsets.UTF_8));
            BondedVesselInitialBindingService service = new BondedVesselInitialBindingService(
                    persistence.getBondedVesselRepository(),
                    request -> CompletableFuture.completedFuture(
                            new BondedVesselInitialBindingService.SourceFinalization(
                                    BondedVesselInitialBindingService.SourceStatus.REPLACED,
                                    "captured-source-replaced")),
                    null, Runnable::run, sequence::getAndIncrement);
            BondedVesselInitialBindingService.Result result = service.bind(
                    new BondedVesselInitialBindingService.Request(
                            operationId, bindingId, "Alechilles:HyDragon", "capture:" + suffix,
                            "capture:" + suffix, profileId, OWNER, 0L,
                            "HyDragon_Draconic_Stone", 3L,
                            "Draconic_Stone_Iron", "Draconic_Stone_Filled",
                            "empty:" + suffix, "stored:" + suffix,
                            "{\"holder\":\"owner\"}",
                            "{\"holder\":\"owner\",\"generation\":1}",
                            "{\"tier\":3}", "population:" + suffix))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(BondedVesselInitialBindingService.Status.COMMITTED, result.status(),
                    () -> "initial vessel binding failed: " + result.reason()
                            + "; queue=" + persistence.getWriteQueueMetrics().lastFailureReason());
            return new VesselIdentity(bindingId, profileId);
        }

        private BondedVesselBindingRecord binding(VesselIdentity identity) throws Exception {
            BondedVesselBindingRecord result = persistence.getBondedVesselRepository()
                    .findBinding(identity.bindingId().toString());
            assertNotNull(result);
            return result;
        }

        private BondedVesselOperationResult transition(
                VesselIdentity identity, BondedVesselTransition transition) throws Exception {
            BondedVesselBindingRecord binding = binding(identity);
            BondedVesselTransitionRequest request = request(
                    identity, transition, binding.generation(), binding.expectedProfileRevision(), binding);
            BondedVesselOperationResult prepared = coordinator.prepareTransition(request)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            if (prepared.status() != BondedVesselOperationResult.Status.RESERVED) return prepared;
            assertEquals(BondedVesselOperationResult.Status.APPLYING,
                    coordinator.claimForApply(prepared.token()).status());
            return coordinator.commit(prepared.token()).toCompletableFuture().get(5, TimeUnit.SECONDS);
        }

        private BondedVesselOperationResult prepareStale(
                VesselIdentity identity, BondedVesselTransition transition, long staleGeneration) throws Exception {
            BondedVesselBindingRecord binding = binding(identity);
            return coordinator.prepareTransition(request(
                            identity, transition, staleGeneration,
                            binding.expectedProfileRevision(), binding))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
        }

        private BondedVesselTransitionRequest request(
                VesselIdentity identity,
                BondedVesselTransition transition,
                long generation,
                long profileRevision,
                BondedVesselBindingRecord binding) {
            UUID expectedNpc = transition == BondedVesselTransition.STORE
                    ? binding.activeNpcUuid() : null;
            PopulationAdmissionLocation destination = transition == BondedVesselTransition.SUMMON
                    ? new PopulationAdmissionLocation(WORLD, 2, -3) : null;
            BondedVesselTransitionContext context = new BondedVesselTransitionContext(
                    binding.lastItemId() == null ? "Draconic_Stone_Filled" : binding.lastItemId(),
                    "player:" + OWNER, "inventory/hotbar", 2,
                    sequence.getAndIncrement(), "fingerprint:" + identity.bindingId() + ':' + generation,
                    expectedNpc, destination);
            return new BondedVesselTransitionRequest(
                    "Alechilles:HyDragon",
                    transition.name().toLowerCase() + ':' + identity.bindingId() + ':' + sequence.getAndIncrement(),
                    OWNER, identity.bindingId(), generation, profileRevision, transition, context);
        }

        private static com.alechilles.alecstamework.vessels.BondedVesselTransitionPlanner.Plan plan(
                BondedVesselBindingRecord binding, BondedVesselTransition transition) {
            BondedVesselState target = switch (transition) {
                case SUMMON -> BondedVesselState.ACTIVE;
                case STORE, REPAIR_DEAD_TO_STORED -> BondedVesselState.STORED;
                case RELEASE -> BondedVesselState.RELEASED;
            };
            return new com.alechilles.alecstamework.vessels.BondedVesselTransitionPlanner.Plan(
                    target,
                    transition == BondedVesselTransition.RELEASE
                            ? BondedVesselProjectionStatus.MISSING
                            : BondedVesselProjectionStatus.PRESENT,
                    target == BondedVesselState.ACTIVE ? "Draconic_Stone_Active"
                            : target == BondedVesselState.RELEASED ? "Draconic_Stone_Iron"
                            : "Draconic_Stone_Filled",
                    "candidate:" + binding.bindingId() + ':' + (binding.generation() + 1L),
                    0L,
                    "{\"group\":\"hydragon:full-dragons\"}");
        }

        @Override public void close() { persistence.close(); }
    }

    private record VesselIdentity(UUID bindingId, String profileId) { }

    private static final class ProfilePort
            implements ProductionBondedVesselMutationAuthority.CanonicalProfilePort {
        private final Map<String, ProductionBondedVesselMutationAuthority.CanonicalProfileSnapshot> profiles;
        private ProfilePort(Map<String, ProductionBondedVesselMutationAuthority.CanonicalProfileSnapshot> profiles) {
            this.profiles = profiles;
        }
        @Override public CompletionStage<ProductionBondedVesselMutationAuthority.CanonicalProfileSnapshot> load(
                String profileId) {
            return CompletableFuture.completedFuture(profiles.get(profileId));
        }
        @Override public ProductionBondedVesselMutationAuthority.ProfileReadiness readiness() {
            return new ProductionBondedVesselMutationAuthority.ProfileReadiness(true, "ready");
        }
    }

    private static final class GroupPopulationPort
            implements ProductionBondedVesselMutationAuthority.UnifiedPopulationPort {
        private final Map<String, ProductionBondedVesselMutationAuthority.CanonicalProfileSnapshot> profiles;
        private final Map<String, ProductionBondedVesselMutationAuthority.PopulationMutationRequest> pending =
                new LinkedHashMap<>();
        private final PopulationGroupAdmissionPolicy policy;
        private final PopulationGroupBucket bucket;
        private String activeProfileId;

        private GroupPopulationPort(
                Map<String, ProductionBondedVesselMutationAuthority.CanonicalProfileSnapshot> profiles) {
            this.profiles = profiles;
            PopulationGroupDefinitionView definition = new PopulationGroupDefinitionView(
                    "HyDragon_Full_Dragons", 3L, "hydragon:full-dragons",
                    Set.of(DRAGON_ROLE), 0L, 1L, PopulationGroupScope.GLOBAL);
            this.policy = new PopulationGroupAdmissionPolicy(index(definition));
            this.bucket = PopulationGroupBucket.of(OWNER, definition, null);
        }

        @Override public CompletionStage<ProductionBondedVesselMutationAuthority.PopulationPreparation> prepare(
                ProductionBondedVesselMutationAuthority.PopulationMutationRequest request) {
            int activeDelta = request.targetLifecycle() == CompanionLifecycleState.ACTIVE ? 1
                    : request.profile().lifecycle() == CompanionLifecycleState.ACTIVE ? -1 : 0;
            PopulationGroupAdmissionPolicy.Decision decision = policy.evaluate(
                    Map.of(bucket, new PopulationGroupCounts(2L, 0L,
                            activeProfileId == null ? 0L : 1L, 0L)),
                    Map.of(bucket, new PopulationGroupCountDelta(0, activeDelta)),
                    PopulationAdmissionForcePolicy.ENFORCE);
            if (!decision.allowed()) {
                return CompletableFuture.completedFuture(
                        new ProductionBondedVesselMutationAuthority.PopulationPreparation(
                                ProductionBondedVesselMutationAuthority.PopulationPreparationStatus.TERMINAL_DENIED,
                                decision.violations().getFirst().reason(), null));
            }
            var handle = new ProductionBondedVesselMutationAuthority.PopulationHandle(
                    request.operation().operationId(), request.binding().bindingId(),
                    request.profile().profileId(), request.operation().priorGeneration(),
                    request.operation().candidateGeneration(),
                    "full-dragon-slot:" + request.operation().operationId());
            pending.put(handle.operationId(), request);
            return CompletableFuture.completedFuture(
                    new ProductionBondedVesselMutationAuthority.PopulationPreparation(
                            ProductionBondedVesselMutationAuthority.PopulationPreparationStatus.PREPARED,
                            "group-slot-prepared", handle));
        }

        @Override public CompletionStage<ProductionBondedVesselMutationAuthority.PopulationClaim> claim(
                ProductionBondedVesselMutationAuthority.PopulationHandle handle) {
            boolean found = pending.containsKey(handle.operationId());
            return CompletableFuture.completedFuture(
                    new ProductionBondedVesselMutationAuthority.PopulationClaim(
                            found ? ProductionBondedVesselMutationAuthority.PopulationClaimStatus.CLAIMED
                                    : ProductionBondedVesselMutationAuthority.PopulationClaimStatus.INDETERMINATE,
                            found ? "group-slot-claimed" : "group-slot-missing",
                            found ? handle : null));
        }

        @Override public CompletionStage<ProductionBondedVesselMutationAuthority.PopulationCommit> commit(
                ProductionBondedVesselMutationAuthority.PopulationHandle handle,
                ProductionBondedVesselMutationAuthority.PopulationMutationRequest request,
                ProductionBondedVesselMutationAuthority.WorldMutationReceipt receipt) {
            ProductionBondedVesselMutationAuthority.CanonicalProfileSnapshot before =
                    profiles.get(request.profile().profileId());
            long nextRevision = before.revision() + 1L;
            UUID npc = request.targetLifecycle() == CompanionLifecycleState.ACTIVE
                    ? receipt.activeNpcUuid() : null;
            profiles.put(before.profileId(),
                    new ProductionBondedVesselMutationAuthority.CanonicalProfileSnapshot(
                            before.profileId(), before.ownerUuid(), before.roleId(), nextRevision,
                            request.targetLifecycle(), npc));
            if (request.targetLifecycle() == CompanionLifecycleState.ACTIVE) {
                activeProfileId = before.profileId();
            } else if (before.profileId().equals(activeProfileId)) {
                activeProfileId = null;
            }
            pending.remove(handle.operationId());
            return CompletableFuture.completedFuture(
                    new ProductionBondedVesselMutationAuthority.PopulationCommit(
                            ProductionBondedVesselMutationAuthority.PopulationCommitStatus.APPLIED,
                            "canonical-group-commit", nextRevision, npc,
                            receipt.activeLocation(), receipt.itemEvidenceJson()));
        }

        @Override public CompletionStage<Boolean> cancel(
                ProductionBondedVesselMutationAuthority.PopulationHandle handle, String reason) {
            pending.remove(handle.operationId());
            return CompletableFuture.completedFuture(true);
        }

        @Override public ProductionBondedVesselMutationAuthority.PopulationReadiness readiness() {
            return new ProductionBondedVesselMutationAuthority.PopulationReadiness(
                    true, true, true, true, true, "ready");
        }

        private static PopulationGroupIndex index(PopulationGroupDefinitionView definition) {
            try {
                Constructor<PopulationGroupIndex> constructor = PopulationGroupIndex.class
                        .getDeclaredConstructor(long.class, Map.class, Map.class);
                constructor.setAccessible(true);
                return constructor.newInstance(
                        3L, Map.of(definition.groupId(), definition),
                        Map.of(DRAGON_ROLE, List.of(definition)));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Unable to construct isolated population policy", failure);
            }
        }
    }

    private static final class DeterministicWorldPort
            implements ProductionBondedVesselMutationAuthority.WorldProjectionPort {
        private final AtomicLong projections = new AtomicLong();
        @Override public CompletionStage<ProductionBondedVesselMutationAuthority.WorldMutationReceipt> apply(
                ProductionBondedVesselMutationAuthority.WorldMutationRequest request) {
            boolean active = request.populationRequest().targetLifecycle() == CompanionLifecycleState.ACTIVE;
            UUID npc = active ? UUID.nameUUIDFromBytes((request.populationRequest().profile().profileId()
                    + ":projection:" + projections.incrementAndGet())
                    .getBytes(StandardCharsets.UTF_8)) : null;
            return CompletableFuture.completedFuture(
                    new ProductionBondedVesselMutationAuthority.WorldMutationReceipt(
                            ProductionBondedVesselMutationAuthority.WorldMutationStatus.APPLIED,
                            active ? "dragon-projected" : "dragon-stored", npc,
                            active ? new BondedVesselBindingRecord.PhysicalLocation(WORLD, 2, -3) : null,
                            "{\"worldMutation\":true}"));
        }
        @Override public ProductionBondedVesselMutationAuthority.WorldReadiness readiness() {
            return new ProductionBondedVesselMutationAuthority.WorldReadiness(true, true, true, "ready");
        }
    }

    private static final class ExactEvidence implements BondedVesselEvidenceAuthority {
        @Override public CompletionStage<SourceObservation> observe(BondedVesselTransitionContext expected) {
            return CompletableFuture.completedFuture(new SourceObservation(
                    Status.EXACT, "exact", expected.sourceHolderEvidenceId(),
                    expected.sourceContainerPath(), expected.sourceInventorySlot(),
                    expected.sourceInventoryRevision(), expected.sourceItemId(),
                    expected.sourceItemFingerprint()));
        }
        @Override public CompletionStage<SourceFinalization> finalizeSource(
                BondedVesselOperationRecord operation, BondedVesselTransitionContext expected) {
            return CompletableFuture.completedFuture(new SourceFinalization(
                    FinalizationStatus.FINALIZED, "item-projection-finalized",
                    operation.replacementFingerprint(), "{\"finalized\":true}"));
        }
        @Override public BondedVesselProjectionValidationView validateProjection(
                BondedVesselBindingRecord binding,
                BondedVesselProjectionValidationRequest request) {
            return new BondedVesselProjectionValidationView(
                    request.bindingId(), BondedVesselProjectionValidationStatus.CONSISTENT,
                    "evidence-consistent", binding.generation(), true);
        }
    }

    private static final class RecoverableProvisioningApi implements CompanionProvisioningApi {
        private final UUID owner;
        private final UUID profile = UUID.fromString("33000000-0000-0000-0000-000000000003");
        private final UUID operation = UUID.fromString("44000000-0000-0000-0000-000000000004");
        private final AtomicInteger provisionCalls = new AtomicInteger();
        private final AtomicInteger activationCalls = new AtomicInteger();
        private RecoverableProvisioningApi(UUID owner) { this.owner = owner; }
        @Override public Optional<ProvisionedCompanionView> getByProfileId(String profileId) {
            return Optional.empty();
        }
        @Override public Optional<ProvisionedCompanionView> getByOrigin(String namespace, String key) {
            return Optional.empty();
        }
        @Override public CompletionStage<CompanionProvisioningResult> provision(
                CompanionProvisioningRequest request) {
            provisionCalls.incrementAndGet();
            return CompletableFuture.completedFuture(result(
                    CompanionProvisioningResult.Status.PROVISIONED_DORMANT,
                    request.idempotencyKey(), PopulationCompanionLifecycle.PROVISIONED_DORMANT,
                    CompanionProvisioningProjectionStatus.NOT_REQUESTED, 0L, "dormant"));
        }
        @Override public CompletionStage<CompanionProvisioningResult> transition(
                ProvisionedCompanionTransitionRequest request) {
            int call = activationCalls.incrementAndGet();
            return CompletableFuture.completedFuture(call == 1
                    ? result(CompanionProvisioningResult.Status.PARTIAL_DORMANT,
                            request.idempotencyKey(), PopulationCompanionLifecycle.PROVISIONED_DORMANT,
                            CompanionProvisioningProjectionStatus.FAILED_RECOVERABLE, 0L,
                            "projection-failed-recoverable")
                    : result(CompanionProvisioningResult.Status.TRANSITIONED,
                            request.idempotencyKey(), PopulationCompanionLifecycle.ACTIVE,
                            CompanionProvisioningProjectionStatus.ACTIVE, 1L, "active"));
        }
        @Override public CompletionStage<Optional<CompanionProvisioningOperationView>> findOperation(
                String namespace, String key) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        private CompanionProvisioningResult result(
                CompanionProvisioningResult.Status status,
                String key,
                PopulationCompanionLifecycle lifecycle,
                CompanionProvisioningProjectionStatus projection,
                long revision,
                String reason) {
            return new CompanionProvisioningResult(
                    status, reason, TameworkGameplayAdapter.CALLER_NAMESPACE, key,
                    operation, profile.toString(), owner,
                    TameworkGameplayAdapter.SOULBOUND_MINIWYVERN_ROLE,
                    lifecycle, projection, reason, null, revision);
        }
    }

    private static final class CountingReservation implements ConsumableReservation {
        private final String operationId;
        private final AtomicInteger consumeCalls = new AtomicInteger();
        private final AtomicInteger releaseCalls = new AtomicInteger();
        private CountingReservation(String operationId) { this.operationId = operationId; }
        @Override public String operationId() { return operationId; }
        @Override public SourceEvidence sourceEvidence() {
            return new SourceEvidence(
                    "Draconic_Soul_Bond", "player", "hotbar", 0,
                    1L, "soul-bond-fingerprint", 1);
        }
        @Override public int quantity() { return 1; }
        @Override public CompletionStage<Disposition> consume() {
            consumeCalls.incrementAndGet();
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
        @Override public CompletionStage<Disposition> release() {
            releaseCalls.incrementAndGet();
            return CompletableFuture.completedFuture(Disposition.APPLIED);
        }
    }
}
