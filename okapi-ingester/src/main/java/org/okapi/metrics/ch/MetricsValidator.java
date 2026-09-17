/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import org.okapi.exceptions.BadRequestException;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.payloads.Gauge;
import org.okapi.rest.metrics.payloads.Histo;
import org.okapi.rest.metrics.payloads.HistoPoint;

public class MetricsValidator {

  public static void validate(Iterable<ExportMetricsRequest> requests) throws BadRequestException {
    for (ExportMetricsRequest req : requests) {
      validateRequest(req);
    }
  }

  public static void validateRequest(ExportMetricsRequest request) throws BadRequestException {
    switch (request.getType()) {
      case GAUGE:
        validateGauge(request.getGauge());
        break;
      case HISTO:
        validateHistogram(request.getHisto());
        break;
      case COUNTER:
        break;
      default:
        throw new BadRequestException("Unsupported metric type: " + request.getType());
    }
  }

  public static void validateGauge(Gauge gauge) throws BadRequestException {
    if (gauge == null) {
      throw new BadRequestException("Gauge payload is required for GAUGE metric type");
    }
    if (gauge.getTs() == null || gauge.getValue() == null) {
      throw new BadRequestException("Gauge timestamp and value are required");
    }
  }

  public static void validatePoint(HistoPoint histoPoint) throws BadRequestException {
    if (histoPoint.getTemporality() == null) {
      throw new BadRequestException("Histogram point temporality is required");
    }
  }

  public static void validateHistogram(Histo histogram) throws BadRequestException {
    if (histogram == null) {
      throw new BadRequestException("Histogram payload is required for HISTO metric type");
    }
    for (var pt : histogram.getHistoPoints()) {
      validatePoint(pt);
    }
  }
}
