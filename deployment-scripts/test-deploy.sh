#!/usr/bin/env bash
set -euo pipefail

NAMESPACE=${NAMESPACE:-okapi}
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f deployment-scripts/values-yaml/test/ch-migrate-job.yaml
kubectl wait --for=condition=complete job/okapi-ch-migrate -n "$NAMESPACE" --timeout=300s

helm upgrade --install okapi-ingester helm/okapi-ingester \
  --namespace "$NAMESPACE" \
  -f deployment-scripts/values-yaml/test/okapi-ingester-values.yaml

helm upgrade --install okapi-web helm/okapi-web \
  --namespace "$NAMESPACE" \
  -f deployment-scripts/values-yaml/test/okapi-web-values.yaml
