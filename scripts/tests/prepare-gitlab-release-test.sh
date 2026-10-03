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

## [1.2.3]

### Changed

- Expected stable release note.

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
assert_equals '1.2.3' \
  "$(release_version_from_gradle_version '1.2.3')" \
  'Stable release-version preservation'
assert_equals 'default' "$(release_marketplace_channel '1.2.3')" 'Stable Marketplace channel'
for prerelease in '1.2.3-alpha.1' '1.2.3-beta.4' '1.2.3-rc.0' '1.2.3-SNAPSHOT'; do
  assert_equals 'dev' "$(release_marketplace_channel "$prerelease")" "Prerelease Marketplace channel: $prerelease"
done
assert_equals '1.2.3b4' "$(python_version_from_gradle_version '1.2.3-beta.4')" 'Python beta normalization'

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
for invalid_version in '01.2.3' '1.02.3' '1.2.03' '1.2.3+local' '1.2.3-beta.01'; do
  if release_version_from_gradle_version "$invalid_version" >/dev/null 2>&1; then
    printf 'FAIL: invalid stable or prerelease version was accepted: %s\n' "$invalid_version" >&2
    exit 1
  fi
done

fixture_root="$test_tmpdir/release-project"
mkdir -p "$fixture_root/build/distributions"
printf 'version = "1.2.3-beta.4"\n' > "$fixture_root/build.gradle.kts"
printf 'version=1.2.3-beta.4\n' > "$fixture_root/gradle.properties"
printf '[project]\nversion = "1.2.3b4"\n' > "$fixture_root/pyproject.toml"
printf '[[package]]\nname = "blender-pycharm"\nversion = "1.2.3b4"\n' > "$fixture_root/uv.lock"
cp "$test_tmpdir/CHANGELOG.md" "$fixture_root/CHANGELOG.md"
touch "$fixture_root/build/distributions/BlenderPythonDevelopment-1.2.3-beta.4.zip"
git -C "$fixture_root" init -q
git -C "$fixture_root" -c user.name='Release Test' -c user.email='release-test@example.invalid' commit --allow-empty -qm 'Release fixture'
if ! validate_release_inputs "$fixture_root" '1.2.3-beta.4' 'v1.2.3-beta.4' 'BlenderPythonDevelopment-1.2.3-beta.4.zip'; then
  printf 'FAIL: matching release metadata was rejected.\n' >&2
  exit 1
fi
printf 'version = "1.2.2"\n' > "$fixture_root/build.gradle.kts"
if validate_release_inputs "$fixture_root" '1.2.3-beta.4' 'v1.2.3-beta.4' 'BlenderPythonDevelopment-1.2.3-beta.4.zip' >/dev/null 2>&1; then
  printf 'FAIL: mismatched build.gradle.kts version was accepted.\n' >&2
  exit 1
fi
printf 'version = "1.2.3-beta.4"\n' > "$fixture_root/build.gradle.kts"
if RELEASE_TAG='v1.2.3' validate_release_inputs "$fixture_root" '1.2.3-beta.4' 'v1.2.3-beta.4' 'BlenderPythonDevelopment-1.2.3-beta.4.zip' >/dev/null 2>&1; then
  printf 'FAIL: mismatched requested tag was accepted.\n' >&2
  exit 1
fi
printf 'version=1.2.2\n' > "$fixture_root/gradle.properties"
if validate_release_inputs "$fixture_root" '1.2.3-beta.4' 'v1.2.3-beta.4' 'BlenderPythonDevelopment-1.2.3-beta.4.zip' >/dev/null 2>&1; then
  printf 'FAIL: mismatched Gradle properties were accepted.\n' >&2
  exit 1
fi
printf 'version=1.2.3-beta.4\n' > "$fixture_root/gradle.properties"
printf '[project]\nversion = "1.2.3"\n' > "$fixture_root/pyproject.toml"
if validate_release_inputs "$fixture_root" '1.2.3-beta.4' 'v1.2.3-beta.4' 'BlenderPythonDevelopment-1.2.3-beta.4.zip' >/dev/null 2>&1; then
  printf 'FAIL: mismatched Python version was accepted.\n' >&2
  exit 1
fi
printf '[project]\nversion = "1.2.3b4"\n' > "$fixture_root/pyproject.toml"
printf '[[package]]\nname = "blender-pycharm"\nversion = "1.2.3"\n' > "$fixture_root/uv.lock"
if validate_release_inputs "$fixture_root" '1.2.3-beta.4' 'v1.2.3-beta.4' 'BlenderPythonDevelopment-1.2.3-beta.4.zip' >/dev/null 2>&1; then
  printf 'FAIL: mismatched uv.lock version was accepted.\n' >&2
  exit 1
fi
printf '[[package]]\nname = "blender-pycharm"\nversion = "1.2.3b4"\n' > "$fixture_root/uv.lock"
git -C "$fixture_root" tag 'v1.2.3-beta.4'
if validate_release_inputs "$fixture_root" '1.2.3-beta.4' 'v1.2.3-beta.4' 'BlenderPythonDevelopment-1.2.3-beta.4.zip' >/dev/null 2>&1; then
  printf 'FAIL: existing tag was accepted.\n' >&2
  exit 1
fi
git -C "$fixture_root" tag -d 'v1.2.3-beta.4' >/dev/null
printf 'version = "1.2.3"\n' > "$fixture_root/build.gradle.kts"
printf 'version=1.2.3\n' > "$fixture_root/gradle.properties"
printf '[project]\nversion = "1.2.3"\n' > "$fixture_root/pyproject.toml"
printf '[[package]]\nname = "blender-pycharm"\nversion = "1.2.3"\n' > "$fixture_root/uv.lock"
touch "$fixture_root/build/distributions/BlenderPythonDevelopment-1.2.3.zip"
if ! validate_release_inputs "$fixture_root" '1.2.3' 'v1.2.3' 'BlenderPythonDevelopment-1.2.3.zip'; then
  printf 'FAIL: matching stable metadata was rejected.\n' >&2
  exit 1
fi
git init --bare -q "$test_tmpdir/origin.git"
git -C "$fixture_root" remote add origin "$test_tmpdir/origin.git"
git -C "$fixture_root" tag 'v1.2.3'
git -C "$fixture_root" push -q origin 'v1.2.3'
git -C "$fixture_root" tag -d 'v1.2.3' >/dev/null
if ! validate_release_inputs "$fixture_root" '1.2.3' 'v1.2.3' 'BlenderPythonDevelopment-1.2.3.zip' 'allow_existing'; then
  printf 'FAIL: Marketplace validation rejected the tag created by GitLab.\n' >&2
  exit 1
fi
if validate_release_inputs "$fixture_root" '1.2.3' 'v1.2.3' 'BlenderPythonDevelopment-1.2.3.zip' >/dev/null 2>&1; then
  printf 'FAIL: existing remote tag was accepted.\n' >&2
  exit 1
fi
git -C "$fixture_root" remote remove origin
mkdir -p "$fixture_root/scripts"
cp "$release_script" "$fixture_root/scripts/prepare-gitlab-release.sh"
bash "$fixture_root/scripts/prepare-gitlab-release.sh" "$test_tmpdir/stable-output" >/dev/null
if ! grep -q '^TAG_NAME=v1.2.3$' "$test_tmpdir/stable-output/release.env" ||
   ! grep -q '^ASSET_NAME=BlenderPythonDevelopment-1.2.3.zip$' "$test_tmpdir/stable-output/release.env" ||
   ! grep -q '^MARKETPLACE_CHANNEL=default$' "$test_tmpdir/stable-output/release.env"; then
  printf 'FAIL: stable metadata dry run produced the wrong release identity.\n' >&2
  exit 1
fi
if validate_release_inputs "$fixture_root" '1.2.3' 'v1.2.3' 'WrongArtifact.zip' >/dev/null 2>&1; then
  printf 'FAIL: mismatched artifact was accepted.\n' >&2
  exit 1
fi
if validate_release_inputs "$fixture_root" '1.2.3' 'v1.2.2' 'BlenderPythonDevelopment-1.2.3.zip' >/dev/null 2>&1; then
  printf 'FAIL: mismatched changelog heading was accepted.\n' >&2
  exit 1
fi

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
