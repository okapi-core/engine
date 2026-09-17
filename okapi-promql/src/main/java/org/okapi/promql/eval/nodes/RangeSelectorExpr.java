/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class RangeSelectorExpr implements LogicalExpr {
  public final SelectorExpr base;
  public final DurationExpr range;
  public final DurationExpr offset;
  public final ExtendedVectorMode mode;
}
