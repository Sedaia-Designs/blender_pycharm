import sys
from pathlib import Path

import addon_utils
import bpy
from bpy.props import *  # noqa: F403

from .. import log
from ..communication import register_post_action, send_dict_as_json
from ..environment import EXTENSIONS_REPOSITORY
from ..load_addons import is_in_any_addon_directory
from ..utils import addon_has_bl_info, extension_manifest_id, is_addon_legacy, redraw_all

LOG = log.getLogger()


class UpdateAddonOperator(bpy.types.Operator):
    bl_idname = "dev.update_addon"
    bl_label = "Update Addon"

    module_name: StringProperty()  # noqa: F405
    module_dir: StringProperty(default="")  # noqa: F405

    @staticmethod
    def _is_namespace_package_error(error: Exception) -> bool:
        message = str(error)
        return "module loaded with no associated file" in message and "__path__=_NamespacePath" in message

    @staticmethod
    def _is_missing_module_error(error: Exception) -> bool:
        return "No module named" in str(error)

    @staticmethod
    def _as_extension_module_name(module_name: str) -> str:
        return "bl_ext." + EXTENSIONS_REPOSITORY + "." + module_name

    def _extension_candidates(self) -> list[str]:
        candidates: list[str] = []
        seen = set()

        def append(module_name: str):
            if not module_name:
                return
            if module_name in seen:
                return
            seen.add(module_name)
            candidates.append(module_name)

        module_name = self.module_name.strip()
        module_dir = Path(self.module_dir).resolve() if self.module_dir else None

        if module_name.startswith("bl_ext."):
            append(module_name)
        else:
            append(self._as_extension_module_name(module_name))

        if module_dir:
            manifest_id = extension_manifest_id(module_dir)
            if manifest_id:
                append(self._as_extension_module_name(manifest_id))

        # Resolve extension names from Blender extension repositories directly.
        # This is more reliable than folder-name guessing when manifest id differs.
        for repo in bpy.context.preferences.extensions.repos:
            if not repo.enabled:
                continue
            repo_module = repo.module
            if not repo_module:
                continue
            repo_dir = Path(repo.custom_directory if repo.use_custom_directory else repo.directory)
            if not repo_dir.exists() or not repo_dir.is_dir():
                continue
            for extension_dir in repo_dir.iterdir():
                if not extension_dir.is_dir():
                    continue
                if module_dir:
                    try:
                        if extension_dir.resolve() != module_dir:
                            continue
                    except OSError:
                        continue
                extension_id = extension_manifest_id(extension_dir) or extension_dir.name
                append(f"bl_ext.{repo_module}.{extension_id}")

        try:
            bpy.ops.extensions.repo_refresh_all()
        except Exception:
            pass

        # Keep a broad fallback from Blender's discovered module list.
        for addon_module in addon_utils.modules():
            discovered = addon_module.__name__
            if discovered.startswith("bl_ext."):
                append(discovered)

        return candidates

    @staticmethod
    def _refresh_before_enable(module_name: str):
        if module_name.startswith("bl_ext."):
            bpy.ops.extensions.repo_refresh_all()
        else:
            bpy.ops.preferences.addon_refresh()

    def execute(self, context):
        LOG.info(f"Reloading add-on module: {self.module_name} ({self.module_dir or 'directory not provided'})")
        try:
            bpy.ops.preferences.addon_disable(module=self.module_name)
        except Exception:
            LOG.exception(f"Failed to disable add-on module before reload: {self.module_name}")
            send_dict_as_json({"type": "disableFailure"})
            return {"CANCELLED"}

        for name in list(sys.modules.keys()):
            if name == self.module_name or name.startswith(self.module_name + "."):
                del sys.modules[name]

        try:
            self._refresh_before_enable(self.module_name)
            bpy.ops.preferences.addon_enable(module=self.module_name)
        except Exception as e:
            if not (self._is_namespace_package_error(e) or self._is_missing_module_error(e)):
                LOG.exception(f"Failed to enable reloaded add-on module: {self.module_name}")
                send_dict_as_json({"type": "enableFailure"})
                return {"CANCELLED"}

            fallback_errors = []
            for fallback_module in self._extension_candidates():
                for name in list(sys.modules.keys()):
                    if name == fallback_module or name.startswith(fallback_module + "."):
                        del sys.modules[name]
                try:
                    self._refresh_before_enable(fallback_module)
                    bpy.ops.preferences.addon_enable(module=fallback_module)
                    break
                except Exception as fallback_error:
                    fallback_errors.append(fallback_error)
            else:
                if fallback_errors:
                    last_error = fallback_errors[-1]
                    LOG.error(
                        f"Failed to enable add-on module `{self.module_name}` using all extension candidates.",
                        exc_info=(type(last_error), last_error, last_error.__traceback__),
                    )
                send_dict_as_json({"type": "enableFailure"})
                return {"CANCELLED"}

        send_dict_as_json({"type": "addonUpdated"})
        LOG.info(f"Reloaded add-on module successfully: {self.module_name}")

        redraw_all()
        return {"FINISHED"}


def reload_addon_action(data):
    targets = []

    def is_addon_root(path: Path) -> bool:
        return (path / "__init__.py").is_file() or (path / "blender_manifest.toml").is_file()

    def append_target(base_name: str, addon_dir: Path):
        if is_addon_legacy(addon_dir):
            targets.append((base_name, addon_dir))
            return
        if addon_has_bl_info(addon_dir) and is_in_any_addon_directory(addon_dir):
            # this addon is compatible with legacy addons and extensions
            # but user is developing it in addon directory. Treat it as addon.
            targets.append((base_name, addon_dir))
            return
        extension_id = extension_manifest_id(addon_dir) or addon_dir.name or base_name
        targets.append(("bl_ext." + EXTENSIONS_REPOSITORY + "." + extension_id, addon_dir))

    for name, dir in zip(data["names"], data["dirs"]):
        requested_dir = Path(dir).resolve()
        if not requested_dir.exists() or not requested_dir.is_dir():
            LOG.warning(f"Skipping reload target because its directory is unavailable: {name} ({requested_dir})")
            continue
        if is_addon_root(requested_dir):
            append_target(name, requested_dir)
            continue
        for child in requested_dir.iterdir():
            if child.is_dir() and is_addon_root(child):
                append_target(child.name, child.resolve())

    LOG.info(f"Resolved {len(targets)} add-on target(s) for reload.")
    for name, addon_dir in targets:
        bpy.ops.dev.update_addon(module_name=name, module_dir=str(addon_dir))


def register():
    bpy.utils.register_class(UpdateAddonOperator)
    register_post_action("reload", reload_addon_action)
