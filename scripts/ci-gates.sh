#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

run_gate() {
  local gate="$1"
  export CI_GATE_NAME="$gate"
  if [ -n "${CI_GATE_TRACE:-}" ]; then printf '%s\n' "$gate" >> "$CI_GATE_TRACE"; fi
  case "$gate" in
    lock) uv lock --check ;;
    python) uv run --locked --python 3.11.7 pytest ;;
    ruff) uv run --locked --python 3.11.7 ruff check --no-fix src/main/python src/test/python && uv run --locked --python 3.11.7 ruff format --check src/main/python src/test/python ;;
    spotless) ./gradlew spotlessCheck --no-daemon ;;
    jvm) ./gradlew compileKotlin test --no-daemon ;;
    release-scripts)
      bash scripts/tests/prepare-github-release-test.sh
      bash scripts/tests/release-marketplace-test.sh
      bash scripts/tests/local-github-ci-test.sh
      bash scripts/tests/ci-gates-test.sh
      bash scripts/tests/verify-downloaded-release-test.sh
      ;;
    plugin-config) ./gradlew verifyPluginProjectConfiguration --no-daemon ;;
    plugin-verifier) ./gradlew verifyPlugin --no-daemon ;;
    *) printf 'Unknown CI gate: %s\n' "$gate" >&2; return 2 ;;
  esac
}

run_validation() {
  local gate
  for gate in lock python ruff spotless jvm release-scripts plugin-config plugin-verifier; do
    printf 'Running gate: %s\n' "$gate"
    run_gate "$gate"
  done
}

case "${1:-validate}" in
  gate) run_gate "${2:?Gate name required}" ;;
  validate) run_validation ;;
  prepare)
    ./gradlew clean --no-daemon
    run_validation
    ./gradlew buildPlugin --no-daemon
    bash scripts/prepare-github-release.sh .release
    bash scripts/check-release-artifact.sh .release
    ;;
  *) printf 'Usage: %s [validate|prepare|gate NAME]\n' "$0" >&2; exit 2 ;;
esac
