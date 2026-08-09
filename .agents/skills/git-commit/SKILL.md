---
name: git-commit
description: Create safe, logically grouped Git commits using this repository's bracketed type-and-module subject format. Use when the user asks to commit changes, create a Git commit, prepare a commit message, stage a logical change for commit, or invokes `/commit`. Analyze staged and unstaged diffs, select only the intended files, generate a project-compliant message, and execute the commit without altering unrelated work.
---

# Git Commit

Create focused commits from the actual repository diff. Follow the repository's `AGENTS.md` instructions when they are more specific than this skill.

## Commit Subject Format

Use one of these forms:

```text
[Type -> module] Description
[Type] Description
```

- Use a concise, title-cased type that describes the change, such as `Feature`, `Fix`, `Docs`, `UI/UX`, `Refactor`, `Test`, `Build`, `CI`, `Chore`, `Release`, or `Revert`.
- Use `-> module` when one or two modules are affected. Derive a short, lowercase module name from the affected area or established repository history.
- Omit `-> module` when three or more modules are affected.
- Write the description in imperative mood and sentence case, without a trailing period.
- Keep the complete subject at or below 150 characters.
- When naming a subclass, qualify it with its parent class, for example `PluginConfig.BlendInstallInfo`.

Examples:

```text
[Feature -> settings] Add Blender version management
[Fix] Validate Blender download URLs
[Docs -> readme] Streamline project overview
```

Do not generate Conventional Commits syntax such as `feat(scope): description`; this repository's bracketed format replaces it.

## Workflow

### 1. Read Repository Instructions

Locate and read the applicable `AGENTS.md` files before staging or committing. Inspect recent subjects to learn established type and module names:

```bash
git log -20 --pretty=format:'%s'
```

### 2. Analyze Repository State

Inspect both staged and unstaged work:

```bash
git status --short --branch
git diff --staged
git diff
```

Treat existing changes as user work. Determine which files form the requested logical change and identify any already-staged unrelated files. Do not overwrite, restore, reset, or otherwise alter unrelated work.

### 3. Stage the Logical Change

Stage explicit paths only:

```bash
git add path/to/file1 path/to/file2
```

- Keep one logical change per commit.
- Do not use broad staging commands such as `git add .` when unrelated changes exist.
- Do not commit secrets, credentials, private keys, environment files, or other sensitive data.
- Never commit files under `docs/Wiki/internal`, even when they were edited for the task.
- If unrelated files are already staged, do not include them silently. Preserve their staged state and isolate the requested commit safely; ask for direction if isolation would require changing user-owned staging.

### 4. Verify the Candidate Commit

Review exactly what will be committed:

```bash
git diff --staged --stat
git diff --staged
git diff --staged --check
```

Select the type, optional module, and description from this staged diff rather than from the user's wording alone. Run relevant validation required by `AGENTS.md` or the task before committing.

### 5. Execute and Confirm

Commit with the generated subject:

```bash
git commit -m "[Type -> module] Description"
```

Then confirm the result and remaining worktree state:

```bash
git show --stat --oneline HEAD
git status --short --branch
```

Report the commit hash, subject, included change, validation performed, and any remaining uncommitted changes.

## Git Safety Protocol

- Never change Git configuration.
- Never run destructive commands or discard changes unless the user explicitly requests the exact action.
- Never bypass hooks with `--no-verify` unless the user explicitly asks.
- If a hook fails, fix the issue within task scope and create a new commit attempt; do not amend an existing commit unless explicitly requested.
- Never push, force-push, publish, tag, or otherwise affect a remote without explicit permission.
