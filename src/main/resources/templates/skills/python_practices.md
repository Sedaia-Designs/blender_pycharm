# Python Practices Skill

## Objectives
- Ensure high-quality, maintainable, and idiomatic Python code for Blender Extensions.
- Follow industry standards (PEP 8) and project-specific conventions.

## Style Conventions
- **PEP 8 Compliance**: Follow PEP 8 guidelines. Aim for a maximum of 79 characters per line.
- **Naming**:
  - Class names: `CamelCase` (e.g., `MyOperator`).
  - Module/Variable names: `snake_case` (e.g., `my_module`).
- **Indentation**: Use **4 spaces** for indentation. Never use tabs.
- **Spaces**: Use spaces around operators (e.g., `1 + 1`, not `1+1`).
- **Quotes**:
  - Use **single quotes** (`'`) for enums and internal identifiers.
  - Use **double quotes** (`"`) for strings intended for the UI or end-users.

## Imports & BPY
- **Explicit Imports**: Use explicit imports only. **No wildcard imports** (e.g., `from bpy.types import *` is forbidden).
- **BPY Alias Pattern**: When possible, use aliased imports for `bpy.types` and `bpy.props`.
  ```python
  # no-inspection PyUnresolvedReferences
  import bpy.types as T
  # no-inspection PyUnresolvedReferences
  import bpy.props as P
  ```
- Use the aliased name when referencing components (e.g., `T.Operator`, `P.StringProperty`).

## Versioning
- **Semantic Versioning (MAJOR.MINOR.PATCH)**:
  - **MAJOR**: Breaking changes (UI reworks, breaking API changes).
  - **MINOR**: New backward-compatible functionality.
  - **PATCH**: Bug fixes and minor internal changes.

## Specialized Rule: NO REGEX
As per the [Main Guidelines](../agent-guidelines.md), **avoid Regex**. Use standard string manipulation or custom logic instead.
