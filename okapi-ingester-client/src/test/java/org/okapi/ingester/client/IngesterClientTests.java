/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ingester.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.rest.common.UNION_TYPE;
import org.okapi.rest.common.UnionValue;
import org.okapi.rest.logs.OkapiLogQlRequest;
import org.okapi.rest.logs.OkapiLogQlResponse;
import org.okapi.rest.logs.OkapiLogQlResultKind;
import org.okapi.rest.traces.OkapiTraceQlRequest;
import org.okapi.rest.traces.OkapiTraceQlResponse;
import org.okapi.rest.traces.OkapiTraceQlResultKind;

class IngesterClientTests {
  private final Gson gson = new Gson();
  private MockWebServer server;
  private IngesterClient ingesterClient;
  private PromQlQueryClient promQlQueryClient;

  @BeforeEach
  void setup() throws IOException {
    server = new MockWebServer();
    server.start();
    ingesterClient =
        new IngesterClient(
            server.url("").toString(), new OkHttpClient(), new ProxyResponseTranslator());
    promQlQueryClient =
        new PromQlQueryClient(
            server.url("").toString(), new OkHttpClient(), new ProxyResponseTranslator());
  }

  @AfterEach
  void tearDown() throws IOException {
    server.close();
  }

  @Test
  void queryLogsQlPostsToLogQlEndpoint() throws Exception {
    var expectedResponse =
        OkapiLogQlResponse.builder()
            .kind(OkapiLogQlResultKind.COUNT_BY)
            .rows(List.of(Map.of("service", "checkout-api", "count", 1.0)))
            .build();
    server.enqueue(
        new MockResponse.Builder()
            .code(200)
            .body(gson.toJson(expectedResponse))
            .addHeader("Content-Type", "application/json")
            .build());

    var request =
        OkapiLogQlRequest.builder()
            .tsStartNanos(10L)
            .tsEndNanos(20L)
            .logQl("service = 'checkout-api' | count by (service)")
            .build();

    var response = ingesterClient.queryLogsQl(request);

    assertEquals(OkapiLogQlResultKind.COUNT_BY, response.getKind());
    assertEquals("checkout-api", response.getRows().getFirst().get("service"));

    var recorded = server.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded);
    assertEquals("/api/v1/logs/query/logql", recorded.getTarget());
    assertEquals("POST", recorded.getMethod());
    var body = recorded.getBody().string(StandardCharsets.UTF_8);
    assertEquals(
        "service = 'checkout-api' | count by (service)",
        gson.fromJson(body, OkapiLogQlRequest.class).getLogQl());
  }

  @Test
  void queryTraceQlPostsToTraceQlEndpoint() throws Exception {
    var expectedResponse =
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
            .build();
    server.enqueue(
        new MockResponse.Builder()
            .code(200)
            .body(gson.toJson(expectedResponse))
            .addHeader("Content-Type", "application/json")
            .build());

    var request =
        OkapiTraceQlRequest.builder()
            .tsStartNanos(10L)
            .tsEndNanos(20L)
            .traceQl("service = 'checkout-api' | count by (service)")
            .build();

    var response = ingesterClient.queryTraceQl(request);

    assertEquals(OkapiTraceQlResultKind.COUNT_BY, response.getKind());
    assertEquals("checkout-api", response.getRows().getFirst().get("service").getStringValue());
    assertEquals(UNION_TYPE.LONG, response.getRows().getFirst().get("count").getType());
    assertEquals(1L, response.getRows().getFirst().get("count").getLongValue());

    var recorded = server.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded);
    assertEquals("/api/v1/spans/query/traceql", recorded.getTarget());
    assertEquals("POST", recorded.getMethod());
    var body = recorded.getBody().string(StandardCharsets.UTF_8);
    assertEquals(
        "service = 'checkout-api' | count by (service)",
        gson.fromJson(body, OkapiTraceQlRequest.class).getTraceQl());
  }

  @Test
  void queryPromQlInstantForwardsGetAndReturnsRawJson() throws Exception {
    var rawJson =
        "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{\"__name__\":\"up\"},\"value\":[1,\"2\"]}]}}";
    enqueueRawJson(rawJson);

    var response = promQlQueryClient.queryInstant("up{job=\"api\"}", "123", "5s");

    assertEquals(rawJson, response);

    var recorded = server.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded);
    assertEquals("GET", recorded.getMethod());
    assertEquals(
        "/api/v1/query?query=up%7Bjob%3D%22api%22%7D&time=123&timeout=5s", recorded.getTarget());
  }

  @Test
  void queryPromQlRangeForwardsPostForm() throws Exception {
    enqueueRawJson("{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}");

    promQlQueryClient.queryRangePost("rate(http_requests_total[5m])", "10", "20", "5", null);

    var recorded = server.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded);
    assertEquals("POST", recorded.getMethod());
    assertEquals("/api/v1/query_range", recorded.getTarget());
    assertEquals("application/x-www-form-urlencoded", recorded.getHeaders().get("Content-Type"));

    var body = recorded.getBody().string(StandardCharsets.UTF_8);
    assertTrue(body.contains("query=rate%28http_requests_total%5B5m%5D%29"));
    assertTrue(body.contains("start=10"));
    assertTrue(body.contains("end=20"));
    assertTrue(body.contains("step=5"));
  }

  @Test
  void queryPromQlLabelsForwardsRepeatedMatchers() throws Exception {
    enqueueRawJson("{\"status\":\"success\",\"data\":[\"job\"]}");

    promQlQueryClient.listLabels("10", "20", List.of("up", "{job=\"api\"}"));

    var recorded = server.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded);
    assertEquals("GET", recorded.getMethod());
    assertEquals(
        "/api/v1/labels?start=10&end=20&match%5B%5D=up&match%5B%5D=%7Bjob%3D%22api%22%7D",
        recorded.getTarget());
  }

  @Test
  void queryPromQlLabelValuesEscapesLabelPathSegment() throws Exception {
    enqueueRawJson("{\"status\":\"success\",\"data\":[\"api\"]}");

    promQlQueryClient.listLabelValues("service.name", "10", "20", null);

    var recorded = server.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded);
    assertEquals("GET", recorded.getMethod());
    assertEquals("/api/v1/label/service.name/values?start=10&end=20", recorded.getTarget());
  }

  @Test
  void queryPromQlMetadataForwardsOptionalParameters() throws Exception {
    enqueueRawJson(
        "{\"status\":\"success\",\"data\":{\"up\":[{\"type\":\"gauge\",\"help\":\"Up\",\"unit\":\"\"}]}}");

    promQlQueryClient.metadata("up", 10);

    var recorded = server.takeRequest(2, TimeUnit.SECONDS);
    assertNotNull(recorded);
    assertEquals("GET", recorded.getMethod());
    assertEquals("/api/v1/metadata?metric=up&limit=10", recorded.getTarget());
  }

  private void enqueueRawJson(String rawJson) {
    server.enqueue(
        new MockResponse.Builder()
            .code(200)
            .body(rawJson)
            .addHeader("Content-Type", "application/json")
            .build());
  }
}
