# Dashboard metric references

The dashboards are based on these reference database versions and collector
receivers:

- PostgreSQL 16 with the `postgresql` receiver.
- ClickHouse 25.12 with ClickHouse's Prometheus metrics endpoint and the
  Collector's `prometheus` receiver.
- An Okapi ingester reachable at `http://localhost:9009`.

## PostgreSQL 16

The PostgreSQL server must allow the Collector to connect without TLS for the
test setup. Create a monitoring account with the `pg_monitor` role:

```sql
CREATE USER monitoring_user WITH PASSWORD 'monitoring_password';
GRANT pg_monitor TO monitoring_user;
```

The receiver should connect to the database service and explicitly disable
TLS for this plain-TCP test deployment:

```yaml
receivers:
  postgresql:
    endpoint: postgres:5432
    username: monitoring_user
    password: monitoring_password
    databases: [okapi_db]
    collection_interval: 10s
    tls:
      insecure: true
```

Export the resulting metrics with the normal OTLP/HTTP exporter:

```yaml
exporters:
  otlphttp/okapi:
    endpoint: http://localhost:9009
    compression: none

service:
  pipelines:
    metrics:
      receivers: [postgresql]
      exporters: [otlphttp/okapi]
```

The exporter sends to Okapi's `/v1/metrics` endpoint. This preserves the
PostgreSQL metric names used by the dashboard.

The PostgreSQL dashboard is
[`postgres-16-monitoring.v2.yaml`](postgres/postgres-16-monitoring.v2.yaml).
It uses the receiver's actual names, including `postgresql.blks_hit`,
`postgresql.tup_inserted`, `postgresql.db_size`, and
`postgresql.bgwriter.*`. Counter-style receiver metrics are queried as
`SUM`/`CUMULATIVE`; `postgresql.connection.max` is a `GAUGE`.

## ClickHouse 25.12

The tested Collector image does not provide a native `clickhouse` receiver.
ClickHouse must expose its Prometheus endpoint instead:

```xml
<clickhouse>
    <prometheus>
        <endpoint>/metrics</endpoint>
        <port>9363</port>
        <metrics>true</metrics>
        <events>true</events>
        <asynchronous_metrics>true</asynchronous_metrics>
        <errors>true</errors>
    </prometheus>
</clickhouse>
```

Scrape that endpoint with the Collector:

```yaml
receivers:
  prometheus/clickhouse:
    config:
      scrape_configs:
        - job_name: clickhouse
          scrape_interval: 10s
          static_configs:
            - targets: [clickhouse:9363]
```

Export the scrape to Okapi with the normal OTLP/HTTP exporter:

```yaml
exporters:
  otlphttp/okapi:
    endpoint: http://localhost:9009
    compression: none

service:
  pipelines:
    metrics:
      receivers: [prometheus/clickhouse]
      exporters: [otlphttp/okapi]
```

The exporter sends to Okapi's `/v1/metrics` endpoint. Do not use
`/prometheus/v1/metrics`, because that endpoint rewrites metric names.

The ClickHouse dashboard is
[`clickhouse-25.12-monitoring.v2.yaml`](clickhouse/clickhouse-25.12-monitoring.v2.yaml).
Its names come from the Prometheus endpoint, for example
`ClickHouseProfileEvents_Query`, `ClickHouseMetrics_MemoryTracking`, and
`ClickHouseAsyncMetrics_DiskUsed_default`. Profile-event counters are queried
as `SUM`/`CUMULATIVE`; server and asynchronous runtime measurements are
queried as `GAUGE`.

ClickHouse exposes thousands of metrics. For a focused dashboard deployment,
use a Prometheus `metric_relabel_configs` keep-list containing the metrics
used by the dashboard. This reduces OTLP payload size and avoids delaying
ingestion of the dashboard's time series.

## Kafka

The Kafka dashboards consume the metric names emitted by the OpenTelemetry
Collector's `kafkametrics` receiver. The Collector connects to Kafka and
exports the resulting metrics to Okapi; Okapi does not need direct network
access to the Kafka brokers.

For a cluster without authentication, configure the receiver and an OTLP/HTTP
metrics pipeline as follows:

```yaml
receivers:
  kafkametrics:
    brokers:
      - kafka-1:9092
      # - kafka-2:9092
    protocol_version: 2.8.0
    collection_interval: 15s
    scrapers:
      - brokers
      - topics
      - consumers

processors:
  batch: {}

exporters:
  otlphttp/okapi:
    # The exporter appends /v1/metrics.
    endpoint: http://localhost:9009
    compression: none
    headers:
      Content-Type: application/octet-stream

service:
  pipelines:
    metrics:
      receivers: [kafkametrics]
      processors: [batch]
      exporters: [otlphttp/okapi]
```

Replace `kafka-1:9092` with an address reachable from the Collector, and
replace the Okapi endpoint when the ingester is not running locally. Configure
TLS and/or SASL in the receiver when the Kafka cluster requires authentication.
The `brokers` scraper reports cluster metrics, `topics` reports topic and
partition metrics, and `consumers` reports consumer-group offsets, lag, and
membership.

Consumer dashboards only have data for groups that exist in Kafka. Create and
run a consumer for the target topic before selecting the `group`, `topic`, and
`partition` dashboard variables.

The Kafka dashboards are:

- [`kafka-broker-overview.v2.yaml`](kafka/kafka-broker-overview.v2.yaml)
- [`kafka-topic-partition-offsets.v2.yaml`](kafka/kafka-topic-partition-offsets.v2.yaml)
- [`kafka-consumer-group-lag.v2.yaml`](kafka/kafka-consumer-group-lag.v2.yaml)
- [`kafka-consumer-offsets.v2.yaml`](kafka/kafka-consumer-offsets.v2.yaml)
