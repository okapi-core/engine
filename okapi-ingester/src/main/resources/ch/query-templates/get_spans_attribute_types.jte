@param org.okapi.traces.ch.ChSpansAttributeTypesTemplate data

SELECT
attribute_name,
attribute_type
FROM okapi_traces.spans_ingested_attribs_dist
WHERE attribute_name IN (
@for(int i = 0; i < data.getAttributes().size(); i++)
    '${data.getAttributes().get(i)}'@if(i < data.getAttributes().size() - 1),@endif
@endfor
)
GROUP BY attribute_name, attribute_type
