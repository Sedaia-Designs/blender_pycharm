#!/usr/bin/env bash

# Builds and publishes one plugin pre-release to Codeberg and JetBrains Marketplace.

release_all_error() {
  printf 'Error: %s\n' "$*" >&2
  return 1
}

release_all_usage() {
  cat <<'EOF'
Usage: scripts/release-all.sh [options]

Builds and publishes the pre-release declared in build.gradle.kts. By default,
the script publishes to both Codeberg and the JetBrains Marketplace dev channel.

Options:
  --check             Build and validate release inputs without remote changes,
                      signing, protected-secret access, or Marketplace publishing.
  --skip-build        Reuse the existing distribution ZIP.
  --create-tag        Create the expected annotated release tag at HEAD when absent.
  --push              Push the current branch and exact release tag before Codeberg publishing.
                      Implies --create-tag.
  --codeberg-only     Publish only to Codeberg.
  --marketplace-only  Publish only to JetBrains Marketplace.
  --yes               Skip the interactive publication confirmation.
  -h, --help          Show this help.

Prerequisites:
  - The worktree and index must be clean.
  - CHANGELOG.md must contain the matching pre-release section.
  - Codeberg publishing uses CODEBERG_TOKEN or the existing macOS Keychain item.
  - Marketplace publishing uses PUBLISH_TOKEN from the environment.
  - Marketplace signing uses the protected file paths configured in build.gradle.kts.
  - The first Marketplace publication must still be uploaded manually.

Examples:
  scripts/release-all.sh --check
  scripts/release-all.sh --push
  scripts/release-all.sh --marketplace-only
EOF
}

confirm_publication() {
  local expected="$1"
  local response

  printf 'Type %s to confirm these remote publication steps: ' "$expected"
  read -r response
  if [ "$response" != "$expected" ]; then
    release_all_error 'Publication confirmation did not match; nothing was published.'
    return 1
  fi
}

current_branch_name() {
  local branch

  branch="$(git branch --show-current)"
  if [ -z "$branch" ]; then
    release_all_error 'HEAD is detached; check out the intended release branch first.'
    return 1
  fi

  printf '%s\n' "$branch"
}

ensure_clean_worktree() {
  if [ -n "$(git status --porcelain --untracked-files=all)" ]; then
    release_all_error 'The worktree and index must be clean before publishing.'
    return 1
  fi
}

ensure_local_tag() {
  local tag_name="$1"
  local create_tag="$2"
  local head_commit
  local tag_commit

  head_commit="$(git rev-parse HEAD)"
  if git rev-parse --verify --quiet "refs/tags/$tag_name" >/dev/null; then
    tag_commit="$(git rev-list -n 1 "$tag_name")"
    if [ "$tag_commit" != "$head_commit" ]; then
      release_all_error "Tag $tag_name resolves to $tag_commit, but HEAD is $head_commit."
      return 1
    fi
    return 0
  fi

  if [ "$create_tag" != true ]; then
    release_all_error "Local tag $tag_name is missing. Re-run with --create-tag or --push."
    return 1
  fi

  git tag -a "$tag_name" -m "Blender Python Development $tag_name"
  printf 'Created annotated tag %s at %s.\n' "$tag_name" "$head_commit"
}

push_release_ref() {
  local remote="$1"
  local branch="$2"
  local tag_name="$3"

  git push "$remote" "$branch"
  git push "$remote" "refs/tags/$tag_name"
}

main() {
  set -euo pipefail

  local check_only=false
  local skip_build=false
  local create_tag=false
  local push_refs=false
  local publish_codeberg=true
  local publish_marketplace=true
  local assume_yes=false
  local selected_destination=false
  local script_dir
  local project_root
  local build_file
  local gradle_version
  local release_version
  local tag_name
  local asset_name
  local asset_path
  local branch
  local git_remote="${CODEBERG_GIT_REMOTE:-origin}"
  local confirmation

  while [ "$#" -gt 0 ]; do
    case "$1" in
      --check)
        check_only=true
        ;;
      --skip-build)
        skip_build=true
        ;;
      --create-tag)
        create_tag=true
        ;;
      --push)
        push_refs=true
        create_tag=true
        ;;
      --codeberg-only)
        if [ "$selected_destination" = true ]; then
          release_all_error 'Choose only one destination-specific option.'
          return 1
        fi
        publish_codeberg=true
        publish_marketplace=false
        selected_destination=true
        ;;
      --marketplace-only)
        if [ "$selected_destination" = true ]; then
          release_all_error 'Choose only one destination-specific option.'
          return 1
        fi
        publish_codeberg=false
        publish_marketplace=true
        selected_destination=true
        ;;
      --yes)
        assume_yes=true
        ;;
      -h|--help)
        release_all_usage
        return 0
        ;;
      *)
        release_all_usage >&2
        release_all_error "Unknown argument: $1"
        return 1
        ;;
    esac
    shift
  done

  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  project_root="$(cd "$script_dir/.." && pwd)"
  build_file="$project_root/build.gradle.kts"

  # shellcheck source=release-codeberg.sh
  source "$script_dir/release-codeberg.sh"

  gradle_version="$(read_gradle_version "$build_file")"
  release_version="$(release_version_from_gradle_version "$gradle_version")"
  tag_name="v$release_version"
  asset_name="BlenderPythonDevelopment-$gradle_version.zip"
  asset_path="$project_root/build/distributions/$asset_name"

  cd "$project_root"
  branch="$(current_branch_name)"
  ensure_clean_worktree

  printf 'Gradle version: %s\n' "$gradle_version"
  printf 'Release tag: %s\n' "$tag_name"
  printf 'Branch: %s\n' "$branch"
  printf 'Artifact: %s\n' "$asset_path"
  if [ "$publish_codeberg" = true ]; then
    printf 'Codeberg remote: %s\n' "$git_remote"
  fi
  if [ "$publish_marketplace" = true ]; then
    printf 'JetBrains Marketplace channel: dev\n'
  fi

  if [ "$skip_build" = false ]; then
    ./gradlew compileKotlin test buildPlugin --no-daemon
  fi
  if [ ! -f "$asset_path" ]; then
    release_all_error "Distribution ZIP not found: $asset_path"
    return 1
  fi

  ensure_local_tag "$tag_name" "$create_tag"

  if [ "$publish_codeberg" = true ]; then
    "$script_dir/release-codeberg.sh" --check
  else
    ./gradlew verifyPluginProjectConfiguration --no-daemon
  fi

  if [ "$check_only" = true ]; then
    printf 'Combined release inputs are valid; no remote changes, signing, or Marketplace publishing occurred.\n'
    return 0
  fi

  if [ "$publish_marketplace" = true ] && [ -z "${PUBLISH_TOKEN:-}" ]; then
    release_all_error 'PUBLISH_TOKEN must be set for JetBrains Marketplace publishing.'
    return 1
  fi

  confirmation="publish-$release_version"
  if [ "$assume_yes" = false ]; then
    confirm_publication "$confirmation"
  fi

  if [ "$push_refs" = true ]; then
    push_release_ref "$git_remote" "$branch" "$tag_name"
  fi

  if [ "$publish_codeberg" = true ]; then
    "$script_dir/release-codeberg.sh" --skip-build
  fi

  if [ "$publish_marketplace" = true ]; then
    ./gradlew publishPlugin --no-daemon
  fi

  unset PUBLISH_TOKEN || true
  printf 'Release %s completed for the selected destinations.\n' "$release_version"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
