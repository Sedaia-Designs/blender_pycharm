---
name: sedaia-docs-authoring
description: Create or revise public Blender Development documentation in the separate Blender Developer Docs Astro/Starlight repository. Use after user-visible plugin changes, when a prompt asks to update public docs, or when Blender Development routes, workflows, settings, compatibility, releases, or troubleshooting guidance change.
---

# Blender Developer Docs authoring

## Repository boundary

- Treat the current Blender Development repository as the authoritative product source.
- Edit public pages in `/Users/Sakura/Documents/WebstormProjects/blender-developer-docs/src/content/docs/blender-development/`.
- Read the documentation repository's root `AGENTS.md` before making changes; its instructions govern files edited there.
- Keep internal material in `docs/Wiki/internal` private. Never copy it into public documentation without explicit approval.
- Preserve unrelated changes in both worktrees. Do not create commits, push, or deploy unless requested.

## Workflow

1. Inspect the relevant implementation, tests, messages, README, changelog, and release metadata in this repository.
2. Read the affected destination page and `src/content/docs/blender-development/index.md`.
3. Reconcile product names, PyCharm and Blender versions, menu labels, commands, URLs, and limitations against the current code.
4. Prefer Markdown. Use MDX only when an imported component is required.
5. Add meaningful `title` and `description` frontmatter to every page.
6. Write for the reader's outcome: prerequisites, ordered steps, verification, and source-supported troubleshooting as useful.
7. Use lowercase kebab-case filenames and stable routes. Add a discoverability link from the landing page or a related guide.
8. If a published route changes, add a redirect in the documentation repository's `vercel.json` and update inbound project links.
9. Invoke `sedaia-docs-validation` after editing.

## Publication rules

- Preserve the product name **Blender Development**.
- Use relative links within the Blender Development documentation area.
- Use absolute HTTPS URLs for other origins, including `sakura-sedaia.com` and GitLab.
- Add language identifiers to fenced code blocks and use Starlight asides only for meaningful callouts.
- Do not expose local paths, credentials, private assets, source-reference inventories, or security-sensitive implementation details.
- Mark pre-release behavior, version constraints, deprecations, and genuinely unverified instructions explicitly.
