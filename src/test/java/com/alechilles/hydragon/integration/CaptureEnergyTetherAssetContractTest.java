package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CaptureEnergyTetherAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", ".")).resolve("src/main/resources");

    @Test
    void captureMoteUsesTheSoulLanternTwoSpawnerContract() throws Exception {
        String item = read("Server/Item/Items/Ingredient/Draconic_Stone.json");
        String system = read("Server/Particles/HyDragon/DragonStone/HyDragon_DragonStone_CaptureMote.particlesystem");
        String mote = read("Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Particle.particlespawner");
        String trail = read("Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Trail.particlespawner");

        assertTrue(item.contains("\"BeamFromTarget\": true"));
        assertTrue(item.contains("\"HomingProjectileSpawnIntervalSeconds\": 0.2"));
        assertTrue(item.contains("\"HomingProjectileMaxConcurrent\": 6"));
        assertEquals(List.of(
                "HyDragon_DragonStone_CaptureMote_Particle",
                "HyDragon_DragonStone_CaptureMote_Trail"), spawnerIds(system));
        assertTrue(trailSpawnerHasStartOffset(system));
        assertSoulLanternTexturesAndAttractors(mote);
        assertSoulLanternTexturesAndAttractors(trail);
        assertFalse(Files.exists(ROOT.resolve(
                "Server/Particles/HyDragon/DragonStone/Spawners/HyDragon_DragonStone_CaptureMote_Sparks.particlespawner")));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }

    private static List<String> spawnerIds(String system) {
        Matcher matcher = Pattern.compile("\\\"SpawnerId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(system);
        return matcher.results().map(result -> result.group(1)).toList();
    }

    private static boolean trailSpawnerHasStartOffset(String system) {
        Pattern trailSpawner = Pattern.compile("\\{\\s*\\\"SpawnerId\\\"\\s*:\\s*\\\"HyDragon_DragonStone_CaptureMote_Trail\\\""
                + "\\s*,\\s*\\\"PositionOffset\\\"\\s*:\\s*\\{\\s*\\\"Y\\\"\\s*:\\s*0\\s*}\\s*}");
        return trailSpawner.matcher(system).find();
    }

    private static void assertSoulLanternTexturesAndAttractors(String spawner) {
        assertTrue(spawner.contains("Particles/Textures/Basic/Glow.png"));
        assertTrue(spawner.contains("Particles/Textures/UVMotion/FlowMap4.png"));
        assertTrue(spawner.contains("\"RadialAcceleration\": -1.0"));
        assertTrue(spawner.contains("\"RadialTangentAcceleration\": 1.0"));
        assertTrue(spawner.contains("\"DampingMultiplier\": {\n        \"X\": 0.5,\n        \"Y\": 0.2,\n        \"Z\": 0.5\n      }"));
        assertTrue(spawner.contains("\"Radius\": 0.2"));
        assertTrue(spawner.contains("\"RadialTangentAcceleration\": -5.0"));
        assertTrue(spawner.contains("\"RadialAxis\": {\n        \"X\": 60.0\n      }"));
    }
}
