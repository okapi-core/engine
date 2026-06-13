@param org.okapi.promql.ch.ChGetGaugeRawQueryTemplate data

SELECT toUnixTimestamp64Milli(timestamp) AS ts_ms, value
FROM ${data.getTable()}
PREWHERE 1
AND metric = '${data.getMetric()}'
AND timestamp >= toDateTime64(${data.getStartMs()}/1000.0, 3, 'UTC')
AND timestamp <= toDateTime64(${data.getEndMs()}/1000.0, 3, 'UTC')
AND length(tags) = ${data.getTags().size()}
@for(var tag : data.getTags().entrySet())
    AND mapContains(tags, '${tag.getKey()}') AND tags['${tag.getKey()}'] = '${tag.getValue()}'
@endfor
ORDER BY ts_ms
