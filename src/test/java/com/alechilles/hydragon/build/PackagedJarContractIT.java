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

/** Verifies the actual release artifact after Maven has packaged it. */
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
        assertFalse(entries.stream().anyMatch(name -> name.equals("HyDragon.zip") || name.startsWith("docs/")));
        assertFalse(entries.stream().anyMatch(name -> name.startsWith("target/") || name.startsWith(".idea/")));
    }
}
