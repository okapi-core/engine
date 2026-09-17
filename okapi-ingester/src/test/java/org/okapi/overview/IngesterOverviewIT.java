/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.overview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.chtest.ChTestOnlyUtils;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.overview.IngesterOverviewRequest;
import org.okapi.testmodules.guice.TestChMetricsModule;

class IngesterOverviewIT {

  @TempDir Path tempDir;

  private Client client;
  private IngesterOverview overview;
  private long nowMillis;
  private long insideMillis;
  private long outsideMillis;

  @BeforeEach
  void setup() throws Exception {
    Injector injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    truncateOverviewTables();

    nowMillis = 1_700_000_300_000L;
    insideMillis = nowMillis - 60_000L;
    outsideMillis = nowMillis - 10 * 60_000L;
    overview =
        new IngesterOverview(
            client,
            injector.getInstance(org.okapi.metrics.ch.template.ChMetricTemplateEngine.class),
            Clock.fixed(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC));
  }

  @Test
  void countsEventsInsideRequestedWindow() {
    insertInsideWindowRows();
    insertOutsideWindowRows();

    var response = overview.overview(IngesterOverviewRequest.builder().window("5m").build());

    assertEquals("5m", response.getWindow());
    assertEquals(nowMillis - 5 * 60_000L, response.getStartMillis());
    assertEquals(nowMillis, response.getEndMillis());
    assertEquals(4L, response.getMetricsEvents());
    assertEquals(2L, response.getTraceEvents());
    assertEquals(3L, response.getLogEvents());
  }

  @Test
  void rejectsUnsupportedWindow() {
    assertThrows(
        BadRequestException.class,
        () -> overview.overview(IngesterOverviewRequest.builder().window("2h").build()));
  }

  private void truncateOverviewTables() {
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_GAUGES);
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_SUM);
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_HISTOS);
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_EXPONENTIAL_HISTOS);
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_SPANS_V1);
    ChTestOnlyUtils.truncateTable(client, ChConstants.TBL_LOGS_V1);
  }

  private void insertInsideWindowRows() {
    insertGauge(insideMillis, "overview_gauge_inside");
    insertSum(insideMillis, "overview_sum_inside");
    insertHisto(insideMillis, "overview_histo_inside");
    insertExponentialHisto(insideMillis, "overview_exp_histo_inside");
    insertSpan(insideMillis, "trace-inside-1", "span-inside-1");
    insertSpan(insideMillis + 1_000L, "trace-inside-2", "span-inside-2");
    insertLog(insideMillis, "inside log one");
    insertLog(insideMillis + 1_000L, "inside log two");
    insertLog(insideMillis + 2_000L, "inside log three");
  }

  private void insertOutsideWindowRows() {
    insertGauge(outsideMillis, "overview_gauge_outside");
    insertSum(outsideMillis, "overview_sum_outside");
    insertHisto(outsideMillis, "overview_histo_outside");
    insertExponentialHisto(outsideMillis, "overview_exp_histo_outside");
    insertSpan(outsideMillis, "trace-outside", "span-outside");
    insertLog(outsideMillis, "outside log");
  }

  private void insertGauge(long millis, String metric) {
    client.queryAll(
        """
        INSERT INTO okapi_metrics.gauge_raw_samples (timestamp, metric, tags, value, unit)
        VALUES (toDateTime64(%s/1000.0, 3, 'UTC'), '%s', map('env', 'test'), 1.0, '1')
        """
            .formatted(millis, metric));
  }

  private void insertSum(long millis, String metric) {
    client.queryAll(
        """
        INSERT INTO okapi_metrics.sums_raw_samples
          (metric_name, tags, ts_start, ts_end, value, unit, sums_type)
        VALUES
          ('%s', map('env', 'test'), toDateTime64(%s/1000.0, 3, 'UTC'),
           toDateTime64(%s/1000.0, 3, 'UTC'), 1.0, '1', 'DELTA')
        """
            .formatted(metric, millis, millis + 1_000L));
  }

  private void insertHisto(long millis, String metric) {
    client.queryAll(
        """
        INSERT INTO okapi_metrics.histo_raw_samples
          (metric_name, tags, ts_start, ts_end, buckets, counts, sum, count, unit, histo_type)
        VALUES
          ('%s', map('env', 'test'), toDateTime64(%s/1000.0, 3, 'UTC'),
           toDateTime64(%s/1000.0, 3, 'UTC'), [1.0], [1], 1.0, 1, 'ms', 'DELTA')
        """
            .formatted(metric, millis, millis + 1_000L));
  }

  private void insertExponentialHisto(long millis, String metric) {
    client.queryAll(
        """
        INSERT INTO okapi_metrics.exponential_histo_raw_samples
          (metric_name, tags, ts_start, ts_end, scale, zero_threshold, zero_count,
           positive_offset, positive_counts, negative_offset, negative_counts, sum, count, unit,
           histo_type)
        VALUES
          ('%s', map('env', 'test'), toDateTime64(%s/1000.0, 3, 'UTC'),
           toDateTime64(%s/1000.0, 3, 'UTC'), 1, 0.0, 0, 0, [1], 0, [], 1.0, 1, 'ms', 'DELTA')
        """
            .formatted(metric, millis, millis + 1_000L));
  }

  private void insertSpan(long millis, String traceId, String spanId) {
    client.queryAll(
        """
        INSERT INTO okapi_traces.spans_table_v1
          (ts_start_ns, ts_end_ns, span_id, span_status, parent_span_id, trace_id, kind,
           kind_string, service_name)
        VALUES
          (%s, %s, '%s', 'OK', '', '%s', 'SERVER', 'server', 'overview-service')
        """
            .formatted(millis * 1_000_000L, (millis + 100L) * 1_000_000L, spanId, traceId));
  }

  private void insertLog(long millis, String body) {
    client.queryAll(
        """
        INSERT INTO okapi_logs.logs_table_v1 (ts_ns, log_stream, service_name, log_level, body)
        VALUES (%s, 'overview-stream', 'overview-service', 9, '%s')
        """
            .formatted(millis * 1_000_000L, body));
  }
}
