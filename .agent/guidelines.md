# Junie Agent Guidelines

This document is the **authoritative Source of Truth** for Junie (the AI agent). It consolidates all core standards, safety protocols, and workflow requirements to ensure reliable and consistent behavior.

---

## 1. Core Mandates

### 1.1 NO REGEX RULE
- **AVOID REGEX AT ALL COSTS.** Never use Regular Expressions unless it is mathematically impossible to solve the problem otherwise.
- **Preferred Alternatives**: Use standard string methods (`contains`, `startsWith`, `endsWith`, `substring`, `split`, `replace` with strings), dedicated parsers, or simple logic.
- **Reasoning**: Regex is error-prone, hard to maintain, and often leads to subtle bugs in AI-generated code.

### 1.2 Environment & Paths
- **Current Environment**: Use the environment provided by the IDE (e.g., Linux/Bash if that's what's active).
- **Cross-Platform Code**: Write code that works on Windows, Linux, and macOS. Use `java.nio.file.Path` or IntelliJ's `VfsUtil`/`FileUtil`.
- **Scratch Directory**: For temporary files, logs, or notes, use the IDE's scratch directory.
  - *Internal Note*: Do NOT hardcode paths like `C:\Users\...` unless you are explicitly instructed to interact with a specific external project at that path.

### 1.3 Project Safety
- **Versioning**: NEVER increment the plugin version in `gradle.properties` or `build.gradle.kts` unless explicitly told to.
- **TODOs**: Do NOT implement or delete `TODO:` comments unless they are directly related to your current task.
- **Cleanup**: Immediately delete any temporary run configurations or files created for testing.

---

## 2. Coding Standards

### 2.1 IntelliJ Platform SDK
- **Logging**: Use `com.intellij.openapi.diagnostic.Logger` instead of `println`.
- **Services**: Use `@Service` (project or application level). Avoid manual singletons.
- **UI**: Use `JBLabel`, `JBTextField`, `JBCheckBox`, and `FormBuilder` for consistency.
- **Internationalization (i18n)**: 
  - Never hardcode user-facing strings.
  - Add keys to `src/main/resources/messages/LangManager.properties` first.
  - Synchronize all `LangManager_<lang>.properties` files.
  - Use `LangManager.message("key")` in code.

### 2.2 Icons
- All icons MUST be declared in `BlenderIcons.kt`.
- Reference as `BlenderIcons.IconName`.

---

## 3. Workflow & Communication

### 3.1 Planning & Status
- **Plan First**: Always create a plan before starting significant work.
- **Status Updates**: Use `update_status` frequently to keep the user informed of progress and findings.
- **Verification**: Clearly state how you verified your changes (tests, linting, manual checks).

### 3.2 Documentation
- **CHANGELOG.md**: Update for every feature or fix using existing categories.
- **README.md / CONTRIBUTING.md**: Update if features or workflows change.
- **Project Wiki**: Whenever significant features or architectural changes are made, you MUST update the corresponding documentation in the project wiki (located at `/mnt/data/PycharmProjects/SakuraProjectWiki/docs/blender-development-pycharm/`).

---

## 4. AI Prompt Aliases

### 4.1 No Edit:
- When this prefix is used, no code changes shall be made.

---

## 5. Git & Commits

### 4.1 Commit Format
- **Format**: `Type(scope): Description`
- **Capitalization**: The `Type` MUST start with a capital letter.
- **Approved Types**: `[Feat]`, `[Fix]`, `[Docs]`, `[Style]`, `[Refactor]`, `[Test]`, `[Chore]`, `[I18n]`, `[Build]`, `[Ci]`, `[Perf]`.
- **Module Type Suffix**: If Necessary, for commits focusing on a single module, add a suffix wrapped in () noting the module
- **Atomic Commits**: Max 1-2 features per commit. Each commit must be a single logical unit.
- **Message Length**: Max 2 sentences (excluding prefix and trailer).

### 4.2 Mandatory Trailer
When Junie performs a commit, the command MUST include the following trailer:
`--trailer "Co-authored-by: Junie <junie@jetbrains.com>"`

---

## 6. Specialized Skills
For domain-specific procedures, refer to:
- [Git Management](skills/git_management.md)
- [Development Standards](skills/development_standards.md) (Deep dive)
- [Blender Extension Dev](skills/blender_extension_dev.md)
- [Documentation & Wiki](skills/documentation.md)
- [Wiki Specifics](wiki_guidelines.md)

---

## 7. Temporary Guidelines

### 7.1 Action Mandates
- **Focus**: Do not act on any findings in `Combined_Audit_Report.md` unless they are explicitly referenced in the current mandate or feedback.
- **Finding Completion**: When a finding is acted on, add a `[Completed]` tag to the front of the Finding Header in `Combined_Audit_Report.md`.
- **Commit Changes**: Once a Finding has been resolved/completed, perform a commit with a single-sentence summary of the changes.
- **Audit Document**: The Audit Document is a private, offline file, do not commit this file.
