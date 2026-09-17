/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.it;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.clickhouse.client.api.Client;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.logs.TestApplication;
import org.okapi.metrics.ch.ChConstants;
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
public class PrometheusCompatibleIngestIT {

  @TempDir static Path tempDir;
  private static Path chWalRoot;

  @LocalServerPort private int port;
  @Autowired private RestClient restClient;
  @Autowired private Client chClient;

  private final Gson gson = new Gson();
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
    chClient.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_GAUGES);
    chClient.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_HISTOS);
    chClient.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_SUM);
    chClient.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_METRIC_EVENTS_META);
  }

  @Test
  void prometheusDialectRewritesMetricAndTags() {
    String svc = "svc-prom-" + UUID.randomUUID();
    String metricWithDots = "kafka.request.time.avg";
    String tagWithDots = "service.instance.id";
    String rewrittenMetric = "kafka_request_time_avg";
    String rewrittenTag = "service_instance_id";

    Map<String, String> tags = Map.of(tagWithDots, "i1", "env", "dev");
    long nowMs = System.currentTimeMillis();
    long ts = nowMs - 1_000;

    ExportMetricsServiceRequest req = buildGaugeRequest(svc, metricWithDots, tags, ts, 1.0);

    restClient
        .post()
        .uri(baseUrl + "/prometheus/v1/metrics")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(req.toByteArray())
        .retrieve()
        .toBodilessEntity();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              JsonObject namesResp = getPromJson("/api/v1/label/__name__/values");
              assertEquals("success", namesResp.get("status").getAsString());
              JsonArray names = namesResp.getAsJsonArray("data");
              assertTrue(
                  names.toString().contains(rewrittenMetric),
                  "Expected rewritten metric name in __name__ values");

              JsonObject labelsResp = getPromJson("/api/v1/labels");
              assertEquals("success", labelsResp.get("status").getAsString());
              JsonArray labels = labelsResp.getAsJsonArray("data");
              assertTrue(
                  labels.toString().contains(rewrittenTag),
                  "Expected rewritten tag key in labels list");
            });
  }

  private ExportMetricsServiceRequest buildGaugeRequest(
      String resourceName, String metricName, Map<String, String> tags, long ts, double value) {
    var point =
        NumberDataPoint.newBuilder()
            .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(ts))
            .setAsDouble(value)
            .addAllAttributes(toKvList(tags))
            .build();
    var gauge = Gauge.newBuilder().addDataPoints(point).build();
    Metric metric = Metric.newBuilder().setName(metricName).setGauge(gauge).build();
    var scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
    var resource =
        Resource.newBuilder()
            .addAttributes(
                KeyValue.newBuilder()
                    .setKey("service.name")
                    .setValue(AnyValue.newBuilder().setStringValue(resourceName).build())
                    .build())
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private List<KeyValue> toKvList(Map<String, String> tags) {
    return tags.entrySet().stream()
        .map(
            e ->
                KeyValue.newBuilder()
                    .setKey(e.getKey())
                    .setValue(AnyValue.newBuilder().setStringValue(e.getValue()).build())
                    .build())
        .toList();
  }

  private JsonObject getPromJson(String path) {
    String json = restClient.get().uri(baseUrl + path).retrieve().body(String.class);
    assertNotNull(json);
    return gson.fromJson(json, JsonObject.class);
  }
}
