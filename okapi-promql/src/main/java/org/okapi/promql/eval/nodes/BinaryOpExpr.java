/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public final class BinaryOpExpr implements LogicalExpr {
  public final String op;
  public final LogicalExpr left, right;
  public final MatchSpec matchSpec;
  public final boolean boolModifier;
}
