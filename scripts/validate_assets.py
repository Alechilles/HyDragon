#!/usr/bin/env python3
"""Deterministic preflight validation for HyDragon's authored asset surface."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOTS = (ROOT / "Common", ROOT / "Server")
JSON_SUFFIXES = {".json", ".particlesystem", ".particlespawner", ".blockymodel", ".blockyanim"}
LOCALES = ("en-US", "pt-BR", "de-DE", "fr-FR", "es-ES")
BANNED_PRE_RELEASE_TOKENS = (
    "Draconic_Essence_Igne",
    "Draconic_Essence_Cryo",
    "Draconic_Essence_Storm",
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


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


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


def validate_interaction_message_localization(parsed: dict[Path, object], errors: list[str]) -> None:
    english_path = ROOT / "Server/Languages/en-US/server.lang"
    english = read_lang(english_path, errors) if english_path.is_file() else {}

    def visit(value: object, path: Path) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                if key == "Message" and isinstance(child, str):
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
        "Server/Item/Items/Ingredient/Draconic_Essence_Water.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Nature.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Lightning.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Wind.json",
        "Server/Item/Items/Ingredient/Draconic_Essence_Void.json",
        "Server/Item/Items/Ingredient/Revitalizing_Essence.json",
        "Server/Item/Items/Ingredient/Draconic_Soul_Bond.json",
        "Server/Item/Items/Tool/HyDragon_Command_Whistle.json",
        "Server/Tamework/Items/Commands/HyDragonDragonCommand.json",
        "Server/Tamework/PopulationGroups/HyDragonFullDragons.json",
        "Server/Tamework/PopulationGroups/HyDragonSoulboundMiniwyvern.json",
        "Server/HyDragon/Encounters/NordicDrakeHighAltitude.json",
        "Server/HyDragon/StoneMaintenance/Default.json",
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
        for name in ("Neutral", "Lightning", "Wind", "Ice", "Fire", "Water", "Nature", "Void")
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
    tamed_path = ROOT / "Server/NPC/Roles/Creature/HyDragon/Wyvern_Mini/Tamed_Wyvern_Mini.json"
    template_path = ROOT / "Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json"
    follow_path = ROOT / "Server/NPC/Roles/Creature/HyDragon/Components/Component_Tamework_Instruction_Follow_Flying.json"
    interaction_path = ROOT / "Server/Tamework/Interactions/HyDragonIntWyvernMini.json"
    companion_path = ROOT / "Server/Tamework/Companion/HyDragonMiniwyvern.json"
    population_path = ROOT / "Server/Tamework/PopulationGroups/HyDragonSoulboundMiniwyvern.json"
    root_bite_path = ROOT / "Server/Item/RootInteractions/NPCs/Creature/HyDragon/Root_NPC_Wyvern_Mini_Bite.json"
    bite_path = ROOT / "Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Bite.json"
    bite_damage_path = ROOT / "Server/Item/Interactions/NPCs/HyDragon/Wyvern_Mini/Wyvern_Mini_Bite_Damage.json"

    wild = parsed.get(wild_path)
    tamed = parsed.get(tamed_path)
    template = parsed.get(template_path)
    follow = parsed.get(follow_path)
    interaction = parsed.get(interaction_path)
    companion = parsed.get(companion_path)
    population = parsed.get(population_path)
    root_bite = parsed.get(root_bite_path)
    bite = parsed.get(bite_path)
    bite_damage = parsed.get(bite_damage_path)

    if not isinstance(tamed, dict) or tamed.get("Reference") != "Template_Wyvern_Mini_Flying_Tamed":
        fail(errors, "Tamed_Wyvern_Mini must reference Template_Wyvern_Mini_Flying_Tamed")
        return
    if not isinstance(template, dict) or template.get("Type") != "Abstract":
        fail(errors, "Miniwyvern tamed template is missing or is not Abstract")
        return
    if not isinstance(wild, dict):
        fail(errors, "Miniwyvern wild role is missing")
        return

    wild_modify = wild.get("Modify")
    if isinstance(wild_modify, dict) and wild_modify.get("InteractionConfigId") not in (None, ""):
        fail(errors, "Soul Bond-only wild Miniwyvern must not expose the tamed interaction config")

    tamed_modify = tamed.get("Modify")
    if not isinstance(tamed_modify, dict):
        fail(errors, "Tamed_Wyvern_Mini has no Modify block")
    else:
        for capability in ("CanFollow", "CanHold", "CanDefend", "CanAttackTarget"):
            if tamed_modify.get(capability) is not True:
                fail(errors, f"Tamed_Wyvern_Mini must explicitly enable {capability}")
        if tamed_modify.get("IsMountable") is not False:
            fail(errors, "Tamed_Wyvern_Mini must explicitly remain non-mountable")
        if tamed_modify.get("Attack") != "Root_NPC_Wyvern_Mini_Bite":
            fail(errors, "Tamed_Wyvern_Mini must retain Root_NPC_Wyvern_Mini_Bite")
        if tamed_modify.get("InteractionConfigId") != "HyDragonIntWyvernMini":
            fail(errors, "Tamed_Wyvern_Mini must reference HyDragonIntWyvernMini")

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

    if not isinstance(interaction, dict):
        fail(errors, "HyDragonIntWyvernMini interaction config is missing")
    else:
        if interaction.get("RoleIds") != ["Tamed_Wyvern_Mini"]:
            fail(errors, "HyDragonIntWyvernMini must be scoped only to Tamed_Wyvern_Mini")
        interactions = interaction.get("Interactions", [])
        interaction_types = {
            entry.get("Type") for entry in interactions if isinstance(entry, dict)
        }
        if {"Feed", "ModeCycle"} - interaction_types:
            fail(errors, "HyDragonIntWyvernMini must provide Feed and ModeCycle")
        if interaction_types & {"Mount", "Tame"}:
            fail(errors, "HyDragonIntWyvernMini must not expose Mount or Tame")
        mode_cycle = next(
            (entry for entry in interactions if isinstance(entry, dict) and entry.get("Type") == "ModeCycle"),
            {},
        )
        cycle_states = {
            entry.get("State") for entry in mode_cycle.get("Cycle", []) if isinstance(entry, dict)
        }
        missing_modes = required_states - cycle_states
        if missing_modes:
            fail(errors, f"HyDragonIntWyvernMini mode cycle is missing: {', '.join(sorted(missing_modes))}")

    if not isinstance(companion, dict) or companion.get("RoleIds") != ["Tamed_Wyvern_Mini"]:
        fail(errors, "HyDragonMiniwyvern companion lifecycle config must target Tamed_Wyvern_Mini")
    else:
        if companion.get("Parent") != "TwCompanionDefault":
            fail(errors, "HyDragonMiniwyvern must inherit Tamework's durable companion lifecycle defaults")
        command = companion.get("Command")
        travel = command.get("Travel") if isinstance(command, dict) else None
        if not isinstance(command, dict) or not isinstance(command.get("DeadRespawnCooldownMins"), (int, float)):
            fail(errors, "HyDragonMiniwyvern must declare command-link-independent death recovery")
        if not isinstance(travel, dict) or travel.get("CrossWorldRecallEnabled") is not True or travel.get("FollowMasterOnWorldChange") is not True:
            fail(errors, "HyDragonMiniwyvern must preserve follow/recovery across world transitions")

    if not isinstance(population, dict) or population.get("RoleIds") != ["Tamed_Wyvern_Mini"]:
        fail(errors, "Soulbound Miniwyvern population group must target Tamed_Wyvern_Mini")
    else:
        limits = population.get("Limits")
        if not isinstance(limits, dict) or limits.get("MaxOwnedPerOwner") != 1 or limits.get("MaxActivePerOwner") != 1:
            fail(errors, "Soulbound Miniwyvern population group must enforce one owned and one active")


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


def validate_altar_recipes(parsed: dict[Path, object], errors: list[str]) -> None:
    outputs = {
        "Draconic_Stone",
        "Draconic_Stone_Thorium",
        "Draconic_Stone_Cobalt",
        "Draconic_Stone_Adamantium",
        "Draconic_Stone_Ancient",
        "Revitalizing_Essence",
        "Draconic_Soul_Bond",
        "HyDragon_Command_Whistle",
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
    item_path = ROOT / "Server/Item/Items/Tool/HyDragon_Command_Whistle.json"
    config_path = ROOT / "Server/Tamework/Items/Commands/HyDragonDragonCommand.json"
    item = parsed.get(item_path)
    config = parsed.get(config_path)
    if not isinstance(item, dict) or item.get("Parent") != "Tamework_Command_Whistle_Example":
        fail(errors, "HyDragon command whistle must inherit Tamework's supported command interaction")
    if not isinstance(config, dict) or config.get("Parent") != "TwCommandExample":
        fail(errors, "HyDragon command config must inherit the supported Tamework command set")
        return
    if config.get("ItemIds") != ["HyDragon_Command_Whistle"]:
        fail(errors, "HyDragon command config must bind only the production HyDragon whistle")
    allowed = config.get("AllowedRoles")
    required_roles = {
        "Tamed_Hydra", "Tamed_NordicDrake", "Tamed_RockDrakeT1",
        "Tamed_RockDrakeT2", "Tamed_RockDrakeT3", "Tamed_Wyvern_Mini",
    }
    actual_roles = set(allowed.get("Allowlist", [])) if isinstance(allowed, dict) else set()
    if actual_roles != required_roles:
        fail(errors, f"HyDragon command role allowlist mismatch: {sorted(actual_roles)}")


def validate_repair_interaction(parsed: dict[Path, object], errors: list[str]) -> None:
    stone_path = ROOT / "Server/Item/Items/Ingredient/Draconic_Stone.json"
    essence_path = ROOT / "Server/Item/Items/Ingredient/Revitalizing_Essence.json"
    stone = parsed.get(stone_path)
    essence = parsed.get(essence_path)
    damaged = stone.get("State", {}).get("Damaged", {}) if isinstance(stone, dict) else {}
    primary = damaged.get("Interactions", {}).get("Primary", {}).get("Interactions", []) \
        if isinstance(damaged, dict) else []
    repair_types = [entry.get("Type") for entry in primary if isinstance(entry, dict)]
    if repair_types != ["HyDragonRepairBondedStone"]:
        fail(errors, "repair interaction must be attached to the held Damaged stone state")
    if isinstance(essence, dict) and "HyDragonRepairBondedStone" in json.dumps(essence):
        fail(errors, "Revitalizing Essence must be reserved from inventory, not used as the held repair authority")
    secondary = essence.get("Interactions", {}).get("Secondary", {}).get("Interactions") \
        if isinstance(essence, dict) else None
    if secondary != []:
        fail(errors, "Revitalizing Essence must explicitly clear the Fire Essence attunement interaction it inherits")


def main() -> int:
    errors: list[str] = []
    parsed = load_json_assets(errors)
    validate_english_ids(errors)
    validate_locales(errors)
    validate_interaction_message_localization(parsed, errors)
    require_files(errors)
    validate_capture_configs(parsed, errors)
    validate_stone_tiers(parsed, errors)
    validate_no_miniwyvern_spawns(parsed, errors)
    validate_miniwyvern_role_wiring(parsed, errors)
    validate_spawn_patch_role_identity(parsed, errors)
    validate_altar_recipes(parsed, errors)
    validate_command_item(parsed, errors)
    validate_repair_interaction(parsed, errors)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"HyDragon asset validation failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print(f"HyDragon asset validation passed ({len(parsed)} JSON assets, {len(LOCALES)} locales).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
