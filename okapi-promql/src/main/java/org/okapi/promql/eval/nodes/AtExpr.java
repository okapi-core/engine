/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class AtExpr implements LogicalExpr {
  public final LogicalExpr inner;
  public final LogicalExpr atScalar;
}
