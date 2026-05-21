# Local minikube manifests (Helm templates)

Manifests are rendered from `chart/` — not applied via raw kustomize overlays for apps.

```bash
# From backend/
./scripts/local-dev.sh deploy
```

Manual render:

```bash
helm template tripplanning k8s/local/chart \
  -f k8s/local/chart/values.yaml \
  -f k8s/local/chart/values-local.yaml \
  --namespace tripplanning \
  > k8s/local/rendered/manifests.yaml
```

The infrastructure repo chart (`infrastructure/ms2/charts/tripplanning`) is unchanged by local dev; this chart is the minikube fork with H2, nginx ingress, debug UIs, and search-index bootstrap settings.
