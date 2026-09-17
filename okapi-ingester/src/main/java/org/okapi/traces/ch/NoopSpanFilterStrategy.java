/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import io.opentelemetry.proto.trace.v1.Span;

public class NoopSpanFilterStrategy implements SpanFilterStrategy {
  @Override
  public boolean shouldPrune(Span span) {
    return false;
  }
}
