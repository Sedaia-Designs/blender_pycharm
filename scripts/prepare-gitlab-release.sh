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

  if [[ "$gradle_version" =~ ^${semver_number}\.${semver_number}\.${semver_number}$ ]]; then
    printf '%s\n' "$gradle_version"
    return
  fi

  if [[ "$gradle_version" =~ ^${semver_number}\.${semver_number}\.${semver_number}-(alpha|beta|rc)\.${semver_number}$ ]]; then
    printf '%s\n' "$gradle_version"
    return
  fi

  release_error "Expected X.Y.Z, X.Y.Z-SNAPSHOT, or X.Y.Z-(alpha|beta|rc).N version: $gradle_version"
  return 1
}

release_marketplace_channel() {
  local gradle_version="$1"
  release_version_from_gradle_version "$gradle_version" >/dev/null || return 1
  if [[ "$gradle_version" == *-* ]]; then
    printf 'dev\n'
  else
    printf 'default\n'
  fi
}

read_metadata_version() {
  local file="$1"
  local pattern="$2"
  local versions
  versions="$(sed -nE "$pattern" "$file")"
  if [ "$(printf '%s\n' "$versions" | awk 'NF { count++ } END { print count + 0 }')" -ne 1 ]; then
    release_error "Expected exactly one version in $file."
    return 1
  fi
  printf '%s\n' "$versions"
}

python_version_from_gradle_version() {
  local gradle_version="$1"
  case "$gradle_version" in
    *-alpha.*) printf '%sa%s\n' "${gradle_version%-alpha.*}" "${gradle_version##*.}" ;;
    *-beta.*) printf '%sb%s\n' "${gradle_version%-beta.*}" "${gradle_version##*.}" ;;
    *-rc.*) printf '%src%s\n' "${gradle_version%-rc.*}" "${gradle_version##*.}" ;;
    *-SNAPSHOT) printf '%s.dev0\n' "${gradle_version%-SNAPSHOT}" ;;
    *) printf '%s\n' "$gradle_version" ;;
  esac
}

validate_release_inputs() {
  local project_root="$1"
  local gradle_version="$2"
  local tag_name="$3"
  local asset_name="$4"
  local tag_policy="${5:-require_new}"
  local expected_python_version
  local build_version
  local gradle_properties_version
  local python_version
  local lock_version
  local remote_tags
  local release_version

  release_version="$(release_version_from_gradle_version "$gradle_version")" || return 1
  build_version="$(read_gradle_version "$project_root/build.gradle.kts")" || return 1
  expected_python_version="$(python_version_from_gradle_version "$gradle_version")"
  [ "$build_version" = "$gradle_version" ] || { release_error 'build.gradle.kts disagrees with the requested release version.'; return 1; }
  [ "$tag_name" = "v$release_version" ] || { release_error "Tag must be v$release_version."; return 1; }
  [ "$asset_name" = "BlenderPythonDevelopment-$gradle_version.zip" ] || { release_error "Artifact must be BlenderPythonDevelopment-$gradle_version.zip."; return 1; }
  gradle_properties_version="$(read_metadata_version "$project_root/gradle.properties" 's/^version=([^[:space:]]+)$/\1/p')" || return 1
  python_version="$(read_metadata_version "$project_root/pyproject.toml" 's/^version = "([^"]+)"$/\1/p')" || return 1
  lock_version="$(sed -n '/^name = "blender-pycharm"$/,/^\[\[package\]\]$/s/^version = "\([^"]*\)"$/\1/p' "$project_root/uv.lock")"

  [ "$gradle_properties_version" = "$gradle_version" ] || { release_error 'gradle.properties disagrees with build.gradle.kts.'; return 1; }
  [ "$python_version" = "$expected_python_version" ] || { release_error "pyproject.toml must use $expected_python_version."; return 1; }
  [ "$lock_version" = "$expected_python_version" ] || { release_error "uv.lock must use $expected_python_version."; return 1; }
  [ "${RELEASE_TAG:-$tag_name}" = "$tag_name" ] || { release_error "Requested tag must be $tag_name."; return 1; }
  [ -f "$project_root/build/distributions/$asset_name" ] || { release_error "Distribution ZIP not found: build/distributions/$asset_name"; return 1; }
  extract_release_notes "$project_root/CHANGELOG.md" "${tag_name#v}" >/dev/null || { release_error "CHANGELOG.md does not contain a section for ${tag_name#v}."; return 1; }

  if [ "$tag_policy" = 'require_new' ] && git -C "$project_root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    if git -C "$project_root" show-ref --verify --quiet "refs/tags/$tag_name"; then
      release_error "Tag already exists locally: $tag_name"
      return 1
    fi
    if git -C "$project_root" remote get-url origin >/dev/null 2>&1; then
      remote_tags="$(git -C "$project_root" ls-remote --tags origin "refs/tags/$tag_name")" || { release_error 'Could not check remote tags.'; return 1; }
      if [ -n "$remote_tags" ]; then
        release_error "Tag already exists on origin: $tag_name"
        return 1
      fi
    fi
  fi
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
  local marketplace_channel

  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  project_root="$(cd "$script_dir/.." && pwd)"
  gradle_version="$(read_gradle_version "$project_root/build.gradle.kts")"
  release_version="$(release_version_from_gradle_version "$gradle_version")"
  tag_name="v$release_version"
  asset_name="BlenderPythonDevelopment-$gradle_version.zip"
  marketplace_channel="$(release_marketplace_channel "$gradle_version")"
  validate_release_inputs "$project_root" "$gradle_version" "$tag_name" "$asset_name" || return 1

  mkdir -p "$output_dir"
  if ! extract_release_notes "$project_root/CHANGELOG.md" "$release_version" > "$output_dir/release-notes.md"; then
    release_error "CHANGELOG.md does not contain a section for $release_version."
    return 1
  fi

  {
    printf 'TAG_NAME=%s\n' "$tag_name"
    printf 'RELEASE_VERSION=%s\n' "$release_version"
    printf 'ASSET_NAME=%s\n' "$asset_name"
    printf 'MARKETPLACE_CHANNEL=%s\n' "$marketplace_channel"
  } > "$output_dir/release.env"

  printf 'Prepared GitLab release %s with artifact %s.\n' "$tag_name" "$asset_name"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
