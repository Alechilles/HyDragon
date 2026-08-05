from __future__ import annotations

import copy
import importlib.util
import unittest
from pathlib import Path


VALIDATOR_PATH = Path(__file__).resolve().parents[1] / "validate_assets.py"
MODULE_SPEC = importlib.util.spec_from_file_location("validate_assets", VALIDATOR_PATH)
if MODULE_SPEC is None or MODULE_SPEC.loader is None:
    raise ImportError(f"Could not load validator module from {VALIDATOR_PATH}")
VALIDATOR = importlib.util.module_from_spec(MODULE_SPEC)
MODULE_SPEC.loader.exec_module(VALIDATOR)


class ValidatorContractTest(unittest.TestCase):
    def test_rejects_obsolete_toggle_in_dragon_horn_wheel(self) -> None:
        load_errors: list[str] = []
        parsed = VALIDATOR.load_json_assets(load_errors)
        self.assertEqual([], load_errors)

        config_path = VALIDATOR.RESOURCE_ROOT / "Server/Tamework/Items/Commands/HyDragonDragonHorn.json"
        config = copy.deepcopy(parsed[config_path])
        self.assertIsInstance(config, dict)
        commands = config["CommandList"]
        toggle = next(
            (command for command in commands if command.get("Id") == "ToggleAirborneMode"),
            None,
        )
        aggressive_index = next(
            (index for index, command in enumerate(commands) if command.get("Id") == "Aggressive"),
            None,
        )
        if toggle is None:
            self.assertIsNotNone(aggressive_index)
            toggle = copy.deepcopy(commands[aggressive_index])
            toggle["Id"] = "ToggleAirborneMode"
        if aggressive_index is None:
            aggressive_index = next(
                index for index, command in enumerate(commands)
                if command.get("Id") == "ToggleAirborneMode"
            )
            commands[aggressive_index] = copy.deepcopy(commands[aggressive_index])
            commands[aggressive_index]["Id"] = "Aggressive"
        commands[aggressive_index] = copy.deepcopy(toggle)
        parsed[config_path] = config

        errors: list[str] = []
        VALIDATOR.validate_command_item(parsed, errors)

        self.assertIn(
            "Dragon Horn command wheel must contain Aggressive instead of ToggleAirborneMode",
            errors,
        )

    def test_rejects_missing_flying_aggressive_path(self) -> None:
        load_errors: list[str] = []
        parsed = VALIDATOR.load_json_assets(load_errors)
        self.assertEqual([], load_errors)

        template_path = (
            VALIDATOR.RESOURCE_ROOT
            / "Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json"
        )
        template = copy.deepcopy(parsed[template_path])
        self.assertIsInstance(template, dict)

        def find_aggressive(value: object) -> dict[str, object] | None:
            if isinstance(value, dict):
                sensor = value.get("Sensor")
                if isinstance(sensor, dict) and sensor.get("Type") == "State" \
                        and sensor.get("State") == "Aggressive":
                    return value
                for child in value.values():
                    found = find_aggressive(child)
                    if found is not None:
                        return found
            elif isinstance(value, list):
                for child in value:
                    found = find_aggressive(child)
                    if found is not None:
                        return found
            return None

        aggressive = find_aggressive(template)
        if aggressive is None:
            aggressive = {
                "Sensor": {"Type": "State", "State": "Aggressive"},
                "Instructions": [
                    {
                        "Sensor": {
                            "Type": "And",
                            "Sensors": [
                                {"Type": "Flag", "Name": "AirborneMode", "Set": False},
                                {"Type": "MotionController", "MotionController": "Walk"},
                            ],
                        },
                        "Instructions": [{"Reference": "Component_Tamework_Instruction_Aggressive"}],
                    },
                    {
                        "Sensor": {
                            "Type": "And",
                            "Sensors": [
                                {"Type": "Flag", "Name": "AirborneMode"},
                                {"Type": "MotionController", "MotionController": "Fly"},
                            ],
                        },
                        "Instructions": [{"Reference": "Component_Tamework_Instruction_Aggressive"}],
                    },
                ],
            }
            template.setdefault("Instructions", []).append(aggressive)

        instructions = aggressive.get("Instructions")
        self.assertIsInstance(instructions, list)
        aggressive["Instructions"] = [
            branch for branch in instructions
            if not (
                isinstance(branch, dict)
                and isinstance(branch.get("Sensor"), dict)
                and any(
                    isinstance(sensor, dict)
                    and sensor.get("Type") == "MotionController"
                    and sensor.get("MotionController") == "Fly"
                    for sensor in branch["Sensor"].get("Sensors", [])
                )
            )
        ]
        parsed[template_path] = template

        errors: list[str] = []
        VALIDATOR.validate_miniwyvern_role_wiring(parsed, errors)

        self.assertIn(
            "Miniwyvern aggressive state must have grounded and flying paths",
            errors,
        )

    def test_rejects_aggressive_path_without_generic_component(self) -> None:
        load_errors: list[str] = []
        parsed = VALIDATOR.load_json_assets(load_errors)
        self.assertEqual([], load_errors)

        template_path = (
            VALIDATOR.RESOURCE_ROOT
            / "Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json"
        )
        source_template = parsed[template_path]

        def find_aggressive(value: object) -> dict[str, object] | None:
            if isinstance(value, dict):
                sensor = value.get("Sensor")
                if isinstance(sensor, dict) and sensor.get("Type") == "State" \
                        and sensor.get("State") == "Aggressive":
                    return value
                for child in value.values():
                    found = find_aggressive(child)
                    if found is not None:
                        return found
            elif isinstance(value, list):
                for child in value:
                    found = find_aggressive(child)
                    if found is not None:
                        return found
            return None

        self.assertIsInstance(source_template, dict)
        source_aggressive = find_aggressive(source_template)
        self.assertIsNotNone(source_aggressive)
        source_instructions = source_aggressive.get("Instructions")
        self.assertIsInstance(source_instructions, list)
        self.assertEqual(2, len(source_instructions))

        for branch_index in range(2):
            for replacement in ([], [{"Reference": "Component_Instruction_Null"}]):
                with self.subTest(branch_index=branch_index, replacement=replacement):
                    template = copy.deepcopy(source_template)
                    aggressive = find_aggressive(template)
                    self.assertIsNotNone(aggressive)
                    instructions = aggressive.get("Instructions")
                    self.assertIsInstance(instructions, list)
                    instructions[branch_index]["Instructions"] = replacement
                    parsed[template_path] = template

                    errors: list[str] = []
                    VALIDATOR.validate_miniwyvern_role_wiring(parsed, errors)

                    self.assertIn(
                        "Miniwyvern aggressive state must have grounded and flying paths",
                        errors,
                    )

    def test_declares_safe_aggressive_attitude_for_tamed_dragons(self) -> None:
        load_errors: list[str] = []
        parsed = VALIDATOR.load_json_assets(load_errors)
        self.assertEqual([], load_errors)

        attitude_path = (
            VALIDATOR.RESOURCE_ROOT
            / "Server/NPC/Attitude/Roles/HyDragonCompanion.json"
        )
        self.assertEqual(
            {
                "Groups": {
                    "Hostile": [
                        "Aggressive",
                        "Predators",
                        "PredatorsBig",
                        "Undead",
                        "Void",
                        "Trork",
                        "Goblin",
                        "Outlander",
                        "Scarak",
                        "Vermin",
                        "Spiders",
                        "Scorpions",
                        "Snakes",
                    ],
                    "Ignore": ["Prey", "PreyBig", "Self"],
                }
            },
            parsed.get(attitude_path),
        )

        mini_template_path = (
            VALIDATOR.RESOURCE_ROOT
            / "Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json"
        )
        mini_template = parsed[mini_template_path]
        self.assertIsInstance(mini_template, dict)
        self.assertEqual("HyDragonCompanion", mini_template.get("AttitudeGroup"))

        state_declarations = mini_template["Instructions"][0]["Actions"]
        self.assertIn(
            {"Type": "State", "State": "Aggressive"},
            state_declarations,
        )

        for template_name in (
            "Template_HyDragon_Dragon_Tamed.json",
            "Template_HyDragon_Tamed.json",
        ):
            template_path = (
                VALIDATOR.RESOURCE_ROOT
                / "Server/NPC/Roles/Creature/HyDragon/Templates"
                / template_name
            )
            template = parsed[template_path]
            self.assertIsInstance(template, dict)
            self.assertEqual(
                "HyDragonCompanion",
                template["Parameters"]["AttitudeGroup"]["Value"],
                template_name,
            )

        for role_path in sorted(
            (VALIDATOR.RESOURCE_ROOT / "Server/NPC/Roles/Creature/HyDragon").rglob("Tamed_*.json")
        ):
            role = parsed[role_path]
            self.assertIsInstance(role, dict)
            attitude_group = role.get("Modify", {}).get("AttitudeGroup")
            if attitude_group is not None:
                self.assertEqual("HyDragonCompanion", attitude_group, role_path.name)

    def test_rejects_miniwyvern_aggressive_without_state_declaration(self) -> None:
        load_errors: list[str] = []
        parsed = VALIDATOR.load_json_assets(load_errors)
        self.assertEqual([], load_errors)

        template_path = (
            VALIDATOR.RESOURCE_ROOT
            / "Server/NPC/Roles/Creature/HyDragon/Templates/Template_Wyvern_Mini_Flying_Tamed.json"
        )
        template = copy.deepcopy(parsed[template_path])
        self.assertIsInstance(template, dict)
        actions = template["Instructions"][0]["Actions"]
        template["Instructions"][0]["Actions"] = [
            action
            for action in actions
            if action != {"Type": "State", "State": "Aggressive"}
        ]
        parsed[template_path] = template

        errors: list[str] = []
        VALIDATOR.validate_miniwyvern_role_wiring(parsed, errors)

        self.assertIn(
            "Miniwyvern tamed template must declare Aggressive as a valid state",
            errors,
        )


if __name__ == "__main__":
    unittest.main()
