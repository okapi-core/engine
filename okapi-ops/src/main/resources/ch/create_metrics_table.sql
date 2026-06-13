CREATE TABLE IF NOT EXISTS okapi_metrics.gauge_raw_samples (
    timestamp DateTime64(3, 'UTC'),
    metric LowCardinality(String),
    tags Map(String, String),
    value Float64,
    unit LowCardinality(String),
    minute UInt8 DEFAULT toStartOfMinute(timestamp),
    hour UInt8 DEFAULT toStartOfHour(timestamp),
    day UInt8 DEFAULT toStartOfDay(timestamp),
    month UInt8 DEFAULT toStartOfMonth(timestamp)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(timestamp)
ORDER BY (metric, toUnixTimestamp(timestamp));
