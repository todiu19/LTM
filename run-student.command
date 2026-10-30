#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd -P)"
# shellcheck source=packaging/macos/common.sh
source "${PROJECT_ROOT}/packaging/macos/common.sh"
MAVEN="$(examwave_require_maven)"
cd "${PROJECT_ROOT}"
exec "${MAVEN}" javafx:run -Pstudent
