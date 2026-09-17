@param org.okapi.metrics.ch.template.ChGetExemplarTemplate data

select * from ${data.getFqTable()}
where ts_nanos <= ${data.getTsNanosEnd()}
AND ts_nanos >= ${data.getTsNanosStart()}
AND metric_name = '${data.getMetricName()}'
@if(data.getTags() != null)
    @for(var tag : data.getTags().entrySet())
        AND mapContains(tags, '${tag.getKey()}')
        AND tags['${tag.getKey()}'] = '${tag.getValue()}'
    @endfor
@endif
LIMIT 1000
