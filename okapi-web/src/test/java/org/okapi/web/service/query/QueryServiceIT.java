/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.auth0.jwt.algorithms.Algorithm;
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
import org.okapi.rest.metrics.query.GetMetricsBatchResponse;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetMetricsResponse;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.auth.TokenManager;
import org.okapi.web.dtos.constraints.TimeConstraint;
import org.okapi.web.dtos.dashboards.MultiQueryPanelWDto;
import org.okapi.web.dtos.dashboards.PanelQueryConfigWDto;
import org.okapi.web.dtos.dashboards.vars.VarsContext;
import org.okapi.web.secrets.SecretsManager;
import org.okapi.web.secrets.SecretsManagerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
  @Autowired private Gson gson;

  @MockitoBean private TokenManager tokenManager;
  @MockitoBean private AccessManager accessManager;
  @MockitoBean private SecretsManagerImpl secretsManagerImpl;
  @MockitoBean private S3Client s3Client;
  @MockitoBean private Algorithm algorithm;

  @TestConfiguration
  static class TestSecretsConfig {
    @Bean
    @Primary
    SecretsManager secretsManager() {
      return new SecretsManager() {
        @Override
        public String getHmacKey() {
          return "test-hmac-key";
        }

        @Override
        public String getApiKey() {
          return "test-api-key";
        }
      };
    }
  }

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
    when(tokenManager.getUserId("token")).thenReturn("user-1");
    when(tokenManager.getOrgId("token")).thenReturn("org-1");

    var backendResponse =
        GetMetricsResponse.builder()
            .metric("m1")
            .tags(Map.of("env", "prod"))
            .build();
    enqueueJson(backendResponse);

    var request =
        GetMetricsRequest.builder()
            .metric("metricA")
            .tags(Map.of("env", "prod"))
            .start(10)
            .end(20)
            .metricType(METRIC_TYPE.GAUGE)
            .build();

    var response = metricsQueryService.queryMetrics("token", request);

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
  void queryMetrics_singleRequest_accessDenied_skipsBackend() {
    when(tokenManager.getUserId("token")).thenReturn("user-1");
    when(tokenManager.getOrgId("token")).thenReturn("org-1");
    doThrow(new RuntimeException("forbidden")).when(accessManager).checkUserIsOrgMember(any());

    var request =
        GetMetricsRequest.builder()
            .metric("metricA")
            .tags(Map.of("env", "prod"))
            .start(10)
            .end(20)
            .metricType(METRIC_TYPE.GAUGE)
            .build();

    int beforeCount = mockWebServer.getRequestCount();
    assertThrows(RuntimeException.class, () -> metricsQueryService.queryMetrics("token", request));
    assertEquals(beforeCount, mockWebServer.getRequestCount());
  }

  @Test
  void queryMetrics_batch_appliesVarsAndTimeConstraint_andAggregatesResults() throws Exception {
    when(tokenManager.getUserId("token")).thenReturn("user-1");
    when(tokenManager.getOrgId("token")).thenReturn("org-1");

    enqueueJson(GetMetricsResponse.builder().metric("metricA").build());
    enqueueJson(GetMetricsResponse.builder().metric("metricB").build());

    var query1 =
        "{\"metric\":\"$__{metric1}\",\"tags\":{\"env\":\"$__{env}\"},\"start\":1,\"end\":2,\"metricType\":\"GAUGE\"}";
    var query2 =
        "{\"metric\":\"$__{metric2}\",\"tags\":{\"env\":\"$__{env}\"},\"start\":3,\"end\":4,\"metricType\":\"GAUGE\"}";

    var panel =
        MultiQueryPanelWDto.builder()
            .timeConstraint(TimeConstraint.builder().start(100).end(200).build())
            .varsContext(
                new VarsContext(
                    Map.of("metric1", "metricA", "metric2", "metricB", "env", "prod")))
            .queries(
                List.of(
                    PanelQueryConfigWDto.builder().query(query1).build(),
                    PanelQueryConfigWDto.builder().query(query2).build()))
            .build();

    GetMetricsBatchResponse response = metricsQueryService.queryMetrics("token", panel);

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
    when(tokenManager.getUserId("token")).thenReturn("user-1");
    when(tokenManager.getOrgId("token")).thenReturn("org-1");

    var query =
        "{\"metric\":\"${__metricA\",\"tags\":{},\"start\":1,\"end\":2,\"metricType\":\"GAUGE\"}";
    var panel =
        MultiQueryPanelWDto.builder()
            .timeConstraint(TimeConstraint.builder().start(100).end(200).build())
            .varsContext(new VarsContext(Map.of("metricA", "metricA")))
            .queries(List.of(PanelQueryConfigWDto.builder().query(query).build()))
            .build();

    int beforeCount = mockWebServer.getRequestCount();
    assertThrows(JsonSyntaxException.class, () -> metricsQueryService.queryMetrics("token", panel));
    assertEquals(beforeCount, mockWebServer.getRequestCount());
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

  private String extractMetric(String body) {
    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
    assertEquals(100, json.get("start").getAsLong());
    assertEquals(200, json.get("end").getAsLong());
    assertEquals("prod", json.get("tags").getAsJsonObject().get("env").getAsString());
    return json.get("metric").getAsString();
  }
}
