#!/usr/bin/env bash

set -euo pipefail

test -n "${PUBLISH_TOKEN:-}"
test -n "${PRIVATE_KEY:-}"
test -n "${CERTIFICATE_CHAIN:-}"
asset_name="$(sed -n 's/^ASSET_NAME=//p' .release/release.env)"
test -s "build/distributions/$asset_name"
shasum -a 256 -c .release/SHA256SUMS

archive="build/distributions/$asset_name"
./gradlew publishPlugin --no-daemon -PvalidatedPluginArchive="$archive"
