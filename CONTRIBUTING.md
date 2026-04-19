# Contributing to Blender Development for PyCharm

Thank you for your interest in contributing to this project! We welcome all contributions, from bug reports and feature requests to code changes and documentation improvements.

## Getting Started

### Prerequisites

- **JDK 21** (or later) – [Download Adoptium Temurin](https://adoptium.net/temurin/releases/?version=21)
- **IntelliJ IDEA** (Community or Ultimate) – [Download](https://www.jetbrains.com/idea/)
- **DevKit Plugin** – Installed within IntelliJ IDEA to provide plugin development support.
- **Blender 4.2+** – [Download](https://www.blender.org/download/)

### Setup

1. **Fork and Clone** the repository:
   ```bash
   git clone https://github.com/your-username/BlenderExtensions.git
   cd BlenderExtensions
   ```
2. **Open the project** in IntelliJ IDEA.
3. **Configure the JDK**: Go to `File > Project Structure > Project` and ensure the Project SDK is set to JDK 21+.
4. **Import Gradle**: IntelliJ should automatically detect the `build.gradle.kts` file and import the project. If not, open the **Gradle** tool window and click the **Refresh** icon.

## Development Workflow

### 1. Create a Branch
Create a new branch for your work:
```bash
git checkout -b feature/your-feature-name
# or
git checkout -b fix/your-bug-fix
```

### 2. Make Changes
Implement your changes in the `src/main/kotlin` directory. Follow the existing project structure and coding style.

### 3. Test Your Changes
Run the plugin in a sandbox environment to verify your changes. You can do this from the terminal or using the **Run IDE** configuration in IntelliJ:
```bash
./gradlew runIde
```
This project includes both unit tests and headless integration tests that validate the communication cycle with Blender. Run all tests with:
```bash
./gradlew test
```
The integration tests use a headless Blender environment to verify the TCP heartbeat and reload logic.

To build the distribution plugin ZIP file:
- **IntelliJ**: Use the **Build Plugin** run configuration or the `buildPlugin` task in the Gradle tool window.
- **Terminal**:
  ```bash
  ./gradlew buildPlugin
  ```
The built plugin will be available in `build/distributions/`.

### 4. Commit and Push
Commit your changes using the following standardized Conventional Commits prefixes:
- `Feat`: New feature implementation.
- `Fix`: Bug fixes.
- `Docs`: Documentation updates.
- `Style`: Stylistic changes (whitespace, formatting) with no logic changes.
- `Refactor`: Code clarity refactors (no functional changes).
- `Test`: Adding or updating tests.
- `Chore`: General file cleanup, dependency updates, or internal tasks.
- `I18n`: Internationalization and localization updates.
- `Build`: Changes affecting the build system or external dependencies.
- `Ci`: Changes to CI configuration files and scripts.
- `Perf`: Performance improvements.
- `UI`: Changes to UI components and layout.

**Commit Guidelines**:
- Each commit must contain at most **1 to 2 features**.
- Commit messages must be at most **2 sentences long** (the prefix does not count towards this limit).
- The use of the `(scope)` (e.g., `Feat(blender):`) is optional but recommended.
- For AI agents, always include the co-author trailer: `--trailer "Co-authored-by: Junie <junie@jetbrains.com>"`

```bash
git add .
git commit -m "Feat(blender): add detailed description"
git push origin feature/your-feature-name
```

### 5. Open a Pull Request
Submit a Pull Request on GitHub. Provide a clear description of the changes and any related issues.

## Code Style

- Use **Kotlin** for all plugin logic.
- Follow the official [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html).
- **NO REGEX**: Avoid using Regular Expressions unless absolutely necessary. Prefer standard string methods (`contains`, `startsWith`, `split`, etc.) or dedicated parsers.
- Keep the UI consistent with IntelliJ Platform guidelines.
- Use Swing and `FormBuilder` for configuration UIs.
- **Icon Management**: Declare all icons in `BlenderIcons.kt` and reference them as `BlenderIcons.Icon` across the codebase.
  - Custom icon example: `@JvmField val BlenderColor: Icon = IconLoader.getIcon("/images/blender_color.svg", BlenderIcons::class.java)`
  - JetBrains icon example: `@JvmField val Install: Icon = AllIcons.Actions.Install`
- **Internationalization (i18n)**: All user-facing UI and log strings MUST have an associated i18n mapping.
  - Use `LangManager.message("key")` for localized strings.
  - English mappings are stored in: `src/main/resources/messages/LangManager.properties`
  - See [Localization Contribution Guidelines](docs/LOCALIZATION_CONTRIBUTING.md) for more details.

## Documentation References

When referencing Blender documentation, please refer to the `.blender-docs` directory in the project root instead of online documentation whenever possible. This contains a local copy of the Blender Python and Manual docs for offline and consistent reference.

## Repository Reference

The [Jacques Lucke VS-Code extension](https://github.com/JacquesLucke/blender_vscode.git) is the main basis for this project. A clone of this repository is maintained in the `.reference` directory for architectural and feature parity reference.

## AI-Assisted Development

This project uses AI agents (like Junie) to maintain high-quality code and documentation. When contributing using an AI agent:

- **Initial Context**: At session start, review `.agent/` files and specialized skills in `.agent/skills/` to align with current architecture and standards.
- **NO REGEX**: Avoid using Regular Expressions unless absolutely necessary. Prefer standard string methods or dedicated parsers.
- **Role Definition**:
  - `.agent/project.md`: Authoritative "Project Map" for goals, architecture, and current state.
  - `.agent/context.md`: Language-specific coding styles (Kotlin, Python).
  - `.agent/guidelines.md`: Entry point and high-level workflow summary.
  - `.agent/skills/`: Modular, procedural instructions for specific domains (Git, Docs, etc.).
- **Versioning**: NEVER bump the plugin version (e.g., in `build.gradle.kts`) unless explicitly instructed by the maintainers.
- **Wiki**: `wiki_guidelines.md` is strictly for the external Sphinx/RST wiki (`PycharmBlenderWiki`) and has NO effect on this project's code or internal documentation. Editing the wiki's source code is allowed only when authorized.

## Reporting Issues

If you find a bug or have a feature request, please [open an issue](https://github.com/Sakura-Sedaia/BlenderExtensions/issues) with:
- A clear title and description.
- Steps to reproduce the bug.
- Your OS, PyCharm version, and Blender version.
- Any relevant logs or screenshots.

## Communication

For questions or discussions, feel free to use GitHub Discussions or open an issue.

For legal notices, AI disclosures, and trademark information, please see [NOTICE.md](docs/NOTICE.md). For detailed architecture, see [ARCHITECTURE.md](docs/ARCHITECTURE.md).

---
By contributing, you agree that your contributions will be licensed under the project's license.
