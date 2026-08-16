import hashlib
import hmac
import importlib
import json
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
        environment.DECODED_PYCHARM_AUTHKEY = b"a" * 32
        environment.ENCODED_PYCHARM_AUTHKEY = "encoded-auth-key"
        environment.PYCHARM_IDENTIFIER = "pycharm-id"
        environment.VSCODE_IDENTIFIER = "runtime-id"
        environment.blender_path = Path("/blender")
        environment.python_path = Path("/python")
        environment.scripts_folder = Path("/scripts")

        runtime_log = types.ModuleType("blender_pycharm.log")
        runtime_log.get_logger = lambda: self.logger

        utils = types.ModuleType("blender_pycharm.utils")
        utils.run_in_main_thread = self.queued_actions.append

        flask = types.ModuleType("flask")
        flask.request = types.SimpleNamespace(
            is_json=True,
            content_length=len(b'{"type":"unknown"}'),
            get_data=lambda cache: b'{"type":"unknown"}',
            get_json=lambda: {},
            headers={},
        )

        class FakeFlask:
            def __init__(self, _name):
                self.config = {}
                self.logger = types.SimpleNamespace(setLevel=lambda _level: None)

            # noinspection method-may-be-static
            def route(self, *_args, **_kwargs):
                return lambda function: function

        flask.Flask = FakeFlask

        werkzeug_serving = types.ModuleType("werkzeug.serving")
        werkzeug_serving.make_server = Mock()

        requests = types.ModuleType("requests")
        requests.post = Mock()

        modules = {
            "blender_pycharm": package,
            "blender_pycharm.environment": environment,
            "blender_pycharm.log": runtime_log,
            "blender_pycharm.utils": utils,
            "debugpy": types.ModuleType("debugpy"),
            "flask": flask,
            "requests": requests,
            "werkzeug": types.ModuleType("werkzeug"),
            "werkzeug.serving": werkzeug_serving,
        }
        self.original_modules = {name: sys.modules.get(name) for name in modules}
        sys.modules.update(modules)
        self.communication = importlib.import_module("blender_pycharm.communication")
        self.requests_post = requests.post

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

    def test_valid_command_without_registered_handler_is_unavailable(self):
        request_body = b'{"type":"reload"}'
        signature = hmac.new(
            self.communication.DECODED_PYCHARM_AUTHKEY,
            request_body,
            hashlib.sha256,
        ).hexdigest()
        sys.modules["flask"].request = types.SimpleNamespace(
            is_json=True,
            content_length=len(request_body),
            get_data=lambda cache: request_body,
            get_json=lambda: {"type": "reload"},
            headers={self.communication.SIGNATURE_HEADER: signature},
        )

        response = self.communication.handle_post()

        self.assertEqual(("Runtime command unavailable", 503), response)

    def test_non_json_content_type_is_rejected_before_authentication(self):
        sys.modules["flask"].request = types.SimpleNamespace(is_json=False)

        response = self.communication.handle_post()

        self.assertEqual(("Content-Type must be application/json", 415), response)

    def test_unknown_command_is_logged_and_rejected(self):
        request_body = b'{"type":"unknown"}'
        signature = hmac.new(
            self.communication.DECODED_PYCHARM_AUTHKEY,
            request_body,
            hashlib.sha256,
        ).hexdigest()
        sys.modules["flask"].request = types.SimpleNamespace(
            is_json=True,
            content_length=len(request_body),
            get_data=lambda cache: request_body,
            get_json=lambda: {"type": "unknown"},
            headers={self.communication.SIGNATURE_HEADER: signature},
        )

        response = self.communication.handle_post()

        self.assertEqual(("Unsupported runtime command", 400), response)
        self.logger.warning.assert_called_once_with("Rejected unsupported runtime command: unknown")

    def test_request_security_accepts_only_the_matching_signature(self):
        request_body = b'{"type":"reload"}'
        signature = hmac.new(
            self.communication.DECODED_PYCHARM_AUTHKEY,
            request_body,
            hashlib.sha256,
        ).hexdigest()

        self.assertTrue(self.communication.is_request_secure(signature, request_body))
        self.assertFalse(self.communication.is_request_secure(signature, b'{"type":"stop"}'))
        self.assertFalse(self.communication.is_request_secure("not-a-signature", request_body))

    def test_request_security_rejects_missing_key_and_signature(self):
        request_body = b'{"type":"reload"}'
        signature = hmac.new(b"a" * 32, request_body, hashlib.sha256).hexdigest()

        self.assertFalse(self.communication.is_request_secure(None, request_body))
        self.communication.DECODED_PYCHARM_AUTHKEY = None
        self.assertFalse(self.communication.is_request_secure(signature, request_body))

    def test_request_security_rejects_non_ascii_signature(self):
        self.assertFalse(self.communication.is_request_secure("\N{LOCK}", b'{"type":"reload"}'))

    def test_post_hmac_matches_sha256_hexdigest(self):
        request_body = b'{"type":"setup","identifier":"pycharm-id"}'
        expected = hmac.new(b"a" * 32, request_body, hashlib.sha256).hexdigest()

        self.assertEqual(expected, self.communication.get_post_hmac(request_body))

    def test_send_dict_as_json_signs_the_exact_compact_body(self):
        self.communication.EDITOR_ADDRESS = "http://127.0.0.1:12345/"
        payload = {
            "type": "setup",
            "identifier": "pycharm-id",
            "pathMappings": [{"src": "/project", "load": "/runtime"}],
        }
        expected_body = json.dumps(payload, separators=(",", ":"))
        expected_signature = hmac.new(
            b"a" * 32,
            expected_body.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()

        self.communication.send_dict_as_json(payload)

        self.requests_post.assert_called_once_with(
            "http://127.0.0.1:12345/",
            data=expected_body,
            headers={
                "Content-Type": "application/json",
                self.communication.SIGNATURE_HEADER: expected_signature,
            },
            timeout=self.communication.REQUEST_TIMEOUT_SECONDS,
        )

    def test_send_connection_information_builds_expected_setup_payload(self):
        self.communication.OWN_SERVER_PORT = 51234
        self.communication.DEBUGPY_PORT = 56789
        self.communication.send_dict_as_json = Mock()
        path_mappings = [{"src": "/project", "load": "/runtime"}]

        self.communication.send_connection_information(path_mappings)

        self.communication.send_dict_as_json.assert_called_once_with(
            {
                "type": "setup",
                "blenderPort": 51234,
                "debugpyPort": 56789,
                "debugProtocol": "debugpy-dap",
                "blenderPath": "/blender",
                "scriptsFolder": "/scripts",
                "pathMappings": path_mappings,
                "addonPathMappings": path_mappings,
                "identifier": "pycharm-id",
            }
        )

    def test_handle_post_rejects_an_invalid_signature(self):
        sys.modules["flask"].request = types.SimpleNamespace(
            is_json=True,
            content_length=len(b'{"type":"reload"}'),
            get_data=lambda cache: b'{"type":"reload"}',
            get_json=lambda: {"type": "reload"},
            headers={self.communication.SIGNATURE_HEADER: "invalid"},
        )

        response = self.communication.handle_post()

        self.assertEqual(("Invalid authentication signature", 401), response)

    def test_handle_post_rejects_a_missing_signature(self):
        sys.modules["flask"].request = types.SimpleNamespace(
            is_json=True,
            content_length=len(b'{"type":"reload"}'),
            get_data=lambda cache: b'{"type":"reload"}',
            get_json=lambda: {"type": "reload"},
            headers={},
        )

        response = self.communication.handle_post()

        self.assertEqual(("Invalid authentication signature", 401), response)


if __name__ == "__main__":
    unittest.main()
