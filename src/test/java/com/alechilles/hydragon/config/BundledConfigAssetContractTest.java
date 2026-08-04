package com.alechilles.hydragon.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.JsonAsset;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.codec.util.RawJsonReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BundledConfigAssetContractTest {
    private static final Path CONFIG_ROOT = Path.of("Server", "HyDragon");
    private static final Pattern TEXTURE_FIELD = Pattern.compile("\\\"Texture\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final List<String> MINI_WYVERN_FORMS = List.of(
            "Wild", "Nature", "Toxic", "Fire", "Void", "Lightning", "Ice");

    @Test
    void companionPoliciesUseCurrentTopLevelSelectionFields() throws IOException {
        for (String asset : List.of(
                "HyDragonMiniwyvern.json",
                "HyDragonNordicDrake.json",
                "HyDragonFullDragons.json")) {
            Path path = Path.of("Server", "Tamework", "Companion", asset);
            com.google.gson.JsonObject config = com.google.gson.JsonParser.parseString(Files.readString(path))
                    .getAsJsonObject();
            assertFalse(config.has("General"), path + " must not use the removed General wrapper");
            assertTrue(config.get("Enabled").getAsBoolean(), path + " must remain enabled");
            assertEquals(100, config.get("Priority").getAsInt(), path + " must retain its role-selection priority");
        }
    }

    @Test
    void miniwyvernRoleSwapAssetsCoverEveryNonSelfDestinationAtExactCost() throws IOException {
        Map<String, String> essenceByForm = Map.of(
                "Wild", "Draconic_Essence", "Nature", "Draconic_Essence_Nature",
                "Toxic", "Draconic_Essence_Toxic", "Fire", "Draconic_Essence_Fire",
                "Void", "Draconic_Essence_Void", "Lightning", "Draconic_Essence_Lightning",
                "Ice", "Draconic_Essence_Ice");
        List<String> roleIds = MINI_WYVERN_FORMS.stream().map(form -> "Tamed_Wyvern_Mini_" + form).toList();
        String roster = Files.readString(Path.of("Server", "Tamework", "BondedCompanions", "Rosters", "HyDragonMiniwyvern.json"));
        String companion = Files.readString(Path.of("Server", "Tamework", "Companion", "HyDragonMiniwyvern.json"));
        String horn = Files.readString(Path.of("Server", "Tamework", "Items", "Commands", "HyDragonDragonHorn.json"));
        String breeding = Files.readString(Path.of("Server", "Tamework", "Breeding", "HyDragonBondedCompanions.json"));
        assertEquals(roleIds, JsonParser.parseString(roster).getAsJsonObject().getAsJsonArray("AllowedRoles")
                .asList().stream().map(element -> element.getAsString()).toList(),
                "roster must contain exactly seven form roles");
        assertEquals(roleIds, JsonParser.parseString(companion).getAsJsonObject().getAsJsonArray("RoleIds")
                .asList().stream().map(element -> element.getAsString()).toList(),
                "companion must contain exactly seven form roles");
        for (String roleId : roleIds) {
            assertTrue(roster.contains("\"" + roleId + "\""), "roster omits " + roleId);
            assertTrue(companion.contains("\"" + roleId + "\""), "companion omits " + roleId);
            assertTrue(horn.contains("\"" + roleId + "\""), "Dragon Horn omits " + roleId);
            assertTrue(breeding.contains("\"" + roleId + "\""), "breeding config omits " + roleId);
        }
        for (String source : MINI_WYVERN_FORMS) {
            String roleId = "Tamed_Wyvern_Mini_" + source;
            Path rolePath = Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini", roleId + ".json");
            String role = Files.readString(rolePath);
            String configId = "HyDragonIntWyvernMini_" + source;
            assertTrue(role.contains("\"Reference\": \"Template_Wyvern_Mini_Flying_Tamed\""), rolePath.toString());
            assertTrue(role.contains("\"InteractionConfigId\": \"" + configId + "\""), rolePath.toString());
            String interaction = Files.readString(Path.of("Server", "Tamework", "Interactions", configId + ".json"));
            assertEquals(6, occurrences(interaction, "\"Type\": \"Custom\""), configId);
            assertTrue(interaction.contains("\"Type\": \"Feed\""), configId);
            assertTrue(interaction.contains("\"Type\": \"ModeCycle\""), configId);
            assertFalse(interaction.contains("\"Role\": \"" + roleId + "\""), configId + " must exclude self");
            for (String destination : MINI_WYVERN_FORMS) {
                if (destination.equals(source)) continue;
                String destinationRole = "Tamed_Wyvern_Mini_" + destination;
                String essence = essenceByForm.get(destination);
                assertTrue(interaction.contains("\"Role\": \"" + destinationRole + "\", \"ChangeAppearance\": true"), configId + " -> " + destinationRole);
                assertTrue(interaction.contains("\"Items\": [\"" + essence + "\"], \"Quantity\": 8"), configId + " must charge " + essence);
            }
            assertEquals(6, occurrences(interaction, "\"IsTamed\": true"), configId);
            assertEquals(6, occurrences(interaction, "\"PlayerIsOwner\": true"), configId);
        }
        assertFalse(Files.exists(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini", "Tamed_Wyvern_Mini_Water.json")));
        assertFalse(Files.exists(Path.of("Server", "NPC", "Roles", "Creature", "HyDragon", "Wyvern_Mini", "Tamed_Wyvern_Mini_Wind.json")));
        assertFalse(Files.exists(Path.of("Server", "Tamework", "Interactions", "HyDragonIntWyvernMini_Water.json")));
        assertFalse(Files.exists(Path.of("Server", "Tamework", "Interactions", "HyDragonIntWyvernMini_Wind.json")));
        assertFalse(Files.exists(Path.of("Server", "Models", "HyDragon", "Wyvern_Mini", "Wyvern_Mini_Water.json")));
        assertFalse(Files.exists(Path.of("Server", "Models", "HyDragon", "Wyvern_Mini", "Wyvern_Mini_Wind.json")));
    }

    @Test
    void elementalMiniwyvernsApplyAVisibleOwnerAuraEffect() throws IOException {
        Map<String, AuraVisual> expectedAuraByForm = Map.of(
                "Nature", new AuraVisual("HyDragon_Miniwyvern_Nature_Regeneration", "Icons/StatusEffects/HyDragon/Miniwyvern_Nature_Aura.png"),
                "Toxic", new AuraVisual("HyDragon_Miniwyvern_Toxic_Aura", "Icons/StatusEffects/HyDragon/Miniwyvern_Toxic_Aura.png"),
                "Fire", new AuraVisual("HyDragon_Miniwyvern_Fire_Aura", "Icons/StatusEffects/HyDragon/Miniwyvern_Fire_Aura.png"),
                "Void", new AuraVisual("HyDragon_Miniwyvern_Void_Aura", "Icons/StatusEffects/HyDragon/Miniwyvern_Void_Aura.png"),
                "Lightning", new AuraVisual("HyDragon_Miniwyvern_Lightning_Boon", "Icons/StatusEffects/HyDragon/Miniwyvern_Lightning_Aura.png"),
                "Ice", new AuraVisual("HyDragon_Miniwyvern_Ice_Aura", "Icons/StatusEffects/HyDragon/Miniwyvern_Ice_Aura.png"));
        for (Map.Entry<String, AuraVisual> expected : expectedAuraByForm.entrySet()) {
            Path archetype = Path.of("Server", "HyDragon", "MiniwyvernArchetypes", expected.getKey() + ".json");
            String json = Files.readString(archetype);
            var passiveEffects = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("PassiveEffects");
            assertEquals(List.of(expected.getValue().effectId()),
                    passiveEffects.asList().stream().map(element -> element.getAsString()).toList(),
                    archetype + " must maintain its owner aura EntityEffect while summoned");
            Path effect = Path.of("Server", "Entity", "Effects", "Status", expected.getValue().effectId() + ".json");
            assertTrue(Files.exists(effect), expected.getValue().effectId() + " must be a bundled EntityEffect");
            String effectJson = Files.readString(effect);
            assertTrue(effectJson.contains("\"StatusEffectIcon\": \"" + expected.getValue().iconPath() + "\""),
                    expected.getValue().effectId() + " must declare its generated HUD icon");
            assertFalse(effectJson.contains("\"EntityBottomTint\""),
                    expected.getValue().effectId() + " must not tint the owner while its aura is active");
            assertFalse(effectJson.contains("\"EntityTopTint\""),
                    expected.getValue().effectId() + " must not tint the owner while its aura is active");
            assertTrue(Files.exists(Path.of("Common", expected.getValue().iconPath())),
                    expected.getValue().iconPath() + " must be bundled with the HUD effect");
        }
    }

    @Test
    void voidExposureAndIceSlowHaveTargetReadableVisuals() throws IOException {
        Path voidEffect = Path.of("Server", "Entity", "Effects", "Status", "HyDragon_Miniwyvern_Void_Exposure.json");
        String voidJson = Files.readString(voidEffect);
        assertTrue(voidJson.contains("\"ModelVFXId\": \"HyDragon_Void_Debuff\""),
                "Void Exposure must use the bundled target ModelVFX");
        Path voidVfx = Path.of("Server", "Entity", "ModelVFX", "HyDragon_Void_Debuff.json");
        assertTrue(Files.exists(voidVfx),
                "Void Exposure ModelVFX must be bundled");
        String voidVfxJson = Files.readString(voidVfx);
        assertTrue(voidVfxJson.contains("\"LoopOption\": \"LoopMirror\""),
                "Void Exposure must retain its authored mirrored-loop timing");
        assertTrue(voidVfxJson.contains("\"PostColor\": \"#560c7d\""),
                "Void Exposure must retain its authored void post-color");
        assertTrue(voidVfxJson.contains("\"PostColorOpacity\": 0.7"),
                "Void Exposure must retain its authored post-color opacity");

        Path iceEffect = Path.of("Server", "Entity", "Effects", "Status", "HyDragon_Miniwyvern_Ice_Slow.json");
        String iceJson = Files.readString(iceEffect);
        assertTrue(iceJson.contains("\"HorizontalSpeedMultiplier\": 0.8"),
                "Ice Slow must reduce target movement speed by twenty percent");
        assertFalse(iceJson.contains("\"EntityBottomTint\""), "Ice Slow must not recolor its target");
        assertFalse(iceJson.contains("\"EntityTopTint\""), "Ice Slow must not recolor its target");
        assertTrue(iceJson.contains("\"SystemId\": \"Effect_Snow\""),
                "Ice Slow must attach the vanilla snow effect to the target");
        assertTrue(iceJson.contains("\"TargetEntityPart\": \"Self\""),
                "Ice Slow frost aura must follow the affected target");
        assertTrue(iceJson.contains("\"PositionOffset\": { \"Y\": 0.05 }"),
                "Ice Slow frost aura must sit at the target's feet");

        assertFalse(Files.exists(Path.of("Server", "Particles", "HyDragon", "Miniwyvern",
                "HyDragon_Miniwyvern_Ice_ChillGround.particlesystem")),
                "Ice Slow must not retain the replaced custom ground particle system");
    }

    private record AuraVisual(String effectId, String iconPath) { }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    @Test
    void itemAssetsDoNotUseEmptyLightColors() throws IOException {
        try (var assets = Files.walk(Path.of("Server", "Item", "Items"))) {
            for (Path asset : assets.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                assertFalse(Files.readString(asset).matches("(?s).*\\\"Light\\\"\\s*:\\s*\\{\\s*\\\"Color\\\"\\s*:\\s*\\\"\\\".*"),
                        asset + " must omit Light rather than supplying an invalid empty color");
            }
        }
    }

    @Test
    void bundledAssetsDecodeWithoutUnknownFieldsAndCrossValidate() throws IOException {
        List<DragonSpeciesConfig> species = decodeDirectory(
                "DragonSpecies", DragonSpeciesConfig.class, DragonSpeciesConfig.CODEC);
        List<MiniwyvernArchetypeConfig> archetypes = decodeDirectory(
                "MiniwyvernArchetypes", MiniwyvernArchetypeConfig.class, MiniwyvernArchetypeConfig.CODEC);
        List<DragonEncounterConfig> encounters = decodeDirectory(
                "Encounters", DragonEncounterConfig.class, DragonEncounterConfig.CODEC);

        HyDragonConfigRepository.Snapshot snapshot = HyDragonConfigRepository.buildSnapshot(
                species, archetypes, encounters);
        assertTrue(snapshot.isValid(), () -> String.join("\n", snapshot.issues()));
    }

    @Test
    void serviceOwnedHealingEffectsArePresentationOnly() throws IOException {
        for (String asset : List.of(
                "HyDragon_Miniwyvern_Nature_Regeneration.json",
                "HyDragon_Miniwyvern_Water_Heal.json")) {
            Path path = Path.of("Server", "Entity", "Effects", "Status", asset);
            String json = Files.readString(path);
            assertFalse(json.contains("\"Parent\""), path + " must not inherit an additional healing mechanic");
            assertFalse(json.contains("\"StatModifiers\""), path + " must not apply health outside the capped service");
            assertTrue(json.contains("\"StatusEffectIcon\"") || json.contains("\"ApplicationEffects\""),
                    path + " should retain visible feedback");
        }
    }

    @Test
    void natureHealingPresentationIsSilentAndFinite() throws IOException {
        Path archetype = Path.of("Server", "HyDragon", "MiniwyvernArchetypes", "Nature.json");
        String archetypeJson = Files.readString(archetype);
        String mistId = "HyDragon_Miniwyvern_Nature_HealingMist";
        assertTrue(archetypeJson.contains("\"ParticleAndSoundIds\": [ \"" + mistId + "\" ]"),
                "Nature healing must emit only its bounded custom mist");
        assertFalse(archetypeJson.contains("Effect_Heal"), "Nature healing must not use the persistent stock heal effect");
        assertFalse(archetypeJson.contains("SFX_"), "Nature healing must not play a sound");

        Path system = Path.of("Server", "Particles", "HyDragon", "Miniwyvern", mistId + ".particlesystem");
        Path spawner = Path.of("Server", "Particles", "HyDragon", "Miniwyvern", "Spawners", mistId + ".particlespawner");
        Path sparkSpawner = Path.of("Server", "Particles", "HyDragon", "Miniwyvern", "Spawners", mistId + "_Spark.particlespawner");
        assertTrue(Files.exists(system), "Nature healing mist particle system must be bundled");
        assertTrue(Files.exists(spawner), "Nature healing mist spawner must be bundled");
        assertTrue(Files.exists(sparkSpawner), "Nature healing magic spark spawner must be bundled");
        String systemJson = Files.readString(system);
        String spawnerJson = Files.readString(spawner);
        assertTrue(systemJson.contains("\"LifeSpan\": 1.25"), "Nature healing magic must have a bounded system lifetime");
        assertTrue(systemJson.contains("\"Y\": 0.45"), "Nature healing magic must originate above the player's feet");
        assertTrue(spawnerJson.contains("\"Max\": 1.1"), "Nature healing mist particles must expire quickly");
        assertTrue(spawnerJson.contains("\"Opacity\": 0.66"), "Nature healing mist must be twenty percent more visible");
        assertTrue(Files.readString(sparkSpawner).contains("\"Opacity\": 0.96"),
                "Nature healing sparks must be twenty percent more visible");
    }

    @Test
    void miniwyvernFormConfigsBindOnlyApprovedFormsToTheirRoles() throws IOException {
        Map<String, MiniwyvernArchetypeConfig> archetypes = decodeDirectory(
                "MiniwyvernArchetypes", MiniwyvernArchetypeConfig.class,
                MiniwyvernArchetypeConfig.CODEC).stream().collect(Collectors.toMap(
                        MiniwyvernArchetypeConfig::getId, Function.identity()));
        assertEquals(Set.of("wild", "nature", "toxic", "fire", "void", "lightning", "ice"),
                archetypes.keySet());
        for (String id : archetypes.keySet()) {
            MiniwyvernArchetypeConfig archetype = archetypes.get(id);
            String title = Character.toUpperCase(id.charAt(0)) + id.substring(1);
            assertEquals("Tamed_Wyvern_Mini_" + title, archetype.getRoleId(), id);
        }
        assertTrue(archetypes.get("nature").getPassiveEffects().contains("HyDragon_Miniwyvern_Nature_Regeneration"));
        assertFalse(Files.readString(Path.of("Server", "HyDragon", "MiniwyvernArchetypes", "Toxic.json"))
                .contains("ActiveAbilities"), "Miniwyvern combat must be owned by role assets");
        assertOwnerAttackAura(archetypes, "toxic", "HyDragon_Miniwyvern_Toxic_Weakness", 6.0);
        assertEquals(0.12, archetypes.get("toxic").getOwnerAttackAura().getDamageReductionFraction());
        assertOwnerAttackAura(archetypes, "fire", "HyDragon_Miniwyvern_Fire_Burn", 4.0);
        assertOwnerAttackAura(archetypes, "ice", "HyDragon_Miniwyvern_Ice_Slow", 4.0);
        assertOwnerAttackAura(archetypes, "void", "HyDragon_Miniwyvern_Void_Exposure", 6.0);
    }

    @Test
    void elementalMiniwyvernEssenceBondUpgradesResolveToBundledEffects() throws IOException {
        for (String form : List.of("Fire", "Ice", "Lightning", "Nature", "Toxic", "Void")) {
            Path archetypePath = Path.of("Server", "HyDragon", "MiniwyvernArchetypes", form + ".json");
            JsonObject archetype = JsonParser.parseString(Files.readString(archetypePath)).getAsJsonObject();
            JsonObject aura = archetype.getAsJsonObject("EssenceBondAura");
            assertTrue(aura != null, form + " must declare EssenceBondAura");
            JsonArray upgrades = aura.getAsJsonArray("Upgrades");
            assertEquals(8, upgrades.size(), form + " must declare eight upgrade tiers");
            Set<String> talentIds = new HashSet<>();
            Map<String, JsonObject> byTalentSuffix = new HashMap<>();
            for (var element : upgrades) {
                JsonObject upgrade = element.getAsJsonObject();
                String talentId = upgrade.get("TalentId").getAsString();
                assertTrue(talentId.startsWith("Miniwyvern_" + form + "_"), talentId);
                assertTrue(talentIds.add(talentId), form + " repeats " + talentId);
                for (String suffix : List.of("Focus", "Attunement", "Amplification", "Resonance",
                        "Efficiency", "Harmony", "Mastery", "Ascendance")) {
                    if (talentId.endsWith(suffix)) byTalentSuffix.put(suffix, upgrade);
                }
                for (String field : List.of("TargetEffectId", "WardEffectId")) {
                    if (!upgrade.has(field)) continue;
                    String effectId = upgrade.get(field).getAsString();
                    Path effectPath = Path.of("Server", "Entity", "Effects", "Status", effectId + ".json");
                    assertTrue(Files.exists(effectPath), form + " references missing " + effectId);
                    JsonObject effect = JsonParser.parseString(Files.readString(effectPath)).getAsJsonObject();
                    assertTrue(effect.has("Name"), effectId + " must retain a localized status name");
                }
            }
            for (String suffix : List.of("Attunement", "Resonance")) {
                JsonObject upgrade = byTalentSuffix.get(suffix);
                assertEquals("ward", upgrade.get("Semantic").getAsString(),
                        form + " " + suffix + " must be a Ward payload");
                assertTrue(upgrade.has("WardEffectId"), form + " " + suffix + " must select a Ward effect");
            }
            JsonObject harmony = byTalentSuffix.get("Harmony");
            assertTrue(Set.of("ward", "conditionalward", "siphon")
                            .contains(harmony.get("Semantic").getAsString()),
                    form + " Harmony must remain on the Ward branch");
            assertTrue(harmony.has("WardEffectId"), form + " Harmony must select a Ward effect");
            for (String suffix : List.of("Focus", "Amplification", "Efficiency")) {
                JsonObject upgrade = byTalentSuffix.get(suffix);
                assertFalse("ward".equals(upgrade.get("Semantic").getAsString()),
                        form + " " + suffix + " must remain on the pressure branch");
            }
        }
    }

    @Test
    void lightningWardUsesRuntimeGeneralReductionInsteadOfDamageResistanceClaims() throws IOException {
        for (String asset : List.of(
                "HyDragon_Miniwyvern_Lightning_Ward.json",
                "HyDragon_Miniwyvern_Lightning_Ward_8.json",
                "HyDragon_Miniwyvern_Lightning_Ward_8_Knockback.json",
                "HyDragon_Miniwyvern_Lightning_Ward_12.json",
                "HyDragon_Miniwyvern_Lightning_Ward_15.json")) {
            Path path = Path.of("Server", "Entity", "Effects", "Status", asset);
            JsonObject status = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertFalse(status.has("DamageResistance"),
                    path + " must not claim cause-scoped resistance for general Static Ward reduction");
        }
        JsonObject lightning = JsonParser.parseString(Files.readString(
                Path.of("Server", "HyDragon", "MiniwyvernArchetypes", "Lightning.json"))).getAsJsonObject();
        JsonArray upgrades = lightning.getAsJsonObject("EssenceBondAura").getAsJsonArray("Upgrades");
        Map<String, Double> expected = Map.of("Attunement", 0.05D, "Resonance", 0.08D,
                "Mastery", 0.12D, "Ascendance", 0.15D);
        for (var element : upgrades) {
            JsonObject upgrade = element.getAsJsonObject();
            String suffix = upgrade.get("TalentId").getAsString();
            suffix = expected.keySet().stream().filter(suffix::endsWith).findFirst().orElse("");
            if (expected.containsKey(suffix)) {
                assertEquals(expected.get(suffix), upgrade.get("WardDamageReductionFraction").getAsDouble(),
                        "Lightning " + suffix + " must carry the runtime general reduction");
            }
        }
    }

    @Test
    void miniwyvernTalentKeysArePresentInEveryServerLocale() throws IOException {
        Set<String> requiredKeys = new HashSet<>();
        for (String form : List.of("Fire", "Ice", "Lightning", "Nature", "Toxic", "Void", "Wild")) {
            Path talentPath = Path.of("Server", "Tamework", "Talents", "HyDragonMiniwyvern" + form + ".json");
            JsonArray talents = JsonParser.parseString(Files.readString(talentPath))
                    .getAsJsonObject().getAsJsonArray("Talents");
            String prefix = "hydragon.talents.miniwyvern." + form.toLowerCase() + ".";
            for (var element : talents) {
                JsonObject talent = element.getAsJsonObject();
                for (String field : List.of("DisplayName", "Description", "Branch")) {
                    String key = talent.get(field).getAsString();
                    assertTrue(key.startsWith(prefix), form + " uses an unscoped " + field + " key: " + key);
                    requiredKeys.add(key);
                }
            }
        }
        for (String locale : List.of("en-US", "de-DE", "es-ES", "fr-FR", "pt-BR")) {
            Map<String, String> values = languageValues(Path.of("Server", "Languages", locale, "server.lang"));
            for (String key : requiredKeys) {
                String value = values.get(key);
                assertTrue(value != null && !value.isBlank(), locale + " omits " + key);
                assertFalse(value.toLowerCase().contains("undefined"), locale + " leaves " + key + " undefined");
                assertFalse(value.toLowerCase().contains("not implemented"), locale + " leaves " + key + " unfinished");
            }
        }
    }

    private static Map<String, String> languageValues(Path path) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readString(path).split("\\R")) {
            int delimiter = line.indexOf('=');
            if (delimiter > 0) values.put(line.substring(0, delimiter), line.substring(delimiter + 1));
        }
        return values;
    }

    private static void assertOwnerAttackAura(Map<String, MiniwyvernArchetypeConfig> archetypes,
            String form, String effectId, double durationSeconds) {
        MiniwyvernArchetypeConfig.OwnerAttackAura aura = archetypes.get(form).getOwnerAttackAura();
        assertEquals(effectId, aura.getEffectId(), form);
        assertEquals(durationSeconds, aura.getDurationSeconds(), form);
    }

    private static <T extends JsonAsset<String>> List<T> decodeDirectory(
            String directory,
            Class<T> type,
            AssetBuilderCodec<String, T> codec) throws IOException {
        Path assetDirectory = CONFIG_ROOT.resolve(directory);
        if (Files.notExists(assetDirectory)) return List.of();
        List<Path> paths;
        try (var stream = Files.list(assetDirectory)) {
            paths = stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        assertTrue(!paths.isEmpty(), "No bundled assets in " + directory);

        List<T> decoded = new ArrayList<>();
        for (Path path : paths) {
            String fileName = path.getFileName().toString();
            String key = fileName.substring(0, fileName.length() - ".json".length());
            AssetExtraInfo.Data data = new AssetExtraInfo.Data(type, key, null);
            AssetExtraInfo<String> extra = new AssetExtraInfo<>(path, data);
            try (RawJsonReader reader = new RawJsonReader(Files.readString(path).toCharArray())) {
                decoded.add(codec.decodeJsonAsset(reader, extra));
            }
            assertTrue(extra.getUnknownKeys().isEmpty(),
                    () -> path + " has unknown keys " + extra.getUnknownKeys());
        }
        return List.copyOf(decoded);
    }
}
