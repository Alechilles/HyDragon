package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.hydragon.integration.PackagedDependencyContract.IssueCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Deterministic negative coverage for the packaged Tamework dependency boundary. */
final class PackagedDependencyContractIT {
    private static final String SUPPORTED_RANGE = ">=3.0.0 <4.0.0";

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingTameworkArtifactFailsBeforeGameplay() throws Exception {
        Path hydragon = pluginJar(
                "hydragon.jar", "0.2.1", SUPPORTED_RANGE);
        Path pom = pom("3.0.0");

        PackagedDependencyContract.Verification verification =
                PackagedDependencyContract.verify(
                        hydragon,
                        temporaryDirectory.resolve("missing-tamework.jar"),
                        pom,
                        SUPPORTED_RANGE);

        assertEquals(List.of(IssueCode.TAMEWORK_JAR_MISSING),
                verification.issueCodes());
    }

    @Test
    void lowerThanSupportedTameworkFailsBeforeGameplay() throws Exception {
        assertOutOfRange("2.9.9");
    }

    @Test
    void excludedUpperBoundaryTameworkFailsBeforeGameplay() throws Exception {
        assertOutOfRange("4.0.0");
    }

    @Test
    void manifestAndBridgeDependencyRangesMustAgree() throws Exception {
        Path hydragon = pluginJar(
                "hydragon.jar", "0.2.1", SUPPORTED_RANGE);
        Path tamework = pluginJar("tamework.jar", "3.0.0", null);

        PackagedDependencyContract.Verification verification =
                PackagedDependencyContract.verify(
                        hydragon, tamework, pom("3.0.0"), ">=3.1.0 <4.0.0");

        assertEquals(List.of(IssueCode.DEPENDENCY_RANGE_MISMATCH),
                verification.issueCodes());
    }

    @Test
    void packagedTameworkAndPomVersionsMustAgree() throws Exception {
        Path hydragon = pluginJar(
                "hydragon.jar", "0.2.1", SUPPORTED_RANGE);
        Path tamework = pluginJar("tamework.jar", "3.1.0", null);

        PackagedDependencyContract.Verification verification =
                PackagedDependencyContract.verify(
                        hydragon, tamework, pom("3.0.0"), SUPPORTED_RANGE);

        assertEquals(List.of(IssueCode.TAMEWORK_VERSION_MISMATCH),
                verification.issueCodes());
    }

    @Test
    void supportedLowerBoundaryPassesWithoutServerBootstrap() throws Exception {
        Path hydragon = pluginJar(
                "hydragon.jar", "0.2.1", SUPPORTED_RANGE);
        Path tamework = pluginJar("tamework.jar", "3.0.0", null);

        PackagedDependencyContract.Verification verification =
                PackagedDependencyContract.verify(
                        hydragon, tamework, pom("3.0.0"), SUPPORTED_RANGE);

        assertTrue(verification.valid(), verification::describe);
        assertEquals("0.2.1", verification.evidence().hydragonVersion());
        assertEquals("3.0.0", verification.evidence().tameworkVersion());
        assertEquals("3.0.0", verification.evidence().pomTameworkVersion());
        assertEquals(SUPPORTED_RANGE,
                verification.evidence().manifestDependencyRange());
    }

    private void assertOutOfRange(String version) throws Exception {
        Path hydragon = pluginJar(
                "hydragon.jar", "0.2.1", SUPPORTED_RANGE);
        Path tamework = pluginJar("tamework-" + version + ".jar", version, null);

        PackagedDependencyContract.Verification verification =
                PackagedDependencyContract.verify(
                        hydragon, tamework, pom(version), SUPPORTED_RANGE);

        assertEquals(List.of(IssueCode.TAMEWORK_VERSION_OUT_OF_RANGE),
                verification.issueCodes());
    }

    private Path pluginJar(
            String filename,
            String version,
            String tameworkRange) throws IOException {
        Path jar = temporaryDirectory.resolve(filename);
        try (ZipOutputStream output = new ZipOutputStream(
                java.nio.file.Files.newOutputStream(jar))) {
            write(output, "manifest.json", pluginManifest(version, tameworkRange));
            Manifest javaManifest = new Manifest();
            Attributes attributes = javaManifest.getMainAttributes();
            attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attributes.putValue("Plugin-Version", version);
            output.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            javaManifest.write(output);
            output.closeEntry();
        }
        return jar;
    }

    private Path pom(String tameworkVersion) throws IOException {
        Path pom = temporaryDirectory.resolve("pom-" + tameworkVersion + ".xml");
        java.nio.file.Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <properties>
                    <tamework.version>%s</tamework.version>
                  </properties>
                </project>
                """.formatted(tameworkVersion), StandardCharsets.UTF_8);
        return pom;
    }

    private static String pluginManifest(
            String version,
            String tameworkRange) {
        String dependencies = tameworkRange == null
                ? "{}"
                : "{\"Alechilles:Alec's Tamework!\":\"" + tameworkRange + "\"}";
        return "{\"Version\":\"" + version
                + "\",\"Dependencies\":" + dependencies + "}";
    }

    private static void write(
            ZipOutputStream output,
            String name,
            String value) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
