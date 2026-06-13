/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import lombok.Getter;

// eval/ScalarResult.java
public final class ScalarResult implements ExpressionResult {
  @Getter public final double value;

  public ScalarResult(double value) {
    this.value = value;
  }

  @Override
  public ValueType type() {
    return ValueType.SCALAR;
  }
}
