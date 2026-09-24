package com.alechilles.hydragon.build;

import com.alechilles.alecstamework.npc.movement.BuilderBodyMotionTameworkFlyingOrbit;
import com.google.gson.JsonParser;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.model.config.camera.CameraSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

/** Exercises the engine codecs that rejected the first GhoulDragon build at server startup. */
final class GhoulDragonAssetCodecTest {
    private static final Path SERVER = Path.of(System.getProperty("hydragon.project.basedir"))
            .resolve("src/main/resources/Server");

    @Test
    void meleeAnimationSettingsPassEngineValidation() throws Exception {
        ExtraInfo info = new ExtraInfo(ExtraInfo.UNSET_VERSION, ValidationResults::new);
        BsonDocument input = read("Item/Animations/NPC/HyDragon/GhoulDragon/GhoulDragon_Default.json");
        // Common animation files are checked by validateHyDragonAssets. This test exercises
        // the engine's required settings without booting a server to populate its asset cache.
        input.put("Animations", new BsonDocument());
        var animations = ItemPlayerAnimations.CODEC.decode(input, info);
        ItemPlayerAnimations.CODEC.validate(animations, info);
        info.getValidationResults().logOrThrowValidatorExceptions(HytaleLogger.getLogger());
    }

    @Test
    void avatarCameraDecodesUsingEngineCameraNodeValues() throws Exception {
        ExtraInfo info = new ExtraInfo(ExtraInfo.UNSET_VERSION, ValidationResults::new);
        var camera = CameraSettings.CODEC.decode(read(
                "Models/HyDragon/GhoulDragon/GhoulDragon_AvatarFlight.json").getDocument("Camera"), info);
        CameraSettings.CODEC.validate(camera, info);
        info.getValidationResults().logOrThrowValidatorExceptions(HytaleLogger.getLogger());
    }

    @Test
    void aerialPursuitPassesMovementBuilderValidation() throws Exception {
        var component = JsonParser.parseString(Files.readString(SERVER.resolve(
                "NPC/Roles/Creature/HyDragon/Components/Component_HyDragon_Instruction_Aerial_Melee_Pursuit.json")))
                .getAsJsonObject().getAsJsonObject("Content");
        // A negative altitude offset rejects NPC loading even though ordinary JSON validation passes.
        new BuilderBodyMotionTameworkFlyingOrbit().readConfig(component.get("BodyMotion"));
    }

    private static BsonDocument read(String relativePath) throws Exception {
        return BsonDocument.parse(Files.readString(SERVER.resolve(relativePath)));
    }
}
