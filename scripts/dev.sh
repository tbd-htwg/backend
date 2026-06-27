#!/usr/bin/env bash
# JVM local development — all backend microservices without Kubernetes.
# Canonical reference: README-GKE.md Option B.
#
# Usage:
#   ./scripts/dev.sh [start|stop|restart|status|logs [service]]
#
# Services: valkey, firestore, external-info, social, customfield, trip, platform
# API: trip :8080, social :8081, external-info :8082, platform :8083, customfield :8084
# Frontend: cd ../frontend && npm run dev
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BACKEND_DIR}/.." && pwd)"
LOCAL_GETTINGSTARTED="${BACKEND_DIR}/docs/gettingstarted"
MS2_GETTINGSTARTED="${REPO_ROOT}/infrastructure/ms2/docs/gettingstarted"
JVM_DEV_DIR="${BACKEND_DIR}/.jvm-dev"
PID_DIR="${JVM_DEV_DIR}/pids"
LOG_DIR="${JVM_DEV_DIR}/logs"

FIRESTORE_EMULATOR_HOST_PORT="${FIRESTORE_EMULATOR_HOST_PORT:-0.0.0.0:9090}"
VALKEY_CONTAINER="${VALKEY_CONTAINER:-tripplanning-valkey-dev}"
VALKEY_PORT="${VALKEY_PORT:-6379}"
REDIS_ENV=(
  "SPRING_DATA_REDIS_HOST=localhost"
  "SPRING_DATA_REDIS_PORT=${VALKEY_PORT}"
)

if [[ -f "${LOCAL_GETTINGSTARTED}/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${LOCAL_GETTINGSTARTED}/.env"
  set +a
fi
if [[ -f "${BACKEND_DIR}/.env.local" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${BACKEND_DIR}/.env.local"
  set +a
fi
if [[ -f "${MS2_GETTINGSTARTED}/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${MS2_GETTINGSTARTED}/.env"
  set +a
fi

JWT_SECRET="${JWT_SECRET:-local-dev-only-change-me-32bytes-min!!}"
INTERNAL_SECRET="${INTERNAL_SECRET:-dev-internal-service-secret}"
TRIPPLANNING_AUTH_JWT_SECRET="${TRIPPLANNING_AUTH_JWT_SECRET:-${JWT_SECRET}}"
TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID:-tbd-cloudappdev}"
CUSTOMFIELD_PORT="${CUSTOMFIELD_PORT:-8084}"
CUSTOMFIELD_URL="${TRIPPLANNING_CUSTOMFIELD_SERVICE_URL:-http://localhost:${CUSTOMFIELD_PORT}}"

ALL_SERVICES=(valkey firestore external-info social customfield trip platform)

# name:port:maven-module (spring-boot:run forks a child JVM — stop must free the port too)
JAVA_SERVICES=(
  "external-info:8082:tripplanning-external-info-service"
  "social:8081:tripplanning-social-service"
  "customfield:${CUSTOMFIELD_PORT}:tripplanning-customfield-service"
  "trip:8080:tripplanning-trip-service"
  "platform:8083:tripplanning-platform-service"
)

require_cmd() {
  for c in "$@"; do
    command -v "$c" >/dev/null 2>&1 || {
      echo "ERROR: required command not found: $c"
      exit 1
    }
  done
}

jdk_home_valid() {
  [[ -n "${1:-}" && -x "${1}/bin/java" && -x "${1}/bin/javac" ]]
}

ensure_java_home() {
  if jdk_home_valid "${JAVA_HOME:-}"; then
    return 0
  fi
  local candidate
  for candidate in \
    /usr/lib/jvm/java-21-openjdk \
    /usr/lib/jvm/java-21-temurin-jdk \
    /usr/lib/jvm/temurin-21-jdk \
    /usr/lib/jvm/java-17-openjdk \
    /usr/lib/jvm/java-17-temurin-jdk; do
    if jdk_home_valid "${candidate}"; then
      export JAVA_HOME="${candidate}"
      return 0
    fi
  done
  local javac_bin
  javac_bin="$(command -v javac 2>/dev/null || true)"
  if [[ -n "${javac_bin}" ]]; then
    javac_bin="$(readlink -f "${javac_bin}")"
    export JAVA_HOME="${javac_bin%/bin/javac}"
    if jdk_home_valid "${JAVA_HOME}"; then
      return 0
    fi
  fi
  echo "ERROR: no full JDK found (need java + javac). Install Java 21."
  exit 1
}

service_running() {
  local name="$1"
  local pid_file="${PID_DIR}/${name}.pid"
  [[ -f "${pid_file}" ]] && kill -0 "$(cat "${pid_file}")" 2>/dev/null
}

pids_on_port() {
  local port="$1"
  local pids=""
  if command -v fuser >/dev/null 2>&1; then
    # fuser prints "8082/tcp: 1234 5678" — extract numeric PIDs only
    pids="$(fuser "${port}/tcp" 2>/dev/null | grep -oE '[0-9]+' | sort -u | tr '\n' ' ' || true)"
  fi
  if [[ -z "${pids// }" ]] && command -v lsof >/dev/null 2>&1; then
    pids="$(lsof -ti ":${port}" 2>/dev/null | tr '\n' ' ' || true)"
  fi
  echo "${pids}" | xargs -r echo
}

kill_port() {
  local port="$1"
  local pids
  pids="$(pids_on_port "${port}")"
  if [[ -n "${pids// }" ]]; then
    echo "Freeing port ${port} (pid(s): ${pids})"
    # shellcheck disable=SC2086
    kill ${pids} 2>/dev/null || true
    sleep 1
    # shellcheck disable=SC2086
    kill -9 ${pids} 2>/dev/null || true
  fi
  # Minikube/kubectl port-forward often leaves docker-proxy bound without fuser/lsof visibility.
  pids="$(pgrep -f "docker-proxy.*-host-port ${port} " 2>/dev/null | tr '\n' ' ' || true)"
  if [[ -n "${pids// }" ]]; then
    echo "Freeing docker-proxy on port ${port} (pid(s): ${pids})"
    # shellcheck disable=SC2086
    kill ${pids} 2>/dev/null || true
    sleep 1
  fi
}

port_is_listening() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -H -ltn "sport = :${port}" 2>/dev/null | grep -q .
    return $?
  fi
  port_in_use_by_pid "${port}"
}

port_in_use_by_pid() {
  local port="$1"
  [[ -n "$(pids_on_port "${port}")" ]]
}

port_in_use() {
  local port="$1"
  port_is_listening "${port}" || port_in_use_by_pid "${port}"
}

port_blocker_hint() {
  local port="$1"
  echo "Port ${port} is already in use and could not be freed."
  echo "  Common cause: a leftover Minikube/Docker port-forward (docker-proxy)."
  echo "  Try: pgrep -af 'docker-proxy.*-host-port ${port} '"
  echo "  Or use another port: CUSTOMFIELD_PORT=8085 TRIPPLANNING_CUSTOMFIELD_SERVICE_URL=http://localhost:8085 ./scripts/dev.sh start"
}

ensure_port_available() {
  local name="$1"
  local port="$2"
  local module="$3"
  if service_running "${name}" && health_ok "${port}"; then
    return 0
  fi
  if port_is_listening "${port}" || port_in_use_by_pid "${port}"; then
    echo "WARN: port ${port} in use (${name} orphan or foreign listener?) — freeing"
    stop_java_service "${name}" "${port}" "${module}"
    sleep 1
  fi
  if port_is_listening "${port}"; then
    echo "ERROR: ${name} cannot bind to port ${port}."
    port_blocker_hint "${port}"
    ss -tln 2>/dev/null | grep ":${port} " || true
    exit 1
  fi
}

stop_service() {
  local name="$1"
  local pid_file="${PID_DIR}/${name}.pid"
  if [[ -f "${pid_file}" ]]; then
    local pid
    pid="$(cat "${pid_file}")"
    if kill -0 "${pid}" 2>/dev/null; then
      pkill -TERM -P "${pid}" 2>/dev/null || true
      kill -- "-${pid}" 2>/dev/null || kill "${pid}" 2>/dev/null || true
      local n=0
      while kill -0 "${pid}" 2>/dev/null && (( n < 15 )); do
        sleep 1
        n=$((n + 1))
      done
      kill -9 -- "-${pid}" 2>/dev/null || kill -9 "${pid}" 2>/dev/null || true
    fi
    rm -f "${pid_file}"
  fi
}

stop_java_service() {
  local name="$1"
  local port="$2"
  local module="$3"
  stop_service "${name}"
  # Orphan JVM left behind by spring-boot:run fork
  pkill -f "${BACKEND_DIR}/${module}/target/classes" 2>/dev/null || true
  pkill -f "spring-boot:run.*${module}" 2>/dev/null || true
  kill_port "${port}"
}

firestore_emulator_port() {
  local host_port="${FIRESTORE_EMULATOR_HOST_PORT}"
  echo "${host_port##*:}"
}

firestore_port_ready() {
  local port
  port="$(firestore_emulator_port)"
  curl -sf "http://127.0.0.1:${port}" >/dev/null 2>&1 \
    || curl -sf "http://127.0.0.1:${port}/" >/dev/null 2>&1
}

# Avoid `gcloud components list` — it can hang for minutes updating the component manager.
firestore_emulator_installed() {
  local sdk_root
  sdk_root="$(gcloud info --format='value(installation.sdk_root)' 2>/dev/null)" || return 1
  [[ -n "${sdk_root}" ]] \
    && [[ -f "${sdk_root}/platform/cloud-firestore-emulator/cloud-firestore-emulator.jar" ]]
}

start_firestore() {
  require_cmd gcloud curl
  if service_running firestore; then
    echo "Firestore emulator already running (pid $(cat "${PID_DIR}/firestore.pid"))."
    return 0
  fi
  if firestore_port_ready; then
    echo "Firestore emulator already listening on :$(firestore_emulator_port) (reusing)."
    return 0
  fi
  echo "== Checking Firestore emulator =="
  if ! firestore_emulator_installed; then
    echo "ERROR: Firestore emulator not installed. Run:"
    echo "  gcloud components install cloud-firestore-emulator"
    exit 1
  fi
  mkdir -p "${PID_DIR}" "${LOG_DIR}"
  echo "== Starting Firestore emulator on ${FIRESTORE_EMULATOR_HOST_PORT} =="
  setsid gcloud emulators firestore start --host-port="${FIRESTORE_EMULATOR_HOST_PORT}" \
    >>"${LOG_DIR}/firestore.log" 2>&1 &
  echo $! >"${PID_DIR}/firestore.pid"
  local n=0
  while (( n < 45 )); do
    if firestore_port_ready; then
      echo "Firestore emulator ready (log: ${LOG_DIR}/firestore.log)"
      return 0
    fi
    sleep 1
    n=$((n + 1))
  done
  echo "ERROR: Firestore emulator did not become ready — see ${LOG_DIR}/firestore.log"
  exit 1
}

stop_firestore() {
  if service_running firestore || firestore_port_ready; then
    echo "Stopping Firestore emulator..."
  fi
  stop_service firestore
  pkill -f "gcloud emulators firestore start" 2>/dev/null || true
  pkill -f "cloud-firestore-emulator" 2>/dev/null || true
  kill_port "$(firestore_emulator_port)"
}

valkey_running() {
  command -v docker >/dev/null 2>&1 \
    && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${VALKEY_CONTAINER}"
}

start_valkey() {
  require_cmd docker
  if valkey_running; then
    echo "Valkey already running (container ${VALKEY_CONTAINER})."
    return 0
  fi
  if docker ps -a --format '{{.Names}}' 2>/dev/null | grep -qx "${VALKEY_CONTAINER}"; then
    echo "== Starting Valkey container ${VALKEY_CONTAINER} =="
    docker start "${VALKEY_CONTAINER}" >/dev/null
  else
    echo "== Starting Valkey on :${VALKEY_PORT} (docker) =="
    docker run -d --name "${VALKEY_CONTAINER}" -p "${VALKEY_PORT}:6379" valkey/valkey:8-alpine >/dev/null
  fi
  local n=0
  while (( n < 30 )); do
    if docker exec "${VALKEY_CONTAINER}" valkey-cli ping 2>/dev/null | grep -q PONG; then
      echo "Valkey ready on localhost:${VALKEY_PORT}"
      return 0
    fi
    sleep 1
    n=$((n + 1))
  done
  echo "ERROR: Valkey did not become ready"
  exit 1
}

stop_valkey() {
  if command -v docker >/dev/null 2>&1 \
    && docker ps -a --format '{{.Names}}' 2>/dev/null | grep -qx "${VALKEY_CONTAINER}"; then
    echo "Stopping Valkey container ${VALKEY_CONTAINER}..."
    docker stop "${VALKEY_CONTAINER}" >/dev/null 2>&1 || true
  fi
}

health_ok() {
  local port="$1"
  # Aggregate /health can be 503 on JVM local (e.g. Elasticsearch indicator while Lucene is active).
  curl -sf "http://127.0.0.1:${port}/actuator/health/liveness" >/dev/null 2>&1 \
    || curl -sf "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1
}

start_maven_service() {
  local name="$1"
  local module="$2"
  local port="$3"
  shift 3
  if service_running "${name}"; then
    if health_ok "${port}"; then
      echo "${name} already running and healthy (pid $(cat "${PID_DIR}/${name}.pid"))."
      return 0
    fi
    echo "WARN: ${name} is running but unhealthy — restarting"
    stop_java_service "${name}" "${port}" "${module}"
    sleep 2
  elif port_in_use "${port}"; then
    echo "WARN: port ${port} in use (orphan ${name}?) — freeing"
    stop_java_service "${name}" "${port}" "${module}"
    sleep 1
  fi
  mkdir -p "${PID_DIR}" "${LOG_DIR}"
  echo "== Starting ${name} =="
  setsid bash -c '
    cd "$1"
    shift
    for arg in "$@"; do
      export "${arg?}"
    done
    exec mvn -pl "'"${module}"'" spring-boot:run
  ' bash "${BACKEND_DIR}" "$@" >>"${LOG_DIR}/${name}.log" 2>&1 &
  echo $! >"${PID_DIR}/${name}.pid"
}

wait_for_health() {
  local name="$1"
  local port="$2"
  local liveness_url="http://127.0.0.1:${port}/actuator/health/liveness"
  local health_url="http://127.0.0.1:${port}/actuator/health"
  local n=0
  local code="000"
  echo "Waiting for ${name} (:${port})..."
  while (( n < 180 )); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "${liveness_url}" 2>/dev/null || echo 000)"
    if [[ "${code}" != "200" ]]; then
      code="$(curl -s -o /dev/null -w '%{http_code}' "${health_url}" 2>/dev/null || echo 000)"
    fi
    if [[ "${code}" == "200" ]]; then
      echo "${name} ready on :${port}"
      return 0
    fi
    if ! service_running "${name}"; then
      echo "ERROR: ${name} exited before becoming healthy — see ${LOG_DIR}/${name}.log"
      tail -n 30 "${LOG_DIR}/${name}.log" 2>/dev/null || true
      exit 1
    fi
    if (( n > 0 && n % 10 == 0 )); then
      echo "  still waiting for ${name} (HTTP ${code})..."
      if [[ "${code}" == "503" ]]; then
        echo "  hint: check ${LOG_DIR}/${name}.log (Valkey :${VALKEY_PORT}, or Elasticsearch health on trip local profile)"
      fi
    fi
    sleep 2
    n=$((n + 2))
  done
  echo "ERROR: ${name} not healthy after 180s (last HTTP ${code}) — see ${LOG_DIR}/${name}.log"
  tail -n 20 "${LOG_DIR}/${name}.log" 2>/dev/null || true
  exit 1
}

start_and_wait() {
  local name="$1"
  local module="$2"
  local port="$3"
  shift 3
  start_maven_service "${name}" "${module}" "${port}" "$@"
  wait_for_health "${name}" "${port}"
}

cmd_start() {
  require_cmd mvn curl
  ensure_java_home

  if [[ ${#JWT_SECRET} -lt 32 ]]; then
    echo "ERROR: JWT_SECRET must be at least 32 characters (set in docs/gettingstarted/.env)"
    exit 1
  fi

  start_valkey
  start_firestore

  start_and_wait external-info tripplanning-external-info-service 8082 \
    "${REDIS_ENV[@]}" \
    TRIPPLANNING_AUTH_JWT_SECRET="${TRIPPLANNING_AUTH_JWT_SECRET}" \
    TRIPPLANNING_INTERNAL_SECRET="${INTERNAL_SECRET}" \
    GOOGLE_MAPS_API_KEY="${GOOGLE_MAPS_API_KEY:-}"

  start_and_wait social tripplanning-social-service 8081 \
    "${REDIS_ENV[@]}" \
    SPRING_PROFILES_ACTIVE=local \
    TRIPPLANNING_AUTH_JWT_SECRET="${TRIPPLANNING_AUTH_JWT_SECRET}" \
    TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    TRIPPLANNING_TRIP_SERVICE_URL=http://localhost:8080 \
    TRIPPLANNING_INTERNAL_SECRET="${INTERNAL_SECRET}" \
    SPRING_CLOUD_GCP_FIRESTORE_HOST_PORT=localhost:9090 \
    SPRING_CLOUD_GCP_FIRESTORE_EMULATOR_ENABLED=true \
    GOOGLE_CLOUD_PROJECT="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}"

  start_and_wait customfield tripplanning-customfield-service 8084 \
    SPRING_PROFILES_ACTIVE=local \
    TRIPPLANNING_AUTH_JWT_SECRET="${TRIPPLANNING_AUTH_JWT_SECRET}" \
    TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    TRIPPLANNING_TRIP_SERVICE_URL=http://localhost:8080 \
    TRIPPLANNING_INTERNAL_SECRET="${INTERNAL_SECRET}" \
    SPRING_CLOUD_GCP_FIRESTORE_HOST_PORT=localhost:9090 \
    SPRING_CLOUD_GCP_FIRESTORE_EMULATOR_ENABLED=true \
    GOOGLE_CLOUD_PROJECT="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}"

  start_and_wait platform tripplanning-platform-service 8083 \
    SPRING_PROFILES_ACTIVE=local \
    TRIPPLANNING_AUTH_JWT_SECRET="${TRIPPLANNING_AUTH_JWT_SECRET}" \
    TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    TRIPPLANNING_TRIP_SERVICE_URL=http://localhost:8080 \
    TRIPPLANNING_CUSTOMFIELD_SERVICE_URL=http://localhost:8084 \
    TRIPPLANNING_INTERNAL_SECRET="${INTERNAL_SECRET}" \
    TRIPPLANNING_PLATFORM_USE_STUBS="${TRIPPLANNING_PLATFORM_USE_STUBS:-true}"

  start_and_wait trip tripplanning-trip-service 8080 \
    "${REDIS_ENV[@]}" \
    SPRING_PROFILES_ACTIVE=local \
    TRIPPLANNING_AUTH_JWT_SECRET="${TRIPPLANNING_AUTH_JWT_SECRET}" \
    TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID="${TRIPPLANNING_AUTH_FIREBASE_PROJECT_ID}" \
    TRIPPLANNING_SOCIAL_SERVICE_URL=http://localhost:8081 \
    TRIPPLANNING_EXTERNAL_INFO_SERVICE_URL=http://localhost:8082 \
    TRIPPLANNING_PLATFORM_BASE_URL=http://localhost:8083 \
    TRIPPLANNING_INTERNAL_SECRET="${INTERNAL_SECRET}"

  if [[ "${TRIPPLANNING_PLATFORM_USE_STUBS:-true}" == "true" ]]; then
    echo ""
    echo "WARN: Provisioning STUB mode (TRIPPLANNING_PLATFORM_USE_STUBS=true) — no real GCP/Terraform resources"
  fi
  echo ""
  echo "== JVM backend ready =="
  echo "  trip-service          http://localhost:8080"
  echo "  social-service        http://localhost:8081"
  echo "  external-info-service http://localhost:8082"
  echo "  platform-service      http://localhost:8083"
  echo "  customfield-service   http://localhost:8084"
  echo "  Valkey (cache)        localhost:${VALKEY_PORT}"
  echo "  Local tenant slug     develop (http://localhost:5173 → tenant-develop)"
  echo "  Logs: ${LOG_DIR}/"
  echo "  Frontend: cd ${REPO_ROOT}/frontend && npm run dev"
}

cmd_stop() {
  local entry name port module
  for entry in "${JAVA_SERVICES[@]}"; do
    IFS=: read -r name port module <<<"${entry}"
    if service_running "${name}" || port_in_use "${port}"; then
      echo "Stopping ${name}..."
      stop_java_service "${name}" "${port}" "${module}"
    fi
  done
  stop_firestore
  stop_valkey
  echo "Stopped JVM backend services."
}

cmd_status() {
  if valkey_running; then
    echo "valkey: running (container ${VALKEY_CONTAINER})"
  else
    echo "valkey: stopped"
  fi
  for name in firestore external-info social customfield trip platform; do
    if service_running "${name}"; then
      echo "${name}: running (pid $(cat "${PID_DIR}/${name}.pid"))"
    else
      echo "${name}: stopped"
    fi
  done
}

cmd_logs() {
  local name="${1:-}"
  if [[ -z "${name}" ]]; then
    echo "Usage: $0 logs <service>"
    echo "Services: ${ALL_SERVICES[*]}"
    exit 1
  fi
  if [[ "${name}" == "valkey" ]]; then
    docker logs -f "${VALKEY_CONTAINER}"
    return
  fi
  local log_file="${LOG_DIR}/${name}.log"
  if [[ ! -f "${log_file}" ]]; then
    echo "No log file: ${log_file}"
    exit 1
  fi
  tail -f "${log_file}"
}

usage() {
  cat <<EOF
JVM local backend (no minikube).

Usage:
  $0 start              Start Valkey + Firestore emulator + all microservices
  $0 stop               Stop all services
  $0 restart            stop then start
  $0 status             Show running processes
  $0 logs <service>     Tail logs (valkey|firestore|external-info|social|trip|platform)

Env: docs/gettingstarted/.env, backend/.env.local (optional)
EOF
}

main() {
  local cmd="${1:-start}"
  shift || true
  case "${cmd}" in
    start) cmd_start ;;
    stop) cmd_stop ;;
    restart) cmd_stop; cmd_start ;;
    status) cmd_status ;;
    logs) cmd_logs "$@" ;;
    help|-h|--help) usage ;;
    *)
      echo "Unknown command: ${cmd}"
      usage
      exit 1
      ;;
  esac
}

main "$@"
