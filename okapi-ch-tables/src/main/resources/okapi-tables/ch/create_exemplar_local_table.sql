CREATE TABLE IF NOT EXISTS okapi_metrics.metric_exemplars_local ON CLUSTER 'okapi' (
    ts_nanos Int64,
    metric_name String CODEC(ZSTD),
    tags Map(String, String),
    span_id String CODEC(ZSTD),
    trace_id String CODEC(ZSTD),
    kind LowCardinality(String),
    double_value Float64,
    int_value Int64,
    attributes_kv_list_json String CODEC(ZSTD)
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/okapi_metrics/metric_exemplars', '{replica}')
PARTITION BY toStartOfHour(toDateTime(ts_nanos / 1000000000))
ORDER BY (ts_nanos, metric_name, trace_id);
