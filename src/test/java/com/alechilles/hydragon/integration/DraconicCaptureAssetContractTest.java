package com.alechilles.hydragon.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DraconicCaptureAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));

    @Test
    void baseStoneUsesPrimaryCaptureChannelWithTerminalCompletion() throws Exception {
        String item = read("Server/Item/Items/Ingredient/Draconic_Stone.json");

        assertTrue(item.contains("\"Primary\""));
        assertTrue(item.contains("\"Type\": \"TameworkCaptureChannel\""));
        assertTrue(item.contains("\"Phase\": \"Begin\""));
        assertTrue(item.contains("\"Type\": \"Charging\""));
        assertTrue(item.contains("\"Phase\": \"Cancel\""));
        assertTrue(item.contains("\"Phase\": \"Complete\""));
    }

    @Test
    void everyMetalStoneResolvesToItsOwnSpawnerPowerTier() throws Exception {
        Map<String, Integer> tiers = Map.of(
                "Draconic_Stone", 1,
                "Draconic_Stone_Thorium", 2,
                "Draconic_Stone_Cobalt", 3,
                "Draconic_Stone_Adamantium", 4,
                "Draconic_Stone_Ancient", 5);
        Map<String, String> configs = Map.of(
                "Draconic_Stone", "HyDragonDraconicStone.json",
                "Draconic_Stone_Thorium", "HyDragonDraconicStoneThorium.json",
                "Draconic_Stone_Cobalt", "HyDragonDraconicStoneCobalt.json",
                "Draconic_Stone_Adamantium", "HyDragonDraconicStoneAdamantium.json",
                "Draconic_Stone_Ancient", "HyDragonDraconicStoneAncient.json");

        for (Map.Entry<String, Integer> tier : tiers.entrySet()) {
            String config = read("Server/Tamework/Items/Spawners/" + configs.get(tier.getKey()));
            assertTrue(config.contains("\"EmptyItemId\": \"" + tier.getKey() + "\""));
            assertTrue(config.contains("\"Power\": " + tier.getValue()));
        }
    }

    @Test
    void wildDragonCaptureRequiresTranquilizationAndMapsEverySupportedRole() throws Exception {
        String config = read("Server/Tamework/Items/Spawners/HyDragonDraconicStone.json");

        assertTrue(config.contains("\"RequiredEffectId\": \"Tw_Status_Tranquilized\""));
        assertTrue(config.contains("\"MaxHealthPercent\": 20.0"));
        for (String role : new String[] {
                "NordicDrake", "Hydra", "RockDrakeT1", "RockDrakeT2", "RockDrakeT3"
        }) {
            assertTrue(config.contains("\"" + role + "\""));
            assertTrue(config.contains("\"" + role + "\": \"Tamed_" + role + "\""));
        }
    }

    @Test
    void soulBondItemDispatchesItsRegisteredPrimaryInteraction() throws Exception {
        String item = read("Server/Item/Items/Ingredient/Draconic_Soul_Bond.json");

        assertTrue(item.contains("\"Primary\""));
        assertTrue(item.contains("\"Type\": \"HyDragonSoulBond\""));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(ROOT.resolve(relative)).replace("\r\n", "\n");
    }
}
