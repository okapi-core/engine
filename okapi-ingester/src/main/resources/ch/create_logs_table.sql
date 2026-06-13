CREATE TABLE IF NOT EXISTS okapi_logs.logs_table_v1
(
    ts_ns Int64,
    log_stream LowCardinality(String),
    service_name LowCardinality(String),
    log_level Int32,
    body String CODEC(ZSTD),
    INDEX body_text_idx(body) TYPE text(tokenizer = 'splitByNonAlpha') GRANULARITY 1
)
ENGINE = MergeTree
PARTITION BY toStartOfHour(toDateTime(ts_ns / 1000000000))
ORDER BY (ts_ns, log_stream, service_name, log_level);
