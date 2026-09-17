/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class FunctionExpr implements LogicalExpr {
  public final String name;
  public final List<LogicalExpr> args;
}
