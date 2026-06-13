@param org.okapi.promql.ch.ChGetExponentialHistoQueryTemplate data

SELECT metric_name, tags, unit,
toUnixTimestamp64Milli(ts_start) AS ts_start_ms,
toUnixTimestamp64Milli(ts_end) AS ts_end_ms,
scale, zero_threshold, zero_count,
positive_offset, positive_counts,
negative_offset, negative_counts,
sum, count, toString(histo_type) AS histo_type
FROM ${data.getTable()}
PREWHERE metric_name = '${data.getMetric()}'
AND unit = '${data.getUnit()}'
AND ts_start < toDateTime64(${data.getTe()}/1000.0, 3, 'UTC')
AND ts_end > toDateTime64(${data.getTs()}/1000.0, 3, 'UTC')
AND histo_type = '${data.getHistoType()}'
AND length(tags) = ${data.getTags().size()}
@for(var tag : data.getTags().entrySet())
    AND mapContains(tags, '${tag.getKey()}') AND tags['${tag.getKey()}'] = '${tag.getValue()}'
@endfor
