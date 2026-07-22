package com.alechilles.hydragon.diagnostics;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HyDragonStatusCommandScopeTest {
    @Test
    void statusIsConsoleCapableAndNotPlayerScoped() {
        assertTrue(AbstractAsyncCommand.class.isAssignableFrom(HyDragonStatusCommand.class));
        assertFalse(AbstractPlayerCommand.class.isAssignableFrom(HyDragonStatusCommand.class));
    }
}
