import importlib
import logging
import sys
import types
import unittest
from pathlib import Path


RUNTIME_PACKAGE = Path(__file__).parents[2] / "main/python"


class RuntimeLogFormatterTest(unittest.TestCase):
    def setUp(self):
        package = types.ModuleType("blender_pycharm")
        package.__path__ = [str(RUNTIME_PACKAGE)]
        environment = types.ModuleType("blender_pycharm.environment")
        environment.LOG_LEVEL = logging.DEBUG

        self.original_modules = {
            name: sys.modules.get(name)
            for name in ("blender_pycharm", "blender_pycharm.environment")
        }
        sys.modules.update({
            "blender_pycharm": package,
            "blender_pycharm.environment": environment,
        })
        self.runtime_log = importlib.import_module("blender_pycharm.log")

    def tearDown(self):
        sys.modules.pop("blender_pycharm.log", None)
        for name, module in self.original_modules.items():
            if module is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = module

    def test_formatter_matches_blender_style_without_ansi_codes(self):
        record = logging.LogRecord(
            name="blender_vs",
            level=logging.INFO,
            pathname="/runtime/installation.py",
            lineno=112,
            msg="module: debugpy is already installed",
            args=(),
            exc_info=None,
        )
        record.relativeCreated = 121

        rendered = self.runtime_log.BlenderFormatter().format(record)

        self.assertEqual(
            "00:00.121  blender.pycharm  | INFO: module: debugpy is already installed (installation.py:112)",
            rendered,
        )
        self.assertNotIn("\x1b", rendered)

    def test_formatter_rolls_elapsed_time_into_minutes(self):
        record = logging.LogRecord("blender_vs", logging.ERROR, "runtime.py", 7, "failed", (), None)
        record.relativeCreated = 61_005

        rendered = self.runtime_log.BlenderFormatter().format(record)

        self.assertTrue(rendered.startswith("01:01.005  blender.pycharm  | ERROR: failed"))


if __name__ == "__main__":
    unittest.main()
