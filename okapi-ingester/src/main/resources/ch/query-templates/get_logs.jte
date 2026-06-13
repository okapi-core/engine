@param org.okapi.logs.ch.ChLogsQueryTemplate data

SELECT
    ts_ns,
    log_stream,
    service_name,
    log_level,
    body
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
        @if(f.isRegex())
            AND match(${f.getColumn()}, '${f.getValue()}')
        @elseif(f.isNumeric())
            AND ${f.getColumn()} ${f.getOperator()} ${f.getValue()}
        @else
            AND ${f.getColumn()} ${f.getOperator()} '${f.getValue()}'
        @endif
    @endfor
@endif
ORDER BY ts_ns DESC
LIMIT ${data.getLimit()}
