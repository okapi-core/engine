/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import org.okapi.exceptions.BadRequestException;

public final class ChTracesValidator {
  private ChTracesValidator() {}

  public static void validate(ExportTraceServiceRequest request) throws BadRequestException {
    if (request == null) {
      throw new BadRequestException("Trace request is required");
    }
    if (!hasSpans(request)) {
      throw new BadRequestException("Trace request must contain spans");
    }
  }

  private static boolean hasSpans(ExportTraceServiceRequest request) {
    return request.getResourceSpansList().stream()
        .anyMatch(
            rs -> rs.getScopeSpansList().stream().anyMatch(ss -> !ss.getSpansList().isEmpty()));
  }
}
