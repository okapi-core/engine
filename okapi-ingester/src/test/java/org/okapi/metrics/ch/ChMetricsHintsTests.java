/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.rest.TimeInterval;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.rest.search.GetMetricNameHints;
import org.okapi.rest.search.GetTagHintsRequest;
import org.okapi.rest.search.GetTagValueHintsRequest;
import org.okapi.rest.search.MetricEventFilter;
import org.okapi.testmodules.guice.TestChMetricsModule;

public class ChMetricsHintsTests {
  @TempDir Path tempDir;

  private Injector injector;
  private Client client;
  private String testSession;

  @BeforeEach
  void setup() {
    testSession = UUID.randomUUID().toString();
    injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_GAUGES);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_HISTOS);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_SUM);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_METRIC_EVENTS_META);
  }

  @Test
  void hintQueriesUseCommonCorpus() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var svc = "svc-hints-" + UUID.randomUUID();
    var tags = Map.of("env", "dev", "region", "us-east", "test-session", testSession);
    var tagsOtherEnv = Map.of("env", "prod", "region", "us-east", "test-session", testSession);

    ingester.ingestOtelProtobuf(
        buildGaugeRequest(svc, "metric.cpu", tags, List.of(1_000L, 90_000L), List.of(1.0, 2.0)));
    ingester.ingestOtelProtobuf(
        buildGaugeRequest(svc, "metric.mem", tagsOtherEnv, List.of(1_500L), List.of(3.0)));

    ingester.ingestOtelProtobuf(
        buildHistogramRequest(
            svc,
            "metric.latency",
            tags,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(
                point(1_000L, 2_000L, List.of(10.0, 20.0), List.of(1L, 2L, 3L)),
                point(90_000L, 91_000L, List.of(10.0, 20.0), List.of(4L, 5L, 6L)))));

    ingester.ingestOtelProtobuf(
        buildSumRequest(
            svc,
            "metric.requests",
            tags,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(numberPoint(1_000L, 2_000L, 3.0), numberPoint(90_000L, 91_000L, 4.0))));

    driver.onTick();

    assertMetricHints(qp);
    assertTagHints(qp, tags);
    assertTagValueHints(qp, tags);
  }

  private void assertMetricHints(ChMetricsQueryProcessor qp) {
    var interval = new TimeInterval(0, 5_000);
    var metricReq =
        new GetMetricNameHints("metric.", interval, new MetricEventFilter(METRIC_TYPE.GAUGE));
    var resp = qp.getMetricHints(metricReq);
    assertNotNull(resp);
    assertNotNull(resp.getMetricHints());
    assertEquals(2, resp.getMetricHints().size(), "Got " + resp.toString());
    assertTrue(resp.getMetricHints().containsAll(List.of("metric.cpu", "metric.mem")));

    var nullFilterMetricReq = new GetMetricNameHints("metric.", interval, null);
    var nullFilterMetricResp = qp.getMetricHints(nullFilterMetricReq);
    assertNotNull(nullFilterMetricResp);
    assertNotNull(nullFilterMetricResp.getMetricHints());
    assertTrue(
        nullFilterMetricResp.getMetricHints().containsAll(List.of("metric.cpu", "metric.mem")));

    var histoReq =
        new GetMetricNameHints("metric.la", interval, new MetricEventFilter(METRIC_TYPE.HISTO));
    var histoResp = qp.getMetricHints(histoReq);
    assertNotNull(histoResp.getMetricHints());
    assertEquals(1, histoResp.getMetricHints().size());
    assertTrue(histoResp.getMetricHints().containsAll(List.of("metric.latency")));

    var sumReq =
        new GetMetricNameHints("metric.re", interval, new MetricEventFilter(METRIC_TYPE.SUM));
    var sumResp = qp.getMetricHints(sumReq);
    assertNotNull(sumResp.getMetricHints());
    assertEquals(1, sumResp.getMetricHints().size());
    assertTrue(sumResp.getMetricHints().containsAll(List.of("metric.requests")));
  }

  private void assertTagHints(ChMetricsQueryProcessor qp, Map<String, String> tags) {
    var interval = new TimeInterval(0, 5_000);
    var req =
        new GetTagHintsRequest(
            "metric.cpu",
            Map.of("env", "dev", "test-session", testSession),
            "re",
            interval,
            new MetricEventFilter(METRIC_TYPE.GAUGE));
    var resp = qp.getTagHints(req);
    assertNotNull(resp);
    assertNotNull(resp.getTagHints());
    assertEquals(1, resp.getTagHints().size());
    assertTrue(resp.getTagHints().containsAll(List.of("region")));

    var nullFilterReq =
        new GetTagHintsRequest(
            "metric.cpu", Map.of("env", "dev", "test-session", testSession), "re", interval, null);
    var nullFilterResp = qp.getTagHints(nullFilterReq);
    assertNotNull(nullFilterResp);
    assertNotNull(nullFilterResp.getTagHints());
    assertTrue(nullFilterResp.getTagHints().containsAll(List.of("region")));
  }

  private void assertTagValueHints(ChMetricsQueryProcessor qp, Map<String, String> tags) {
    var interval = new TimeInterval(0, 5_000);
    var req =
        new GetTagValueHintsRequest(
            "metric.cpu",
            Map.of("env", "dev", "test-session", testSession),
            "region",
            interval,
            new MetricEventFilter(METRIC_TYPE.GAUGE));
    var resp = qp.getTagValueHints(req);
    assertNotNull(resp);
    assertNotNull(resp.getTagValueHints());
    assertEquals("region", resp.getTagValueHints().getTag());
    assertNotNull(resp.getTagValueHints().getCandidates());
    assertEquals(1, resp.getTagValueHints().getCandidates().size());
    assertTrue(resp.getTagValueHints().getCandidates().containsAll(List.of("us-east")));

    var nullFilterReq =
        new GetTagValueHintsRequest(
            "metric.cpu",
            Map.of("env", "dev", "test-session", testSession),
            "region",
            interval,
            null);
    var nullFilterResp = qp.getTagValueHints(nullFilterReq);
    assertNotNull(nullFilterResp);
    assertNotNull(nullFilterResp.getTagValueHints());
    assertTrue(nullFilterResp.getTagValueHints().getCandidates().containsAll(List.of("us-east")));

    var noSvcMetricReq =
        new GetTagValueHintsRequest(
            null, Map.of("env", "dev", "test-session", testSession), "region", interval, null);
    var noSvcMetricResp = qp.getTagValueHints(noSvcMetricReq);
    assertNotNull(noSvcMetricResp);
    assertNotNull(noSvcMetricResp.getTagValueHints());
    assertTrue(noSvcMetricResp.getTagValueHints().getCandidates().containsAll(List.of("us-east")));
  }

  private ExportMetricsServiceRequest buildGaugeRequest(
      String resourceName,
      String metricName,
      Map<String, String> tags,
      List<Long> timestamps,
      List<Double> values) {
    var dataPoints = new java.util.ArrayList<NumberDataPoint>(timestamps.size());
    for (int i = 0; i < timestamps.size(); i++) {
      long ts = timestamps.get(i);
      double val = values.get(i);
      dataPoints.add(
          NumberDataPoint.newBuilder()
              .setTimeUnixNano(ts * 1_000_000)
              .setAsDouble(val)
              .addAllAttributes(
                  tags.entrySet().stream()
                      .map(
                          e ->
                              io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                                  .setKey(e.getKey())
                                  .setValue(
                                      io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                                          .setStringValue(e.getValue())
                                          .build())
                                  .build())
                      .toList())
              .build());
    }
    var gauge = Gauge.newBuilder().addAllDataPoints(dataPoints).build();
    Metric metric = Metric.newBuilder().setName(metricName).setGauge(gauge).build();
    var scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
    var resource =
        Resource.newBuilder()
            .addAttributes(
                io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                    .setKey("service.name")
                    .setValue(
                        io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                            .setStringValue(resourceName)
                            .build())
                    .build())
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private ExportMetricsServiceRequest buildHistogramRequest(
      String resourceName,
      String metricName,
      Map<String, String> tags,
      AggregationTemporality temporality,
      List<HistogramDataPoint> points) {
    var hist =
        Histogram.newBuilder()
            .setAggregationTemporality(temporality)
            .addAllDataPoints(points)
            .build();
    Metric metric = Metric.newBuilder().setName(metricName).setHistogram(hist).build();
    var scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
    var resource =
        Resource.newBuilder()
            .addAttributes(
                io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                    .setKey("service.name")
                    .setValue(
                        io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                            .setStringValue(resourceName)
                            .build())
                    .build())
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private HistogramDataPoint point(
      long startMs, long endMs, List<Double> bounds, List<Long> counts) {
    var builder =
        HistogramDataPoint.newBuilder()
            .setStartTimeUnixNano(startMs * 1_000_000)
            .setTimeUnixNano(endMs * 1_000_000)
            .addAllExplicitBounds(bounds)
            .addAllBucketCounts(counts);
    builder.addAllAttributes(
        List.of(
            io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                .setKey("env")
                .setValue(
                    io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                        .setStringValue("dev")
                        .build())
                .build(),
            io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                .setKey("region")
                .setValue(
                    io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                        .setStringValue("us-east")
                        .build())
                .build(),
            io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                .setKey("test-session")
                .setValue(
                    io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                        .setStringValue(testSession)
                        .build())
                .build()));
    return builder.build();
  }

  private ExportMetricsServiceRequest buildSumRequest(
      String resourceName,
      String metricName,
      Map<String, String> tags,
      AggregationTemporality temporality,
      List<NumberDataPoint> points) {
    var sum =
        Sum.newBuilder()
            .setAggregationTemporality(temporality)
            .setIsMonotonic(false)
            .addAllDataPoints(points)
            .build();
    Metric metric = Metric.newBuilder().setName(metricName).setSum(sum).build();
    var scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
    var resource =
        Resource.newBuilder()
            .addAttributes(
                io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                    .setKey("service.name")
                    .setValue(
                        io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                            .setStringValue(resourceName)
                            .build())
                    .build())
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private NumberDataPoint numberPoint(long startMs, long endMs, double val) {
    var builder =
        NumberDataPoint.newBuilder()
            .setStartTimeUnixNano(startMs * 1_000_000)
            .setTimeUnixNano(endMs * 1_000_000)
            .setAsDouble(val);
    builder.addAllAttributes(
        List.of(
            io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                .setKey("env")
                .setValue(
                    io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                        .setStringValue("dev")
                        .build())
                .build(),
            io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                .setKey("region")
                .setValue(
                    io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                        .setStringValue("us-east")
                        .build())
                .build(),
            io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                .setKey("test-session")
                .setValue(
                    io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                        .setStringValue(testSession)
                        .build())
                .build()));
    return builder.build();
  }
}
