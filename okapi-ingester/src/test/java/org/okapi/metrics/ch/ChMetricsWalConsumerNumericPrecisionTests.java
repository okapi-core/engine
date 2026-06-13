/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.payloads.Gauge;
import org.okapi.rest.metrics.payloads.Histo;
import org.okapi.rest.metrics.payloads.HistoPoint;
import org.okapi.rest.metrics.payloads.SUM_TEMPORALITY;
import org.okapi.rest.metrics.payloads.Sum;
import org.okapi.rest.metrics.payloads.SumPoint;

class ChMetricsWalConsumerNumericPrecisionTests {
  private final Gson gson = new Gson();
  private final ChMetricsWalConsumer consumer = new ChMetricsWalConsumer(null, 1, null, null);

  @Test
  void rawNumericRowsPreserveFractionalValues() {
    var gaugeRows =
        consumer
            .getGaugeSamples(
                ExportMetricsRequest.builder()
                    .metricName("cpu")
                    .gauge(Gauge.builder().ts(List.of(1L)).value(List.of(1.234567890123d)).build())
                    .build())
            .rows();
    var sumRows =
        consumer
            .getSumSamples(
                ExportMetricsRequest.builder()
                    .metricName("requests")
                    .sum(
                        Sum.builder()
                            .temporality(SUM_TEMPORALITY.DELTA)
                            .sumPoints(List.of(new SumPoint(1L, 2L, 4.25d)))
                            .build())
                    .build())
            .rows();

    assertEquals(1.234567890123d, value(gaugeRows.getFirst()));
    assertEquals(4.25d, value(sumRows.getFirst()));
  }

  @Test
  void explicitHistogramRowsPreserveRawOtelBucketCounts() {
    var rows =
        consumer
            .getHistoSamples(
                ExportMetricsRequest.builder()
                    .metricName("latency")
                    .histo(
                        Histo.builder()
                            .histoPoints(
                                List.of(
                                    HistoPoint.builder()
                                        .start(1L)
                                        .end(2L)
                                        .temporality(HistoPoint.TEMPORALITY.DELTA)
                                        .buckets(new float[] {10f, 20f})
                                        .bucketCounts(new long[] {5L, 7L, 2L})
                                        .count(14L)
                                        .build()))
                            .build())
                    .build())
            .rows();

    @SuppressWarnings("unchecked")
    var row = (Map<String, Object>) gson.fromJson(rows.getFirst(), Map.class);
    assertEquals(List.of(5d, 7d, 2d), row.get("counts"));
  }

  private double value(String json) {
    @SuppressWarnings("unchecked")
    var row = (Map<String, Object>) gson.fromJson(json, Map.class);
    return (double) row.get("value");
  }
}
