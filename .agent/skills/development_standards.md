# Development Standards Skill

This document provides deep-dive practices for the IntelliJ Platform SDK and overall code quality.

## IntelliJ Platform SDK Practices
- **API Versioning**: Always use the most recent, non-deprecated APIs. Check for `@Deprecated` annotations and follow Javadoc suggestions.
- **Process Execution**: Use `com.intellij.execution.configurations.GeneralCommandLine` for launching external processes.
- **File I/O**: Prefer `java.nio.file.Path` and IntelliJ's `VfsUtil`, `FileUtil`, or `NioPathUtil` over `java.io.File`.
- **Network**: Use `com.intellij.util.io.HttpRequests` for downloading files or making network requests.
- **Progress**: Wrap long-running operations in `ProgressManager.getInstance().runProcessWithProgressSynchronously` or `runModal`.

## Clean & Readable Code Practices
- **Single Responsibility Principle (SRP)**: Each class/function should have one clear purpose. Decompose large classes if they handle multiple distinct responsibilities.
- **Descriptive Naming**: Use names that clearly state intent; avoid cryptic abbreviations.
- **Function Size**: Aim for small, focused functions. Extract sub-functions if logic becomes complex.
- **DRY (Don't Repeat Yourself)**: Extract common logic into helper methods or utility classes.
- **Constant Extraction**: Avoid hardcoded strings or numbers. Extract them into meaningful constants.
- **Comments**: Write self-documenting code. Use comments only to explain "why" if it's not obvious from "what" and "how".

## Specialized Rule: NO REGEX
As per the [Main Guidelines](../guidelines.md), **avoid Regex**. Use standard string manipulation or custom logic instead.
