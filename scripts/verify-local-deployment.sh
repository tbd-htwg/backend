#!/usr/bin/env bash
# Smoke-test checklist for tripplanning services on local minikube.
# Keep in sync with backend/docs/gettingstarted/README.md §6.
set -euo pipefail

NS="${NS:-tripplanning}"
VERIFY_STRICT="${VERIFY_STRICT:-false}"
SMOKE_DEV_LOGIN="${SMOKE_DEV_LOGIN:-true}"
FAILED=0

note_fail() {
  echo "FAIL: $*"
  FAILED=1
}

echo "== Pods =="
kubectl get pods -n "$NS" -o wide

TRIP_READY="$(kubectl get pods -n "$NS" -l app=trip-service \
  -o jsonpath='{.items[?(@.status.containerStatuses[?(@.name=="trip-service")].ready==true)].metadata.name}' 2>/dev/null || true)"
SOCIAL_READY="$(kubectl get pods -n "$NS" -l app=social-service \
  -o jsonpath='{.items[?(@.status.containerStatuses[?(@.name=="social-service")].ready==true)].metadata.name}' 2>/dev/null || true)"
EXT_READY="$(kubectl get pods -n "$NS" -l app=external-info-service \
  -o jsonpath='{.items[?(@.status.containerStatuses[?(@.name=="external-info-service")].ready==true)].metadata.name}' 2>/dev/null || true)"
FSE_READY="$(kubectl get pods -n "$NS" -l app.kubernetes.io/name=firestore-emulator \
  -o jsonpath='{.items[?(@.status.phase=="Running")].metadata.name}' 2>/dev/null | head -1 || true)"

if [[ -z "${TRIP_READY}" ]]; then
  note_fail "no Ready trip-service pod (often Elasticsearch warmup — check logs)"
else
  echo "trip-service ready pod: ${TRIP_READY}"
fi
if [[ -z "${SOCIAL_READY}" ]]; then
  note_fail "no Ready social-service pod"
else
  echo "social-service ready pod: ${SOCIAL_READY}"
fi
if [[ -z "${EXT_READY}" ]]; then
  note_fail "no Ready external-info-service pod"
else
  echo "external-info-service ready pod: ${EXT_READY}"
fi
if [[ -z "${FSE_READY}" ]]; then
  note_fail "no Running firestore-emulator pod"
else
  echo "firestore-emulator pod: ${FSE_READY}"
fi

echo "== Services =="
kubectl get svc -n "$NS"

if [[ -n "${TRIP_READY}" ]]; then
  echo "== Trip health (port-forward) =="
  kubectl port-forward -n "$NS" "pod/${TRIP_READY}" 18080:8080 &
  PF=$!
  sleep 2
  curl -sf "http://127.0.0.1:18080/actuator/health" | head -c 200 || note_fail "trip health curl failed"
  kill "$PF" 2>/dev/null || true
  wait "$PF" 2>/dev/null || true
else
  echo "Skipping trip port-forward (no ready pod)"
fi

if [[ -n "${SOCIAL_READY}" ]]; then
  echo "== Social health (port-forward) =="
  kubectl port-forward -n "$NS" "svc/social-service" 18081:8081 &
  PF=$!
  sleep 2
  curl -sf "http://127.0.0.1:18081/actuator/health" | head -c 200 || note_fail "social health curl failed"
  kill "$PF" 2>/dev/null || true
  wait "$PF" 2>/dev/null || true
else
  echo "Skipping social port-forward (no ready pod)"
fi

if [[ -n "${EXT_READY}" ]]; then
  echo "== External-info health (port-forward) =="
  kubectl port-forward -n "$NS" "svc/external-info-service" 18082:8082 &
  PF=$!
  sleep 2
  curl -sf "http://127.0.0.1:18082/actuator/health" | head -c 200 || note_fail "external-info health curl failed"
  kill "$PF" 2>/dev/null || true
  wait "$PF" 2>/dev/null || true
else
  echo "Skipping external-info port-forward (no ready pod)"
fi

if [[ "${SMOKE_DEV_LOGIN}" == "true" && -n "${TRIP_READY}" ]]; then
  echo "== Dev login smoke (port-forward) =="
  kubectl port-forward -n "$NS" "pod/${TRIP_READY}" 18080:8080 &
  PF=$!
  sleep 2
  if curl -sf -X POST "http://127.0.0.1:18080/api/v2/auth/dev-login" \
    -H "Content-Type: application/json" \
    -d '{"email":"verify@local.dev","name":"Verify"}' | grep -q accessToken; then
    echo "dev-login OK"
  else
    note_fail "dev-login smoke failed"
  fi
  kill "$PF" 2>/dev/null || true
  wait "$PF" 2>/dev/null || true
fi

if [[ "${FAILED}" -ne 0 ]]; then
  echo ""
  echo "Verify reported failures. Logs:"
  echo "  kubectl logs -n ${NS} deployment/trip-service --tail=40"
  echo "  kubectl logs -n ${NS} deployment/social-service --tail=40"
  echo "  kubectl logs -n ${NS} deployment/firestore-emulator --tail=40"
  if [[ "${VERIFY_STRICT}" == "true" ]]; then
    exit 1
  fi
  echo "VERIFY_STRICT=false — continuing (warnings only)."
fi

echo "Done. Use port-forward: ./scripts/local-dev.sh port-forward"
