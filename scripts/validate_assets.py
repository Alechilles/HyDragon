#!/usr/bin/env python3
"""Deterministic preflight validation for HyDragon's authored asset surface."""

from __future__ import annotations

import json
import hashlib
import os
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOTS = (ROOT / "Common", ROOT / "Server")
JSON_SUFFIXES = {".json", ".particlesystem", ".particlespawner", ".blockymodel", ".blockyanim"}
LOCALES = ("en-US", "pt-BR", "de-DE", "fr-FR", "es-ES")
REQUIRED_STATUS_MESSAGE_KEYS = {
    "messages.status.description",
    "messages.status.usage",
    "messages.status.unavailable",
    "messages.status.title",
    "messages.status.config",
    "messages.status.configIssue",
    "messages.status.configMore",
    "messages.status.rejectedReload",
    "messages.status.tamework",
    "messages.status.feature",
    "messages.status.tameworkPersistence",
    "messages.status.diagnosticsIssue",
    "messages.status.localPersistence",
    "messages.status.orphan",
    "messages.status.orphanMore",
    "messages.status.localPersistenceIssue",
    "messages.status.state.ready",
    "messages.status.state.invalid",
    "messages.status.state.disabled",
    "messages.status.state.unavailable",
    "messages.status.state.readWrite",
    "messages.status.state.readOnly",
    "messages.refund.description",
}
BANNED_PRE_RELEASE_TOKENS = (
    "Draconic_Essence_Igne",
    "Draconic_Essence_Cryo",
    "Draconic_Essence_Storm",
    "CryoDraconicEssence",
    "IgneDraconicEssence",
    "StormDraconicEssence",
    "WyverNature",
    "WyverStorm",
    "WyverThunder",
    "WyverToxic",
    "WyvernIgneo",
    "WyvernVoid",
    "AlbineTexture",
    "IgnesTexture",
    "LumenTexture",
    "MusgTexture",
)
PLACEHOLDER = re.compile(r"\{[^{}]+\}|%(?:\d+\$)?[a-zA-Z]")
WORLD_SPAWN_FIELDS_056 = {
    "Parent", "NPCs", "Despawn", "DayTimeRange", "MoonPhaseRange", "LightRanges",
    "ScaleDayTimeRange", "Tags", "Environments", "MoonPhaseWeightModifiers",
}
BEACON_SPAWN_FIELDS_056 = WORLD_SPAWN_FIELDS_056 | {
    "Model", "TargetDistanceFromPlayer", "MinDistanceFromPlayer", "YRange",
    "MaxSpawnedNPCs", "ConcurrentSpawnsRange", "SpawnAfterGameTimeRange",
    "SpawnAfterRealTimeRange", "InitialSpawnDelayRange", "NPCIdleDespawnTime",
    "BeaconVacantDespawnGameTime", "BeaconRadius", "SpawnRadius", "NPCSpawnState",
    "NPCSpawnSubState", "TargetSlot", "SpawnSuppression", "OverrideSpawnSuppressors",
    "MaxSpawnsScalingCurve", "ConcurrentSpawnsScalingCurve", "Debug",
}
ROLE_SPAWN_FIELDS_056 = {
    "Id", "Weight", "SpawnBlockSet", "SpawnFluidTag", "MovementModes",
    "EnableSafeSpawning", "Flock",
}
WORKSHOP_056_PATCH_TARGETS = {
    "Server/NPC/Spawn/Beacons/Zone1/Zone1_Cave_Tier2/Zone1_Cave_Forests_Aggro.json": (
        "Env_Zone1_Caves_Forests", {"LightRanges", "MinDistanceFromPlayer", "SpawnRadius", "SpawnAfterGameTimeRange"}),
    "Server/NPC/Spawn/Beacons/Zone2/Zone2_Cave_Tier2/Zone2_Cave_Volcanic_T2_Aggro.json": (
        "Env_Zone2_Caves_Volcanic_T2", {"LightRanges", "MinDistanceFromPlayer", "SpawnRadius", "SpawnAfterGameTimeRange"}),
    "Server/NPC/Spawn/Beacons/Zone2/Zone2_Cave_Tier3/Zone2_Cave_Volcanic_T3_Aggro.json": (
        "Env_Zone2_Caves_Volcanic_T3", {"LightRanges", "MinDistanceFromPlayer", "SpawnRadius", "SpawnAfterGameTimeRange"}),
    "Server/NPC/Spawn/Beacons/Zone3/Zone3_Cave_Tier3/Zone3_Cave_Glacial_Aggro.json": (
        "Env_Zone3_Caves_Glacial", {"LightRanges", "MinDistanceFromPlayer", "SpawnRadius", "SpawnAfterGameTimeRange"}),
}


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def hytale_asset_root(errors: list[str]) -> Path | None:
    configured = os.environ.get("HYTALE_ASSETS_PATH")
    candidates = [] if not configured else [Path(configured)]
    appdata = os.environ.get("APPDATA")
    if appdata:
        candidates.append(Path(appdata) / "Hytale/install/release/package/game/latest/Assets")
    for candidate in candidates:
        if (candidate / "Server").is_dir() and (candidate / "Common").is_dir():
            return candidate.resolve()
    fail(errors, "installed Hytale Assets directory unavailable; set HYTALE_ASSETS_PATH for base-reference validation")
    return None


def asset_stems(root: Path) -> set[str]:
    return {path.stem for path in root.rglob("*") if path.is_file()}


def load_json_assets(errors: list[str]) -> dict[Path, object]:
    parsed: dict[Path, object] = {}
    for root in ASSET_ROOTS:
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix not in JSON_SUFFIXES:
                continue
            try:
                parsed[path] = json.loads(path.read_text(encoding="utf-8-sig"))
            except (OSError, UnicodeError, json.JSONDecodeError) as exc:
                fail(errors, f"invalid JSON: {path.relative_to(ROOT)}: {exc}")
    return parsed


def validate_runtime_item_contracts(parsed: dict[Path, object], errors: list[str]) -> None:
    """Mirror runtime-only item checks that are not expressed by JSON syntax."""
    item_root = ROOT / "Server/Item/Items"
    allowed_icon_roots = ("Icons/ItemsGenerated/", "Icons/Items/")

    def validate_item(item: object, context: str) -> None:
        if not isinstance(item, dict):
            return
        icon = item.get("Icon")
        if isinstance(icon, str) and not icon.startswith(allowed_icon_roots):
            fail(errors, f"{context}.Icon must be within Icons/ItemsGenerated or Icons/Items: {icon}")
        interactions = item.get("Interactions")
        if isinstance(interactions, dict):
            for channel, root_interaction in interactions.items():
                if not isinstance(root_interaction, dict):
                    continue
                sequence = root_interaction.get("Interactions")
                if isinstance(sequence, list) and not sequence:
                    fail(errors, f"{context}.Interactions.{channel}.Interactions must not be empty")
        states = item.get("State")
        if isinstance(states, dict):
            for state_name, state in states.items():
                validate_item(state, f"{context}.State.{state_name}")

    for path in sorted(item_root.rglob("*.json")):
        validate_item(parsed.get(path), path.relative_to(ROOT).as_posix())


def validate_english_ids(errors: list[str]) -> None:
    for root in ASSET_ROOTS:
        for path in root.rglob("*"):
            if not path.is_file():
                continue
            relative = path.relative_to(ROOT).as_posix()
            for token in BANNED_PRE_RELEASE_TOKENS:
                if token in relative:
                    fail(errors, f"pre-release identifier remains in filename: {relative}")
            if path.suffix.lower() in {".png", ".zip"}:
                continue
            try:
                text = path.read_text(encoding="utf-8-sig")
            except (OSError, UnicodeError):
                continue
            for token in BANNED_PRE_RELEASE_TOKENS:
                if token in text:
                    fail(errors, f"pre-release identifier remains in content: {relative}: {token}")


def validate_no_pre_release_archives(errors: list[str]) -> None:
    artifact_root = ROOT / "artifacts"
    for path in sorted(artifact_root.glob("*.zip")):
        fail(errors, f"pre-release asset archive must not remain tracked: {path.relative_to(ROOT)}")


def validate_bonded_system_removed(errors: list[str]) -> None:
    obsolete_paths = (
        "Server/Item/Items/Ingredient/Soul_Bound_Wyvern.json",
        "Server/Item/Items/Tool/HyDragon_Command_Whistle.json",
        "Server/Tamework/Items/Commands/HyDragonDragonCommand.json",
        "Server/HyDragon/StoneMaintenance",
        "Common/Icons/ItemsGenerated/Draconic_Stone_Filled.png",
        "Common/Items/HyDragon/Draconic_Stone_Filled.blockymodel",
    )
    for relative in obsolete_paths:
        if (ROOT / relative).exists():
            fail(errors, f"obsolete bonded-system asset remains: {relative}")

    obsolete_tokens = (
        "Soul_Bound_Wyvern",
        "HyDragon_Command_Whistle",
        "HyDragonRepairBondedStone",
        '"Vessel"',
        "Draconic_Stone_State_",
    )
    for root in (ROOT / "Common", ROOT / "Server", ROOT / "src/main"):
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix.lower() in {".png", ".zip"}:
                continue
            try:
                text = path.read_text(encoding="utf-8-sig")
            except (OSError, UnicodeError):
                continue
            for token in obsolete_tokens:
                if token in text:
                    fail(errors, f"obsolete bonded-system token remains: {path.relative_to(ROOT)}: {token}")


def read_lang(path: Path, errors: list[str]) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(errors, f"invalid localization line: {path.relative_to(ROOT)}:{line_number}")
            continue
        key, value = line.split("=", 1)
        if key in values:
            fail(errors, f"duplicate localization key: {path.relative_to(ROOT)}:{line_number}: {key}")
        if not key or not value:
            fail(errors, f"empty localization key/value: {path.relative_to(ROOT)}:{line_number}")
        values[key] = value
    return values


def validate_locales(errors: list[str]) -> None:
    catalogs: dict[str, dict[str, str]] = {}
    for locale in LOCALES:
        path = ROOT / "Server" / "Languages" / locale / "server.lang"
        if not path.is_file():
            fail(errors, f"missing localization catalog: {path.relative_to(ROOT)}")
            continue
        catalogs[locale] = read_lang(path, errors)
    source = catalogs.get("en-US", {})
    for locale, catalog in catalogs.items():
        missing_status = sorted(REQUIRED_STATUS_MESSAGE_KEYS - set(catalog))
        if missing_status:
            fail(errors, f"{locale} missing status command keys: {', '.join(missing_status)}")
    for locale in LOCALES[1:]:
        translated = catalogs.get(locale, {})
        missing = sorted(set(source) - set(translated))
        extra = sorted(set(translated) - set(source))
        if missing:
            fail(errors, f"{locale} missing keys: {', '.join(missing)}")
        if extra:
            fail(errors, f"{locale} extra keys: {', '.join(extra)}")
        for key in sorted(set(source) & set(translated)):
            if PLACEHOLDER.findall(source[key]) != PLACEHOLDER.findall(translated[key]):
                fail(errors, f"placeholder mismatch: {locale}:{key}")

    status_command = ROOT / "src/main/java/com/alechilles/hydragon/diagnostics/HyDragonStatusCommand.java"
    if not status_command.is_file():
        fail(errors, "missing HyDragon status command source")
    elif "Message.raw(" in status_command.read_text(encoding="utf-8"):
        fail(errors, "HyDragon status command must not emit raw player-facing messages")

    command_root = ROOT / "src/main/java/com/alechilles/hydragon/diagnostics"
    description_pattern = re.compile(r'super\(\s*"[^"]+"\s*,\s*"([^"]+)"')
    for command_path in sorted(command_root.glob("*Command.java")):
        source_text = command_path.read_text(encoding="utf-8")
        for description in description_pattern.findall(source_text):
            if not description.startswith("server."):
                fail(errors, f"raw HyDragon command description: {command_path.relative_to(ROOT)}: {description}")
            elif description.removeprefix("server.") not in source:
                fail(errors, f"missing HyDragon command description localization: "
                     f"{command_path.relative_to(ROOT)}: {description}")


def validate_interaction_message_localization(parsed: dict[Path, object], errors: list[str]) -> None:
    english_path = ROOT / "Server/Languages/en-US/server.lang"
    english = read_lang(english_path, errors) if english_path.is_file() else {}

    def visit(value: object, path: Path) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                if key in {"Message", "PromptHint"} and isinstance(child, str):
                    if not child.startswith("server."):
                        fail(errors, f"raw player-facing interaction message: {path.relative_to(ROOT)}: {child}")
                    elif child.removeprefix("server.") not in english:
                        fail(errors, f"missing interaction message localization: {path.relative_to(ROOT)}: {child}")
                else:
                    visit(child, path)
        elif isinstance(value, list):
            for child in value:
                visit(child, path)

    interaction_root = ROOT / "Server/Tamework/Interactions"
    for path in sorted(interaction_root.glob("*.json")):
        visit(parsed.get(path), path)


def require_files(errors: list[str]) -> None:
    required = [
        "Server/Item/Items/Bench/Draconic_Altar.json",
        "Server/Item/Items/Ingredient/Draconic_Essence.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Fire.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Ice.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Nature.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Toxic.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Lightning.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Void.json",
        "Server/Item/Items/Ingredient/Revitalizing_Essence.json",
        "Server/Item/Items/Ingredient/Wyvern_Egg.json",
        "Server/Item/Items/Tool/HyDragon_Dragon_Horn.json",
        "Server/Tamework/Items/Commands/HyDragonDragonHorn.json",
        "Server/Tamework/BondedCompanions/Rosters/HyDragonFullDragons.json",
        "Server/Tamework/BondedCompanions/Rosters/HyDragonMiniwyvern.json",
        "Server/Tamework/Patches/HyDragonRoles/Tamed_NordicDrake_AvatarFlight.json",
        "Server/HyDragon/Encounters/NordicDrakeHighAltitude.json",
        "Server/Tamework/CapturePolicies/HyDragonHydra.json",
        "Server/Tamework/CapturePolicies/HyDragonNordicDrake.json",
        "Server/Tamework/CapturePolicies/HyDragonRockDrakeT1.json",
        "Server/Tamework/CapturePolicies/HyDragonRockDrakeT2.json",
        "Server/Tamework/CapturePolicies/HyDragonRockDrakeT3.json",
    ]
    required.extend(
        f"Server/Item/Items/Ingredient/Draconic_Stone_{tier}.json"
        for tier in ("Thorium", "Cobalt", "Adamantium", "Ancient")
    )
    required.extend(
        f"Server/HyDragon/MiniwyvernArchetypes/{name}.json"
        for name in ("Wild", "Nature", "Toxic", "Lightning", "Ice", "Fire", "Void")
    )
    required.extend(
        f"Server/HyDragon/DragonSpecies/{name}.json"
        for name in ("Hydra", "NordicDrake", "RockDrakeT1", "RockDrakeT2", "RockDrakeT3")
    )
    required.extend(
        f"Server/Tamework/Items/Spawners/HyDragonDraconicStone{suffix}.json"
        for suffix in ("", "Thorium", "Cobalt", "Adamantium", "Ancient")
    )
    for relative in required:
        if not (ROOT / relative).is_file():
            fail(errors, f"missing required asset: {relative}")


def validate_capture_configs(parsed: dict[Path, object], errors: list[str]) -> None:
    spawner_root = ROOT / "Server" / "Tamework" / "Items" / "Spawners"
    banned_roles = {"Wyvern_Mini", "Tamed_Wyvern_Mini"}
    for path in spawner_root.glob("HyDragonDraconicStone*.json"):
        data = parsed.get(path)
        if not isinstance(data, dict):
            continue
        allowed = data.get("AllowedRoles")
        if isinstance(allowed, dict):
            roles = set(allowed.get("Allowlist", []))
            overlap = sorted(roles & banned_roles)
            if overlap:
                fail(errors, f"Miniwyvern capture role in {path.relative_to(ROOT)}: {', '.join(overlap)}")
        capture = data.get("Capture")
        if isinstance(capture, dict):
            overrides = set(capture.get("TamedRoleOverrides", {}))
            overlap = sorted(overrides & banned_roles)
            if overlap:
                fail(errors, f"Miniwyvern tamed override in {path.relative_to(ROOT)}: {', '.join(overlap)}")


def validate_miniwyvern_ability_contract(parsed: dict[Path, object], errors: list[str]) -> None:
    archetype_root = ROOT / "Server/HyDragon/MiniwyvernArchetypes"
    effect_root = ROOT / "Server/Entity/Effects/Status"
    hostile_policies = {"OWNER_HOSTILE_ONLY", "OWNER_HOSTILE_AREA"}
    for path in sorted(archetype_root.glob("*.json")):
        data = parsed.get(path)
        if not isinstance(data, dict):
            continue
        presentation_ids = data.get("ParticleAndSoundIds", [])
        if not isinstance(presentation_ids, list) or any(
            not isinstance(asset_id, str) or not asset_id.strip() for asset_id in presentation_ids
        ):
            fail(errors, f"invalid Miniwyvern presentation IDs in {path.relative_to(ROOT)}")

        modifiers = data.get("PassiveModifiers", {})
        modifier_effects = data.get("PassiveModifierEffects", {})
        if not isinstance(modifiers, dict) or not isinstance(modifier_effects, dict):
            fail(errors, f"invalid Miniwyvern passive modifier maps in {path.relative_to(ROOT)}")
            continue
        for semantic, effect_id in modifier_effects.items():
            if semantic != "MovementSpeedMultiplier":
                fail(errors, f"unsupported Miniwyvern modifier effect semantic {semantic} in {path.relative_to(ROOT)}")
                continue
            effect_path = effect_root / f"{effect_id}.json"
            effect = parsed.get(effect_path)
            application = effect.get("ApplicationEffects") if isinstance(effect, dict) else None
            actual = application.get("HorizontalSpeedMultiplier") if isinstance(application, dict) else None
            requested = modifiers.get(semantic)
            maximum = modifiers.get("MaximumMovementSpeedMultiplier", requested)
            if not isinstance(actual, (int, float)) or not isinstance(requested, (int, float)) \
                    or abs(float(actual) - float(requested)) > 0.00001:
                fail(errors, f"{semantic} effect {effect_id} does not match its configured value in {path.relative_to(ROOT)}")
            if isinstance(maximum, (int, float)) and isinstance(requested, (int, float)) \
                    and float(requested) > float(maximum):
                fail(errors, f"{semantic} exceeds its configured maximum in {path.relative_to(ROOT)}")

        for ability in data.get("ActiveAbilities", []):
            if not isinstance(ability, dict):
                continue
            trigger = ability.get("Trigger")
            policy = ability.get("TargetPolicy")
            if trigger == "COMBAT_INTERVAL" and policy not in hostile_policies:
                fail(errors, f"COMBAT_INTERVAL has non-hostile target policy in {path.relative_to(ROOT)}")
            if trigger == "OWNER_HEALTH_BELOW_PERCENT" and policy != "OWNER_ONLY":
                fail(errors, f"OWNER_HEALTH_BELOW_PERCENT must target OWNER_ONLY in {path.relative_to(ROOT)}")
            if trigger not in {"COMBAT_INTERVAL", "OWNER_HEALTH_BELOW_PERCENT"}:
                fail(errors, f"unsupported Miniwyvern trigger {trigger!r} in {path.relative_to(ROOT)}")
            maximum_stacks = ability.get("MaximumStacks")
            if maximum_stacks is not None and (ability.get("EffectId") is None or maximum_stacks != 1):
                fail(errors, f"Hytale 0.5.6 supports only one capped effect stack in {path.relative_to(ROOT)}")

            if data.get("Id") != "void":
                continue
            effect_id = ability.get("EffectId")
            effect = parsed.get(effect_root / f"{effect_id}.json")
            resistance = effect.get("DamageResistance") if isinstance(effect, dict) else None
            minimum = ability.get("MinimumDefenseMultiplier")
            maximum = ability.get("MaximumReduction")
            requested = abs(float(ability.get("Magnitude", 0.0)))
            amounts: list[object] = []
            if isinstance(resistance, dict):
                for entries in resistance.values():
                    if isinstance(entries, list):
                        amounts.extend(
                            entry.get("Amount") for entry in entries if isinstance(entry, dict)
                        )
            if not amounts or not isinstance(minimum, (int, float)) or not isinstance(maximum, (int, float)):
                fail(errors, f"Void defense bounds are not backed by an effect asset in {path.relative_to(ROOT)}")
                continue
            reductions = [-float(amount) for amount in amounts if isinstance(amount, (int, float)) and amount < 0]
            if len(reductions) != len(amounts) or not any(abs(value - requested) <= 0.00001 for value in reductions):
                fail(errors, f"Void effect reduction does not match Magnitude in {path.relative_to(ROOT)}")
            if any(value > float(maximum) + 0.000001 or 1.0 - value < float(minimum) - 0.000001
                   for value in reductions):
                fail(errors, f"Void effect crosses its configured defense bounds in {path.relative_to(ROOT)}")


def validate_stone_tiers(parsed: dict[Path, object], errors: list[str]) -> None:
    spawner_root = ROOT / "Server" / "Tamework" / "Items" / "Spawners"
    tiers = (
        ("HyDragonDraconicStone.json", 1),
        ("HyDragonDraconicStoneThorium.json", 2),
        ("HyDragonDraconicStoneCobalt.json", 3),
        ("HyDragonDraconicStoneAdamantium.json", 4),
        ("HyDragonDraconicStoneAncient.json", 5),
    )
    observed: list[int] = []
    for filename, expected_power in tiers:
        path = spawner_root / filename
        data = parsed.get(path)
        if not isinstance(data, dict):
            continue
        capture = data.get("Capture")
        power = capture.get("Power") if isinstance(capture, dict) else None
        if power != expected_power:
            fail(errors, f"invalid capture power in {path.relative_to(ROOT)}: expected {expected_power}, got {power}")
        if isinstance(power, int):
            observed.append(power)
    if observed and observed != sorted(set(observed)):
        fail(errors, f"stone capture powers are not strictly increasing: {observed}")

    ancient_path = spawner_root / "HyDragonDraconicStoneAncient.json"
    ancient = parsed.get(ancient_path)
    ancient_capture = ancient.get("Capture") if isinstance(ancient, dict) else None
    if not isinstance(ancient_capture, dict) or ancient_capture.get("MaximumChance") != 1.0:
        fail(errors, "Ancient stone must cap eligible capture probability at 1.0")

    base = parsed.get(spawner_root / "HyDragonDraconicStone.json")
    capture = base.get("Capture") if isinstance(base, dict) else None
    required = {
        "SourceConsumption": "ResolvedAttempt",
        "SuccessDisposition": "StoreBondedCompanion",
        "BondedRosterId": "hydragon:dragon_horn",
        "RequiredCommandConfigId": "HyDragonDragonHorn",
        "RequireCommandAccessItem": True,
    }
    if not isinstance(capture, dict):
        fail(errors, "base Draconic Stone capture config is missing")
    else:
        for field, expected in required.items():
            if capture.get(field) != expected:
                fail(errors, f"base Draconic Stone Capture.{field} must be {expected!r}")
        if capture.get("SuccessDisposition") == "TameAndCommandLink":
            fail(errors, "base Draconic Stone Capture must not use retired TameAndCommandLink")
        if "CommandFamilyId" in capture:
            fail(errors, "base Draconic Stone Capture must not declare retired CommandFamilyId")
    for filename, _ in tiers:
        path = spawner_root / filename
        data = parsed.get(path)
        if isinstance(data, dict) and ("Vessel" in data or "FilledItemId" in data):
            fail(errors, f"obsolete filled/vessel capture config remains: {path.relative_to(ROOT)}")


def validate_no_miniwyvern_spawns(parsed: dict[Path, object], errors: list[str]) -> None:
    spawn_roots = (
        ROOT / "Server" / "NPC" / "Spawn",
        ROOT / "Server" / "Tamework" / "Patches",
    )
    banned = {"Wyvern_Mini", "Tamed_Wyvern_Mini"}

    def visit(value: object) -> bool:
        if isinstance(value, str):
            return value in banned
        if isinstance(value, list):
            return any(visit(item) for item in value)
        if isinstance(value, dict):
            return any(visit(item) for item in value.values())
        return False

    for root in spawn_roots:
        if not root.is_dir():
            continue
        for path in root.rglob("*.json"):
            if visit(parsed.get(path)):
                fail(errors, f"production Miniwyvern spawn path remains: {path.relative_to(ROOT)}")

    wild_role_path = ROOT / "Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Wyvern_Mini.json"
    wild_role = parsed.get(wild_role_path)
    modify = wild_role.get("Modify") if isinstance(wild_role, dict) else None
    if isinstance(modify, dict):
        if modify.get("IsTameable") is not False:
            fail(errors, "Soul Bond-only Miniwyvern wild role must set IsTameable to false")
        if modify.get("TameRoleChange") not in (None, ""):
            fail(errors, "Soul Bond-only Miniwyvern wild role must not expose TameRoleChange")


def validate_miniwyvern_role_wiring(parsed: dict[Path, object], errors: list[str]) -> None:
    """Validate the Soul Bond companion's complete role/config reference graph."""
    wild_path = ROOT / "Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Wyvern_Mini.json"
    template_path = ROOT / "Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json"
    follow_path = ROOT / "Server/NPC/Roles/Creature/HyDragon/Components/Component_Tamework_Instruction_Follow_Flying.json"
    companion_path = ROOT / "Server/Tamework/Companion/HyDragonMiniwyvern.json"
    root_bite_path = ROOT / "Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Wyvern_Mini_Bite.json"
    bite_path = ROOT / "Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Bite.json"
    bite_damage_path = ROOT / "Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Bite_Damage.json"

    wild = parsed.get(wild_path)
    template = parsed.get(template_path)
    follow = parsed.get(follow_path)
    companion = parsed.get(companion_path)
    root_bite = parsed.get(root_bite_path)
    bite = parsed.get(bite_path)
    bite_damage = parsed.get(bite_damage_path)

    if not isinstance(template, dict) or template.get("Type") != "Abstract":
        fail(errors, "Miniwyvern tamed template is missing or is not Abstract")
        return
    if not isinstance(wild, dict):
        fail(errors, "Miniwyvern wild role is missing")
        return

    wild_modify = wild.get("Modify")
    if isinstance(wild_modify, dict) and wild_modify.get("InteractionConfigId") not in (None, ""):
        fail(errors, "Soul Bond-only wild Miniwyvern must not expose the tamed interaction config")

    forms = ("Wild", "Nature", "Toxic", "Fire", "Void", "Lightning", "Ice")
    essences = {"Wild": "Draconic_Essence", "Nature": "Draconic_Essence_Nature", "Toxic": "Draconic_Essence_Toxic", "Fire": "Draconic_Essence_Fire", "Void": "Draconic_Essence_Void", "Lightning": "Draconic_Essence_Lightning", "Ice": "Draconic_Essence_Ice"}
    appearances = {"Wild": "Wyvern_Mini", "Nature": "Wyvern_Mini_Nature", "Toxic": "Wyvern_Mini_Toxic", "Fire": "Wyvern_Mini_Fire", "Void": "Wyvern_Mini_Void", "Lightning": "Wyvern_Mini_Lightning", "Ice": "Wyvern_Mini_Ice"}
    for form in forms:
        role_id = f"Tamed_Wyvern_Mini_{form}"
        role = parsed.get(ROOT / f"Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/{role_id}.json")
        modify = role.get("Modify") if isinstance(role, dict) else None
        if not isinstance(role, dict) or role.get("Reference") != "Template_Wyvern_Mini_Flying_Tamed" or not isinstance(modify, dict):
            fail(errors, f"{role_id} must be a Template_Wyvern_Mini_Flying_Tamed variant")
            continue
        for capability in ("CanFollow", "CanHold", "CanDefend", "CanAttackTarget"):
            if modify.get(capability) is not True:
                fail(errors, f"{role_id} must explicitly enable {capability}")
        if modify.get("IsMountable") is not False or modify.get("Attack") != "Root_NPC_Wyvern_Mini_Bite" or modify.get("Appearance") != appearances[form]:
            fail(errors, f"{role_id} has invalid companion or form wiring")
        config_id = f"HyDragonIntWyvernMini_{form}"
        interaction = parsed.get(ROOT / f"Server/Tamework/Interactions/{config_id}.json")
        if modify.get("InteractionConfigId") != config_id or not isinstance(interaction, dict) or interaction.get("RoleIds") != [role_id]:
            fail(errors, f"{role_id} must reference a role-specific interaction config")
            continue
        entries = interaction.get("Interactions")
        transforms = [entry for entry in entries if isinstance(entry, dict) and entry.get("Type") == "Custom"] if isinstance(entries, list) else []
        destinations = set()
        for entry in transforms:
            requirements = entry.get("Requires", {}).get("All", {})
            effects = entry.get("Effects", {})
            set_role, remove = effects.get("SetRole", {}), effects.get("RemoveItemsHand", {})
            destination = set_role.get("Role")
            destination_form = destination.removeprefix("Tamed_Wyvern_Mini_") if isinstance(destination, str) else ""
            item = essences.get(destination_form)
            held = requirements.get("ItemsInHand")
            if (requirements.get("IsTamed") is not True or requirements.get("PlayerIsOwner") is not True
                    or not isinstance(held, list) or len(held) != 1 or held[0].get("Items") != [item] or held[0].get("Quantity") != 8
                    or set_role.get("ChangeAppearance") is not True or remove.get("Items") != [item] or remove.get("Quantity") != 8):
                fail(errors, f"{config_id} has an invalid transform cost or ownership gate")
            destinations.add(destination)
        expected = {f"Tamed_Wyvern_Mini_{destination}" for destination in forms if destination != form}
        if len(transforms) != 6 or destinations != expected:
            fail(errors, f"{config_id} must offer exactly six non-self transforms")
        types = {entry.get("Type") for entry in entries if isinstance(entry, dict)} if isinstance(entries, list) else set()
        if {"Feed", "ModeCycle"} - types or types & {"Mount", "Tame"}:
            fail(errors, f"{config_id} must preserve Feed and ModeCycle without Mount or Tame")

    if template.get("StartState") != "Follow":
        fail(errors, "Soulbound Miniwyvern must start in Follow when no persisted state is restored")
    parameters = template.get("Parameters")
    if not isinstance(parameters, dict):
        fail(errors, "Miniwyvern tamed template has no Parameters block")
    else:
        for capability in ("CanFollow", "CanHold", "CanDefend", "CanAttackTarget"):
            value = parameters.get(capability)
            if not isinstance(value, dict) or value.get("Value") is not True:
                fail(errors, f"Miniwyvern tamed template must default {capability} to true")
        mountable = parameters.get("IsMountable")
        if not isinstance(mountable, dict) or mountable.get("Value") is not False:
            fail(errors, "Miniwyvern tamed template must default IsMountable to false")

    serialized_template = json.dumps(template, sort_keys=True)
    required_states = {"Follow", "Hold", "Idle", "Defend"}
    state_values: set[str] = set()

    def collect_state_sensors(value: object, inside_sensor: bool = False) -> None:
        if isinstance(value, dict):
            if inside_sensor and value.get("Type") == "State" and isinstance(value.get("State"), str):
                state_values.add(value["State"])
            for key, child in value.items():
                collect_state_sensors(child, inside_sensor or key == "Sensor")
        elif isinstance(value, list):
            for child in value:
                collect_state_sensors(child, inside_sensor)

    collect_state_sensors(template)
    missing_states = sorted(required_states - state_values)
    if missing_states:
        fail(errors, f"Miniwyvern tamed template has no wiring for states: {', '.join(missing_states)}")
    for reference in (
        "Component_Tamework_Instruction_Follow_Flying",
        "Component_Tamework_Instruction_Defend",
    ):
        if reference not in serialized_template:
            fail(errors, f"Miniwyvern tamed template is missing component reference: {reference}")
    for safety_token in ("TameworkIsOwner", "TameworkAttitudeFromTargetSlot", "MasterTarget", "LockedTarget", "Friendly"):
        if safety_token not in serialized_template:
            fail(errors, f"Miniwyvern target-safety wiring is missing: {safety_token}")

    if not isinstance(follow, dict) or follow.get("Type") != "Component" or follow.get("Class") != "Instruction":
        fail(errors, "Component_Tamework_Instruction_Follow_Flying does not resolve to an instruction component")
    elif follow.get("Interface") != "Tamework.Instruction.Follow":
        fail(errors, "flying follow component must implement Tamework.Instruction.Follow")

    root_interactions = root_bite.get("Interactions", []) if isinstance(root_bite, dict) else []
    if "Wyvern_Mini_Bite" not in root_interactions:
        fail(errors, "Root_NPC_Wyvern_Mini_Bite does not resolve to Wyvern_Mini_Bite")
    if not isinstance(bite, dict) or "Wyvern_Mini_Bite_Damage" not in json.dumps(bite):
        fail(errors, "Wyvern_Mini_Bite does not resolve to its damage interaction")
    if not isinstance(bite_damage, dict) or bite_damage.get("Parent") != "DamageEntityParent":
        fail(errors, "Wyvern_Mini_Bite_Damage must inherit DamageEntityParent")

    expected_roles = [f"Tamed_Wyvern_Mini_{form}" for form in forms]
    if not isinstance(companion, dict) or companion.get("RoleIds") != expected_roles:
        fail(errors, "HyDragonMiniwyvern companion lifecycle config must target exactly the seven Miniwyvern roles")
    else:
        if companion.get("Parent") != "TwCompanionDefault":
            fail(errors, "HyDragonMiniwyvern must inherit Tamework's durable companion lifecycle defaults")
        command = companion.get("Command")
        if any(field in command for field in ("Travel", "Summon", "Revive")):
            fail(errors, "HyDragonMiniwyvern must leave bonded travel/summon/revive lifecycle to its roster policy")


def validate_spawn_patch_role_identity(parsed: dict[Path, object], errors: list[str]) -> None:
    species_root = ROOT / "Server/HyDragon/DragonSpecies"
    patch_root = ROOT / "Server/Tamework/Patches/HyDragon"
    for species_path in sorted(species_root.glob("*.json")):
        species = parsed.get(species_path)
        if not isinstance(species, dict):
            continue
        wild_roles = set(species.get("WildRoleIds", []))
        spawn = species.get("Spawn")
        ordinary_ids = spawn.get("OrdinarySpawnAssetIds", []) if isinstance(spawn, dict) else []
        for asset_id in ordinary_ids:
            patch_path = patch_root / f"{asset_id}.json"
            if not patch_path.is_file():
                continue
            patch = parsed.get(patch_path)
            operations = patch.get("Operations", []) if isinstance(patch, dict) else []
            inserted_roles = {
                operation.get("Value", {}).get("Id")
                for operation in operations
                if isinstance(operation, dict) and isinstance(operation.get("Value"), dict)
            }
            if not inserted_roles.intersection(wild_roles):
                fail(errors, f"spawn patch {patch_path.relative_to(ROOT)} inserts {sorted(inserted_roles)} but species declares {sorted(wild_roles)}")


def validate_range(value: object, size: int, minimum: float, maximum: float) -> bool:
    return (
        isinstance(value, list)
        and len(value) == size
        and all(isinstance(item, (int, float)) and minimum <= item <= maximum for item in value)
        and value[0] <= value[-1]
    )


def validate_role_spawn(entry: object, context: str, known_assets: set[str], errors: list[str]) -> None:
    if not isinstance(entry, dict):
        fail(errors, f"{context} contains a non-object NPC spawn entry")
        return
    unknown = sorted(set(entry) - ROLE_SPAWN_FIELDS_056)
    if unknown:
        fail(errors, f"{context} has fields outside Hytale 0.5.6 RoleSpawnParameters: {unknown}")
    role_id = entry.get("Id")
    if not isinstance(role_id, str) or role_id not in known_assets:
        fail(errors, f"{context} references unresolved NPC role: {role_id}")
    weight = entry.get("Weight")
    if not isinstance(weight, (int, float)) or weight <= 0:
        fail(errors, f"{context} requires a positive NPC weight")
    block_set = entry.get("SpawnBlockSet")
    if block_set is not None and (not isinstance(block_set, str) or block_set not in known_assets):
        fail(errors, f"{context} references unresolved SpawnBlockSet: {block_set}")


def validate_spawn_shape(
    data: object,
    asset_type: str,
    context: str,
    known_assets: set[str],
    errors: list[str],
) -> None:
    if not isinstance(data, dict):
        fail(errors, f"{context} is not a JSON object")
        return
    allowed = WORLD_SPAWN_FIELDS_056 if asset_type == "WorldNPCSpawn" else BEACON_SPAWN_FIELDS_056
    unknown = sorted(set(data) - allowed)
    if unknown:
        fail(errors, f"{context} has fields outside Hytale 0.5.6 {asset_type}: {unknown}")
    environments = data.get("Environments")
    if not isinstance(environments, list) or not environments or any(
        not isinstance(value, str) or value not in known_assets for value in environments
    ):
        fail(errors, f"{context} has an empty or unresolved Environments list: {environments}")
    npcs = data.get("NPCs")
    if not isinstance(npcs, list) or not npcs:
        fail(errors, f"{context} must declare at least one NPC")
    else:
        for index, entry in enumerate(npcs):
            validate_role_spawn(entry, f"{context}.NPCs[{index}]", known_assets, errors)
    for field, size, minimum, maximum in (
        ("DayTimeRange", 2, 0, 24),
        ("MoonPhaseRange", 2, 0, 4),
    ):
        if field in data and data[field] is not None and not validate_range(data[field], size, minimum, maximum):
            fail(errors, f"{context}.{field} violates the Hytale 0.5.6 range contract")
    lights = data.get("LightRanges")
    if lights is not None:
        allowed_lights = {"Light", "SkyLight", "Sunlight", "RedLight", "GreenLight", "BlueLight"}
        if not isinstance(lights, dict) or set(lights) - allowed_lights:
            fail(errors, f"{context}.LightRanges has unsupported Hytale 0.5.6 keys")
        elif any(not validate_range(value, 2, 0, 100) for value in lights.values()):
            fail(errors, f"{context}.LightRanges contains an invalid range")
    moon_weights = data.get("MoonPhaseWeightModifiers")
    if moon_weights is not None and (
        not isinstance(moon_weights, list)
        or len(moon_weights) != 5
        or any(not isinstance(value, (int, float)) or value < 0 for value in moon_weights)
    ):
        fail(errors, f"{context}.MoonPhaseWeightModifiers must contain five non-negative weights")
    if "YRange" in data and data["YRange"] is not None and not validate_range(data["YRange"], 2, -4096, 4096):
        fail(errors, f"{context}.YRange must contain two ordered integer offsets")


def validate_static_spawn_contracts(
    parsed: dict[Path, object],
    base_root: Path | None,
    known_assets: set[str],
    errors: list[str],
) -> None:
    """Validate authored spawns plus base patches against Workshop's 0.5.6 contracts."""
    world_root = ROOT / "Server/NPC/Spawn/World"
    local_spawn_ids: set[str] = set()
    for path in sorted(world_root.rglob("*.json")):
        local_spawn_ids.add(path.stem)
        validate_spawn_shape(parsed.get(path), "WorldNPCSpawn", path.relative_to(ROOT).as_posix(), known_assets, errors)

    patch_root = ROOT / "Server/Tamework/Patches/HyDragon"
    patch_ids: set[str] = set()
    for path in sorted(patch_root.glob("*.json")):
        data = parsed.get(path)
        context = path.relative_to(ROOT).as_posix()
        if not isinstance(data, dict):
            continue
        if set(data) - {"Id", "Target", "Priority", "Enabled", "Operations"}:
            fail(errors, f"{context} has unsupported patch fields")
        patch_id = data.get("Id")
        if not isinstance(patch_id, str) or not patch_id:
            fail(errors, f"{context} has no stable Id")
        else:
            patch_ids.add(path.stem)
            if patch_id != f"HyDragon_{path.stem}":
                fail(errors, f"{context} Id must be HyDragon_{path.stem}")
        target = data.get("Target")
        if target not in WORKSHOP_056_PATCH_TARGETS:
            fail(errors, f"{context} target is not in the verified Workshop 0.5.6 manifest: {target}")
            continue
        if base_root is None:
            continue
        target_path = base_root / str(target)
        if not target_path.is_file():
            fail(errors, f"{context} base target does not exist in the installed Hytale assets: {target}")
            continue
        try:
            base = json.loads(target_path.read_text(encoding="utf-8-sig"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            fail(errors, f"cannot read base spawn target {target}: {exc}")
            continue
        expected_environment, required_fields = WORKSHOP_056_PATCH_TARGETS[str(target)]
        if expected_environment not in base.get("Environments", []):
            fail(errors, f"{context} base target environment drifted from Workshop 0.5.6 evidence")
        missing_fields = sorted(required_fields - set(base))
        if missing_fields:
            fail(errors, f"{context} base target lost required static-spawn fields: {missing_fields}")
        merged = dict(base)
        merged["NPCs"] = list(base.get("NPCs", []))
        operations = data.get("Operations")
        if not isinstance(operations, list) or not operations:
            fail(errors, f"{context} must contain at least one patch operation")
            continue
        operation_ids: set[str] = set()
        for index, operation in enumerate(operations):
            operation_context = f"{context}.Operations[{index}]"
            if not isinstance(operation, dict) or set(operation) - {"Id", "Op", "Path", "Position", "Existing", "Value"}:
                fail(errors, f"{operation_context} has an invalid patch operation shape")
                continue
            operation_id = operation.get("Id")
            if not isinstance(operation_id, str) or not operation_id or operation_id in operation_ids:
                fail(errors, f"{operation_context} has a blank or duplicate operation Id")
            else:
                operation_ids.add(operation_id)
            if (operation.get("Op"), operation.get("Path")) == ("Add", "/YRange"):
                if not validate_range(operation.get("Value"), 2, -4096, 4096):
                    fail(errors, f"{operation_context}.Value violates the Hytale 0.5.6 YRange contract")
                else:
                    merged["YRange"] = operation.get("Value")
                continue
            if (operation.get("Op"), operation.get("Path"), operation.get("Position")) != ("Insert", "/NPCs", "End"):
                fail(errors, f"{operation_context} must append to the schema-defined NPCs array")
                continue
            value = operation.get("Value")
            validate_role_spawn(value, f"{operation_context}.Value", known_assets, errors)
            if isinstance(value, dict):
                merged["NPCs"].append(value)
        validate_spawn_shape(merged, "BeaconNPCSpawn", f"{context} effective target", known_assets, errors)

    species_root = ROOT / "Server/HyDragon/DragonSpecies"
    available_routes = local_spawn_ids | patch_ids
    for path in sorted(species_root.glob("*.json")):
        species = parsed.get(path)
        spawn = species.get("Spawn") if isinstance(species, dict) else None
        ordinary = spawn.get("OrdinarySpawnAssetIds", []) if isinstance(spawn, dict) else []
        for asset_id in ordinary:
            if asset_id not in available_routes:
                fail(errors, f"{path.relative_to(ROOT)} references unresolved ordinary spawn route: {asset_id}")


def validate_domain_references(
    parsed: dict[Path, object], known_assets: set[str], projectile_ids: set[str], errors: list[str]
) -> None:
    """Resolve release-critical species, encounter, and archetype references to local/base assets."""
    species_root = ROOT / "Server/HyDragon/DragonSpecies"
    species_ids: set[str] = set()
    for path in sorted(species_root.glob("*.json")):
        species = parsed.get(path)
        if not isinstance(species, dict):
            continue
        species_ids.add(species.get("Id"))
        fields = {
            "WildRoleIds": species.get("WildRoleIds", []),
            "TamedRoleIdByWildRole": list(species.get("TamedRoleIdByWildRole", {}).values()),
            "StatsAndBehaviorAssetIds": species.get("StatsAndBehaviorAssetIds", []),
            "DropListId": [species.get("DropListId")],
        }
        presentation = species.get("Presentation", {})
        fields["Presentation.ModelIds"] = presentation.get("ModelIds", []) if isinstance(presentation, dict) else []
        mount = species.get("Mount", {})
        avatar = mount.get("AvatarFlightConfigId") if isinstance(mount, dict) else None
        if avatar:
            fields["Mount.AvatarFlightConfigId"] = [avatar]
        for field, references in fields.items():
            for reference in references:
                if not isinstance(reference, str) or reference not in known_assets:
                    fail(errors, f"{path.relative_to(ROOT)} unresolved {field} reference: {reference}")

    for path in sorted((ROOT / "Server/HyDragon/Encounters").glob("*.json")):
        encounter = parsed.get(path)
        if not isinstance(encounter, dict):
            continue
        target_species = encounter.get("TargetSpeciesId")
        if target_species not in species_ids:
            fail(errors, f"{path.relative_to(ROOT)} unresolved TargetSpeciesId: {target_species}")
        grounding = encounter.get("Grounding", {})
        grounded_effect = grounding.get("GroundedEffectId") if isinstance(grounding, dict) else None
        if grounded_effect not in known_assets:
            fail(errors, f"{path.relative_to(ROOT)} unresolved GroundedEffectId: {grounded_effect}")
        for source in grounding.get("BuildupSourceIds", []) if isinstance(grounding, dict) else []:
            for segment in source.split("+"):
                _, separator, reference = segment.partition(":")
                if not separator or reference not in known_assets:
                    fail(errors, f"{path.relative_to(ROOT)} unresolved grounding source asset: {source}")

    for path in sorted((ROOT / "Server/HyDragon/MiniwyvernArchetypes").glob("*.json")):
        archetype = parsed.get(path)
        if not isinstance(archetype, dict):
            continue
        references: list[tuple[str, object]] = []
        if archetype.get("RoleId") is not None:
            references.append(("RoleId", archetype["RoleId"]))
        owner_attack_aura = archetype.get("OwnerAttackAura")
        if isinstance(owner_attack_aura, dict) and owner_attack_aura.get("EffectId") is not None:
            references.append(("OwnerAttackAura.EffectId", owner_attack_aura["EffectId"]))
        references.extend(("ParticleAndSoundIds", value) for value in archetype.get("ParticleAndSoundIds", []))
        references.extend(("PassiveEffects", value) for value in archetype.get("PassiveEffects", []))
        passive_modifier_effects = archetype.get("PassiveModifierEffects", {})
        if isinstance(passive_modifier_effects, dict):
            references.extend(("PassiveModifierEffects", value) for value in passive_modifier_effects.values())
        for ability in archetype.get("ActiveAbilities", []):
            if not isinstance(ability, dict):
                continue
            for field in ("EffectId", "ProjectileId", "ControlEffectId"):
                if ability.get(field) is not None:
                    references.append((f"ActiveAbilities.{field}", ability[field]))
            projectile_id = ability.get("ProjectileId")
            if projectile_id is not None and (not isinstance(projectile_id, str) or projectile_id not in projectile_ids):
                fail(errors, f"{path.relative_to(ROOT)} ActiveAbilities.ProjectileId is not a typed projectile asset: {projectile_id}")
        for field, reference in references:
            if not isinstance(reference, str) or reference not in known_assets:
                fail(errors, f"{path.relative_to(ROOT)} unresolved {field} reference: {reference}")


def validate_release_content_contracts(parsed: dict[Path, object], errors: list[str]) -> None:
    """Gate first-release capture, flight, spawn, loot, and presentation contracts."""
    stone_path = ROOT / "Server/Item/Items/Ingredient/Draconic_Stone.json"
    stone = parsed.get(stone_path)
    if not isinstance(stone, dict) or "State" in stone or "MaxDurability" in stone:
        fail(errors, "Draconic Stones must be stateless consumable capture attempts")

    expected_drop_ids: list[str] = []
    for tier in (1, 2, 3):
        expected = f"Drop_RockDrake_T{tier}"
        expected_drop_ids.append(expected)
        species_path = ROOT / f"Server/HyDragon/DragonSpecies/RockDrakeT{tier}.json"
        role_path = ROOT / f"Server/NPC/Roles/Creature/HyDragon/RockDrake/RockDrakeT{tier}.json"
        drop_path = ROOT / f"Server/Drops/HyDragon/{expected}.json"
        species = parsed.get(species_path)
        role = parsed.get(role_path)
        drop = parsed.get(drop_path)
        if not isinstance(species, dict) or species.get("DropListId") != expected:
            fail(errors, f"Rock Drake T{tier} species must select its independently tunable {expected}")
        modify = role.get("Modify") if isinstance(role, dict) else None
        if not isinstance(modify, dict) or modify.get("DropList") != expected:
            fail(errors, f"Rock Drake T{tier} role must select its independently tunable {expected}")
        if not isinstance(drop, dict):
            fail(errors, f"missing independently tunable Rock Drake drop list: {drop_path.relative_to(ROOT)}")
            continue
        serialized = json.dumps(drop, sort_keys=True)
        for required_item in ("Draconic_Scale", "Draconic_Essence"):
            if required_item not in serialized:
                fail(errors, f"{drop_path.relative_to(ROOT)} does not source {required_item}")
    if len(set(expected_drop_ids)) != 3:
        fail(errors, "Rock Drake tier drop-list IDs must be distinct")

    hydra_spawn_path = ROOT / "Server/NPC/Spawn/World/Zone3/Spawns_Zone3_Glacial_HyDragon_Predator.json"
    hydra_spawn = parsed.get(hydra_spawn_path)
    if not isinstance(hydra_spawn, dict) or hydra_spawn.get("MoonPhaseRange") is None \
            or hydra_spawn.get("MoonPhaseWeightModifiers") is None:
        fail(errors, f"{hydra_spawn_path.relative_to(ROOT)} must author moon range and weight tuning")
    altitude_patch_path = ROOT / "Server/Tamework/Patches/HyDragon/RockDrakeT3_Zone3_Cave_Glacial_Aggro.json"
    altitude_patch = parsed.get(altitude_patch_path)
    operations = altitude_patch.get("Operations", []) if isinstance(altitude_patch, dict) else []
    if not any(
        isinstance(operation, dict)
        and operation.get("Op") == "Add"
        and operation.get("Path") == "/YRange"
        and validate_range(operation.get("Value"), 2, -4096, 4096)
        for operation in operations
    ):
        fail(errors, f"{altitude_patch_path.relative_to(ROOT)} must author a valid BeaconNPCSpawn YRange")

    flight_patch_path = ROOT / "Server/Tamework/Patches/HyDragonRoles/Tamed_NordicDrake_AvatarFlight.json"
    flight_patch = parsed.get(flight_patch_path)
    flight_operations = flight_patch.get("Operations", []) if isinstance(flight_patch, dict) else []
    expected_flight_values = {
        "MountMode": "TameworkAvatarFlight",
        "AvatarFlightConfig": "HyDragonNordicDrake",
    }
    if not any(
        isinstance(operation, dict)
        and operation.get("Op") == "Merge"
        and operation.get("Path") == "/Modify"
        and operation.get("Value") == expected_flight_values
        for operation in flight_operations
    ):
        fail(errors, "Nordic Drake must receive Tamework avatar-flight role wiring through its clean patch asset")
    avatar_config = parsed.get(ROOT / "Server/Tamework/AvatarFlight/HyDragonNordicDrake.json")
    model = avatar_config.get("Model") if isinstance(avatar_config, dict) else None
    if not isinstance(avatar_config, dict) or avatar_config.get("Enabled") is not True \
            or not isinstance(model, dict) or model.get("ModelId") != "NordicDrake_AvatarFlight":
        fail(errors, "HyDragonNordicDrake must be an enabled avatar-flight config using its authored model")
    nordic_species = parsed.get(ROOT / "Server/HyDragon/DragonSpecies/NordicDrake.json")
    mount = nordic_species.get("Mount") if isinstance(nordic_species, dict) else None
    if mount != {"Mode": "AVATAR_FLIGHT", "AvatarFlightConfigId": "HyDragonNordicDrake"}:
        fail(errors, "Nordic Drake species must select the HyDragonNordicDrake avatar-flight config")
    encounter = parsed.get(ROOT / "Server/HyDragon/Encounters/NordicDrakeHighAltitude.json")
    eligibility = encounter.get("PlayerEligibility") if isinstance(encounter, dict) else None
    if not isinstance(eligibility, dict) \
            or eligibility.get("RequiredItemId") != "Tamework_Flightmasters_Talisman":
        fail(errors, "Nordic Drake flight eligibility must use only Tamework's Flightmaster's Talisman")
    interaction = parsed.get(ROOT / "Server/Tamework/Interactions/HyDragonIntDragon.json")
    interaction_entries = interaction.get("Interactions", []) if isinstance(interaction, dict) else []
    if not any(isinstance(entry, dict) and entry.get("Type") == "Mount" and entry.get("Enabled") is True
               for entry in interaction_entries):
        fail(errors, "Tamed Nordic Drake interaction config must expose the Tamework mount entry")


def validate_nordic_landing_recovery(parsed: dict[Path, object], errors: list[str]) -> None:
    """Ensure a failed Nordic Drake touchdown returns to a fresh landing approach."""
    template_path = ROOT / "Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon.json"
    template = parsed.get(template_path)
    if not isinstance(template, dict):
        fail(errors, "Nordic Drake landing template is unavailable")
        return

    def contains_landing_recovery(instructions: object) -> bool:
        if isinstance(instructions, dict):
            body_motion = instructions.get("BodyMotion")
            actions = instructions.get("Actions")
            if isinstance(body_motion, dict) and body_motion.get("Type") == "Land" \
                    and isinstance(actions, list):
                action_types = {action.get("Type") for action in actions if isinstance(action, dict)}
                if {"Timeout", "ResetSearchRays", "State"} <= action_types and any(
                        isinstance(action, dict) and action.get("Type") == "State"
                        and action.get("State") == ".AirLand"
                        for action in actions):
                    return True
            return any(contains_landing_recovery(child) for child in instructions.values())
        if isinstance(instructions, list):
            return any(contains_landing_recovery(instruction) for instruction in instructions)
        return False

    def find_touchdown_instructions(value: object) -> object | None:
        if isinstance(value, dict):
            sensor = value.get("Sensor")
            if isinstance(sensor, dict) and sensor.get("Type") == "State" \
                    and sensor.get("State") == ".AirTouchdown":
                return value.get("Instructions")
            for child in value.values():
                result = find_touchdown_instructions(child)
                if result is not None:
                    return result
        elif isinstance(value, list):
            for child in value:
                result = find_touchdown_instructions(child)
                if result is not None:
                    return result
        return None

    touchdown_instructions = find_touchdown_instructions(template)
    if not contains_landing_recovery(touchdown_instructions):
        fail(errors, "Nordic Drake touchdown must run its bounded recovery alongside the active Land motion")


def validate_nordic_health_phase_recovery(parsed: dict[Path, object], errors: list[str]) -> None:
    """Keep health-phase transitions coherent when healing interrupts a landing."""
    template_path = ROOT / "Server/NPC/Roles/Creature/HyDragon/Templates/Template_HyDragon_Dragon.json"
    template = parsed.get(template_path)
    if not isinstance(template, dict):
        fail(errors, "Nordic Drake health-phase template is unavailable")
        return

    def has_state(sensor: object, state: str) -> bool:
        if not isinstance(sensor, dict):
            return False
        if sensor.get("Type") == "State" and sensor.get("State") == state:
            return True
        return any(has_state(child, state) for child in sensor.get("Sensors", []))

    def has_motion_controller(sensor: object, controller: str) -> bool:
        if not isinstance(sensor, dict):
            return False
        if sensor.get("Type") == "MotionController" and sensor.get("MotionController") == controller:
            return True
        return any(has_motion_controller(child, controller) for child in sensor.get("Sensors", []))

    def has_health_range(sensor: object, parameter: str) -> bool:
        if not isinstance(sensor, dict):
            return False
        if sensor.get("Type") == "Self":
            for filter_ in sensor.get("Filters", []):
                if isinstance(filter_, dict) and filter_.get("Type") == "Stat" \
                        and filter_.get("ValueRange") == {"Compute": parameter}:
                    return True
        return any(has_health_range(child, parameter) for child in sensor.get("Sensors", []))

    def has_action(actions: object, action_type: str, state: str | None = None) -> bool:
        return any(
            isinstance(action, dict) and action.get("Type") == action_type
            and (state is None or action.get("State") == state)
            for action in actions if isinstance(actions, list)
        )

    candidates: list[dict[str, object]] = []

    def collect(value: object) -> None:
        if isinstance(value, dict):
            candidates.append(value)
            for child in value.values():
                collect(child)
        elif isinstance(value, list):
            for child in value:
                collect(child)

    collect(template.get("Instructions"))
    landing_cancel = any(
        has_state(candidate.get("Sensor"), ".AirLand")
        and has_state(candidate.get("Sensor"), ".AirTouchdown")
        and has_motion_controller(candidate.get("Sensor"), "Fly")
        and has_health_range(candidate.get("Sensor"), "AirPhaseHealthRange")
        and has_action(candidate.get("Actions"), "ResetSearchRays")
        and has_action(candidate.get("Actions"), "State", ".AirRanged")
        for candidate in candidates
    )
    if not landing_cancel:
        fail(errors, "Nordic Drake must cancel an airborne landing when health returns to the flight phase")

    grounded_fly_recovery = any(
        has_state(candidate.get("Sensor"), ".Default")
        and has_motion_controller(candidate.get("Sensor"), "Fly")
        and has_health_range(candidate.get("Sensor"), "GroundPhaseHealthRange")
        and has_action(candidate.get("Actions"), "State", ".AirLand")
        for candidate in candidates
    )
    if not grounded_fly_recovery:
        fail(errors, "Nordic Drake must route an airborne grounded default state back through landing")

def validate_altar_recipes(parsed: dict[Path, object], errors: list[str]) -> None:
    outputs = {
        "Draconic_Stone",
        "Draconic_Stone_Thorium",
        "Draconic_Stone_Cobalt",
        "Draconic_Stone_Adamantium",
        "Draconic_Stone_Ancient",
        "Revitalizing_Essence",
        "Wyvern_Egg",
        "HyDragon_Dragon_Horn",
    }
    seen: set[str] = set()
    item_root = ROOT / "Server" / "Item" / "Items"
    for path in item_root.rglob("*.json"):
        data = parsed.get(path)
        if not isinstance(data, dict):
            continue
        recipe = data.get("Recipe")
        if not isinstance(recipe, dict):
            continue
        output_ids = {
            entry.get("ItemId")
            for entry in recipe.get("Output", [])
            if isinstance(entry, dict)
        }
        targets = outputs & output_ids
        if not targets:
            continue
        seen.update(targets)
        benches = recipe.get("BenchRequirement", [])
        if not any(
            isinstance(bench, dict)
            and bench.get("Type") == "Crafting"
            and bench.get("Id") == "Draconic_Altar"
            for bench in benches
        ):
            fail(errors, f"draconic recipe is not altar-only: {path.relative_to(ROOT)}")
    missing = sorted(outputs - seen)
    if missing:
        fail(errors, f"missing altar recipe outputs: {', '.join(missing)}")


def validate_command_item(parsed: dict[Path, object], errors: list[str]) -> None:
    item_path = ROOT / "Server/Item/Items/Tool/HyDragon_Dragon_Horn.json"
    config_path = ROOT / "Server/Tamework/Items/Commands/HyDragonDragonHorn.json"
    item = parsed.get(item_path)
    config = parsed.get(config_path)
    if not isinstance(item, dict) or item.get("Parent") != "Tamework_Command_Whistle_Example":
        fail(errors, "HyDragon Dragon Horn must inherit Tamework's supported command interaction")
    if not isinstance(config, dict) or config.get("Parent") != "TwCommandExample":
        fail(errors, "HyDragon command config must inherit the supported Tamework command set")
        return
    if config.get("ItemIds") != ["HyDragon_Dragon_Horn"]:
        fail(errors, "HyDragon command config must bind only the Dragon Horn")
    if config.get("BondedRosterId") != "hydragon:dragon_horn" \
            or config.get("RosterStorage") != "BondedCompanions":
        fail(errors, "Dragon Horn must use the shared bonded-companion roster")
    if config.get("LinkEnabled") is not False \
            or config.get("LinkUseTogglesMembership") is not False:
        fail(errors, "Dragon Horn must disable inherited generic link/toggle behavior")
    if "CommandFamilyId" in config or "ProjectRosterToItemMetadata" in config:
        fail(errors, "Dragon Horn must not retain generic owner-family projection settings")
    allowed = config.get("AllowedRoles")
    required_roles = {
        "Tamed_Hydra", "Tamed_NordicDrake", "Tamed_RockDrakeT1",
        "Tamed_RockDrakeT2", "Tamed_RockDrakeT3",
        "Tamed_Wyvern_Mini_Wild", "Tamed_Wyvern_Mini_Nature", "Tamed_Wyvern_Mini_Toxic",
        "Tamed_Wyvern_Mini_Fire", "Tamed_Wyvern_Mini_Void", "Tamed_Wyvern_Mini_Lightning",
        "Tamed_Wyvern_Mini_Ice",
    }
    actual_roles = set(allowed.get("Allowlist", [])) if isinstance(allowed, dict) else set()
    if actual_roles != required_roles:
        fail(errors, f"HyDragon command role allowlist mismatch: {sorted(actual_roles)}")


def validate_revival_configs(parsed: dict[Path, object], errors: list[str]) -> None:
    companion_paths = [
        ROOT / "Server/Tamework/Companion/HyDragonFullDragons.json",
        ROOT / "Server/Tamework/Companion/HyDragonMiniwyvern.json",
    ]
    for path in companion_paths:
        data = parsed.get(path)
        command = data.get("Command") if isinstance(data, dict) else None
        if not isinstance(command, dict) or any(
                field in command for field in ("Travel", "Summon", "Revive")):
            fail(errors, f"{path.relative_to(ROOT)} must not own bonded travel/summon/revive lifecycle")

    policies = {
        "HyDragonFullDragons.json": {
            "FamilyId": "hydragon:full_dragons", "MaximumOwned": 0,
            "MaximumActive": 1,
            "Timers": {"SessionDurationSeconds": 600,
                       "SummonCooldownSeconds": 300},
            "AllowedRoles": {"Tamed_NordicDrake", "Tamed_Hydra", "Tamed_RockDrakeT1",
                             "Tamed_RockDrakeT2", "Tamed_RockDrakeT3"},
            "Costs": [("Revitalizing_Essence", 2), ("Draconic_Essence", 4)],
            "Features": {"Capture": True, "Provision": False, "Summon": True,
                         "Dismiss": True, "Revive": True},
        },
        "HyDragonMiniwyvern.json": {
            "FamilyId": "hydragon:soulbound_mini", "MaximumOwned": 1,
            "MaximumActive": 1,
            # An omitted timer decodes to Tamework's zero/unlimited sentinel.
            "AbsentTimers": {"SessionDurationSeconds",
                             "SummonCooldownSeconds"},
            "AllowedRoles": {"Tamed_Wyvern_Mini_Wild", "Tamed_Wyvern_Mini_Nature", "Tamed_Wyvern_Mini_Toxic", "Tamed_Wyvern_Mini_Fire", "Tamed_Wyvern_Mini_Void", "Tamed_Wyvern_Mini_Lightning", "Tamed_Wyvern_Mini_Ice"},
            "Costs": [("Revitalizing_Essence", 1), ("Draconic_Essence", 2)],
            "Features": {"Capture": False, "Provision": True, "Summon": True,
                         "Dismiss": True, "Revive": True},
        },
    }
    policy_root = ROOT / "Server/Tamework/BondedCompanions/Rosters"
    for filename, expected in policies.items():
        path = policy_root / filename
        data = parsed.get(path)
        if not isinstance(data, dict):
            fail(errors, f"missing bonded roster policy: {path.relative_to(ROOT)}")
            continue
        for field in ("FamilyId", "MaximumOwned", "MaximumActive"):
            if data.get(field) != expected[field]:
                fail(errors, f"{path.relative_to(ROOT)} has invalid {field}")
        for field, value in expected.get("Timers", {}).items():
            if data.get(field) != value:
                fail(errors, f"{path.relative_to(ROOT)} has invalid {field}")
        for field in expected.get("AbsentTimers", set()):
            if field in data:
                fail(errors, f"{path.relative_to(ROOT)} must omit {field} for an unlimited Miniwyvern")
        if data.get("RosterId") != "hydragon:dragon_horn" \
                or set(data.get("AllowedRoles", [])) != expected["AllowedRoles"]:
            fail(errors, f"{path.relative_to(ROOT)} has invalid roster/role authority")
        costs = data.get("RevivePrice", {}).get("Costs")
        actual_costs = [(entry.get("ItemId"), entry.get("Quantity"))
                        for entry in costs] if isinstance(costs, list) \
            and all(isinstance(entry, dict) for entry in costs) else None
        if actual_costs != expected["Costs"]:
            fail(errors, f"{path.relative_to(ROOT)} has invalid ordered revive recipe")
        if data.get("Features") != expected["Features"]:
            fail(errors, f"{path.relative_to(ROOT)} has invalid bonded feature policy")

    population_root = ROOT / "Server/Tamework/PopulationGroups"
    for obsolete in ("HyDragonFullDragons.json", "HyDragonSoulboundMiniwyvern.json"):
        if (population_root / obsolete).exists():
            fail(errors, f"obsolete generic population policy remains: {obsolete}")
    encounter = parsed.get(ROOT / "Server/HyDragon/Encounters/NordicDrakeHighAltitude.json")
    eligibility = encounter.get("PlayerEligibility") if isinstance(encounter, dict) else None
    if isinstance(eligibility, dict) and "ActiveCompanionGroup" in eligibility:
        fail(errors, "Nordic encounter must not retain obsolete ActiveCompanionGroup evidence")

    essence_path = ROOT / "Server/Item/Items/Ingredient/Revitalizing_Essence.json"
    essence = parsed.get(essence_path)
    secondary = essence.get("Interactions", {}).get("Secondary", {}).get("Interactions") \
        if isinstance(essence, dict) else None
    if secondary != [{"Type": "Simple"}]:
        fail(errors, "Revitalizing Essence must override inherited Fire attunement with a runtime-valid no-op")


def validate_miniwyvern_projectile_contract(parsed: dict[Path, object], errors: list[str]) -> None:
    """Keep the seven modern Miniwyvern projectile forms self-contained and safe."""
    forms = {
        "Fire": (8, 12, 10, "HyDragon_Miniwyvern_Fire_Burn"),
        "Ice": (8, 12, 10, "HyDragon_Miniwyvern_Ice_Slow"),
        "Lightning": (8, 12, 10, "HyDragon_Miniwyvern_Lightning_Shock"),
        "Nature": (8, 12, 10, "HyDragon_Miniwyvern_Nature_Root"),
        "Toxic": (8, 12, 10, "HyDragon_Miniwyvern_Toxic_Projectile_Weakness"),
        "Void": (8, 12, 10, "HyDragon_Miniwyvern_Void_Projectile_Exposure"),
        "Wild": (10, 15, 12, None),
    }
    config_root = ROOT / "Server/ProjectileConfigs/HyDragon/Wyvern_Mini"
    interaction_root = ROOT / "Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini"
    root_root = ROOT / "Server/Item/RootInteractions/NPCs/HyDragon/Wyvern_Mini"
    profiles = {
        "Base": (28, 32, 6, 4.0, 0),
        "Intermediate": (34, 40, 4, 5.0, 1),
        "Pattern_First": (34, 40, 4, 5.0, 1),
        "Pattern_Echo": (34, 40, 4, 5.0, 1),
        "Mastery_First": (40, 48, 3, 6.0, 2),
        "Mastery_Echo": (40, 48, 3, 6.0, 2),
    }
    legacy_root = ROOT / "Server/Projectiles/HyDragon/Wyvern_Mini"
    hit_root_dir = root_root / "ProjectileHits"
    hit_child_dir = interaction_root / "ProjectileHits"
    hit_references: dict[str, int] = {}
    if legacy_root.is_dir() and any(legacy_root.glob("*.json")):
        fail(errors, "obsolete Miniwyvern Server/Projectiles profiles remain")

    for path in sorted(interaction_root.rglob("*.json")):
        source = path.read_text(encoding="utf-8-sig")
        if "LaunchProjectile" in source or "ProjectileId" in source:
            fail(errors, f"deprecated Miniwyvern projectile launch in {path.relative_to(ROOT)}")

    for form, (base_damage, intermediate_damage, apex_damage, status) in forms.items():
        damages = (base_damage, intermediate_damage, intermediate_damage,
                   intermediate_damage, apex_damage, apex_damage)
        for (tier, (force, terminal, gravity, timeout, damage_index)), damage in zip(profiles.items(), damages):
            config_id = f"Projectile_Config_HyDragon_Miniwyvern_{form}_{tier}"
            config_path = config_root / f"{config_id}.json"
            config = parsed.get(config_path)
            if not isinstance(config, dict):
                fail(errors, f"missing Miniwyvern projectile config: {config_path.relative_to(ROOT)}")
                continue
            physics = config.get("Physics")
            offset = config.get("SpawnOffset")
            if config.get("LaunchForce") != force or not isinstance(physics, dict) \
                    or physics.get("Type") != "Standard" or physics.get("Gravity") != gravity \
                    or physics.get("TerminalVelocityAir") != terminal or offset != {"X": 0, "Y": 0, "Z": 1}:
                fail(errors, f"invalid Miniwyvern {form} {tier} physics profile")
            if not isinstance(config.get("Model"), str) or any(field in config for field in
                    ("Parent", "Damage", "Splash", "BlockDamage", "Knockback", "Impact")):
                fail(errors, f"Miniwyvern {form} {tier} is not self-contained and bounded")
            interactions = config.get("Interactions")
            if not isinstance(interactions, dict) or set(interactions) != {"ProjectileSpawn", "ProjectileHit", "ProjectileMiss"}:
                fail(errors, f"Miniwyvern {form} {tier} has no interactions")
                continue
            expected_hit_tier = {
                "Base": "Base",
                "Intermediate": "Intermediate_First",
                "Pattern_First": "Intermediate_First",
                "Pattern_Echo": "Intermediate_Echo",
                "Mastery_First": "Mastery_First",
                "Mastery_Echo": "Mastery_Echo",
            }[tier]
            hit_root_id = f"Root_HyDragon_Miniwyvern_{form}_ProjectileHit_{expected_hit_tier}"
            hit_child_id = f"HyDragon_Miniwyvern_{form}_ProjectileHit_{expected_hit_tier}"
            if interactions.get("ProjectileHit") != hit_root_id:
                fail(errors, f"Miniwyvern {form} {tier} must reference {hit_root_id} as ProjectileHit")
                continue
            hit_references[hit_root_id] = hit_references.get(hit_root_id, 0) + 1
            hit_root = parsed.get(hit_root_dir / f"{hit_root_id}.json")
            if not isinstance(hit_root, dict) or hit_root.get("Interactions") != [hit_child_id]:
                fail(errors, f"Miniwyvern {form} {tier} hit root does not resolve its intended hit child")
                continue
            damage_step = parsed.get(hit_child_dir / f"{hit_child_id}.json")
            calculator = damage_step.get("DamageCalculator") if isinstance(damage_step, dict) else None
            actual_damage = calculator.get("BaseDamage", {}).get("Physical") if isinstance(calculator, dict) else None
            if not isinstance(damage_step, dict) or set(damage_step) != {"Type", "DamageCalculator", "Next", "Failed", "Blocked"} \
                    or damage_step.get("Type") != "DamageEntity" \
                    or not isinstance(calculator, dict) or calculator != {"Type": "Absolute", "BaseDamage": {"Physical": damage}, "RandomPercentageModifier": 0} \
                    or actual_damage != damage:
                fail(errors, f"Miniwyvern {form} {tier} has invalid hit damage")
                continue
            next_step = damage_step.get("Next")
            failed = damage_step.get("Failed")
            blocked = damage_step.get("Blocked")
            if not isinstance(next_step, dict) or failed != {"Type": "RemoveEntity", "Entity": "User"} \
                    or blocked != {"Type": "RemoveEntity", "Entity": "User"}:
                fail(errors, f"Miniwyvern {form} {tier} does not safely terminate hit outcomes")
            expected_status = status if not tier.endswith("Echo") else None
            remove = {"Type": "RemoveEntity", "Entity": "User"}
            effect = {"Type": "ApplyEffect", "Entity": "Target", "EffectId": expected_status}
            if expected_status is None:
                expected_next = remove
            elif form == "Lightning":
                expected_next = {"Type": "Serial", "Interactions": [
                    {"Type": "Interrupt", "Entity": "Target", "ExcludedTag": "Uninterruptable"}, effect, remove]}
            else:
                expected_next = {"Type": "Serial", "Interactions": [effect, remove]}
            if next_step != expected_next:
                fail(errors, f"Miniwyvern {form} {tier} has an invalid accepted DamageEntity chain")
            spawn = interactions.get("ProjectileSpawn")
            spawn_steps = spawn.get("Interactions") if isinstance(spawn, dict) else None
            miss = interactions.get("ProjectileMiss")
            miss_steps = miss.get("Interactions") if isinstance(miss, dict) else None
            if not isinstance(spawn, dict) or set(spawn) != {"Cooldown", "Interactions"} \
                    or spawn.get("Cooldown") != {"Cooldown": 0} \
                    or not isinstance(miss, dict) or set(miss) != {"Cooldown", "Interactions"} \
                    or miss.get("Cooldown") != {"Cooldown": 0} \
                    or not isinstance(spawn_steps, list) or len(spawn_steps) != 2 \
                    or spawn_steps[0] != {"Type": "Simple", "RunTime": timeout} \
                    or spawn_steps[1] != {"Type": "RemoveEntity", "Entity": "User"} \
                    or miss_steps != [{"Type": "RemoveEntity", "Entity": "User"}]:
                fail(errors, f"Miniwyvern {form} {tier} has unsafe miss/timeout behavior")

        expected_references = {
            f"Root_HyDragon_Miniwyvern_{form}_ProjectileHit_Base": 1,
            f"Root_HyDragon_Miniwyvern_{form}_ProjectileHit_Intermediate_First": 2,
            f"Root_HyDragon_Miniwyvern_{form}_ProjectileHit_Intermediate_Echo": 1,
            f"Root_HyDragon_Miniwyvern_{form}_ProjectileHit_Mastery_First": 1,
            f"Root_HyDragon_Miniwyvern_{form}_ProjectileHit_Mastery_Echo": 1,
        }
        for hit_root_id, expected_count in expected_references.items():
            if hit_references.get(hit_root_id) != expected_count:
                fail(errors, f"Miniwyvern {form} hit root {hit_root_id} has an invalid reference count")

        for tier in ("Base", "Intermediate"):
            root = parsed.get(root_root / f"Root_NPC_Wyvern_Mini_{form}_Projectile_{tier}.json")
            interaction = parsed.get(interaction_root / f"Wyvern_Mini_{form}_Projectile_{tier}.json")
            expected = f"Projectile_Config_HyDragon_Miniwyvern_{form}_{tier}"
            expected_root = {"Interactions": [f"Wyvern_Mini_{form}_Projectile_{tier}"], "Tags": {"Attack": ["Ranged"]}}
            if root != expected_root \
                    or not isinstance(interaction, dict) or interaction != {"Type": "Projectile", "Config": expected}:
                fail(errors, f"Miniwyvern {form} {tier} root does not resolve its modern projectile")
        for tier in ("Pattern", "Mastery"):
            root = parsed.get(root_root / f"Root_NPC_Wyvern_Mini_{form}_Projectile_{tier}.json")
            interaction = parsed.get(interaction_root / f"Wyvern_Mini_{form}_Projectile_{tier}.json")
            prefix = f"Projectile_Config_HyDragon_Miniwyvern_{form}_{tier}_"
            expected_root = {"Interactions": [f"Wyvern_Mini_{form}_Projectile_{tier}"], "Tags": {"Attack": ["Ranged"]}}
            echo_root = parsed.get(root_root / f"Root_NPC_Wyvern_Mini_{form}_Projectile_{tier}_Echo.json")
            echo_interaction = parsed.get(interaction_root / f"Wyvern_Mini_{form}_Projectile_{tier}_Echo.json")
            expected_interaction = {"Type": "Projectile", "Config": prefix + "First"}
            expected_echo_root = {"Interactions": [f"Wyvern_Mini_{form}_Projectile_{tier}_Echo"], "Tags": {"Attack": ["Ranged"]}}
            expected_echo_interaction = {"Type": "Projectile", "Config": prefix + "Echo"}
            if root != expected_root or interaction != expected_interaction or echo_root != expected_echo_root or echo_interaction != expected_echo_interaction:
                fail(errors, f"Miniwyvern {form} {tier} roots must expose direct first and echo projectiles")


def main() -> int:
    errors: list[str] = []
    parsed = load_json_assets(errors)
    base_root = hytale_asset_root(errors)
    known_assets = asset_stems(ROOT / "Common") | asset_stems(ROOT / "Server")
    if base_root is not None:
        known_assets |= asset_stems(base_root)
    validate_english_ids(errors)
    validate_no_pre_release_archives(errors)
    validate_bonded_system_removed(errors)
    validate_runtime_item_contracts(parsed, errors)
    validate_locales(errors)
    validate_interaction_message_localization(parsed, errors)
    require_files(errors)
    validate_capture_configs(parsed, errors)
    validate_miniwyvern_ability_contract(parsed, errors)
    validate_miniwyvern_projectile_contract(parsed, errors)
    validate_stone_tiers(parsed, errors)
    validate_no_miniwyvern_spawns(parsed, errors)
    validate_miniwyvern_role_wiring(parsed, errors)
    validate_spawn_patch_role_identity(parsed, errors)
    validate_static_spawn_contracts(parsed, base_root, known_assets, errors)
    projectile_ids = {
        path.stem for root in (ROOT, base_root) if root is not None
        for path in (root / "Server" / "Projectiles").rglob("*.json")
    }
    validate_domain_references(parsed, known_assets, projectile_ids, errors)
    validate_release_content_contracts(parsed, errors)
    validate_nordic_landing_recovery(parsed, errors)
    validate_nordic_health_phase_recovery(parsed, errors)
    validate_altar_recipes(parsed, errors)
    validate_command_item(parsed, errors)
    validate_revival_configs(parsed, errors)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"HyDragon asset validation failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print(f"HyDragon asset validation passed ({len(parsed)} JSON assets, {len(LOCALES)} locales).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
