import json
import os
import socket
import sys
import threading
import time
import traceback

import bpy


CONFIG_ENV = "BLENDER_PYCHARM_CONFIG"
CONFIG_JSON_ENV = "BLENDER_PYCHARM_CONFIG_JSON"


def _nested_get(data, path, default=None):
    current = data
    for key in path:
        if not isinstance(current, dict) or key not in current:
            return default
        current = current[key]
    return current


def _nested_set(data, path, value):
    current = data
    for key in path[:-1]:
        current = current.setdefault(key, {})
    current[path[-1]] = value


def _env_bool(name):
    value = os.environ.get(name)
    if value is None:
        return None
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _env_int(name):
    value = os.environ.get(name)
    if value is None or value.strip() == "":
        return None
    return int(value)


def _env_float(name):
    value = os.environ.get(name)
    if value is None or value.strip() == "":
        return None
    return float(value)


def _apply_environment_overrides(config):
    overrides = {
        "BLENDER_PYCHARM_REPO_NAME": (("repository", "name"), os.environ.get),
        "BLENDER_PYCHARM_REPO_PATH": (("repository", "path"), os.environ.get),
        "BLENDER_PYCHARM_EXTENSION_NAME": (("extension", "name"), os.environ.get),
        "BLENDER_PYCHARM_SERVER_HOST": (("reload", "server"), os.environ.get),
        "BLENDER_PYCHARM_RELOAD_PORT": (("reload", "port"), _env_int),
        "BLENDER_PYCHARM_MAX_RETRIES": (("reload", "max_retries"), _env_int),
        "BLENDER_PYCHARM_RETRY_DELAY_SECONDS": (("reload", "retry_delay_seconds"), _env_float),
        "BLENDER_PYCHARM_DEBUG_ENABLED": (("debugpy", "enabled"), _env_bool),
        "BLENDER_PYCHARM_DEBUG_HOST": (("debugpy", "host"), os.environ.get),
        "BLENDER_PYCHARM_DEBUG_PORT": (("debugpy", "port"), _env_int),
        "BLENDER_PYCHARM_PRINT_ENVIRONMENT": (("logging", "print_environment"), _env_bool),
    }

    for env_name, (path, loader) in overrides.items():
        try:
            value = loader(env_name)
        except ValueError as exc:
            print(f"Ignoring invalid {env_name}: {exc}")
            continue
        if value is not None:
            _nested_set(config, path, value)


def _load_config():
    inline_config = os.environ.get(CONFIG_JSON_ENV)
    if inline_config:
        config = json.loads(inline_config)
    else:
        config_path = os.environ.get(CONFIG_ENV)
        if not config_path:
            raise RuntimeError(f"Missing {CONFIG_ENV} environment variable.")
        with open(config_path, "r", encoding="utf-8") as config_file:
            config = json.load(config_file)

    _apply_environment_overrides(config)
    return config


def _module_name(config, extension_name=None):
    repo_name = _nested_get(config, ("repository", "name"), "blender_pycharm")
    extension = extension_name or _nested_get(config, ("extension", "name"))
    if not extension:
        return None
    return f"bl_ext.{repo_name}.{extension}"


def _print_environment(config):
    if not _nested_get(config, ("logging", "print_environment"), True):
        return

    print("--- Blender Python Environment ---")
    print(f"Python Version: {sys.version}")
    print(f"Executable: {sys.executable}")
    print(f"Path: {sys.path}")
    print("----------------------------------")


def _start_debugpy(config):
    if not _nested_get(config, ("debugpy", "enabled"), False):
        return

    host = _nested_get(config, ("debugpy", "host"), "127.0.0.1")
    port = int(_nested_get(config, ("debugpy", "port"), 5678))
    try:
        import debugpy

        debugpy.listen((host, port))
        print(f"debugpy listening on {host}:{port}")
    except Exception as exc:
        print(f"Failed to start debugpy on {host}:{port}: {exc}")


def ensure_extension_repo_exists(config):
    repo_name = _nested_get(config, ("repository", "name"), "blender_pycharm")
    repo_path = _nested_get(config, ("repository", "path"))
    if bpy.app.version < (4, 2, 0):
        return
    if not repo_path or not os.path.exists(repo_path):
        return

    repo_path = os.path.normpath(repo_path)
    existing_repo = None
    for repo in bpy.context.preferences.extensions.repos:
        if getattr(repo, "module", None) == repo_name:
            existing_repo = repo
            break

    if existing_repo:
        try:
            existing_repo.enabled = True
        except Exception:
            pass

        current_path = getattr(existing_repo, "directory", getattr(existing_repo, "path", None))
        if current_path and os.path.normpath(current_path) == repo_path:
            print(f"Extension repo '{repo_name}' is already configured.")
            return

        print(f"Repo '{repo_name}' points to {current_path}. Reconfiguring it for {repo_path}.")
        try:
            bpy.context.preferences.extensions.repos.remove(existing_repo)
        except Exception as exc:
            print(f"Failed to remove existing repo: {exc}")
            try:
                if hasattr(existing_repo, "directory"):
                    existing_repo.directory = repo_path
                    return
                if hasattr(existing_repo, "path"):
                    existing_repo.path = repo_path
                    return
            except Exception as fallback_exc:
                print(f"Fallback repo path update failed: {fallback_exc}")

    try:
        if hasattr(bpy.ops.preferences, "extension_repo_add"):
            bpy.ops.preferences.extension_repo_add(
                name=repo_name,
                type="LOCAL",
                custom_directory=repo_path,
                use_custom_directory=True,
            )
            print(f"Added extensions repository: {repo_name} -> {repo_path}")
        elif hasattr(bpy.ops.extensions, "repo_add"):
            bpy.ops.extensions.repo_add(name=repo_name, type="LOCAL", directory=repo_path)
            print(f"Added extensions repository: {repo_name} -> {repo_path}")
        else:
            new_repo = bpy.context.preferences.extensions.repos.new(name=repo_name, module=repo_name)
            if hasattr(new_repo, "directory"):
                new_repo.directory = repo_path
            elif hasattr(new_repo, "path"):
                new_repo.path = repo_path
            new_repo.enabled = True
            print(f"Created extensions repository: {repo_name} -> {repo_path}")
    except Exception as exc:
        print(f"Failed to create extensions repository: {exc}")
        traceback.print_exc()


def ensure_extension_enabled(config, extension_name=None):
    module_name = _module_name(config, extension_name)
    if not module_name:
        return None

    if module_name not in bpy.context.preferences.addons:
        print(f"Automatically enabling extension: {module_name}")
        try:
            if hasattr(bpy.ops.extensions, "repo_refresh_all"):
                bpy.ops.extensions.repo_refresh_all()
            bpy.ops.preferences.addon_enable(module=module_name)
        except Exception as exc:
            print(f"Failed to auto-enable {module_name}: {exc}")
            traceback.print_exc()
    return None


def _reload_extension(config, extension_name):
    module_name = _module_name(config, extension_name)
    if not module_name:
        print("Reload command did not include an extension name.")
        return None

    try:
        if module_name in bpy.context.preferences.addons:
            bpy.ops.preferences.addon_disable(module=module_name)

        if hasattr(bpy.ops.extensions, "repo_refresh_all"):
            bpy.ops.extensions.repo_refresh_all()

        for module in list(sys.modules.keys()):
            if module == module_name or module.startswith(module_name + "."):
                del sys.modules[module]

        bpy.ops.preferences.addon_enable(module=module_name)
        print(f"Successfully reloaded extension: {module_name}")
    except Exception as exc:
        print(f"Error during reload of {module_name}: {exc}")
        traceback.print_exc()

    return None


def _run_on_main_thread(callback):
    if hasattr(bpy.app, "timers"):
        bpy.app.timers.register(callback)
    else:
        callback()


def listen_for_reload(config):
    host = _nested_get(config, ("reload", "server"), "127.0.0.1")
    port = int(_nested_get(config, ("reload", "port")))
    max_retries = int(_nested_get(config, ("reload", "max_retries"), 5))
    retry_delay = float(_nested_get(config, ("reload", "retry_delay_seconds"), 1.0))

    connection = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    connected = False
    for retry_count in range(1, max_retries + 1):
        try:
            connection.connect((host, port))
            connected = True
            break
        except Exception as exc:
            print(f"Connection attempt {retry_count} to {host}:{port} failed: {exc}")
            time.sleep(retry_delay)

    if not connected:
        print(f"Failed to connect to IntelliJ after {max_retries} attempts.")
        connection.close()
        return

    try:
        ready_message = {"type": "ready", "server": host, "port": port}
        connection.sendall(json.dumps(ready_message).encode("utf-8") + b"\n")
        print(f"Connected to IntelliJ for extension reloading on {host}:{port}")

        buffer = ""
        while True:
            data = connection.recv(4096)
            if not data:
                break
            buffer += data.decode("utf-8")
            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                if not line.strip():
                    continue
                try:
                    message = json.loads(line)
                    if message.get("type") == "reload":
                        extension_name = message.get("name")
                        print(f"Received reload command for: {extension_name}")
                        _run_on_main_thread(lambda: _reload_extension(config, extension_name))
                except Exception as exc:
                    print(f"Error parsing reload message: {exc}")
                    traceback.print_exc()
    except Exception as exc:
        print(f"Error in listen_for_reload: {exc}")
        traceback.print_exc()
    finally:
        connection.close()


def main():
    config = _load_config()
    _print_environment(config)
    _start_debugpy(config)
    ensure_extension_repo_exists(config)

    extension_name = _nested_get(config, ("extension", "name"))
    if extension_name:
        _run_on_main_thread(lambda: ensure_extension_enabled(config, extension_name))

    thread = threading.Thread(target=listen_for_reload, args=(config,), daemon=True)
    thread.start()


main()
