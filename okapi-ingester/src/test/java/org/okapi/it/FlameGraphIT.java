/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.it;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.clickhouse.client.api.Client;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.chtest.ChTestOnlyUtils;
import org.okapi.corpus.FlameGraphTestCorpus;
import org.okapi.logs.TestApplication;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.otel.OtelAnyValueDecoder;
import org.okapi.rest.traces.*;
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
public class FlameGraphIT {

  @TempDir static Path tempDir;
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
  }

  @Test
  void ingestAndQueryFlameGraph() {
    byte[] traceId = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    var corpus = new FlameGraphTestCorpus(traceId, 0L);
    postOtel(corpus.buildRequest());

    var request =
        SpanQueryV2Request.builder()
            .timestampFilter(
                TimestampFilter.builder().tsStartNanos(400).tsEndNanos(4_000 * 1_000_0000L).build())
            .traceId(OtelAnyValueDecoder.bytesToHex(traceId))
            .build();

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              SpansFlameGraphResponse response = postFlameGraph(request);
              var orphanId = hexId("span-orphan");
              var rootId = hexId("span-root");
              var childId = hexId("span-child");
              var grandId = hexId("span-grand");
              assertNotNull(response);
              assertEquals(2, response.getRoots().size());
              assertEquals(orphanId, response.getRoots().get(0).getSpanId());
              assertEquals(rootId, response.getRoots().get(1).getSpanId());
              assertEquals(1, response.getRoots().get(1).getChildren().size());
              assertEquals(childId, response.getRoots().get(1).getChildren().get(0).getSpanId());
              assertEquals(1, response.getRoots().get(1).getChildren().get(0).getChildren().size());
              assertEquals(
                  grandId,
                  response.getRoots().get(1).getChildren().get(0).getChildren().get(0).getSpanId());
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

  private SpansFlameGraphResponse postFlameGraph(SpanQueryV2Request request) {
    return restClient
        .post()
        .uri(baseUrl + "/api/v1/spans/flamegraph")
        .body(request)
        .retrieve()
        .body(SpansFlameGraphResponse.class);
  }

  private static String hexId(String value) {
    return OtelAnyValueDecoder.bytesToHex(value.getBytes());
  }
}
