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
    }

    @Test
    void writableStartupComposesEveryProductionRuntime() throws IOException {
        String source = pluginSource();

        assertTrue(source.contains("new StateStoreSoulBondLedger(store)"));
        assertTrue(source.contains("new StateStoreOperationJournal(store, System::currentTimeMillis)"));
        assertTrue(source.contains("new StateStoreMiniwyvernProfileProjection(store)"));
        assertTrue(source.contains("new TameworkBondedRepairRequestResolver(api)"));
        assertTrue(source.contains("HyDragonInteractionRuntime.install(gameplayRuntime, () -> bridge.snapshot());"));
        assertTrue(source.contains("new ConsumableSagaRecoveryRuntime("));
        assertTrue(source.contains("new ConsumableRefundClaimService(journal)"));
        assertTrue(source.contains("HyDragonEncounterRegistrationFacade.install("));
        assertTrue(source.contains("HyDragonAbilityRegistrationFacade.install("));
        assertTrue(source.contains("serverRuntime.start("));
        assertTrue(source.contains("encounterRuntime, abilityRuntime, sagaRecoveryRuntime"));
    }

    @Test
    void shutdownStopsWorkersAndSubscriptionsBeforeDroppingPersistence() throws IOException {
        String source = pluginSource();

        assertOrdered(source, "protected void shutdown()", "stopRuntimes();", "stateStore = null;");
        assertOrdered(source,
                "private void stopRuntimes()",
                "closeRuntime(\"live server\", serverRuntime);",
                "HyDragonInteractionRuntime.uninstall(gameplayRuntime);");
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
