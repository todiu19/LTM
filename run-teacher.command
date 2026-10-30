#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=packaging/macos/common.sh
source "${PROJECT_ROOT}/packaging/macos/common.sh"
MAVEN="$(examwave_require_maven)"
cd "${PROJECT_ROOT}"
export DB_URL="${DB_URL:-jdbc:mysql://localhost:3308/examwave_next?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh}"
export DB_USER="${DB_USER:-root}"
export DB_PASSWORD="${DB_PASSWORD:-root}"
exec "${MAVEN}" javafx:run -Pteacher
