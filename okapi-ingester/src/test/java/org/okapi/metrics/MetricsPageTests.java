/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.okapi.metrics.io.MetricsPage;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.payloads.Gauge;
import org.okapi.rest.metrics.payloads.Histo;
import org.okapi.rest.metrics.payloads.HistoPoint;
import org.okapi.testutils.OkapiTestUtils;

import java.util.Arrays;
import java.util.List;

public class MetricsPageTests {

  @Test
  void testSingleElementInsert() {
    var page = new MetricsPage(1000L, 1000L, 1000, 0.01);
    page.append(
        ExportMetricsRequest.builder()
            .metricName("cpu_usage")
            .gauge(Gauge.builder().ts(List.of(1000L)).value(List.of(0.1f)).build())
            .type(org.okapi.rest.metrics.MetricType.GAUGE)
            .build());

    var result = page.getSecondly("cpu_usage{}", 1000L, new double[] {0.5});
    assert (result.isPresent());
    assert (result.get().getMean() == 0.1f);

    assertRange(page, 1000L, 1000L);
  }

  @Test
  void testMultipleElementInsert() {
    var page = new MetricsPage(1000L, 1000L, 1000, 0.01);
    page.append(
        ExportMetricsRequest.builder()
            .metricName("cpu_usage")
            .gauge(Gauge.builder().ts(List.of(1000L, 2000L)).value(List.of(0.1f, 0.2f)).build())
            .type(org.okapi.rest.metrics.MetricType.GAUGE)
            .build());

    var result1 = page.getSecondly("cpu_usage{}", 1000L, new double[] {0.5});
    assert (result1.isPresent());
    assert (result1.get().getMean() == 0.1f);

    var result2 = page.getSecondly("cpu_usage{}", 2000L, new double[] {0.5});
    assert (result2.isPresent());
    assert (result2.get().getMean() == 0.2f);
    assertRange(page, 1000L, 2000L);
  }

  @Test
  void testMultipleTypeInsert() {
    var page = new MetricsPage(1000L, 1000L, 1000, 0.01);
    page.append(
        ExportMetricsRequest.builder()
            .metricName("cpu_usage")
            .gauge(Gauge.builder().ts(List.of(1000L)).value(List.of(0.1f)).build())
            .type(org.okapi.rest.metrics.MetricType.GAUGE)
            .build());
    // add another gauge and two histograms
    page.append(
        ExportMetricsRequest.builder()
            .metricName("memory_usage")
            .gauge(Gauge.builder().ts(List.of(1000L)).value(List.of(0.3f)).build())
            .type(org.okapi.rest.metrics.MetricType.GAUGE)
            .build());
    // add histogram
    page.append(
        ExportMetricsRequest.builder()
            .metricName("request_latency")
            .histo(
                Histo.builder()
                    .histoPoints(
                        Arrays.asList(
                            new HistoPoint(
                                1000L,
                                2000L,
                                HistoPoint.TEMPORALITY.DELTA,
                                new float[] {0.1f, 0.2f, 0.3f, 0.4f},
                                new long[] {1, 2, 3, 4, 5})))
                    .build())
            .build());
    var result1 = page.getSecondly("cpu_usage{}", 1000L, new double[] {0.5});
    assert (result1.isPresent());
    assert (result1.get().getMean() == 0.1f);
    var result2 = page.getSecondly("memory_usage{}", 1000L, new double[] {0.5});
    assert (result2.isPresent());
    assert (result2.get().getMean() == 0.3f);
    var histoResult = page.getHistogram("request_latency{}", 1000L);
    assert (histoResult.isPresent());

    assert (histoResult.get().getBucketCounts().size() == 5);
    OkapiTestUtils.assertListEquals(
        Arrays.asList(1L, 2L, 3L, 4L, 5L), histoResult.get().getBucketCounts());
    OkapiTestUtils.assertListEquals(
        Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f), histoResult.get().getBuckets());

    assertRange(page, 1000L, 2000L);
  }

  @Test
  void testPageWithoutAppendIsMarkedEmpty() {
    var metricsPage = new MetricsPage(1000L, 1000L, 1000, 0.01);
    Assertions.assertTrue(metricsPage.isEmpty());
    Assertions.assertFalse(metricsPage.isFull());
  }

  public void assertRange(MetricsPage page, long expectedStart, long expectedEnd) {
    var range = page.range();
    Assertions.assertTrue(range.isPresent());
    Assertions.assertEquals(expectedStart, range.get().startInclusive());
    Assertions.assertEquals(expectedEnd, range.get().endInclusive());
  }
}
