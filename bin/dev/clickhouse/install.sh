#!/bin/sh
set -eu

VERSION="${CLICKHOUSE_VERSION:-v26.5.6.64-stable}"
INSTALL_ROOT="${CLICKHOUSE_TEST_ROOT:-$(pwd)/.local/clickhouse}"
BIN_DIR="${INSTALL_ROOT}/bin"
BIN="${BIN_DIR}/clickhouse"

case "$(uname -s)-$(uname -m)" in
  Darwin-arm64 | Darwin-aarch64)
    ASSET="clickhouse-macos-aarch64"
    PACKAGE_ARCH="arm64"
    ;;
  Darwin-x86_64 | Darwin-amd64)
    ASSET="clickhouse-macos"
    PACKAGE_ARCH="amd64"
    ;;
  *)
    echo "Unsupported platform. This installer currently targets macOS without Docker." >&2
    exit 1
    ;;
esac

URL="https://github.com/ClickHouse/ClickHouse/releases/download/${VERSION}/${ASSET}"
EXPECTED_VERSION="${VERSION#v}"
EXPECTED_VERSION="${EXPECTED_VERSION%%-*}"
CONFIG_DIR="${INSTALL_ROOT}/etc"
CONFIG_VERSION_FILE="${CONFIG_DIR}/version"

mkdir -p "${BIN_DIR}"
if [ -x "${BIN}" ] && "${BIN}" --version 2>/dev/null | grep -Fq "${EXPECTED_VERSION}" ; then
  echo "ClickHouse ${VERSION} is already installed at ${BIN}"
else
  TMP="${BIN}.download"
  trap 'rm -f "${TMP}"' EXIT HUP INT TERM
  curl --fail --location --retry 3 --output "${TMP}" "${URL}"
  chmod 0755 "${TMP}"
  mv "${TMP}" "${BIN}"
  trap - EXIT HUP INT TERM
fi

if [ ! -f "${CONFIG_VERSION_FILE}" ] \
  || [ "$(cat "${CONFIG_VERSION_FILE}")" != "${EXPECTED_VERSION}" ]; then
  CONFIG_ARCHIVE="clickhouse-server-${EXPECTED_VERSION}-${PACKAGE_ARCH}.tgz"
  CONFIG_URL="https://github.com/ClickHouse/ClickHouse/releases/download/${VERSION}/${CONFIG_ARCHIVE}"
  CONFIG_TMP="$(mktemp -d)"
  trap 'rm -rf "${CONFIG_TMP}"' EXIT HUP INT TERM
  curl --fail --location --retry 3 --output "${CONFIG_TMP}/${CONFIG_ARCHIVE}" "${CONFIG_URL}"
  tar -xzf "${CONFIG_TMP}/${CONFIG_ARCHIVE}" -C "${CONFIG_TMP}"
  mkdir -p "${CONFIG_DIR}"
  cp "${CONFIG_TMP}/clickhouse-server-${EXPECTED_VERSION}/etc/clickhouse-server/config.xml" \
    "${CONFIG_DIR}/config.xml"
  cp "${CONFIG_TMP}/clickhouse-server-${EXPECTED_VERSION}/etc/clickhouse-server/users.xml" \
    "${CONFIG_DIR}/users.xml"
  echo "${EXPECTED_VERSION}" >"${CONFIG_VERSION_FILE}"
  rm -rf "${CONFIG_TMP}"
  trap - EXIT HUP INT TERM
fi

mkdir -p "${CONFIG_DIR}/config.d"
{
  printf '%s\n' '<?xml version="1.0"?>'
  printf '%s\n' '<clickhouse>'
  printf '    <path>%s/data/</path>\n' "${INSTALL_ROOT}"
  printf '    <tmp_path>%s/data/tmp/</tmp_path>\n' "${INSTALL_ROOT}"
  printf '    <user_files_path>%s/data/user_files/</user_files_path>\n' "${INSTALL_ROOT}"
  printf '    <format_schema_path>%s/data/format_schemas/</format_schema_path>\n' "${INSTALL_ROOT}"
  printf '%s\n' '    <user_directories>'
  printf '%s\n' '        <local_directory>'
  printf '            <path>%s/data/access/</path>\n' "${INSTALL_ROOT}"
  printf '%s\n' '        </local_directory>'
  printf '%s\n' '    </user_directories>'
  printf '%s\n' '</clickhouse>'
} >"${CONFIG_DIR}/config.d/datastoria-local.xml"

"${BIN}" --version
echo "Installed ${VERSION} binary and server config under ${INSTALL_ROOT}"
