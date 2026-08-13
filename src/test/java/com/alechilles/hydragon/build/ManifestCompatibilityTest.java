package com.alechilles.hydragon.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.semver.SemverRange;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ManifestCompatibilityTest {
    @Test
    void shippedManifestRangeAcceptsSupportedServerVersionsOnly() throws IOException {
        Path manifestPath = Path.of(
                System.getProperty("hydragon.project.basedir", "."),
                "src", "main", "resources", "manifest.json");
        String serverVersionRange;
        try (Reader reader = Files.newBufferedReader(manifestPath)) {
            serverVersionRange = JsonParser.parseReader(reader)
                    .getAsJsonObject()
                    .get("ServerVersion")
                    .getAsString();
        }

        SemverRange range = SemverRange.fromString(serverVersionRange);

        assertAccepted(range, "0.5.7");
        assertAccepted(range, "0.6.0-pre.11");
        assertAccepted(range, "0.6.0");
        assertRejected(range, "0.5.6");
        assertRejected(range, "0.6.0-pre.0");
        assertRejected(range, "0.7.0");
    }

    private static void assertAccepted(SemverRange range, String version) {
        assertTrue(range.satisfies(Semver.fromString(version)), version + " must be accepted");
    }

    private static void assertRejected(SemverRange range, String version) {
        assertFalse(range.satisfies(Semver.fromString(version)), version + " must be rejected");
    }
}
