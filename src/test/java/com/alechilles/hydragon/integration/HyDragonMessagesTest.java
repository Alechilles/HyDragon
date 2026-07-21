package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HyDragonMessagesTest {
    @Test
    void gameplayMessagesUseServerLanguageKeys() {
        assertEquals("server.messages.soulBond.claimed", HyDragonMessages.soulBondClaimed().getMessageId());
        assertEquals("server.messages.attunement.success", HyDragonMessages.attunementSuccess().getMessageId());
        assertEquals("server.messages.repair.success", HyDragonMessages.repairSuccess().getMessageId());
        assertEquals("server.messages.vessel.unavailable", HyDragonMessages.vesselUnavailable().getMessageId());
    }
}
