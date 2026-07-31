package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression contract for the deterministic Miniwyvern aerial swoop cycle. */
final class MiniwyvernSwoopAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));

    @Test
    void aerialSwoopUsesDedicatedTimerPhasesAndExactDamageProfiles() throws IOException {
        String component = read("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json").replaceAll("\\s+", "");
        assertTrue(component.contains("\"Name\":\"Miniwyvern_Swoop_Cooldown\""));
        assertTrue(component.contains("\"TalentId\":\"SwoopMastery\""));
        assertTrue(component.contains("\"StartValueRange\":[18,24]"));
        assertTrue(component.contains("\"TalentId\":\"RelentlessSwoop\""));
        assertTrue(component.contains("\"StartValueRange\":[20,26]"));
        assertTrue(component.contains("\"TalentId\":\"SwoopCadence\""));
        assertTrue(component.contains("\"StartValueRange\":[22,30]"));
        assertTrue(component.contains("\"StartValueRange\":[25,35]"));
        assertTrue(component.contains("\"Miniwyvern_Swoop_Approach\""));
        assertTrue(component.contains("\"SwoopApproachTimeout\":{\"Value\":[6,6]}"));
        assertTrue(component.contains("\"Miniwyvern_Swoop_Pending\""));
        assertTrue(component.contains("\"Miniwyvern_Swooping\""));
        assertTrue(component.contains("\"Miniwyvern_Swoop_Strike_Committed\""));
        assertTrue(component.contains("\"State\":\".Swoop\""));
        assertTrue(component.contains("\"State\":\".Recovery\""));
        assertTrue(component.contains("\"DesiredAltitudeRange\":{\"Compute\":\"SwoopAltitudeRange\"}"));
        assertTrue(component.contains("\"RelativeSpeed\":0.7"));
        assertTrue(component.contains("\"RelativeSpeed\":0.55"));
        assertFalse(component.contains("\"Type\": \"Random\""));

        for (int damage : new int[] {16, 20, 24, 28}) {
            String suffix = switch (damage) {
                case 16 -> "";
                case 20 -> "_Ferocity";
                case 24 -> "_Rending";
                default -> "_Mastery";
            };
            String asset = read("Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/"
                    + "Wyvern_Mini_Swoop_Bite_Damage" + suffix + ".json");
            assertTrue(asset.contains("\"Physical\":" + damage));
            assertTrue(asset.contains("\"RandomPercentageModifier\":0"));
            assertFalse(asset.contains("Knockback"));
        }
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
