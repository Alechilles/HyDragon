package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HyDragonMessagesTest {
    @Test
    void gameplayMessagesUseServerLanguageKeys() {
        assertEquals("server.messages.soulBond.claimed", HyDragonMessages.soulBondClaimed().getMessageId());
        assertEquals("server.messages.capture.hornRequired", HyDragonMessages.dragonHornRequired().getMessageId());
        assertEquals("server.messages.attunement.success", HyDragonMessages.attunementSuccess().getMessageId());
        assertEquals("server.messages.gameplay.unavailable", HyDragonMessages.gameplayUnavailable().getMessageId());
        assertEquals("server.messages.status.usage", HyDragonMessages.statusUsage().getMessageId());
        assertEquals("server.messages.status.unavailable", HyDragonMessages.statusUnavailable().getMessageId());
        assertEquals("server.messages.status.title", HyDragonMessages.statusTitle("1.0.0").getMessageId());
        assertEquals("server.messages.status.config", HyDragonMessages.statusConfig(
                HyDragonMessages.statusStateReady(), 1, 2, 3, 0).getMessageId());
    }
}
