#!/usr/bin/env bash

set -euo pipefail

job="${1:-prepare_release}"

run_prepare_release() {
  ./gradlew compileKotlin test buildPlugin --no-daemon
  bash scripts/prepare-gitlab-release.sh .release
  sha256sum "build/distributions/$(sed -n 's/^ASSET_NAME=//p' .release/release.env)"
}

run_marketplace_check() {
  command -v git
  ./gradlew compileKotlin test buildPlugin verifyPluginProjectConfiguration --no-daemon
  bash scripts/prepare-gitlab-release.sh .release
  test -s .release/release-notes.md
  PRIVATE_KEY=local-ci-placeholder CERTIFICATE_CHAIN=local-ci-placeholder ./gradlew signPlugin --dry-run --no-daemon
}

case "$job" in
  prepare_release)
    run_prepare_release
    ;;
  marketplace_check)
    run_marketplace_check
    ;;
  all)
    run_prepare_release
    run_marketplace_check
    ;;
  *)
    printf 'Unknown local CI job: %s\n' "$job" >&2
    printf 'Supported jobs: prepare_release, marketplace_check, all\n' >&2
    exit 2
    ;;
esac
