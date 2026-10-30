#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd -P)"
# shellcheck source=common.sh
source "${PROJECT_ROOT}/packaging/macos/common.sh"
MAVEN="$(examwave_require_maven)"
JDK_HOME="$(examwave_require_jdk)"
MODE="${1:-both}"

cd "${PROJECT_ROOT}"
"${MAVEN}" -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime \
    -DoutputDirectory="${PROJECT_ROOT}/target/dependency"

INPUT_DIR="${PROJECT_ROOT}/target/mac-input"
IMAGE_DIR="${PROJECT_ROOT}/target/mac-images"
DIST_DIR="${PROJECT_ROOT}/dist"
rm -rf "${INPUT_DIR}" "${IMAGE_DIR}"
mkdir -p "${INPUT_DIR}" "${IMAGE_DIR}" "${DIST_DIR}"
cp "${PROJECT_ROOT}/target/examwave-macos-1.0.0.jar" "${INPUT_DIR}/examwave.jar"
cp "${PROJECT_ROOT}"/target/dependency/*.jar "${INPUT_DIR}/"

build_app() {
    local app_name="$1"
    local main_class="$2"
    local identifier="$3"
    local app_path="${IMAGE_DIR}/${app_name}.app"
    local artifact="${DIST_DIR}/${app_name}-macOS-$(uname -m).app.zip"
    rm -f "${artifact}"
    "${JDK_HOME}/bin/jpackage" \
        --type app-image \
        --name "${app_name}" \
        --app-version 1.0.0 \
        --vendor ExamWave \
        --input "${INPUT_DIR}" \
        --main-jar examwave.jar \
        --main-class "${main_class}" \
        --dest "${IMAGE_DIR}" \
        --java-options '-Dfile.encoding=UTF-8' \
        --mac-package-identifier "${identifier}" \
        --mac-package-name "${app_name}"
    ditto -c -k --sequesterRsrc --keepParent "${app_path}" "${artifact}"
    printf '%s\n' "Đã tạo ${artifact}"
}

case "${MODE}" in
    student) build_app ExamWaveStudent io.examwave.student.StudentLauncher io.examwave.student ;;
    teacher) build_app ExamWaveTeacher io.examwave.server.TeacherLauncher io.examwave.teacher ;;
    both)
        build_app ExamWaveStudent io.examwave.student.StudentLauncher io.examwave.student
        build_app ExamWaveTeacher io.examwave.server.TeacherLauncher io.examwave.teacher
        ;;
    *) printf '%s\n' 'Tham số phải là student, teacher hoặc both.' >&2; exit 2 ;;
esac
