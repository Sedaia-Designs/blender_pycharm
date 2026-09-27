#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
release_script="$script_dir/../release-marketplace.sh"

# shellcheck source=../release-marketplace.sh
source "$release_script"

test_tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/release-marketplace-test.XXXXXX")"
trap 'rm -rf -- "$test_tmpdir"' EXIT

assert_fails() {
  local description="$1"
  shift

  if "$@" >/dev/null 2>&1; then
    printf 'FAIL: %s\n' "$description" >&2
    exit 1
  fi
}

if ! release_marketplace_usage | grep -q -- 'GitLab pipeline'; then
  printf 'FAIL: usage does not describe GitLab Marketplace publication.\n' >&2
  exit 1
fi

if ! release_marketplace_usage | grep -q -- 'PRIVATE_KEY and CERTIFICATE_CHAIN'; then
  printf 'FAIL: usage does not document CI signing variables.\n' >&2
  exit 1
fi

if ! printf 'publish-1.2.3-beta.1\n' | confirm_publication 'publish-1.2.3-beta.1' >/dev/null; then
  printf 'FAIL: exact publication confirmation was rejected.\n' >&2
  exit 1
fi

assert_fails \
  'mismatched publication confirmation was accepted' \
  sh -c "printf 'wrong-version\\n' | confirm_publication 'publish-1.2.3-beta.1'"

release_marketplace_validation_dir="$test_tmpdir/validation"
mkdir "$release_marketplace_validation_dir"
cleanup_release_marketplace_validation
if [ -e "$test_tmpdir/validation" ]; then
  printf 'FAIL: Marketplace validation directory was not cleaned.\n' >&2
  exit 1
fi
if [ -n "$release_marketplace_validation_dir" ]; then
  printf 'FAIL: Marketplace validation directory ownership was not released.\n' >&2
  exit 1
fi

printf 'All release-marketplace tests passed.\n'
