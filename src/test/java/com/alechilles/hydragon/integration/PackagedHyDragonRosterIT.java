package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.TameworkApi;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** Verify-stage gate for the packaged Dragon Horn roster contract shared by both plugins. */
final class PackagedHyDragonRosterIT {
    private static final String HYDRAGON_VERSION = "0.2.1";
    private static final String TAMEWORK_VERSION = "3.0.0";
    private static final String TAMEWORK_RANGE = ">=3.0.0 <4.0.0";
    private static final List<String> STONES = List.of(
            "HyDragonDraconicStone",
            "HyDragonDraconicStoneThorium",
            "HyDragonDraconicStoneCobalt",
            "HyDragonDraconicStoneAdamantium",
            "HyDragonDraconicStoneAncient");

    @Test
    void packagedVersionsAndRequiredDependencyAgree() {
        Path hydragon = packaged("hydragon.packaged.jar");
        Path tamework = packaged("hydragon.tamework.jar");
        Path pom = Path.of(System.getProperty("hydragon.project.basedir"))
                .resolve("pom.xml").toAbsolutePath().normalize();

        PackagedDependencyContract.Verification verification =
                PackagedDependencyContract.verify(
                        hydragon,
                        tamework,
                        pom,
                        TameworkBridge.REQUIRED_TAMEWORK_RANGE);

        assertTrue(verification.valid(), verification::describe);
        PackagedDependencyContract.Evidence evidence = verification.evidence();
        assertEquals(HYDRAGON_VERSION, evidence.hydragonVersion());
        assertEquals(TAMEWORK_VERSION, evidence.tameworkVersion());
        assertEquals(TAMEWORK_VERSION, evidence.pomTameworkVersion());
        assertEquals(TAMEWORK_RANGE, evidence.manifestDependencyRange());
        assertEquals(TAMEWORK_RANGE, TameworkBridge.REQUIRED_TAMEWORK_RANGE);
    }

    @Test
    void packagedAssetsSelectResolvedSpendAndBondedRosterStorage() throws Exception {
        Path hydragon = packaged("hydragon.packaged.jar");
        try (ZipFile zip = new ZipFile(hydragon.toFile())) {
            String horn = text(zip, "Server/Tamework/Items/Commands/HyDragonDragonHorn.json");
            assertContains(horn,
                    "\"RosterStorage\": \"BondedCompanions\"",
                    "\"BondedRosterId\": \"hydragon:dragon_horn\"",
                    "\"LinkEnabled\": false",
                    "\"LinkUseTogglesMembership\": false");
            assertFalse(horn.contains("\"CommandFamilyId\""));
            assertFalse(horn.contains("ProjectRosterToItemMetadata"));
            assertNotNull(zip.getEntry("Server/Item/Items/Tool/HyDragon_Dragon_Horn.json"));
            String baseStone = text(zip,
                    "Server/Tamework/Items/Spawners/HyDragonDraconicStone.json");
            assertContains(baseStone,
                    "\"SourceConsumption\": \"ResolvedAttempt\"",
                    "\"SuccessDisposition\": \"StoreBondedCompanion\"",
                    "\"BondedRosterId\": \"hydragon:dragon_horn\"",
                    "\"RequiredCommandConfigId\": \"HyDragonDragonHorn\"",
                    "\"RequireCommandAccessItem\": true");
            for (String stone : STONES) {
                String config = text(zip, "Server/Tamework/Items/Spawners/" + stone + ".json");
                if (!stone.equals("HyDragonDraconicStone")) {
                    assertContains(config, "\"Parent\": \"HyDragonDraconicStone\"");
                }
                assertFalse(config.contains("\"Vessel\""));
                assertFalse(config.contains("\"FilledItemId\""));
            }
            Set<String> entries = zip.stream().map(ZipEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertFalse(entries.contains("Server/Item/Items/Ingredient/Soul_Bound_Wyvern.json"));
            assertFalse(entries.contains("Server/Item/Items/Tool/HyDragon_Command_Whistle.json"));
            assertFalse(entries.stream().anyMatch(name -> name.contains("Draconic_Stone_Filled")));
        }
    }

    @Test
    void packagedAssetsAndApiExposeSharedPoliciesAndBondedApi() throws Exception {
        Path hydragon = packaged("hydragon.packaged.jar");
        Path tamework = packaged("hydragon.tamework.jar");
        try (ZipFile hy = new ZipFile(hydragon.toFile()); ZipFile tw = new ZipFile(tamework.toFile())) {
            String fullPolicy = text(hy,
                    "Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json");
            String miniPolicy = text(hy,
                    "Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json");
            assertContains(fullPolicy,
                    "\"RosterId\": \"hydragon:dragon_horn\"",
                    "\"FamilyId\": \"hydragon:full_dragons\"",
                    "\"MaximumActive\": 1");
            assertContains(miniPolicy,
                    "\"RosterId\": \"hydragon:dragon_horn\"",
                    "\"FamilyId\": \"hydragon:soulbound_mini\"",
                    "\"MaximumOwned\": 1",
                    "\"MaximumActive\": 1");

            Set<String> entries = hy.stream().map(ZipEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> packagedPolicies = entries.stream()
                    .filter(name -> name.startsWith(
                            "Server/Tamework/BondedCompanions/Rosters/"))
                    .filter(name -> name.endsWith(".json"))
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of(
                    "Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json",
                    "Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json"),
                    packagedPolicies);
            assertFalse(entries.contains(
                    "Server/Tamework/PopulationGroups/HyDragonFullDragons.json"));
            assertFalse(entries.contains(
                    "Server/Tamework/PopulationGroups/HyDragonSoulboundMiniwyvern.json"));

            for (String companion : List.of("HyDragonFullDragons", "HyDragonMiniwyvern")) {
                String config = text(hy, "Server/Tamework/Companion/" + companion + ".json");
                assertContains(config, "\"ReturnHomeTeleportDistance\"");
                assertFalse(config.contains("\"Travel\""));
                assertFalse(config.contains("\"Summon\""));
                assertFalse(config.contains("\"Revive\""));
            }

            assertNotNull(hy.getEntry("Server/Item/Items/Ingredient/Wyvern_Egg.json"));
            assertNotNull(tw.getEntry(
                    "com/alechilles/alecstamework/api/BondedCompanionApi.class"));
            assertNotNull(tw.getEntry(
                    "com/alechilles/alecstamework/api/BondedCompanionReviveRequest.class"));
            assertFalse(tw.stream().map(ZipEntry::getName)
                    .anyMatch(name -> name.contains("BondedVessel")));
        }

        assertNotNull(TameworkApi.class.getMethod("bondedCompanions"));
    }

    private static Path packaged(String property) {
        Path path = Path.of(System.getProperty(property)).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(path), () -> "missing packaged artifact: " + path);
        return path;
    }

    private static String text(ZipFile zip, String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        assertNotNull(entry, () -> "missing packaged entry " + entryName);
        try (var input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertContains(String text, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(text.contains(fragment), () -> "missing packaged contract fragment " + fragment);
        }
    }

}
