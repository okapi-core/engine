CREATE TABLE IF NOT EXISTS okapi_metrics.exponential_histo_raw_samples ON CLUSTER 'okapi'
AS okapi_metrics.exponential_histo_raw_samples_local
ENGINE = Distributed(
    'okapi', 'okapi_metrics', 'exponential_histo_raw_samples_local', cityHash64(metric_name));
