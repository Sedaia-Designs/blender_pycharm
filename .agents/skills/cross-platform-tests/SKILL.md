---
name: cross-platform-tests
description: Create, review, and fix JVM or Kotlin tests that must behave consistently on Windows, Linux, and macOS. Use for tests involving paths, files, processes, archives, environment variables, permissions, line endings, or OS-dependent behavior.
---

# Cross-Platform Tests

Keep tests portable across Windows, Linux, and macOS unless the behavior under test is intentionally OS-specific.

## Assert Semantics, Not Host Representation

- Construct filesystem paths with `Path.of`, `resolve`, and test-framework temporary directories. Do not embed `/` or `\\` as a
  separator in portable expectations.
- When production code returns a platform path string, derive the expected value from the same test-owned `Path` with `toString()`.
  Normalize both sides when path normalization is part of the contract.
- Do not use POSIX-looking paths such as `/tmp/example` or `/Applications/example` as supposedly absolute portable fixtures. Derive
  absolute paths from a temporary directory or the test project's base path.
- Preserve the actual invariant. For example, verify that a path containing spaces remains one process argument instead of requiring
  its string form to use a particular separator.
- Compare file content as text with an intentional line-ending policy. Use `System.lineSeparator()` only when native line endings are
  the contract; otherwise normalize CRLF and LF before comparison.

## Isolate Platform Behavior

- Prefer Java, Kotlin, IntelliJ Platform, and EEL APIs over invoking shell built-ins or assuming commands such as `which`, `chmod`,
  `sh`, or `cmd` exist.
- Do not assume Unix executable bits, symlink support, case sensitivity, atomic moves, file-lock behavior, or deletion semantics are
  identical across operating systems. Design fixtures around the contract being tested.
- Use OS conditions only when the product behavior is intentionally different. Keep shared assertions outside OS branches, and make
  skipped or conditional coverage explicit.
- Pass environment variables explicitly to process fixtures. Do not rely on machine-specific home directories, PATH contents, installed
  applications, locale, timezone, or default encoding.
- Close streams, archive filesystems, and process handles before cleanup so Windows can release file locks.

## Review and Validation

Before accepting a test change, inspect literals and assertions for separators, roots, shell syntax, commands, line endings, and
permission assumptions. Distinguish a product defect from an OS-specific fixture or expectation before changing production code.

Run the narrowest relevant test first, then the containing test class or suite and the repository formatting check. When only one host OS
is available, state which portability risks were addressed by construction and which OS-specific execution was not performed.
