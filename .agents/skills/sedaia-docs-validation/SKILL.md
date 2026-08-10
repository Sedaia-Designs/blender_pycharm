---
name: sedaia-docs-validation
description: Validate public Blender Development content edited in the separate Blender Developer Docs repository. Use after Markdown, MDX, Astro/Starlight configuration, routes, SolidJS components, SCSS, dependencies, or links affecting Blender Development documentation are changed.
---

# Blender Developer Docs validation

Run checks from `/Users/Sakura/Documents/WebstormProjects/blender-developer-docs`.

## Required checks

1. Run `pnpm build`.
2. Confirm Astro reports zero errors and no warnings affecting the change.
3. Confirm the expected `/blender-development/` routes are generated.
4. Confirm Pagefind indexing finishes and the sitemap is generated.
5. Inspect changed content for valid frontmatter, heading order, fenced-code languages, and Starlight aside syntax.
6. Inspect internal links for the correct slug and trailing slash. Confirm cross-origin links use absolute HTTPS URLs.
7. Review `git status` in both repositories and ensure validation did not stage or overwrite unrelated work.

## Conditional checks

- For route changes, verify sidebar discovery and required redirects in `vercel.json`.
- For SolidJS changes, check semantic markup, labels, server-rendered fallback, and the Astro client directive.
- For SCSS or layout changes, check narrow and wide layouts in light and dark themes when browser preview is practical.
- When a plugin implementation change accompanies the docs, run the targeted Gradle validation required by the root
  `AGENTS.md` in this repository.

Report exactly which checks passed and identify any skipped check and its remaining risk.
