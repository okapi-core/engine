CREATE TABLE IF NOT EXISTS okapi_metrics.metric_events_stream_meta ON CLUSTER 'okapi'
AS okapi_metrics.metric_events_stream_meta_local
ENGINE = Distributed('okapi', 'okapi_metrics', 'metric_events_stream_meta_local', cityHash64(metric));
