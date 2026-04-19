# AI Workflow Skill

This document standardizes AI agent behavior and environmental management.

## Session Management
- **Initial Context**: Review [Main Guidelines](../agent-guidelines.md) at the start of every session.
- **Project Map**: Refer to [`.agent/project.md`](../project.md) for current task status and architecture.
- **Resource Management**: Download external assets locally for reliability and offline availability.
- **SSH/Passphrase Handling**: If a process (e.g., Git) requires a passphrase, use the `ask_user` tool.

## Role Definition
- **.agent/guidelines.md**: The authoritative "Source of Truth" for behavior.
- **.agent/project.md**: Current goals and state.
- **.agent/context.md**: Coding styles (Kotlin, Python).
- **.agent/skills/**: Specialized, domain-specific instructions.
