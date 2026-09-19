#!/usr/bin/env bash
set -euo pipefail

NAMESPACE=${NAMESPACE:-okapi}
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install ops helm/ops \
  --namespace "$NAMESPACE" \
  --wait \
  --timeout 15m \
  -f deployment-artifacts/values-yaml/test/ops-values.yaml

helm upgrade --install ingester helm/ingester \
  --namespace "$NAMESPACE" \
  -f deployment-artifacts/values-yaml/test/okapi-ingester-values.yaml

helm upgrade --install web helm/web \
  --namespace "$NAMESPACE" \
  -f deployment-artifacts/values-yaml/test/okapi-web-values.yaml
