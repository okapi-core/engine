# Okapi

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
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
