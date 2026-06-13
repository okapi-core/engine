@param org.okapi.metrics.ch.template.ChGetSumQueryTemplate data

SELECT metric_name, tags, unit,
toUnixTimestamp64Milli(ts_start) AS ts_start_ms,
toUnixTimestamp64Milli(ts_end) AS ts_end_ms,
value, toString(sums_type) AS sums_type
FROM ${data.getTable()}
PREWHERE metric_name = '${data.getMetric()}'
AND ts_start < toDateTime64(${data.getTe()}/1000.0, 3, 'UTC')
AND ts_end > toDateTime64(${data.getTs()}/1000.0, 3, 'UTC')
AND sums_type = '${data.getSumsType()}'
@if(data.getTags() != null)
    AND length(tags) = ${data.getTags().size()}
    @for(var tag : data.getTags().entrySet())
        AND mapContains(tags, '${tag.getKey()}') AND tags['${tag.getKey()}'] = '${tag.getValue()}'
    @endfor
@endif
