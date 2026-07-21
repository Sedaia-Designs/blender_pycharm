import sys
import tempfile
import types
import unittest
from pathlib import Path


TEMPLATE_PATH = Path(__file__).parents[2] / "main/resources/fileTemplates/internal/BlenderRuntimeRepoSyncLaunch.py.ft"


class RuntimeRepoSyncLaunchTest(unittest.TestCase):
    def execute_template(self, project_dir: Path, source_folder: str, symlink_name: str):
        linked_addons = []
        enabled_modules = []

        class AddonInfo:
            def __init__(self, load_dir, module_name):
                self.load_dir = load_dir
                self.module_name = module_name

        load_addons = types.ModuleType("blender_pycharm.load_addons")
        load_addons.setup_addon_links = lambda addons: linked_addons.extend(addons)
        blender_pycharm = types.ModuleType("blender_pycharm")
        blender_pycharm.AddonInfo = AddonInfo
        blender_pycharm.load_addons = load_addons

        preferences = types.SimpleNamespace(
            addon_disable=lambda **kwargs: None,
            addon_refresh=lambda: None,
            addon_enable=lambda module: enabled_modules.append(module),
        )
        extensions = types.SimpleNamespace(repo_refresh_all=lambda: None)
        bpy = types.ModuleType("bpy")
        bpy.ops = types.SimpleNamespace(preferences=preferences, extensions=extensions)

        original_modules = {
            name: sys.modules.get(name)
            for name in ("bpy", "blender_pycharm", "blender_pycharm.load_addons")
        }
        sys.modules.update({
            "bpy": bpy,
            "blender_pycharm": blender_pycharm,
            "blender_pycharm.load_addons": load_addons,
        })
        try:
            source = TEMPLATE_PATH.read_text(encoding="utf-8")
            substitutions = {
                "${includeDirLiteral}": "",
                "${projectPathLiteral}": str(project_dir),
                "${sourceFolderLiteral}": source_folder,
                "${addonSymlinkNameLiteral}": symlink_name,
                "${extensionsRepositoryLiteral}": "pycharm_blender",
            }
            for placeholder, value in substitutions.items():
                source = source.replace(placeholder, value)
            exec(compile(source, str(TEMPLATE_PATH), "exec"), {})
        finally:
            for name, module in original_modules.items():
                if module is None:
                    sys.modules.pop(name, None)
                else:
                    sys.modules[name] = module

        return linked_addons, enabled_modules

    def test_extension_uses_only_manifest_module_id(self):
        with tempfile.TemporaryDirectory() as project_directory:
            project_path = Path(project_directory)
            extension_path = project_path / "extension"
            extension_path.mkdir()
            (extension_path / "blender_manifest.toml").write_text(
                'id = "manifest_module_id"\n',
                encoding="utf-8",
            )

            linked_addons, enabled_modules = self.execute_template(
                project_path,
                "extension",
                "configured_symlink_name",
            )

            self.assertEqual("manifest_module_id", linked_addons[0].module_name)
            self.assertEqual(["bl_ext.pycharm_blender.manifest_module_id"], enabled_modules)

    def test_legacy_addon_keeps_configured_symlink_name(self):
        with tempfile.TemporaryDirectory() as project_directory:
            project_path = Path(project_directory)
            addon_path = project_path / "addon_source"
            addon_path.mkdir()
            (addon_path / "__init__.py").touch()

            linked_addons, enabled_modules = self.execute_template(
                project_path,
                "addon_source",
                "configured_symlink_name",
            )

            self.assertEqual("configured_symlink_name", linked_addons[0].module_name)
            self.assertEqual(["configured_symlink_name"], enabled_modules)


if __name__ == "__main__":
    unittest.main()
