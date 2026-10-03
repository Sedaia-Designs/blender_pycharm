#!/usr/bin/env bash

set -euo pipefail

job="${1:-prepare}"
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
  prepare|validate|gate)
    docker compose -f docker-compose.local-ci.yml build local-ci
    docker compose -f docker-compose.local-ci.yml run --rm local-ci "$@"
    ;;
  *)
    printf 'Supported jobs: prepare, validate, gate NAME\n' >&2
    exit 2
    ;;
esac
