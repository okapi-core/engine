/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;

public interface TraceFilterStrategy {
  boolean shouldPrune(ExportTraceServiceRequest request);
}
