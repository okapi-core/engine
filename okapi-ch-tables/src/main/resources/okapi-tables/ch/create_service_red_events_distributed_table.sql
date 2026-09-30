CREATE TABLE IF NOT EXISTS okapi_traces.service_red_events_dist ON CLUSTER 'okapi'
AS okapi_traces.service_red_events_local
ENGINE = Distributed(
    'okapi', 'okapi_traces', 'service_red_events_local', cityHash64(service_name));
