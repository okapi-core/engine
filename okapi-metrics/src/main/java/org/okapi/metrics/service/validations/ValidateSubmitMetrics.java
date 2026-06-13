/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.service.validations;

import org.okapi.exceptions.BadRequestException;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.payloads.Gauge;
import org.okapi.rest.metrics.payloads.Histo;
import org.okapi.rest.metrics.payloads.HistoPoint;
import org.okapi.rest.metrics.payloads.Sum;
import org.okapi.usermessages.UserFacingMessages;

import static org.okapi.validation.OkapiChecks.checkArgument;

public class ValidateSubmitMetrics {

  public static void checkSubmitMetricsRequest(ExportMetricsRequest exportMetricsRequest)
      throws BadRequestException {
    checkArgument(
        exportMetricsRequest.getType() != null,
        () -> new BadRequestException(UserFacingMessages.METRIC_TYPE_MISSING));
    var type = exportMetricsRequest.getType();
    switch (type) {
      case GAUGE -> checkGauge(exportMetricsRequest.getGauge());
      case HISTO -> checkHisto(exportMetricsRequest.getHisto());
      case COUNTER -> checkCounter(exportMetricsRequest.getSum());
    }
  }

  private static void checkGauge(Gauge gauge) throws BadRequestException {
    checkArgument(
        gauge != null, () -> new BadRequestException(UserFacingMessages.GAUGE_PAYLOAD_MISSING));
    var ts = gauge.getTs();
    if (ts == null || ts.size() <= 1) {
      return;
    }
    long min = ts.get(0);
    long max = ts.get(0);
    for (long t : ts) {
      if (t < min) min = t;
      if (t > max) max = t;
    }
    // Ensure timestamps are not more than an hour (in ms) apart
    checkArgument(
        (max - min) <= 3_600_000L * 24,
        () -> new BadRequestException(UserFacingMessages.GAUGE_TIMESTAMPS_TOO_FAR_APART));
  }

  private static void checkHisto(Histo histo) throws BadRequestException {
    // Payload must not be null
    checkArgument(
        histo != null, () -> new BadRequestException(UserFacingMessages.HISTO_PAYLOAD_MISSING));

    var points = histo.getHistoPoints();
    checkArgument(
        points != null,
        () -> new BadRequestException(UserFacingMessages.HISTO_LES_VALUES_REQUIRED));
    for (HistoPoint pt : points) {
      var buckets = pt.getBuckets();
      var counts = pt.getBucketCounts();
      checkArgument(
          buckets != null && counts != null,
          () -> new BadRequestException(UserFacingMessages.HISTO_LES_VALUES_REQUIRED));
      checkArgument(
          counts.length == buckets.length + 1,
          () -> new BadRequestException(UserFacingMessages.HISTO_ARRAYS_MUST_BE_EQUAL_LENGTH));
      for (var c : counts) {
        checkArgument(
            c >= 0,
            () -> new BadRequestException(UserFacingMessages.HISTO_VALUES_MUST_BE_POSITIVE));
      }
    }
  }

  private static void checkCounter(Sum counter) throws BadRequestException {
    // Payload must not be null
    checkArgument(
        counter != null, () -> new BadRequestException(UserFacingMessages.COUNTER_PAYLOAD_MISSING));

    // For Sum, ensure points are present
    checkArgument(
        counter.getSumPoints() != null,
        () -> new BadRequestException(UserFacingMessages.COUNTER_TS_REQUIRED));
  }
}
