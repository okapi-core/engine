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
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.metrics.v1.Histogram;
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
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.otelshorthand.OtelShortHands;
import org.okapi.rest.metrics.query.*;
import org.okapi.testmodules.guice.TestChMetricsModule;

public class ChMetricsExactOnlyMatchTest {
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
  void exactTagMatchRequiredForAggregations() throws Exception {
    var ingester = injector.getInstance(ChMetricsIngester.class);
    var driver = injector.getInstance(ChMetricsWalConsumerDriver.class);
    var qp = injector.getInstance(ChMetricsQueryProcessor.class);

    var tagsA = Map.of("env", "dev", "region", "us-east", "host", "a", "test-session", testSession);
    var tagsB = Map.of("env", "dev", "region", "us-west", "host", "b", "test-session", testSession);
    var partialTags = Map.of("env", "dev", "test-session", testSession);

    ingester.ingestOtelProtobuf(
        buildGaugeRequest("svc", "metric.cpu", List.of(gaugePoint(1_000L, tagsA, 1.0))));
    ingester.ingestOtelProtobuf(
        buildGaugeRequest("svc", "metric.cpu", List.of(gaugePoint(2_000L, tagsB, 2.0))));

    ingester.ingestOtelProtobuf(
        buildHistogramRequest(
            "svc",
            "metric.latency",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(point(1_000L, 2_000L, tagsA, List.of(10.0), List.of(1L, 1L)))));
    ingester.ingestOtelProtobuf(
        buildHistogramRequest(
            "svc",
            "metric.latency",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(point(2_000L, 3_000L, tagsB, List.of(10.0), List.of(2L, 2L)))));

    ingester.ingestOtelProtobuf(
        buildSumRequest(
            "svc",
            "metric.requests",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(numberPoint(1_000L, 2_000L, tagsA, 3.0))));
    ingester.ingestOtelProtobuf(
        buildSumRequest(
            "svc",
            "metric.requests",
            AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA,
            List.of(numberPoint(2_000L, 3_000L, tagsB, 4.0))));

    driver.onTick();

    var gaugeFull =
        qp.getMetricsResponse(
            GetMetricsRequest.builder()
                .metric("metric.cpu")
                .tags(tagsA)
                .start(0)
                .end(10_000)
                .metricType(METRIC_TYPE.GAUGE)
                .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.SUM))
                .build());
    assertNotNull(gaugeFull.getGaugeResponse());
    assertFalse(gaugeFull.getGaugeResponse().getSeries().isEmpty());

    // Gauge uses subset matching: partialTags is a subset of both tagsA and tagsB,
    // so both series should be returned.
    var gaugePartial =
        qp.getMetricsResponse(
            GetMetricsRequest.builder()
                .metric("metric.cpu")
                .tags(partialTags)
                .start(0)
                .end(10_000)
                .metricType(METRIC_TYPE.GAUGE)
                .gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.SECONDLY, AGG_TYPE.SUM))
                .build());
    assertNotNull(gaugePartial.getGaugeResponse());
    assertEquals(2, gaugePartial.getGaugeResponse().getSeries().size());

    var histoFull =
        qp.getMetricsResponse(
            GetMetricsRequest.builder()
                .metric("metric.latency")
                .tags(tagsA)
                .start(0)
                .end(10_000)
                .metricType(METRIC_TYPE.HISTO)
                .histoQueryConfig(
                    HistoQueryConfig.builder()
                        .temporality(HistoQueryConfig.TEMPORALITY.DELTA)
                        .build())
                .build());
    assertNotNull(histoFull.getHistogramResponse());
    assertNotNull(histoFull.getHistogramResponse().getSeries());
    assertFalse(histoFull.getHistogramResponse().getSeries().isEmpty());

    var histoPartial =
        qp.getMetricsResponse(
            GetMetricsRequest.builder()
                .metric("metric.latency")
                .tags(partialTags)
                .start(0)
                .end(10_000)
                .metricType(METRIC_TYPE.HISTO)
                .histoQueryConfig(
                    HistoQueryConfig.builder()
                        .temporality(HistoQueryConfig.TEMPORALITY.DELTA)
                        .build())
                .build());
    assertNotNull(histoPartial.getHistogramResponse());
    assertNotNull(histoPartial.getHistogramResponse().getSeries());
    assertEquals(2, histoPartial.getHistogramResponse().getSeries().size());

    var sumFull =
        qp.getMetricsResponse(
            GetMetricsRequest.builder()
                .metric("metric.requests")
                .tags(tagsA)
                .start(0)
                .end(10_000)
                .metricType(METRIC_TYPE.SUM)
                .sumsQueryConfig(
                    GetSumsQueryConfig.builder()
                        .temporality(GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE)
                        .build())
                .build());
    assertNotNull(sumFull.getSumsResponse());
    assertFalse(sumFull.getSumsResponse().getSums().isEmpty());

    var sumPartial =
        qp.getMetricsResponse(
            GetMetricsRequest.builder()
                .metric("metric.requests")
                .tags(partialTags)
                .start(0)
                .end(10_000)
                .metricType(METRIC_TYPE.SUM)
                .sumsQueryConfig(
                    GetSumsQueryConfig.builder()
                        .temporality(GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE)
                        .build())
                .build());
    assertNull(sumPartial.getSumsResponse());
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
            .addAttributes(OtelShortHands.keyValue("service.name", resourceName))
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private HistogramDataPoint point(
      long startMs, long endMs, Map<String, String> tags, List<Double> bounds, List<Long> counts) {
    return HistogramDataPoint.newBuilder()
        .setStartTimeUnixNano(startMs * 1_000_000)
        .setTimeUnixNano(endMs * 1_000_000)
        .addAllExplicitBounds(bounds)
        .addAllBucketCounts(counts)
        .addAllAttributes(OtelShortHands.keyValues(tags))
        .build();
  }

  private ExportMetricsServiceRequest buildSumRequest(
      String resourceName,
      String metricName,
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
            .addAttributes(OtelShortHands.keyValue("service.name", resourceName))
            .build();
    var resourceMetrics =
        ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
    return ExportMetricsServiceRequest.newBuilder().addResourceMetrics(resourceMetrics).build();
  }

  private NumberDataPoint numberPoint(
      long startMs, long endMs, Map<String, String> tags, double val) {
    return NumberDataPoint.newBuilder()
        .setStartTimeUnixNano(startMs * 1_000_000)
        .setTimeUnixNano(endMs * 1_000_000)
        .setAsDouble(val)
        .addAllAttributes(OtelShortHands.keyValues(tags))
        .build();
  }

  private NumberDataPoint gaugePoint(long tsMs, Map<String, String> tags, double val) {
    return NumberDataPoint.newBuilder()
        .setTimeUnixNano(tsMs * 1_000_000)
        .setAsDouble(val)
        .addAllAttributes(OtelShortHands.keyValues(tags))
        .build();
  }
}
