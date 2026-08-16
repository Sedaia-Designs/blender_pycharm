import hashlib
import hmac
import importlib
import json
import sys
import types
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import Mock, patch

RUNTIME_PACKAGE = Path(__file__).parents[2] / "main/python"


class RuntimeBootstrapProtocolTest(unittest.TestCase):
    def setUp(self):
        package = types.ModuleType("blender_pycharm")
        package.__path__ = [str(RUNTIME_PACKAGE)]

        self.logger = Mock()
        environment = types.ModuleType("blender_pycharm.environment")
        environment.DECODED_PYCHARM_AUTHKEY = b"a" * 32
        runtime_log = types.ModuleType("blender_pycharm.log")
        runtime_log.get_logger = lambda: self.logger
        bpy = types.ModuleType("bpy")
        bpy.app = types.SimpleNamespace(version=(4, 5, 0))

        modules = {
            "blender_pycharm": package,
            "blender_pycharm.environment": environment,
            "blender_pycharm.log": runtime_log,
            "bpy": bpy,
        }
        self.original_modules = {name: sys.modules.get(name) for name in modules}
        sys.modules.update(modules)
        self.runtime = importlib.import_module("blender_pycharm.__init__")

    def tearDown(self):
        sys.modules.pop("blender_pycharm.__init__", None)
        for name, module in self.original_modules.items():
            if module is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = module

    def test_both_failure_types_sign_exact_transmitted_unicode_bytes(self):
        for failure_type in ("bootstrapFailure", "dependencyFailure"):
            with self.subTest(failure_type=failure_type):
                captured_request = []

                def open_request(request, timeout):
                    captured_request.append((request, timeout))
                    return _ResponseContext()

                with patch("urllib.request.urlopen", side_effect=open_request):
                    self.runtime._report_bootstrap_failure(
                        "http://127.0.0.1:12345/",
                        "session-日本語",
                        "Bootstrap failed: café",
                        "Détails ☃",
                        failure_type,
                    )

                request, timeout = captured_request[0]
                expected_signature = hmac.new(b"a" * 32, request.data, hashlib.sha256).hexdigest()
                self.assertEqual(5, timeout)
                signature_header = next(
                    value
                    for name, value in request.header_items()
                    if name.casefold() == self.runtime.SIGNATURE_HEADER.casefold()
                )
                self.assertEqual(expected_signature, signature_header)
                self.assertEqual("application/json", request.get_header("Content-type"))
                self.assertEqual(failure_type, json.loads(request.data)["type"])
                self.assertEqual("Bootstrap failed: café", json.loads(request.data)["message"])

    def test_missing_key_fails_safely_without_transmitting(self):
        self.runtime.environment.DECODED_PYCHARM_AUTHKEY = None

        with patch("urllib.request.urlopen") as urlopen:
            self.runtime._report_bootstrap_failure(
                "http://127.0.0.1:12345/", "session", "failed"
            )

        urlopen.assert_not_called()
        self.logger.error.assert_called_once()

    def test_malformed_key_fails_safely_without_transmitting(self):
        self.runtime.environment.DECODED_PYCHARM_AUTHKEY = "not-bytes"

        with patch("urllib.request.urlopen") as urlopen:
            self.runtime._report_bootstrap_failure(
                "http://127.0.0.1:12345/", "session", "failed"
            )

        urlopen.assert_not_called()
        self.logger.error.assert_called_once()

    def test_setup_failures_and_commands_share_header_and_hmac_contract(self):
        communication_source = (RUNTIME_PACKAGE / "communication.py").read_text()
        setup_payload = {"type": "setup", "identifier": "session"}
        command_payload = {"type": "reload"}

        for payload in (setup_payload, command_payload):
            body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
            signature = hmac.new(b"a" * 32, body, hashlib.sha256).hexdigest()
            self.assertEqual(64, len(signature))

        self.assertEqual("X-Blender-PyCharm-Signature", self.runtime.SIGNATURE_HEADER)
        self.assertIn('SIGNATURE_HEADER = "X-Blender-PyCharm-Signature"', communication_source)
        self.assertIn("hmac.new(DECODED_PYCHARM_AUTHKEY, message, hashlib.sha256).hexdigest()", communication_source)


# noinspection class-has-no-init
class _ResponseContext:
    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False


if __name__ == "__main__":
    unittest.main()
