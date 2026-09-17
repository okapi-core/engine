/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.clickhouse.client.api.Client;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.ch.CreateChTablesSpec;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.HistoQueryConfig;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.testmodules.guice.TestChMetricsModule;

public class ChHistogramTests {
  @TempDir java.nio.file.Path tempDir;

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
  }

  @Test
  void deltaHistogramsMergeAcrossTwoPoints() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var resource = "svc-histo-" + UUID.randomUUID();
    var metric = "metric_histo";
    var tags = Map.of("env", "dev", "test-session", testSession);

    var req =
        buildHistogramRequest(
            resource,
            metric,
            tags,
            "ms",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(
                point(1_000L, 2_000L, List.of(10.0, 20.0), List.of(1L, 2L, 3L)),
                point(2_000L, 3_000L, List.of(10.0, 20.0), List.of(4L, 5L, 6L))));

    ingester.ingestOtelProtobuf(req);
    driver.onTick();

    var queryReq =
        GetMetricsRequest.builder()
            .metric(metric)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.HISTO)
            .histoQueryConfig(
                HistoQueryConfig.builder().temporality(HistoQueryConfig.TEMPORALITY.MERGED).build())
            .build();

    var resp = qp.getMetricsResponse(queryReq);
    assertNotNull(resp.getHistogramResponse());
    var series = resp.getHistogramResponse().getSeries();
    assertNotNull(series);
    assertEquals(1, series.size());
    var histo = series.get(0).getHistogram();
    assertEquals("ms", series.get(0).getUnit());
    assertEquals(List.of(1L + 4, 2L + 5, 3L + 6), histo.getCounts());
    assertEquals(List.of(10.0f, 20.0f), histo.getBuckets());
  }

  @Test
  void queryMixedUnits() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var resource = "svc-histo-mixed-" + UUID.randomUUID();
    var metric = "metric_histo_units";
    var tags = Map.of("env", "dev", "test-session", testSession);

    var msReq =
        buildHistogramRequest(
            resource,
            metric,
            tags,
            "ms",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(point(1_000L, 2_000L, List.of(10.0, 20.0), List.of(1L, 2L, 3L))));
    var sReq =
        buildHistogramRequest(
            resource,
            metric,
            tags,
            "s",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(point(1_000L, 2_000L, List.of(10.0, 20.0), List.of(4L, 5L, 6L))));

    ingester.ingestOtelProtobuf(msReq);
    ingester.ingestOtelProtobuf(sReq);
    driver.onTick();

    var queryReq =
        GetMetricsRequest.builder()
            .metric(metric)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.HISTO)
            .histoQueryConfig(
                HistoQueryConfig.builder().temporality(HistoQueryConfig.TEMPORALITY.MERGED).build())
            .build();

    var resp = qp.getMetricsResponse(queryReq);
    assertNotNull(resp.getHistogramResponse());
    var series = resp.getHistogramResponse().getSeries();
    assertEquals(2, series.size());

    var byUnit =
        series.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    org.okapi.rest.metrics.query.HistogramSeries::getUnit,
                    org.okapi.rest.metrics.query.HistogramSeries::getHistogram));
    assertEquals(java.util.Set.of("ms", "s"), byUnit.keySet());
    assertEquals(List.of(1L, 2L, 3L), byUnit.get("ms").getCounts());
    assertEquals(List.of(4L, 5L, 6L), byUnit.get("s").getCounts());
  }

  @Test
  void histogramWithNoUnit_returnsEmptyUnit() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var resource = "svc-histo-nounit-" + UUID.randomUUID();
    var metric = "metric_histo_nounit";
    var tags = Map.of("env", "dev", "test-session", testSession);

    ingester.ingestOtelProtobuf(
        buildHistogramRequest(
            resource,
            metric,
            tags,
            "",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(point(1_000L, 2_000L, List.of(10.0, 20.0), List.of(1L, 2L, 3L)))));
    driver.onTick();

    var queryReq =
        GetMetricsRequest.builder()
            .metric(metric)
            .tags(tags)
            .start(0)
            .end(5_000)
            .metricType(METRIC_TYPE.HISTO)
            .histoQueryConfig(
                HistoQueryConfig.builder().temporality(HistoQueryConfig.TEMPORALITY.MERGED).build())
            .build();

    var series = qp.getMetricsResponse(queryReq).getHistogramResponse().getSeries();
    assertEquals(1, series.size());
    assertEquals("", series.get(0).getUnit());
  }

  private ExportMetricsServiceRequest buildHistogramRequest(
      String resourceName,
      String metricName,
      Map<String, String> tags,
      String unit,
      AggregationTemporality temporality,
      List<HistogramDataPoint> points) {
    var hist =
        Histogram.newBuilder()
            .setAggregationTemporality(temporality)
            .addAllDataPoints(points)
            .build();
    Metric metric =
        Metric.newBuilder().setName(metricName).setUnit(unit).setHistogram(hist).build();
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
    builder.addAttributes(
        io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
            .setKey("env")
            .setValue(
                io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                    .setStringValue("dev")
                    .build())
            .build());
    builder.addAttributes(
        io.opentelemetry.proto.common.v1.KeyValue.newBuilder()
            .setKey("test-session")
            .setValue(
                io.opentelemetry.proto.common.v1.AnyValue.newBuilder()
                    .setStringValue(testSession)
                    .build())
            .build());
    return builder.build();
  }
}
