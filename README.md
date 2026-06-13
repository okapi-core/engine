# Okapi - an observability stack built for fast queries and compatibility with existing systems

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![Community](https://img.shields.io/badge/community-discussions-green)](https://github.com/okapi-core/okapi/discussions)

Okapi is an observability stack.

It stores metrics and traces collected in OpenTelemetry format and allows users to create dashboards, explore metrics,
query spans in a UI.
Okapi also exposes a PromQl querying system meaning metrics ingested in Okapi can be visualized on
Grafana or Perses.

Okapi is built to be easy to self-host and manage. Getting started is easy with the `okapi-cp` CLI.
Please refer to the Quickstart for options on how deploy Okapi.

---

## (Quicker) Very fast quickstart
The fastest way to try out Okapi is with Okapi's fork of the Otel Demo. An OpenAI API key is needed to run Oscar.

```shell
export OPENAI_API_KEY=<your-openai-api-key>
git clone https://github.com/okapi-core/opentelemetry-demo.git
cd opentelemetry-demo
make start-minimal
```

## Why and when to use Okapi ?

Existing observability are usually bursting at the seams, there's either too much data or too many dollars involved.
Okapi is built to augment existing observability deployments with additional capacity.
This is why "play-nice" is an explicit goal so teams can start using Okapi with minimal disruption and onboarding.

## Feature list
- 🪄**Oscar an AI SRE agent** : Okapi has a research agent called Oscar. Its aim is to remove grunt work associated with data collection while debugging production issues. Oscar can either do simple tasks such as finding metrics, error traces, summarizing service or do multistep debugging (upto a point) such as correlating host specific metrics with slow database queries. The best way to test it capabilities is to run Oscar on the Otel demo.
- 🔌 **OpenTelemetry native** : Okapi is compatible with OTLP meaning any collector that emits metrics as OTel or submits
  spans via OTel can leverage Okapi as a storage and analysis layer.
- 📈 **PromQl** : Okapi has its own implementation of PromQl. Metrics stored in Okapi can be
  visualized using existing solutions such as Grafana and Perses.
- 🧩 **Dashboard designer** : Okapi has its own dashboard designer should you choose to just use our own UI.
- 🧱 **Dashboards as code and templates** : Dashboards can be expressed as intuitive YAML. Refer to templates for examples
  on how to monitor Clickhouse, Postgres, Kafka.
- 🔍 **Spans Browser and Visualizer** : Okapi comes with its own browser for Spans. Users can search spans, visualize span attributes, view attributes of a span.
- ✨ **Autocomplete almost everywhere** : Nearly every form field in the Okapi UI has autocomplete minimizing the
  need for copy-paste.
- 🧷 **Support for arbitrary span fields** : Traces forwarded to Okapi can contain spans with arbitrary attributes.
- 🩺 **Out of box service healths** : Get golden metrics in one place with Service health boards.
- 🎯 **Granular REDs** : Okapi calculates golden signals not just for the aggregate service, all its service-peer paths and all associated operations. This data is picked up automatically from spans submitted to Okapi.


## (Slower) Quickstart

### Deploy on local machine (for testing)

Install the CLI:

```sh
pip install okapi-cp
```

Local deployment (testing):

```sh
# note these keys will need to be changed for a production deployment.
okapi-cp deploy local --hmac-key 5e1a04d3 --api-key a2991d99
```

Okapi will be available at `http://localhost:9001` once the healthcheck succeeds.

### Development test infrastructure

The repository uses Docker Compose for the local services required by integration tests:
ClickHouse, LocalStack, PostgreSQL, and Vault.

```sh
export OPENAI_API_KEY=<your-openai-api-key>
make test-infra
```

`make test-infra` builds the operations artifact, waits for each container to become healthy,
initializes Vault, and runs the ClickHouse and DynamoDB migrations. Stop the stack with:

```sh
make stop-test-infra
```

ClickHouse data remains under `${HOME}/.okapi-data` when the stack is stopped. Set `ch_dir`
when invoking Make to use another location. The existing `make ch`, `make localstack`,
`make postgres`, and `make oscar-vault-dev` targets start individual Compose services.

### Deploy on Kubernetes (sample production deployment)

Full deployment with LocalStack and a new ClickHouse install:

```sh
helm repo add clickhouse https://charts.clickhouse.com
helm install clickhouse clickhouse/clickhouse --namespace okapi --create-namespace
okapi-cp deploy k8s \
  --chart-repo oci://ghcr.io/okapi-core \
  --namespace okapi \
  --aws-mode localstack \
  --aws-endpoint http://localstack.okapi.svc.cluster.local:4566 \
  --clickhouse-host clickhouse.okapi.svc.cluster.local \
  --clickhouse-port 8123 \
  --ingester-service-type ClusterIP \
  --web-service-type LoadBalancer
```

Using an existing ClickHouse deployment:

```sh
okapi-cp deploy k8s \
  --chart-repo oci://ghcr.io/okapi-core \
  --namespace okapi \
  --aws-mode localstack \
  --aws-endpoint http://localstack.okapi.svc.cluster.local:4566 \
  --clickhouse-host <CLICKHOUSE_HOST> \
  --clickhouse-port 8123
```

Note: the Okapi Helm charts are published in GHCR at `oci://ghcr.io/okapi-core`.

### Helm charts (OCI)

Okapi Helm charts are published to GHCR:

- `oci://ghcr.io/okapi-core/okapi-ingester`
- `oci://ghcr.io/okapi-core/okapi-web`

Sample Helm deploy (assumes ClickHouse and LocalStack are already running in `okapi`):

```sh
helm upgrade --install okapi-ingester oci://ghcr.io/okapi-core/okapi-ingester \
  --namespace okapi --create-namespace \
  --set springOverrides.okapi.clickhouse.host=clickhouse.okapi.svc.cluster.local \
  --set springOverrides.okapi.clickhouse.port=8123 \
  --set springOverrides.okapi.clickhouse.username=default \
  --set springOverrides.okapi.clickhouse.password=YOUR_CLICKHOUSE_PASSWORD \
  --set springOverrides.okapi.clickhouse.secure=false \
  --set springOverrides.okapi.aws.endpoint=http://localstack.okapi.svc.cluster.local:4566

helm upgrade --install okapi-web oci://ghcr.io/okapi-core/okapi-web \
  --namespace okapi --create-namespace \
  --set springOverrides.clusterEndpoint=http://okapi-ingester.okapi.svc.cluster.local:9009 \
  --set springOverrides.okapi.aws.endpoint=http://localstack.okapi.svc.cluster.local:4566
```

## OTLP/HTTP Ingest (OpenTelemetry)

### Example: Setting up OTel collector with okapi export

Assuming a local deployment, here's how to forward host metrics via OTel collector to Okapi.
Collector config (`otel-collector.yaml`):

```yaml
receivers:
  hostmetrics:
    collection_interval: 10s
    scrapers:
      cpu: { }
      memory: { }
      disk: { }
      filesystem: { }
      load: { }
      network: { }

processors:
  batch: { }

exporters:
  otlphttp/okapi:
    # Base endpoint; exporter appends /v1/metrics for metrics
    endpoint: http://localhost:9009
    compression: none
    headers:
      X-Okapi-Tenant: demo

service:
  pipelines:
    metrics:
      receivers: [ hostmetrics ]
      processors: [ batch ]
      exporters: [ otlphttp/okapi ]
```

Run the collector:

```bash
otelcol --config otel-collector.yaml
```

You can then start making dashboards in the Okapi UI.
`http://localhost:9001`

## License

Licensed under the [Apache License, Version 2.0](./LICENSE).
