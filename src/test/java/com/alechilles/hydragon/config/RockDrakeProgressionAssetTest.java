package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RockDrakeProgressionAssetTest {
    private static final List<String> ROLES = List.of(
            "Tamed_RockDrakeT1", "Tamed_RockDrakeT2", "Tamed_RockDrakeT3");
    private static final List<String> LOCALES = List.of(
            "en-US", "de-DE", "es-ES", "fr-FR", "pt-BR");

    @Test
    void allTamedRockDrakeTiersShareTheApprovedThirtyLevelProgression() throws IOException {
        JsonObject leveling = json(Path.of("Server", "Tamework", "Leveling", "HyDragonRockDrake.json"));
        assertEquals(ROLES, strings(leveling.getAsJsonArray("RoleIds")));
        assertEquals(30, leveling.getAsJsonObject("Levels").get("MaxLevel").getAsInt());
        assertEquals(160.0, leveling.getAsJsonObject("Levels").get("BaseXp").getAsDouble());
        assertEquals(1.09, leveling.getAsJsonObject("Levels").get("GrowthFactor").getAsDouble());
        assertTrue(leveling.getAsJsonObject("XpSources").getAsJsonObject("Combat").get("Enabled").getAsBoolean());
        assertTrue(leveling.getAsJsonObject("XpSources").getAsJsonObject("Summoned").get("Enabled").getAsBoolean());
        assertEquals(0.042482758620689655, growth(leveling, "MaxHealthMultiplier"));
        assertEquals(0.038482758620689655, growth(leveling, "DamageDealtMultiplier"));
        assertEquals(1, leveling.getAsJsonObject("TalentPoints").get("PointsPerLevel").getAsInt());
    }

    @Test
    void rockDrakeTreeHasCombatDefenseAndFullSummonTimerLinesInEveryLocale() throws IOException {
        JsonArray talents = json(Path.of("Server", "Tamework", "Talents", "HyDragonRockDrake.json"))
                .getAsJsonArray("Talents");
        assertEquals(10, talents.size());
        Map<String, JsonObject> byId = new LinkedHashMap<>();
        for (JsonElement element : talents) {
            JsonObject talent = element.getAsJsonObject();
            assertTrue(byId.put(talent.get("Id").getAsString(), talent) == null, "duplicate talent");
        }
        assertEquals(Set.of(
                "RockDrake_CrushingJaws", "RockDrake_TectonicMomentum",
                "RockDrake_GraniteHide", "RockDrake_FortressScales",
                "RockDrake_EnduringStride", "RockDrake_DeepStoneFocus",
                "RockDrake_DragonboundPact", "RockDrake_SwiftRecall",
                "RockDrake_EternalWings", "RockDrake_HornmastersCall"), byId.keySet());
        assertEquals(List.of("RockDrake_DragonboundPact"), strings(
                byId.get("RockDrake_SwiftRecall").getAsJsonArray("RequiresTalentIds")));
        assertEquals(List.of("RockDrake_SwiftRecall"), strings(
                byId.get("RockDrake_EternalWings").getAsJsonArray("RequiresTalentIds")));
        assertEquals(List.of("RockDrake_EternalWings"), strings(
                byId.get("RockDrake_HornmastersCall").getAsJsonArray("RequiresTalentIds")));

        for (String locale : LOCALES) {
            Map<String, String> entries = localeEntries(locale);
            for (JsonObject talent : byId.values()) {
                assertTrue(entries.containsKey(talent.get("Branch").getAsString()), locale + " branch");
                assertTrue(entries.containsKey(talent.get("DisplayName").getAsString()), locale + " name");
                assertTrue(entries.containsKey(talent.get("Description").getAsString()), locale + " description");
            }
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<String> strings(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }

    private static double growth(JsonObject leveling, String effectKey) {
        return leveling.getAsJsonObject("StatGrowth").getAsJsonArray("Effects").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(effect -> effectKey.equals(effect.get("EffectKey").getAsString()))
                .findFirst().orElseThrow().get("PerLevel").getAsDouble();
    }

    private static Map<String, String> localeEntries(String locale) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of("Server", "Languages", locale, "server.lang"))) {
            int separator = line.indexOf('=');
            if (separator > 0 && !line.startsWith("#")) {
                entries.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return entries;
    }
}
