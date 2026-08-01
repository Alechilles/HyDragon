package com.alechilles.hydragon.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class NordicDrakeChainingDataSystemTest {
    @Test
    void provisionsChainingDataOnlyForTamedNordicDrakes() {
        AtomicInteger provisions = new AtomicInteger();
        NordicDrakeChainingDataSystem.provisionChainingData(
                "Tamed_NordicDrake", provisions::incrementAndGet);
        NordicDrakeChainingDataSystem.provisionChainingData(
                "NordicDrake", provisions::incrementAndGet);
        NordicDrakeChainingDataSystem.provisionChainingData(
                "Tamed_Hydra", provisions::incrementAndGet);
        NordicDrakeChainingDataSystem.provisionChainingData(
                null, provisions::incrementAndGet);

        assertEquals(1, provisions.get());
    }
}
