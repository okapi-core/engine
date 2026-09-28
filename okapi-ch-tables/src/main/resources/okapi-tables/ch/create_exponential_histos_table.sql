CREATE TABLE IF NOT EXISTS okapi_metrics.exponential_histo_raw_samples (
    metric_name LowCardinality(String),
    tags Map(String, String),
    ts_start DateTime64(3, 'UTC'),
    ts_end DateTime64(3, 'UTC'),
    scale Int32,
    zero_threshold Float64,
    zero_count UInt64,
    positive_offset Int32,
    positive_counts Array(UInt64),
    negative_offset Int32,
    negative_counts Array(UInt64),
    sum Nullable(Float64),
    count UInt64,
    unit LowCardinality(String),
    histo_type Enum('DELTA' = 1, 'CUMULATIVE' = 2)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(ts_start)
ORDER BY (metric_name, toUnixTimestamp(ts_start));
