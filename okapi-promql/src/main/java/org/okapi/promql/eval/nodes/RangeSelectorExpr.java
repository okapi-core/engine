/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

public final class RangeSelectorExpr implements LogicalExpr {
  public final SelectorExpr base;
  public final DurationExpr range;
  public final DurationExpr offset;
  public final ExtendedVectorMode mode;

  public RangeSelectorExpr(SelectorExpr base, DurationExpr range, DurationExpr offset) {
    this(base, range, offset, ExtendedVectorMode.NONE);
  }

  public RangeSelectorExpr(
      SelectorExpr base, DurationExpr range, DurationExpr offset, ExtendedVectorMode mode) {
    this.base = base;
    this.range = range;
    this.offset = offset;
    this.mode = mode;
  }

  public RangeSelectorExpr(SelectorExpr base, long rangeMs, Long offsetMs) {
    this(base, DurationExpr.fixedMs(rangeMs), offsetMs == null ? null : DurationExpr.fixedMs(offsetMs));
  }
}
