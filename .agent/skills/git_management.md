# Git Management Skill

Detailed procedures for maintaining a clean and atomic commit history.

## Procedures
1. **Commit Format**: Follow [Main Guidelines](../guidelines.md#41-commit-format).
2. **Co-author Trailer**: For commits performed by Junie, MUST include: `--trailer "Co-authored-by: Junie <junie@jetbrains.com>"`
3. **Atomic Commits**: Each commit should represent a single logical unit. If multiple tasks are performed, commit them separately.
4. **No Premature Versioning**: NEVER bump the plugin version unless authorized.

## Verification
- Run `git log -1` after a commit to verify the message and trailer format.
