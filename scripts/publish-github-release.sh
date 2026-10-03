#!/usr/bin/env bash

set -euo pipefail

test -n "${GH_TOKEN:-}"
metadata=.release/release.env
tag_name="$(sed -n 's/^TAG_NAME=//p' "$metadata")"
asset_name="$(sed -n 's/^ASSET_NAME=//p' "$metadata")"
source_sha="$(sed -n 's/^SOURCE_SHA=//p' "$metadata")"
test -n "$tag_name"
test -n "$asset_name"
test -n "$source_sha"
if gh release view "$tag_name" --json targetCommitish --jq .targetCommitish >/dev/null 2>&1; then
  existing_target="$(gh release view "$tag_name" --json targetCommitish --jq .targetCommitish)"
  test "$existing_target" = "$source_sha" || { printf 'Existing release targets another commit.\n' >&2; exit 1; }
  git fetch --no-tags origin "refs/tags/$tag_name"
  test "$(git rev-parse 'FETCH_HEAD^{commit}')" = "$source_sha" || { printf 'Existing release tag points to another commit.\n' >&2; exit 1; }
  existing_assets="$(mktemp -d "${TMPDIR:-/tmp}/blender-existing-release.XXXXXX")"
  trap 'rm -rf -- "$existing_assets"' EXIT
  gh release download "$tag_name" --dir "$existing_assets" --pattern "$asset_name" --pattern SHA256SUMS
  test -s "$existing_assets/$asset_name"
  test -s "$existing_assets/SHA256SUMS"
  cmp .release/SHA256SUMS "$existing_assets/SHA256SUMS"
  expected_digest="$(awk '{print $1}' .release/SHA256SUMS)"
  actual_digest="$(shasum -a 256 "$existing_assets/$asset_name" | awk '{print $1}')"
  test "$expected_digest" = "$actual_digest"
  printf 'Existing GitHub release matches the validated artifact.\n'
  exit 0
fi
if git ls-remote --exit-code --tags origin "refs/tags/$tag_name" >/dev/null; then
  printf 'Tag exists without a matching release: %s\n' "$tag_name" >&2
  exit 1
else
  remote_status=$?
  test "$remote_status" = 2 || { printf 'Could not check the remote release tag.\n' >&2; exit 1; }
fi
gh release create "$tag_name" "build/distributions/$asset_name" .release/SHA256SUMS \
  --target "$source_sha" --title "Blender Python Development $tag_name" \
  --notes-file .release/release-notes.md
git fetch --no-tags origin "refs/tags/$tag_name"
test "$(git rev-parse 'FETCH_HEAD^{commit}')" = "$source_sha"
