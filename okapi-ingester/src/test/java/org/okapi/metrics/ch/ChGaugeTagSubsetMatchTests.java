/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.*;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.metrics.common.MetricPaths;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.rest.metrics.query.GaugeQueryConfig;
import org.okapi.rest.metrics.query.GetGaugeResponse;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.testmodules.guice.TestChMetricsModule;

public class ChGaugeTagSubsetMatchTests {

  private static final String METRIC = "cpu_usage";

  @TempDir Path tempDir;

  private Injector injector;
  private Client client;
  private ChMetricsIngester ingester;
  private ChMetricsWalConsumerDriver driver;
  private ChMetricsQueryProcessor qp;

  @BeforeEach
  void setup() throws Exception {
    injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    truncateGaugeTable();
    ingester = injector.getInstance(ChMetricsIngester.class);
    driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    qp = injector.getInstance(ChMetricsQueryProcessor.class);
    ingestCorpus();
    driver.onTick();
  }

  @Test
  void subsetMatchServiceReturnsAllSeries() throws Exception {
    var req = buildRequest(Map.of("service", "checkout"));
    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNotNull(resp);
    var expected = expectedSeries();
    assertSeriesContentMatch(resp, expected);
    assertEquals(3, resp.getSeries().size());
  }

  @Test
  void subsetMatchEnvReturnsProdSeries() throws Exception {
    var req = buildRequest(Map.of("env", "prod"));
    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNotNull(resp);
    var expected = expectedSeries();
    assertEquals(2, resp.getSeries().size());
    assertSeriesContentMatch(resp, expected);
  }

  @Test
  void subsetMatchEnvAndServiceReturnsProdSeries() throws Exception {
    var req = buildRequest(Map.of("env", "prod", "service", "checkout"));
    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNotNull(resp);
    var expected = expectedSeries();
    assertEquals(2, resp.getSeries().size());
    assertSeriesContentMatch(resp, expected);
  }

  @Test
  void subsetMatchWithNoResultsReturnsEmpty() throws Exception {
    var req = buildRequest(Map.of("env", "dev", "pod", "x"));
    var resp = qp.getMetricsResponse(req).getGaugeResponse();
    assertNull(resp);
  }

  private GetMetricsRequest buildRequest(Map<String, String> tags) {
    return GetMetricsRequest.builder()
        .metric(METRIC)
        .tags(tags)
        .start(0)
        .end(10_000)
        .metricType(METRIC_TYPE.GAUGE)
        .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.AVG))
        .build();
  }

  private void assertSeriesContentMatch(
      GetGaugeResponse resp, Map<String, ExpectedSeries> expected) {
    for (var series : resp.getSeries()) {
      var key = MetricPaths.getMetricPath(METRIC, series.getTags());
      var expectation = expected.get(key);
      assertEquals(expectation.times(), series.getTimes());
      assertEquals(expectation.values(), series.getValues());
    }
  }

  private Map<String, ExpectedSeries> expectedSeries() {
    var out = new HashMap<String, ExpectedSeries>();
    out.put(
        MetricPaths.getMetricPath(METRIC, Map.of("env", "prod", "service", "checkout", "pod", "x")),
        new ExpectedSeries(List.of(1_000L, 2_000L), List.of(1.0f, 2.0f)));
    out.put(
        MetricPaths.getMetricPath(METRIC, Map.of("env", "dev", "service", "checkout", "pod", "y")),
        new ExpectedSeries(List.of(1_000L, 2_000L), List.of(3.0f, 4.0f)));
    out.put(
        MetricPaths.getMetricPath(METRIC, Map.of("env", "prod", "service", "checkout", "pod", "z")),
        new ExpectedSeries(List.of(1_000L, 2_000L), List.of(5.0f, 6.0f)));
    return out;
  }

  private void ingestCorpus() throws Exception {
    ingester.ingestOtelProtobuf(
        buildGaugeRequest(
            "svc-1",
            METRIC,
            List.of(
                gaugePoint(1_000L, Map.of("env", "prod", "service", "checkout", "pod", "x"), 1.0),
                gaugePoint(
                    2_000L, Map.of("env", "prod", "service", "checkout", "pod", "x"), 2.0))));
    ingester.ingestOtelProtobuf(
        buildGaugeRequest(
            "svc-2",
            METRIC,
            List.of(
                gaugePoint(1_000L, Map.of("env", "dev", "service", "checkout", "pod", "y"), 3.0),
                gaugePoint(2_000L, Map.of("env", "dev", "service", "checkout", "pod", "y"), 4.0))));
    ingester.ingestOtelProtobuf(
        buildGaugeRequest(
            "svc-3",
            METRIC,
            List.of(
                gaugePoint(1_000L, Map.of("env", "prod", "service", "checkout", "pod", "z"), 5.0),
                gaugePoint(
                    2_000L, Map.of("env", "prod", "service", "checkout", "pod", "z"), 6.0))));
  }

  private ExportMetricsServiceRequest buildGaugeRequest(
      String resourceName, String metricName, List<NumberDataPoint> points) {
    var gauge = Gauge.newBuilder().addAllDataPoints(points).build();
    Metric metric = Metric.newBuilder().setName(metricName).setGauge(gauge).build();
    var scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
    var resource =
        Resource.newBuilder()
            .addAttributes(OtelShortHands.keyValue("service.name", resourceName))
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private NumberDataPoint gaugePoint(long tsMs, Map<String, String> tags, double val) {
    return NumberDataPoint.newBuilder()
        .setTimeUnixNano(tsMs * 1_000_000)
        .setAsDouble(val)
        .addAllAttributes(OtelShortHands.keyValues(tags))
        .build();
  }

  private void truncateGaugeTable() {
    client.queryAll("TRUNCATE TABLE IF EXISTS okapi_metrics.gauge_raw_samples");
  }

  private record ExpectedSeries(List<Long> times, List<Float> values) {}
}
