#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
release_script="$script_dir/../release-marketplace.sh"

# shellcheck source=../release-marketplace.sh
source "$release_script"

assert_fails() {
  local description="$1"
  shift

  if "$@" >/dev/null 2>&1; then
    printf 'FAIL: %s\n' "$description" >&2
    exit 1
  fi
}

if ! release_marketplace_usage | grep -q -- '.gitlab-ci.yml'; then
  printf 'FAIL: usage does not direct GitLab releases to the manual pipeline.\n' >&2
  exit 1
fi

if ! printf 'publish-1.2.3-beta.1\n' | confirm_publication 'publish-1.2.3-beta.1' >/dev/null; then
  printf 'FAIL: exact publication confirmation was rejected.\n' >&2
  exit 1
fi

assert_fails \
  'mismatched publication confirmation was accepted' \
  sh -c "printf 'wrong-version\\n' | confirm_publication 'publish-1.2.3-beta.1'"

printf 'All release-marketplace tests passed.\n'
