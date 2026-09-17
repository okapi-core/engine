@param org.okapi.logs.ch.ChLogsQueryTemplate data

SELECT
    ts_ns,
    log_stream,
    service_name,
    log_level,
    severity_text,
    trace_id,
    span_id,
    body,
    resource_attribs_str_0,
    resource_attribs_str_1,
    resource_attribs_str_2,
    resource_attribs_str_3,
    resource_attribs_str_4,
    resource_attribs_str_5,
    resource_attribs_str_6,
    resource_attribs_str_7,
    resource_attribs_str_8,
    resource_attribs_str_9,
    resource_attribs_number_0,
    resource_attribs_number_1,
    resource_attribs_number_2,
    resource_attribs_number_3,
    resource_attribs_number_4,
    resource_attribs_number_5,
    resource_attribs_number_6,
    resource_attribs_number_7,
    resource_attribs_number_8,
    resource_attribs_number_9,
    attribs_str_0,
    attribs_str_1,
    attribs_str_2,
    attribs_str_3,
    attribs_str_4,
    attribs_str_5,
    attribs_str_6,
    attribs_str_7,
    attribs_str_8,
    attribs_str_9,
    attribs_number_0,
    attribs_number_1,
    attribs_number_2,
    attribs_number_3,
    attribs_number_4,
    attribs_number_5,
    attribs_number_6,
    attribs_number_7,
    attribs_number_8,
    attribs_number_9
FROM ${data.getTable()}
WHERE 1=1
@if(data.getTsStartNs() != null)
    AND ts_ns >= ${data.getTsStartNs()}
@endif
@if(data.getTsEndNs() != null)
    AND ts_ns <= ${data.getTsEndNs()}
@endif
@if(data.getFilters() != null && !data.getFilters().isEmpty())
    @for(var f : data.getFilters())
        AND ${f.getSql()}
    @endfor
@endif
ORDER BY ts_ns DESC
LIMIT ${data.getLimit()}
