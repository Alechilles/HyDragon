package com.alechilles.hydragon.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** Verifies the actual release artifact after Gradle has packaged it. */
final class PackagedJarContractIT {
    @Test
    void packagedJarContainsOnlyThePluginAndSupportedAssetRoots() throws IOException {
        Path jar = Path.of(System.getProperty("hydragon.packaged.jar"));
        assertTrue(Files.isRegularFile(jar), () -> "missing packaged JAR: " + jar);

        Set<String> entries = new HashSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            zip.stream().map(ZipEntry::getName).forEach(entries::add);
        }

        assertTrue(entries.contains("manifest.json"));
        assertTrue(entries.contains("com/alechilles/hydragon/HyDragonPlugin.class"));
        assertTrue(entries.stream().anyMatch(name -> name.startsWith("Common/")));
        assertTrue(entries.stream().anyMatch(name -> name.startsWith("Server/")));
        assertTrue(entries.contains("Common/Items/HyDragon/Draconic_Essence_Wind.png"),
                "the Wind essence texture must ship with its item asset");
        for (String entry : Set.of(
                "Server/Audio/SoundEvents/SFX/HyDragon/Wyvern_Mini/SFX_HyDragon_Miniwyvern_Projectile.json",
                "Server/Audio/SoundEvents/SFX/HyDragon/Wyvern_Mini/SFX_HyDragon_Miniwyvern_Bite.json",
                "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_01.ogg",
                "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_02.ogg",
                "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_03.ogg",
                "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Projectile_04.ogg",
                "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_01.ogg",
                "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_02.ogg",
                "Common/Sounds/HyDragon/Wyvern_Mini/Attack/Bite_03.ogg")) {
            assertTrue(entries.contains(entry), "missing MiniWyvern attack-feedback resource " + entry);
        }
        assertFalse(entries.stream().anyMatch(name -> name.equals("HyDragon.zip") || name.startsWith("docs/")));
        assertFalse(entries.stream().anyMatch(name -> name.startsWith("target/") || name.startsWith(".idea/")));
        assertFalse(entries.stream().anyMatch(name -> name.contains("/Source/") || name.endsWith(".bbmodel")));
    }
}
