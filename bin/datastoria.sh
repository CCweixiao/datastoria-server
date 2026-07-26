#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
INSTALL_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
CONF_DIR="$INSTALL_DIR/conf"
RUN_DIR="$INSTALL_DIR/run"
LOG_DIR="$INSTALL_DIR/logs"
DATA_DIR="$INSTALL_DIR/data"
ENV_FILE="${DATASTORIA_ENV_FILE:-$CONF_DIR/datastoria.env}"

mkdir -p "$RUN_DIR" "$LOG_DIR" "$DATA_DIR"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

DATASTORIA_PROFILE="${DATASTORIA_PROFILE:-local}"
BACKEND_HOST="${BACKEND_HOST:-127.0.0.1}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_HOST="${FRONTEND_HOST:-0.0.0.0}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
if [[ -z "${JAVA_BIN:-}" && -n "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="${JAVA_BIN:-java}"
fi
NODE_BIN="${NODE_BIN:-node}"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx1024m}"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"
LOCAL_KEY_FILE="$CONF_DIR/.local-master-key"

is_running() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null
}

wait_for_url() {
  local name="$1" url="$2" pid_file="$3"
  local attempts="${STARTUP_ATTEMPTS:-60}"
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if command -v curl >/dev/null 2>&1 && curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    if ! is_running "$pid_file"; then
      echo "$name exited during startup. See $LOG_DIR." >&2
      return 1
    fi
    sleep 1
  done
  echo "$name did not become ready: $url" >&2
  return 1
}

ensure_local_master_key() {
  if [[ "$DATASTORIA_PROFILE" != "local" || -n "${DATASTORIA_MASTER_KEY:-}" ]]; then
    return
  fi
  if [[ ! -s "$LOCAL_KEY_FILE" ]]; then
    umask 077
    dd if=/dev/urandom bs=32 count=1 2>/dev/null | base64 >"$LOCAL_KEY_FILE"
    echo "Generated local encryption key: $LOCAL_KEY_FILE"
  fi
  DATASTORIA_MASTER_KEY="$(tr -d '\r\n' <"$LOCAL_KEY_FILE")"
  export DATASTORIA_MASTER_KEY
}

initialize() {
  if [[ ! -f "$ENV_FILE" ]]; then
    cp "$CONF_DIR/datastoria.env.example" "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    echo "Created runtime configuration: $ENV_FILE"
  fi
  ensure_local_master_key
  echo "DataStoria initialization complete (profile $DATASTORIA_PROFILE)."
}

start_backend() {
  if is_running "$BACKEND_PID_FILE"; then
    echo "Backend already running (PID $(cat "$BACKEND_PID_FILE"))."
    return
  fi
  if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
    echo "Java runtime not found: $JAVA_BIN" >&2
    return 1
  fi
  local java_major
  java_major="$("$JAVA_BIN" -version 2>&1 | awk -F '[".]' '/version/ {print ($2 == "1" ? $3 : $2); exit}')"
  if [[ "$java_major" != "17" ]]; then
    echo "JDK 17 is required to run DataStoria (found: ${java_major:-unknown}). Set JAVA_HOME or JAVA_BIN." >&2
    return 1
  fi
  local -a java_opts
  read -r -a java_opts <<<"$JAVA_OPTS"
  (
    cd "$INSTALL_DIR"
    exec nohup "$JAVA_BIN" "${java_opts[@]}" -jar app/backend/datastoria-server.jar \
      "--spring.profiles.active=$DATASTORIA_PROFILE" \
      "--spring.config.additional-location=optional:file:$CONF_DIR/" \
      "--server.address=$BACKEND_HOST" \
      "--server.port=$BACKEND_PORT"
  ) >>"$LOG_DIR/backend.log" 2>&1 &
  echo $! >"$BACKEND_PID_FILE"
  if ! wait_for_url "Backend" "http://$BACKEND_HOST:$BACKEND_PORT/actuator/health" "$BACKEND_PID_FILE"; then
    stop_process "Backend" "$BACKEND_PID_FILE"
    return 1
  fi
  echo "Backend started: http://$BACKEND_HOST:$BACKEND_PORT"
}

start_frontend() {
  if is_running "$FRONTEND_PID_FILE"; then
    echo "Frontend already running (PID $(cat "$FRONTEND_PID_FILE"))."
    return
  fi
  if ! command -v "$NODE_BIN" >/dev/null 2>&1; then
    echo "Node.js runtime not found: $NODE_BIN" >&2
    return 1
  fi
  local node_major
  node_major="$("$NODE_BIN" --version | sed 's/^v//' | cut -d. -f1)"
  if ((node_major < 20)); then
    echo "Node.js 20 or newer is required (found: $node_major)." >&2
    return 1
  fi
  (
    cd "$INSTALL_DIR/app/frontend"
    export HOSTNAME="$FRONTEND_HOST"
    export PORT="$FRONTEND_PORT"
    export DATASTORIA_JAVA_INTERNAL_URL="http://$BACKEND_HOST:$BACKEND_PORT"
    exec nohup "$NODE_BIN" server.js
  ) >>"$LOG_DIR/frontend.log" 2>&1 &
  echo $! >"$FRONTEND_PID_FILE"
  if ! wait_for_url "Frontend" "http://127.0.0.1:$FRONTEND_PORT/" "$FRONTEND_PID_FILE"; then
    stop_process "Frontend" "$FRONTEND_PID_FILE"
    return 1
  fi
  echo "Frontend started: http://$FRONTEND_HOST:$FRONTEND_PORT"
}

stop_process() {
  local name="$1" pid_file="$2"
  if ! is_running "$pid_file"; then
    rm -f "$pid_file"
    echo "$name is not running."
    return
  fi
  local pid
  pid="$(cat "$pid_file")"
  kill "$pid"
  for _ in {1..20}; do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.25
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid"
  fi
  rm -f "$pid_file"
  echo "$name stopped."
}

start_all() {
  ensure_local_master_key
  start_backend
  if ! start_frontend; then
    stop_process "Backend" "$BACKEND_PID_FILE"
    return 1
  fi
  echo "DataStoria is ready at http://127.0.0.1:$FRONTEND_PORT"
}

status_all() {
  if is_running "$BACKEND_PID_FILE"; then
    echo "Backend: running (PID $(cat "$BACKEND_PID_FILE"), profile $DATASTORIA_PROFILE)"
  else
    echo "Backend: stopped"
  fi
  if is_running "$FRONTEND_PID_FILE"; then
    echo "Frontend: running (PID $(cat "$FRONTEND_PID_FILE"))"
  else
    echo "Frontend: stopped"
  fi
}

case "${1:-}" in
  init) initialize ;;
  start) start_all ;;
  stop)
    stop_process "Frontend" "$FRONTEND_PID_FILE"
    stop_process "Backend" "$BACKEND_PID_FILE"
    ;;
  restart)
    "$0" stop
    "$0" start
    ;;
  status) status_all ;;
  logs) tail -n "${2:-100}" "$LOG_DIR/backend.log" "$LOG_DIR/frontend.log" ;;
  *)
    echo "Usage: $0 {init|start|stop|restart|status|logs [lines]}" >&2
    exit 2
    ;;
esac
