/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.okapi.otelshorthand.OtelShortHands.keyValue;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.common.UnionValue;
import org.okapi.rest.logs.ChLogFilter;
import org.okapi.rest.logs.ChLogFilterOp;
import org.okapi.rest.logs.ChLogsSummaryRequest;
import org.okapi.testmodules.guice.TestChLogsModule;

public class ChLogsSummaryServiceTests {
  private static final long BASE_NS = 1_000_000_000L;
  private static final long HOUR_BOUNDARY_NS = 3_600_000_000_000L;

  private Injector injector;
  private Client client;
  private ChLogsSummaryService summaryService;

  @BeforeEach
  void setup() {
    injector =
        Guice.createInjector(
            new TestChLogsModule(
                List.of(new org.okapi.logs.core.LogsEvent(buildCorpus().toByteArray())), 16));
    client = injector.getInstance(Client.class);
    summaryService = injector.getInstance(ChLogsSummaryService.class);
    recreateLogsTable();
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_LOGS_V1);
    injector.getInstance(ChLogsWalConsumerDriver.class).onTick();
  }

  private void recreateLogsTable() {
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_logs");
    client.queryAll("DROP TABLE IF EXISTS " + ChConstants.TBL_LOGS_V1);
    CreateChTablesSpec.migrate(client);
  }

  @Test
  void returnsCoreAnalyticsForFilteredWindow() {
    var response =
        summaryService.getSummary(
            ChLogsSummaryRequest.builder()
                .tsStartNanos(0L)
                .tsEndNanos(HOUR_BOUNDARY_NS + 1_000_000_000L)
                .bucketMillis(1_000L)
                .limit(10)
                .build());

    assertNotNull(response);
    assertEquals(7, response.getCount());
    assertEquals(1_000L, response.getBucketMillis());
    assertEquals(2, response.getVolume().size());
    assertEquals(6, response.getVolume().get(0).getCount());
    assertEquals(1, response.getVolume().get(1).getCount());

    assertEquals(4, response.getSeverityDistribution().size());
    assertCountForLevel(
        response.getSeverityDistribution(), SeverityNumber.SEVERITY_NUMBER_DEBUG_VALUE, 1);
    assertCountForLevel(
        response.getSeverityDistribution(), SeverityNumber.SEVERITY_NUMBER_INFO_VALUE, 3);
    assertCountForLevel(
        response.getSeverityDistribution(), SeverityNumber.SEVERITY_NUMBER_WARN_VALUE, 1);
    assertCountForLevel(
        response.getSeverityDistribution(), SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE, 2);

    assertEquals("checkout-api", response.getTopServices().getFirst().getValue());
    assertEquals(4, response.getTopServices().getFirst().getCount());
    assertTrue(
        response.getTopStreams().stream()
            .anyMatch(facet -> "prod-us-east".equals(facet.getValue()) && facet.getCount() == 4));

    var checkout =
        response.getServiceSeverity().stream()
            .filter(row -> "checkout-api".equals(row.getServiceName()))
            .findFirst()
            .orElseThrow();
    assertEquals(4, checkout.getTotal());
    assertEquals(1, checkout.getDebug());
    assertEquals(1, checkout.getInfo());
    assertEquals(2, checkout.getError());
    assertEquals(0.5, checkout.getErrorRatio(), 0.0001);
  }

  @Test
  void appliesSameFiltersAsSearch() {
    var response =
        summaryService.getSummary(
            ChLogsSummaryRequest.builder()
                .tsStartNanos(0L)
                .tsEndNanos(HOUR_BOUNDARY_NS + 1_000_000_000L)
                .bucketMillis(1_000L)
                .filters(
                    List.of(
                        exact("log.stream", "prod-us-east"),
                        regex("body", ".*failed.*"),
                        number(
                            "severity.number",
                            ChLogFilterOp.GT,
                            SeverityNumber.SEVERITY_NUMBER_WARN_VALUE)))
                .limit(10)
                .build());

    assertEquals(2, response.getCount());
    assertEquals(1, response.getTopServices().size());
    assertEquals("checkout-api", response.getTopServices().getFirst().getValue());
    assertEquals(1, response.getSeverityDistribution().size());
    assertEquals(
        SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE,
        response.getSeverityDistribution().getFirst().getLogLevel());
    assertEquals(2, response.getSeverityTimeline().getFirst().getCount());
  }

  private void assertCountForLevel(
      List<org.okapi.rest.logs.ChLogsSeverityCount> counts, int logLevel, long expected) {
    var count =
        counts.stream()
            .filter(item -> item.getLogLevel() == logLevel)
            .findFirst()
            .orElseThrow()
            .getCount();
    assertEquals(expected, count);
  }

  private ChLogFilter exact(String key, String value) {
    return ChLogFilter.builder()
        .key(key)
        .op(ChLogFilterOp.EQ)
        .value(UnionValue.stringValue(value))
        .build();
  }

  private ChLogFilter regex(String key, String value) {
    return ChLogFilter.builder()
        .key(key)
        .op(ChLogFilterOp.REGEX)
        .value(UnionValue.stringValue(value))
        .build();
  }

  private ChLogFilter number(String key, ChLogFilterOp op, int value) {
    return ChLogFilter.builder().key(key).op(op).value(UnionValue.integerValue(value)).build();
  }

  private ExportLogsServiceRequest buildCorpus() {
    return ExportLogsServiceRequest.newBuilder()
        .addResourceLogs(
            resourceLogs(
                "checkout-api",
                "prod-us-east",
                List.of(
                    log(BASE_NS + 1, SeverityNumber.SEVERITY_NUMBER_INFO_VALUE, "Order confirmed"),
                    log(
                        BASE_NS + 2,
                        SeverityNumber.SEVERITY_NUMBER_DEBUG_VALUE,
                        "PricingEngine applied discount"),
                    log(
                        BASE_NS + 4,
                        SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE,
                        "Payment authorization failed"),
                    log(
                        BASE_NS + 7,
                        SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE,
                        "Order creation failed"))))
        .addResourceLogs(
            resourceLogs(
                "catalog-api",
                "prod-eu-west",
                List.of(
                    log(BASE_NS + 3, SeverityNumber.SEVERITY_NUMBER_WARN_VALUE, "Inventory low"))))
        .addResourceLogs(
            resourceLogs(
                "worker",
                null,
                List.of(
                    log(BASE_NS + 5, SeverityNumber.SEVERITY_NUMBER_INFO_VALUE, "Worker started"),
                    log(
                        HOUR_BOUNDARY_NS + 5_000_000L,
                        SeverityNumber.SEVERITY_NUMBER_INFO_VALUE,
                        "Boundary log"))))
        .build();
  }

  private ResourceLogs resourceLogs(String service, String stream, List<LogRecord> records) {
    var resource = Resource.newBuilder().addAttributes(keyValue("service.name", service));
    if (stream != null) {
      resource.addAttributes(keyValue("log.stream", stream));
    }
    return ResourceLogs.newBuilder()
        .setResource(resource)
        .addScopeLogs(ScopeLogs.newBuilder().addAllLogRecords(records))
        .build();
  }

  private LogRecord log(long tsNs, int severity, String body) {
    return LogRecord.newBuilder()
        .setTimeUnixNano(tsNs)
        .setSeverityNumber(SeverityNumber.forNumber(severity))
        .setBody(AnyValue.newBuilder().setStringValue(body))
        .build();
  }
}
