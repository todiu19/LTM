#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd -P)"
exec "${PROJECT_ROOT}/packaging/macos/Build-MacApps.sh" student
