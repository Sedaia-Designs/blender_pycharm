#!/usr/bin/env bash

set -euo pipefail

job="${1:-prepare_release}"
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v docker >/dev/null 2>&1; then
  printf 'Docker is required. Install and start Docker Desktop, then retry.\n' >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  printf 'The Docker engine is not running. Start Docker Desktop, then retry.\n' >&2
  exit 1
fi

cd "$project_root"
case "$job" in
  publish_release_check)
    docker compose -f docker-compose.local-ci.yml pull publish-release-check
    docker compose -f docker-compose.local-ci.yml run --rm publish-release-check
    ;;
  all)
    docker compose -f docker-compose.local-ci.yml build local-ci
    docker compose -f docker-compose.local-ci.yml run --rm local-ci all
    docker compose -f docker-compose.local-ci.yml pull publish-release-check
    docker compose -f docker-compose.local-ci.yml run --rm publish-release-check
    ;;
  *)
    docker compose -f docker-compose.local-ci.yml build local-ci
    docker compose -f docker-compose.local-ci.yml run --rm local-ci "$job"
    ;;
esac
