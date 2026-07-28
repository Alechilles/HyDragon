package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaptureEnergyTetherAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));

    @Test
    void capturePresentationUsesAContinuousTetherAndDirectionalRibbons() throws Exception {
        String item = read("Server/Item/Items/Ingredient/Draconic_Stone.json");
        String mote = read("Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Particle.particlespawner");
        String trail = read("Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Trail.particlespawner");
        String sparks = read("Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Sparks.particlespawner");

        assertTrue(item.contains("\"BeamFromTarget\": true"));
        assertTrue(item.contains("\"HomingProjectileSpawnIntervalSeconds\": 0.2"));
        assertTrue(item.contains("\"HomingProjectileMaxConcurrent\": 6"));
        assertTrue(mote.contains("Particles/Textures/Basic/Glow_Direction2.png"));
        assertTrue(trail.contains("Particles/Textures/Basic/Glow_Direction2.png"));
        assertFalse(mote.contains("Particles/Textures/Circles/Circle_Glow.png"));
        assertTrue(sparks.contains("\"Min\": 3.0"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }
}
