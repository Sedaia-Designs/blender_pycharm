#!/usr/bin/env bash

# Publishes the snapshot version declared in build.gradle.kts as a Codeberg pre-release.

release_error() {
  printf 'Error: %s\n' "$*" >&2
  return 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || release_error "Required command not found: $1"
}

codeberg_curl() {
  local release_token="$1"
  shift

  printf 'header = "Authorization: token %s"\n' "$release_token" | curl --config - "$@"
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

  case "$gradle_version" in
    *-SNAPSHOT)
      printf '%s-Snapshot\n' "${gradle_version%-SNAPSHOT}"
      ;;
    *)
      release_error "Only -SNAPSHOT versions can be published with this script: $gradle_version"
      return 1
      ;;
  esac
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

usage() {
  cat <<'EOF'
Usage: scripts/release-codeberg.sh [--check] [--skip-build]

Publishes the -SNAPSHOT version declared in build.gradle.kts as an idempotent
Codeberg pre-release and uploads its Gradle distribution ZIP.

Options:
  --check       Validate and print local release inputs without Keychain or network access.
  --skip-build  Publish an existing distribution ZIP without running Gradle validation/build.
  -h, --help    Show this help.

Authentication:
  Uses CODEBERG_TOKEN when already set. Otherwise reads the generic-password item
  named "codeberg-release-token" for the current macOS account from Keychain.
EOF
}

main() {
  set -euo pipefail

  local check_only=false
  local skip_build=false
  local script_dir
  local project_root
  local build_file
  local changelog_file
  local gradle_version
  local release_version
  local tag_name
  local release_name
  local asset_name
  local asset_path
  local release_tmpdir
  local release_notes_path
  local local_hash
  local tag_commit
  local head_commit
  local remote_tag_commit
  local api_base="${CODEBERG_API_BASE:-https://codeberg.org/api/v1}"
  local repository="${CODEBERG_REPOSITORY:-SakuraSedaia/blender_pycharm}"
  local target_branch="${CODEBERG_TARGET_BRANCH:-main}"
  local git_remote="${CODEBERG_GIT_REMOTE:-origin}"
  local release_token
  local release_status
  local release_id
  local existing_asset_id
  local asset_url
  local remote_hash
  local release_url

  while [ "$#" -gt 0 ]; do
    case "$1" in
      --check)
        check_only=true
        ;;
      --skip-build)
        skip_build=true
        ;;
      -h|--help)
        usage
        return 0
        ;;
      *)
        usage >&2
        release_error "Unknown argument: $1"
        return 1
        ;;
    esac
    shift
  done

  require_command awk
  require_command git
  require_command sed
  require_command shasum

  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  project_root="$(cd "$script_dir/.." && pwd)"
  build_file="$project_root/build.gradle.kts"
  changelog_file="$project_root/CHANGELOG.md"

  gradle_version="$(read_gradle_version "$build_file")"
  release_version="$(release_version_from_gradle_version "$gradle_version")"
  tag_name="v$release_version"
  release_name="Blender Python Development $tag_name"
  asset_name="BlenderPythonDevelopment-$gradle_version.zip"
  asset_path="$project_root/build/distributions/$asset_name"

  release_tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/blender-pycharm-release.XXXXXX")"
  release_cleanup_dir="$release_tmpdir"
  trap 'rm -rf -- "$release_cleanup_dir"' EXIT
  release_notes_path="$release_tmpdir/release-notes.md"

  if ! extract_release_notes "$changelog_file" "$release_version" > "$release_notes_path"; then
    release_error "CHANGELOG.md does not contain a section for $release_version."
    return 1
  fi

  cd "$project_root"
  if [ -n "$(git status --porcelain --untracked-files=all)" ]; then
    release_error 'The worktree and index must be clean before publishing.'
    return 1
  fi

  if ! git rev-parse --verify --quiet "refs/tags/$tag_name" >/dev/null; then
    release_error "Local tag does not exist: $tag_name"
    return 1
  fi

  tag_commit="$(git rev-list -n 1 "$tag_name")"
  head_commit="$(git rev-parse HEAD)"
  if [ "$tag_commit" != "$head_commit" ]; then
    release_error "Tag $tag_name resolves to $tag_commit, but HEAD is $head_commit."
    return 1
  fi

  if [ "$check_only" = false ] && [ "$skip_build" = false ]; then
    ./gradlew compileKotlin test buildPlugin --no-daemon
  fi

  if [ ! -f "$asset_path" ]; then
    release_error "Distribution ZIP not found: $asset_path"
    return 1
  fi

  local_hash="$(shasum -a 256 "$asset_path" | awk '{print $1}')"
  printf 'Gradle version: %s\n' "$gradle_version"
  printf 'Release version: %s\n' "$release_version"
  printf 'Tag: %s\n' "$tag_name"
  printf 'Artifact: %s\n' "$asset_name"
  printf 'Artifact SHA-256: %s\n' "$local_hash"

  if [ "$check_only" = true ]; then
    printf 'Local release inputs are valid; no Keychain or network access was used.\n'
    return 0
  fi

  require_command curl
  require_command jq

  remote_tag_commit="$(git ls-remote --tags "$git_remote" "refs/tags/$tag_name^{}" | awk 'NR == 1 { print $1 }')"
  if [ -z "$remote_tag_commit" ]; then
    remote_tag_commit="$(git ls-remote --tags "$git_remote" "refs/tags/$tag_name" | awk 'NR == 1 { print $1 }')"
  fi
  if [ -z "$remote_tag_commit" ]; then
    release_error "Remote tag does not exist on $git_remote: $tag_name"
    return 1
  fi
  if [ "$remote_tag_commit" != "$head_commit" ]; then
    release_error "Remote tag $git_remote/$tag_name resolves to $remote_tag_commit, but HEAD is $head_commit."
    return 1
  fi

  if [ -n "${CODEBERG_TOKEN:-}" ]; then
    release_token="$CODEBERG_TOKEN"
  else
    require_command security
    release_token="$(security find-generic-password -a "$USER" -s codeberg-release-token -w)"
  fi
  if [ -z "$release_token" ]; then
    release_error 'Codeberg token is empty.'
    return 1
  fi
  codeberg_curl "$release_token" --fail-with-body --silent --show-error \
    "$api_base/repos/$repository" \
    -o "$release_tmpdir/repository.json"
  if ! jq -e '.permissions.push == true' "$release_tmpdir/repository.json" >/dev/null; then
    release_error "Token does not have push permission for $repository."
    return 1
  fi

  release_status="$(codeberg_curl "$release_token" --silent --show-error \
    -o "$release_tmpdir/release.json" \
    -w '%{http_code}' \
    "$api_base/repos/$repository/releases/tags/$tag_name")"

  case "$release_status" in
    200)
      printf 'Release already exists; reusing it.\n'
      ;;
    404)
      jq -n \
        --arg tag_name "$tag_name" \
        --arg target_commitish "$target_branch" \
        --arg name "$release_name" \
        --rawfile body "$release_notes_path" \
        '{tag_name:$tag_name,target_commitish:$target_commitish,name:$name,body:$body,draft:false,prerelease:true}' \
        > "$release_tmpdir/payload.json"
      codeberg_curl "$release_token" --fail-with-body --silent --show-error \
        -X POST \
        -H 'Content-Type: application/json' \
        --data-binary @"$release_tmpdir/payload.json" \
        "$api_base/repos/$repository/releases" \
        -o "$release_tmpdir/release.json"
      printf 'Release created.\n'
      ;;
    *)
      release_error "Release lookup returned HTTP $release_status."
      return 1
      ;;
  esac

  if ! jq -e '.id | type == "number"' "$release_tmpdir/release.json" >/dev/null; then
    release_error 'Release response does not contain a numeric release ID.'
    return 1
  fi

  if ! jq -e \
    --arg tag "$tag_name" \
    --arg name "$release_name" \
    '.tag_name == $tag and .name == $name and .draft == false and .prerelease == true' \
    "$release_tmpdir/release.json" >/dev/null; then
    release_error 'Existing release metadata does not match the expected published pre-release.'
    return 1
  fi

  release_id="$(jq -r '.id' "$release_tmpdir/release.json")"
  existing_asset_id="$(jq -r --arg name "$asset_name" '.assets[]? | select(.name == $name) | .id' \
    "$release_tmpdir/release.json" | head -n 1)"
  if [ -z "$existing_asset_id" ]; then
    codeberg_curl "$release_token" --fail-with-body --silent --show-error \
      -X POST \
      -H 'Content-Type: application/octet-stream' \
      --data-binary @"$asset_path" \
      "$api_base/repos/$repository/releases/$release_id/assets?name=$asset_name" \
      -o "$release_tmpdir/asset.json"
    printf 'Release asset uploaded.\n'
  else
    printf 'Release asset already exists; verifying it.\n'
  fi

  codeberg_curl "$release_token" --fail-with-body --silent --show-error \
    "$api_base/repos/$repository/releases/tags/$tag_name" \
    -o "$release_tmpdir/final-release.json"
  if ! jq -e \
    '(.body | contains("### Added")) and
     (.body | contains("### Changed")) and
     (.body | contains("### Fixed")) and
     (.body | contains("### Removed")) and
     (.body | contains("### Known Issues"))' \
    "$release_tmpdir/final-release.json" >/dev/null; then
    release_error 'Published release notes are missing one or more required sections.'
    return 1
  fi

  asset_url="$(jq -r --arg name "$asset_name" \
    '.assets[]? | select(.name == $name) | .browser_download_url' \
    "$release_tmpdir/final-release.json")"
  if [ -z "$asset_url" ] || [ "$asset_url" = null ]; then
    release_error "Published release does not contain $asset_name."
    return 1
  fi

  codeberg_curl "$release_token" --fail-with-body --silent --show-error --location \
    "$asset_url" \
    -o "$release_tmpdir/downloaded.zip"
  remote_hash="$(shasum -a 256 "$release_tmpdir/downloaded.zip" | awk '{print $1}')"
  if [ "$remote_hash" != "$local_hash" ]; then
    release_error "Published asset SHA-256 $remote_hash does not match local SHA-256 $local_hash."
    return 1
  fi

  release_url="$(jq -r '.html_url' "$release_tmpdir/final-release.json")"
  unset release_token
  printf 'Published asset SHA-256 verified: %s\n' "$remote_hash"
  printf 'Release URL: %s\n' "$release_url"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
