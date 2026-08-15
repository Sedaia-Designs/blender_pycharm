#!/usr/bin/env bash

# Builds and publishes one plugin pre-release to the JetBrains Marketplace dev channel.

release_marketplace_error() {
  printf 'Error: %s\n' "$*" >&2
  return 1
}

release_marketplace_usage() {
  cat <<'EOF'
Usage: scripts/release-marketplace.sh [options]

Builds and publishes the pre-release declared in build.gradle.kts to the
JetBrains Marketplace dev channel. The GitLab pipeline uses this script after
the manually approved GitLab release succeeds; it also remains available for
local validation and publication recovery.

Options:
  --check       Build and validate release inputs without signing, protected-secret
                access, or Marketplace publishing.
  --skip-build  Reuse the existing distribution ZIP.
  --yes         Skip the interactive publication confirmation.
  -h, --help    Show this help.

Prerequisites:
  - The worktree and index must be clean.
  - CHANGELOG.md must contain the matching pre-release section.
  - PUBLISH_TOKEN must be available in the environment for publication.
  - CI signing uses PRIVATE_KEY and CERTIFICATE_CHAIN environment variables.
  - Local signing falls back to the protected file paths configured in build.gradle.kts.
EOF
}

confirm_publication() {
  local expected="$1"
  local response

  printf 'Type %s to confirm Marketplace publication: ' "$expected"
  read -r response
  if [ "$response" != "$expected" ]; then
    release_marketplace_error 'Publication confirmation did not match; nothing was published.'
    return 1
  fi
}

ensure_clean_worktree() {
  if [ -n "$(git status --porcelain --untracked-files=all)" ]; then
    release_marketplace_error 'The worktree and index must be clean before publishing.'
    return 1
  fi
}

main() {
  set -euo pipefail

  local check_only=false
  local skip_build=false
  local assume_yes=false
  local script_dir
  local project_root
  local gradle_version
  local release_version
  local asset_name
  local asset_path
  local validation_dir

  while [ "$#" -gt 0 ]; do
    case "$1" in
      --check)
        check_only=true
        ;;
      --skip-build)
        skip_build=true
        ;;
      --yes)
        assume_yes=true
        ;;
      -h|--help)
        release_marketplace_usage
        return 0
        ;;
      *)
        release_marketplace_usage >&2
        release_marketplace_error "Unknown argument: $1"
        return 1
        ;;
    esac
    shift
  done

  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  project_root="$(cd "$script_dir/.." && pwd)"

  # shellcheck source=prepare-gitlab-release.sh
  source "$script_dir/prepare-gitlab-release.sh"

  gradle_version="$(read_gradle_version "$project_root/build.gradle.kts")"
  release_version="$(release_version_from_gradle_version "$gradle_version")"
  asset_name="BlenderPythonDevelopment-$gradle_version.zip"
  asset_path="$project_root/build/distributions/$asset_name"

  cd "$project_root"
  ensure_clean_worktree

  if [ "$skip_build" = false ]; then
    ./gradlew compileKotlin test buildPlugin --no-daemon
  fi
  if [ ! -f "$asset_path" ]; then
    release_marketplace_error "Distribution ZIP not found: $asset_path"
    return 1
  fi

  validation_dir="$(mktemp -d "${TMPDIR:-/tmp}/blender-marketplace-release.XXXXXX")"
  trap 'rm -rf -- "$validation_dir"' EXIT
  extract_release_notes "$project_root/CHANGELOG.md" "$release_version" > "$validation_dir/release-notes.md"
  ./gradlew verifyPluginProjectConfiguration --no-daemon

  if [ "$check_only" = true ]; then
    printf 'Marketplace release inputs are valid; no signing or publishing occurred.\n'
    return 0
  fi

  if [ -z "${PUBLISH_TOKEN:-}" ]; then
    release_marketplace_error 'PUBLISH_TOKEN must be set for JetBrains Marketplace publishing.'
    return 1
  fi
  if [ "$assume_yes" = false ]; then
    confirm_publication "publish-$release_version"
  fi

  ./gradlew publishPlugin --no-daemon
  unset PUBLISH_TOKEN || true
  printf 'Published %s to the JetBrains Marketplace dev channel.\n' "$release_version"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
