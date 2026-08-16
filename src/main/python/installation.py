import subprocess
import sys
from pathlib import Path

import bpy

from . import log
from .environment import python_path

LOG = log.get_logger()
_CWD_FOR_SUBPROCESSES = python_path.parent


class DependencyInstallationError(RuntimeError):
    pass


def ensure_packages_are_installed(package_names):
    if packages_are_installed(package_names):
        return

    install_packages(package_names)


def packages_are_installed(package_names):
    return all(module_can_be_imported(name) for name in package_names)


def install_packages(package_names):
    if not module_can_be_imported("pip"):
        install_pip()

    for name in package_names:
        ensure_package_is_installed(name)

    assert packages_are_installed(package_names)


def ensure_package_is_installed(name: str):
    if not module_can_be_imported(name):
        install_package(name)


def install_package(name: str):
    target = get_package_install_directory()
    command = [str(python_path), "-m", "pip", "install", name, "--target", target]
    LOG.info(f"Execute: {' '.join(command)}")
    result = subprocess.run(command, cwd=_CWD_FOR_SUBPROCESSES, capture_output=True, text=True)
    if result.returncode != 0:
        raise DependencyInstallationError(
            _build_command_failure_message(
                package_name=name,
                command=command,
                return_code=result.returncode,
                stdout=result.stdout,
                stderr=result.stderr,
            )
        )

    if not module_can_be_imported(name):
        raise DependencyInstallationError(f"Package install finished but module is still unavailable: {name}")


def install_pip():
    # try ensurepip before get-pip.py
    if module_can_be_imported("ensurepip"):
        command = [str(python_path), "-m", "ensurepip", "--upgrade"]
        LOG.info(f"Execute: {' '.join(command)}")
        result = subprocess.run(command, cwd=_CWD_FOR_SUBPROCESSES, capture_output=True, text=True)
        if result.returncode != 0:
            raise DependencyInstallationError(
                _build_command_failure_message(
                    package_name="pip",
                    command=command,
                    return_code=result.returncode,
                    stdout=result.stdout,
                    stderr=result.stderr,
                )
            )
        return
    # pip can not necessarily be imported into Blender after this
    get_pip_path = Path(__file__).parent / "external" / "get-pip.py"
    command = [str(python_path), str(get_pip_path)]
    result = subprocess.run(command, cwd=_CWD_FOR_SUBPROCESSES, capture_output=True, text=True)
    if result.returncode != 0:
        raise DependencyInstallationError(
            _build_command_failure_message(
                package_name="pip",
                command=command,
                return_code=result.returncode,
                stdout=result.stdout,
                stderr=result.stderr,
            )
        )


def get_package_install_directory() -> str:
    # user modules loaded are loaded by default by blender from this path
    # https://docs.blender.org/manual/en/4.2/editors/preferences/file_paths.html#script-directories
    modules_path = bpy.utils.user_resource("SCRIPTS", path="modules")
    if modules_path not in sys.path:
        # if the path does not exist blender will not load it, usually occurs in fresh install
        sys.path.append(modules_path)
    return modules_path


def module_can_be_imported(name: str):
    try:
        stripped_name = _strip_pip_version(name)
        mod = __import__(stripped_name)
        LOG.info("module: " + name + " is already installed")
        LOG.debug(stripped_name + ":" + getattr(mod ,"__version__", "None") + " in path: " + getattr(mod, "__file__", "None"))
        return True
    except ModuleNotFoundError:
        return False


def _strip_pip_version(name: str) -> str:
    name_strip_comparison_sign = name.replace(">", "=").replace("<", "=")
    return name_strip_comparison_sign.split("=")[0]


def _build_command_failure_message(
    package_name: str,
    command: list[str],
    return_code: int,
    stdout: str,
    stderr: str,
) -> str:
    stdout_tail = (stdout or "").strip()[-700:]
    stderr_tail = (stderr or "").strip()[-700:]
    return (
        f"Failed to install runtime dependency `{package_name}`. Command: {' '.join(command)} "
        f"(exit={return_code}). stdout={stdout_tail!r} stderr={stderr_tail!r}"
    )
