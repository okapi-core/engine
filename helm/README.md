# Okapi Helm charts

This directory contains the four independently deployable Okapi charts. They
assume ClickHouse and PostgreSQL are provided by the surrounding platform.

| Chart | Owns |
| --- | --- |
| `ops` | One-shot ClickHouse and PostgreSQL migrations |
| `ingester` | OTLP ingestion and query cluster |
| `oscar` | Oscar incident-triage service |
| `web` | Okapi API and UI |

The charts are published as OCI artifacts under `oci://ghcr.io/okapi-core`.

## Local Minikube test

The Okapi charts do not install databases. For a disposable Minikube test,
the repository provides a separate infrastructure target that installs
PostgreSQL and the repository-owned single-node ClickHouse test chart:

```sh
make helm-infra-local
make helm-local
```

`helm-local` uses the pinned published Okapi image tag `0.0.2` by default.
Override `HELM_LOCAL_IMAGE_REPO` and `HELM_LOCAL_IMAGE_TAG` to test local
images.

The infrastructure target uses the `okapi` namespace by default. Override it
with `HELM_INFRA_NAMESPACE` and `HELM_NS` when using a temporary namespace.

Example:

```yaml
springOverrides:
  okapi:
    clickhouse:
      host: clickhouse
      port: 8123
      secure: false
    aws:
      region: eu-west-2
```

Install:

```sh
helm upgrade --install ops helm/ops -f ops-values.yaml --wait
helm upgrade --install ingester helm/ingester -f ingester-values.yaml --wait
helm upgrade --install oscar helm/oscar -f oscar-values.yaml --wait
helm upgrade --install web helm/web -f web-values.yaml --wait
```

## HA deployment (self-hosted)
In this setup we deploy a replicated version of okapi as a service with sub-components fronted by a load balancer.

Steps:
1) Deploy ClickHouse first.
   - Use the official ClickHouse chart or your own manifests.
   - Note the ClickHouse service DNS name (example):
     `clickhouse.okapi.svc.cluster.local`

2) Ensure AWS credentials are available to the okapi workloads.

3) Deploy ingester as a replicated service (ClusterIP), pointing it at the externally managed ClickHouse service.

```sh
helm install ingester helm/ingester \
  --namespace okapi --create-namespace \
  --set service.type=ClusterIP \
  --set clickhouse.host=clickhouse.okapi.svc.cluster.local \
  --set clickhouse.port=8123 \
  --set clickhouse.username=default \
  --set clickhouse.password=secure_prod_password
```

4) Deploy Oscar and web. Oscar remains internal; web serves the API and UI and can be exposed through a LoadBalancer or Ingress.

```sh
helm install oscar helm/oscar \
  --namespace okapi \
  --set ingester.endpoint=http://ingester.okapi.svc.cluster.local:9009

helm install web helm/web \
  --namespace okapi \
  --set replicaCount=2 \
  --set service.type=LoadBalancer \
  --set ingester.endpoint=http://ingester.okapi.svc.cluster.local:9009 \
  --set oscar.endpoint=http://oscar.okapi.svc.cluster.local:9002
```

```sh
kubectl get svc -n okapi web
```

To fetch the external IP for the web UI once the LoadBalancer is provisioned, run:

```sh
kubectl get svc -n okapi web -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
```

You can now open this UI in the browser and get started :).

# Overriding spring configs
The charts expose service-specific configuration through values and support
references to existing Kubernetes Secrets for database credentials. Additional
Spring configuration can be supplied through `springOverrides`.
