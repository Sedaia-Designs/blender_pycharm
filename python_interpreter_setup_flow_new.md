# Python Interpreter Setup Flow (Tool Window Action)

This flow describes what happens when the user clicks **"Setup Python Interpreter"** in the Blender tool window.

## Entry Points

- Managed Blender table button:
  - `BlenderToolWindowContent.setupManagedButtons()`
  - On click, selected version is resolved to an executable path via `BlenderDownloader.getOrDownloadBlenderPath(version)`
  - Then calls `pythonService.setupPythonInterpreter(path)`

- System Blender table button:
  - `BlenderToolWindowContent.setupSystemButtons()`
  - On click, selected installation path is used directly
  - Then calls `pythonService.setupPythonInterpreter(inst.path)`

## High-Level Call Chain

1. `BlenderToolWindowContent` button handler
2. `PythonService.setupPythonInterpreter(blenderExePath)`
3. `PythonInterpreterService.setupPythonInterpreter(blenderExePath)`

## Detailed Runtime Flow

```mermaid
flowchart TD
    A[User clicks Setup Python Interpreter in Tool Window] --> B{Source table?}
    B -->|Managed| C[Get selected Blender version]
    C --> D[BlenderDownloader.getOrDownloadBlenderPath(version)]
    D --> E{Path resolved?}
    E -->|No| Z1[Stop: no setup call]
    E -->|Yes| F[PythonService.setupPythonInterpreter(path)]

    B -->|System| G[Get selected installation path]
    G --> F

    F --> H[PythonInterpreterService.setupPythonInterpreter(blenderExePath)]
    H --> I[Find bundled Blender Python: PythonUtil.findPythonExecutable]
    I --> J{Found?}
    J -->|No| Z2[Show error notification: Python executable not found]
    J -->|Yes| K[Read bundled Python version: PythonUtil.getPythonVersion]

    K --> L{Version detected?}
    L -->|No| Z3[Show error notification: cannot determine version]
    L -->|Yes| M[Compute major.minor]

    M --> N[Resolve system Python match: getOrInstallPython(major.minor)]
    N --> O[Choose interpreter: resolved system Python or bundled Blender Python]

    O --> P[Set venv path: project/.venv]
    P --> Q{venv Python exists?}
    Q -->|No| R[Run: chosenPython -m venv project/.venv]
    R --> S{venv creation ok?}
    S -->|No| Z4[Show error notification with stderr]
    S -->|Yes| T[Continue]
    Q -->|Yes| T

    T --> U[Resolve Python SDK type]
    U --> V{SDK type available?}
    V -->|No| Z5[Show error notification: Python SDK type unavailable]
    V -->|Yes| W[Open write action]

    W --> X[Create/reuse SDK named Blender Python <parent-folder>]
    X --> Y[Set SDK homePath = .venv python]
    Y --> Y1[Clear SDK roots]
    Y1 --> Y2[Add venv site-packages root if present]
    Y2 --> Y3[Add lint directory root if present]
    Y3 --> Y4[Commit SDK changes]
    Y4 --> Y5[Add SDK to table if newly created]
    Y5 --> Y6[Set project SDK = this SDK]

    Y6 --> Z6[Show success notification with venv path]

    H -.catch exception.-> Z7[Show generic error notification]
```

## Branches and Failure Modes

- Missing Blender-bundled Python executable -> immediate error notification.
- Could not parse bundled Python version -> immediate error notification.
- Venv creation command failed -> immediate error notification (stderr included).
- Python SDK type unavailable in IDE -> immediate error notification.
- Any unexpected exception during setup -> generic error notification.

## Important Implementation Notes

- The created project interpreter lives at `project/.venv`.
- SDK name format: `Blender Python (<blenderExePath parent directory name>)`.
- SDK roots are reset each run (`removeAllRoots`) to avoid duplicate roots.
- Lint root inclusion is conditional on lint directory existence.
- Managed-table flow may trigger Blender download first; system-table flow does not.
