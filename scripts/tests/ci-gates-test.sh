#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
test_root="$(mktemp -d "${TMPDIR:-/tmp}/blender-ci-gates-test.XXXXXX")"
trap 'rm -rf -- "$test_root"' EXIT
mkdir -p "$test_root/scripts/tests" "$test_root/bin"
cp "$project_root/scripts/ci-gates.sh" "$test_root/scripts/ci-gates.sh"

for command_name in uv gradlew; do
  printf '#!/usr/bin/env bash\nif [ "${CI_GATE_NAME:-}" = "${FAIL_GATE:-}" ]; then exit 17; fi\nexit 0\n' > "$test_root/bin/$command_name"
  chmod +x "$test_root/bin/$command_name"
done
cp "$test_root/bin/gradlew" "$test_root/gradlew"
for test_name in prepare-github-release-test release-marketplace-test local-github-ci-test ci-gates-test verify-downloaded-release-test; do
  printf '#!/usr/bin/env bash\nif [ "${CI_GATE_NAME:-}" = "${FAIL_GATE:-}" ]; then exit 17; fi\nexit 0\n' > "$test_root/scripts/tests/$test_name.sh"
done

for gate in lock python ruff spotless jvm release-scripts plugin-config plugin-verifier; do
  : > "$test_root/trace"
  if (cd "$test_root" && PATH="$test_root/bin:$PATH" CI_GATE_NAME='' FAIL_GATE="$gate" CI_GATE_TRACE="$test_root/trace" bash scripts/ci-gates.sh prepare) >/dev/null 2>&1; then
    printf 'FAIL: release preparation accepted a failing %s gate.\n' "$gate" >&2
    exit 1
  fi
  if [ "$(tail -n 1 "$test_root/trace")" != "$gate" ]; then
    printf 'FAIL: preparation stopped before reaching the %s gate.\n' "$gate" >&2
    exit 1
  fi
  if [ -e "$test_root/.release/release.env" ]; then
    printf 'FAIL: a failing %s gate produced release metadata.\n' "$gate" >&2
    exit 1
  fi
done

if ! grep -qF 'run: bash scripts/verify-downloaded-release.sh' "$project_root/.github/workflows/publish.yml"; then
  printf 'FAIL: publication does not verify the downloaded artifact.\n' >&2
  exit 1
fi
if ! grep -qF 'run: bash scripts/ci-gates.sh prepare' "$project_root/.github/workflows/validate.yml"; then
  printf 'FAIL: release preparation bypasses the shared gate runner.\n' >&2
  exit 1
fi

printf 'All CI gate failure-path tests passed.\n'
