#!/usr/bin/env bash
# Drop Hibernate Search OpenSearch indices and restart trip-service so mass indexing rebuilds
# from PostgreSQL. Required after perf seed jobs: seed-job inserts via JDBC and does not update
# OpenSearch; trip-service skips reindex when document counts already match (stale content).
#
# Usage:
#   ./scripts/reset-search-index.sh [options]
#
# Options:
#   --namespace NS           Kubernetes namespace (default: tripplanning or SEARCH_RESET_NAMESPACE)
#   --trip-index-prefix P    Trip index prefix (default: from trip-service-config or tripentity-local)
#   --skip-restart           Delete indices + clear Valkey only; do not restart trip-service
#   --no-wait                Restart trip-service but do not wait for READY search-index status
#   -h, --help
#
# Environment overrides: SEARCH_RESET_NAMESPACE, SEARCH_RESET_TRIP_INDEX_PREFIX,
# SEARCH_RESET_SKIP_RESTART, SEARCH_RESET_WAIT_TIMEOUT (seconds, default 600).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

NS="${SEARCH_RESET_NAMESPACE:-tripplanning}"
TRIP_INDEX_PREFIX="${SEARCH_RESET_TRIP_INDEX_PREFIX:-}"
SKIP_RESTART="${SEARCH_RESET_SKIP_RESTART:-false}"
NO_WAIT="${SEARCH_RESET_NO_WAIT:-false}"
WAIT_TIMEOUT="${SEARCH_RESET_WAIT_TIMEOUT:-600}"
VALKEY_LOCK_KEY="${SEARCH_RESET_LOCK_KEY:-tripplanning:search:index:lock}"
VALKEY_STATUS_KEY="${SEARCH_RESET_STATUS_KEY:-tripplanning:search:index:status}"
EMBEDDED_INDEX_PREFIXES=(accomentity transportentity triplocationentity)

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
}

require_cmd() {
  for c in "$@"; do
    command -v "$c" >/dev/null 2>&1 || {
      echo "ERROR: required command not found: $c" >&2
      exit 1
    }
  done
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace) NS="${2:?--namespace requires a value}"; shift ;;
    --trip-index-prefix) TRIP_INDEX_PREFIX="${2:?--trip-index-prefix requires a value}"; shift ;;
    --skip-restart) SKIP_RESTART=true ;;
    --no-wait) NO_WAIT=true ;;
    -h | --help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

resolve_trip_index_prefix() {
  if [[ -n "${TRIP_INDEX_PREFIX}" ]]; then
    printf '%s' "${TRIP_INDEX_PREFIX}"
    return 0
  fi
  local from_cm
  from_cm="$(kubectl get configmap trip-service-config -n "${NS}" \
    -o jsonpath='{.data.TRIPPLANNING_SEARCH_ELASTICSEARCH_INDEX_NAME}' 2>/dev/null || true)"
  if [[ -n "${from_cm}" ]]; then
    printf '%s' "${from_cm}"
    return 0
  fi
  printf '%s' "tripentity-local"
}

resolve_opensearch_target() {
  local pod="" container=""
  pod="$(kubectl get pods -n "${NS}" -l 'app.kubernetes.io/name=opensearch' \
    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
  if [[ -n "${pod}" ]]; then
    container="opensearch"
  else
    pod="$(kubectl get pods -n "${NS}" -l 'app.kubernetes.io/component=elasticsearch' \
      -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
    container="elasticsearch"
  fi
  if [[ -z "${pod}" ]]; then
    pod="$(kubectl get pods -n "${NS}" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null \
      | grep -E '^(opensearch|elasticsearch)-0$' | head -1 || true)"
    if [[ "${pod}" == opensearch-* ]]; then
      container="opensearch"
    elif [[ -n "${pod}" ]]; then
      container="elasticsearch"
    fi
  fi
  if [[ -z "${pod}" ]]; then
    echo "ERROR: no OpenSearch/Elasticsearch pod found in namespace ${NS}" >&2
    exit 1
  fi
  printf '%s %s' "${pod}" "${container}"
}

delete_hibernate_search_indices() {
  local pod="$1"
  local container="$2"
  local trip_prefix="$3"
  local patterns=("${trip_prefix}" "${EMBEDDED_INDEX_PREFIXES[@]}")
  echo "== Dropping Hibernate Search indices in ${NS}/${pod} (prefix ${trip_prefix}) =="
  kubectl exec -n "${NS}" "${pod}" -c "${container}" -- bash -lc '
    set -euo pipefail
    for prefix in "$@"; do
      code="$(curl -sS -o /dev/null -w "%{http_code}" -X DELETE \
        "http://127.0.0.1:9200/${prefix}-*?expand_wildcards=all" || true)"
      echo "  DELETE ${prefix}-* → HTTP ${code}"
    done
  ' bash "${patterns[@]}"
}

clear_valkey_search_coordination() {
  echo "== Clearing search-index coordination keys in Valkey =="
  kubectl exec -n "${NS}" deploy/valkey -- valkey-cli DEL "${VALKEY_LOCK_KEY}" "${VALKEY_STATUS_KEY}" \
    >/dev/null
}

wait_for_search_index_ready() {
  local internal secret_key
  secret_key="$(kubectl get secret trip-service-secrets -n "${NS}" \
    -o jsonpath='{.data.TRIPPLANNING_INTERNAL_SECRET}' 2>/dev/null || true)"
  if [[ -z "${secret_key}" ]]; then
    echo "WARN: trip-service-secrets missing; skipping search-index readiness poll." >&2
    return 0
  fi
  internal="$(printf '%s' "${secret_key}" | base64 -d)"
  echo "== Waiting for trip-service search index (timeout ${WAIT_TIMEOUT}s) =="
  local attempt deadline=$((SECONDS + WAIT_TIMEOUT)) json ready state
  while [[ "${SECONDS}" -lt "${deadline}" ]]; do
    json="$(kubectl exec -n "${NS}" deployment/trip-service -- \
      curl -sS -H "X-Internal-Secret: ${internal}" http://127.0.0.1:8080/internal/debug/search-index \
      2>/dev/null || true)"
    ready="$(printf '%s' "${json}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('ready', False))" 2>/dev/null || true)"
    state="$(printf '%s' "${json}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('state', ''))" 2>/dev/null || true)"
    if [[ "${ready}" == "True" || "${ready}" == "true" ]]; then
      echo "   search index READY (${json})"
      return 0
    fi
    echo "   state=${state:-unknown}; retrying..."
    sleep 5
  done
  echo "WARN: timed out waiting for search index READY. Check:" >&2
  echo "  kubectl logs -n ${NS} deployment/trip-service --tail=80 | rg -i 'search|index'" >&2
  return 1
}

restart_trip_service() {
  echo "== Restarting trip-service (mass indexer) =="
  kubectl rollout restart deployment/trip-service -n "${NS}"
  kubectl rollout status deployment/trip-service -n "${NS}" --timeout="${WAIT_TIMEOUT}s"
  if [[ "${NO_WAIT}" != "true" ]]; then
    wait_for_search_index_ready || true
  fi
}

main() {
  require_cmd kubectl curl python3
  local trip_prefix pod container
  trip_prefix="$(resolve_trip_index_prefix)"
  read -r pod container <<<"$(resolve_opensearch_target)"
  delete_hibernate_search_indices "${pod}" "${container}" "${trip_prefix}"
  clear_valkey_search_coordination
  if [[ "${SKIP_RESTART}" == "true" ]]; then
    echo "Skip restart requested; run: kubectl rollout restart deployment/trip-service -n ${NS}"
    return 0
  fi
  restart_trip_service
  echo "Search index reset complete."
}

main "$@"
