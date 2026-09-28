CREATE TABLE IF NOT EXISTS okapi_metrics.histo_raw_samples ON CLUSTER 'okapi'
AS okapi_metrics.histo_raw_samples_local
ENGINE = Distributed('okapi', 'okapi_metrics', 'histo_raw_samples_local', cityHash64(metric_name));
