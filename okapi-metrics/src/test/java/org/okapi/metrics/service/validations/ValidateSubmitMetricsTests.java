/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.service.validations;

import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.MetricType;
import org.okapi.rest.metrics.payloads.*;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ValidateSubmitMetricsTests {

  @Test
  public void gauge_null_payload_throws() {
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.GAUGE)
            .build();
    assertThrows(
        BadRequestException.class, () -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void gauge_timestamps_too_far_apart_throws() {
    var gauge = new Gauge(Arrays.asList(0L, 24 * 3_600_001L + 1000L), Arrays.asList(1.0f, 2.0f));
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.GAUGE)
            .gauge(gauge)
            .build();
    assertThrows(
        BadRequestException.class, () -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void gauge_single_timestamp_passes() {
    var gauge = new Gauge(Arrays.asList(1L), Arrays.asList(1.0f));
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.GAUGE)
            .gauge(gauge)
            .build();
    assertDoesNotThrow(() -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void histo_null_payload_throws() {
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.HISTO)
            .build();
    assertThrows(
        BadRequestException.class, () -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void histo_missing_arrays_throws() {
    var pt = new HistoPoint(0L, 1000L, HistoPoint.TEMPORALITY.DELTA, null, null);
    var histo = new Histo(java.util.List.of(pt));
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.HISTO)
            .histo(histo)
            .build();
    assertThrows(
        BadRequestException.class, () -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void histo_unequal_lengths_throws() {
    var pt =
        new HistoPoint(
            0L, 1000L, HistoPoint.TEMPORALITY.DELTA, new float[] {0.1f, 0.2f}, new long[] {1});
    var histo = new Histo(java.util.List.of(pt));
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.HISTO)
            .histo(histo)
            .build();
    assertThrows(
        BadRequestException.class, () -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void histo_negative_value_throws() {
    var pt =
        new HistoPoint(
            0L,
            1000L,
            HistoPoint.TEMPORALITY.DELTA,
            new float[] {0.1f, 0.2f},
            new long[] {1, -1, 2});
    var histo = new Histo(java.util.List.of(pt));
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.HISTO)
            .histo(histo)
            .build();
    assertThrows(
        BadRequestException.class, () -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void histo_valid_passes() {
    var pt =
        new HistoPoint(
            0L,
            1000L,
            HistoPoint.TEMPORALITY.DELTA,
            new float[] {0.1f, 0.2f},
            new long[] {1, 2, 3});
    var histo = new Histo(java.util.List.of(pt));
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.HISTO)
            .histo(histo)
            .build();
    assertDoesNotThrow(() -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void counter_null_payload_throws() {
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.COUNTER)
            .build();
    assertThrows(
        BadRequestException.class, () -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void counter_valid_passes() {
    var counter = new Sum(SUM_TEMPORALITY.DELTA, java.util.List.of(new SumPoint(0L, 1000L, 2)));
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.COUNTER)
            .sum(counter)
            .build();
    assertDoesNotThrow(() -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }

  @Test
  public void counter_missing_points_throws() {
    var counter = new Sum(SUM_TEMPORALITY.CUMULATIVE, null);
    var req =
        ExportMetricsRequest.builder()
            .metricName("m")
            .tags(Map.of("k", "v"))
            .type(MetricType.COUNTER)
            .sum(counter)
            .build();
    assertThrows(
        BadRequestException.class, () -> ValidateSubmitMetrics.checkSubmitMetricsRequest(req));
  }
}
