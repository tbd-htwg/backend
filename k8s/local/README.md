# Local minikube manifests (Helm chart)

Deploy with the local dev script (registers a Helm release named `tripplanning`):

```bash
# From backend/
./scripts/local-dev.sh deploy
```

Backing services **Valkey** and **OpenSearch** are installed via official Helm subcharts (`valkey-io/valkey-helm`, `opensearch-project/helm-charts`). Vendored charts live under `chart/charts/`; `local-dev.sh deploy` runs `helm dependency build` automatically.

Manual upgrade (same as deploy, without rebuild):

```bash
helm dependency build k8s/local/chart
helm upgrade --install tripplanning k8s/local/chart \
  -n tripplanning \
  --create-namespace \
  --take-ownership \
  -f k8s/local/chart/values.yaml \
  -f k8s/local/chart/values-local.yaml
```

Optional render-only export (debugging / diff):

```bash
./scripts/local-dev.sh deploy   # also writes k8s/local/rendered/manifests.yaml
# or:
helm template tripplanning k8s/local/chart \
  -f k8s/local/chart/values.yaml \
  -f k8s/local/chart/values-local.yaml \
  --namespace tripplanning \
  > k8s/local/rendered/manifests.yaml
```

The GKE chart at [`infrastructure/ms2/charts/tripplanning`](../../../infrastructure/ms2/charts/tripplanning) uses the same Valkey/OpenSearch subcharts. This local chart is the minikube fork with PostgreSQL (first-party templates), nginx ingress, debug UIs, and search-index bootstrap settings.

## Debug UIs (Valkey Admin, OpenSearch Dashboards)

Optional debug UIs are exposed via ingress when `./scripts/local-dev.sh port-forward` is running (`localhost:8080`): **Valkey Admin** (`/debug/valkey/`) and **OpenSearch Dashboards** (`/debug/opensearch`, chart `opensearch-dashboards` 2.33.0).

### Valkey Admin

The UI at `/debug/valkey/` loads over ingress, but live features need a WebSocket to the admin server at the site root (`ws://host:port`), not under `/debug/valkey`. Through ingress that connection fails; you may also see “add connection” because env preconfig only targets **clusters**, not our standalone Valkey.

Use a direct port-forward instead (from `backend/`):

```bash
kubectl port-forward -n tripplanning svc/valkey-admin 8090:8080
```

Open `http://localhost:8090/` (trailing slash optional). Do **not** enter `localhost:8080` as a Valkey host — that is the web UI port. If you add a connection manually, use host `valkey`, port `6379`, TLS off (in-cluster DNS; the admin pod connects server-side).

For a quick CLI check: `kubectl exec -n tripplanning deploy/valkey -- valkey-cli KEYS '*'`

### OpenSearch Dashboards

Open **OpenSearch Dashboards** at `http://localhost:8080/debug/opensearch` → **Dev Tools** for ad-hoc queries, or **Discover** after creating an index pattern.

**Quick search (Dev Tools)** — local trip index alias `tripentity-local-read`:

```http
GET tripentity-local-read/_search
{
  "size": 10,
  "query": { "match_all": {} }
}
```

Other entity types use the same `*-read` aliases (`accomentity-read`, `transportentity-read`, `triplocationentity-read`). Ignore `top_queries-*` indices.

**Browse in the UI (Discover)** — **Stack Management → Index Patterns → Create**: pattern name `tripentity-local*` (matches read/write aliases). Skip the time filter if no `@timestamp` field. Then open **Discover** and select that pattern. Repeat with `accomentity*`, `transportentity*`, etc. if needed.
