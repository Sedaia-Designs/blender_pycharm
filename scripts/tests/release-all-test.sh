#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
release_script="$script_dir/../release-all.sh"

# shellcheck source=../release-all.sh
source "$release_script"

test_tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/release-all-test.XXXXXX")"
trap 'rm -rf -- "$test_tmpdir"' EXIT

assert_fails() {
  local description="$1"
  shift

  if "$@" >/dev/null 2>&1; then
    printf 'FAIL: %s\n' "$description" >&2
    exit 1
  fi
}

if ! release_all_usage | grep -q -- '--marketplace-only'; then
  printf 'FAIL: usage does not document destination-specific recovery.\n' >&2
  exit 1
fi

if ! printf 'publish-1.2.3-beta.1\n' | confirm_publication 'publish-1.2.3-beta.1' >/dev/null; then
  printf 'FAIL: exact publication confirmation was rejected.\n' >&2
  exit 1
fi

assert_fails \
  'mismatched publication confirmation was accepted' \
  sh -c "printf 'wrong-version\\n' | confirm_publication 'publish-1.2.3-beta.1'"

mkdir -p "$test_tmpdir/bin"
cat > "$test_tmpdir/bin/git" <<'EOF'
#!/usr/bin/env bash
case "$1" in
  branch)
    printf 'main\n'
    ;;
  status)
    ;;
  rev-parse)
    if [ "$2" = HEAD ]; then
      printf 'abc123\n'
    else
      exit 1
    fi
    ;;
  tag)
    printf '%s\n' "$*" >> "$RELEASE_ALL_TEST_LOG"
    ;;
  *)
    printf 'Unexpected git command: %s\n' "$*" >&2
    exit 1
    ;;
esac
EOF
chmod +x "$test_tmpdir/bin/git"

RELEASE_ALL_TEST_LOG="$test_tmpdir/git.log"
export RELEASE_ALL_TEST_LOG
PATH="$test_tmpdir/bin:$PATH"
export PATH

test_repo="$test_tmpdir/repository"
mkdir -p "$test_repo"
cd "$test_repo"
ensure_local_tag 'v1.2.3-beta.1' true

if ! grep -q 'tag -a v1.2.3-beta.1' "$RELEASE_ALL_TEST_LOG"; then
  printf 'FAIL: missing release tag was not created as an annotated tag.\n' >&2
  exit 1
fi

assert_fails \
  'missing release tag was accepted without --create-tag' \
  ensure_local_tag 'v1.2.4-beta.1' false

printf 'All release-all tests passed.\n'
