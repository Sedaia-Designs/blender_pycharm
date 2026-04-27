# Wiki Agent Guidelines & Context

## Wiki Project Path
- **Windows**: `C:\Users\Sakura\Documents\PycharmProjects\PycharmBlenderWiki`
- **General**: All projects under `PycharmProjects` (including the wiki) will always exist in `$USER_HOME\Documents\PycharmProjects`.

This file contains the imported guidelines and context for interacting with the `PycharmBlenderWiki` project. **These are only to be referenced when making changes to the wiki.**

## Sphinx Practices
- **Documentation Format**: Use reStructuredText (RST) for all documentation files.
- **Theme Usage**: Leverage the `furo` theme's capabilities (e.g., sidebar nesting, accent colors) for a clean, navigation-focused experience.
- **Internal Linking**: Use Sphinx's `:doc:` role for linking to other pages and `:ref:` for specific sections to maintain link integrity and support move-refactors.
- **Media Assets**: Store images and static assets in `_static/` and reference them using relative paths or Sphinx directives.
- **Table of Contents**: Maintain a clear hierarchy in `index.rst` and use `toctree` directives effectively in sub-pages.
- **Build Quality**: Regularly run `make html` and ensure there are no broken links or build warnings.
- **Dependencies**: Manage all documentation-related dependencies (Sphinx, themes, extensions) in `requirements.txt`.

## Clean & Readable Documentation Practices
- **Clarity and Conciseness**: Each document should have a clear purpose and be as concise as possible while remaining comprehensive.
- **Descriptive Headers**: Use meaningful section headers that allow for easy scanning and logical flow.
- **Consistent Terminology**: Use the same terms for the same concepts throughout the documentation to avoid confusion.
- **DRY (Don't Repeat Yourself)**: Use include directives or cross-references instead of duplicating content across multiple pages.
- **Logical Structure**: Organize files and directories logically (e.g., grouping related topics in subdirectories like `docs/usage-guide/`).
- **Self-Documenting Structure**: File names should clearly reflect their content (e.g., `Installation.rst` instead of `Setup.rst`).

## Technical Context: Sphinx & Furo Documentation
- **Section Headers**: Use consistent underlining for hierarchy:
  - `=` for Page Titles
  - `-` for Section Headers
  - `~` for Sub-section Headers
- **Indentation**: RST is whitespace-sensitive. Standard indentation is 3 spaces for directives.
- **Code Blocks**: Use `.. code-block:: <language>` for syntax-highlighted code.
- **Theme (Furo)**: Clean, navigation-focused, supports deep hierarchy in the sidebar, and automatic light/dark mode.
