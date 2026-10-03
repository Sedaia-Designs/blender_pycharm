#!/usr/bin/env bash

set -euo pipefail

release_dir="${1:-.release}"
metadata="$release_dir/release.env"
test -s "$metadata"
test -s "$release_dir/release-notes.md"

# The metadata file contains only the four values emitted by prepare-github-release.sh.
asset_name="$(sed -n 's/^ASSET_NAME=//p' "$metadata")"
release_version="$(sed -n 's/^RELEASE_VERSION=//p' "$metadata")"
tag_name="$(sed -n 's/^TAG_NAME=//p' "$metadata")"
case "$asset_name" in
  BlenderPythonDevelopment-*.zip) ;;
  *) printf 'Invalid asset name: %s\n' "$asset_name" >&2; exit 1 ;;
esac
test "$tag_name" = "v$release_version"
test -s "build/distributions/$asset_name"

zip_count="$(find build/distributions -maxdepth 1 -type f -name 'BlenderPythonDevelopment-*.zip' -print | wc -l | tr -d ' ')"
test "$zip_count" = 1 || { printf 'Expected exactly one plugin distribution ZIP; found %s.\n' "$zip_count" >&2; exit 1; }

unzip -tqq "build/distributions/$asset_name"
archive_listing="$(unzip -Z1 "build/distributions/$asset_name")"
gradle_version="${asset_name#BlenderPythonDevelopment-}"
gradle_version="${gradle_version%.zip}"
jar_name="BlenderPythonDevelopment/lib/BlenderPythonDevelopment-$gradle_version.jar"
printf '%s\n' "$archive_listing" | grep -qxF "$jar_name"
test -n "$jar_name" || { printf 'Plugin distribution has no JAR.\n' >&2; exit 1; }
jar_tmp="$(mktemp "${TMPDIR:-/tmp}/blender-plugin-jar.XXXXXX")"
trap 'rm -f -- "$jar_tmp"' EXIT
unzip -p "build/distributions/$asset_name" "$jar_name" > "$jar_tmp"
jar_listing="$(unzip -Z1 "$jar_tmp")"
printf '%s\n' "$jar_listing" | grep -qx 'META-INF/plugin.xml'
printf '%s\n' "$jar_listing" | grep -qx 'blender-runtime/blender-runtime.zip'

shasum -a 256 "build/distributions/$asset_name" > "$release_dir/SHA256SUMS"
printf 'Verified release artifact %s.\n' "$asset_name"
