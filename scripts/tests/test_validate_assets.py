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


if __name__ == "__main__":
    unittest.main()
