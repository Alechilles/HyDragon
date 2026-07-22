package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.CommandTimedSummoningApi;
import com.alechilles.alecstamework.api.CommandTimedSummoningRequest;
import com.alechilles.alecstamework.api.CompanionProvisioningApi;
import com.alechilles.alecstamework.api.CompanionProvisioningLinkRequest;
import com.alechilles.alecstamework.api.PaidCommandRevivalApi;
import com.alechilles.alecstamework.api.PaidCommandRevivalRequest;
import com.alechilles.alecstamework.api.TameworkApi;
import java.io.IOException;
import java.lang.reflect.Method;
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
    private static final List<String> STONES = List.of(
            "HyDragonDraconicStone",
            "HyDragonDraconicStoneThorium",
            "HyDragonDraconicStoneCobalt",
            "HyDragonDraconicStoneAdamantium",
            "HyDragonDraconicStoneAncient");

    @Test
    void packagedAssetsSelectResolvedSpendLiveTameAndOwnerRoster() throws Exception {
        Path hydragon = packaged("hydragon.packaged.jar");
        try (ZipFile zip = new ZipFile(hydragon.toFile())) {
            String horn = text(zip, "Server/Tamework/Items/Commands/HyDragonDragonHorn.json");
            assertContains(horn,
                    "\"CommandFamilyId\": \"hydragon:dragon_horn\"",
                    "\"RosterStorage\": \"OwnerCommandFamily\"",
                    "\"ProjectRosterToItemMetadata\": true");
            assertNotNull(zip.getEntry("Server/Item/Items/Tool/HyDragon_Dragon_Horn.json"));
            String baseStone = text(zip,
                    "Server/Tamework/Items/Spawners/HyDragonDraconicStone.json");
            assertContains(baseStone,
                    "\"SourceConsumption\": \"ResolvedAttempt\"",
                    "\"SuccessDisposition\": \"TameAndCommandLink\"",
                    "\"CommandFamilyId\": \"hydragon:dragon_horn\"",
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
    void packagedAssetsAndApiExposeCapsLeasesEggLinkAndMultiItemRevival() throws Exception {
        Path hydragon = packaged("hydragon.packaged.jar");
        Path tamework = packaged("hydragon.tamework.jar");
        try (ZipFile hy = new ZipFile(hydragon.toFile()); ZipFile tw = new ZipFile(tamework.toFile())) {
            String fullGroup = text(hy,
                    "Server/Tamework/PopulationGroups/HyDragonFullDragons.json");
            String miniGroup = text(hy,
                    "Server/Tamework/PopulationGroups/HyDragonSoulboundMiniwyvern.json");
            assertContains(fullGroup, "\"MaxActivePerOwner\": 1");
            assertContains(miniGroup,
                    "\"MaxOwnedPerOwner\": 1",
                    "\"MaxActivePerOwner\": 1");

            for (String companion : List.of("HyDragonFullDragons", "HyDragonMiniwyvern")) {
                String config = text(hy, "Server/Tamework/Companion/" + companion + ".json");
                assertContains(config,
                        "\"ActiveDurationMs\"",
                        "\"ResummonCooldownMs\"",
                        "\"ExpiryWarningThresholdsMs\"",
                        "\"Costs\"");
                assertTrue(occurrences(config, "\"ItemId\"") >= 2,
                        companion + " must package a multi-component revival example");
            }

            assertNotNull(hy.getEntry("Server/Item/Items/Ingredient/Wyvern_Egg.json"));
            assertNotNull(tw.getEntry(
                    "com/alechilles/alecstamework/api/CompanionProvisioningLinkRequest.class"));
            assertNotNull(tw.getEntry(
                    "com/alechilles/alecstamework/api/CommandTimedSummoningRequest.class"));
            assertNotNull(tw.getEntry(
                    "com/alechilles/alecstamework/api/PaidCommandRevivalRequest.class"));
            assertFalse(tw.stream().map(ZipEntry::getName)
                    .anyMatch(name -> name.contains("BondedVessel")));
        }

        Method provisionAndLink = CompanionProvisioningApi.class.getMethod(
                "provisionAndLink", CompanionProvisioningLinkRequest.class);
        Method timedSummon = CommandTimedSummoningApi.class.getMethod(
                "summon", CommandTimedSummoningRequest.class);
        Method timedDismiss = CommandTimedSummoningApi.class.getMethod(
                "dismiss", CommandTimedSummoningRequest.class);
        Method paidRevive = PaidCommandRevivalApi.class.getMethod(
                "revive", PaidCommandRevivalRequest.class);
        assertNotNull(provisionAndLink);
        assertNotNull(timedSummon);
        assertNotNull(timedDismiss);
        assertNotNull(paidRevive);
        assertNotNull(TameworkApi.class.getMethod("commandFamilyRosters"));
        assertNotNull(TameworkApi.class.getMethod("commandTimedSummoning"));
        assertNotNull(TameworkApi.class.getMethod("paidCommandRevival"));
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

    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int cursor = 0; (cursor = text.indexOf(fragment, cursor)) >= 0; cursor += fragment.length()) {
            count++;
        }
        return count;
    }
}
