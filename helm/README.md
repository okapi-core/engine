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

## Production high availability

For a production deployment, use the HA sample values in
`deployment-scripts/values-yaml/ha/` and follow the deployment guide in the
[repository README](../README.md#production-high-availability-deployment).
ClickHouse and PostgreSQL are external dependencies; these charts do not
install or manage them.

# Overriding spring configs
The charts expose service-specific configuration through values and support
references to existing Kubernetes Secrets for database credentials. Additional
Spring configuration can be supplied through `springOverrides`.
