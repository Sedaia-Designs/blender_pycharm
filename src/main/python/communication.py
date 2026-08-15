import hashlib
import hmac
import json
import logging
import random
import threading
import time
from typing import Callable, Dict, Optional

import debugpy
import flask
import requests
from werkzeug.serving import make_server

from . import log
from .environment import (
    DECODED_PYCHARM_AUTHKEY,
    LOG_FLASK,
    PYCHARM_IDENTIFIER,
    blender_path,
    python_path,
    scripts_folder,
)
from .utils import run_in_main_thread

LOG = log.getLogger()

EDITOR_ADDRESS = None
OWN_SERVER_PORT = None
DEBUGPY_PORT = None

SERVER = flask.Flask("Blender Server")
SERVER.logger.setLevel(logging.DEBUG if LOG_FLASK else logging.ERROR)
POST_HANDLERS = {}
REQUEST_TIMEOUT_SECONDS = 5
SIGNATURE_HEADER = "X-Blender-PyCharm-Signature"


def setup(address: str, path_mappings, wait_for_debugger: bool = True):
    global EDITOR_ADDRESS, OWN_SERVER_PORT, DEBUGPY_PORT
    EDITOR_ADDRESS = address

    OWN_SERVER_PORT = start_own_server()
    DEBUGPY_PORT = start_debugpy_server()

    send_connection_information(path_mappings)

    if wait_for_debugger:
        LOG.info("Waiting for debug client.")
        debugpy.wait_for_client()
        LOG.info("Debug client attached.")


def start_own_server():
    server_started = threading.Event()
    startup_failed = threading.Event()
    result = {"port": None, "exception": None}

    def server_thread_function():
        for _attempt in range(10):
            port = get_random_port()
            try:
                httpd = make_server("127.0.0.1", port, SERVER)

                # Startup was successful — signal and continue
                result["port"] = port
                server_started.set()

                # Blocks here. If it fails the error remains unhandled.
                httpd.serve_forever()
                return
            except OSError as e:
                # retry on port conflicts, etc
                LOG.info(f"Port {port} failed with OSError, retrying... ({e})")
                continue
            except Exception as e:
                LOG.error(f"Unexpected error starting server on port {port}: {e}")
                result["exception"] = e
                startup_failed.set()
                return
        # If loop exhausted without success and without unexpected exception
        LOG.exception("Failed to start server after 10 attempts.")
        startup_failed.set()

    thread = threading.Thread(target=server_thread_function, daemon=True)
    thread.start()

    timeout = 15  # seconds
    deadline = time.time() + timeout
    # Wait for either success or failure
    while time.time() < deadline:
        if server_started.is_set():
            LOG.debug(f"Flask server started on port {result['port']}")
            return result["port"]
        if startup_failed.is_set():
            raise RuntimeError("Failed to start Flask server. See logs for details.") from result["exception"]
        time.sleep(0.07)
    raise TimeoutError(f"Flask server did not start within {timeout} seconds.")


def start_debugpy_server():
    # retry on port conflicts, todo catch only specific exceptions
    # note debugpy changed exception types between versions, todo investigate
    last_exception = None
    for _attempt in range(15):
        port = get_random_port()
        last_exception = None
        try:
            # for < 2.92 support (debugpy has problems when using bpy.app.binary_path_python)
            # https://github.com/microsoft/debugpy/issues/1330
            debugpy.configure(python=str(python_path))
            debugpy.listen(("localhost", port))
            return port
        except Exception as e:
            LOG.warning(f"Debugpy failed to start on port {port}: {e}")
            last_exception = e
    raise RuntimeError("Failed to start debugpy after 15 attempts.") from last_exception

# TODO: Write a new pydev backend for the debug server to make plugin compatible with older Pycharm versions
def start_pydev_server():
    pass

# Server
#########################################


@SERVER.route("/", methods=["POST"])
def handle_post():
    request_body = flask.request.get_data(cache=True)
    signature = flask.request.headers.get(SIGNATURE_HEADER)
    if not is_request_secure(signature, request_body):
        LOG.warning("Rejected runtime command with an invalid authentication signature.")
        return "Invalid authentication signature", 401

    data = flask.request.get_json()
    command_type = data.get("type") if isinstance(data, dict) else None
    LOG.info(f"Received runtime command: {command_type or '<missing>'}")

    if command_type in POST_HANDLERS:
        return POST_HANDLERS[command_type](data)

    LOG.warning(f"Unhandled runtime command payload: {data}")
    return "Unhandled runtime command", 400


@SERVER.route("/ping", methods=["GET"])
def handle_get_ping():
    LOG.debug("Got ping")
    return "OK"


def register_post_handler(type: str, handler: Callable):
    assert type not in POST_HANDLERS, POST_HANDLERS
    POST_HANDLERS[type] = handler


def register_post_action(type: str, handler: Callable):
    def request_handler_wrapper(data):
        def logged_action():
            LOG.info(f"Executing runtime command: {type}")
            try:
                handler(data)
            except Exception:
                LOG.exception(f"Runtime command failed: {type}")
                return
            LOG.info(f"Runtime command completed: {type}")

        run_in_main_thread(logged_action)
        LOG.debug(f"Queued runtime command on Blender's main thread: {type}")
        return "OK"

    register_post_handler(type, request_handler_wrapper)


# Sending Format
###############################


def send_connection_information(path_mappings: Dict):
    send_dict_as_json(
        {
            "type": "setup",
            "blenderPort": OWN_SERVER_PORT,
            "debugpyPort": DEBUGPY_PORT,
            "debugProtocol": "debugpy-dap",
            "blenderPath": str(blender_path),
            "scriptsFolder": str(scripts_folder),
            "pathMappings": path_mappings,
            "addonPathMappings": path_mappings,
            "identifier": PYCHARM_IDENTIFIER,
        }
    )


def send_dict_as_json(data):
    LOG.debug(f"Sending: {data}")

    body = json.dumps(data, separators=(",", ":"))
    signature = get_post_hmac(body.encode("utf-8"))

    requests.post(
        EDITOR_ADDRESS,
        data=body,
        headers={
            "Content-Type": "application/json",
            SIGNATURE_HEADER: signature
        },
        timeout=REQUEST_TIMEOUT_SECONDS
    )


# Utils
###############################

def get_post_hmac(message: bytes) -> str:
    if DECODED_PYCHARM_AUTHKEY is None:
        raise RuntimeError("Runtime authentication key is unavailable.")
    return hmac.new(DECODED_PYCHARM_AUTHKEY, message, hashlib.sha256).hexdigest()


def is_request_secure(hmac_header: Optional[str], message: bytes) -> bool:
    """Return whether ``hmac_header`` authenticates the exact request body.

    This matches BlenderAuthentication.authenticateMessage(), which generates a
    hexadecimal HMAC-SHA-256 digest using the shared 32-byte authentication key.
    """
    if DECODED_PYCHARM_AUTHKEY is None or hmac_header is None:
        return False

    try:
        received_signature = hmac_header.encode("ascii")
    except UnicodeEncodeError:
        return False

    expected_signature = get_post_hmac(message).encode("ascii")
    return hmac.compare_digest(expected_signature, received_signature)


def get_random_port():
    """
    Generate a random port number within the dynamic/private port range.

    This function generates a random port number between 49152 and 65535,
    inclusive. These ports are typically used for dynamic or ephemeral
    port assignments.

    :return: A randomly generated port number within the range of 49152 to 65535.
    :rtype: int
    """
    return random.randint(49152, 65535)


def get_blender_port():
    """
    Retrieve the port number used by the Blender server.

    This function provides the port number assigned to the Blender server,
    allowing for communication and configuration with the server.

    :return: The port number assigned to the Blender server.
    :rtype: int
    """
    return OWN_SERVER_PORT


def get_debugpy_port():
    """
    Retrieves the port number used for the debugpy debugging tool.

    This function is used to access the port number configured for
    debugpy, which facilitates interaction with the debugger during
    runtime.

    :return: The port number currently set for debugpy.
    :rtype: int
    """
    return DEBUGPY_PORT


def get_editor_address():
    """
    Retrieve the address of the editor.

    This function provides the current editor's address, which is stored in a
    constant.

    :return: The address of the editor.
    :rtype: str
    """
    return EDITOR_ADDRESS
