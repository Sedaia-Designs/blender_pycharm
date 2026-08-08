#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
release_script="$script_dir/../prepare-gitlab-release.sh"

# shellcheck source=../prepare-gitlab-release.sh
source "$release_script"

test_tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/prepare-gitlab-release-test.XXXXXX")"
trap 'rm -rf -- "$test_tmpdir"' EXIT

assert_equals() {
  local expected="$1"
  local actual="$2"
  local description="$3"

  if [ "$expected" != "$actual" ]; then
    printf 'FAIL: %s\nExpected: %s\nActual: %s\n' "$description" "$expected" "$actual" >&2
    exit 1
  fi
}

printf '%s\n' \
  'plugins {' \
  '    id("org.jetbrains.kotlin.jvm")' \
  '}' \
  'version = "1.2.3-SNAPSHOT"' \
  > "$test_tmpdir/build.gradle.kts"

cat > "$test_tmpdir/CHANGELOG.md" <<'EOF'
# Changelog

## [Unreleased]

## [1.2.3-beta.4]

### Added

- Expected beta release note.

## [1.2.3-Snapshot] - 2026-01-02

### Added

- Expected release note.

## [1.2.2-Snapshot] - 2025-12-01

- Older release note.
EOF

assert_equals '1.2.3-SNAPSHOT' \
  "$(read_gradle_version "$test_tmpdir/build.gradle.kts")" \
  'Gradle version parsing'
assert_equals '1.2.3-Snapshot' \
  "$(release_version_from_gradle_version '1.2.3-SNAPSHOT')" \
  'Snapshot release-version normalization'
assert_equals '1.2.3-alpha.1' \
  "$(release_version_from_gradle_version '1.2.3-alpha.1')" \
  'Alpha release-version preservation'
assert_equals '1.2.3-beta.4' \
  "$(release_version_from_gradle_version '1.2.3-beta.4')" \
  'Beta release-version preservation'
assert_equals '1.2.3-rc.0' \
  "$(release_version_from_gradle_version '1.2.3-rc.0')" \
  'Release-candidate version preservation'

beta_release_notes="$(extract_release_notes "$test_tmpdir/CHANGELOG.md" '1.2.3-beta.4')"
if ! printf '%s\n' "$beta_release_notes" | grep -q 'Expected beta release note'; then
  printf 'FAIL: matching beta changelog section was not extracted.\n' >&2
  exit 1
fi
if printf '%s\n' "$beta_release_notes" | grep -q 'Expected release note'; then
  printf 'FAIL: beta changelog extraction included the following release.\n' >&2
  exit 1
fi

release_notes="$(extract_release_notes "$test_tmpdir/CHANGELOG.md" '1.2.3-Snapshot')"
if ! printf '%s\n' "$release_notes" | grep -q 'Expected release note'; then
  printf 'FAIL: matching changelog section was not extracted.\n' >&2
  exit 1
fi
if printf '%s\n' "$release_notes" | grep -q 'Older release note'; then
  printf 'FAIL: changelog extraction included the following release.\n' >&2
  exit 1
fi
if release_version_from_gradle_version '1.2.3' >/dev/null 2>&1; then
  printf 'FAIL: stable version was accepted by the pre-release script.\n' >&2
  exit 1
fi
for invalid_version in \
  '1.2.3-BETA' \
  '1.2.3-beta' \
  '1.2.3-beta.01' \
  '01.2.3-beta.1' \
  '1.2-beta.1' \
  'release-SNAPSHOT' \
  '1.2.3-preview.1'; do
  if release_version_from_gradle_version "$invalid_version" >/dev/null 2>&1; then
    printf 'FAIL: invalid pre-release version was accepted: %s\n' "$invalid_version" >&2
    exit 1
  fi
done

printf '%s\n' \
  'version = "1.2.3-SNAPSHOT"' \
  'version = "2.0.0-SNAPSHOT"' \
  > "$test_tmpdir/duplicate-version.gradle.kts"
if read_gradle_version "$test_tmpdir/duplicate-version.gradle.kts" >/dev/null 2>&1; then
  printf 'FAIL: multiple Gradle version assignments were accepted.\n' >&2
  exit 1
fi
if extract_release_notes "$test_tmpdir/CHANGELOG.md" '9.9.9-Snapshot' >/dev/null 2>&1; then
  printf 'FAIL: missing changelog version was accepted.\n' >&2
  exit 1
fi

printf 'All prepare-gitlab-release tests passed.\n'
