/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.logs.core.FakeLogsEventEmitter;
import org.okapi.logs.core.LogsEvent;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.logs.ChLogFilter;
import org.okapi.rest.logs.ChLogFilterField;
import org.okapi.rest.logs.ChLogLevelComparison;
import org.okapi.rest.logs.ChLogRow;
import org.okapi.rest.logs.ChLogStringMatchType;
import org.okapi.rest.logs.ChLogsQueryRequest;
import org.okapi.testmodules.guice.TestChLogsModule;

public class ChLogsQueryServiceTests {
  private static final long BASE_NS = 1_000_000_000L;
  private static final long HOUR_BOUNDARY_NS = 3_600_000_000_000L;

  private Injector injector;
  private Client client;
  private ChLogsQueryService queryService;

  @BeforeEach
  void setup() {
    var corpus = buildCorpus();
    injector =
        Guice.createInjector(
            new TestChLogsModule(List.of(new LogsEvent(corpus.toByteArray())), 16));
    client = injector.getInstance(Client.class);
    queryService = injector.getInstance(ChLogsQueryService.class);
    CreateChTablesSpec.migrate(client);
    truncateTable();
    injector.getInstance(ChLogsWalConsumerDriver.class).onTick();
  }

  @Test
  void consumesEventsAndCommits() {
    var emitter = injector.getInstance(FakeLogsEventEmitter.class);
    assertEquals(1, emitter.getCommitCount());

    var resp = queryService.getLogs(request(null, 20));
    assertNotNull(resp.getItems());
    assertEquals(7, resp.getItems().size());
  }

  @Test
  void filtersByExactStreamServiceAndContent() {
    var resp =
        queryService.getLogs(
            request(
                List.of(
                    exact(ChLogFilterField.LOG_STREAM, "prod-us-east"),
                    exact(ChLogFilterField.SERVICE_NAME, "checkout-api"),
                    exact(ChLogFilterField.CONTENT, "Order confirmed order=1001 user=u123")),
                10));

    assertSingleBody(resp.getItems(), "Order confirmed order=1001 user=u123");
  }

  @Test
  void filtersByRegexStreamServiceAndContent() {
    var resp =
        queryService.getLogs(
            request(
                List.of(
                    regex(ChLogFilterField.LOG_STREAM, "prod-.*"),
                    regex(ChLogFilterField.SERVICE_NAME, ".*api"),
                    regex(ChLogFilterField.CONTENT, ".*failed.*")),
                10));

    assertEquals(2, resp.getItems().size());
    assertTrue(
        resp.getItems().stream()
            .map(ChLogRow::getBody)
            .toList()
            .containsAll(
                List.of(
                    "Payment authorization failed order=2002 provider=stripe",
                    "Order creation failed order=2002 cause=payment_error")));
  }

  @Test
  void filtersByLevelComparisons() {
    var equal =
        queryService.getLogs(
            request(List.of(level(ChLogLevelComparison.EQUAL, SeverityNumber.SEVERITY_NUMBER_WARN_VALUE)), 10));
    assertEquals(1, equal.getItems().size());
    assertEquals("Inventory low for sku=SHOE-RED-42 remaining=2", equal.getItems().getFirst().getBody());

    var greater =
        queryService.getLogs(
            request(List.of(level(ChLogLevelComparison.GREATER, SeverityNumber.SEVERITY_NUMBER_WARN_VALUE)), 10));
    assertEquals(2, greater.getItems().size());

    var less =
        queryService.getLogs(
            request(List.of(level(ChLogLevelComparison.LESS, SeverityNumber.SEVERITY_NUMBER_INFO_VALUE)), 10));
    assertEquals(1, less.getItems().size());
    assertEquals(
        "PricingEngine applied discount code=SPRING10 order=1001",
        less.getItems().getFirst().getBody());
  }

  @Test
  void appliesTimeBoundsAndOrdersByNewestFirst() {
    var resp =
        queryService.getLogs(
            ChLogsQueryRequest.builder()
                .tsStartNanos(BASE_NS + 3)
                .tsEndNanos(BASE_NS + 7)
                .limit(10)
                .build());

    assertEquals(4, resp.getItems().size());
    assertEquals(BASE_NS + 7, resp.getItems().get(0).getTsNanos());
    assertEquals(BASE_NS + 3, resp.getItems().get(3).getTsNanos());
  }

  @Test
  void appliesAndSemanticsAcrossFilters() {
    var matching =
        queryService.getLogs(
            request(
                List.of(
                    exact(ChLogFilterField.LOG_STREAM, "prod-us-east"),
                    exact(ChLogFilterField.SERVICE_NAME, "checkout-api"),
                    level(ChLogLevelComparison.EQUAL, SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE),
                    regex(ChLogFilterField.CONTENT, "Payment.*failed.*")),
                10));
    assertSingleBody(matching.getItems(), "Payment authorization failed order=2002 provider=stripe");

    var nonMatching =
        queryService.getLogs(
            request(
                List.of(
                    exact(ChLogFilterField.LOG_STREAM, "prod-us-east"),
                    exact(ChLogFilterField.SERVICE_NAME, "catalog-api"),
                    level(ChLogLevelComparison.EQUAL, SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE)),
                10));
    assertEquals(0, nonMatching.getItems().size());
  }

  @Test
  void appliesLimit() {
    var resp = queryService.getLogs(request(null, 2));
    assertEquals(2, resp.getItems().size());
    assertEquals(HOUR_BOUNDARY_NS + 5_000_000L, resp.getItems().get(0).getTsNanos());
    assertEquals(BASE_NS + 7, resp.getItems().get(1).getTsNanos());
  }

  @Test
  void queriesEscapedContent() {
    var exact =
        queryService.getLogs(
            request(List.of(exact(ChLogFilterField.CONTENT, "Worker path O'Reilly\\ops")), 10));
    assertSingleBody(exact.getItems(), "Worker path O'Reilly\\ops");

    var regex =
        queryService.getLogs(
            request(List.of(regex(ChLogFilterField.CONTENT, "Worker path O'Reilly\\\\ops")), 10));
    assertSingleBody(regex.getItems(), "Worker path O'Reilly\\ops");
  }

  @Test
  void handlesPartitionBoundaryTimeFilter() {
    var resp =
        queryService.getLogs(
            ChLogsQueryRequest.builder()
                .tsStartNanos(HOUR_BOUNDARY_NS)
                .tsEndNanos(HOUR_BOUNDARY_NS + 10_000_000L)
                .limit(10)
                .build());
    assertSingleBody(resp.getItems(), "Boundary log after first hour");
  }

  private ChLogsQueryRequest request(List<ChLogFilter> filters, int limit) {
    return ChLogsQueryRequest.builder()
        .tsStartNanos(0L)
        .tsEndNanos(HOUR_BOUNDARY_NS + 1_000_000_000L)
        .filters(filters)
        .limit(limit)
        .build();
  }

  private ChLogFilter exact(ChLogFilterField field, String value) {
    return ChLogFilter.builder()
        .field(field)
        .stringMatchType(ChLogStringMatchType.EXACT)
        .value(value)
        .build();
  }

  private ChLogFilter regex(ChLogFilterField field, String value) {
    return ChLogFilter.builder()
        .field(field)
        .stringMatchType(ChLogStringMatchType.REGEX)
        .value(value)
        .build();
  }

  private ChLogFilter level(ChLogLevelComparison comparison, int level) {
    return ChLogFilter.builder()
        .field(ChLogFilterField.LOG_LEVEL)
        .levelComparison(comparison)
        .level(level)
        .build();
  }

  private void assertSingleBody(List<ChLogRow> items, String body) {
    assertEquals(1, items.size(), items.toString());
    assertEquals(body, items.getFirst().getBody());
  }

  private void truncateTable() {
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_LOGS_V1);
  }

  private ExportLogsServiceRequest buildCorpus() {
    return ExportLogsServiceRequest.newBuilder()
        .addResourceLogs(
            resourceLogs(
                "checkout-api",
                "prod-us-east",
                List.of(
                    log(BASE_NS + 1, SeverityNumber.SEVERITY_NUMBER_INFO_VALUE,
                        "Order confirmed order=1001 user=u123", Map.of()),
                    log(BASE_NS + 2, SeverityNumber.SEVERITY_NUMBER_DEBUG_VALUE,
                        "PricingEngine applied discount code=SPRING10 order=1001", Map.of()),
                    log(BASE_NS + 4, SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE,
                        "Payment authorization failed order=2002 provider=stripe", Map.of()),
                    log(BASE_NS + 7, SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE,
                        "Order creation failed order=2002 cause=payment_error", Map.of()))))
        .addResourceLogs(
            resourceLogs(
                "catalog-api",
                "prod-eu-west",
                List.of(
                    log(BASE_NS + 3, SeverityNumber.SEVERITY_NUMBER_WARN_VALUE,
                        "Inventory low for sku=SHOE-RED-42 remaining=2", Map.of()))))
        .addResourceLogs(
            resourceLogs(
                "worker",
                null,
                List.of(
                    log(BASE_NS + 5, SeverityNumber.SEVERITY_NUMBER_INFO_VALUE,
                        "Worker path O'Reilly\\ops", Map.of("log.stream", "batch-payments")),
                    log(HOUR_BOUNDARY_NS + 5_000_000L, SeverityNumber.SEVERITY_NUMBER_INFO_VALUE,
                        "Boundary log after first hour", Map.of("log.stream", "boundary")))))
        .build();
  }

  private ResourceLogs resourceLogs(String service, String stream, List<LogRecord> records) {
    var resource = Resource.newBuilder().addAttributes(attr("service.name", service));
    if (stream != null) {
      resource.addAttributes(attr("log.stream", stream));
    }
    return ResourceLogs.newBuilder()
        .setResource(resource)
        .addScopeLogs(ScopeLogs.newBuilder().addAllLogRecords(records))
        .build();
  }

  private LogRecord log(long tsNs, int severity, String body, Map<String, String> attrs) {
    var builder =
        LogRecord.newBuilder()
            .setTimeUnixNano(tsNs)
            .setSeverityNumber(SeverityNumber.forNumber(severity))
            .setBody(AnyValue.newBuilder().setStringValue(body));
    attrs.forEach((key, value) -> builder.addAttributes(attr(key, value)));
    return builder.build();
  }

  private KeyValue attr(String key, String value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(AnyValue.newBuilder().setStringValue(value))
        .build();
  }
}
