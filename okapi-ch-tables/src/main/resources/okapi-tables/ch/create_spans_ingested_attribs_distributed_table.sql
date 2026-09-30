CREATE TABLE IF NOT EXISTS okapi_traces.spans_ingested_attribs_dist ON CLUSTER 'okapi'
AS okapi_traces.spans_ingested_attribs_local
ENGINE = Distributed(
    'okapi', 'okapi_traces', 'spans_ingested_attribs_local', cityHash64(attribute_name));
