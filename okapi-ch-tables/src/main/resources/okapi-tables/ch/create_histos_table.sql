CREATE TABLE IF NOT EXISTS okapi_metrics.histo_raw_samples (
    metric_name LowCardinality(String),
    tags Map(String, String),
    ts_start DateTime64(3, 'UTC'),
    ts_end DateTime64(3, 'UTC'),
    buckets Array(Float64),
    counts Array(UInt64),
    sum Nullable(Float64),
    count UInt64,
    unit LowCardinality(String),
    histo_type Enum('DELTA' = 1, 'CUMULATIVE' = 2),
    minute UInt8 DEFAULT toStartOfMinute(ts_start),
    hour UInt8 DEFAULT toStartOfHour(ts_start),
    day UInt8 DEFAULT toStartOfDay(ts_start),
    month UInt8 DEFAULT toStartOfMonth(ts_start)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(ts_start)
ORDER BY (metric_name, toUnixTimestamp(ts_start));
