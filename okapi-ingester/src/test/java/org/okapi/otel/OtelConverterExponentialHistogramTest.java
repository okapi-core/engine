/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.ExponentialHistogram;
import io.opentelemetry.proto.metrics.v1.ExponentialHistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import org.junit.jupiter.api.Test;
import org.okapi.metrics.otel.OtelConverter;
import org.okapi.rest.metrics.payloads.HistoPoint;

class OtelConverterExponentialHistogramTest {
  @Test
  void exponentialHistogramConversionPreservesNativeBuckets() {
    var point =
        ExponentialHistogramDataPoint.newBuilder()
            .setStartTimeUnixNano(1_000_000L)
            .setTimeUnixNano(2_000_000L)
            .setScale(3)
            .setZeroThreshold(0.01d)
            .setZeroCount(2L)
            .setPositive(
                ExponentialHistogramDataPoint.Buckets.newBuilder()
                    .setOffset(-1)
                    .addAllBucketCounts(java.util.List.of(3L, 4L)))
            .setNegative(
                ExponentialHistogramDataPoint.Buckets.newBuilder()
                    .setOffset(2)
                    .addAllBucketCounts(java.util.List.of(5L)))
            .setSum(12.5d)
            .setCount(14L)
            .build();
    var metric =
        Metric.newBuilder()
            .setName("latency")
            .setUnit("ms")
            .setExponentialHistogram(
                ExponentialHistogram.newBuilder()
                    .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA)
                    .addDataPoints(point))
            .build();
    var request =
        ExportMetricsServiceRequest.newBuilder()
            .addResourceMetrics(
                ResourceMetrics.newBuilder()
                    .addScopeMetrics(ScopeMetrics.newBuilder().addMetrics(metric)))
            .build();

    var converted = new OtelConverter().toOkapiRequests(request).getFirst();
    var actual = converted.getExponentialHisto().getHistoPoints().getFirst();

    assertEquals("ms", converted.getUnit());
    assertEquals(HistoPoint.TEMPORALITY.DELTA, actual.getTemporality());
    assertEquals(3, actual.getScale());
    assertEquals(0.01d, actual.getZeroThreshold());
    assertEquals(2L, actual.getZeroCount());
    assertEquals(-1, actual.getPositiveOffset());
    assertArrayEquals(new long[] {3L, 4L}, actual.getPositiveCounts());
    assertEquals(2, actual.getNegativeOffset());
    assertArrayEquals(new long[] {5L}, actual.getNegativeCounts());
    assertEquals(12.5d, actual.getSum());
    assertEquals(14L, actual.getCount());
  }
}
