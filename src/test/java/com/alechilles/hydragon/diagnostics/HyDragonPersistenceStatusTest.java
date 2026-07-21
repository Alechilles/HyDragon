package com.alechilles.hydragon.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.hydragon.persistence.HyDragonStateStore;
import com.alechilles.hydragon.persistence.ProfileExtensionRecord;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HyDragonPersistenceStatusTest {
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROFILE_ONE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsAnUnavailableStoreWithoutInventingRuntimeCounts() {
        HyDragonPersistenceStatus status = HyDragonPersistenceStatus.from(null, null);

        assertFalse(status.available());
        assertFalse(status.writable());
        assertEquals(0, status.players());
        assertEquals(0, status.pendingReconciliation());
        assertEquals("state store not initialized", status.reason());
    }

    @Test
    void summarizesEveryStartupVerificationQueue() throws Exception {
        HyDragonStateStore store = new HyDragonStateStore(temporaryDirectory.resolve("hydragon-state.properties"));
        store.beginSoulBond(PLAYER_ONE, "soul:pending");
        store.beginSoulBond(PLAYER_TWO, "soul:claimed");
        store.completeSoulBond(PLAYER_TWO, "soul:claimed", PROFILE_ONE, 1_000);
        store.putProfileExtension(ProfileExtensionRecord.soulboundMiniwyvern(
                PROFILE_ONE,
                "hydragon:miniwyvern",
                "neutral",
                Optional.of("soul:claimed")));

        HyDragonPersistenceStatus status = HyDragonPersistenceStatus.from(store, null);

        assertTrue(status.available());
        assertTrue(status.writable());
        assertEquals(2, status.players());
        assertEquals(1, status.profiles());
        assertEquals(3, status.pendingReconciliation());
        assertNull(status.reason());
    }

    @Test
    void exposesUnsupportedStoreSchemaAsReadOnly() throws Exception {
        Path file = temporaryDirectory.resolve("future-state.properties");
        Properties properties = new Properties();
        properties.setProperty("store.schema", "42");
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "future schema fixture");
        }

        HyDragonPersistenceStatus status = HyDragonPersistenceStatus.from(new HyDragonStateStore(file), null);

        assertTrue(status.available());
        assertFalse(status.writable());
        assertEquals(1, status.quarantined());
        assertEquals("unsupported store schema or quarantined store", status.reason());
    }
}
