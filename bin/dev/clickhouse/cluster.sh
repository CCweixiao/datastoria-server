#!/bin/sh
set -eu

ROOT="${CLICKHOUSE_TEST_ROOT:-$(pwd)/.local/clickhouse}"
BIN="${ROOT}/bin/clickhouse"
DATA_DIR="${ROOT}/data"
LOG_DIR="${ROOT}/log"
RUN_DIR="${ROOT}/run"
PID_FILE="${RUN_DIR}/clickhouse.pid"
HTTP_PORT="${CLICKHOUSE_HTTP_PORT:-18123}"
TCP_PORT="${CLICKHOUSE_TCP_PORT:-19000}"

require_binary() {
  if [ ! -x "${BIN}" ]; then
    echo "ClickHouse is not installed. Run bin/dev/clickhouse/install.sh first." >&2
    exit 1
  fi
}

is_running() {
  [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null
}

start() {
  require_binary
  if is_running; then
    echo "ClickHouse is already running (pid $(cat "${PID_FILE}"))."
    return
  fi
  mkdir -p "${DATA_DIR}" "${LOG_DIR}" "${RUN_DIR}"
  rm -f "${PID_FILE}"
  "${BIN}" server \
    --daemon \
    --config-file="${ROOT}/etc/config.xml" \
    --pid-file="${PID_FILE}" \
    --log-file="${LOG_DIR}/clickhouse.log" \
    --errorlog-file="${LOG_DIR}/clickhouse.err.log" \
    -- \
    --path="${DATA_DIR}/" \
    --tmp_path="${DATA_DIR}/tmp/" \
    --user_files_path="${DATA_DIR}/user_files/" \
    --format_schema_path="${DATA_DIR}/format_schemas/" \
    --access_control_path="${DATA_DIR}/access/" \
    --preprocessed_configs_path="${DATA_DIR}/preprocessed_configs/" \
    --http_port="${HTTP_PORT}" \
    --tcp_port="${TCP_PORT}" \
    --listen_host=127.0.0.1

  attempts=0
  until curl --fail --silent "http://127.0.0.1:${HTTP_PORT}/ping" >/dev/null 2>&1; do
    attempts=$((attempts + 1))
    if [ "${attempts}" -ge 60 ] || { [ -f "${PID_FILE}" ] && ! is_running; }; then
      echo "ClickHouse failed to start. See ${LOG_DIR}/clickhouse.err.log." >&2
      rm -f "${PID_FILE}"
      exit 1
    fi
    sleep 1
  done
  echo "ClickHouse is ready at http://127.0.0.1:${HTTP_PORT}."
}

stop() {
  if ! is_running; then
    rm -f "${PID_FILE}"
    echo "ClickHouse is not running."
    return
  fi
  pid="$(cat "${PID_FILE}")"
  kill "${pid}"
  attempts=0
  while kill -0 "${pid}" 2>/dev/null; do
    attempts=$((attempts + 1))
    if [ "${attempts}" -ge 30 ]; then
      echo "ClickHouse did not stop within 30 seconds." >&2
      exit 1
    fi
    sleep 1
  done
  rm -f "${PID_FILE}"
  echo "ClickHouse stopped."
}

seed() {
  require_binary
  if ! is_running; then
    echo "ClickHouse is not running. Run '$0 start' first." >&2
    exit 1
  fi
  "${BIN}" client --host 127.0.0.1 --port "${TCP_PORT}" \
    --multiquery < bin/dev/clickhouse/seed.sql
  echo "Seeded the datastoria_test database."
}

status() {
  if is_running; then
    "${BIN}" client --host 127.0.0.1 --port "${TCP_PORT}" \
      --query "SELECT version(), uptime()"
  else
    echo "ClickHouse is not running."
    exit 1
  fi
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  restart) stop; start ;;
  seed) seed ;;
  status) status ;;
  *)
    echo "Usage: $0 {start|stop|restart|seed|status}" >&2
    exit 2
    ;;
esac
