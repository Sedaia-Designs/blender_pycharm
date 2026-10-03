#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
test_root="$(mktemp -d "${TMPDIR:-/tmp}/blender-downloaded-release-test.XXXXXX")"
trap 'rm -rf -- "$test_root"' EXIT
mkdir -p "$test_root/build/distributions" "$test_root/.release"
git -C "$test_root" init -q
git -C "$test_root" -c user.name='Release Test' -c user.email='release-test@example.invalid' commit --allow-empty -qm 'Release fixture'
source_sha="$(git -C "$test_root" rev-parse HEAD)"
asset_name=BlenderPythonDevelopment-1.2.3.zip
printf 'fixture plugin archive\n' > "$test_root/build/distributions/$asset_name"
printf 'Release notes\n' > "$test_root/.release/release-notes.md"
printf 'SOURCE_SHA=%s\nSOURCE_RUN_ID=123\nASSET_NAME=%s\nTAG_NAME=v1.2.3\nRELEASE_VERSION=1.2.3\n' "$source_sha" "$asset_name" > "$test_root/.release/release.env"
(cd "$test_root" && shasum -a 256 "build/distributions/$asset_name" > .release/SHA256SUMS)

if ! (cd "$test_root" && SOURCE_SHA="$source_sha" SOURCE_RUN_ID=123 bash "$project_root/scripts/verify-downloaded-release.sh") >/dev/null; then
  printf 'FAIL: matching downloaded release was rejected.\n' >&2
  exit 1
fi
if (cd "$test_root" && SOURCE_SHA="$source_sha" SOURCE_RUN_ID=456 bash "$project_root/scripts/verify-downloaded-release.sh") >/dev/null 2>&1; then
  printf 'FAIL: mismatched run ID was accepted.\n' >&2
  exit 1
fi
printf 'tampered\n' >> "$test_root/build/distributions/$asset_name"
if (cd "$test_root" && SOURCE_SHA="$source_sha" SOURCE_RUN_ID=123 bash "$project_root/scripts/verify-downloaded-release.sh") >/dev/null 2>&1; then
  printf 'FAIL: changed archive checksum was accepted.\n' >&2
  exit 1
fi

printf 'All downloaded-release integrity tests passed.\n'
