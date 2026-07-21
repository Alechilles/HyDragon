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
    validate_spawn_patch_role_identity(parsed, errors)
    validate_altar_recipes(parsed, errors)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"HyDragon asset validation failed with {len(errors)} error(s).", file=sys.stderr)
        return 1
    print(f"HyDragon asset validation passed ({len(parsed)} JSON assets, {len(LOCALES)} locales).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
