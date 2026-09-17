/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.trace.v1.Span;
import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
public class TraceContext {
  SystemState state;
  byte[] traceId;
  Map<String, List<Span>> spansByService;
  Map<String, List<LogRecord>> logsByService;
}
