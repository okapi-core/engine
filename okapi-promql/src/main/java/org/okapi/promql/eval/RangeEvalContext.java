/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import org.okapi.promql.eval.nodes.ExtendedVectorMode;

/** Expression-local range window semantics layered on top of the outer query context. */
public record RangeEvalContext(EvalContext query, long rangeMs, ExtendedVectorMode mode) {
  public long windowStart(long anchorMs) {
    return anchorMs - rangeMs;
  }

  public boolean includes(long ts, long anchorMs) {
    long start = windowStart(anchorMs);
    return switch (mode) {
      case NONE, SMOOTHED -> ts > start && ts <= anchorMs;
      case ANCHORED -> ts >= start && ts <= anchorMs;
    };
  }
}
