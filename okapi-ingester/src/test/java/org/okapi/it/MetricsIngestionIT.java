/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.it;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.clickhouse.client.api.Client;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Exemplar;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Histogram;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.resource.v1.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.bytes.OkapiBytes;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.logs.TestApplication;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.rest.TimeInterval;
import org.okapi.rest.metrics.exemplar.GetExemplarsRequest;
import org.okapi.rest.metrics.exemplar.GetExemplarsResponse;
import org.okapi.rest.metrics.query.GaugeQueryConfig;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetMetricsResponse;
import org.okapi.rest.metrics.query.GetSumsQueryConfig;
import org.okapi.rest.metrics.query.HistoQueryConfig;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.rest.search.GetMetricNameHints;
import org.okapi.rest.search.GetMetricsHintsResponse;
import org.okapi.rest.search.GetTagHintsRequest;
import org.okapi.rest.search.GetTagValueHintsRequest;
import org.okapi.rest.search.MetricEventFilter;
import org.okapi.rest.traces.TimestampFilter;
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
@ActiveProfiles({Profiles.PROFILE_CH})
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
public class MetricsIngestionIT {

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
    chClient.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_GAUGES);
    chClient.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_HISTOS);
    chClient.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_SUM);
    chClient.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_EXEMPLAR);
    chClient.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_METRIC_EVENTS_META);
  }

  @Test
  void ingestAndQueryGaugeSumHisto() {
    String svc = "svc-it-" + UUID.randomUUID();
    String cpuUsageIt = "cpu_usage_it";
    String requestCountIt = "req_count_it";
    String latencyMsIt = "latency_ms_it";
    String cpuUsageItOther = "cpu_usage_it_other";

    Map<String, String> tagsDevEnv = Map.of("env", "dev");
    Map<String, String> tagsProdEnv = Map.of("env", "prod");
    String otherSvc = "svc-it-other-" + UUID.randomUUID();
    Map<String, String> tagsOtherSvc = Map.of("env", "dev");

    long nowMs = System.currentTimeMillis();
    long t1 = nowMs - 60_000;
    long t2 = nowMs - 5_000;

    var gaugePayload =
        buildOtelGauge(
            svc,
            cpuUsageIt,
            List.of(numberPointAt(t1, 0.3, tagsDevEnv), numberPointAt(t2, 0.8, tagsDevEnv)));

    var sumPayload =
        buildOtelSum(
            svc,
            requestCountIt,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(sumPoint(t1, t2, 3.0, tagsDevEnv)));

    var histoPayload =
        buildOtelHistogram(
            svc,
            latencyMsIt,
            List.of(
                histoPoint(
                    t1, t2, List.of(10.0, 20.0, 30.0), List.of(3L, 4L, 5L, 5L), tagsDevEnv)));

    var gaugePayloadOtherTag =
        buildOtelGauge(svc, cpuUsageIt, List.of(numberPointAt(t1 + 500, 0.6, tagsProdEnv)));
    var gaugePayloadOtherSvc =
        buildOtelGauge(
            otherSvc, cpuUsageItOther, List.of(numberPointAt(t1 + 1000, 0.9, tagsOtherSvc)));

    postOtel(gaugePayload);
    postOtel(sumPayload);
    postOtel(histoPayload);
    postOtel(gaugePayloadOtherTag);
    postOtel(gaugePayloadOtherSvc);

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // gauge: basic ingestion
              var gaugeReq =
                  GetMetricsRequest.builder()
                      .metric(cpuUsageIt)
                      .start(nowMs - 70_000)
                      .end(nowMs + 5_000)
                      .metricType(METRIC_TYPE.GAUGE)
                      .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
                      .build();
              GetMetricsResponse gaugeResp = postQuery(gaugeReq);
              assertNotNull(gaugeResp.getGaugeResponse());
              assertEquals(2, gaugeResp.getGaugeResponse().getSeries().size());
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var sumReq =
                  GetMetricsRequest.builder()
                      .metric(requestCountIt)
                      .start(nowMs - 70_000)
                      .end(nowMs + 5_000)
                      .metricType(METRIC_TYPE.SUM)
                      .sumsQueryConfig(
                          GetSumsQueryConfig.builder()
                              .temporality(GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE)
                              .build())
                      .build();
              GetMetricsResponse sumResp = postQuery(sumReq);
              assertNotNull(sumResp.getSumsResponse());
              assertEquals(1, sumResp.getSumsResponse().getSums().size());
              assertEquals(3L, sumResp.getSumsResponse().getSums().get(0).getCount());
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var histoReq =
                  GetMetricsRequest.builder()
                      .metric(latencyMsIt)
                      .start(nowMs - 120_000)
                      .end(nowMs)
                      .metricType(METRIC_TYPE.HISTO)
                      .histoQueryConfig(
                          HistoQueryConfig.builder()
                              .temporality(HistoQueryConfig.TEMPORALITY.MERGED)
                              .build())
                      .build();
              GetMetricsResponse histoResp = postQuery(histoReq);
              assertNotNull(histoResp.getHistogramResponse());
              assertNotNull(histoResp.getHistogramResponse().getSeries());
              assertEquals(1, histoResp.getHistogramResponse().getSeries().size());
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // gauge: metric isolation
              var gaugeReq =
                  GetMetricsRequest.builder()
                      .metric(cpuUsageItOther)
                      .start(nowMs - 70_000)
                      .end(nowMs + 5_000)
                      .metricType(METRIC_TYPE.GAUGE)
                      .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
                      .build();
              GetMetricsResponse gaugeResp = postQuery(gaugeReq);
              assertNotNull(gaugeResp.getGaugeResponse());
              assertEquals(1, gaugeResp.getGaugeResponse().getSeries().size());
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var gaugeReq =
                  GetMetricsRequest.builder()
                      .metric(cpuUsageIt)
                      .start(t1 + 10_000)
                      .end(t1 + 20_000)
                      .metricType(METRIC_TYPE.GAUGE)
                      .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
                      .build();
              GetMetricsResponse gaugeResp = postQuery(gaugeReq);
              assertNull(gaugeResp.getGaugeResponse());
            });
  }

  @Test
  void exemplarCorpusIsPersistedAndQueryableAcrossMetricTypes() {
    String gaugeMetric = "exemplar_corpus_gauge";
    String sumMetric = "exemplar_corpus_sum";
    String histoMetric = "exemplar_corpus_histo";
    Map<String, String> labels = Map.of("env", "exemplar-test");
    long nowMs = System.currentTimeMillis();
    long startMs = nowMs - 60_000;
    long endMs = nowMs + 5_000;

    postOtel(
        buildOtelGauge(
            "exemplar-corpus",
            gaugeMetric,
            List.of(
                numberPointWithExemplar(
                    startMs, 1.0, labels, startMs, "0000000000000001", "0000000000000001"))));
    postOtel(
        buildOtelSum(
            "exemplar-corpus",
            sumMetric,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(
                sumPointWithExemplar(
                    startMs,
                    endMs,
                    2.0,
                    labels,
                    endMs - 1_000,
                    "0000000000000002",
                    "0000000000000002"))));
    postOtel(
        buildOtelHistogram(
            "exemplar-corpus",
            histoMetric,
            List.of(
                histoPointWithExemplar(
                    startMs,
                    endMs,
                    List.of(10.0, 20.0),
                    List.of(1L, 2L, 1L),
                    labels,
                    endMs - 2_000,
                    "0000000000000003",
                    "0000000000000003"))));

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var count =
                  chClient
                      .queryAll("SELECT count() AS exemplar_count FROM " + ChConstants.TBL_EXEMPLAR)
                      .getFirst()
                      .getLong("exemplar_count");
              assertEquals(3L, count);
            });

    assertExemplarQuery(gaugeMetric, labels, startMs, endMs, "0000000000000001");
    assertExemplarQuery(sumMetric, labels, startMs, endMs, "0000000000000002");
    assertExemplarQuery(histoMetric, labels, startMs, endMs, "0000000000000003");

    var outsideWindow = queryExemplars(gaugeMetric, labels, endMs, endMs + 10_000);
    assertTrue(outsideWindow.getExemplars().isEmpty());
  }

  @Test
  void hintsApiReturnsCompletions() {
    String svc = "svc-hints-it-" + UUID.randomUUID();
    String gaugeMetric = "cpu_hint_it";
    String sumMetric = "req_hint_it";
    String otherSvc = "svc-hints-other-" + UUID.randomUUID();

    Map<String, String> tags = Map.of("env", "dev", "region", "us-east");
    Map<String, String> tagsOtherSvc = Map.of("env", "dev", "region", "eu-west");
    long nowMs = System.currentTimeMillis();
    long t1 = nowMs - 60_000;
    long t2 = nowMs - 5_000;

    postOtel(
        buildOtelGauge(
            svc, gaugeMetric, List.of(numberPointAt(t1, 0.2, tags), numberPointAt(t2, 0.4, tags))));
    postOtel(
        buildOtelSum(
            svc,
            sumMetric,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(sumPoint(t1, t2, 2.0, tags))));
    postOtel(
        buildOtelGauge(otherSvc, "cpu_hint_other", List.of(numberPointAt(t1, 0.5, tagsOtherSvc))));

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // metric hints: positive match
              var hintsReq =
                  new GetMetricNameHints(
                      "cpu_",
                      new TimeInterval(nowMs - 70_000, nowMs + 5_000),
                      new MetricEventFilter(METRIC_TYPE.GAUGE));
              GetMetricsHintsResponse hintsResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/name/hints")
                      .body(hintsReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(hintsResp);
              assertTrue(hintsResp.getMetricHints().contains(gaugeMetric));

              var nullFilterReq =
                  new GetMetricNameHints(
                      "cpu_", new TimeInterval(nowMs - 70_000, nowMs + 5_000), null);
              GetMetricsHintsResponse nullFilterResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/name/hints")
                      .body(nullFilterReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(nullFilterResp);
              assertTrue(nullFilterResp.getMetricHints().contains(gaugeMetric));
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // tag hints: positive match
              var tagHintsReq =
                  new GetTagHintsRequest(
                      gaugeMetric,
                      Map.of("env", "dev"),
                      "re",
                      new TimeInterval(nowMs - 70_000, nowMs + 5_000),
                      new MetricEventFilter(METRIC_TYPE.GAUGE));
              GetMetricsHintsResponse tagResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/tag/hints")
                      .body(tagHintsReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(tagResp);
              assertTrue(tagResp.getTagHints().contains("region"));

              var nullFilterReq =
                  new GetTagHintsRequest(
                      gaugeMetric,
                      Map.of("env", "dev"),
                      "re",
                      new TimeInterval(nowMs - 70_000, nowMs + 5_000),
                      null);
              GetMetricsHintsResponse nullFilterResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/tag/hints")
                      .body(nullFilterReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(nullFilterResp);
              assertTrue(nullFilterResp.getTagHints().contains("region"));
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // tag value hints: positive match
              var tagValReq =
                  new GetTagValueHintsRequest(
                      gaugeMetric,
                      Map.of("env", "dev"),
                      "region",
                      new TimeInterval(nowMs - 70_000, nowMs + 5_000),
                      new MetricEventFilter(METRIC_TYPE.GAUGE));
              GetMetricsHintsResponse tagValResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/tag-value/hints")
                      .body(tagValReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(tagValResp);
              assertNotNull(tagValResp.getTagValueHints());
              assertTrue(tagValResp.getTagValueHints().getCandidates().contains("us-east"));

              var nullFilterReq =
                  new GetTagValueHintsRequest(
                      gaugeMetric,
                      Map.of("env", "dev"),
                      "region",
                      new TimeInterval(nowMs - 70_000, nowMs + 5_000),
                      null);
              GetMetricsHintsResponse nullFilterResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/tag-value/hints")
                      .body(nullFilterReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(nullFilterResp);
              assertNotNull(nullFilterResp.getTagValueHints());
              assertTrue(nullFilterResp.getTagValueHints().getCandidates().contains("us-east"));
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // metric hints: prefix miss
              var hintsReq =
                  new GetMetricNameHints(
                      "does_not_exist",
                      new TimeInterval(nowMs - 70_000, nowMs + 5_000),
                      new MetricEventFilter(METRIC_TYPE.GAUGE));
              GetMetricsHintsResponse hintsResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/name/hints")
                      .body(hintsReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(hintsResp);
              assertEquals(0, hintsResp.getMetricHints().size());
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // tag hints: otherTags mismatch
              var tagHintsReq =
                  new GetTagHintsRequest(
                      gaugeMetric,
                      Map.of("env", "prod"),
                      "re",
                      new TimeInterval(nowMs - 70_000, nowMs + 5_000),
                      new MetricEventFilter(METRIC_TYPE.GAUGE));
              GetMetricsHintsResponse tagResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/tag/hints")
                      .body(tagHintsReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(tagResp);
              assertEquals(0, tagResp.getTagHints().size());
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // tag value hints: missing tag
              var tagValReq =
                  new GetTagValueHintsRequest(
                      gaugeMetric,
                      Map.of("env", "dev"),
                      "missing_tag",
                      new TimeInterval(nowMs - 70_000, nowMs + 5_000),
                      new MetricEventFilter(METRIC_TYPE.GAUGE));
              GetMetricsHintsResponse tagValResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/tag-value/hints")
                      .body(tagValReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(tagValResp);
              assertNotNull(tagValResp.getTagValueHints());
              assertEquals(0, tagValResp.getTagValueHints().getCandidates().size());
            });

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              // hints: no service scoping
              var hintsReq =
                  new GetMetricNameHints(
                      "cpu_hint_other",
                      new TimeInterval(nowMs - 70_000, nowMs + 5_000),
                      new MetricEventFilter(METRIC_TYPE.GAUGE));
              GetMetricsHintsResponse hintsResp =
                  restClient
                      .post()
                      .uri(baseUrl + "/api/v1/metrics/name/hints")
                      .body(hintsReq)
                      .retrieve()
                      .body(GetMetricsHintsResponse.class);
              assertNotNull(hintsResp);
              assertTrue(hintsResp.getMetricHints().contains("cpu_hint_other"));
            });
  }

  private void postOtel(ExportMetricsServiceRequest request) {
    restClient
        .post()
        .uri(baseUrl + "/v1/metrics")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(request.toByteArray())
        .retrieve()
        .toBodilessEntity();
  }

  private GetMetricsResponse postQuery(GetMetricsRequest request) {
    return restClient
        .post()
        .uri(baseUrl + "/api/v1/metrics/query")
        .body(request)
        .retrieve()
        .body(GetMetricsResponse.class);
  }

  private void assertExemplarQuery(
      String metric, Map<String, String> labels, long startMs, long endMs, String traceId) {
    var response = queryExemplars(metric, labels, startMs, endMs);
    assertEquals(1, response.getExemplars().size());
    assertEquals(metric, response.getExemplars().getFirst().getMetric());
    assertEquals(labels, response.getExemplars().getFirst().getTags());
    assertEquals(traceId, response.getExemplars().getFirst().getTraceId());
    assertEquals(traceId, response.getExemplars().getFirst().getSpanId());
  }

  private GetExemplarsResponse queryExemplars(
      String metric, Map<String, String> labels, long startMs, long endMs) {
    return restClient
        .post()
        .uri(baseUrl + "/api/v1/metrics/exemplars")
        .body(
            GetExemplarsRequest.builder()
                .metric(metric)
                .labels(labels)
                .timeFilter(
                    TimestampFilter.builder()
                        .tsStartNanos(TimeUnit.MILLISECONDS.toNanos(startMs))
                        .tsEndNanos(TimeUnit.MILLISECONDS.toNanos(endMs))
                        .build())
                .build())
        .retrieve()
        .body(GetExemplarsResponse.class);
  }

  private ExportMetricsServiceRequest buildOtelGauge(
      String svc, String metricName, List<NumberDataPoint> points) {
    var gauge = Gauge.newBuilder().addAllDataPoints(points).build();
    return wrapMetric(
        svc, metricName, Metric.newBuilder().setName(metricName).setGauge(gauge).build());
  }

  private ExportMetricsServiceRequest buildOtelSum(
      String svc,
      String metricName,
      AggregationTemporality temporality,
      List<NumberDataPoint> points) {
    var sum =
        Sum.newBuilder()
            .setAggregationTemporality(temporality)
            .setIsMonotonic(false)
            .addAllDataPoints(points)
            .build();
    return wrapMetric(svc, metricName, Metric.newBuilder().setName(metricName).setSum(sum).build());
  }

  private ExportMetricsServiceRequest buildOtelHistogram(
      String svc, String metricName, List<HistogramDataPoint> points) {
    var histo =
        Histogram.newBuilder()
            .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA)
            .addAllDataPoints(points)
            .build();
    return wrapMetric(
        svc, metricName, Metric.newBuilder().setName(metricName).setHistogram(histo).build());
  }

  private ExportMetricsServiceRequest wrapMetric(String svc, String metricName, Metric metric) {
    var scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
    var resource =
        Resource.newBuilder().addAttributes(OtelShortHands.keyValue("service.name", svc)).build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private NumberDataPoint numberPointAt(long tsMillis, double value, Map<String, String> tags) {
    return NumberDataPoint.newBuilder()
        .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(tsMillis))
        .setAsDouble(value)
        .addAllAttributes(toKvList(tags))
        .build();
  }

  private NumberDataPoint sumPoint(
      long startMs, long endMs, double value, Map<String, String> tags) {
    return NumberDataPoint.newBuilder()
        .setStartTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(startMs))
        .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(endMs))
        .setAsDouble(value)
        .addAllAttributes(toKvList(tags))
        .build();
  }

  private NumberDataPoint numberPointWithExemplar(
      long tsMs,
      double value,
      Map<String, String> tags,
      long exemplarTsMs,
      String traceId,
      String spanId) {
    return numberPointAt(tsMs, value, tags).toBuilder()
        .addExemplars(exemplar(exemplarTsMs, value, traceId, spanId))
        .build();
  }

  private NumberDataPoint sumPointWithExemplar(
      long startMs,
      long endMs,
      double value,
      Map<String, String> tags,
      long exemplarTsMs,
      String traceId,
      String spanId) {
    return sumPoint(startMs, endMs, value, tags).toBuilder()
        .addExemplars(exemplar(exemplarTsMs, value, traceId, spanId))
        .build();
  }

  private HistogramDataPoint histoPoint(
      long startMs, long endMs, List<Double> bounds, List<Long> counts, Map<String, String> tags) {
    return HistogramDataPoint.newBuilder()
        .setStartTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(startMs))
        .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(endMs))
        .addAllExplicitBounds(bounds)
        .addAllBucketCounts(counts)
        .addAllAttributes(toKvList(tags))
        .build();
  }

  private HistogramDataPoint histoPointWithExemplar(
      long startMs,
      long endMs,
      List<Double> bounds,
      List<Long> counts,
      Map<String, String> tags,
      long exemplarTsMs,
      String traceId,
      String spanId) {
    return histoPoint(startMs, endMs, bounds, counts, tags).toBuilder()
        .addExemplars(exemplar(exemplarTsMs, 10.0, traceId, spanId))
        .build();
  }

  private Exemplar exemplar(long tsMs, double value, String traceId, String spanId) {
    return Exemplar.newBuilder()
        .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(tsMs))
        .setAsDouble(value)
        .setTraceId(ByteString.copyFrom(OkapiBytes.decodeHex(traceId)))
        .setSpanId(ByteString.copyFrom(OkapiBytes.decodeHex(spanId)))
        .build();
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
}
