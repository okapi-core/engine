/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

public final class BinaryOpExpr implements LogicalExpr {
  public final String op;
  public final LogicalExpr left, right;
  public final MatchSpec matchSpec;
  public final boolean boolModifier;
  public final FillSpec fillSpec;

  public BinaryOpExpr(
      String op, LogicalExpr left, LogicalExpr right, MatchSpec matchSpec, boolean boolModifier) {
    this(op, left, right, matchSpec, boolModifier, FillSpec.none());
  }

  public BinaryOpExpr(
      String op,
      LogicalExpr left,
      LogicalExpr right,
      MatchSpec matchSpec,
      boolean boolModifier,
      FillSpec fillSpec) {
    this.op = op;
    this.left = left;
    this.right = right;
    this.matchSpec = matchSpec;
    this.boolModifier = boolModifier;
    this.fillSpec = fillSpec;
  }
}
