#!/usr/bin/env bash

examwave_find_maven() {
    if command -v mvn >/dev/null 2>&1; then
        command -v mvn
        return 0
    fi
    local wrapper_root="${HOME}/.m2/wrapper/dists"
    local candidate=""
    if [[ -d "${wrapper_root}" ]]; then
        while IFS= read -r candidate; do
            if [[ -x "${candidate}" ]]; then
                printf '%s\n' "${candidate}"
                return 0
            fi
        done < <(find "${wrapper_root}" -type f -path '*/bin/mvn' 2>/dev/null)
    fi
    return 1
}

examwave_require_maven() {
    local found=""
    if ! found="$(examwave_find_maven)"; then
        printf '%s\n' 'Không tìm thấy Maven 3.9+. Cài bằng: brew install maven' >&2
        return 1
    fi
    printf '%s\n' "${found}"
}

examwave_require_jdk() {
    if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/jpackage" ]]; then
        printf '%s\n' "${JAVA_HOME}"
        return 0
    fi
    if [[ -x /usr/libexec/java_home ]]; then
        local detected=""
        if detected="$(/usr/libexec/java_home -v 21+ 2>/dev/null)" && [[ -x "${detected}/bin/jpackage" ]]; then
            printf '%s\n' "${detected}"
            return 0
        fi
    fi
    printf '%s\n' 'Cần JDK 21 có jpackage.' >&2
    return 1
}
