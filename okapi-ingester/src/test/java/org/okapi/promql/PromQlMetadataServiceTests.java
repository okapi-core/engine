/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql;

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
import io.opentelemetry.proto.resource.v1.Resource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.ch.ChMetricsIngester;
import org.okapi.metrics.ch.ChMetricsWalConsumerDriver;
import org.okapi.promql.query.PromQlMetadataService;
import org.okapi.rest.promql.PromQlMetadataItem;
import org.okapi.testmodules.guice.TestChMetricsModule;

public class PromQlMetadataServiceTests {

  @TempDir Path tempDir;

  private Injector injector;
  private Client client;

  @BeforeEach
  void setup() {
    injector = Guice.createInjector(new TestChMetricsModule(tempDir.resolve("wal"), 16));
    client = injector.getInstance(Client.class);
    CreateChTablesSpec.migrate(client);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_GAUGES);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_HISTOS);
    client.queryAll("TRUNCATE TABLE IF EXISTS " + ChConstants.TBL_METRIC_EVENTS_META);
  }

  @Test
  void metadataReturnsRecentMetrics() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var service = injector.getInstance(PromQlMetadataService.class);

    var resource = "svc-meta-" + UUID.randomUUID();
    var gaugeMetric = "cpu_meta";
    var histoMetric = "latency_meta";
    var tags = Map.of("env", "dev");

    ingester.ingestOtelProtobuf(
        buildGaugeRequest(resource, gaugeMetric, tags, List.of(nowMs()), List.of(1.0)));
    ingester.ingestOtelProtobuf(
        buildHistogramRequest(
            resource,
            histoMetric,
            tags,
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(
                point(tags, nowMs() - 1_000, nowMs(), List.of(10.0, 20.0), List.of(1L, 0L, 0L)))));
    driver.onTick();

    var resp = service.getMetadata(null, 100);
    assertNotNull(resp);
    assertTrue(resp.getData().containsKey(gaugeMetric));
    assertTrue(resp.getData().containsKey(histoMetric));
  }

  @Test
  void metadataRespectsLimit() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var service = injector.getInstance(PromQlMetadataService.class);

    var resource = "svc-limit-" + UUID.randomUUID();
    var tags = Map.of("env", "dev");
    for (int i = 0; i < 3; i++) {
      ingester.ingestOtelProtobuf(
          buildGaugeRequest(resource, "metric_" + i, tags, List.of(nowMs()), List.of(1.0)));
    }
    driver.onTick();

    var resp = service.getMetadata(null, 2);
    assertNotNull(resp);
    assertTrue(resp.getData().size() <= 2);
  }

  @Test
  void metadataTypeMapping() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var service = injector.getInstance(PromQlMetadataService.class);

    var resource = "svc-type-" + UUID.randomUUID();
    var metric = "cpu_type";
    var tags = Map.of("env", "prod");
    ingester.ingestOtelProtobuf(
        buildGaugeRequest(resource, metric, tags, List.of(nowMs()), List.of(2.0)));
    driver.onTick();

    var resp = service.getMetadata(metric, 10);
    assertNotNull(resp);
    var items = resp.getData().get(metric);
    assertNotNull(items);
    assertEquals(1, items.size());
    PromQlMetadataItem item = items.getFirst();
    assertEquals("gauge", item.getType());
  }

  private long nowMs() {
    return System.currentTimeMillis();
  }

  private ExportMetricsServiceRequest buildGaugeRequest(
      String resourceName,
      String metricName,
      Map<String, String> tags,
      List<Long> ts,
      List<Double> vals) {
    var points = new ArrayList<NumberDataPoint>();
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
      points.add(builder.build());
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
      Map<String, String> tags, long startMs, long endMs, List<Double> bounds, List<Long> counts) {
    var builder =
        HistogramDataPoint.newBuilder()
            .setStartTimeUnixNano(startMs * 1_000_000)
            .setTimeUnixNano(endMs * 1_000_000)
            .addAllExplicitBounds(bounds)
            .addAllBucketCounts(counts);
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
