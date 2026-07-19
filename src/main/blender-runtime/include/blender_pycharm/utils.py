import ast
from pathlib import Path
import bpy
import queue
import traceback
import tomllib


def is_addon_legacy(addon_dir: Path) -> bool:
    """Return whether an addon uses the legacy bl_info behavior, or the new blender_manifest behavior"""
    if bpy.app.version < (4, 2, 0):
        return True
    preferences = getattr(bpy.context, "preferences", None)
    if not hasattr(preferences, "extensions"):
        # Guard for compatibility with builds where extension APIs are unavailable.
        return True
    if not (addon_dir / "blender_manifest.toml").exists():
        return True
    return False


def addon_has_bl_info(addon_dir: Path) -> bool:
    """Perform best effort check to find bl_info. Does not perform an import on file to avoid code execution."""
    init_file = addon_dir / "__init__.py"
    if not init_file.exists():
        return False
    try:
        with open(init_file) as init_addon_file:
            node = ast.parse(init_addon_file.read())
    except (OSError, SyntaxError):
        return False
    for element in node.body:
        if not isinstance(element, ast.Assign):
            continue
        for target in element.targets:
            if not isinstance(target, ast.Name):
                continue
            if target.id == "bl_info":
                return True
    return False


def extension_manifest_id(addon_dir: Path) -> str:
    manifest_path = addon_dir / "blender_manifest.toml"
    if not manifest_path.exists():
        return ""
    try:
        with open(manifest_path, "rb") as manifest_file:
            manifest_data = tomllib.load(manifest_file)
    except (OSError, tomllib.TOMLDecodeError):
        return ""
    extension_id = manifest_data.get("id", "")
    if isinstance(extension_id, str):
        return extension_id.strip()
    return ""


def redraw_all():
    for window in bpy.context.window_manager.windows:
        for area in window.screen.areas:
            area.tag_redraw()


def get_prefixes(all_names, separator):
    return set(name.split(separator)[0] for name in all_names if separator in name)


execution_queue = queue.Queue()


def run_in_main_thread(func):
    execution_queue.put(func)


def always():
    while not execution_queue.empty():
        func = execution_queue.get()
        try:
            func()
        except Exception:
            traceback.print_exc()
    return 0.1


bpy.app.timers.register(always, persistent=True)
