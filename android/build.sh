#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_DIR="${SCRIPT_DIR}"
JNI_LIBS_DIR="${PROJECT_DIR}/app/src/main/jniLibs"

echo "=== PdfCraft Android Build Tool ==="
echo "Repository root: ${REPO_ROOT}"

# Verify Rust targets
rustup target add aarch64-linux-android x86_64-linux-android || true

# Check cargo-ndk
if ! command -v cargo-ndk &> /dev/null; then
    echo "Installing cargo-ndk..."
    cargo install cargo-ndk
fi

# Create target directories
mkdir -p "${JNI_LIBS_DIR}/arm64-v8a"
mkdir -p "${JNI_LIBS_DIR}/x86_64"

# Compile native libraries
pushd "${REPO_ROOT}/apps/pdfcraft-android" > /dev/null
echo "Building cdylib for arm64-v8a and x86_64..."
cargo ndk -t arm64-v8a -t x86_64 -o "${JNI_LIBS_DIR}" build --release
popd > /dev/null

echo "Native libraries successfully compiled into: ${JNI_LIBS_DIR}"

if [[ "${1:-}" == "--apk" ]]; then
    pushd "${PROJECT_DIR}" > /dev/null
    echo "Building Android APK..."
    if [[ -f "./gradlew" ]]; then
        ./gradlew assembleDebug
    else
        gradle assembleDebug
    fi
    popd > /dev/null
    echo "APK build complete!"
fi
