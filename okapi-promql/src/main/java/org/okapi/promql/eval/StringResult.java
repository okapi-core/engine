/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import lombok.Getter;

public final class StringResult implements ExpressionResult {
  @Getter public final String value;

  public StringResult(String value) {
    this.value = value;
  }

  @Override
  public ValueType type() {
    return ValueType.STRING;
  }
}
