#!/usr/bin/env bash

# Validates and prepares metadata consumed by the manual GitLab release pipeline.

release_error() {
  printf 'Error: %s\n' "$*" >&2
  return 1
}

read_gradle_version() {
  local build_file="$1"
  local versions
  local version_count

  versions="$(sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"([^"]+)"[[:space:]]*$/\1/p' "$build_file")"
  version_count="$(printf '%s\n' "$versions" | awk 'NF { count++ } END { print count + 0 }')"

  if [ "$version_count" -ne 1 ]; then
    release_error "Expected exactly one literal version assignment in $build_file; found $version_count."
    return 1
  fi

  printf '%s\n' "$versions"
}

release_version_from_gradle_version() {
  local gradle_version="$1"
  local semver_number='(0|[1-9][0-9]*)'

  if [[ "$gradle_version" =~ ^${semver_number}\.${semver_number}\.${semver_number}-SNAPSHOT$ ]]; then
    printf '%s-Snapshot\n' "${gradle_version%-SNAPSHOT}"
    return
  fi

  if [[ "$gradle_version" =~ ^${semver_number}\.${semver_number}\.${semver_number}-(alpha|beta|rc)\.${semver_number}$ ]]; then
    printf '%s\n' "$gradle_version"
    return
  fi

  release_error "Expected X.Y.Z-SNAPSHOT or X.Y.Z-(alpha|beta|rc).N pre-release version: $gradle_version"
  return 1
}

extract_release_notes() {
  local changelog_file="$1"
  local release_version="$2"

  awk -v prefix="## [$release_version]" '
    $0 == prefix || index($0, prefix " - ") == 1 { found = 1 }
    found && /^## \[/ && index($0, prefix) != 1 { exit }
    found { print }
    END { if (!found) exit 4 }
  ' "$changelog_file"
}

main() {
  set -euo pipefail

  local output_dir="${1:-.release}"
  local script_dir
  local project_root
  local gradle_version
  local release_version
  local tag_name
  local asset_name

  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  project_root="$(cd "$script_dir/.." && pwd)"
  gradle_version="$(read_gradle_version "$project_root/build.gradle.kts")"
  release_version="$(release_version_from_gradle_version "$gradle_version")"
  tag_name="v$release_version"
  asset_name="BlenderPythonDevelopment-$gradle_version.zip"

  if [ ! -f "$project_root/build/distributions/$asset_name" ]; then
    release_error "Distribution ZIP not found: build/distributions/$asset_name"
    return 1
  fi

  mkdir -p "$output_dir"
  if ! extract_release_notes "$project_root/CHANGELOG.md" "$release_version" > "$output_dir/release-notes.md"; then
    release_error "CHANGELOG.md does not contain a section for $release_version."
    return 1
  fi

  {
    printf 'TAG_NAME=%s\n' "$tag_name"
    printf 'RELEASE_VERSION=%s\n' "$release_version"
    printf 'ASSET_NAME=%s\n' "$asset_name"
  } > "$output_dir/release.env"

  printf 'Prepared GitLab release %s with artifact %s.\n' "$tag_name" "$asset_name"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
