#!/usr/bin/env bash
set -euo pipefail

# Compila campus_core como XCFramework (iOS Device + Simulator) e gera os bindings Swift via UniFFI.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUST_CORE_DIR="${REPO_ROOT}/rust-core"
IOS_DIR="${REPO_ROOT}/ios-app"
BUILD_DIR="${REPO_ROOT}/target/ios-xcframework"
HEADERS_DIR="${BUILD_DIR}/headers"
SWIFT_OUT_DIR="${IOS_DIR}/CampusUFOP/CoreFFI/Generated"
XCFRAMEWORK_OUT="${IOS_DIR}/Frameworks/CampusCoreFFI.xcframework"
UNIFFI_CONFIG="${RUST_CORE_DIR}/uniffi.toml"

TARGETS=(
  "aarch64-apple-ios"
  "aarch64-apple-ios-sim"
  "x86_64-apple-ios"
)

if ! command -v cargo >/dev/null 2>&1; then
  echo "Erro: 'cargo' não encontrado no PATH." >&2
  exit 1
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "Erro: 'xcodebuild' não encontrado (necessário macOS com Xcode)." >&2
  exit 1
fi

cd "${REPO_ROOT}"

echo "==> Garantindo targets Rust para iOS"
for target in "${TARGETS[@]}"; do
  rustup target add "${target}" >/dev/null 2>&1 || true
done

echo "==> Compilando campus_core para targets iOS (release)"
for target in "${TARGETS[@]}"; do
  cargo build -p campus_core --target "${target}" --release
done

rm -rf "${BUILD_DIR}"
mkdir -p "${HEADERS_DIR}" "${SWIFT_OUT_DIR}" "$(dirname "${XCFRAMEWORK_OUT}")"

echo "==> Gerando bindings Swift e headers C via uniffi-bindgen"
HOST_LIB="${REPO_ROOT}/target/aarch64-apple-ios/release/libcampus_core.a"
if command -v uniffi-bindgen >/dev/null 2>&1; then
  uniffi-bindgen generate \
    --library "${HOST_LIB}" \
    --language swift \
    --config "${UNIFFI_CONFIG}" \
    --out-dir "${BUILD_DIR}/swift"
else
  cargo run -p campus_core --bin uniffi-bindgen -- generate \
    --library "${HOST_LIB}" \
    --language swift \
    --config "${UNIFFI_CONFIG}" \
    --out-dir "${BUILD_DIR}/swift"
fi

cp "${BUILD_DIR}/swift/"*.swift "${SWIFT_OUT_DIR}/"
cp "${BUILD_DIR}/swift/"*.h "${SWIFT_OUT_DIR}/"
if ls "${BUILD_DIR}/swift/"*.modulemap >/dev/null 2>&1; then
  cp "${BUILD_DIR}/swift/"*.modulemap "${SWIFT_OUT_DIR}/"
  cp "${BUILD_DIR}/swift/"*.modulemap "${HEADERS_DIR}/module.modulemap"
fi
cp "${BUILD_DIR}/swift/"*.h "${HEADERS_DIR}/"

echo "==> Criando fat static library para iOS Simulator"
mkdir -p "${BUILD_DIR}/sim"
lipo -create \
  "${REPO_ROOT}/target/aarch64-apple-ios-sim/release/libcampus_core.a" \
  "${REPO_ROOT}/target/x86_64-apple-ios/release/libcampus_core.a" \
  -output "${BUILD_DIR}/sim/libcampus_core.a"

echo "==> Empacotando CampusCoreFFI.xcframework"
rm -rf "${XCFRAMEWORK_OUT}"
xcodebuild -create-xcframework \
  -library "${REPO_ROOT}/target/aarch64-apple-ios/release/libcampus_core.a" \
  -headers "${HEADERS_DIR}" \
  -library "${BUILD_DIR}/sim/libcampus_core.a" \
  -headers "${HEADERS_DIR}" \
  -output "${XCFRAMEWORK_OUT}"

echo "==> Concluído! XCFramework em: ${XCFRAMEWORK_OUT}"
echo "==> Bindings Swift em: ${SWIFT_OUT_DIR}"
