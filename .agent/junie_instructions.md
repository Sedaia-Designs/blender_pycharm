# Junie Agent Guidelines

This document provides high-priority instructions for Junie (the AI agent) to ensure consistent behavior across sessions. These rules override or clarify other documentation in the `.agent/` directory.

## 1. Mandatory Formatting Rules

### 1.1 Commit Messages
- **Format**: `Type(scope): Description`
- **Capitalization**: The `Type` MUST start with a capital letter.
- **Approved Types**: `Feat`, `Fix`, `Docs`, `Style`, `Refactor`, `Test`, `Chore`, `I18n`, `Build`, `Ci`, `Perf`.
- **Scope**: Optional but encouraged (e.g., `Feat(blender):`, `Fix(ui):`).
- **Feature Limit**: Each commit MUST contain at most **1 to 2 features**. Do NOT group more features into a single commit.
- **Message Length**: Commit messages (excluding the prefix and trailer) MUST be at most **2 sentences** long.
- **Trailer**: EVERY commit MUST include the co-author trailer:
  `Co-authored-by: Junie <junie@jetbrains.com>`

### 1.2 Internationalization (i18n)
- **Never hardcode** user-facing strings or log messages.
- **Always** add new keys to `src/main/resources/messages/LangManager.properties` first.
- **Synchronize** all other `LangManager_<lang>.properties` files when adding new keys. Use the same key and provide high-quality translations.

## 2. Project Safety & Hygiene

### 2.1 Versioning
- **Never** increment the plugin version in `gradle.properties` or `build.gradle.kts` unless explicitly told to do so by the user.

### 2.2 Run Configurations
- **Cleanup**: Any temporary run configurations created for testing MUST be deleted before finishing the task.
- **Standard Configurations**: Only the following should persist: `buildPlugin`, `runIde`, `runAllTests`, `runUnitTests`, `runIntegrationTests`.

### 2.3 TODOs
- **Constraint**: Do NOT implement or delete `TODO:` comments found in the code unless they are directly related to your current task or the user specifically asks you to.

## 3. Environment Specifics

### 3.1 Paths & OS
- **Operating System**: Windows. Use `\` for paths in code and commands.
- **Terminal**: PowerShell. Use `;` to chain commands, not `&&`.
- **Scratch Directory**: Use `C:\Users\Sakura\AppData\Roaming\JetBrains\IntelliJIdea2025.3\scratches\` for temporary files, logs, or notes. Do NOT use Linux paths like `/home/sakura/...`.

## 4. Workflow & Communication

### 4.1 Planning
- **Always** update the plan via `update_status` when significant changes occur or new information is discovered.
- **Verification**: Clearly state how you verified your changes (tests run, linting, etc.) in the final submission.

### 4.2 Documentation
- **CHANGELOG.md**: Update this file for every feature or fix, following the existing versioning and category structure.
