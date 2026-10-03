#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if ! grep -qxF '.env/' "$project_root/.dockerignore"; then
  printf 'FAIL: Docker context does not exclude the protected .env directory.\n' >&2
  exit 1
fi

if ! grep -qxF '.venv/' "$project_root/.dockerignore"; then
  printf 'FAIL: Docker context does not exclude the local Python environment.\n' >&2
  exit 1
fi

if ! grep -qxF '.release' "$project_root/.gitignore"; then
  printf 'FAIL: Git does not ignore generated release artifacts.\n' >&2
  exit 1
fi

if ! grep -qF 'platform: ${LOCAL_CI_PLATFORM:-linux/amd64}' "$project_root/docker-compose.local-ci.yml"; then
  printf 'FAIL: Local CI does not default to the GitHub runner architecture.\n' >&2
  exit 1
fi

if ! grep -qF 'GRADLE_OPTS: -Dorg.gradle.vfs.watch=false' "$project_root/docker-compose.local-ci.yml"; then
  printf 'FAIL: Local CI does not disable unsupported container file watching.\n' >&2
  exit 1
fi

if ! grep -qF 'bash scripts/prepare-github-release.sh .release' "$project_root/scripts/ci-gates.sh"; then
  printf 'FAIL: Local CI does not prepare release metadata.\n' >&2
  exit 1
fi

if ! grep -qF 'bash scripts/ci-gates.sh validate' "$project_root/.github/workflows/validate.yml"; then
  printf 'FAIL: GitHub validation does not use the local gate runner.\n' >&2
  exit 1
fi

if ! grep -qF 'gh release create' "$project_root/scripts/publish-github-release.sh"; then
  printf 'FAIL: GitHub release publication is missing.\n' >&2
  exit 1
fi

printf 'All local GitHub CI harness tests passed.\n'
