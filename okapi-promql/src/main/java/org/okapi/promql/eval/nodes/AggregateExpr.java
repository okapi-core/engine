/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class AggregateExpr implements LogicalExpr {
  public final String op;
  public final boolean isBy;
  public final List<String> groupLabels;
  public final List<LogicalExpr> args;
}
