import importlib
import logging
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, call


RUNTIME_PACKAGE = Path(__file__).parents[2] / "main/python"


class RuntimeCommunicationLoggingTest(unittest.TestCase):
    def setUp(self):
        self.queued_actions = []
        self.logger = Mock()

        package = types.ModuleType("blender_pycharm")
        package.__path__ = [str(RUNTIME_PACKAGE)]

        environment = types.ModuleType("blender_pycharm.environment")
        environment.LOG_FLASK = False
        environment.PYCHARM_IDENTIFIER = "pycharm-id"
        environment.VSCODE_IDENTIFIER = "runtime-id"
        environment.blender_path = Path("/blender")
        environment.python_path = Path("/python")
        environment.scripts_folder = Path("/scripts")

        runtime_log = types.ModuleType("blender_pycharm.log")
        runtime_log.getLogger = lambda: self.logger

        utils = types.ModuleType("blender_pycharm.utils")
        utils.run_in_main_thread = self.queued_actions.append

        flask = types.ModuleType("flask")
        flask.request = types.SimpleNamespace(get_json=lambda: {})

        class FakeFlask:
            def __init__(self, _name):
                self.logger = types.SimpleNamespace(setLevel=lambda _level: None)

            # noinspection method-may-be-static
            def route(self, *_args, **_kwargs):
                return lambda function: function

        flask.Flask = FakeFlask

        werkzeug_serving = types.ModuleType("werkzeug.serving")
        werkzeug_serving.make_server = Mock()

        modules = {
            "blender_pycharm": package,
            "blender_pycharm.environment": environment,
            "blender_pycharm.log": runtime_log,
            "blender_pycharm.utils": utils,
            "debugpy": types.ModuleType("debugpy"),
            "flask": flask,
            "requests": types.ModuleType("requests"),
            "werkzeug": types.ModuleType("werkzeug"),
            "werkzeug.serving": werkzeug_serving,
        }
        self.original_modules = {name: sys.modules.get(name) for name in modules}
        sys.modules.update(modules)
        self.communication = importlib.import_module("blender_pycharm.communication")

    def tearDown(self):
        sys.modules.pop("blender_pycharm.communication", None)
        for name, module in self.original_modules.items():
            if module is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = module

    def test_registered_action_logs_queue_execution_and_completion(self):
        handler = Mock()
        self.communication.register_post_action("reload", handler)

        response = self.communication.POST_HANDLERS["reload"]({"type": "reload"})
        self.queued_actions[0]()

        self.assertEqual("OK", response)
        handler.assert_called_once_with({"type": "reload"})
        self.logger.debug.assert_called_once_with("Queued runtime command on Blender's main thread: reload")
        self.logger.info.assert_any_call("Executing runtime command: reload")
        self.logger.info.assert_any_call("Runtime command completed: reload")

    def test_registered_action_logs_handler_failure(self):
        handler = Mock(side_effect=RuntimeError("reload failed"))
        self.communication.register_post_action("reload", handler)

        self.communication.POST_HANDLERS["reload"]({"type": "reload"})
        self.queued_actions[0]()

        self.logger.exception.assert_called_once_with("Runtime command failed: reload")
        self.assertNotIn(call("Runtime command completed: reload"), self.logger.info.call_args_list)

    def test_unknown_command_is_logged_and_rejected(self):
        sys.modules["flask"].request.get_json = lambda: {"type": "unknown"}

        response = self.communication.handle_post()

        self.assertEqual(("Unhandled runtime command", 400), response)
        self.logger.warning.assert_called_once_with("Unhandled runtime command payload: {'type': 'unknown'}")


if __name__ == "__main__":
    unittest.main()
