#!/usr/bin/env bash
# Smoke-test checklist for tripplanning services on local minikube.
# Keep in sync with backend/docs/gettingstarted/README.md §6.
set -euo pipefail

NS="${NS:-tripplanning}"
VERIFY_STRICT="${VERIFY_STRICT:-false}"
SMOKE_DEV_LOGIN="${SMOKE_DEV_LOGIN:-true}"
API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8080}"
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

echo "== Ingress =="
kubectl get ingress -n "$NS" 2>/dev/null || note_fail "no Ingress in ${NS}"

start_api_port_forward() {
  local ingress_ns="ingress-nginx"
  if kubectl get svc -n "${ingress_ns}" ingress-nginx-controller >/dev/null 2>&1; then
    kubectl port-forward -n "${ingress_ns}" svc/ingress-nginx-controller 18080:80 &
    echo $!
    return 0
  fi
  if [[ -n "${TRIP_READY}" ]]; then
    kubectl port-forward -n "$NS" "pod/${TRIP_READY}" 18080:8080 &
    echo $!
    return 0
  fi
  return 1
}

API_PF=""
if PF_PID="$(start_api_port_forward)"; then
  API_PF="${PF_PID}"
  sleep 2
  API_BASE_URL="http://127.0.0.1:18080"
  echo "== API smoke via ${API_BASE_URL} =="
  curl -sf "${API_BASE_URL}/actuator/health" | head -c 200 || note_fail "trip health via API entry failed"
else
  echo "Skipping API entry smoke (no ingress or trip pod)"
fi

if [[ "${SMOKE_DEV_LOGIN}" == "true" && -n "${API_PF}" ]]; then
  echo "== Dev login (via API gateway) =="
  TOKEN_JSON="$(curl -sf -X POST "${API_BASE_URL}/api/v2/auth/dev-login" \
    -H "Content-Type: application/json" \
    -d '{"email":"verify@local.dev","name":"Verify"}' || true)"
  if echo "${TOKEN_JSON}" | grep -q accessToken; then
    echo "dev-login OK"
    ACCESS_TOKEN="$(echo "${TOKEN_JSON}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
    if [[ -n "${ACCESS_TOKEN}" ]]; then
      echo "== External details via gateway (JWT) =="
      code="$(curl -s -o /dev/null -w '%{http_code}' \
        "${API_BASE_URL}/api/v2/external/details?location=Paris&countryCode=FR&lat=48.85&lon=2.35" \
        -H "Authorization: Bearer ${ACCESS_TOKEN}")"
      if [[ "${code}" == "200" ]]; then
        echo "external-info via gateway OK"
      else
        note_fail "external-info via gateway returned HTTP ${code} (expected 200)"
      fi
      echo "== Social countLikes via gateway =="
      code="$(curl -s -o /dev/null -w '%{http_code}' \
        "${API_BASE_URL}/api/v2/trips/search/countLikes?tripId=1")"
      if [[ "${code}" == "200" || "${code}" == "404" ]]; then
        echo "social countLikes via gateway OK (HTTP ${code})"
      else
        note_fail "social countLikes via gateway returned HTTP ${code}"
      fi
    fi
  else
    note_fail "dev-login smoke failed"
  fi
fi

if [[ -n "${API_PF}" ]]; then
  kill "${API_PF}" 2>/dev/null || true
  wait "${API_PF}" 2>/dev/null || true
fi

if [[ "${FAILED}" -ne 0 ]]; then
  echo ""
  echo "Verify reported failures. Logs:"
  echo "  kubectl logs -n ${NS} deployment/trip-service --tail=40"
  echo "  kubectl logs -n ${NS} deployment/social-service --tail=40"
  echo "  kubectl logs -n ${NS} deployment/external-info-service --tail=40"
  echo "  kubectl logs -n ${NS} deployment/firestore-emulator --tail=40"
  if [[ "${VERIFY_STRICT}" == "true" ]]; then
    exit 1
  fi
  echo "VERIFY_STRICT=false — continuing (warnings only)."
fi

echo "Done. API entry: ./scripts/local-dev.sh port-forward  # ingress → localhost:8080"
