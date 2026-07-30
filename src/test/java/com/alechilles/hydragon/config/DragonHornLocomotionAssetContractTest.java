package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class DragonHornLocomotionAssetContractTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));
    private static final List<String> COMMAND_IDS = List.of(
            "Follow", "Hold", "Recall", "MoveToPing", "Defend", "AttackTarget", "Idle", "ToggleAirborneMode");
    private static final Set<String> HORN_COMMAND_LANGUAGE_KEYS = Set.of(
            "hydragon.commands.defend.name",
            "hydragon.commands.defend.hud",
            "hydragon.commands.toggleAirborneMode.name",
            "hydragon.commands.toggleAirborneMode.hud");
    private static final List<String> LOCALES = List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR");

    @Test
    void dragonHornDefinesTheExplicitStatePreservingLocomotionCommandContract() throws IOException {
        JsonObject horn = readJson("Server/Tamework/Items/Commands/HyDragonDragonHorn.json");

        assertTrue(horn.has("CommandList"),
                "HyDragonDragonHorn must explicitly replace the inherited CommandList array");
        JsonArray commands = horn.getAsJsonArray("CommandList");
        assertNotNull(commands);
        assertEquals(COMMAND_IDS, commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(command -> command.get("Id").getAsString())
                .toList());
        assertFalse(commandIds(commands).contains("SetHome"));
        assertFalse(commandIds(commands).contains("ReturnHome"));

        JsonObject follow = command(commands, "Follow");
        assertTrue(follow.get("Default").getAsBoolean());
        assertEquals(1, commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(command -> command.has("Default") && command.get("Default").getAsBoolean())
                .count());

        assertDefendSteps(command(commands, "Defend").getAsJsonArray("Steps"));
        assertAttackTargetSteps(command(commands, "AttackTarget").getAsJsonArray("Steps"));
        assertToggleSteps(command(commands, "ToggleAirborneMode").getAsJsonArray("Steps"));

        assertIdenticalHornCommandCatalogs();
    }

    private static void assertDefendSteps(JsonArray steps) {
        assertEquals(3, steps.size());
        assertEquals(JsonParser.parseString("""
                { "Type": "ClearTarget", "TargetSlot": "LockedTarget" }
                """), steps.get(0));
        assertEquals(JsonParser.parseString("""
                { "Type": "SetTarget", "TargetSlot": "MasterTarget", "Source": "OwnerPlayer" }
                """), steps.get(1));
        assertEquals(JsonParser.parseString("""
                { "Type": "SetState", "State": "Defend" }
                """), steps.get(2));
    }

    private static void assertAttackTargetSteps(JsonArray steps) {
        assertEquals(2, steps.size());
        assertEquals(JsonParser.parseString("""
                {
                  "Type": "SetTarget",
                  "TargetSlot": "LockedTarget",
                  "Source": "CrosshairTarget",
                  "FailurePolicy": "AbortCommandForNpc"
                }
                """), steps.get(0));
        assertEquals(JsonParser.parseString("""
                { "Type": "SetState", "State": "Defend" }
                """), steps.get(1));
        assertFalse(steps.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(step -> "SetTarget".equals(step.get("Type").getAsString())
                        && "MasterTarget".equals(step.get("TargetSlot").getAsString())));
    }

    private static void assertToggleSteps(JsonArray steps) {
        assertEquals(1, steps.size());
        assertEquals(JsonParser.parseString("""
                { "Type": "TriggerHook", "HookId": "HyDragon.Command.ToggleAirborneMode" }
                """), steps.get(0));
    }

    private static void assertIdenticalHornCommandCatalogs() throws IOException {
        Map<String, Set<String>> languageKeysByLocale = new LinkedHashMap<>();
        for (String locale : LOCALES) {
            Set<String> keys = Files.readAllLines(ROOT.resolve("Server/Languages").resolve(locale).resolve("server.lang"))
                    .stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                    .map(line -> line.substring(0, line.indexOf('=')))
                    .filter(HORN_COMMAND_LANGUAGE_KEYS::contains)
                    .collect(Collectors.toSet());
            assertEquals(HORN_COMMAND_LANGUAGE_KEYS, keys, locale);
            languageKeysByLocale.put(locale, keys);
        }
        assertEquals(languageKeysByLocale.get("en-US"), Set.copyOf(languageKeysByLocale.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet())));
    }

    private static Set<String> commandIds(JsonArray commands) {
        return commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(command -> command.get("Id").getAsString())
                .collect(Collectors.toSet());
    }

    private static JsonObject command(JsonArray commands, String id) {
        return commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(command -> id.equals(command.get("Id").getAsString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing command " + id));
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relativePath))).getAsJsonObject();
    }
}
