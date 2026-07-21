package com.alechilles.hydragon.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceKeyedEffectRegistryTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Test
    void sharedEffectRemainsUntilItsLastLiveLogicalSourceIsReleased() {
        SourceKeyedEffectRegistry registry = new SourceKeyedEffectRegistry();
        SourceKeyedEffectRegistry.EffectKey key =
                new SourceKeyedEffectRegistry.EffectKey("world", TARGET, "shared-effect");

        SourceKeyedEffectRegistry.RetainResult first = registry.retain(key, "mini-a", 2_000L, 1_000L);
        SourceKeyedEffectRegistry.RetainResult second = registry.retain(key, "mini-b", 3_000L, 1_000L);

        assertNull(first.previousExpiryMs());
        assertEquals(2_000L, first.effectiveExpiryMs());
        assertNull(second.previousExpiryMs());
        assertEquals(3_000L, second.effectiveExpiryMs());

        SourceKeyedEffectRegistry.ReleaseResult releaseA = registry.release(key, "mini-a", 1_500L);
        assertFalse(releaseA.removeUnderlyingEffect());
        assertEquals(1_500L, releaseA.remainingDurationMs());

        SourceKeyedEffectRegistry.ReleaseResult releaseB = registry.release(key, "mini-b", 1_500L);
        assertTrue(releaseB.removeUnderlyingEffect());
    }

    @Test
    void failedRefreshRollbackRestoresPreviousSourceExpiry() {
        SourceKeyedEffectRegistry registry = new SourceKeyedEffectRegistry();
        SourceKeyedEffectRegistry.EffectKey key =
                new SourceKeyedEffectRegistry.EffectKey("world", TARGET, "shared-effect");
        registry.retain(key, "mini-a", 2_000L, 1_000L);

        SourceKeyedEffectRegistry.RetainResult refresh = registry.retain(key, "mini-a", 4_000L, 1_100L);
        registry.rollback(key, "mini-a", refresh.previousExpiryMs());

        SourceKeyedEffectRegistry.ReleaseResult release = registry.release(key, "unknown-source", 1_500L);
        assertFalse(release.removeUnderlyingEffect());
        assertEquals(500L, release.remainingDurationMs());
    }
}
