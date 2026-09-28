CREATE TABLE IF NOT EXISTS okapi_traces.service_red_events (
    ts_start_nanos Int64,
    ts_end_nanos Int64,
    service_name LowCardinality(String),
    span_name LowCardinality(String),
    peer_service_name LowCardinality(String),
    span_kind Enum('CLIENT' = 1, 'SERVER' = 2, 'PRODUCER' = 3, 'CONSUMER' = 4, 'UNK' = 5, 'INTERNAL' = 6),
    span_status Enum('OK' = 1, 'ERROR' = 2, 'UNK' = 3),
)
ENGINE = MergeTree
PARTITION BY toStartOfHour(toDateTime(ts_start_nanos / 1000000000))
ORDER BY (ts_start_nanos, service_name);
