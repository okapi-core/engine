/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.it;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.okapi.otelshorthand.OtelShortHands.keyValue;
import static org.okapi.otelshorthand.OtelShortHands.utf8Bytes;

import com.clickhouse.client.api.Client;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.chtest.ChTestOnlyUtils;
import org.okapi.logs.TestApplication;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.rest.common.UNION_TYPE;
import org.okapi.rest.traces.OkapiTraceQlRequest;
import org.okapi.rest.traces.OkapiTraceQlResponse;
import org.okapi.rest.traces.OkapiTraceQlResultKind;
import org.okapi.spring.configs.Profiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    classes = {TestApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", Profiles.PROFILE_CH})
@TestPropertySource(
    properties = {
      "okapi.clickhouse.userName=default",
      "okapi.clickhouse.password=okapi_testing_password",
      "okapi.clickhouse.secure=false",
      "okapi.clickhouse.chMetricsWalCfg.segmentSize=1024",
      "okapi.clickhouse.chLogsCfg.segmentSize=1024",
      "okapi.clickhouse.chTracesWalCfg.segmentSize=1024",
      "okapi.ch.wal.consumeIntervalMs=200",
      "okapi.ch.wal.batchSize=64"
    })
class TraceQlQueryIT {

  private static Path chWalRoot;

  @LocalServerPort private int port;
  @Autowired private RestClient restClient;
  @Autowired private Client chClient;

  private String baseUrl;

  @DynamicPropertySource
  static void dynamicProps(DynamicPropertyRegistry registry) throws Exception {
    chWalRoot = Files.createTempDirectory("okapi-ch-wal");
    registry.add(
        "okapi.clickhouse.host",
        () -> System.getenv().getOrDefault("OKAPI_TEST_CLICKHOUSE_HOST", "127.0.0.1"));
    registry.add(
        "okapi.clickhouse.port",
        () -> System.getenv().getOrDefault("OKAPI_TEST_CLICKHOUSE_PORT", "8123"));
    registry.add("okapi.clickhouse.chMetricsWal", () -> chWalRoot.resolve("metrics").toString());
    registry.add("okapi.clickhouse.chLogsWal", () -> chWalRoot.resolve("logs").toString());
    registry.add("okapi.clickhouse.chTracesWal", () -> chWalRoot.resolve("traces").toString());
  }

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + port;
    CreateChTablesSpec.migrate(chClient);
    ChTestOnlyUtils.truncateTable(chClient, ChConstants.TBL_SPANS_V1);
    ChTestOnlyUtils.truncateTable(chClient, ChConstants.TBL_SPANS_INGESTED_ATTRIBS);
  }

  @Test
  void ingestAndQueryTraceQl() {
    postOtel(buildRequest());

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var rows =
                  postTraceQl(
                      "service = 'svc-http' and duration > 90ms | select(service, status, duration) | limit 5");
              assertEquals(OkapiTraceQlResultKind.TABLE, rows.getKind());
              assertEquals(1, rows.getRows().size());
              assertEquals("svc-http", rows.getRows().getFirst().get("service").getStringValue());
              assertEquals("OK", rows.getRows().getFirst().get("status").getStringValue());
              assertEquals(UNION_TYPE.LONG, rows.getRows().getFirst().get("duration").getType());

              var counts = postTraceQl("attributes['custom.env'] = 'prod' | count by (service)");
              assertEquals(OkapiTraceQlResultKind.COUNT_BY, counts.getKind());
              assertEquals(1, counts.getRows().size());
              assertEquals("svc-http", counts.getRows().getFirst().get("service").getStringValue());
              assertEquals(UNION_TYPE.LONG, counts.getRows().getFirst().get("count").getType());
              assertEquals(1L, counts.getRows().getFirst().get("count").getLongValue());
            });
  }

  private void postOtel(ExportTraceServiceRequest request) {
    restClient
        .post()
        .uri(baseUrl + "/v1/traces")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(request.toByteArray())
        .retrieve()
        .toBodilessEntity();
  }

  private OkapiTraceQlResponse postTraceQl(String traceQl) {
    var request =
        OkapiTraceQlRequest.builder()
            .tsStartNanos(0L)
            .tsEndNanos(10_000_000_000L)
            .traceQl(traceQl)
            .build();
    var response =
        restClient
            .post()
            .uri(baseUrl + "/api/v1/spans/query/traceql")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(OkapiTraceQlResponse.class);
    assertNotNull(response);
    return response;
  }

  private ExportTraceServiceRequest buildRequest() {
    return ExportTraceServiceRequest.newBuilder()
        .addResourceSpans(buildHttpResourceSpans())
        .addResourceSpans(buildDbResourceSpans())
        .build();
  }

  private ResourceSpans buildHttpResourceSpans() {
    var resource =
        Resource.newBuilder().addAttributes(keyValue("service.name", "svc-http")).build();
    var span =
        Span.newBuilder()
            .setTraceId(utf8Bytes("trace-id-0000001"))
            .setSpanId(utf8Bytes("span0001"))
            .setName("http-span")
            .setKind(Span.SpanKind.SPAN_KIND_SERVER)
            .setStartTimeUnixNano(1_000_000_000L)
            .setEndTimeUnixNano(1_100_000_000L)
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
            .addAttributes(keyValue("http.request.method", "GET"))
            .addAttributes(OtelShortHands.keyValue("http.response.status_code", 200))
            .addAttributes(keyValue("custom.env", "prod"))
            .addAttributes(OtelShortHands.keyValue("custom.number", 42))
            .build();
    var scope = ScopeSpans.newBuilder().addSpans(span).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }

  private ResourceSpans buildDbResourceSpans() {
    var resource = Resource.newBuilder().addAttributes(keyValue("service.name", "svc-db")).build();
    var span =
        Span.newBuilder()
            .setTraceId(utf8Bytes("trace-id-0000002"))
            .setSpanId(utf8Bytes("span0002"))
            .setName("db-span")
            .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
            .setStartTimeUnixNano(1_500_000_000L)
            .setEndTimeUnixNano(1_750_000_000L)
            .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR).build())
            .addAttributes(keyValue("db.system", "mysql"))
            .addAttributes(keyValue("db.operation", "select"))
            .build();
    var scope = ScopeSpans.newBuilder().addSpans(span).build();
    return ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope).build();
  }
}
