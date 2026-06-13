/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

public final class OffsetExpr implements LogicalExpr {
  public final LogicalExpr inner;
  public final DurationExpr offset;

  public OffsetExpr(LogicalExpr inner, DurationExpr offset) {
    this.inner = inner;
    this.offset = offset;
  }

  public OffsetExpr(LogicalExpr inner, long offsetMs) {
    this(inner, DurationExpr.fixedMs(offsetMs));
  }
}
