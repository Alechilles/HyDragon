package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToxicHydraVariantAssetTest {
    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();

    @Test
    void sharedRangedChoreographyUsesElementVariablesWithoutTimingDrift() throws Exception {
        JsonArray direct = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/Hydra_Ice_Ball.json")
                .getAsJsonArray("Interactions");
        assertEquals(8, direct.size());
        assertAnimation(direct.get(0).getAsJsonObject(), "PrepareShoot", 0.45);
        assertReplacement(direct.get(1).getAsJsonObject(), "Hydra_Ball_Charge_Effect",
                "Hydra_Ice_Ball_Charge_Effect", 1.0);
        assertReplacement(direct.get(2).getAsJsonObject(), "Hydra_Ball_Launch",
                "Hydra_Ice_Ball_Launch", 0.0);
        assertReplacement(direct.get(3).getAsJsonObject(), "Hydra_Ball_Charge_Effect",
                "Hydra_Ice_Ball_Charge_Effect", 0.5);
        assertReplacement(direct.get(4).getAsJsonObject(), "Hydra_Ball_Launch",
                "Hydra_Ice_Ball_Launch", 0.0);
        assertReplacement(direct.get(5).getAsJsonObject(), "Hydra_Ball_Charge_Effect",
                "Hydra_Ice_Ball_Charge_Effect", 0.5);
        assertReplacement(direct.get(6).getAsJsonObject(), "Hydra_Ball_Launch",
                "Hydra_Ice_Ball_Launch", 0.0);
        assertAnimation(direct.get(7).getAsJsonObject(), "FinishShoot", 0.5);

        JsonArray rain = json("Server/Item/RootInteractions/NPCs/Creature/HyDragon/"
                + "Root_NPC_Hydra_RainShoot_Barrage.json")
                .getAsJsonArray("Interactions").get(0).getAsJsonObject().getAsJsonArray("Interactions");
        assertEquals(40, rain.size());
        for (int shot = 0; shot < 20; shot++) {
            assertReplacement(rain.get(shot * 2).getAsJsonObject(), "Hydra_Rain_Charge_Effect",
                    "Hydra_Rain_Ice_Charge_Effect", 0.3);
            assertReplacement(rain.get(shot * 2 + 1).getAsJsonObject(), "Hydra_Rain_Launch",
                    "Hydra_Rain_Ice_Launch", 0.0);
        }
    }

    @Test
    void iceRolesProvideAllFourRangedDefaults() throws Exception {
        for (String role : List.of("Hydra.json", "Tamed_Hydra.json")) {
            JsonObject vars = json("Server/NPC/Roles/Creature/HyDragon/Hydra/" + role)
                    .getAsJsonObject("Modify").getAsJsonObject("_InteractionVars");
            assertVariableLeaf(vars, "Hydra_Ball_Charge_Effect", "Hydra_Ice_Ball_Charge_Effect");
            assertVariableLeaf(vars, "Hydra_Ball_Launch", "Hydra_Ice_Ball_Launch");
            assertVariableLeaf(vars, "Hydra_Rain_Charge_Effect", "Hydra_Rain_Ice_Charge_Effect");
            assertVariableLeaf(vars, "Hydra_Rain_Launch", "Hydra_Rain_Ice_Launch");
        }
    }

    @Test
    void iceLeafInteractionsRetainExistingProjectileBehavior() throws Exception {
        JsonObject directCharge = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/"
                + "Hydra_Ice_Ball_Charge_Effect.json");
        assertEquals("Simple", directCharge.get("Type").getAsString());
        assertFalse(directCharge.has("RunTime"));
        assertEquals("ChargeShoot", directCharge.getAsJsonObject("Effects")
                .get("ItemAnimationId").getAsString());

        JsonObject directLaunch = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/"
                + "Hydra_Ice_Ball_Launch.json");
        assertEquals("TameworkLaunchProjectile", directLaunch.get("Type").getAsString());
        assertFalse(directLaunch.has("RunTime"));
        assertEquals("Hydra_Ice_Ball", directLaunch.get("ProjectileId").getAsString());
        assertEquals("Direct", directLaunch.get("TrajectoryMode").getAsString());
        assertEquals("CAETargetSlot", directLaunch.get("TargetSlot").getAsString());
        assertEquals(3.0, directLaunch.getAsJsonObject("ImpactEffect").get("Radius").getAsDouble());
        assertTrue(directLaunch.getAsJsonObject("ImpactEffect").get("ExcludeSource").getAsBoolean());

        JsonObject rainCharge = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/"
                + "Hydra_Rain_Ice_Charge_Effect.json");
        assertEquals("Simple", rainCharge.get("Type").getAsString());
        assertFalse(rainCharge.has("RunTime"));

        JsonObject rainLaunch = json("Server/Item/Interactions/NPCs/HyDragon/Hydra/"
                + "Hydra_Rain_Ice_Launch.json");
        assertEquals("TameworkLaunchProjectile", rainLaunch.get("Type").getAsString());
        assertFalse(rainLaunch.has("RunTime"));
        assertEquals("Hydra_Rain_Ice_Ball", rainLaunch.get("ProjectileId").getAsString());
        assertEquals(6.0, rainLaunch.get("RandomAroundSourceMinRadius").getAsDouble());
        assertEquals(15.0, rainLaunch.get("RandomAroundSourceMaxRadius").getAsDouble());
        JsonObject hazard = rainLaunch.getAsJsonObject("LingeringHazard");
        assertEquals(4.0, hazard.get("Radius").getAsDouble());
        assertEquals(6.0, hazard.get("DurationSeconds").getAsDouble());
        assertEquals(1.0, hazard.get("TickIntervalSeconds").getAsDouble());
        assertEquals(5.0, hazard.get("DamagePerTick").getAsDouble());
        assertEquals("Chilled", hazard.get("EffectId").getAsString());
        assertEquals("hydragon.rain_ice_hazard", hazard.get("SourceTypeId").getAsString());
    }

    private static void assertAnimation(JsonObject interaction, String animation, double runTime) {
        assertEquals("Simple", interaction.get("Type").getAsString());
        assertEquals(animation, interaction.getAsJsonObject("Effects").get("ItemAnimationId").getAsString());
        assertEquals(runTime, interaction.get("RunTime").getAsDouble());
    }

    private static void assertReplacement(JsonObject interaction, String variable, String leaf, double runTime) {
        assertEquals("Replace", interaction.get("Type").getAsString());
        assertTrue(interaction.get("DefaultOk").getAsBoolean());
        assertEquals(variable, interaction.get("Var").getAsString());
        assertEquals(runTime, interaction.get("RunTime").getAsDouble());
        JsonArray defaults = interaction.getAsJsonObject("DefaultValue").getAsJsonArray("Interactions");
        assertEquals(1, defaults.size());
        assertEquals(leaf, defaults.get(0).getAsString());
    }

    private static void assertVariableLeaf(JsonObject vars, String variable, String leaf) {
        JsonArray interactions = vars.getAsJsonObject(variable).getAsJsonArray("Interactions");
        assertEquals(1, interactions.size());
        assertEquals(leaf, interactions.get(0).getAsJsonObject().get("Parent").getAsString());
    }

    private static JsonObject json(String relativePath) throws IOException {
        return JsonParser.parseString(read(relativePath)).getAsJsonObject();
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
