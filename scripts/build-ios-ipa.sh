#!/usr/bin/env bash
set -euo pipefail

# Compila o cliente iOS SwiftUI (`VidaAcademicaUFOP`) para iOS Device (Release sem assinatura)
# e empacota o .ipa distribuível em `dist/Vida-Academica-UFOP-v<versao>-unsigned.ipa`.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
IOS_DIR="${REPO_ROOT}/ios-app"
BUILD_DIR="${REPO_ROOT}/target/ios-ipa-build"
DIST_DIR="${REPO_ROOT}/dist"
GRADLE_FILE="${REPO_ROOT}/android-app/app/build.gradle.kts"
ASSETS_DIR="${REPO_ROOT}/android-app/app/src/main/assets"
IOS_DATA_DIR="${IOS_DIR}/CampusUFOP/Resources/Data"

VERSION="${VERSION_OVERRIDE:-}"
if [[ -z "${VERSION}" && -f "${GRADLE_FILE}" ]]; then
  VERSION="$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' "${GRADLE_FILE}" | head -n 1)"
fi
VERSION="${VERSION:-1.9.6}"

BUILD_NUM="${BUILD_NUMBER_OVERRIDE:-}"
if [[ -z "${BUILD_NUM}" && -f "${GRADLE_FILE}" ]]; then
  BUILD_NUM="$(sed -n 's/.*versionCode *= *\([0-9][0-9]*\).*/\1/p' "${GRADLE_FILE}" | head -n 1)"
fi
BUILD_NUM="${BUILD_NUM:-1}"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "Erro: 'xcodegen' não encontrado no PATH. Instale via: brew install xcodegen" >&2
  exit 1
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "Erro: 'xcodebuild' não encontrado (necessário macOS com Xcode)." >&2
  exit 1
fi

echo "==> Sincronizando catálogos UFOP JSON para Resources/Data"
mkdir -p "${IOS_DATA_DIR}"
for json_asset in "ufop_matrizes_2026_2.json" "ufop_room_allocations_2026_2.json"; do
  if [[ -f "${ASSETS_DIR}/${json_asset}" ]]; then
    cp -f "${ASSETS_DIR}/${json_asset}" "${IOS_DATA_DIR}/${json_asset}"
  fi
done

echo "==> Gerando projeto Xcode com XcodeGen (versão ${VERSION}, build ${BUILD_NUM})"
rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}" "${DIST_DIR}"
xcodegen generate --spec "${IOS_DIR}/project.yml" --project "${IOS_DIR}"

echo "==> Compilando VidaAcademicaUFOP (Release / iphoneos sem code signing)"
xcodebuild \
  -project "${IOS_DIR}/VidaAcademicaUFOP.xcodeproj" \
  -scheme VidaAcademicaUFOP \
  -configuration Release \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "${BUILD_DIR}/DerivedData" \
  MARKETING_VERSION="${VERSION}" \
  CURRENT_PROJECT_VERSION="${BUILD_NUM}" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  build

APP_PATH="${BUILD_DIR}/DerivedData/Build/Products/Release-iphoneos/VidaAcademicaUFOP.app"
if [[ ! -d "${APP_PATH}" ]]; then
  echo "Erro: bundle compilado não encontrado em '${APP_PATH}'." >&2
  exit 1
fi

STAGE_DIR="${BUILD_DIR}/ipa-stage"
IPA_NAME="Vida-Academica-UFOP-v${VERSION}-unsigned.ipa"
IPA_OUT="${DIST_DIR}/${IPA_NAME}"

rm -rf "${STAGE_DIR}"
mkdir -p "${STAGE_DIR}/Payload"
cp -R "${APP_PATH}" "${STAGE_DIR}/Payload/VidaAcademicaUFOP.app"

echo "==> Empacotando ${IPA_NAME}"
rm -f "${IPA_OUT}" "${IPA_OUT}.sha256"
(
  cd "${STAGE_DIR}"
  zip -qry "${IPA_OUT}" Payload
)

(
  cd "${DIST_DIR}"
  shasum -a 256 "${IPA_NAME}" > "${IPA_NAME}.sha256"
)

echo "==> IPA gerado com sucesso: ${IPA_OUT}"
ls -lh "${IPA_OUT}"
cat "${IPA_OUT}.sha256"
