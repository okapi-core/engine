/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class StringLiteralExpr implements LogicalExpr {
  public final String value;
}
