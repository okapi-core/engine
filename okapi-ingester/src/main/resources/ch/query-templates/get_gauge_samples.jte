@param org.okapi.metrics.ch.template.ChGetGaugeQueryTemplate data

SELECT ${data.getBucketExpr()} AS bucket_ms, ${data.getAggExpr()} AS value, tags, unit
FROM ${data.getTable()}
WHERE 1 = 1
AND metric = '${data.getMetric()}'
AND timestamp >= toDateTime64(${data.getStartMs()}/1000.0, 3, 'UTC')
AND timestamp < toDateTime64(${data.getEndMs()}/1000.0, 3, 'UTC')
@if (data.getTags() != null)
    @for(var tag : data.getTags().entrySet())
        AND mapContains(tags, '${tag.getKey()}') AND tags['${tag.getKey()}'] = '${tag.getValue()}'
    @endfor
@endif
GROUP BY tags, unit, bucket_ms ORDER BY tags, unit, bucket_ms
