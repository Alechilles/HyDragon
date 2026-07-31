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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Asset contract for Miniwyvern attack animations and randomized sound pools. */
final class MiniwyvernAttackFeedbackAssetTest {
    private static final Path ROOT = Path.of(System.getProperty("hydragon.project.basedir", "."));

    @Test
    void attackSoundPoolsUseTrimmedMonoVorbisClipsWithoutImmediateRepeats() throws IOException {
        assertPool("Projectile", List.of(
                "Projectile_01.ogg", "Projectile_02.ogg",
                "Projectile_03.ogg", "Projectile_04.ogg"));
        assertPool("Bite", List.of("Bite_01.ogg", "Bite_02.ogg", "Bite_03.ogg"));
    }

    @Test
    void everyAttackPlaysItsAnimationBeforeAndItsSoundAtDispatch() throws IOException {
        JsonObject component = load("Server/NPC/Roles/Creature/HyDragon/Components/"
                + "Component_HyDragon_Instruction_Miniwyvern_Aerial_Defend.json");
        List<JsonArray> sequences = new ArrayList<>();
        collectActionSequences(component, sequences);
        int projectileCount = 0;
        int biteCount = 0;
        for (JsonArray actions : sequences) {
            for (int index = 0; index < actions.size(); index++) {
                JsonObject action = actions.get(index).getAsJsonObject();
                if (!"Attack".equals(type(action))) {
                    continue;
                }
                String attack = action.getAsJsonObject("Attack").get("Compute").getAsString();
                if (attack.startsWith("TalentProjectile")) {
                    assertFeedback(actions, index, "Shoot", "SFX_HyDragon_Miniwyvern_Projectile");
                    projectileCount++;
                } else if (attack.startsWith("SwoopAttack")) {
                    assertFeedback(actions, index, "Bite", "SFX_HyDragon_Miniwyvern_Bite");
                    biteCount++;
                }
            }
        }
        assertEquals(11, projectileCount, "every projectile talent branch and echo needs feedback");
        assertEquals(4, biteCount, "every swoop damage profile needs feedback");
    }

    @Test
    void miniwyvernModelPreservesTheUserAuthoredAttackAnimationsAndFlightTuning() throws IOException {
        JsonObject model = load("Server/Models/HyDragon/Wyvern_Mini/Wyvern_Mini.json");
        assertEquals(0.75, model.get("EyeHeight").getAsDouble(), 0.0);
        JsonObject sets = model.getAsJsonObject("AnimationSets");
        assertEquals("NPC/HyDragon/Wyvern_Mini/Animations/Bite.blockyanim",
                sets.getAsJsonObject("Bite").getAsJsonArray("Animations").get(0)
                        .getAsJsonObject().get("Animation").getAsString());
        assertEquals("NPC/HyDragon/Wyvern_Mini/Animations/Shoot.blockyanim",
                sets.getAsJsonObject("Shoot").getAsJsonArray("Animations").get(0)
                        .getAsJsonObject().get("Animation").getAsString());
        JsonArray fly = sets.getAsJsonObject("Fly").getAsJsonArray("Animations");
        assertEquals(0.2, fly.get(0).getAsJsonObject().get("BlendingDuration").getAsDouble(), 0.0);
        assertEquals(0.1, fly.get(1).getAsJsonObject().get("Weight").getAsDouble(), 0.0);
        assertEquals(0.2, fly.get(1).getAsJsonObject().get("BlendingDuration").getAsDouble(), 0.0);
    }

    private static void assertFeedback(JsonArray actions, int attackIndex,
            String animation, String soundEvent) {
        assertTrue(attackIndex > 0 && attackIndex + 1 < actions.size(),
                "attack feedback must be adjacent to dispatch");
        JsonObject before = actions.get(attackIndex - 1).getAsJsonObject();
        assertEquals("PlayAnimation", type(before));
        assertEquals("Action", before.get("Slot").getAsString());
        assertEquals(animation, before.get("Animation").getAsString());
        JsonObject after = actions.get(attackIndex + 1).getAsJsonObject();
        assertEquals("PlaySound", type(after));
        assertEquals(soundEvent, after.get("SoundEventId").getAsString());
    }

    private static void collectActionSequences(JsonElement element, List<JsonArray> sequences) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectActionSequences(child, sequences);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        for (var entry : element.getAsJsonObject().entrySet()) {
            if ("Actions".equals(entry.getKey()) && entry.getValue().isJsonArray()) {
                sequences.add(entry.getValue().getAsJsonArray());
            }
            collectActionSequences(entry.getValue(), sequences);
        }
    }

    private static String type(JsonObject object) {
        return object.has("Type") ? object.get("Type").getAsString() : null;
    }

    private static JsonObject load(String relativePath) throws IOException {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relativePath))).getAsJsonObject();
    }

    private static void assertPool(String family, List<String> files) throws IOException {
        Path eventPath = ROOT.resolve(Path.of("Server", "Audio", "SoundEvents", "SFX", "HyDragon",
                "Wyvern_Mini", "SFX_HyDragon_Miniwyvern_" + family + ".json"));
        assertTrue(Files.isRegularFile(eventPath), () -> "missing attack SoundEvent " + eventPath);
        JsonObject event = JsonParser.parseString(Files.readString(eventPath)).getAsJsonObject();
        assertEquals("SFX_Attn_Quiet", event.get("Parent").getAsString());
        assertEquals(1, event.getAsJsonArray("Layers").size(), "attack sound must use one randomized layer");
        JsonObject layer = event.getAsJsonArray("Layers").get(0).getAsJsonObject();
        assertEquals(1, layer.get("RoundRobinHistorySize").getAsInt());
        assertEquals(files.stream().map(name -> "Sounds/HyDragon/Wyvern_Mini/Attack/" + name).toList(),
                layer.getAsJsonArray("Files").asList().stream().map(JsonElement::getAsString).toList());
        for (String file : files) {
            Path audio = ROOT.resolve(Path.of("Common", "Sounds", "HyDragon", "Wyvern_Mini", "Attack", file));
            assertTrue(Files.isRegularFile(audio), () -> "missing attack sound " + audio);
            assertMonoVorbis(audio);
        }
    }

    private static void assertMonoVorbis(Path soundPath) throws IOException {
        byte[] bytes = Files.readAllBytes(soundPath);
        byte[] identificationHeader = {1, 'v', 'o', 'r', 'b', 'i', 's'};
        for (int index = 0; index <= bytes.length - identificationHeader.length - 5; index++) {
            boolean matches = true;
            for (int offset = 0; offset < identificationHeader.length; offset++) {
                if (bytes[index + offset] != identificationHeader[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                assertEquals(1, Byte.toUnsignedInt(bytes[index + identificationHeader.length + 4]),
                        () -> soundPath + " must be mono");
                return;
            }
        }
        throw new AssertionError("missing Vorbis identification header in " + soundPath);
    }
}
