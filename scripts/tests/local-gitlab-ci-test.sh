#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if ! grep -qxF '.env/' "$project_root/.dockerignore"; then
  printf 'FAIL: Docker context does not exclude the protected .env directory.\n' >&2
  exit 1
fi

if ! git -C "$project_root" check-ignore --quiet .release/release-notes.md; then
  printf 'FAIL: Git does not ignore generated release artifacts.\n' >&2
  exit 1
fi

if ! grep -qF 'platform: ${LOCAL_CI_PLATFORM:-linux/amd64}' "$project_root/docker-compose.local-ci.yml"; then
  printf 'FAIL: Local CI does not default to the GitLab runner architecture.\n' >&2
  exit 1
fi

if ! grep -qF 'GRADLE_OPTS: -Dorg.gradle.vfs.watch=false' "$project_root/docker-compose.local-ci.yml"; then
  printf 'FAIL: Local CI does not disable unsupported container file watching.\n' >&2
  exit 1
fi

if ! grep -qF 'bash scripts/prepare-gitlab-release.sh .release' "$project_root/scripts/local-gitlab-ci-job.sh"; then
  printf 'FAIL: Local CI does not prepare GitLab release metadata.\n' >&2
  exit 1
fi

if ! grep -qF 'apk add --no-cache curl' "$project_root/.gitlab-ci.yml"; then
  printf 'FAIL: GitLab publish_release does not install curl.\n' >&2
  exit 1
fi

if ! grep -qF 'publish-release-check' "$project_root/docker-compose.local-ci.yml"; then
  printf 'FAIL: Local CI does not validate the publish-release image.\n' >&2
  exit 1
fi

if ! grep -qF 'apt-get install --yes --no-install-recommends git' "$project_root/.gitlab-ci.yml"; then
  printf 'FAIL: GitLab publish_marketplace does not install git.\n' >&2
  exit 1
fi

if ! grep -qF 'PRIVATE_KEY=local-ci-placeholder CERTIFICATE_CHAIN=local-ci-placeholder' "$project_root/scripts/local-gitlab-ci-job.sh"; then
  printf 'FAIL: Local CI does not validate environment-backed signing configuration.\n' >&2
  exit 1
fi

printf 'All local GitLab CI harness tests passed.\n'
