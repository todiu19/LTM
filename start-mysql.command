#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd -P)"
cd "${PROJECT_ROOT}"
if ! command -v docker >/dev/null 2>&1; then
    printf '%s\n' 'Không tìm thấy Docker Desktop.' >&2
    exit 1
fi
docker compose -f docker-compose.yml -f docker-compose.macos.yml up -d --wait
printf '%s\n' 'MySQL ExamWave đã sẵn sàng tại localhost:3308.'
