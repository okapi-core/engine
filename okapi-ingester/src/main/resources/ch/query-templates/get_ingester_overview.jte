@param org.okapi.overview.OverviewQueryTemplate data

SELECT
    (
        SELECT count()
        FROM ${data.getGaugeTable()}
        WHERE timestamp >= toDateTime64(${data.getStartMillis()}/1000.0, 3, 'UTC')
          AND timestamp < toDateTime64(${data.getEndMillis()}/1000.0, 3, 'UTC')
    )
    +
    (
        SELECT count()
        FROM ${data.getSumTable()}
        WHERE ts_start >= toDateTime64(${data.getStartMillis()}/1000.0, 3, 'UTC')
          AND ts_start < toDateTime64(${data.getEndMillis()}/1000.0, 3, 'UTC')
    )
    +
    (
        SELECT count()
        FROM ${data.getHistoTable()}
        WHERE ts_start >= toDateTime64(${data.getStartMillis()}/1000.0, 3, 'UTC')
          AND ts_start < toDateTime64(${data.getEndMillis()}/1000.0, 3, 'UTC')
    )
    +
    (
        SELECT count()
        FROM ${data.getExponentialHistoTable()}
        WHERE ts_start >= toDateTime64(${data.getStartMillis()}/1000.0, 3, 'UTC')
          AND ts_start < toDateTime64(${data.getEndMillis()}/1000.0, 3, 'UTC')
    ) AS metrics_events,
    (
        SELECT count()
        FROM ${data.getTracesTable()}
        WHERE ts_start_ns >= ${data.getStartNanos()}
          AND ts_start_ns < ${data.getEndNanos()}
    ) AS trace_events,
    (
        SELECT count()
        FROM ${data.getLogsTable()}
        WHERE ts_ns >= ${data.getStartNanos()}
          AND ts_ns < ${data.getEndNanos()}
    ) AS log_events
