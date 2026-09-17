/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.List;
import java.util.Objects;
import lombok.Value;

@Value
public final class ComparisonLogQueryExpr implements LogQueryExpr {
  FieldRef field;
  ComparisonOp op;
  List<LiteralValue> values;

  public ComparisonLogQueryExpr(FieldRef field, ComparisonOp op, List<LiteralValue> values) {
    this.field = Objects.requireNonNull(field, "field");
    this.op = Objects.requireNonNull(op, "op");
    this.values = values;
  }
}
