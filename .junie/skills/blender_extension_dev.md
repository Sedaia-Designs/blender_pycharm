# Blender Extension Development Skill

## Objectives
- Efficiently develop, debug, and maintain Blender Extensions (4.2+) within IntelliJ IDEA / PyCharm.
- Ensure proper interaction between the plugin and Blender's internal extension system.

## Extension Architecture (Blender 4.2+)
- **Extensions vs. Add-ons**: Blender 4.2 introduced a new extension system. Extensions are typically distributed as `.zip` files and managed via repositories.
- **Repository Structure**: The plugin uses a local repository approach. Extensions are symlinked or copied into a directory that Blender recognizes as a repository.
- **`blender_manifest.toml`**: Every extension MUST have a manifest file. Ensure any template generation includes a valid manifest.

## Key Components in Plugin
- **`BlenderScanner`**: Responsible for finding Blender installations and their versions.
- **`BlenderLauncher`**: Handles starting Blender with specific arguments (e.g., `--python`, `--python-expr`).
- **`BlenderLinker`**: Manages the "link" between the project source and Blender's extension directory (via symlinks or copies).
- **`BlenderScriptGenerator`**: Generates Python scripts that are executed inside Blender to:
    - Register local repositories.
    - Enable/Disable extensions.
    - Setup reload servers.

## Development Patterns
- **Main Thread Execution**: Many Blender API calls (like `bpy.ops`) MUST run on the main thread. Use `bpy.app.timers.register(functional_logic)` to defer execution if coming from a background thread (like a socket listener).
- **Module Purging**: When reloading, just re-enabling an addon is often not enough because Python caches modules in `sys.modules`.
    - **Protocol**: 
        1. Disable addon.
        2. Refresh repositories (`bpy.ops.extensions.repo_refresh_all()`).
        3. Remove modules from `sys.modules` that match the extension's package name.
        4. Re-enable addon.
- **Heartbeat Mechanism**: Maintain a TCP connection between the plugin and Blender for real-time status and reload commands. Implement retry logic and handle connection drops gracefully.

## Best Practices
- **Path Handling**: Use `BlenderPathUtil` for platform-specific paths (Config, Scripts, Executables).
- **Version Compatibility**: Check `bpy.app.version` if using features that vary between Blender versions.
- **Logging inside Blender**: Use `print()` for simple output which appears in Blender's system console. For structured data, use `json.dumps()` over the heartbeat socket.
- **Headless Testing**: Use the `BlenderIntegrationTest` pattern to verify communication without manual Blender interaction.
