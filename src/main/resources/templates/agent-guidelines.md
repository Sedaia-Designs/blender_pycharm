# Agent Coding Guidelines

This document is the **primary entry point** for AI agents working on this project. It outlines the core standards and references specialized skills for detailed procedures.

---

## 1. Core Mandates

### 1.1 NO REGEX RULE
- **AVOID REGEX AT ALL COSTS.** Never use Regular Expressions unless it is the only possible solution.
- **Preferred Alternatives**: Use standard string methods (`contains`, `startsWith`, `split`, etc.) or dedicated parsers.

### 1.2 Environment & Paths
- **Cross-Platform**: Write code that works on Windows, Linux, and macOS. Use `java.nio.file.Path`.
- **Scratch Directory**: For temporary files, use the IDE's scratch directory. Do NOT hardcode paths.

### 1.3 Project Safety
- **Versioning**: NEVER increment the project version unless explicitly told to.
- **TODOs**: Do NOT implement or delete `TODO:` comments unless they are directly related to your task.

---

## 2. Coding Standards

### 2.1 Clean & Readable Code
- **SRP**: Each class/function should have one clear purpose.
- **Descriptive Naming**: Use names that clearly state intent.
- **Function Size**: Aim for small, focused functions.
- **DRY**: Extract common logic into helpers or utilities.

---

## 3. Workflow & Communication

### 3.1 Planning & Status
- **Plan First**: Always create a plan before starting significant work.
- **Status Updates**: Use `update_status` frequently.
- **Verification**: Clearly state how you verified your changes.

### 3.2 Documentation
- **Narrative Documentation**: Avoid "changelog-like" dialogue (e.g., "Added...", "Fixed...", "Updated...") in non-changelog files. Describe the *current state* and *capabilities* of the system (e.g., "Provides...", "Includes...", "Supports...") rather than the history of changes.

---

## 4. Git & Commits

### 4.1 Commit Format
- **Format**: `type(scope): Description`
- **Atomic Commits**: Max 1-2 features per commit.
- **Mandatory Trailer**: When Junie performs a commit, the command MUST include the following trailer:
  `Co-authored-by: Junie <junie@jetbrains.com>`

---

## 5. Specialized Skills
- [Blender Extension Dev](skills/blender_extension_dev.md)
- [Python Practices](skills/python_practices.md)
- [Git Management](skills/git_management.md)
- [AI Workflow](skills/ai_workflow.md)
