# Python Interpreter Setup Flow (Tool Window Action)

This flow describes what happens when the user clicks **"Setup Python Interpreter"** in the Blender tool window.

NOTE: Use Pycharm's Python Interpreter Setup Flow and API to ensure maximum compatability.

## Entry Points

### Interpreter:
- Managed Blender table button:
  - `BlenderToolWindowContent.setupManagedButtons()`
  - On click, selected version is resolved to an executable path via `BlenderDownloader.getOrDownloadBlenderPath(version)`
  - Then calls `pythonService.setupPythonInterpreter(path)`

- System Blender table button:
  - `BlenderToolWindowContent.setupSystemButtons()`
  - On click, selected installation path is used directly
  - Then calls `pythonService.setupPythonInterpreter(inst.path)`

### Linter:
- "Setup Python Interpreter" table button:
  - `BlenderToolWindowContent.setupPythonInterpreter()`
  - On click, the Interpreter setup flow runs, once complete the user is prompted to setup the linter.
  - If the user does not want to setup the linter, exit the setup process.
  - If the user wants to setup the linter, call `pythonService.setupPythonLinter(path)`

## Detailed Runtime Flow

### Setup Python Interpreter
1. User initiates **"Setup Python Interpreter"** action with the desired Blender version selected
2. The Plugin then searches the Blender Executable Path of the given blender version, managed or system, for the `python` executable/binary. (Typically in `app/$blend_version/$blend_version/python/bin/python*`, where $blend_version is the `Major.Minor` version of the Blender)
3. The plugin then uses `$blend_version/python/bin/python* --version` to determine the Python version, using the output in the next step
4. The plugin then checks if that version of Python is available in the `blender_pycharm/python/py-$version` directory.
   1. If False: Run Install Python Workflow
   2. If True: Continue to the next step
5. Once the python binary/executable is found/created, create a new `project/.venv`, with the `project` directory being the project root.
6. Once the venv is created, query user if they want to go ahead and setup the linter.
   1. If yes, perform the setup linter actions.
   2. If no, exit the setup process and send a completion notification.

### Install Python
1. Initiated by Setup Python Interpreter
2. Install the desired Python version to `blender_downloads/python/py-$version`
3. Once finished, install `fake-bpy-module` to `blender_downloads/linter/bpy_$version`
4. Set the `blender_downloads/python/py-$version` as the project interpreter
5. Set the `blender_downloads/linter/bpy_$version` as a project interpreter path
6. Return to step 5 of the Setup Python Interpreter flow.