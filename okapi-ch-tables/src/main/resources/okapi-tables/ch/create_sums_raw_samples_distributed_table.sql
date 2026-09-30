CREATE TABLE IF NOT EXISTS okapi_metrics.sums_raw_samples_dist ON CLUSTER 'okapi'
AS okapi_metrics.sums_raw_samples_local
ENGINE = Distributed('okapi', 'okapi_metrics', 'sums_raw_samples_local', cityHash64(metric_name));
