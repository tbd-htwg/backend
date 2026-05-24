# Local minikube manifests (Helm chart)

Deploy with the local dev script (registers a Helm release named `tripplanning`):

```bash
# From backend/
./scripts/local-dev.sh deploy
```

Manual upgrade (same as deploy, without rebuild):

```bash
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

The infrastructure repo chart (`infrastructure/ms2/charts/tripplanning`) is unchanged by local dev; this chart is the minikube fork with PostgreSQL, nginx ingress, debug UIs, and search-index bootstrap settings.
