/*
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

class ChMetricsWalConsumerMetadataTests {
  private final Gson gson = new Gson();
  private final ChMetricsWalConsumer consumer = new ChMetricsWalConsumer(1, null, null);

  @Test
  void metadataRowsCarryUnitsForEveryMetricType() {
    assertUnit(
        consumer
            .getGaugeSamples(
                ExportMetricsRequest.builder()
                    .metricName("cpu")
                    .unit("cores")
                    .tags(Map.of("host", "a"))
                    .gauge(Gauge.builder().ts(List.of(1L)).value(List.of(2f)).build())
                    .build())
            .meta()
            .getFirst(),
        "cores");
    assertUnit(
        consumer
            .getHistoSamples(
                ExportMetricsRequest.builder()
                    .metricName("latency")
                    .unit("ms")
                    .tags(Map.of("host", "a"))
                    .histo(
                        Histo.builder()
                            .histoPoints(
                                List.of(
                                    HistoPoint.builder()
                                        .start(1L)
                                        .end(2L)
                                        .temporality(HistoPoint.TEMPORALITY.DELTA)
                                        .buckets(new float[] {1f})
                                        .bucketCounts(new long[] {1L, 2L})
                                        .build()))
                            .build())
                    .build())
            .meta()
            .getFirst(),
        "ms");
    assertUnit(
        consumer
            .getSumSamples(
                ExportMetricsRequest.builder()
                    .metricName("requests")
                    .unit("requests")
                    .tags(Map.of("host", "a"))
                    .sum(
                        Sum.builder()
                            .temporality(SUM_TEMPORALITY.DELTA)
                            .sumPoints(List.of(new SumPoint(1L, 2L, 3L)))
                            .build())
                    .build())
            .meta()
            .getFirst(),
        "requests");
  }

  private void assertUnit(String json, String expected) {
    @SuppressWarnings("unchecked")
    var metadata = (Map<String, Object>) gson.fromJson(json, Map.class);
    assertEquals(expected, metadata.get("unit"));
  }
}
