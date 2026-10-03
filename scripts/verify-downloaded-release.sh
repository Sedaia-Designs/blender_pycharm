#!/usr/bin/env bash

set -euo pipefail

metadata=.release/release.env
test -s "$metadata"
source_sha="$(sed -n 's/^SOURCE_SHA=//p' "$metadata")"
source_run_id="$(sed -n 's/^SOURCE_RUN_ID=//p' "$metadata")"
asset_name="$(sed -n 's/^ASSET_NAME=//p' "$metadata")"
tag_name="$(sed -n 's/^TAG_NAME=//p' "$metadata")"
test "$source_sha" = "${SOURCE_SHA:?Expected source SHA is required}"
test "$source_run_id" = "${SOURCE_RUN_ID:?Expected source run ID is required}"
test "$(git rev-parse HEAD)" = "$source_sha"
test -s "build/distributions/$asset_name"
test -s .release/release-notes.md
test -s .release/SHA256SUMS
shasum -a 256 -c .release/SHA256SUMS
test "$(find build/distributions -maxdepth 1 -type f -name 'BlenderPythonDevelopment-*.zip' -print | wc -l | tr -d ' ')" = 1
test "$tag_name" = "v$(sed -n 's/^RELEASE_VERSION=//p' "$metadata")"
printf 'Validated downloaded artifact for %s at %s.\n' "$tag_name" "$source_sha"
