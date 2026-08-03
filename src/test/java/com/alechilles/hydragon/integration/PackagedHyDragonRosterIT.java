package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alechilles.alecstamework.api.TameworkApi;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final String HYDRAGON_VERSION = "1.0.0";
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
            assertFalse(baseStone.contains("\"SuccessDisposition\": \"TameAndCommandLink\""));
            assertFalse(baseStone.contains("\"CommandFamilyId\""));
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
    void packagedToxicHydraAssetsAreCompleteAndTextureResolves() throws Exception {
        Path hydragon = packaged("hydragon.packaged.jar");
        try (ZipFile zip = new ZipFile(hydragon.toFile())) {
            for (String entry : List.of(
                    "Server/NPC/Roles/Creature/HyDragon/Hydra/Hydra_Toxic.json",
                    "Server/NPC/Roles/Creature/HyDragon/Hydra/Tamed_Hydra_Toxic.json",
                    "Server/Projectiles/HyDragon/Hydra/Hydra_Toxic_Ball.json",
                    "Server/Projectiles/HyDragon/Hydra/Hydra_Rain_Toxic_Ball.json",
                    "Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Charge_Effect.json",
                    "Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Toxic_Ball_Launch.json",
                    "Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Charge_Effect.json",
                    "Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Rain_Toxic_Launch.json",
                    "Server/Models/HyDragon/Hydra/Hydra_Toxic.json",
                    "Server/Models/Projectiles/HyDragon/Hydra_Toxic_Ball_Projectile.json",
                    "Server/Patchwork/Patches/HyDragon/ToxicHydra_Zone1_Swamps_Predator.json")) {
                assertNotNull(zip.getEntry(entry), () -> "missing packaged entry " + entry);
            }
            JsonObject model = json(zip, "Server/Models/HyDragon/Hydra/Hydra_Toxic.json");
            String texture = model.get("Texture").getAsString();
            assertEquals("NPC/HyDragon/Hydra/Model/Toxic.png", texture);
            assertNotNull(zip.getEntry("Common/" + texture));
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

            JsonObject miniwyvern = json(hy, "Server/Tamework/Companion/HyDragonMiniwyvern.json");
            JsonObject nordic = json(hy, "Server/Tamework/Companion/HyDragonNordicDrake.json");
            JsonObject groundOnly = json(hy, "Server/Tamework/Companion/HyDragonFullDragons.json");
            assertCompanionLifecycle(miniwyvern);
            assertCompanionLifecycle(nordic);
            assertCompanionLifecycle(groundOnly);
            assertEquals(List.of(
                    "Tamed_Wyvern_Mini_Wild", "Tamed_Wyvern_Mini_Nature", "Tamed_Wyvern_Mini_Toxic",
                    "Tamed_Wyvern_Mini_Fire", "Tamed_Wyvern_Mini_Void", "Tamed_Wyvern_Mini_Lightning",
                    "Tamed_Wyvern_Mini_Ice"), roleIds(miniwyvern));
            assertFlightToggle(miniwyvern);
            assertEquals(List.of("Tamed_NordicDrake"), roleIds(nordic));
            assertFlightToggle(nordic);
            assertEquals(List.of(
                    "Tamed_Hydra", "Tamed_Hydra_Toxic", "Tamed_RockDrakeT1", "Tamed_RockDrakeT2",
                    "Tamed_RockDrakeT3"),
                    roleIds(groundOnly));
            String groundOnlyJson = groundOnly.toString();
            assertFalse(groundOnly.getAsJsonObject("Command").has("FlightToggle"));
            assertFalse(groundOnlyJson.contains("FlightToggle"));
            assertFalse(groundOnlyJson.contains("AirborneMode"));
            assertFalse(groundOnlyJson.contains("HyDragon.Command.ToggleAirborneMode"));

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

    private static JsonObject json(ZipFile zip, String entryName) throws IOException {
        return JsonParser.parseString(text(zip, entryName)).getAsJsonObject();
    }

    private static List<String> roleIds(JsonObject config) {
        return config.getAsJsonArray("RoleIds").asList().stream()
                .map(JsonElement::getAsString)
                .toList();
    }

    private static void assertFlightToggle(JsonObject config) {
        JsonObject toggle = config.getAsJsonObject("Command").getAsJsonObject("FlightToggle");
        assertEquals(Set.of("Enabled", "HookId"), toggle.keySet());
        assertTrue(toggle.get("Enabled").getAsBoolean());
        assertEquals("HyDragon.Command.ToggleAirborneMode", toggle.get("HookId").getAsString());
    }

    private static void assertCompanionLifecycle(JsonObject config) {
        JsonObject command = config.getAsJsonObject("Command");
        assertTrue(command.has("ReturnHomeTeleportDistance"));
        assertFalse(command.has("Travel"));
        assertFalse(command.has("Summon"));
        assertFalse(command.has("Revive"));
    }

    private static void assertContains(String text, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(text.contains(fragment), () -> "missing packaged contract fragment " + fragment);
        }
    }

}
