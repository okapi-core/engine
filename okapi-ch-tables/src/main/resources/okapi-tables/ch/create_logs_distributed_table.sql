CREATE TABLE IF NOT EXISTS okapi_logs.logs_table_v1_dist ON CLUSTER 'okapi'
AS okapi_logs.logs_table_v1_local
ENGINE = Distributed('okapi', 'okapi_logs', 'logs_table_v1_local', cityHash64(log_stream));
