/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

public final class SubqueryExpr implements LogicalExpr {
  public final LogicalExpr inner;
  public final DurationExpr range;
  public final DurationExpr step;
  public final DurationExpr offset;

  public SubqueryExpr(
      LogicalExpr inner, DurationExpr range, DurationExpr step, DurationExpr offset) {
    this.inner = inner;
    this.range = range;
    this.step = step;
    this.offset = offset;
  }

  public SubqueryExpr(LogicalExpr inner, long rangeMs, long stepMs, Long offsetMs) {
    this(
        inner,
        DurationExpr.fixedMs(rangeMs),
        DurationExpr.fixedMs(stepMs),
        offsetMs == null ? null : DurationExpr.fixedMs(offsetMs));
  }
}
