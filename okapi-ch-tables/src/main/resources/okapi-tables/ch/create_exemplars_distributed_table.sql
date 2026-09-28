CREATE TABLE IF NOT EXISTS okapi_metrics.metric_exemplars ON CLUSTER 'okapi'
AS okapi_metrics.metric_exemplars_local
ENGINE = Distributed('okapi', 'okapi_metrics', 'metric_exemplars_local', cityHash64(metric_name));
