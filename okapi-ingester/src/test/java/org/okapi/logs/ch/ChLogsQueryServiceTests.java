/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.logs.ch.ChLogsCorpus.*;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.engine.ch.ChLogsEngine;
import org.okapi.logs.core.FakeLogsEventEmitter;
import org.okapi.logs.core.LogsEvent;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.common.UNION_TYPE;
import org.okapi.rest.logs.*;
import org.okapi.testmodules.guice.TestChLogsModule;

@TestInstance(Lifecycle.PER_CLASS)
public class ChLogsQueryServiceTests {
  private Injector injector;
  private Client client;
  private ChLogsQueryService queryService;
  private ChLogsEngine logsEngine;

  @BeforeAll
  void setup() {
    var corpus = buildCorpus();
    injector =
        Guice.createInjector(
            new TestChLogsModule(List.of(new LogsEvent(corpus.toByteArray())), 16));
    client = injector.getInstance(Client.class);
    queryService = injector.getInstance(ChLogsQueryService.class);
    logsEngine = injector.getInstance(ChLogsEngine.class);
    recreateLogsTable();
    truncateTable();
    injector.getInstance(ChLogsWalConsumerDriver.class).onTick();
  }

  private void recreateLogsTable() {
    client.queryAll("CREATE DATABASE IF NOT EXISTS okapi_logs");
    client.queryAll("DROP TABLE IF EXISTS " + ChConstants.TBL_LOGS_V1);
    CreateChTablesSpec.migrate(client);
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
                    exact("log.stream", "prod-us-east"),
                    exact("service.name", "checkout-api"),
                    exact("body", "Order confirmed order=1001 user=u123")),
                10));

    assertSingleBody(resp.getItems(), "Order confirmed order=1001 user=u123");
  }

  @Test
  void filtersByRegexStreamServiceAndContent() {
    var resp =
        queryService.getLogs(
            request(
                List.of(
                    regex("log.stream", "prod-.*"),
                    regex("service.name", ".*api"),
                    regex("body", ".*failed.*")),
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
            request(
                List.of(
                    number(
                        "severity.number",
                        ChLogFilterOp.EQ,
                        SeverityNumber.SEVERITY_NUMBER_WARN_VALUE)),
                10));
    assertEquals(1, equal.getItems().size());
    assertEquals(
        "Inventory low for sku=SHOE-RED-42 remaining=2", equal.getItems().getFirst().getBody());

    var greater =
        queryService.getLogs(
            request(
                List.of(
                    number(
                        "severity.number",
                        ChLogFilterOp.GT,
                        SeverityNumber.SEVERITY_NUMBER_WARN_VALUE)),
                10));
    assertEquals(2, greater.getItems().size());

    var less =
        queryService.getLogs(
            request(
                List.of(
                    number(
                        "severity.number",
                        ChLogFilterOp.LT,
                        SeverityNumber.SEVERITY_NUMBER_INFO_VALUE)),
                10));
    assertEquals(1, less.getItems().size());
    assertEquals(
        "PricingEngine applied discount code=SPRING10 order=1001",
        less.getItems().getFirst().getBody());
  }

  @Test
  void testFiltrationOps() {
    assertLineIds(
        queryService
            .getLogs(request(List.of(exact("service.name", "checkout-api")), 10))
            .getItems(),
        Set.of("line-1", "line-2", "line-4", "line-5"));
    assertLineIds(
        queryService
            .getLogs(
                request(List.of(string("service.name", ChLogFilterOp.NEQ, "checkout-api")), 10))
            .getItems(),
        Set.of("line-3", "line-6", "line-7"));
    assertLineIds(
        queryService
            .getLogs(request(List.of(string("body", ChLogFilterOp.CONTAINS, "failed")), 10))
            .getItems(),
        Set.of("line-4", "line-5"));
    assertLineIds(
        queryService.getLogs(request(List.of(regex("body", ".*failed.*")), 10)).getItems(),
        Set.of("line-4", "line-5"));
    assertLineIds(
        queryService
            .getLogs(request(List.of(string("log.stream", ChLogFilterOp.PREFIX, "prod-")), 10))
            .getItems(),
        Set.of("line-1", "line-2", "line-3", "line-4", "line-5"));
    assertLineIds(
        queryService
            .getLogs(
                request(
                    List.of(
                        number(
                            "severity.number",
                            ChLogFilterOp.GTE,
                            SeverityNumber.SEVERITY_NUMBER_WARN_VALUE)),
                    10))
            .getItems(),
        Set.of("line-3", "line-4", "line-5"));
    assertLineIds(
        queryService
            .getLogs(
                request(
                    List.of(
                        number(
                            "severity.number",
                            ChLogFilterOp.LTE,
                            SeverityNumber.SEVERITY_NUMBER_INFO_VALUE)),
                    10))
            .getItems(),
        Set.of("line-1", "line-2", "line-6", "line-7"));
    assertLineIds(
        queryService
            .getLogs(
                request(
                    List.of(
                        ChLogFilter.builder()
                            .key("http.status_code")
                            .op(ChLogFilterOp.EXISTS)
                            .build()),
                    10))
            .getItems(),
        Set.of("line-1"));
    assertLineIds(
        queryService
            .getLogs(
                request(
                    List.of(
                        ChLogFilter.builder()
                            .key("http.status_code")
                            .op(ChLogFilterOp.NOT_EXISTS)
                            .build()),
                    10))
            .getItems(),
        Set.of("line-2", "line-3", "line-4", "line-5", "line-6", "line-7"));
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
                    exact("log.stream", "prod-us-east"),
                    exact("service.name", "checkout-api"),
                    number(
                        "severity.number",
                        ChLogFilterOp.EQ,
                        SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE),
                    regex("body", "Payment.*failed.*")),
                10));
    assertSingleBody(
        matching.getItems(), "Payment authorization failed order=2002 provider=stripe");

    var nonMatching =
        queryService.getLogs(
            request(
                List.of(
                    exact("log.stream", "prod-us-east"),
                    exact("service.name", "catalog-api"),
                    number(
                        "severity.number",
                        ChLogFilterOp.EQ,
                        SeverityNumber.SEVERITY_NUMBER_ERROR_VALUE)),
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
        queryService.getLogs(request(List.of(exact("body", "Worker path O'Reilly\\ops")), 10));
    assertSingleBody(exact.getItems(), "Worker path O'Reilly\\ops");

    var regex =
        queryService.getLogs(request(List.of(regex("body", "Worker path O'Reilly\\\\ops")), 10));
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

  @Test
  void storesStructuredAttributesInPartitionedMaps() {
    int routeBucket = ChLogsAttributeBucketer.bucketForKey("http.route");
    int statusBucket = ChLogsAttributeBucketer.bucketForKey("http.status_code");
    int durationBucket = ChLogsAttributeBucketer.bucketForKey("duration_ms");
    int envBucket = ChLogsAttributeBucketer.bucketForKey("deployment.environment");

    var rows =
        client.queryAll(
            """
            SELECT
              attribs_str_%d['http.route'] AS route,
              attribs_number_%d['http.status_code'] AS status,
              attribs_number_%d['duration_ms'] AS duration,
              resource_attribs_str_%d['deployment.environment'] AS env
            FROM %s
            WHERE body = 'Order confirmed order=1001 user=u123'
            """
                .formatted(
                    routeBucket, statusBucket, durationBucket, envBucket, ChConstants.TBL_LOGS_V1));

    assertEquals(1, rows.size());
    var row = rows.getFirst();
    assertEquals("/checkout", row.getString("route"));
    assertEquals(200.0, row.getDouble("status"));
    assertEquals(12.5, row.getDouble("duration"));
    assertEquals("prod", row.getString("env"));
  }

  @Test
  void queryReturnsStructuredFieldsAndFiltersByStructuredAttributes() {
    var resp =
        queryService.getLogs(
            request(List.of(number("http.status_code", ChLogFilterOp.EQ, 200)), 10));

    assertSingleBody(resp.getItems(), "Order confirmed order=1001 user=u123");
    var row = resp.getItems().getFirst();
    assertEquals("INFO", row.getSeverityText());
    assertEquals("", row.getTraceId());
    assertEquals("", row.getSpanId());
    assertEquals("/checkout", row.getAttributes().get("http.route").getStringValue());
    assertEquals(200.0, row.getAttributes().get("http.status_code").getDoubleValue());
    assertEquals(12.5, row.getAttributes().get("duration_ms").getDoubleValue());
    assertEquals(
        "prod", row.getResourceAttributes().get("deployment.environment").getStringValue());
  }

  @Test
  void queryCanProjectStructuredAttributes() {
    var resp =
        queryService.getLogs(
            ChLogsQueryRequest.builder()
                .tsStartNanos(0L)
                .tsEndNanos(HOUR_BOUNDARY_NS + 1_000_000_000L)
                .filters(List.of(exact("body", "Order confirmed order=1001 user=u123")))
                .attributeKeys(List.of("http.route"))
                .includeResourceAttributes(false)
                .limit(10)
                .build());

    var row = resp.getItems().getFirst();
    assertEquals(1, row.getAttributes().size());
    assertEquals("/checkout", row.getAttributes().get("http.route").getStringValue());
    assertEquals(null, row.getResourceAttributes());
  }

  @Test
  void returnsFieldAndValueSuggestionsForAutocomplete() {
    var fields =
        queryService.getFields(
            ChLogsFieldsRequest.builder().queryPrefix("http.").limit(10).build());

    assertTrue(
        fields.getFields().stream()
            .anyMatch(
                field ->
                    field.getType() == UNION_TYPE.STRING && "http.route".equals(field.getName())));
    assertTrue(
        fields.getFields().stream()
            .anyMatch(
                field ->
                    field.getType() == UNION_TYPE.DOUBLE
                        && "http.status_code".equals(field.getName())));

    var values =
        queryService.getFieldValues(
            ChLogsFieldValuesRequest.builder()
                .key("http.route")
                .type(UNION_TYPE.STRING)
                .valuePrefix("/che")
                .limit(10)
                .build());

    assertEquals(1, values.getValues().size());
    assertEquals("/checkout", values.getValues().getFirst().getValue().getStringValue());
    assertEquals(1, values.getValues().getFirst().getCount());
  }

  @Test
  void logQlProjectsStructuredAttributesAsRows() {
    var response =
        logsEngine.queryWithLogQl(
            OkapiLogQlRequest.builder()
                .tsStartNanos(0L)
                .tsEndNanos(HOUR_BOUNDARY_NS + 1_000_000_000L)
                .logQl(
                    "service = 'checkout-api' and fields['http.status_code'] = 200"
                        + " | select (service, fields['http.route'], fields['http.status_code'])"
                        + " | limit 5")
                .build());

    assertEquals(OkapiLogQlResultKind.TABLE, response.getKind());
    assertEquals(1, response.getRows().size());
    var row = response.getRows().getFirst();
    assertEquals("checkout-api", row.get("service"));
    assertEquals("/checkout", row.get("http_route"));
    assertEquals("200", row.get("http_status_code"));
  }

  @Test
  void logQlCountByReturnsGroupedRows() {
    var response =
        logsEngine.queryWithLogQl(
            OkapiLogQlRequest.builder()
                .tsStartNanos(0L)
                .tsEndNanos(HOUR_BOUNDARY_NS + 1_000_000_000L)
                .logQl("service = 'checkout-api' | count by (service, fields['http.status_code'])")
                .build());

    assertEquals(OkapiLogQlResultKind.COUNT_BY, response.getKind());
    var statusRow =
        response.getRows().stream()
            .filter(row -> "checkout-api".equals(row.get("service")))
            .filter(row -> "200".equals(row.get("http_status_code")))
            .findFirst()
            .orElseThrow();
    assertEquals(1L, ((Number) statusRow.get("count")).longValue());
  }

  private void assertSingleBody(List<ChLogRow> items, String body) {
    assertEquals(1, items.size(), items.toString());
    assertEquals(body, items.getFirst().getBody());
  }

  private void truncateTable() {
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_LOGS_V1);
  }

  private void assertLineIds(List<ChLogRow> items, Set<String> expectedLineIds) {
    assertEquals(
        expectedLineIds,
        items.stream()
            .map(item -> item.getAttributes().get("line_id").getStringValue())
            .collect(Collectors.toSet()));
  }
}
