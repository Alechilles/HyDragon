package com.alechilles.hydragon.compat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HytaleNpcSupportAccessTest {
    @Test
    void bindsAgainstTheActiveServerApiWithoutLinkageFailure() {
        assertDoesNotThrow(() -> Class.forName(HytaleNpcSupportAccess.class.getName(), true,
                HytaleNpcSupportAccess.class.getClassLoader()));
    }

    @Test
    void missingNpcContextIsReportedAsMissingSupport() {
        assertNull(HytaleNpcSupportAccess.stateSupport(null, null, null));
        assertNull(HytaleNpcSupportAccess.markedEntitySupport(null, null, null));
        assertNull(HytaleNpcSupportAccess.worldSupport(null, null, null));
    }
}
