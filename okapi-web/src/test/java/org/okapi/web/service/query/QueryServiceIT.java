/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;
import org.okapi.grammar.GRAMMAR;
import org.okapi.rest.common.UNION_TYPE;
import org.okapi.rest.common.UnionValue;
import org.okapi.rest.logs.OkapiLogQlRequest;
import org.okapi.rest.logs.OkapiLogQlResponse;
import org.okapi.rest.logs.OkapiLogQlResultKind;
import org.okapi.rest.metrics.exemplar.GetExemplarsBatchResponse;
import org.okapi.rest.metrics.exemplar.GetExemplarsResponse;
import org.okapi.rest.metrics.query.GetMetricsBatchResponse;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetMetricsResponse;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.rest.traces.OkapiTraceQlRequest;
import org.okapi.rest.traces.OkapiTraceQlResponse;
import org.okapi.rest.traces.OkapiTraceQlResultKind;
import org.okapi.web.dtos.constraints.TimeConstraint;
import org.okapi.web.dtos.dashboards.MultiQueryRequest;
import org.okapi.web.dtos.dashboards.QueryConfig;
import org.okapi.web.dtos.dashboards.vars.VarsContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@ActiveProfiles("test")
class QueryServiceIT {

  private static MockWebServer mockWebServer;

  @Autowired private MetricsQueryService metricsQueryService;
  @Autowired private LogsQueryService logsQueryService;
  @Autowired private SpansQueryService spansQueryService;
  @Autowired private PromQlService promQlService;
  @Autowired private Gson gson;

  @MockitoBean private S3Client s3Client;

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    ensureServerStarted();
    registry.add("clusterEndpoint", () -> mockWebServer.url("").toString());
  }

  @BeforeAll
  static void startServer() {
    ensureServerStarted();
  }

  @BeforeEach
  void drainRequests() throws InterruptedException {
    if (mockWebServer == null) {
      return;
    }
    while (mockWebServer.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
      // drain any pending requests from prior tests
    }
  }

  @AfterAll
  static void stopServer() throws IOException {
    if (mockWebServer != null) {
      mockWebServer.close();
    }
  }

  @Test
  void queryMetrics_singleRequest_postsToIngester_andReturnsParsedResponse() throws Exception {
    var backendResponse =
        GetMetricsResponse.builder().metric("m1").tags(Map.of("env", "prod")).build();
    enqueueJson(backendResponse);

    var request =
        GetMetricsRequest.builder()
            .metric("metricA")
            .tags(Map.of("env", "prod"))
            .start(10)
            .end(20)
            .metricType(METRIC_TYPE.GAUGE)
            .build();

    var response = metricsQueryService.queryMetrics(request);

    assertEquals("m1", response.getMetric());
    assertEquals(Map.of("env", "prod"), response.getTags());

    var recorded = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded, "Expected a request to be sent to the mock server");
    assertEquals("/api/v1/metrics/query", recorded.getTarget());
    assertEquals("POST", recorded.getMethod());

    assertNotNull(recorded.getBody());
    var bodyJson =
        JsonParser.parseString(recorded.getBody().string(StandardCharsets.UTF_8)).getAsJsonObject();
    assertEquals("metricA", bodyJson.get("metric").getAsString());
    assertEquals(10, bodyJson.get("start").getAsLong());
    assertEquals(20, bodyJson.get("end").getAsLong());
    assertEquals("GAUGE", bodyJson.get("metricType").getAsString());
    assertEquals("prod", bodyJson.get("tags").getAsJsonObject().get("env").getAsString());
  }

  @Test
  void queryMetrics_batch_appliesVarsAndTimeConstraint_andAggregatesResults() throws Exception {

    enqueueJson(GetMetricsResponse.builder().metric("metricA").build());
    enqueueJson(GetMetricsResponse.builder().metric("metricB").build());

    var query1 =
        "{\"metric\":\"$__{metric1}\",\"tags\":{\"env\":\"$__{env}\"},\"start\":1,\"end\":2,\"metricType\":\"GAUGE\"}";
    var query2 =
        "{\"metric\":\"$__{metric2}\",\"tags\":{\"env\":\"$__{env}\"},\"start\":3,\"end\":4,\"metricType\":\"GAUGE\"}";

    var panel =
        MultiQueryRequest.builder()
            .timeConstraint(TimeConstraint.builder().start(100).end(200).build())
            .varsContext(
                new VarsContext(Map.of("metric1", "metricA", "metric2", "metricB", "env", "prod")))
            .queries(
                List.of(
                    QueryConfig.builder().query(query1).build(),
                    QueryConfig.builder().query(query2).build()))
            .build();

    GetMetricsBatchResponse response = metricsQueryService.queryMetrics(panel);

    assertEquals(2, response.getResponses().size());
    assertTrue(response.getResponses().stream().anyMatch(r -> "metricA".equals(r.getMetric())));
    assertTrue(response.getResponses().stream().anyMatch(r -> "metricB".equals(r.getMetric())));

    var req1 = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
    var req2 = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(req1, "Expected first request to be sent to the mock server");
    assertNotNull(req2, "Expected second request to be sent to the mock server");

    var sentMetrics =
        List.of(
            extractMetric(req1.getBody().string(StandardCharsets.UTF_8)),
            extractMetric(req2.getBody().string(StandardCharsets.UTF_8)));
    assertTrue(sentMetrics.containsAll(List.of("metricA", "metricB")));
  }

  @Test
  void queryMetrics_batch_badVarSyntax_throwsMalformedQueryException() {

    var query =
        "{\"metric\":\"${__metricA\",\"tags\":{},\"start\":1,\"end\":2,\"metricType\":\"GAUGE\"}";
    var panel =
        MultiQueryRequest.builder()
            .timeConstraint(TimeConstraint.builder().start(100).end(200).build())
            .varsContext(new VarsContext(Map.of("metricA", "metricA")))
            .queries(List.of(QueryConfig.builder().query(query).build()))
            .build();

    int beforeCount = mockWebServer.getRequestCount();
    assertThrows(JsonSyntaxException.class, () -> metricsQueryService.queryMetrics(panel));
    assertEquals(beforeCount, mockWebServer.getRequestCount());
  }

  @Test
  void queryExemplars_batch_hydratesVarsAndAppliesTimeConstraint() throws Exception {
    enqueueJson(GetExemplarsResponse.builder().metric("metricA").build());
    enqueueJson(GetExemplarsResponse.builder().metric("metricB").build());

    var panel =
        MultiQueryRequest.builder()
            .grammar(GRAMMAR.OKAPI_JSON)
            .timeConstraint(TimeConstraint.builder().start(100).end(200).build())
            .varsContext(new VarsContext(Map.of("metric", "metricA", "env", "prod")))
            .queries(
                List.of(
                    QueryConfig.builder()
                        .query(
                            "{\"metric\":\"$__{metric}\",\"tags\":{\"env\":\"$__{env}\"},\"start\":1,\"end\":2,\"metricType\":\"GAUGE\"}")
                        .build(),
                    QueryConfig.builder()
                        .query(
                            "{\"metric\":\"metricB\",\"tags\":{},\"start\":3,\"end\":4,\"metricType\":\"GAUGE\"}")
                        .build()))
            .build();

    GetExemplarsBatchResponse response = metricsQueryService.queryExemplars(panel);

    assertEquals(2, response.getResponses().size());
    var first = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
    var second = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(first);
    assertNotNull(second);
    assertEquals("/api/v1/metrics/exemplars", first.getTarget());
    assertEquals("/api/v1/metrics/exemplars", second.getTarget());
    for (var request : List.of(first, second)) {
      var body =
          JsonParser.parseString(request.getBody().string(StandardCharsets.UTF_8))
              .getAsJsonObject();
      assertEquals(
          100_000_000L, body.getAsJsonObject("timeFilter").get("tsStartNanos").getAsLong());
      assertEquals(200_000_000L, body.getAsJsonObject("timeFilter").get("tsEndNanos").getAsLong());
    }
  }

  @Test
  void queryExemplars_rejectsNonOkapiJsonGrammar() {
    int beforeCount = mockWebServer.getRequestCount();
    var panel =
        MultiQueryRequest.builder()
            .grammar(GRAMMAR.PROMQL)
            .timeConstraint(TimeConstraint.builder().start(100).end(200).build())
            .queries(List.of(QueryConfig.builder().query("{}").build()))
            .build();

    assertThrows(BadRequestException.class, () -> metricsQueryService.queryExemplars(panel));
    assertEquals(beforeCount, mockWebServer.getRequestCount());
  }

  @Test
  void queryLogsQl_postsToIngester_andReturnsParsedRows() throws Exception {
    enqueueJson(
        OkapiLogQlResponse.builder()
            .kind(OkapiLogQlResultKind.COUNT_BY)
            .rows(List.of(Map.of("service", "checkout-api", "count", 1.0)))
            .build());

    var request =
        OkapiLogQlRequest.builder()
            .tsStartNanos(10L)
            .tsEndNanos(20L)
            .logQl("service = 'checkout-api' | count by (service)")
            .build();

    var response = logsQueryService.queryLogsQl(request);

    assertEquals(OkapiLogQlResultKind.COUNT_BY, response.getKind());
    assertEquals("checkout-api", response.getRows().getFirst().get("service"));

    var recorded = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded, "Expected a request to be sent to the mock server");
    assertEquals("/api/v1/logs/query/logql", recorded.getTarget());
    assertEquals("POST", recorded.getMethod());

    var bodyJson =
        JsonParser.parseString(recorded.getBody().string(StandardCharsets.UTF_8)).getAsJsonObject();
    assertEquals(10, bodyJson.get("tsStartNanos").getAsLong());
    assertEquals(20, bodyJson.get("tsEndNanos").getAsLong());
    assertEquals(
        "service = 'checkout-api' | count by (service)", bodyJson.get("logQl").getAsString());
  }

  @Test
  void queryTraceQl_postsToIngester_andReturnsParsedRows() throws Exception {
    enqueueJson(
        OkapiTraceQlResponse.builder()
            .kind(OkapiTraceQlResultKind.COUNT_BY)
            .rows(
                List.of(
                    Map.of(
                        "service",
                        UnionValue.builder()
                            .type(UNION_TYPE.STRING)
                            .stringValue("checkout-api")
                            .build(),
                        "count",
                        UnionValue.builder().type(UNION_TYPE.LONG).longValue(1L).build())))
            .build());

    var request =
        OkapiTraceQlRequest.builder()
            .tsStartNanos(10L)
            .tsEndNanos(20L)
            .traceQl("service = 'checkout-api' | count by (service)")
            .build();

    var response = spansQueryService.queryTraceQl(request);

    assertEquals(OkapiTraceQlResultKind.COUNT_BY, response.getKind());
    assertEquals("checkout-api", response.getRows().getFirst().get("service").getStringValue());
    assertEquals(UNION_TYPE.LONG, response.getRows().getFirst().get("count").getType());
    assertEquals(1L, response.getRows().getFirst().get("count").getLongValue());

    var recorded = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded, "Expected a request to be sent to the mock server");
    assertEquals("/api/v1/spans/query/traceql", recorded.getTarget());
    assertEquals("POST", recorded.getMethod());

    var bodyJson =
        JsonParser.parseString(recorded.getBody().string(StandardCharsets.UTF_8)).getAsJsonObject();
    assertEquals(10, bodyJson.get("tsStartNanos").getAsLong());
    assertEquals(20, bodyJson.get("tsEndNanos").getAsLong());
    assertEquals(
        "service = 'checkout-api' | count by (service)", bodyJson.get("traceQl").getAsString());
  }

  @Test
  void queryPromQlRangePost_forwardsToIngester_andReturnsRawJson() throws Exception {
    var rawJson =
        "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[{\"metric\":{\"__name__\":\"up\"},\"values\":[[1,\"2\"]]}]}}";
    enqueueRawJson(rawJson);

    var response = promQlService.queryPromQlRangePost("up", "1", "2", "1", "5s");

    assertEquals(rawJson, response);

    var recorded = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded, "Expected a request to be sent to the mock server");
    assertEquals("/api/v1/query_range", recorded.getTarget());
    assertEquals("POST", recorded.getMethod());

    var body = recorded.getBody().string(StandardCharsets.UTF_8);
    assertTrue(body.contains("query=up"));
    assertTrue(body.contains("start=1"));
    assertTrue(body.contains("end=2"));
    assertTrue(body.contains("step=1"));
    assertTrue(body.contains("timeout=5s"));
  }

  @Test
  void queryPromQlMetadata_forwardsToIngester_andReturnsRawJson() throws Exception {
    var rawJson =
        "{\"status\":\"success\",\"data\":{\"up\":[{\"type\":\"gauge\",\"help\":\"Up\",\"unit\":\"\"}]}}";
    enqueueRawJson(rawJson);

    var response = promQlService.queryPromQlMetadata("up", 10);

    assertEquals(rawJson, response);

    var recorded = mockWebServer.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded, "Expected a request to be sent to the mock server");
    assertEquals("/api/v1/metadata?metric=up&limit=10", recorded.getTarget());
    assertEquals("GET", recorded.getMethod());
  }

  private static void ensureServerStarted() {
    if (mockWebServer == null) {
      mockWebServer = new MockWebServer();
      try {
        mockWebServer.start();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void enqueueJson(Object body) {
    mockWebServer.enqueue(
        new MockResponse.Builder()
            .code(200)
            .body(gson.toJson(body))
            .addHeader("Content-Type", "application/json")
            .build());
  }

  private void enqueueRawJson(String body) {
    mockWebServer.enqueue(
        new MockResponse.Builder()
            .code(200)
            .body(body)
            .addHeader("Content-Type", "application/json")
            .build());
  }

  private String extractMetric(String body) {
    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
    assertEquals(100, json.get("start").getAsLong());
    assertEquals(200, json.get("end").getAsLong());
    assertEquals("prod", json.get("tags").getAsJsonObject().get("env").getAsString());
    return json.get("metric").getAsString();
  }
}
