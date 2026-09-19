# Okapi

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![CI](https://github.com/okapi-core/engine/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/okapi-core/engine/actions/workflows/ci.yml)
[![Community](https://img.shields.io/badge/community-discussions-green)](https://github.com/okapi-core/okapi/discussions)

Okapi is an OpenTelemetry-native observability platform for AI-assisted
incident investigation. It ingests metrics, logs, and traces in OpenTelemetry
format, stores them in a ClickHouse-backed data lake, and makes that context
available to people and AI agents.

Okapi includes Oscar, its built-in AI incident-triage agent.

## Capabilities

- Ingest OpenTelemetry metrics, logs, and traces over OTLP/HTTP.
- Consume telemetry directly or through Kafka-based pipelines.
- Query metrics with PromQL, traces with TraceQL, and logs with Okapi LogQL.
- Explore dashboards, metric and attribute hints, trace flame graphs, and
  RED/service-overview data.
- Ask questions and steer Oscar’s investigation through a chat interface.
- Investigate incidents with Oscar using correlated metrics, logs, and traces.
- Access telemetry through an API-backed pathway, giving Oscar a defined
  interface for querying data stored in Okapi rather than unfettered access to
  the underlying telemetry store.
- Connect external AI agents through MCP.
- Retrieve relevant code context from GitHub and GitLab while investigating
  application errors.

## Choose an installation path

The intended user-facing interface is `okapictl`.

### Try the complete demo locally

The local demo runs Okapi together with the OpenTelemetry Astronomy Shop and
requires Docker. It is the recommended way to evaluate Okapi without creating
cloud infrastructure:

```sh
pip install okapi-ctl
okapictl demo --local
```

The command prints the URLs for the Okapi UI and the OpenTelemetry demo when
the services are ready. The demo can be stopped and removed with:

```sh
okapictl demo stop --local
okapictl demo destroy --local
```

### Try the complete demo on AWS

The AWS demo provisions the OpenTelemetry demo and Okapi on AWS using the
public `okapi-demo-tf` deployment repository. It requires an AWS account and
credentials, and creates billable resources:

```sh
pip install okapi-ctl
okapictl demo --aws
```

When finished, stop or destroy the environment explicitly:

```sh
okapictl demo stop --aws
okapictl demo destroy --aws
```

### Install Okapi locally

Use this path when you want Okapi itself but do not need the complete
OpenTelemetry demo:

```sh
pip install okapi-ctl
okapictl install --local
```

### Install Okapi on Kubernetes

Use this path for a Kubernetes cluster. The installation uses the Okapi Helm
charts and requires a ClickHouse deployment, either an existing one or one
provided by the installation workflow:

```sh
pip install okapi-ctl
okapictl install --k8s
```

For direct Helm usage and configuration options, see the [Helm documentation](./helm/README.md).

## Deploying Okapi

The Okapi Helm charts deploy only Okapi. ClickHouse and PostgreSQL are
external dependencies and must already be reachable from the Kubernetes
namespace where Okapi is installed.

The charts are published as OCI artifacts in GHCR. For a release such as
`0.0.2`, install the migration job first, followed by the services:

```sh
export OKAPI_VERSION=0.0.2
export OKAPI_NAMESPACE=okapi

helm upgrade --install ops oci://ghcr.io/okapi-core/ops \
  --version "$OKAPI_VERSION" \
  --namespace "$OKAPI_NAMESPACE" --create-namespace \
  -f deployment-artifacts/values-yaml/ha/ops-values.yaml --wait

helm upgrade --install ingester oci://ghcr.io/okapi-core/ingester \
  --version "$OKAPI_VERSION" \
  --namespace "$OKAPI_NAMESPACE" \
  -f deployment-artifacts/values-yaml/ha/okapi-ingester-values.yaml --wait

helm upgrade --install oscar oci://ghcr.io/okapi-core/oscar \
  --version "$OKAPI_VERSION" \
  --namespace "$OKAPI_NAMESPACE" \
  -f deployment-artifacts/values-yaml/ha/oscar-values.yaml --wait

helm upgrade --install web oci://ghcr.io/okapi-core/web \
  --version "$OKAPI_VERSION" \
  --namespace "$OKAPI_NAMESPACE" \
  -f deployment-artifacts/values-yaml/ha/okapi-web-values.yaml --wait
```

The sample files contain placeholders for database service names. Replace
them and provide credentials through Kubernetes Secrets before installing.

### Production high-availability deployment

Production database topology, storage, backups, ClickHouse replication, and
PostgreSQL high availability are outside the scope of Okapi. Configure those
systems first, then create an application Secret containing the keys expected
by the sample values files:

```sh
kubectl -n okapi create secret generic okapi-secrets \
  --from-literal=clickhouse-username='<clickhouse-user>' \
  --from-literal=clickhouse-password='<clickhouse-password>' \
  --from-literal=migration-username='<postgres-migration-user>' \
  --from-literal=migration-password='<postgres-migration-password>' \
  --from-literal=oscar-username='<postgres-oscar-user>' \
  --from-literal=oscar-password='<postgres-oscar-password>' \
  --from-literal=web-username='<postgres-web-user>' \
  --from-literal=web-password='<postgres-web-password>' \
  --from-literal=openai-api-key='<openai-api-key>'
```

Edit the four files in
`deployment-artifacts/values-yaml/ha/` with the service DNS names for the
external databases. The examples configure three replicas, HPA, and a
PodDisruptionBudget for the stateless services. Expose `web` through an
Ingress or LoadBalancer and keep `ingester` and `oscar` internal unless your
architecture requires otherwise.

The chart `values.yaml` files under `helm/` document the complete schema and
development defaults, but they are not production configurations: they use
single replicas, `latest` image defaults, inline value placeholders, and
ClusterIP services. Use the HA sample overrides as the starting point for a
real deployment and store environment-specific Secrets outside Git.

## Configure OpenTelemetry to send data to Okapi

Okapi accepts OTLP/HTTP on the ingester endpoint. For a local installation,
the endpoint is typically:

```text
http://localhost:9009
```

The OTLP/HTTP exporter appends the signal-specific paths automatically:

```text
/v1/metrics
/v1/traces
/v1/logs
```

For example, an OpenTelemetry Collector can forward all three signal types to
Okapi with this configuration:

```yaml
receivers:
  otlp:
    protocols:
      grpc:
      http:

processors:
  batch:

exporters:
  otlphttp/okapi:
    endpoint: http://localhost:9009
    compression: none
    headers:
      X-Okapi-Tenant: demo

service:
  pipelines:
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlphttp/okapi]
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlphttp/okapi]
    logs:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlphttp/okapi]
```

Run the collector with:

```sh
otelcol --config otel-collector.yaml
```

For a remote or Kubernetes installation, replace `http://localhost:9009` with
the externally reachable Okapi ingester endpoint. Keep the tenant header
consistent with the tenant used by the Okapi installation.

## OpenTelemetry demo harness

For development and benchmark work, the repository also contains a pinned
OpenTelemetry demo harness:

```sh
make docker-all
make otel-harness
```

The harness checks out a pinned OpenTelemetry demo revision, adds the Okapi
collector and Compose configuration, and starts the demo without the demo's
other observability backends.

To check that telemetry is flowing into Okapi:

```sh
curl -s \
  -H 'Content-Type: application/json' \
  -d '{"window":"5m"}' \
  http://localhost:9009/api/v1/overview
```

The setup is ready when the response reports non-zero metrics and trace event
counts.

## License

Licensed under the [Apache License, Version 2.0](./LICENSE).
