#!/usr/bin/env bash
set -euo pipefail

: "${CLUSTER_NAME:?CLUSTER_NAME is required}"
NAMESPACE="${NAMESPACE:-okapi}"
IMAGE_REPO="${IMAGE_REPO:-ghcr.io/okapi-core}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
CHART_VERSION="${CHART_VERSION:-0.0.0-ci.local}"
HELM_TIMEOUT="${HELM_TIMEOUT:-15m}"

for service in ops ingester oscar web; do
  chart="helm/dist/$service-$CHART_VERSION.tgz"
  test -f "$chart" || {
    echo "Missing packaged chart: $chart" >&2
    exit 1
  }
  docker image inspect "$IMAGE_REPO/$service:$IMAGE_TAG" >/dev/null
done

port_forward_pids=()

cleanup() {
  for pid in "${port_forward_pids[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
}
trap cleanup EXIT

if ! kind get clusters | grep -qx "$CLUSTER_NAME"; then
  echo "Kind cluster '$CLUSTER_NAME' does not exist" >&2
  exit 1
fi

kubectl config use-context "kind-$CLUSTER_NAME" >/dev/null
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null

for service in ops ingester oscar web; do
  image="$IMAGE_REPO/$service:$IMAGE_TAG"
  kind load docker-image "$image" --name "$CLUSTER_NAME"
done

helm upgrade --install postgres helm/postgres \
  --namespace "$NAMESPACE" \
  --set-string auth.database=okapi_oscar \
  --set-string auth.username=okapi_oscar_user_admin \
  --set-string auth.password=okapi_oscar_password \
  --wait --timeout "$HELM_TIMEOUT"

helm upgrade --install clickhouse helm/clickhouse \
  --namespace "$NAMESPACE" \
  --set-string auth.password=okapi_testing_password \
  --wait --timeout "$HELM_TIMEOUT"

chart_ref() {
  local service="$1"
  printf 'helm/dist/%s-%s.tgz' "$service" "$CHART_VERSION"
}

helm upgrade --install ops "$(chart_ref ops)" \
  --namespace "$NAMESPACE" --wait --timeout "$HELM_TIMEOUT" \
  --set-string image.repository="$IMAGE_REPO/ops" --set-string image.tag="$IMAGE_TAG" \
  --set-string clickhouse.host=clickhouse --set-string clickhouse.port=8123 \
  --set-string clickhouse.username=default --set-string clickhouse.password=okapi_testing_password \
  --set-string postgres.url='jdbc:postgresql://postgres:5432/okapi_oscar?currentSchema=okapi_web' \
  --set-string postgres.username=okapi_web_migration_user \
  --set-string postgres.password=okapi_web_migration_password

helm upgrade --install ingester "$(chart_ref ingester)" \
  --namespace "$NAMESPACE" --wait --timeout "$HELM_TIMEOUT" \
  --set-string image.repository="$IMAGE_REPO/ingester" --set-string image.tag="$IMAGE_TAG" \
  --set-string clickhouse.host=clickhouse --set-string clickhouse.port=8123 \
  --set-string clickhouse.username=default --set-string clickhouse.password=okapi_testing_password

helm upgrade --install oscar "$(chart_ref oscar)" \
  --namespace "$NAMESPACE" --wait --timeout "$HELM_TIMEOUT" \
  --set-string image.repository="$IMAGE_REPO/oscar" --set-string image.tag="$IMAGE_TAG" \
  --set-string postgres.host=postgres --set-string postgres.port=5432 \
  --set-string postgres.database=okapi_oscar --set-string postgres.username=okapi_oscar_user \
  --set-string postgres.password=okapi_oscar_password --set-string openai.apiKey=dummy

helm upgrade --install web "$(chart_ref web)" \
  --namespace "$NAMESPACE" --wait --timeout "$HELM_TIMEOUT" \
  --set-string image.repository="$IMAGE_REPO/web" --set-string image.tag="$IMAGE_TAG" \
  --set-string postgres.host=postgres --set-string postgres.port=5432 \
  --set-string postgres.database=okapi_oscar --set-string postgres.username=okapi_web_user \
  --set-string postgres.password=okapi_web_password

kubectl -n "$NAMESPACE" rollout status statefulset/postgres --timeout="$HELM_TIMEOUT"
kubectl -n "$NAMESPACE" rollout status statefulset/clickhouse --timeout="$HELM_TIMEOUT"
kubectl -n "$NAMESPACE" wait --for=condition=complete job/ops --timeout="$HELM_TIMEOUT"
for service in ingester oscar web; do
  kubectl -n "$NAMESPACE" rollout status deployment/"$service" --timeout="$HELM_TIMEOUT"
done

kubectl -n "$NAMESPACE" port-forward svc/web 19001:9001 >/tmp/okapi-web-port-forward.log 2>&1 &
port_forward_pids+=("$!")
kubectl -n "$NAMESPACE" port-forward svc/ingester 19009:9009 >/tmp/okapi-ingester-port-forward.log 2>&1 &
port_forward_pids+=("$!")

for attempt in {1..30}; do
  if curl --fail --silent http://127.0.0.1:19001/internal/healthcheck >/dev/null \
    && curl --fail --silent http://127.0.0.1:19009/health >/dev/null; then
    echo "Okapi Helm validation passed"
    exit 0
  fi
  sleep 2
done

echo "Okapi HTTP smoke checks failed" >&2
cat /tmp/okapi-web-port-forward.log >&2 || true
cat /tmp/okapi-ingester-port-forward.log >&2 || true
exit 1
