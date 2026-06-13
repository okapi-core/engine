/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

import org.okapi.promql.eval.Evaluable;
import org.okapi.promql.eval.LogicalExpr;

public final class StringLiteralExpr implements LogicalExpr {
  public final String value;

  public StringLiteralExpr(String value) {
    this.value = value;
  }

  @Override
  public Evaluable lower() {
    return ctx -> {
      throw new IllegalStateException("string literal is not directly evaluable");
    };
  }
}
