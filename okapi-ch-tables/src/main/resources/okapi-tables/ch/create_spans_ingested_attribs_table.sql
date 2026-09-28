CREATE TABLE IF NOT EXISTS okapi_traces.spans_ingested_attribs (
    ts_start_ns Int64,
    ts_end_ns Int64,
    attribute_name String CODEC(ZSTD),
    attribute_type Enum8('string' = 1, 'number' = 2)
)
ENGINE = MergeTree
PARTITION BY toStartOfHour(toDateTime(ts_start_ns / 1000000000))
ORDER BY (attribute_type, attribute_name, ts_start_ns);
