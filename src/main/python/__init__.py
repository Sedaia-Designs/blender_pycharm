import hashlib
import hmac
import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from pprint import pformat
from typing import List, Optional

import bpy

from . import environment, log

LOG = log.get_logger()
SIGNATURE_HEADER = "X-Blender-PyCharm-Signature"


# noinspection class-has-no-init
@dataclass
class AddonInfo:
    load_dir: Path
    module_name: str


def startup(
    editor_address=None,
    addons_to_load: List[AddonInfo] = None,
    wait_for_debugger: bool = True,
    project_root="",
    source_folder="",
    addon_symlink_name="",
):
    if bpy.app.version < (2, 80, 34):
        handle_fatal_error("Please use a newer version of Blender")

    if editor_address is None:
        LOG.info(
            "Phase 1 runtime bootstrap loaded (project_root=%s, source_folder=%s, addon_symlink_name=%s).",
            project_root,
            source_folder,
            addon_symlink_name,
        )
        return

    try:
        from . import installation

        # blender 2.80 'ssl' module is compiled with 'OpenSSL 1.1.0h' what breaks with requests >2.29.0
        try:
            installation.ensure_packages_are_installed(
                ["debugpy", "requests<=2.29.0", "werkzeug<=3.0.3", "flask<=3.0.3"]
            )
        except installation.DependencyInstallationError as error:
            _report_bootstrap_failure(
                editor_address=editor_address,
                identifier=_read_runtime_identifier(),
                message=f"Blender runtime dependency setup failed: {error}",
                details=repr(error),
                payload_type="dependencyFailure",
            )
            handle_fatal_error(f"Blender runtime dependency setup failed.\n{error}")

        from . import load_addons

        if addons_to_load is None:
            addons_to_load = []

        path_mappings = load_addons.setup_addon_links(addons_to_load)

        from . import communication

        communication.setup(editor_address, path_mappings, wait_for_debugger=wait_for_debugger)

        from . import operators, ui

        ui.register()
        operators.register()

        load_addons.load(addons_to_load)
    except Exception as error:
        _report_bootstrap_failure(
            editor_address=editor_address,
            identifier=_read_runtime_identifier(),
            message=f"Blender runtime bootstrap failed: {error}",
            details=repr(error),
            payload_type="bootstrapFailure",
        )
        raise


def handle_fatal_error(message):
    print()
    print("#" * 80)
    for line in message.splitlines():
        print(">  ", line)
    print("#" * 80)
    print(f"PATHONPATH: {pformat(sys.path)}")
    print()
    sys.exit(1)


def _report_bootstrap_failure(
    editor_address: Optional[str],
    identifier: str,
    message: str,
    details: str = "",
    payload_type: str = "bootstrapFailure",
):
    if not editor_address:
        return
    payload = {
        "type": payload_type,
        "identifier": identifier,
        "pycharmIdentifier": identifier,
        "message": message,
        "details": details,
    }
    body = json.dumps(payload).encode("utf-8")
    auth_key = environment.DECODED_PYCHARM_AUTHKEY
    if not isinstance(auth_key, bytes):
        LOG.error("Cannot authenticate runtime bootstrap failure report because the launch key is unavailable.")
        return
    signature = hmac.new(auth_key, body, hashlib.sha256).hexdigest()

    request = urllib.request.Request(
        editor_address,
        data=body,
        headers={
            "Content-Type": "application/json",
            SIGNATURE_HEADER: signature,
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=5):
            pass
    except (urllib.error.URLError, TimeoutError):
        LOG.exception("Failed to report runtime bootstrap failure to editor.")


def _read_runtime_identifier() -> str:
    return os.environ.get("BLENDER_PYCHARM_IDENTIFIER", "")
