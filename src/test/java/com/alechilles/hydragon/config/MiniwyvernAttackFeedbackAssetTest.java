package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
