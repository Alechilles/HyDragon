package com.alechilles.hydragon.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the startup ordering that cannot be exercised without a live Hytale plugin manager. */
final class PluginLifecycleContractTest {
    private final Path projectRoot = Path.of(System.getProperty("hydragon.project.basedir"));

    @Test
    void encounterComponentsAreRegisteredDuringSetupBeforeAssetRegistration() throws IOException {
        String source = pluginSource();

        assertOrdered(source,
                "serverRuntime = HyDragonEncounterRegistrationFacade.registerServerRuntime(this);",
                "registerConfigAssets();");
        assertTrue(source.contains("new MiniwyvernVoidEffectLifetimeSystem()"));
        assertTrue(source.contains("new MiniwyvernOwnerAuraDamageSystem(miniwyvernOwnerAuras, voidLifetime)"));
        assertTrue(source.contains("new MiniwyvernToxicWeaknessDamageSystem(miniwyvernOwnerAuras)"));
    }

    @Test
    void writableStartupComposesEveryProductionRuntime() throws IOException {
        String source = pluginSource();

        assertTrue(source.contains("new HyDragonRuntimeComposition("));
        assertTrue(source.contains("HyDragonRuntimeComposition.Slot.BONDED_GAMEPLAY"));
        assertTrue(source.contains("HyDragonRuntimeComposition.Slot.MINIWYVERN_ABILITIES"));
        assertTrue(source.contains("HyDragonRuntimeComposition.Slot.DYNAMIC_ENCOUNTERS"));
        assertTrue(source.contains("new StateStoreSoulBondLedger(store)"));
        assertTrue(source.contains("new StateStoreOperationJournal("));
        assertTrue(source.contains("store, System::currentTimeMillis"));
        assertTrue(source.contains("new BondedMiniwyvernExtensionStore("));
        assertTrue(source.contains("new BondedMiniwyvernExtensionCodec()"));
        assertTrue(source.contains("new SoulBondService("));
        assertTrue(source.contains("new HyDragonGameplayRuntime("));
        assertTrue(source.contains("soulBondService, new SoulBondAbandonmentHandler(soulBonds)"));
        assertTrue(source.contains("gameplay.start(adapter);"));
        assertTrue(source.contains("new ConsumableSagaRecoveryRuntime("));
        assertTrue(source.contains("new ConsumableRefundClaimService(journal)"));
        assertTrue(source.contains("HyDragonEncounterRegistrationFacade.install("));
        assertTrue(source.contains("HyDragonAbilityRegistrationFacade.install("));
        assertTrue(source.contains("miniwyvernOwnerAuras"));
        assertTrue(source.contains("serverRuntime.start("));
        assertTrue(source.contains("encounterRuntime, abilityRuntime,"));
        assertTrue(source.contains("sagaRecoveryRuntime, configRepository::snapshot"));
    }

    @Test
    void shutdownStopsWorkersAndSubscriptionsBeforeDroppingPersistence() throws IOException {
        String source = pluginSource();

        assertOrdered(source, "protected void shutdown()", "stopRuntimes();", "stateStore = null;");
        assertOrdered(source,
                "private void stopRuntimes()",
                "closeRuntime(\"live server\", serverRuntime);",
                "closeRuntime(\"feature composition\", runtimeComposition);",
                "runtimeComposition = null;");
        assertTrue(source.contains("HyDragonInteractionRuntime.uninstall(gameplay);"));
        assertTrue(source.contains("gameplay.close();"));
        assertTrue(source.contains("miniwyvernOwnerAuras.clear();"));
    }

    @Test
    void startupEmitsPerCapabilityDiagnostics() throws IOException {
        String source = pluginSource();

        assertTrue(source.contains("emitCapabilityDiagnostics();"));
        assertTrue(source.contains("TameworkCapabilityDiagnostics.evaluate(bridge.snapshot())"));
        assertTrue(source.contains("entry.present() ? Level.INFO : Level.WARNING"));
    }

    @Test
    void commandDescriptionsUseServerTranslationKeys() throws IOException {
        String statusSource = Files.readString(projectRoot.resolve(
                "src/main/java/com/alechilles/hydragon/diagnostics/HyDragonStatusCommand.java"));
        String refundSource = Files.readString(projectRoot.resolve(
                "src/main/java/com/alechilles/hydragon/diagnostics/HyDragonRefundClaimCommand.java"));

        assertTrue(statusSource.contains(
                "super(\"hydragon\", \"server.messages.status.description\")"));
        assertTrue(refundSource.contains(
                "super(\"hydragonclaim\", \"server.messages.refund.description\")"));
    }

    private String pluginSource() throws IOException {
        return Files.readString(projectRoot.resolve(
                "src/main/java/com/alechilles/hydragon/HyDragonPlugin.java"));
    }

    private static void assertOrdered(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int next = source.indexOf(token, previous + 1);
            assertTrue(next > previous, () -> "expected lifecycle token in order: " + token);
            previous = next;
        }
    }
}
