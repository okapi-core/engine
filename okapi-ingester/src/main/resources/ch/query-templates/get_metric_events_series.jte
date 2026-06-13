@param org.okapi.promql.ch.ChSeriesDiscoveryQueryTemplate data

SELECT DISTINCT metric, tags, unit
FROM ${data.getTable()}
PREWHERE ts_start <= toDateTime64(${data.getEndMs()}/1000.0, 3, 'UTC')
AND ts_end >= toDateTime64(${data.getStartMs()}/1000.0, 3, 'UTC')
@if(data.getMetric() != null)
    AND metric = '${data.getMetric()}'
@endif
