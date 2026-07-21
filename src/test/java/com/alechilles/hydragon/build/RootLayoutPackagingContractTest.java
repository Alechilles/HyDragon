package com.alechilles.hydragon.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Protects the no-mass-move root-layout packaging contract. */
final class RootLayoutPackagingContractTest {
    private final Path projectRoot = Path.of(System.getProperty("hydragon.project.basedir"));

    @Test
    void manifestDeclaresCombinedPluginAndSupportedTameworkRange() throws IOException {
        String manifest = Files.readString(projectRoot.resolve("manifest.json"));

        assertTrue(manifest.contains("\"Main\": \"com.alechilles.hydragon.HyDragonPlugin\""));
        assertTrue(manifest.contains("\"IncludesAssetPack\": true"));
        assertTrue(manifest.contains("\">=3.0.0 <4.0.0\""));
        assertFalse(manifest.contains("Master of Flight"));
    }

    @Test
    void pomPackagesOnlyTheExplicitRootAssetTrees() throws IOException {
        String pom = Files.readString(projectRoot.resolve("pom.xml"));

        assertTrue(pom.contains("${project.basedir}/Common"));
        assertTrue(pom.contains("<targetPath>Common</targetPath>"));
        assertTrue(pom.contains("${project.basedir}/Server"));
        assertTrue(pom.contains("<targetPath>Server</targetPath>"));
        assertFalse(pom.contains("<directory>${project.basedir}</directory>\n                <filtering>false"));
    }

    @Test
    void sourceVersionMatchesFilteredManifestVersion() throws IOException {
        String pom = Files.readString(projectRoot.resolve("pom.xml"));
        String manifest = Files.readString(projectRoot.resolve("manifest.json"));

        assertTrue(pom.contains("<version>0.2.1</version>"));
        assertEquals(1, count(manifest, "${project.version}"));
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
