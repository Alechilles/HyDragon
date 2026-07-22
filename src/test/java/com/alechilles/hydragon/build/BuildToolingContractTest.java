package com.alechilles.hydragon.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Protects the wrapper and install workflow shared with Tamework. */
final class BuildToolingContractTest {
    private final Path projectRoot = Path.of(System.getProperty("hydragon.project.basedir"));

    @Test
    void repositoryShipsTheTameworkMavenWrapperContract() throws IOException {
        assertTrue(Files.isRegularFile(projectRoot.resolve("mvnw")));
        assertTrue(Files.isRegularFile(projectRoot.resolve("mvnw.cmd")));
        assertTrue(Files.size(projectRoot.resolve(".mvn/wrapper/maven-wrapper.jar")) > 0L);

        String properties = Files.readString(
                projectRoot.resolve(".mvn/wrapper/maven-wrapper.properties"));
        assertTrue(properties.contains("apache-maven-3.9.9-bin.zip"));
        assertTrue(properties.contains("maven-wrapper-3.3.2.jar"));
    }

    @Test
    void installProfileCopiesThePackagedJarToBothRuntimeLocations() throws IOException {
        String pom = Files.readString(projectRoot.resolve("pom.xml"));

        assertTrue(pom.contains("<id>install-plugin</id>"));
        assertTrue(pom.contains("<id>copy-plugin-to-server</id>"));
        assertTrue(pom.contains("${hytale.install.path}/Server/mods"));
        assertTrue(pom.contains("<id>copy-plugin-to-userdata</id>"));
        assertTrue(pom.contains("${hytale.userdata.path}/Mods"));
        assertTrue(pom.contains("C:/Users/22ale/AppData/Roaming/Hytale/UserData"));
        assertTrue(pom.contains("C:/Users/22ale/AppData/Roaming/Hytale/data/pre-release"));
    }

    @Test
    void buildDocumentationPreservesTameworkFirstOrdering() throws IOException {
        String documentation = Files.readString(projectRoot.resolve("BUILDING.md"));
        int tamework = documentation.indexOf(
                "..\\alecstamework\\mvnw.cmd package \"-Dmaven.test.skip=true\" -Pinstall-plugin");
        int hydragon = documentation.indexOf(
                ".\\mvnw.cmd package \"-Dmaven.test.skip=true\" -Pinstall-plugin");

        assertTrue(tamework >= 0);
        assertTrue(hydragon > tamework);
        assertTrue(documentation.contains(".\\mvnw.cmd clean verify"));
    }
}
