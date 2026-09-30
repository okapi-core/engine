CREATE TABLE IF NOT EXISTS okapi_traces.spans_table_v1_dist ON CLUSTER 'okapi'
AS okapi_traces.spans_table_v1_local
ENGINE = Distributed('okapi', 'okapi_traces', 'spans_table_v1_local', cityHash64(trace_id));
