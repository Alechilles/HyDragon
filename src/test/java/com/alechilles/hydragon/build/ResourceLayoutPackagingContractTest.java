package com.alechilles.hydragon.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Protects the conventional Gradle resource-layout packaging contract. */
final class ResourceLayoutPackagingContractTest {
    private final Path projectRoot = Path.of(System.getProperty("hydragon.project.basedir"));

    @Test
    void manifestDeclaresCombinedPluginAndSupportedTameworkRange() throws IOException {
        String manifest = Files.readString(projectRoot.resolve("src/main/resources/manifest.json"));

        assertTrue(manifest.contains("\"Main\": \"com.alechilles.hydragon.HyDragonPlugin\""));
        assertTrue(manifest.contains("\"IncludesAssetPack\": true"));
        assertTrue(manifest.contains("\"ServerVersion\": \"0.5.x\""));
        assertTrue(manifest.contains("\"Alechilles:Alec's Tamework!\": \">=3.0.0 <4.0.0\""));
        assertFalse(manifest.contains("Master of Flight"));
    }

    @Test
    void gradlePackagesTheStandardResourceTree() throws IOException {
        String build = Files.readString(projectRoot.resolve("build.gradle"));

        assertTrue(build.contains("assetPackSourceDirectory = layout.projectDirectory.dir('src/main/resources')"));
        assertTrue(build.contains("exclude 'Common/**/Source/**', 'Common/**/*.bbmodel'"));
        assertFalse(build.contains("workspace-assets"));
    }

    @Test
    void sourceVersionMatchesManifestVersion() throws IOException {
        String properties = Files.readString(projectRoot.resolve("gradle.properties"));
        String manifest = Files.readString(projectRoot.resolve("src/main/resources/manifest.json"));

        assertTrue(properties.contains("mod_version=1.0.0"));
        assertEquals(1, count(manifest, "\"Version\": \"1.0.0\""));
    }

    private static int count(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
