# Architecture Overview

This document describes the internal workings of the Blender Development for PyCharm. For a high-level overview, see the [Wiki](https://wiki.sakura-sedaia.com/docs/blender-development-pycharm/core-concepts/architecture.html).

## How it Works

The plugin starts a local TCP server when Blender is launched. It injects a startup Python script into Blender that handles communication and repository management.

### 1. Repository Management
The plugin automatically configures a local extension repository named `blender_pycharm` pointing to the project's symlink. It handles API differences between Blender 4.2+ and 5.x to ensure compatibility across versions.

### 2. Communication
Blender connects back to PyCharm and listens for structured JSON reload commands (e.g., `{"type": "reload", "name": "my_extension"}`). The communication uses a bidirectional heartbeat and retry logic to maintain a robust connection.

### 3. Robust Reload Cycle
Reloads are executed on Blender's main thread (via `bpy.app.timers`) to avoid threading issues:
- **Disable**: The specific extension module is disabled.
- **Purge**: The module and all its submodules are purged from Python's `sys.modules` to clear the cache.
- **Refresh**: A fresh scan of all extension repositories is triggered via `bpy.ops.extensions.repo_refresh_all()`.
- **Enable**: The extension is re-enabled, forcing a fresh import of your code changes.

### 4. Sandboxing and Virtual Environments
To isolate development settings and ensure dependency isolation:
- **Sandboxing**: The plugin creates a project-local Blender user environment in `.venv/blender_sandbox`. It uses a project-local app template (`pycharm`) and user directories to avoid conflicts with your main Blender installation.
- **Virtual Environments**: The plugin implements a **Virtual Environment Guardrail**, automatically creating and configuring a project-local `.venv`. All Python operations, including linter setup, are executed within this environment.
- **Customization**: Supports custom splash screens (`images/sandbox_splash.png` in project root) and can optionally import your standard Blender user configuration.

### 5. Global Version Management
The plugin automatically handles multi-version downloads (4.2+ and 5.x) and manages global installations. A dedicated tool window provides a UI for downloading, deleting, and monitoring these installations.

### 6. Telemetry and Diagnostics
Local-only telemetry is collected to aid in debugging and stability monitoring. Communication errors and heartbeat timeouts are surfaced via IDE notifications and logged in `blender_plugin.log`.

## Project Structure

The project follows the standard IntelliJ Platform plugin structure:

```
.
├── build.gradle.kts        # Gradle build configuration
├── src
│   ├── main
│   │   ├── kotlin          # Plugin source code
│   │   │   └── com.sakurasedaia.blenderextensions
│   │   │       ├── actions     # Keyboard shortcuts and menu actions
│   │   │       ├── blender     # Core Blender service, TCP server, and communication
│   │   │       ├── icons       # Custom icons and icon providers
│   │   │       ├── listeners   # File system listeners for auto-reload
│   │   │       ├── notifications # IDE notifications and alerts
│   │   │       ├── project     # Project template generators and manifest wizards
│   │   │       ├── python      # Python SDK, VENV management, and linter configuration
│   │   │       ├── run         # Specialized Run Configurations (Testing, Build, etc.)
│   │   │       ├── settings    # Persistent settings and configuration UI
│   │   │       ├── system      # External process execution and system utilities
│   │   │       └── ui          # Blender Management Tool window and UI components
│   │   └── resources
│   │       └── META-INF
│   │           └── plugin.xml  # Plugin manifest
```
