package com.alechilles.hydragon.encounters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.RemoveReason;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HytaleEncounterWorldDispatcherTest {
    private static final UUID TARGET = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void permanentRemovalProvesOnlyTheExactEncounterTargetAbsent() {
        HytaleEncounterWorldDispatcher.RemovalLedger ledger =
                new HytaleEncounterWorldDispatcher.RemovalLedger();

        ledger.observeRemoved("world", "encounter:one", TARGET);

        assertTrue(ledger.wasRemoved("world", "encounter:one", TARGET));
        assertTrue(ledger.wasRemoved("world", "encounter:one", null));
        assertFalse(ledger.wasRemoved("other", "encounter:one", TARGET));
        assertFalse(ledger.wasRemoved("world", "encounter:one", UUID.randomUUID()));
    }

    @Test
    void aReloadedTargetClearsEarlierRemovalEvidence() {
        HytaleEncounterWorldDispatcher.RemovalLedger ledger =
                new HytaleEncounterWorldDispatcher.RemovalLedger();
        ledger.observeRemoved("world", "encounter:one", TARGET);

        ledger.observeAdded("world", "encounter:one", TARGET);

        assertFalse(ledger.wasRemoved("world", "encounter:one", TARGET));
    }

    @Test
    void unloadRemainsAmbiguousWhilePermanentRemovalProvesAbsence() {
        assertFalse(HyDragonEncounterTargetLifecycleSystem.provesAbsence(RemoveReason.UNLOAD));
        assertTrue(HyDragonEncounterTargetLifecycleSystem.provesAbsence(RemoveReason.REMOVE));
        assertTrue(HyDragonEncounterTargetLifecycleSystem.provesAbsence(
                RemoveReason.BUILDER_TOOLS_UNDO));
    }
}
