# Blender Extension Development Skill

## Objectives
- Efficiently develop, debug, and maintain Blender Extensions (4.2+) within IntelliJ IDEA / PyCharm.
- Ensure proper interaction between the extension and Blender's internal system.

## Extension Architecture (Blender 4.2+)
- **Root Directory**: The active addon code is located in the `src/` directory.
- **Manifest Required**: Every extension must include a `blender_manifest.toml` file at its root (within `src/` for active development).
- **Required Metadata**: The manifest must include `id`, `version`, `name`, `tagline`, `maintainer`, `type`, and `license`.
- **Permissions**: Explicitly state required permissions (e.g., `network`, `files`) in the manifest with a short explanation.

## Development Patterns
- **Main Thread Execution**: Many Blender API calls (like `bpy.ops`) MUST run on the main thread. Use `bpy.app.timers.register(functional_logic)` to defer execution if coming from a background thread (like a socket listener).
- **Resource Management**: Download fonts and images locally (e.g., `src/fonts`) instead of using CDNs to ensure reliability and offline availability.
- **Local File Storage**: When creating options relating to local file storage for the addon, use the method `bpy.utils.extension_path_user(__package__, create=True, path="")` to ensure compliance with [Blender Extension guidelines](https://developer.blender.org/docs/handbook/extensions/addon_guidelines/).

## Validation & Submitting
- **Validation**: Before submitting, verify that the code complies with the `blender_manifest.toml` and that all class names follow the project's naming standards.
- **CLI Check**: Run `blender --command extension validate` (if available) to ensure the code runs without errors.
- **NO REGEX**: Avoid using Regular Expressions unless absolutely necessary. Prefer standard string methods or dedicated parsers.
- **Cleanup**: Remove unused variables, redundant parentheses, and debug print statements (use `self.report()` instead).

## Import & BPY
```python
# no-inspection PyUnresolvedReferences
import bpy
# no-inspection PyUnresolvedReferences
import bpy.types as T
# no-inspection PyUnresolvedReferences
import bpy.props as P
```
