#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
INSTALL_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
CONF_DIR="$INSTALL_DIR/conf"
RUN_DIR="$INSTALL_DIR/run"
LOG_DIR="$INSTALL_DIR/logs"
DATA_DIR="$INSTALL_DIR/data"
FRONTEND_DIR="$INSTALL_DIR/app/frontend"
ENV_FILE="${DATASTORIA_ENV_FILE:-$CONF_DIR/datastoria.env}"

mkdir -p "$RUN_DIR" "$LOG_DIR" "$DATA_DIR"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

DATASTORIA_PROFILE="${DATASTORIA_PROFILE:-dev}"
SERVER_HOST="${SERVER_HOST:-0.0.0.0}"
SERVER_PORT="${SERVER_PORT:-8080}"
if [[ -z "${JAVA_BIN:-}" && -n "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="${JAVA_BIN:-java}"
fi
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx1024m}"
SERVER_PID_FILE="$RUN_DIR/server.pid"

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

initialize() {
  if [[ ! -f "$ENV_FILE" ]]; then
    cp "$CONF_DIR/datastoria.env.example" "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    echo "Created runtime configuration: $ENV_FILE"
  fi
  echo "DataStoria initialization complete (profile $DATASTORIA_PROFILE)."
}

start_server() {
  if is_running "$SERVER_PID_FILE"; then
    echo "DataStoria already running (PID $(cat "$SERVER_PID_FILE"))."
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
  if [[ ! -f "$FRONTEND_DIR/index.html" ]]; then
    echo "Exported frontend is missing: $FRONTEND_DIR/index.html" >&2
    return 1
  fi
  local -a java_opts
  read -r -a java_opts <<<"$JAVA_OPTS"
  (
    cd "$INSTALL_DIR"
    exec nohup "$JAVA_BIN" "${java_opts[@]}" -jar app/backend/datastoria-server.jar \
      "--spring.profiles.active=$DATASTORIA_PROFILE" \
      "--spring.config.additional-location=optional:file:$CONF_DIR/" \
      "--server.address=$SERVER_HOST" \
      "--server.port=$SERVER_PORT" \
      "--spring.web.resources.static-locations=file:$FRONTEND_DIR/"
  ) >>"$LOG_DIR/server.log" 2>&1 &
  echo $! >"$SERVER_PID_FILE"
  if ! wait_for_url "Backend" "http://127.0.0.1:$SERVER_PORT/actuator/health" "$SERVER_PID_FILE"; then
    stop_server
    return 1
  fi
  if ! wait_for_url "Web app" "http://127.0.0.1:$SERVER_PORT/" "$SERVER_PID_FILE"; then
    echo "Backend is up but the web app did not serve the landing page." >&2
    stop_server
    return 1
  fi
  echo "DataStoria started: http://$SERVER_HOST:$SERVER_PORT (single process, no Node.js required)"
}

stop_server() {
  if ! is_running "$SERVER_PID_FILE"; then
    rm -f "$SERVER_PID_FILE"
    echo "DataStoria is not running."
    return
  fi
  local pid
  pid="$(cat "$SERVER_PID_FILE")"
  kill "$pid"
  # SIGTERM first: lets the JVM shutdown hook settle in-flight agent runs (the graceful-shutdown
  # timeout defaults to 20s) before Spring closes. Wait up to 30s, then force-kill.
  for _ in {1..60}; do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.5
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid"
  fi
  rm -f "$SERVER_PID_FILE"
  echo "DataStoria stopped."
}

case "${1:-}" in
  init) initialize ;;
  start) start_server ;;
  stop) stop_server ;;
  restart)
    "$0" stop
    "$0" start
    ;;
  status)
    if is_running "$SERVER_PID_FILE"; then
      echo "DataStoria: running (PID $(cat "$SERVER_PID_FILE"), profile $DATASTORIA_PROFILE)"
    else
      echo "DataStoria: stopped"
    fi
    ;;
  logs) tail -n "${2:-100}" "$LOG_DIR/server.log" ;;
  *)
    echo "Usage: $0 {init|start|stop|restart|status|logs [lines]}" >&2
    exit 2
    ;;
esac
