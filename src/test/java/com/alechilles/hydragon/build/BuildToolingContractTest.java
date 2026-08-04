package com.alechilles.hydragon.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Protects the shared Gradle workspace workflow. */
final class BuildToolingContractTest {
    private final Path projectRoot = Path.of(System.getProperty("hydragon.project.basedir"));

    @Test
    void repositoryShipsTheGradleWrapperContract() throws IOException {
        assertTrue(Files.isRegularFile(projectRoot.resolve("gradlew")));
        assertTrue(Files.isRegularFile(projectRoot.resolve("gradlew.bat")));
        assertTrue(Files.size(projectRoot.resolve("gradle/wrapper/gradle-wrapper.jar")) > 0L);

        String properties = Files.readString(
                projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties"));
        assertTrue(properties.contains("gradle-9.5.1-bin.zip"));
    }

    @Test
    void gradleBuildUsesWorkspaceTameworkAndLinkedAssetSources() throws IOException {
        String build = Files.readString(projectRoot.resolve("build.gradle"));

        assertTrue(build.contains("compileOnly files(tameworkProject.sourceSets.main.output)"));
        assertTrue(build.contains("assetPackSourceDirectory = workspaceAssetsDirectory"));
        assertTrue(build.contains("prepareWorkspaceAssets"));
    }

    @Test
    void buildDocumentationUsesTheSharedWorkspace() throws IOException {
        String documentation = Files.readString(projectRoot.resolve("BUILDING.md"));
        assertTrue(documentation.contains("..\\gradlew.bat stageAllModAssets"));
        assertTrue(documentation.contains("..\\gradlew.bat runAllMods"));
        assertTrue(documentation.contains(".\\gradlew.bat clean test packagingTest"));
    }

    @Test
    void configRegistryAvoidsTheRuntimeCompilersUnsupportedRecursiveAssetMapBound() throws IOException {
        String repository = Files.readString(projectRoot.resolve(
                "src/main/java/com/alechilles/hydragon/config/HyDragonConfigRepository.java"));

        assertFalse(repository.contains(
                "JsonAssetWithMap<String, DefaultAssetMap<String, T>>"));
        assertFalse(repository.contains(
                "AssetStore<String, T, DefaultAssetMap<String, T>>"));
    }
}
