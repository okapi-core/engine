/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import io.opentelemetry.proto.resource.v1.Resource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.Constants;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.ch.ChMetricsIngester;
import org.okapi.metrics.ch.ChMetricsWalConsumerDriver;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.VectorData;
import org.okapi.promql.parse.LabelMatcher;
import org.okapi.promql.parse.LabelOp;
import org.okapi.promql.query.PromQlQueryProcessor;
import org.okapi.promql.runtime.SeriesDiscoveryFactory;
import org.okapi.testmodules.guice.TestChMetricsModule;

public class PromQlHistogramWindowTests {

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
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_HISTOS);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_GAUGES);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_METRIC_EVENTS_META);
  }

  @Test
  void histogramQuantileUsesWindowedSamples() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var promql = injector.getInstance(PromQlQueryProcessor.class);

    var resource = "svc-histo-" + UUID.randomUUID();
    var metric = "latency_histo";
    var tags = Map.of("env", "dev", "test-session", testSession);

    var req =
        buildHistogramRequest(
            resource,
            metric,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(
                point(tags, 0L, 1_000L, List.of(10.0, 20.0), List.of(1L, 0L, 0L), 5d),
                point(tags, 1_000L, 2_000L, List.of(10.0, 20.0), List.of(0L, 1L, 0L), 15d)));

    ingester.ingestOtelProtobuf(req);
    driver.onTick();

    var result =
        promql.queryRange(
            Constants.DEFAULT_TENANT,
            "histogram_quantile(0.5, latency_histo)",
            1_000L,
            2_000L,
            1_000L);
    assertNotNull(result);
    var matrix = ((InstantVectorResult) result).toMatrix();
    assertFalse(matrix.isEmpty());
    var entry = matrix.entrySet().iterator().next();
    List<VectorData.Sample> samples = entry.getValue();
    assertEquals(2, samples.size());
    assertEquals(5.0f, samples.get(0).value());
    assertEquals(15.0f, samples.get(1).value());

    var sumResult =
        promql.queryRange(
            Constants.DEFAULT_TENANT, "histogram_sum(latency_histo)", 1_000L, 2_000L, 1_000L);
    var sumSamples = ((InstantVectorResult) sumResult).toMatrix().values().iterator().next();
    assertEquals(5.0f, sumSamples.get(0).value());
    assertEquals(15.0f, sumSamples.get(1).value());
  }

  @Test
  void seriesDiscoveryIncludesTagsOnly() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var discoveryFactory = injector.getInstance(SeriesDiscoveryFactory.class);

    var resource = "svc-label-" + UUID.randomUUID();
    var metric = "cpu_usage";
    var tags = Map.of("env", "prod", "test-session", testSession);

    ingester.ingestOtelProtobuf(
        buildGaugeRequest(resource, metric, tags, List.of(1_000L), List.of(1.0)));
    driver.onTick();

    var discovery = discoveryFactory.get(Constants.DEFAULT_TENANT);
    var series = discovery.expand(metric, List.of(), 0, 5_000);
    assertFalse(series.isEmpty());
    var labels = series.getFirst().labels().tags();
    assertEquals("prod", labels.get("env"));
    assertFalse(labels.containsKey("service"));
  }

  @Test
  void seriesDiscoveryMatchesNameWithoutReturningNameLabel() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var discoveryFactory = injector.getInstance(SeriesDiscoveryFactory.class);

    var resource = "svc-name-" + UUID.randomUUID();
    var metric = "disk_bytes";
    var tags = Map.of("env", "prod", "test-session", testSession);

    ingester.ingestOtelProtobuf(
        buildGaugeRequest(resource, metric, tags, List.of(1_000L), List.of(1.0)));
    driver.onTick();

    var discovery = discoveryFactory.get(Constants.DEFAULT_TENANT);
    var matchers = List.of(new LabelMatcher("__name__", LabelOp.EQ, metric));
    var series = discovery.expand(null, matchers, 0, 5_000);
    assertFalse(series.isEmpty());
    var labels = series.getFirst().labels().tags();
    assertEquals("prod", labels.get("env"));
    assertFalse(labels.containsKey("service"));
    assertFalse(labels.containsKey("__name__"));
  }

  private ExportMetricsServiceRequest buildHistogramRequest(
      String resourceName,
      String metricName,
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

  private ExportMetricsServiceRequest buildGaugeRequest(
      String resourceName,
      String metricName,
      Map<String, String> tags,
      List<Long> ts,
      List<Double> vals) {
    var points = new java.util.ArrayList<NumberDataPoint>();
    for (int i = 0; i < ts.size(); i++) {
      var builder =
          NumberDataPoint.newBuilder()
              .setTimeUnixNano(ts.get(i) * 1_000_000)
              .setAsDouble(vals.get(i));
      for (var entry : tags.entrySet()) {
        builder.addAttributes(
            io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
                .setKey(entry.getKey())
                .setValue(
                    io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                        .setStringValue(entry.getValue())
                        .build())
                .build());
      }
      var pt = builder.build();
      points.add(pt);
    }
    var gauge = Gauge.newBuilder().addAllDataPoints(points).build();
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

  private HistogramDataPoint point(
      Map<String, String> tags,
      long startMs,
      long endMs,
      List<Double> bounds,
      List<Long> counts,
      double sum) {
    var builder =
        HistogramDataPoint.newBuilder()
            .setStartTimeUnixNano(startMs * 1_000_000)
            .setTimeUnixNano(endMs * 1_000_000)
            .addAllExplicitBounds(bounds)
            .addAllBucketCounts(counts)
            .setCount(counts.stream().mapToLong(Long::longValue).sum())
            .setSum(sum);
    for (var entry : tags.entrySet()) {
      builder.addAttributes(
          io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
              .setKey(entry.getKey())
              .setValue(
                  io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                      .setStringValue(entry.getValue())
                      .build())
              .build());
    }
    return builder.build();
  }
}
