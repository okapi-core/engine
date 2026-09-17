/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Histogram;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.resource.v1.Resource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.exceptions.BadRequestException;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.rest.search.AnyMetricOrValueFilter;
import org.okapi.rest.search.LabelValueFilter;
import org.okapi.rest.search.LabelValuePatternFilter;
import org.okapi.rest.search.MetricPath;
import org.okapi.rest.search.SearchMetricsRequest;
import org.okapi.testmodules.guice.TestChMetricsModule;

public class ChSearchMetricsProcessorTest {

  @TempDir Path tempDir;

  private Injector injector;
  private ChSearchMetricsProcessor searchProcessor;
  private ChMetricsIngester ingester;
  private ChMetricsWalConsumerDriver driver;

  // Corpus: 5 distinct metric paths
  // P1: cpu.usage  {env=prod, region=us-east, host=web-01}
  // P2: cpu.usage  {env=prod, region=eu-west, host=web-02}
  // P3: cpu.usage  {env=dev,  region=us-east, host=db-01}
  // P4: cpu.idle   {env=prod, region=us-east, host=web-01}
  // P5: memory.usage {env=prod, region=us-east, host=web-01}

  private static final Map<String, String> TAGS_PROD_WEB_01 =
      Map.of("env", "prod", "region", "us-east", "host", "web-01");
  private static final Map<String, String> TAGS_PROD_WEB_02 =
      Map.of("env", "prod", "region", "eu-west", "host", "web-02");
  private static final Map<String, String> TAGS_DEV_DB_01 =
      Map.of("env", "dev", "region", "us-east", "host", "db-01");

  @BeforeEach
  void setup() throws Exception {
    injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    var client = injector.getInstance(Client.class);
    searchProcessor = injector.getInstance(ChSearchMetricsProcessor.class);

    CreateChTablesSpec.migrate(client);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_METRIC_EVENTS_META);

    ingester = injector.getInstance(ChMetricsIngester.class);
    driver = injector.getInstance(ChMetricsWalConsumerDriver.class);

    ingester.ingestOtelProtobuf(gauge("cpu.usage", TAGS_PROD_WEB_01));
    ingester.ingestOtelProtobuf(gauge("cpu.usage", TAGS_PROD_WEB_02));
    ingester.ingestOtelProtobuf(gauge("cpu.usage", TAGS_DEV_DB_01));
    ingester.ingestOtelProtobuf(gauge("cpu.idle", TAGS_PROD_WEB_01));
    ingester.ingestOtelProtobuf(gauge("memory.usage", TAGS_PROD_WEB_01));

    driver.onTick();
  }

  @Test
  void searchByExactMetricName() {
    var resp = search(req().metricName("cpu.usage"));
    assertEquals(3, resp.size());
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_02)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_DEV_DB_01)));
  }

  @Test
  void searchByMetricPattern() {
    var resp = search(req().metricNamePattern("cpu\\..*"));
    assertEquals(4, resp.size());
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_02)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_DEV_DB_01)));
    assertTrue(resp.contains(path("cpu.idle", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
  }

  @Test
  void searchByMetricPatternNoMatch() {
    var resp = search(req().metricNamePattern("disk\\..*"));
    assertEquals(0, resp.size());
  }

  @Test
  void searchByLabelValue() {
    var resp = search(req().valueFilters(List.of(labelFilter("env", "prod"))));
    assertEquals(4, resp.size());
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_02)));
    assertTrue(resp.contains(path("cpu.idle", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("memory.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
  }

  @Test
  void searchByLabelValueNoMatch() {
    var resp = search(req().valueFilters(List.of(labelFilter("env", "staging"))));
    assertEquals(0, resp.size());
  }

  @Test
  void searchByLabelValuePattern() {
    var resp = search(req().patternFilters(List.of(patternFilter("host", "web-.*"))));
    assertEquals(4, resp.size());
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_02)));
    assertTrue(resp.contains(path("cpu.idle", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("memory.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
  }

  @Test
  void searchByLabelValuePatternNoMatch() {
    var resp = search(req().patternFilters(List.of(patternFilter("host", "cache-.*"))));
    assertEquals(0, resp.size());
  }

  @Test
  void searchByMetricPatternAndLabelValue() {
    var resp =
        search(
            req().metricNamePattern("cpu\\..*").valueFilters(List.of(labelFilter("env", "prod"))));
    assertEquals(3, resp.size());
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_02)));
    assertTrue(resp.contains(path("cpu.idle", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
  }

  @Test
  void searchByMetricPatternAndLabelValuePattern() {
    var resp =
        search(
            req()
                .metricNamePattern("cpu\\..*")
                .patternFilters(List.of(patternFilter("host", "web-.*"))));
    assertEquals(3, resp.size());
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_02)));
    assertTrue(resp.contains(path("cpu.idle", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
  }

  @Test
  void searchNoFilters() {
    var resp = search(req());
    assertEquals(5, resp.size());
  }

  @Test
  void searchIncludesTemporalityForHistoAndSum() throws Exception {
    ingester.ingestOtelProtobuf(
        histogram(
            "latency.histo",
            TAGS_PROD_WEB_01,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA));
    ingester.ingestOtelProtobuf(
        sum(
            "requests.sum",
            TAGS_PROD_WEB_01,
            AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE));
    driver.onTick();

    var histoResp = search(req().metricName("latency.histo"));
    assertTrue(
        histoResp.contains(path("latency.histo", METRIC_TYPE.HISTO, "DELTA", TAGS_PROD_WEB_01)));

    var sumResp = search(req().metricName("requests.sum"));
    assertTrue(
        sumResp.contains(path("requests.sum", METRIC_TYPE.SUM, "CUMULATIVE", TAGS_PROD_WEB_01)));
  }

  // --- anyMetricOrValueFilter ---

  @Test
  void searchByAnyFilter_exactMetricName() {
    var resp = search(req().anyMetricOrValueFilter(anyFilter().value("cpu.usage").build()));
    assertEquals(3, resp.size());
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_02)));
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_DEV_DB_01)));
  }

  @Test
  void searchByAnyFilter_exactTagValue() {
    // "web-01" appears as host tag in P1, P4, P5
    var resp = search(req().anyMetricOrValueFilter(anyFilter().value("web-01").build()));
    assertEquals(3, resp.size());
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("cpu.idle", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
    assertTrue(resp.contains(path("memory.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
  }

  @Test
  void searchByAnyFilter_patternMetricName() {
    var resp = search(req().anyMetricOrValueFilter(anyFilter().pattern("memory\\..*").build()));
    assertEquals(1, resp.size());
    assertTrue(resp.contains(path("memory.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_01)));
  }

  @Test
  void searchByAnyFilter_patternTagValue() {
    // "eu-west" only appears in P2
    var resp = search(req().anyMetricOrValueFilter(anyFilter().pattern("eu-.*").build()));
    assertEquals(1, resp.size());
    assertTrue(resp.contains(path("cpu.usage", METRIC_TYPE.GAUGE, TAGS_PROD_WEB_02)));
  }

  @Test
  void searchByAnyFilter_noMatch() {
    var resp = search(req().anyMetricOrValueFilter(anyFilter().value("nonexistent").build()));
    assertEquals(0, resp.size());
  }

  @Test
  void searchByAnyFilter_combinedWithMetricPattern() {
    // anyFilter matches metric name OR tag; metricPattern further restricts to cpu.*
    var resp =
        search(
            req()
                .anyMetricOrValueFilter(anyFilter().value("prod").build())
                .metricNamePattern("cpu\\..*"));
    // "prod" is a tag value in P1, P2, P4 — all are cpu.* metrics
    assertEquals(3, resp.size());
  }

  @Test
  void searchByAnyFilter_bothBlank_throwsBadRequest() {
    assertThrows(
        BadRequestException.class, () -> search(req().anyMetricOrValueFilter(anyFilter().build())));
  }

  // --- helpers ---

  // Year 2100 in millis — large enough for tests, safely within DateTime64(3) range
  private static final long FAR_FUTURE_MS = 4_102_444_800_000L;

  private Set<MetricPath> search(SearchMetricsRequest.SearchMetricsRequestBuilder builder) {
    var request = builder.tsStartMillis(0).tsEndMillis(FAR_FUTURE_MS).build();
    return searchProcessor.searchMetricsResponse(request).getMatchingPaths().stream()
        .collect(Collectors.toSet());
  }

  private SearchMetricsRequest.SearchMetricsRequestBuilder req() {
    return SearchMetricsRequest.builder();
  }

  private MetricPath path(
      String metric, METRIC_TYPE type, String temporality, Map<String, String> labels) {
    return MetricPath.builder()
        .metric(metric)
        .metricType(type)
        .temporality(temporality)
        .labels(labels)
        .build();
  }

  private MetricPath path(String metric, METRIC_TYPE type, Map<String, String> labels) {
    return path(metric, type, null, labels);
  }

  private LabelValueFilter labelFilter(String label, String value) {
    return LabelValueFilter.builder().label(label).value(value).build();
  }

  private LabelValuePatternFilter patternFilter(String label, String pattern) {
    return LabelValuePatternFilter.builder().label(label).pattern(pattern).build();
  }

  private AnyMetricOrValueFilter.AnyMetricOrValueFilterBuilder anyFilter() {
    return AnyMetricOrValueFilter.builder();
  }

  private ExportMetricsServiceRequest gauge(String metricName, Map<String, String> tags) {
    var point =
        NumberDataPoint.newBuilder()
            .setTimeUnixNano(1_000L * 1_000_000)
            .setAsDouble(1.0)
            .addAllAttributes(OtelShortHands.keyValues(tags))
            .build();
    var metric =
        Metric.newBuilder()
            .setName(metricName)
            .setGauge(Gauge.newBuilder().addDataPoints(point).build())
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder()
            .setResource(Resource.newBuilder().build())
            .addScopeMetrics(ScopeMetrics.newBuilder().addMetrics(metric).build())
            .build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private ExportMetricsServiceRequest histogram(
      String metricName, Map<String, String> tags, AggregationTemporality temporality) {
    var point =
        HistogramDataPoint.newBuilder()
            .setStartTimeUnixNano(1_000L * 1_000_000)
            .setTimeUnixNano(2_000L * 1_000_000)
            .addAllExplicitBounds(List.of(1.0, 2.0))
            .addAllBucketCounts(List.of(3L, 4L, 5L))
            .addAllAttributes(OtelShortHands.keyValues(tags))
            .build();
    var histo =
        Histogram.newBuilder().setAggregationTemporality(temporality).addDataPoints(point).build();
    var metric = Metric.newBuilder().setName(metricName).setHistogram(histo).build();
    var resourceMetrics =
        ResourceMetrics.newBuilder()
            .setResource(Resource.newBuilder().build())
            .addScopeMetrics(ScopeMetrics.newBuilder().addMetrics(metric).build())
            .build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private ExportMetricsServiceRequest sum(
      String metricName, Map<String, String> tags, AggregationTemporality temporality) {
    var point =
        NumberDataPoint.newBuilder()
            .setStartTimeUnixNano(1_000L * 1_000_000)
            .setTimeUnixNano(2_000L * 1_000_000)
            .setAsDouble(42.0)
            .addAllAttributes(OtelShortHands.keyValues(tags))
            .build();
    var sum =
        Sum.newBuilder()
            .setAggregationTemporality(temporality)
            .setIsMonotonic(false)
            .addDataPoints(point)
            .build();
    var metric = Metric.newBuilder().setName(metricName).setSum(sum).build();
    var resourceMetrics =
        ResourceMetrics.newBuilder()
            .setResource(Resource.newBuilder().build())
            .addScopeMetrics(ScopeMetrics.newBuilder().addMetrics(metric).build())
            .build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }
}
